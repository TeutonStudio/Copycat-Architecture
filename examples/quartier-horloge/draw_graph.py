#!/usr/bin/env python3
"""Draw the actual saved building graph without altering its source."""
import argparse
import json
from pathlib import Path
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parent
GROUPS = {
    "COPYCAT": ("01 · Copycat-Formen", "#dfcfb7"),
    "AREA": ("02 · Raster und Geometrie", "#dae5db"),
    "ROOM_GEOMETRY": ("02 · Raster und Geometrie", "#dae5db"),
    "SEGMENT_LAYOUT": ("02 · Raster und Geometrie", "#dae5db"),
    "SEGMENT": ("03 · Bauteile", "#e3c1ae"),
    "PATTERN": ("03 · Bauteile", "#e3c1ae"),
    "FLOOR": ("03 · Bauteile", "#e3c1ae"),
    "CEILING": ("03 · Bauteile", "#e3c1ae"),
    "ROOM": ("04 · Räume", "#b8cfda"),
    "STOREY": ("05 · Geschosse", "#bdc6e0"),
    "ROOF_LEVEL": ("06 · Dach und Erschließung", "#d9c7de"),
    "ROOF_LAYOUT": ("06 · Dach und Erschließung", "#d9c7de"),
    "ROOF": ("06 · Dach und Erschließung", "#d9c7de"),
    "STAIRWELL": ("06 · Dach und Erschließung", "#d9c7de"),
    "BUILDING": ("07 · Ergebnis", "#95b9a4"),
    "GARDEN": ("06 · Außenraum", "#d1ddbc"),
    "PATH": ("06 · Außenraum", "#d1ddbc"),
}


def quote(value):
    return json.dumps(str(value), ensure_ascii=False)


def references(node):
    for index, source in enumerate(node.get("inputs", [])):
        yield source, str(index + 1)
    for key in ("geometry", "layout", "floor", "ceiling", "corner", "roof"):
        yield node.get(key, ""), key
    for key, source in node.get("walls", {}).items():
        yield source, "Wand " + key
    for key, source in node.get("levels", {}).items():
        yield source, "Geschoss " + key
    for instance in node.get("accessInstances", []):
        yield instance.get("source", ""), "Treppe"
    for key in ("tread", "landing", "enclosure"):
        yield (node.get("stairwell") or {}).get(key, ""), key
    yield (node.get("roofLevel") or {}).get("area", ""), "Fläche"
    config = node.get("roofConfig") or {}
    yield config.get("wall", ""), "Giebel"
    for source in config.get("variants", {}).values():
        yield source, "Dachform"
    for family in config.get("families", []):
        for source in family.get("variants", {}).values():
            yield source, "Dachform"


def draw(path):
    building = json.loads(path.read_text())
    nodes = building.get("graph", {}).get("nodes", [])
    if not nodes:
        raise ValueError(f"Kein Gebäudegraph in {path}")
    ids = {n["id"] for n in nodes}
    lines = ["digraph Building {", "rankdir=LR; bgcolor=\"#faf8f2\"; pad=0.4; nodesep=0.24; ranksep=0.8;", 
             "graph [fontname=\"DejaVu Sans\",label=" + quote(building.get("name", path.stem)) + ",labelloc=t,fontsize=24];",
             "node [shape=box,style=\"rounded,filled\",fontname=\"DejaVu Sans\",fontsize=10,color=\"#52616a\",margin=0.13];",
             "edge [fontname=\"DejaVu Sans\",fontsize=7,color=\"#82919b80\",arrowsize=0.6];"]
    stages = {}
    for node in nodes:
        stage, color = GROUPS.get(node["kind"], ("06 · Weitere Bauteile", "#dddddd"))
        stages.setdefault(stage, []).append((node, color))
    for index, (stage, items) in enumerate(sorted(stages.items())):
        lines.append(f"subgraph cluster_{index} {{ label={quote(stage)}; color=\"#cfd5d5\"; style=rounded;")
        for node, color in items:
            title = "\n".join(textwrap.wrap(node.get("name") or node["id"], 30))
            label = title + "\n" + node["kind"]
            lines.append(f"{quote(node['id'])} [label={quote(label)},fillcolor={quote(color)}];")
        lines.append("}")
    for node in nodes:
        done = set()
        for source, label in references(node):
            if source in ids and source != node["id"] and source not in done:
                done.add(source)
                lines.append(f"{quote(source)} -> {quote(node['id'])} [tooltip={quote(label)}];")
    lines.append("}")
    output = ROOT / "views" / (path.stem + "-graph")
    output.parent.mkdir(exist_ok=True)
    output.with_suffix(".dot").write_text("\n".join(lines) + "\n")
    subprocess.run(["dot", "-Tsvg", str(output.with_suffix(".dot")), "-o", str(output.with_suffix(".svg"))], check=True)
    print(output.with_suffix(".svg"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("buildings", nargs="*", type=Path)
    args = parser.parse_args()
    for source in args.buildings or [ROOT / "building.json"]:
        draw(source)
