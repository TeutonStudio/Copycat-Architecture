#!/usr/bin/env python3
"""Draw measured horizontal sections from the exported native building meshes.

No design geometry is introduced here: the contours are intersections of scene.json
polygons with two horizontal planes. Requires Pillow and Shapely.
"""
from __future__ import annotations

import argparse
import html
import json
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from shapely.geometry import LineString, MultiPoint, box
from shapely.ops import polygonize, unary_union

COLORS = {
    "glass": "#9ac5cc", "granite": "#b8735b", "bricks": "#a7614e",
    "andesite": "#888c87", "stone": "#8b908a", "deepslate": "#41494c",
    "spruce": "#9b7850", "dark_oak": "#584b3c", "oak": "#ae9267",
    "iron": "#455459", "copper": "#a1714d", "quartz": "#d9d4c2",
    "terracotta": "#cfbea6", "grass": "#92a777",
}


def color(material: str) -> str:
    return next((value for key, value in COLORS.items() if key in material), "#92958b")


def slice_lines(mesh: dict, elevation: float):
    vertices = mesh["vertices"]
    if not vertices or min(p[1] for p in vertices) >= elevation or max(p[1] for p in vertices) <= elevation:
        return []
    lines = []
    for face in mesh["faces"]:
        points = []
        for index, first in enumerate(face):
            a, b = vertices[first], vertices[face[(index + 1) % len(face)]]
            if (a[1] < elevation < b[1]) or (b[1] < elevation < a[1]):
                t = (elevation - a[1]) / (b[1] - a[1])
                points.append((round(a[0] + t * (b[0] - a[0]), 6), round(a[2] + t * (b[2] - a[2]), 6)))
            elif abs(a[1] - elevation) < 1e-8:
                points.append((round(a[0], 6), round(a[2], 6)))
        points = list(dict.fromkeys(points))
        if len(points) == 2 and points[0] != points[1]:
            lines.append(LineString(points))
    return lines


def contours(scene: dict, elevation: float):
    segments = defaultdict(list)
    for mesh in scene["meshes"]:
        segments[mesh["material"]].extend(slice_lines(mesh, elevation))
    result = {}
    for material, lines in segments.items():
        result[material] = list(polygonize(unary_union(lines)))
    return result


def font(size: int, bold: bool = False):
    path = "/usr/share/fonts/truetype/dejavu/DejaVuSans" + ("-Bold" if bold else "") + ".ttf"
    if not Path(path).exists():
        path = "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans" + ("-Bold" if bold else "") + ".ttf"
    if not Path(path).exists():
        path = "/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf"
    result = ImageFont.truetype(path, size) if Path(path).exists() else ImageFont.load_default(size=size)
    if "[wght]" in path:
        result.set_variation_by_axes([700 if bold else 400])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scene", type=Path, nargs="?", default=Path("examples/commerce-quarter/scene.json"))
    parser.add_argument("--output", type=Path, default=Path("examples/commerce-quarter/views/grundrisse.png"))
    args = parser.parse_args()
    scene = json.loads(args.scene.read_text())
    width, height = 2000, 1200
    canvas = Image.new("RGB", (width, height), "#f3f0e8")
    draw = ImageDraw.Draw(canvas)
    draw.text((70, 40), "HANDELSHOF · GRÜNE GASSE", font=font(34, True), fill="#343f41")
    draw.text((70, 89), "Horizontale Schnitte aus dem kompilierten Gebäudegraphen · Maße in Minecraft-Blöcken", font=font(20), fill="#616b69")
    all_vertices = [v for m in scene["meshes"] for v in m["vertices"]]
    lo_x, hi_x = min(v[0] for v in all_vertices), max(v[0] for v in all_vertices)
    lo_z, hi_z = min(v[2] for v in all_vertices), max(v[2] for v in all_vertices)
    scale = min(790 / (hi_x - lo_x), 760 / (hi_z - lo_z))
    panels = [(0, 2.375, "ERDGESCHOSS", 72), (1, 8.375, "OBERGESCHOSS", 1068)]
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">', '<rect width="2000" height="1200" fill="#f3f0e8"/>',
           '<text x="70" y="76" font-family="sans-serif" font-size="34" fill="#343f41">HANDELSHOF · GRÜNE GASSE</text>',
           '<text x="70" y="112" font-family="sans-serif" font-size="20" fill="#616b69">Horizontale Schnitte aus dem kompilierten Gebäudegraphen · Maße in Minecraft-Blöcken</text>']
    for level, elevation, title, left in panels:
        top = 230
        draw.text((left, 170), title, font=font(24, True), fill="#343f41")
        svg.append(f'<text x="{left}" y="195" font-family="sans-serif" font-size="24" fill="#343f41">{title}</text>')

        def xy(point):
            return (left + (point[0] - lo_x) * scale, top + (point[1] - lo_z) * scale)

        # A quiet grid expresses the real block scale, rather than suggesting a
        # dimensionless artist's plan.
        for x in range(int(lo_x), int(hi_x) + 1):
            p, q = xy((x, lo_z)), xy((x, hi_z))
            draw.line((p, q), fill="#dddcd4", width=1)
        for z in range(int(lo_z), int(hi_z) + 1):
            p, q = xy((lo_x, z)), xy((hi_x, z))
            draw.line((p, q), fill="#dddcd4", width=1)

        found = contours(scene, elevation)
        for material, polygons in found.items():
            fill = color(material)
            for polygon in polygons:
                exterior = [xy(p) for p in polygon.exterior.coords]
                draw.polygon(exterior, fill=fill)
                draw.line(exterior, fill="#343f41", width=1)
                path = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in exterior) + " Z"
                for interior in polygon.interiors:
                    ring = [xy(p) for p in interior.coords]
                    draw.polygon(ring, fill="#f3f0e8")
                    path += " M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in ring) + " Z"
                svg.append(f'<path d="{path}" fill="{fill}" stroke="#343f41" stroke-width="0.6" fill-rule="evenodd"/>')

        # The native renderer exports the two visible glass faces and culls
        # interfaces against opaque frame members. Their open section lines
        # therefore need to be drawn as lines, rather than discarded for not
        # forming a separately watertight polygon.
        base = 1 + level * 6
        for mesh in scene["meshes"]:
            lines = slice_lines(mesh, elevation)
            fill = color(mesh["material"])
            if "glass" in mesh["material"] and lines:
                pane = MultiPoint([p for line in lines for p in line.coords]).convex_hull
                if pane.geom_type == "Polygon":
                    ring = [xy(p) for p in pane.exterior.coords]
                    draw.polygon(ring, fill=fill)
                    svg.append('<polygon points="' + ' '.join(f'{x:.2f},{y:.2f}' for x,y in ring) + f'" fill="{fill}"/>')
            for line in lines:
                p, q = [xy(v) for v in line.coords]
                draw.line((p, q), fill=fill, width=2)
                svg.append(f'<line x1="{p[0]:.2f}" y1="{p[1]:.2f}" x2="{q[0]:.2f}" y2="{q[1]:.2f}" stroke="{fill}" stroke-width="1.5"/>')
            vertices = mesh["vertices"]
            if (mesh["component"] in ("wall", "stairs", "landing") and vertices
                    and min(v[1] for v in vertices) >= base - .01
                    and max(v[1] for v in vertices) < elevation):
                # Draw the actual horizontal faces of low furniture below the
                # section. No bounding-box furniture is invented.
                for face in mesh["faces"]:
                    vs = [vertices[i] for i in face]
                    if len(vs) >= 3 and max(p[1] for p in vs) - min(p[1] for p in vs) < 1e-6:
                        coords = [xy((p[0], p[2])) for p in vs]
                        draw.polygon(coords, fill=fill)
                        draw.line(coords + coords[:1], fill="#343f41", width=1)
                        svg.append('<polygon points="' + ' '.join(f'{x:.2f},{y:.2f}' for x,y in coords) + f'" fill="{fill}" stroke="#343f41" stroke-width="1"/>')

        # The stair's actual reserved cells identify its connection even when
        # the selected horizontal plane lies above the landing.
        for points in scene.get("accessFootprints", {}).values():
            footprint = unary_union([box(p[0],p[2],p[0]+1,p[2]+1) for p in points])
            polygons = [footprint] if footprint.geom_type == "Polygon" else list(footprint.geoms)
            for polygon in polygons:
                ring = [xy(p) for p in polygon.exterior.coords]
                for a,b in zip(ring,ring[1:]):
                    length = ((b[0]-a[0])**2+(b[1]-a[1])**2)**.5
                    n = max(1,int(length/7))
                    for i in range(0,n,2):
                        u,v = i/n,min(i+1,n)/n
                        draw.line(((a[0]+u*(b[0]-a[0]),a[1]+u*(b[1]-a[1])),(a[0]+v*(b[0]-a[0]),a[1]+v*(b[1]-a[1]))),fill="#596965",width=2)
                svg.append('<polyline points="' + ' '.join(f'{x:.2f},{y:.2f}' for x,y in ring) + '" fill="none" stroke="#596965" stroke-width="2" stroke-dasharray="7 7"/>')
            center = footprint.centroid
            px,py = xy((center.x,center.y))
            draw.text((px,py),"U-Treppe",font=font(17,True),fill="#343f41",anchor="mm",stroke_width=3,stroke_fill="#f3f0e8")
            svg.append(f'<text x="{px:.2f}" y="{py:.2f}" text-anchor="middle" font-family="sans-serif" font-size="17" fill="#343f41">U-Treppe</text>')

        # Labels describe the model's two main usable wings. Furniture and inner
        # walls remain visible through the unmodified section geometry.
        labels = [(6, 7, "Gasthaus"), (15, 8, "Gasse"), (26, 3, "Handel")]
        if level == 1:
            labels = [(6, 7, "Dach Gasthaus"), (15, 8, "Luftraum"), (26, 3, "Wohnen / Atelier")]
        for x, z, text in labels:
            if lo_x <= x <= hi_x and lo_z <= z <= hi_z:
                px, py = xy((x, z))
                draw.text((px, py), text, font=font(19, True), fill="#343f41", anchor="mm", stroke_width=4, stroke_fill="#f3f0e8")
                svg.append(f'<text x="{px:.2f}" y="{py:.2f}" text-anchor="middle" font-family="sans-serif" font-size="19" fill="#343f41">{html.escape(text)}</text>')

        # Literal five-block scale bar.
        base = top + (hi_z - lo_z) * scale + 55
        draw.line((left, base, left + 5 * scale, base), fill="#343f41", width=4)
        for x in (left, left + 5 * scale):
            draw.line((x, base - 7, x, base + 7), fill="#343f41", width=3)
        draw.text((left + 2.5 * scale, base + 18), "5 Blöcke", font=font(19), fill="#343f41", anchor="mt")
        draw.text((left, base + 62), f"Schnitthöhe Y = {elevation:g}", font=font(17), fill="#616b69")
        svg.append(f'<path d="M {left},{base} h {5*scale}" fill="none" stroke="#343f41" stroke-width="4"/><text x="{left}" y="{base+36}" font-family="sans-serif" font-size="19" fill="#343f41">5 Blöcke</text>')
        svg.append(f'<text x="{left}" y="{base+80}" font-family="sans-serif" font-size="17" fill="#616b69">Schnitthöhe Y = {elevation:g}</text>')
    footer = "Niedrige Möbel projiziert · Glas blau · Treppenfläche gestrichelt · Rahmen und Steinprofile als Schnittkonturen"
    draw.text((70, 1130), footer, font=font(18), fill="#616b69")
    svg.append(f'<text x="70" y="1152" font-family="sans-serif" font-size="18" fill="#616b69">{footer}</text>')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(args.output)
    svg.append("</svg>")
    args.output.with_suffix(".svg").write_text("\n".join(svg) + "\n")
    print(args.output)


if __name__ == "__main__":
    main()
