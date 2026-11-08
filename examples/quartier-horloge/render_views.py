#!/usr/bin/env python3
"""Render the compiled Quartier de l'Horloge building graph with Blender.

    python3 examples/quartier-horloge/render_views.py --quick
    python3 examples/quartier-horloge/render_views.py --final

The Python launcher finds Blender and, for this workstation's OCIO 2.4 / 2.5
packaging mismatch, creates a compatible local copy of Blender's color profile.
It never edits Blender or user preferences. Direct Blender invocation also works
on systems with a working OCIO installation.

Architecture comes exclusively from plan-geometry.json. The renderer supplies
materials, daylight, cameras and a neutral ground plane. It does not invent any
architectural geometry. Coordinates are Minecraft (X,Y-up,Z), one block = 1 m.
Optional top-level cameras use the same coordinate system.
"""

from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import traceback

HERE = Path(__file__).resolve().parent


def launch_blender():
    executable = shutil.which("blender")
    if not executable:
        raise SystemExit("Blender is required. Install Blender, then rerun this command.")
    env = dict(os.environ)
    config = Path("/usr/share/blender/5.2/datafiles/colormanagement/config.ocio")
    if config.is_file():
        text = config.read_text(encoding="utf8")
        if "ocio_profile_version: 2.5" in text:
            text = text.replace("ocio_profile_version: 2.5", "ocio_profile_version: 2.4")
            # OCIO 2.4 ignores the newer metadata; AgX's transforms are compatible.
            def search_paths(match):
                paths = match.group(1).split(":")
                return 'search_path: "' + ":".join(str(config.parent / p) for p in paths) + '"'
            text = re.sub(r'^search_path: "([^"]+)"', search_paths, text, flags=re.MULTILINE)
            local_config = HERE / "views" / "render-color.ocio"
            local_config.parent.mkdir(parents=True, exist_ok=True)
            local_config.write_text(text, encoding="utf8")
            env["OCIO"] = str(local_config)
            print(f"Using local compatible color profile: {local_config}", flush=True)
    command = [executable, "--background", "--factory-startup", "-noaudio", "--python", str(Path(__file__).resolve()), "--", *sys.argv[1:]]
    return subprocess.call(command, env=env)


try:
    import bpy
    from mathutils import Vector
except ImportError:
    if __name__ == "__main__":
        raise SystemExit(launch_blender())
    raise


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mesh", type=Path, default=HERE / "plan-geometry.json")
    parser.add_argument("--output", type=Path, default=HERE / "views")
    quality = parser.add_mutually_exclusive_group()
    quality.add_argument("--quick", action="store_true")
    quality.add_argument("--final", action="store_true")
    parser.add_argument("--views", nargs="+")
    parser.add_argument("--samples", type=int)
    parser.add_argument("--width", type=int)
    parser.add_argument("--height", type=int)
    parser.add_argument("--engine", default="CYCLES", choices=["CYCLES", "BLENDER_EEVEE_NEXT"])
    parser.add_argument("--no-blend", action="store_true")
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    return parser.parse_args(argv)


def xyz(v):
    return (float(v[0]), -float(v[2]), float(v[1]))


def look_at(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def shader_material(name, color, roughness=0.65, metallic=0):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*color, 1)
    mat.use_nodes = True
    shader = mat.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*color, 1)
    shader.inputs["Roughness"].default_value = roughness
    shader.inputs["Metallic"].default_value = metallic
    return mat, shader


def grain(mat, shader, color, scale=18, strength=0.08, distance=0.012):
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    geo = nodes.new("ShaderNodeNewGeometry")
    noise = nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 2
    links.new(geo.outputs["Position"], noise.inputs["Vector"])
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*[c * 0.87 for c in color], 1)
    ramp.color_ramp.elements[1].color = (*[min(c * 1.10, 1) for c in color], 1)
    links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], shader.inputs["Base Color"])
    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = strength
    bump.inputs["Distance"].default_value = distance
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], shader.inputs["Normal"])
    return geo, noise


def brick_material(key):
    color = (0.34, 0.125, 0.072)
    mat, shader = shader_material(key, color, roughness=0.8)
    geo, noise = grain(mat, shader, color, scale=38, strength=0.11, distance=0.012)
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    position, normal = nodes.new("ShaderNodeSeparateXYZ"), nodes.new("ShaderNodeSeparateXYZ")
    links.new(geo.outputs["Position"], position.inputs[0])
    links.new(geo.outputs["Normal"], normal.inputs[0])
    textures = []
    for axes in [("X", "Z", "Y"), ("Y", "Z", "X"), ("X", "Y", "Z")]:
        vector = nodes.new("ShaderNodeCombineXYZ")
        for dest, axis in zip(("X", "Y", "Z"), axes):
            links.new(position.outputs[axis], vector.inputs[dest])
        brick = nodes.new("ShaderNodeTexBrick")
        brick.inputs["Scale"].default_value = 1
        brick.inputs["Color1"].default_value = (0.38, 0.145, 0.086, 1)
        brick.inputs["Color2"].default_value = (0.25, 0.076, 0.041, 1)
        brick.inputs["Mortar"].default_value = (0.31, 0.245, 0.18, 1)
        brick.inputs["Mortar Size"].default_value = 0.009
        brick.inputs["Mortar Smooth"].default_value = 0.005
        brick.inputs["Brick Width"].default_value = 0.42
        brick.inputs["Row Height"].default_value = 0.16
        links.new(vector.outputs[0], brick.inputs["Vector"])
        textures.append(brick)
    side = nodes.new("ShaderNodeMixRGB")
    top = nodes.new("ShaderNodeMixRGB")
    abs_x, abs_z = nodes.new("ShaderNodeMath"), nodes.new("ShaderNodeMath")
    abs_x.operation = abs_z.operation = "ABSOLUTE"
    links.new(normal.outputs["X"], abs_x.inputs[0])
    links.new(normal.outputs["Z"], abs_z.inputs[0])
    links.new(abs_x.outputs[0], side.inputs[0])
    links.new(textures[0].outputs["Color"], side.inputs[1])
    links.new(textures[1].outputs["Color"], side.inputs[2])
    links.new(abs_z.outputs[0], top.inputs[0])
    links.new(side.outputs[0], top.inputs[1])
    links.new(textures[2].outputs["Color"], top.inputs[2])
    links.new(top.outputs[0], shader.inputs["Base Color"])
    return mat


def wood_material(key):
    """Subtle directional grain; this changes surface shading only."""
    dark = "dark_oak" in key
    color = (0.13, 0.060, 0.023) if dark else (0.28, 0.15, 0.067)
    mat, shader = shader_material(key, color, roughness=0.6)
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    geo = nodes.new("ShaderNodeNewGeometry")
    vector = nodes.new("ShaderNodeVectorMath")
    vector.operation = "MULTIPLY"
    vector.inputs[1].default_value = (1.0, 24.0, 24.0)
    links.new(geo.outputs["Position"], vector.inputs[0])
    noise = nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 3.5
    noise.inputs["Detail"].default_value = 3
    noise.inputs["Roughness"].default_value = 0.65
    links.new(vector.outputs["Vector"], noise.inputs["Vector"])
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*[c * 0.62 for c in color], 1)
    ramp.color_ramp.elements[1].color = (*[c * 1.25 for c in color], 1)
    links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], shader.inputs["Base Color"])
    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.12
    bump.inputs["Distance"].default_value = 0.004
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], shader.inputs["Normal"])
    return mat


def graph_material(key, overrides):
    lower = key.lower()
    if "glass" in lower:
        mat, shader = shader_material(key, (0.78, 0.87, 0.88), 0.065)
        shader.inputs["Transmission Weight"].default_value = 0.98
        shader.inputs["IOR"].default_value = 1.45
        return mat
    if "white_terracotta" in lower or "stone_brick" in lower:
        color = (0.67, 0.61, 0.51) if "terracotta" in lower else (0.36, 0.355, 0.30)
        mat, shader = shader_material(key, color, 0.79)
        grain(mat, shader, color, 22, 0.06, 0.01)
        return mat
    if any(s in lower for s in ("brick", "terracotta", "granite")):
        return brick_material(key)
    if any(s in lower for s in ("oak", "spruce", "wood", "plank")):
        return wood_material(key)
    if any(s in lower for s in ("brass", "gold", "copper")):
        color = (0.48, 0.29, 0.075) if "oxid" not in lower else (0.1, 0.29, 0.23)
        mat, shader = shader_material(key, color, 0.36, 0.67)
    elif any(s in lower for s in ("slate", "blackstone", "basalt")):
        color = (0.057, 0.073, 0.087)
        mat, shader = shader_material(key, color, 0.62)
    elif any(s in lower for s in ("oak", "spruce", "wood", "plank")):
        color = (0.24, 0.115, 0.044)
        mat, shader = shader_material(key, color, 0.58)
    elif any(s in lower for s in ("iron", "steel", "andesite_casing")):
        color = (0.085, 0.105, 0.11)
        mat, shader = shader_material(key, color, 0.4, 0.56)
    elif any(s in lower for s in ("lamp", "glow", "light")):
        color = (0.9, 0.68, 0.32)
        mat, shader = shader_material(key, color, 0.35)
        shader.inputs["Emission Color"].default_value = (1, 0.72, 0.35, 1)
        shader.inputs["Emission Strength"].default_value = 2.5
    elif any(s in lower for s in ("calcite", "limestone", "quartz", "sandstone", "diorite")):
        color = (0.66, 0.585, 0.445)
        mat, shader = shader_material(key, color, 0.74)
    elif any(s in lower for s in ("grass", "leaves", "moss")):
        color = (0.17, 0.245, 0.105)
        mat, shader = shader_material(key, color, 0.83)
    else:
        color = (0.37, 0.36, 0.31)
        mat, shader = shader_material(key, color, 0.76)
    definition = overrides.get(key, {}) if isinstance(overrides, dict) else {}
    if "color" in definition:
        color = tuple(definition["color"][:3])
    for setting in ("roughness", "metallic"):
        if setting in definition:
            shader.inputs[setting.title()].default_value = definition[setting]
    grain(mat, shader, color)
    return mat


def box_mesh(item):
    lo, hi = item["min"], item["max"]
    vertices = [[x, y, z] for z in (lo[2], hi[2]) for y in (lo[1], hi[1]) for x in (lo[0], hi[0])]
    faces = [[0, 2, 3, 1], [4, 5, 7, 6], [0, 1, 5, 4], [2, 6, 7, 3], [0, 4, 6, 2], [1, 3, 7, 5]]
    return vertices, faces


def import_graph(path):
    document = json.loads(path.read_text(encoding="utf8"))
    collection = bpy.data.collections.new("ARCHITECTURE | compiled building graph")
    bpy.context.scene.collection.children.link(collection)
    materials, source_vertices = {}, []
    items = document.get("meshes", document.get("objects", []))
    items += [dict(item, **dict(zip(("vertices", "faces"), box_mesh(item)))) for item in document.get("boxes", [])]
    if not items:
        raise ValueError("No meshes, objects, or boxes in graph geometry")
    for number, item in enumerate(items):
        key = item.get("material", "minecraft:stone")
        if key not in materials:
            materials[key] = graph_material(key, document.get("materials", {}))
        vertices = [xyz(v) for v in item["vertices"]]
        if not vertices or not item["faces"]:
            continue
        source_vertices.extend(vertices)
        name = item.get("name", f"graph-{number}")
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata(vertices, [], item["faces"])
        mesh.update()
        obj = bpy.data.objects.new(name, mesh)
        collection.objects.link(obj)
        mesh.materials.append(materials[key])
        obj["graph_component"] = str(item.get("component", ""))
        obj["graph_material"] = key
        obj["source"] = str(path)
        if "glass" not in key.lower():
            bevel = obj.modifiers.new("Render-only 8 mm edge highlight", "BEVEL")
            bevel.width = 0.008
            bevel.segments = 1
            bevel.limit_method = "ANGLE"
            bevel.angle_limit = math.radians(35)
    if not source_vertices:
        raise ValueError("Geometry contains no vertices")
    bounds = [(min(v[i] for v in source_vertices), max(v[i] for v in source_vertices)) for i in range(3)]
    print(f"Imported {len(collection.objects)} exact graph meshes; bounds={bounds}", flush=True)
    return document, bounds


def area_light(name, location, target, energy, size, color):
    data = bpy.data.lights.new(name, "AREA")
    data.energy, data.shape, data.size, data.color = energy, "DISK", size, color
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = location
    look_at(obj, target)
    return obj


def environment(bounds, document):
    (xmin, xmax), (ymin, ymax), (zmin, zmax) = bounds
    cx, cy = (xmin + xmax) / 2, (ymin + ymax) / 2
    bpy.ops.mesh.primitive_plane_add(size=1200, location=(cx, cy, zmin - 0.06))
    obj = bpy.context.object
    obj.name = "PRESENTATION ONLY | neutral mineral ground"
    mat, shader = shader_material("Ground | pale warm grey", (0.40, 0.39, 0.345), 0.9)
    grain(mat, shader, (0.40, 0.39, 0.345), 1.4, 0.05, 0.02)
    obj.data.materials.append(mat)
    world = bpy.data.worlds.new("Daylight | architectural model")
    bpy.context.scene.world = world
    world.use_nodes = True
    nodes, links = world.node_tree.nodes, world.node_tree.links
    sky = nodes.new("ShaderNodeTexSky")
    sky_models = {item.identifier for item in sky.bl_rna.properties["sky_type"].enum_items}
    sky.sky_type = "MULTIPLE_SCATTERING" if "MULTIPLE_SCATTERING" in sky_models else "NISHITA"
    sky.sun_elevation, sky.sun_rotation = math.radians(33), math.radians(145)
    sky.air_density, sky.ozone_density = 1, 1.2
    if hasattr(sky, "aerosol_density"):
        sky.aerosol_density = 0.7
    else:
        sky.dust_density = 0.7
    sky.sun_intensity, sky.sun_size = 0.6, math.radians(3)
    sky.sun_disc = False  # The separate Sun lamp defines the direct illumination.
    background = nodes.get("Background")
    background.inputs["Strength"].default_value = 0.42
    links.new(sky.outputs["Color"], background.inputs["Color"])
    data = bpy.data.lights.new("Sun | broad warm shadows", "SUN")
    data.energy, data.angle, data.color = 2.1, math.radians(4), (1, 0.91, 0.78)
    obj = bpy.data.objects.new(data.name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = (cx - 60, cy + 80, zmax + 80)
    look_at(obj, (cx, cy, 0))
    area_light("Sky fill | soft blue", (cx + 50, cy + 20, zmax + 20), (cx, cy, 5), 6500, 45, (0.78, 0.88, 1))
    lights = []
    default_lights = [
        {"name": "hall south windows", "eye": [7, 4.4, 2], "target": [7, 3, 15], "energy": 1050, "size": 8, "color": [0.87, 0.93, 1]},
        {"name": "hall clerestory", "eye": [7, 11.6, 11], "target": [7, 2, 11], "energy": 1350, "size": 10},
        {"name": "hall north windows", "eye": [7, 8.8, 20], "target": [7, 7, 8], "energy": 950, "size": 8},
        {"name": "clock tower", "eye": [21, 22, 3], "target": [21, 13, 3], "energy": 950, "size": 4},
        {"name": "tower landing bounce", "eye": [24, 16, 1], "target": [20, 16, 4], "energy": 250, "size": 3},
    ]
    for index, definition in enumerate(document.get("render_lights", default_lights)):
        lamp = area_light("Photography bounce | " + definition.get("name", str(index)), xyz(definition["eye"]), xyz(definition["target"]), definition.get("energy", 1200), definition.get("size", 6), tuple(definition.get("color", [1, 0.91, 0.78])))
        lamp["purpose"] = "Invisible photographic bounce, not a graph component"
        lights.append(lamp)
    return lights


def cameras(document, bounds):
    (xmin, xmax), (ymin, ymax), (_, zmax) = bounds
    cx, cz = (xmin + xmax) / 2, -(ymin + ymax) / 2
    width = max(xmax - xmin, ymax - ymin)
    defaults = {
        "exterior-front": {"label": "01 | Außenansicht · Uhrturm und Quartier", "eye": [-42, 28, -55], "target": [11, 16, 17], "lens": 55},
        "exterior-rear": {"label": "02 | Außenansicht · Hof und Dachlandschaft", "eye": [63, 32, 65], "target": [12, 13, 19], "lens": 48},
        "courtyard": {"label": "03 | Außenansicht · Hof zwischen den Wohnhäusern", "eye": [13, 5, 44], "target": [13, 9, 25], "lens": 25},
        "interior-hall": {"label": "04 | Innenansicht · Halle mit Galerie", "eye": [7.5, 3.1, 2.4], "target": [7.5, 6.4, 19], "lens": 22, "interior": True},
        "interior-gallery": {"label": "05 | Innenansicht · Blick von der Galerie", "eye": [7.5, 9.2, 21], "target": [7.5, 4, 5.5], "lens": 21, "interior": True},
        "interior-tower": {"label": "06 | Innenansicht · Treppenraum im Uhrturm", "eye": [24, 14.7, 0.8], "target": [20, 16, 4.6], "lens": 18, "interior": True},
        "clock-detail": {"label": "07 | Detailansicht · Uhr und exaktes Kupferdach", "eye": [36, 28, -19], "target": [21.5, 28, 1], "lens": 50},
    }
    supplied = document.get("cameras", {})
    if isinstance(supplied, list):
        supplied = {item["name"]: item for item in supplied}
    definitions = defaults | supplied
    result = {}
    for name, definition in definitions.items():
        data = bpy.data.cameras.new(name)
        data.lens = definition.get("lens", 35)
        data.sensor_width = 36
        data.clip_start, data.clip_end = 0.03, 2000
        if definition.get("orthographic"):
            data.type, data.ortho_scale = "ORTHO", definition["orthographic"]
        obj = bpy.data.objects.new("CAMERA | " + name, data)
        bpy.context.scene.collection.objects.link(obj)
        obj.location = xyz(definition["eye"])
        look_at(obj, xyz(definition["target"]))
        obj["caption"] = definition.get("label", name)
        obj["minecraft_eye"] = definition["eye"]
        result[name] = (obj, definition)
    return result


def setup_render(args):
    scene = bpy.context.scene
    scene.render.engine = args.engine
    scene.render.resolution_x = args.width or (2000 if args.final else 800)
    scene.render.resolution_y = args.height or (1400 if args.final else 560)
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode, scene.render.image_settings.color_depth = "RGB", "8"
    scene.render.film_transparent = False
    scene.render.threads_mode, scene.render.threads = "FIXED", 12
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
    scene.view_settings.exposure = 0.3
    if args.engine == "CYCLES":
        scene.cycles.samples = args.samples or (72 if args.final else 12)
        scene.cycles.use_denoising = True
        scene.cycles.adaptive_threshold = 0.035 if args.final else 0.11
        scene.cycles.max_bounces, scene.cycles.diffuse_bounces = 10, 4
        scene.cycles.glossy_bounces, scene.cycles.transmission_bounces = 4, 8
        scene.cycles.transparent_max_bounces, scene.cycles.seed = 12, 67
    scene["architecture_source"] = str(args.mesh)
    scene["coordinate_conversion"] = "Minecraft (X,Y,Z) -> Blender (X,-Z,Y); 1 block = 1 metre"
    scene["render_note"] = "Exact compiled graph meshes; render-only 8 mm bevel. Ground, lights and cameras are presentation only."


def main():
    args = arguments()
    args.output.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    document, bounds = import_graph(args.mesh)
    interior_lights = environment(bounds, document)
    specs = cameras(document, bounds)
    setup_render(args)
    scene = bpy.context.scene
    names = args.views or list(specs)
    manifest = {"mesh": str(args.mesh), "coordinate_conversion": "(X,Y,Z) -> (X,-Z,Y)", "architecture": "Compiled graph geometry only; non-destructive 8 mm edge highlight", "views": []}
    for name in names:
        if name not in specs:
            raise ValueError(f"Unknown camera {name!r}; available: {list(specs)}")
        camera, spec = specs[name]
        scene.camera = camera
        interior = spec.get("interior", False)
        scene.view_settings.exposure = spec.get("exposure", 0.1 if interior else 0.3)
        for lamp in interior_lights:
            lamp.hide_render = not interior
        destination = args.output / f"{name}.png"
        scene.render.filepath = str(destination)
        print(f"Rendering {name} -> {destination}", flush=True)
        bpy.ops.render.render(write_still=True)
        manifest["views"].append({"file": destination.name, "caption": spec.get("label", name), "minecraft_eye": spec["eye"], "minecraft_target": spec["target"], "lens_mm": camera.data.lens, "resolution": [scene.render.resolution_x, scene.render.resolution_y]})
    (args.output / "render-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    scene.camera = specs[names[0]][0]
    scene.view_settings.exposure = 0.3
    for lamp in interior_lights:
        lamp.hide_render = True
    if not args.no_blend:
        bpy.ops.wm.save_as_mainfile(filepath=str(args.output / "quartier-horloge.blend"))
    print(f"Completed {len(names)} views in {args.output}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        traceback.print_exc()
        sys.stdout.flush()
        sys.stderr.flush()
        if bpy.app.background:
            os._exit(1)
        raise
    if bpy.app.background:
        # This workstation's PipeWire teardown can hang after a completed render.
        # All PNG/JSON/.blend writes above are synchronous and already finished.
        sys.stdout.flush()
        sys.stderr.flush()
        os._exit(0)
