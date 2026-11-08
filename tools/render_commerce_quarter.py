#!/usr/bin/env python3
"""Render the compiled Commerce Quarter mesh without redrawing its architecture.

Run with the system Python (the script launches Blender itself)::

    python3 tools/render_commerce_quarter.py
    python3 tools/render_commerce_quarter.py --width 900 --samples 12 --only exterior-street

The sole source for architecture, openings, roofs and furniture is scene.json.
The GEBÄUDEGRAPH collection is an unmodified import, with Minecraft Y mapped
onto Blender Z. A second collection contains declared photographic staging:
site paving, distant trees, cameras and light sources. The cutaway is a clipped
copy of the imported mesh, not an invented interior. No downloaded assets or
image generation are used. Available local Minecraft client textures are read
from the cached client JAR; copyright remains with Mojang/Microsoft.
"""
from __future__ import annotations
import argparse
import json
import math
import os
from pathlib import Path
import random
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SCENE = ROOT / 'examples/commerce-quarter/scene.json'
DEFAULT_OUTPUT = ROOT / 'examples/commerce-quarter/views'


def arguments():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--scene', type=Path, default=DEFAULT_SCENE)
    p.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    p.add_argument('--width', type=int, default=1800)
    p.add_argument('--height', type=int, default=1125)
    p.add_argument('--samples', type=int, default=40)
    p.add_argument('--engine', choices=['cycles', 'eevee'], default='cycles')
    p.add_argument('--only', nargs='*', default=[])
    p.add_argument('--blend', action='store_true')
    p.add_argument('--no-textures', action='store_true')
    return p.parse_args(argv)


def launch_blender():
    args = arguments()
    env = dict(os.environ)
    # This distribution pairs Blender's OCIO 2.5 configuration with OCIO 2.4.
    # Copy the configuration, retaining all LUTs, to match the supported profile version.
    # This fixes colour management without touching system installations.
    config = Path('/usr/share/blender/5.2/datafiles/colormanagement/config.ocio')
    if config.exists() and not env.get('OCIO'):
        cfg = config.read_text()
        if 'ocio_profile_version: 2.5' in cfg:
            cfg = cfg.replace('ocio_profile_version: 2.5', 'ocio_profile_version: 2.4')
            paths = ':'.join(str(config.parent / x) for x in ['icc', 'luts', 'filmic'])
            cfg = cfg.replace('search_path: "icc:luts:filmic"', 'search_path: "' + paths + '"')
            target = ROOT / 'build/exports/commerce-quarter-render/config.ocio'
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(cfg)
            env['OCIO'] = str(target)
    env.setdefault('OPENBLAS_NUM_THREADS', '1')
    blender = shutil.which('blender') or '/usr/bin/blender'
    command = [blender, '--background', '--factory-startup', '--threads', '12', '--python', str(Path(__file__).resolve()), '--'] + sys.argv[1:]
    raise SystemExit(subprocess.call(command, env=env))


try:
    import bpy
    import bmesh
    from mathutils import Vector
except ImportError:
    launch_blender()

ARGS = arguments()
RNG = random.Random(630)
MATERIALS = {}
TEXTURES = {}
IMPORTED = []
STAGED = []
CUT = []


def xyz(v):
    """Orientation-preserving Minecraft (X,Y,Z) -> Blender (X,-Z,Y)."""
    return (float(v[0]), -float(v[2]), float(v[1]))


def collection(name):
    col = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(col)
    return col


def attach(obj, col):
    for old in list(obj.users_collection):
        old.objects.unlink(obj)
    col.objects.link(obj)
    return obj


def srgb(rgb):
    def linear(c):
        return c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4
    return tuple(linear(c) for c in rgb)


def block_colour(name):
    n = name.lower()
    palette = [
        ('glass', (.65, .83, .88)), ('granite', (.65, .38, .31)),
        ('brick', (.59, .31, .24)), ('terracotta', (.69, .48, .36)),
        ('deepslate', (.23, .27, .29)), ('blackstone', (.18, .19, .20)),
        ('andesite', (.52, .55, .53)), ('calcite', (.82, .79, .68)),
        ('quartz', (.91, .88, .79)), ('sandstone', (.77, .70, .54)),
        ('white', (.88, .86, .77)), ('iron', (.28, .31, .31)),
        ('copper', (.62, .41, .25)), ('spruce', (.43, .29, .17)),
        ('dark_oak', (.28, .20, .14)), ('oak', (.61, .46, .29)),
        ('stripped', (.53, .39, .25)), ('leaves', (.28, .43, .20)),
        ('moss', (.33, .43, .23)), ('grass', (.31, .41, .24)),
        ('dirt', (.38, .29, .20)), ('water', (.27, .49, .55)),
        ('stone', (.53, .55, .54)), ('red', (.58, .24, .19)),
        ('black', (.20, .22, .22)), ('lantern', (.95, .69, .33)),
        ('gold', (.83, .64, .26)), ('yellow', (.84, .66, .34)),
    ]
    return next((colour for key, colour in palette if key in n), (.62, .59, .51))


def texture_file(name):
    if ARGS.no_textures:
        return None
    block = name.split(':')[-1].split('[')[0]
    # Full blocks used by compiled surfaces, with harmless aliases for states.
    aliases = {'grass_block': 'grass_block_top', 'dark_oak_log': 'dark_oak_log',
               'spruce_log': 'spruce_log', 'oak_log': 'oak_log',
               'water': None, 'glass_pane': 'glass', 'iron_bars': 'iron_block',
               'lantern': None, 'chain': 'iron_block'}
    block = aliases.get(block, block)
    if block is None:
        return None
    if block in TEXTURES:
        return TEXTURES[block]
    jar = Path('/home/alex/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
    if not jar.exists():
        TEXTURES[block] = None
        return None
    key = 'assets/minecraft/textures/block/' + block + '.png'
    with zipfile.ZipFile(jar) as zf:
        if key not in zf.namelist():
            TEXTURES[block] = None
            return None
        target = ROOT / 'build/exports/commerce-quarter-render/textures' / (block + '.png')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(zf.read(key))
    TEXTURES[block] = target
    return target


def material(name):
    if name in MATERIALS:
        return MATERIALS[name]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    bsdf = nodes.get('Principled BSDF')
    colour = block_colour(name)
    bsdf.inputs['Base Color'].default_value = (*srgb(colour), 1)
    bsdf.inputs['Roughness'].default_value = .73
    if 'glass' in name:
        # Clear photographic glass keeps the actual graph openings legible.
        bsdf.inputs['Base Color'].default_value = (*srgb((.78, .89, .91)), 1)
        bsdf.inputs['Roughness'].default_value = .13
        bsdf.inputs['IOR'].default_value = 1.45
        bsdf.inputs['Transmission Weight'].default_value = .94
        bsdf.inputs['Alpha'].default_value = .18
    else:
        file = texture_file(name)
        if file:
            image = bpy.data.images.load(str(file), check_existing=True)
            tex = nodes.new('ShaderNodeTexImage')
            tex.image = image
            tex.interpolation = 'Closest'
            tex.extension = 'REPEAT'
            links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
            bump = nodes.new('ShaderNodeBump')
            bump.inputs['Strength'].default_value = .10
            bump.inputs['Distance'].default_value = .035
            links.new(tex.outputs['Color'], bump.inputs['Height'])
            links.new(bump.outputs['Normal'], bsdf.inputs['Normal'])
        if any(word in name for word in ['iron', 'copper', 'gold']):
            bsdf.inputs['Metallic'].default_value = .6
            bsdf.inputs['Roughness'].default_value = .39
        if any(word in name for word in ['lantern', 'glowstone', 'sea_lantern']):
            bsdf.inputs['Emission Color'].default_value = (*srgb((1, .69, .32)), 1)
            bsdf.inputs['Emission Strength'].default_value = 2
        if 'water' in name:
            bsdf.inputs['Roughness'].default_value = .18
            bsdf.inputs['Metallic'].default_value = .3
    mat.diffuse_color = (*colour, 1)
    MATERIALS[name] = mat
    return mat


def assign_uv(mesh):
    uv = mesh.uv_layers.new(name='Minecraft blocks / 1m')
    for poly in mesh.polygons:
        normal = poly.normal
        major = max(range(3), key=lambda i: abs(normal[i]))
        for li in poly.loop_indices:
            co = mesh.vertices[mesh.loops[li].vertex_index].co
            pair = (co.x, -co.y) if major == 2 else ((-co.y, co.z) if major == 0 else (co.x, co.z))
            uv.data[li].uv = pair


def import_graph(data, col):
    # Grouping avoids one Blender object per printed block. It never changes
    # a vertex or a face: only mesh buffers sharing their surface material join.
    grouped = {}
    for item in data['meshes']:
        key = (item.get('material', 'minecraft:stone'), item.get('component', 'architecture'))
        group = grouped.setdefault(key, {'vertices': [], 'faces': []})
        offset = len(group['vertices'])
        group['vertices'].extend(xyz(v) for v in item['vertices'])
        group['faces'].extend([[offset + ix for ix in f] for f in item['faces']])
    for i, ((block, component), item) in enumerate(grouped.items()):
        verts = item['vertices']
        faces = item['faces']
        if not verts or not faces:
            continue
        mesh = bpy.data.meshes.new(f'{component}-{i:04d}')
        mesh.from_pydata(verts, [], faces)
        mesh.validate(verbose=False)
        mesh.update()
        # Keep exact planar graph surfaces and their sharp silhouettes.
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        bm.to_mesh(mesh)
        bm.free()
        mesh.update()
        assign_uv(mesh)
        obj = bpy.data.objects.new(f'{component} | {block} | {i:04d}', mesh)
        col.objects.link(obj)
        obj.data.materials.append(material(block))
        obj['source'] = 'compiled building graph; unchanged mesh geometry'
        obj['component'] = component
        obj['block_material'] = block
        IMPORTED.append(obj)
    print(f'IMPORTED {len(IMPORTED)} graph meshes, {sum(len(o.data.polygons) for o in IMPORTED)} faces', flush=True)


def plain_material(name, rgb, roughness=.8):
    key = 'staging:' + name
    if key in MATERIALS:
        return MATERIALS[key]
    m = bpy.data.materials.new(key)
    m.use_nodes = True
    p = m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value = (*srgb(rgb), 1)
    p.inputs['Roughness'].default_value = roughness
    MATERIALS[key] = m
    return m


def staged_box(name, center, size, mat, col):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(center))
    obj = bpy.context.object
    obj.name = 'INSZENIERUNG | ' + name
    obj.dimensions = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    attach(obj, col)
    obj.data.materials.append(mat)
    obj['source'] = 'declared external photographic staging, not building graph'
    assign_uv(obj.data)
    STAGED.append(obj)
    return obj


def tree(pos, size, col):
    """Restrained faceted tree well outside the compiled building's footprint."""
    x, y, z = pos
    trunk = plain_material('Baumstamm', (.32, .25, .17))
    leaves = plain_material('Baumkrone', (.36, .45, .23))
    staged_box('Baumstamm', (x, y + size * .30, z), (.35, size * .60, .35), trunk, col)
    for ox, oy, oz, scale in [(0, .64, 0, .37), (-.18, .70, .07, .28), (.16, .79, -.03, .30)]:
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=size * scale,
                                            location=xyz((x + ox * size, y + oy * size, z + oz * size)))
        obj = bpy.context.object
        obj.name = 'INSZENIERUNG | Baumkrone'
        attach(obj, col)
        obj.data.materials.append(leaves)
        obj['source'] = 'declared external photographic staging, not building graph'
        STAGED.append(obj)


def stage_site(col, data):
    lo, hi = data['bounds']['min'], data['bounds']['max']
    cx = (lo[0] + hi[0]) / 2
    ground = plain_material('Hintergrund Kies', (.69, .69, .62))
    paving = material('minecraft:polished_andesite')
    road = plain_material('Strassenstein', (.37, .39, .37))
    grass = plain_material('Staudenbeet', (.43, .47, .31))
    staged_box('Hintergrund', (cx, -.33, 10), (1500, .25, 1500), ground, col)
    staged_box('Strasse', (cx, -.16, -7), (90, .12, 9), road, col)
    staged_box('Gehweg', (cx, -.10, -1.5), (66, .18, 3), paving, col)
    staged_box('Umfeld rechts', (hi[0] + 6, -.15, 15), (9, .12, 37), grass, col)
    for x, z, scale in [(lo[0] - 7, 12, 7), (lo[0] - 8, 25, 6.4), (hi[0] + 8, 24, 8),
                         (hi[0] + 9, 4, 6), (cx - 7, hi[2] + 9, 7), (cx + 11, hi[2] + 10, 7.8)]:
        tree((x, 0, z), scale, col)
    # Thin kerb modules give scale without modelling neighbouring buildings.
    for i in range(-20, 24):
        staged_box('Bordstein', (cx + i * 1.6, .02, -3.05), (1.55, .24, .25), paving, col)


def light(name, kind, position, energy, colour, size=5, target=None):
    dat = bpy.data.lights.new(name, kind)
    dat.energy = energy
    dat.color = colour
    if kind == 'AREA':
        dat.shape = 'DISK'
        dat.size = size
    obj = bpy.data.objects.new(name, dat)
    LIGHTS.objects.link(obj)
    obj.location = xyz(position)
    if target:
        obj.rotation_euler = (Vector(xyz(target)) - obj.location).to_track_quat('-Z', 'Y').to_euler()
    return obj


def set_lighting(interior=False, axon=False):
    for obj in list(LIGHTS.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    world = bpy.context.scene.world
    world.use_nodes = True
    bg = world.node_tree.nodes.get('Background')
    bg.inputs['Color'].default_value = (.68, .79, .93, 1)
    bg.inputs['Strength'].default_value = .5 if not interior else .6
    sun = light('Licht | Nachmittagssonne', 'SUN', (-20, 42, -22), 2.3 if not interior else 1.8,
                (1.0, .89, .74), target=(16, 0, 12))
    sun.data.angle = math.radians(12)
    light('Licht | Himmelsaufhellung', 'AREA', (18, 30, 6), 4500, (.76, .85, 1), 28, (17, 0, 10))
    light('Licht | Strassenreflex', 'AREA', (13, 11, -14), 2600, (1, .91, .80), 20, (17, 5, 8))
    light('Licht | Hofreflex', 'AREA', (15, 15, 32), 2400, (.91, .96, 1), 18, (17, 4, 13))
    if interior:
        # Invisible photographic fill, not invented lamps or architecture.
        light('Fotoaufhellung | Gasthaus', 'AREA', (6, 4.7, 5), 650, (1, .88, .74), 6, (6, 1, 5))
        light('Fotoaufhellung | Atelier', 'AREA', (25, 11.5, 5), 1000, (1, .92, .82), 7, (25, 7, 5))
        light('Fotoaufhellung | Atelier hinten', 'AREA', (26, 11.5, 17), 950, (1, .94, .85), 8, (26, 7, 17))
        light('Fotoaufhellung | Treppenhaus', 'AREA', (27, 5.6, 17), 350, (1, .94, .85), 5, (27, 1, 17))


def camera(name, pos, target, lens=40, ortho=None):
    dat = bpy.data.cameras.new(name)
    obj = bpy.data.objects.new(name, dat)
    CAMERAS.objects.link(obj)
    obj.location = xyz(pos)
    obj.rotation_euler = (Vector(xyz(target)) - obj.location).to_track_quat('-Z', 'Y').to_euler()
    dat.lens = lens
    dat.sensor_width = 36
    dat.clip_start = .05
    dat.clip_end = 400
    if ortho:
        dat.type = 'ORTHO'
        dat.ortho_scale = ortho
    return obj


def create_cutaway(data):
    """Cut at actual planes; all retained triangles are from the compiler."""
    cut = collection('SCHNITT | Kopie der kompilierten Geometrie')
    cut_height = float(data.get('metadata', {}).get('cut_height', 12.85))
    front_cut = float(data.get('metadata', {}).get('cut_front', 7.8))
    for original in IMPORTED:
        if original.get('component', '').lower() in ['roof', 'gable']:
            continue
        obj = original.copy()
        obj.data = original.data.copy()
        obj.name = 'SCHNITT | ' + original.name
        cut.objects.link(obj)
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        if original.get('component', '').lower() == 'ceiling':
            # Upper ceilings and the inn roof deck are removed as view-only
            # coverings. Retain the shop's real intermediate floor at Y6..7.
            covering = [f for f in bm.faces if f.calc_center_median().z > 11.5
                        or f.calc_center_median().x < 13]
            bmesh.ops.delete(bm, geom=covering, context='FACES')
        # Remove roof and upper wall above a real horizontal section plane.
        bmesh.ops.bisect_plane(bm, geom=list(bm.verts) + list(bm.edges) + list(bm.faces),
                              dist=.00001, plane_co=(0, 0, cut_height), plane_no=(0, 0, 1),
                              clear_outer=True, clear_inner=False)
        # A true vertical section removes the near half of both storeys,
        # including floors, so the real stair, tables and two levels are visible.
        if original.get('component', '').lower() not in ['garden', 'landscape', 'pavement']:
            bmesh.ops.bisect_plane(bm, geom=list(bm.verts) + list(bm.edges) + list(bm.faces),
                                  dist=.00001, plane_co=(0, -front_cut, 0), plane_no=(0, 1, 0),
                                  clear_outer=True, clear_inner=False)
        bm.to_mesh(obj.data)
        bm.free()
        obj.data.update()
        obj['source'] = 'sectioned copy of compiled building graph'
        obj.hide_render = True
        CUT.append(obj)


def render_view(view):
    name = view['name']
    if ARGS.only and name not in ARGS.only:
        return None
    is_cut = name == 'section-axon'
    for obj in IMPORTED:
        obj.hide_render = is_cut
        obj.hide_set(is_cut)
    for obj in CUT:
        obj.hide_render = not is_cut
        obj.hide_set(not is_cut)
    for obj in STAGED:
        obj.hide_render = is_cut
        obj.hide_set(is_cut)
    set_lighting(interior=name.startswith('interior') or is_cut, axon=is_cut)
    if is_cut:
        # Neutral background and contact plane for the architectural section.
        bg = bpy.context.scene.world.node_tree.nodes.get('Background')
        bg.inputs['Color'].default_value = (.83, .85, .83, 1)
        bg.inputs['Strength'].default_value = .7
    bpy.context.scene.camera = camera(name, view['position'], view['target'], view.get('lens', 40), view.get('ortho'))
    bpy.context.scene.render.filepath = str(ARGS.output / f'{name}.png')
    bpy.context.scene.view_settings.exposure = view.get('exposure', .55 if name.startswith('interior') else 0)
    print('RENDER_VIEW ' + name + ' ' + json.dumps(view), flush=True)
    bpy.ops.render.render(write_still=True)
    return {'file': name + '.png', 'camera_minecraft': view, 'geometry': 'scene.json',
            'sectioned': is_cut, 'staging': 'external site and lighting only'}


def main():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    bpy.context.scene.world = bpy.data.worlds.new('Licht | Heller Himmel')
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES' if ARGS.engine == 'cycles' else 'BLENDER_EEVEE'
    if ARGS.engine == 'cycles':
        scene.cycles.device = 'CPU'
        scene.cycles.samples = ARGS.samples
        scene.cycles.use_denoising = True
        scene.cycles.max_bounces = 7
        scene.cycles.diffuse_bounces = 4
        scene.cycles.glossy_bounces = 4
        scene.cycles.transmission_bounces = 6
        scene.cycles.transparent_max_bounces = 8
        scene.cycles.adaptive_threshold = .035
        scene.render.use_persistent_data = True
    else:
        scene.eevee.taa_render_samples = ARGS.samples
        scene.eevee.use_raytracing = True
    scene.render.resolution_x = ARGS.width
    scene.render.resolution_y = ARGS.height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.image_settings.color_depth = '8'
    scene.render.film_transparent = False
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 12
    try:
        scene.view_settings.view_transform = 'AgX'
        scene.view_settings.look = 'AgX - Medium High Contrast'
    except TypeError:
        scene.view_settings.view_transform = 'Standard'
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1
    data = json.loads(ARGS.scene.read_text())
    if 'bounds' not in data:
        vertices = [v for mesh in data['meshes'] for v in mesh['vertices']]
        data['bounds'] = {'min': [min(v[i] for v in vertices) for i in range(3)],
                          'max': [max(v[i] for v in vertices) for i in range(3)]}
    ARGS.output.mkdir(parents=True, exist_ok=True)
    global LIGHTS, CAMERAS
    LIGHTS = collection('INSZENIERUNG | Lichtquellen')
    CAMERAS = collection('ANSICHTEN | Kameras')
    imported_col = collection('GEBAEUDEGRAPH | Kompilierte Originalgeometrie')
    staging_col = collection('INSZENIERUNG | Umfeld ausserhalb Gebaeudegraph')
    import_graph(data, imported_col)
    stage_site(staging_col, data)
    create_cutaway(data)
    # The model's real room dimensions may provide camera overrides in metadata.
    views = [
        {'name': 'exterior-street', 'position': [48, 25, -42], 'target': [16, 6.5, 8], 'lens': 46},
        {'name': 'exterior-courtyard', 'position': [-16, 23, 46], 'target': [17, 6, 10], 'lens': 43},
        {'name': 'interior-inn', 'position': [1.9, 2.8, 8.6], 'target': [9, 2.1, 2], 'lens': 22},
        {'name': 'interior-atelier', 'position': [19, 8.8, 9], 'target': [29, 8, 1.4], 'lens': 22},
        {'name': 'section-axon', 'position': [48, 43, -42], 'target': [16.5, 3, 11], 'ortho': 48, 'lens': 45},
    ]
    aliases = {'exterior_overview': 'exterior-street', 'exterior_court': 'exterior-courtyard',
               'interior_inn': 'interior-inn', 'interior_upper': 'interior-atelier'}
    supplied = {}
    for c in data.get('cameras', []) + data.get('metadata', {}).get('cameras', []):
        name = c.get('name') or aliases.get(c.get('id'))
        if name:
            supplied[name] = {k: v for k, v in c.items() if k != 'id'}
    for view in views:
        view.update(supplied.get(view['name'], {}))
    # Camera refinements after the compiler's suggested views: retain complete
    # chimneys in the exterior frames and enough context around the site.
    for view in views:
        if view['name'] == 'exterior-street':
            view.update(position=[48, 19, -50], target=[16, 7.5, 10], lens=43)
        elif view['name'] == 'exterior-courtyard':
            view.update(position=[-28, 23, 50], target=[15, 7.5, 12], lens=40)
    manifest = []
    for view in views:
        item = render_view(view)
        if item:
            manifest.append(item)
    (ARGS.output / 'render-manifest.json').write_text(json.dumps({
        'source': str(ARGS.scene.relative_to(ROOT)) if ARGS.scene.is_relative_to(ROOT) else str(ARGS.scene),
        'coordinate_mapping': 'Minecraft [X,Y,Z] -> Blender [X,-Z,Y]',
        'geometry_policy': 'Architecture, openings, roofs, furnishings imported unchanged from compiled graph. Section view is a clipped copy.',
        'texture_source': 'locally cached Minecraft 1.21.1 client JAR; original pixel textures',
        'renderer': 'Blender ' + bpy.app.version_string,
        'engine': ARGS.engine, 'samples': ARGS.samples,
        'resolution': [ARGS.width, ARGS.height], 'views': manifest,
    }, indent=2) + '\n')
    if ARGS.blend:
        target = ROOT / 'build/exports/commerce-quarter.blend'
        target.parent.mkdir(parents=True, exist_ok=True)
        bpy.ops.wm.save_as_mainfile(filepath=str(target), compress=True)
    print('RENDER_COMPLETE ' + str(ARGS.output), flush=True)


if __name__ == '__main__':
    main()
