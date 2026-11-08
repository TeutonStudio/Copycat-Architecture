#!/usr/bin/env python3
"""Two schematic floor plans read directly from the saved building graph."""
import json
from html import escape
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCALE = 15
data = json.loads((ROOT / "building.json").read_text())
nodes = {n["id"]: n for n in data["graph"]["nodes"]}
building = next(n for n in nodes.values() if n["kind"] == "BUILDING")
colors = {"hall": "#d5a88d", "tower": "#b6bdc8", "house": "#ded4ba"}
svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="900" viewBox="0 0 1120 900">',
       '<rect width="1120" height="900" fill="#faf8f2"/>',
       '<g font-family="DejaVu Sans, sans-serif" fill="#263941">',
       '<text x="45" y="42" font-size="24">Quartier de l’Horloge · Grundrisse</text>',
       '<text x="45" y="69" font-size="12">Raumflächen aus building.json · 1 Rasterzelle = 1 Minecraft-Block · schematisch, ohne Wandprofile</text>']


def point(x, z, left, top):
    return left + (x + 3) * SCALE, top + (z + 6) * SCALE


def cells(room):
    geometry = nodes.get(room.get("geometry", ""), room)
    if geometry.get("coordinates"):
        return {(p.get("x", 0), p.get("z", 0)) for p in geometry["coordinates"]}
    return {(x, z) for r in geometry.get("rectangles", [])
            for x in range(r.get("x", 0), r.get("x", 0) + r["width"])
            for z in range(r.get("z", 0), r.get("z", 0) + r["length"])}


for panel, level in enumerate(["0", "1"]):
    left, top = 45 + panel * 545, 120
    svg.append(f'<text x="{left}" y="105" font-size="18">{"Erdgeschoss" if level == "0" else "Obergeschoss / Galerie"}</text>')
    for x in range(-3, 31):
        a, _ = point(x, -6, left, top)
        svg.append(f'<path d="M{a},{top}v705" stroke="#e6e3d9" stroke-width="0.6"/>')
    for z in range(-6, 42):
        _, a = point(-3, z, left, top)
        svg.append(f'<path d="M{left},{a}h495" stroke="#e6e3d9" stroke-width="0.6"/>')
    storey = nodes[building["levels"][level]]
    for index, room_id in enumerate(storey.get("inputs", [])):
        room = nodes[room_id]
        local = cells(room)
        if not local:
            continue
        offset = storey.get("offsets", [])[index]
        ox, oz = offset.get("x", 0), offset.get("z", 0)
        kind = "tower" if "tower" in room_id else "house" if "dwelling" in room_id else "hall"
        is_void = "nave" in room_id or "void" in room_id
        for x, z in sorted(local):
            a, b = point(x + ox, z + oz, left, top)
            fill = "#f1eee4" if is_void else colors[kind]
            svg.append(f'<rect x="{a}" y="{b}" width="15" height="15" fill="{fill}"/>')
            for dx, dz, path in [(0,-1,f'M{a},{b}h15'),(1,0,f'M{a+15},{b}v15'),(0,1,f'M{a},{b+15}h15'),(-1,0,f'M{a},{b}v15')]:
                if (x + dx, z + dz) not in local:
                    svg.append(f'<path d="{path}" stroke="#52616a" stroke-width="1.2"/>')
        lo_x, hi_x = min(x for x,z in local), max(x for x,z in local)
        lo_z, hi_z = min(z for x,z in local), max(z for x,z in local)
        cx, cy = point((lo_x+hi_x+1)/2 + ox, (lo_z+hi_z+1)/2 + oz, left, top)
        label = "Luftraum" if is_void else "Uhrturm" if kind == "tower" else "Wohnhaus" if kind == "house" else "Restaurant" if level == "0" else "Galerie"
        svg.append(f'<text x="{cx}" y="{cy}" text-anchor="middle" font-size="12">{escape(label)}</text>')
    for index, access in enumerate(building.get("accessInstances", [])):
        pos = access["position"]
        a, b = point(pos.get("x", 0)+0.5, pos.get("z", 0)+0.5, left, top)
        svg.append(f'<circle cx="{a}" cy="{b}" r="9" fill="#9a5942"/><text x="{a}" y="{b+4}" text-anchor="middle" font-size="10" fill="white">{index+1}</text>')
    a, b = point(12.5, 31, left, top)
    svg.append(f'<text x="{a}" y="{b}" text-anchor="middle" font-size="12" transform="rotate(90 {a} {b})">Hof</text>')
svg.extend(['<text x="45" y="866" font-size="12">1–4: Bezugspunkte der vier Treppeninstanzen. Treppenläufe, Geländer und Profile sind in den 3D-Planansichten sichtbar.</text>', '</g></svg>'])
output = ROOT / "views" / "floorplans.svg"
output.write_text("\n".join(svg) + "\n")
print(output)
