#!/usr/bin/env python3
"""Independently check the exported Lichtwerk mesh using only Python's stdlib.

Run from any working directory: python3 /path/to/source/check_mesh.py
The deterministic result is saved as ../qa-report.json. This checker reads the
actual mesh faces; it neither rebuilds nor alters the architecture. Its window
queries resolve both explicit segment layouts and reused PATTERN bay sources.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EPS = 1e-6


def read_json(path):
    return json.loads(path.read_text(encoding="utf8"))


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_mesh_sha256(objects):
    """Record face geometry independent of object order and polygon start index."""
    faces = []
    for obj in objects:
        vertices = obj["vertices"]
        for face in obj["faces"]:
            points = [tuple(vertices[i]) for i in face]
            rotations = [points[i:]+points[:i] for i in range(len(points))]
            faces.append((obj.get("material", ""), obj.get("component", ""), min(rotations)))
    return hashlib.sha256(json.dumps(sorted(faces), separators=(",", ":")).encode()).hexdigest()


class AxisRays:
    """Intersect axis-parallel rays with triangulated exported polygon faces.

    The two-dimensional spatial bins are only a broad phase. Barycentric
    coordinates determine intersections with the original triangle surfaces.
    No block occupancy or information from the graph validator is substituted.
    """

    def __init__(self, triangles, axis):
        self.axis = axis
        self.uv = [i for i in range(3) if i != axis]
        self.bins = collections.defaultdict(list)
        u, v = self.uv
        for triangle in triangles:
            a, b, c = triangle[:3]
            denominator = (b[v]-c[v])*(a[u]-c[u]) + (c[u]-b[u])*(a[v]-c[v])
            if abs(denominator) < 1e-9:
                continue
            for i in range(math.floor(min(a[u], b[u], c[u])), math.floor(max(a[u], b[u], c[u]))+1):
                for j in range(math.floor(min(a[v], b[v], c[v])), math.floor(max(a[v], b[v], c[v]))+1):
                    self.bins[i, j].append((triangle, denominator))

    def hits(self, p, q):
        axis = self.axis
        u, v = self.uv
        result = []
        for triangle, denominator in self.bins.get((math.floor(p), math.floor(q)), []):
            a, b, c = triangle[:3]
            w0 = ((b[v]-c[v])*(p-c[u]) + (c[u]-b[u])*(q-c[v])) / denominator
            w1 = ((c[v]-a[v])*(p-c[u]) + (a[u]-c[u])*(q-c[v])) / denominator
            w2 = 1-w0-w1
            if min(w0, w1, w2) < -1e-8:
                continue
            position = a[axis]*w0 + b[axis]*w1 + c[axis]*w2
            result.append((round(position, 6), *triangle[3:]))
        return sorted(set(result))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "qa-report.json")
    args = parser.parse_args()
    mesh_path = ROOT / "render-mesh.json"
    mesh = read_json(mesh_path)
    building = read_json(ROOT / "building.json")
    library = read_json(ROOT / "library.json")
    bill = read_json(ROOT / "material-bill.json")
    nodes = {n["id"]: n for n in building["graph"]["nodes"]}
    objects = mesh["objects"]
    canonical_sha = canonical_mesh_sha256(objects)
    triangles = []
    for obj in objects:
        vertices = obj["vertices"]
        for face in obj["faces"]:
            for k in range(1, len(face)-1):
                triangles.append((vertices[face[0]], vertices[face[k]], vertices[face[k+1]],
                                  obj.get("material", ""), obj.get("component", "")))
    x_rays, y_rays, z_rays = [AxisRays(triangles, axis) for axis in range(3)]
    checks = []

    def check(name, passed, **detail):
        checks.append(dict(name=name, passed=bool(passed), **detail))

    check("library_building_identity", building == library["buildings"][building["id"]+"@1"])
    check("library_forms_identity", all(read_json(ROOT / "forms" / (form["id"]+".json")) == form
                                         for form in library["forms"].values()), forms=len(library["forms"]))
    check("export_metadata", len(nodes) == bill["buildingNodes"] and len(library["forms"]) == bill["forms"]
          and sum(len(o["faces"]) for o in objects) == bill["exactMeshFaces"] and mesh["bounds"] == bill["bounds"])

    # The side elevations use two 2-block end piers around four reused 6-block
    # bays. Geometry is checked below, independently of this graph structure.
    pattern_sources = {key: nodes[key]["inputs"] for key in ["hall_36", "hall_28"]}
    pattern_lengths = {key: (nodes[nodes[key]["layout"]]["length"] if "layout" in nodes[key]
                             else nodes[key]["length"]) for key in ["bay_6", "end_pier"]}
    check("compact_graph_patterns", nodes["hall_36"]["kind"] == "PATTERN"
          and pattern_sources["hall_36"] == ["bay_6"]*6 and nodes["hall_28"]["kind"] == "PATTERN"
          and pattern_sources["hall_28"] == ["end_pier"]+["bay_6"]*4+["end_pier"]
          and pattern_lengths == {"bay_6": 6, "end_pier": 2},
          nodes=len(nodes), patterns=pattern_sources, source_lengths=pattern_lengths)
    check("mesh_export_digest_matches_bill", sha256(mesh_path) == bill["meshSha256"],
          mesh_sha256=sha256(mesh_path), material_bill_mesh_sha256=bill["meshSha256"])

    missing, intermediate = [], []
    for x in range(36):
        for z in range(28):
            for dx, dz in [(.25, .25), (.25, .75), (.75, .25), (.75, .75)]:
                hits = [h for h in y_rays.hits(x+dx, z+dz) if h[0] > 3]
                if not hits:
                    missing.append((x+dx, z+dz))
                elif hits[0][0] < 12:
                    intermediate.append((x+dx, z+dz, hits[0]))
    check("hall_roof_coverage_and_open_volume", not missing and not intermediate, rays=4032,
          missing=missing[:10], intermediate_surfaces=intermediate[:10])

    glass_failures = []
    for z in [12.25, 21.25]:
        for x in [4.25, 8.25, 18.25, 24.25, 31.25]:
            hits = [h for h in y_rays.hits(x, z) if h[0] > 3]
            if not hits or any(h[1] != "minecraft:glass" for h in hits):
                glass_failures.append((x, z, hits))
    check("both_rooflight_bands_are_glass", not glass_failures, rays=10, failures=glass_failures[:10])

    gable_missing, count = [], 0
    for x4 in range(1, 144, 2):
        x = x4/4
        underside = 12+min(x+1, 37-x)/4
        for iy in range(1, math.floor((underside-12)*4)):
            y = 12+iy/4
            hits = z_rays.hits(x, y)
            for lower, upper in [(-1, 0), (28, 29)]:
                count += 1
                if not any(lower-EPS <= h[0] <= upper+EPS and h[1] == "minecraft:bricks"
                           and h[2] in ["gable", "roof"] for h in hits):
                    gable_missing.append((x, y, lower, hits))
    check("gable_closure", not gable_missing, rays=count, failures=gable_missing[:10])

    def segment_cells(node_id):
        node = nodes[node_id]
        if node["kind"] == "PATTERN":
            cursor, cells = 0, []
            for child in node["inputs"]:
                length, child_cells = segment_cells(child)
                cells.extend((x+cursor, y, source) for x, y, source in child_cells)
                cursor += length
            return cursor, cells
        # Direct-form end-pier segments contain no assigned window cells.
        if "layout" not in node:
            return node["length"], []
        layout = nodes[node["layout"]]
        return layout["length"], [(c["x"], c["y"], node["inputs"][c["sourceIndex"]])
                                  for c in layout["layoutCells"]]

    window_count, window_failures = 0, []
    for base, room in [(1, "hall_ground"), (7, "hall_upper"), (1, "office_ground"), (7, "gallery_upper")]:
        node = nodes[room]
        width = 36 if room.startswith("hall") else 10
        depth, offset_x = 28, 0 if room.startswith("hall") else 38
        for direction in range(4):
            edge = next(k for k in node["walls"] if k.startswith(str(direction)+":"))
            _, cells = segment_cells(node["walls"][edge])
            for sx, sy, source in cells:
                if source != "window":
                    continue
                y = base+sy+.25  # Avoid the actual 1/16 steel frame strips.
                if direction == 0:
                    hits, lo, hi = z_rays.hits(offset_x+sx+.25, y), -1, 0
                elif direction == 1:
                    hits, lo, hi = x_rays.hits(y, sx+.25), offset_x+width, offset_x+width+1
                elif direction == 2:
                    hits, lo, hi = z_rays.hits(offset_x+width-sx-.25, y), depth, depth+1
                else:
                    hits, lo, hi = x_rays.hits(y, depth-sx-.25), offset_x-1, offset_x
                hits = [h for h in hits if lo-EPS <= h[0] <= hi+EPS]
                window_count += 1
                if not hits or any(h[1] != "minecraft:glass" for h in hits):
                    window_failures.append((room, direction, sx, sy, hits))
    check("windows_have_no_opaque_backing", not window_failures, rays=window_count, failures=window_failures[:10])

    passage_failures = []
    for z in [12.25, 12.75, 13.25, 13.75, 14.25, 14.75, 15.25, 15.75]:
        for y in [1.5, 2.5, 3.5, 4.5]:
            hits = [h for h in x_rays.hits(y, z) if 36-EPS <= h[0] <= 38+EPS]
            if hits:
                passage_failures.append((y, z, hits))
    check("hall_office_passage_clear", not passage_failures, rays=32, failures=passage_failures[:10])

    bridge = {str(x): any(abs(h[0]-7) < EPS for h in y_rays.hits(x, 18.25))
              for x in [36.25, 36.75, 37.25, 37.5, 38.25]}
    check("gallery_shells_bridge_at_level_7", all(bridge.values()), walking_surface_samples=bridge)

    route = [(42, 8.5, 1), (42, 9.25, 1.5), (42, 9.75, 2), (42, 10.25, 2.5), (42, 10.75, 3),
             (42, 11.25, 3.5), (42, 11.75, 4), (42, 12.5, 4), (43.5, 12.5, 4), (43.5, 11.75, 4.5),
             (43.5, 11.25, 5), (43.5, 10.75, 5.5), (43.5, 10.25, 6), (43.5, 9.75, 6.5),
             (43.5, 9.25, 7), (43.5, 8.5, 7), (44.5, 8.5, 7), (45.5, 8.5, 7)]
    route_failures = []
    for x, z, top in route:
        hits = y_rays.hits(x, z)
        if not any(abs(h[0]-top) < EPS for h in hits):
            route_failures.append((x, z, "walking surface", top, hits))
        obstruction = [h for h in hits if top+EPS < h[0] < top+2.7-EPS]
        if obstruction:
            route_failures.append((x, z, "headroom", obstruction))
    check("stair_route_and_headroom", not route_failures, samples=len(route), minimum_headroom=2.7,
          failures=route_failures[:10])

    entrance_failures = []
    for x in [42.25, 42.75, 43.25, 43.75]:
        for y in [1.5, 2.5, 3.5]:
            hits = [h for h in z_rays.hits(x, y) if 28-EPS <= h[0] <= 29+EPS]
            if hits:
                entrance_failures.append((x, y, hits))
    check("personnel_entrance_clear", not entrance_failures, rays=12, failures=entrance_failures[:10])

    step_failures = []
    for x in [42.25, 43.25]:
        for z, top in [(29.75, .5), (29.25, 1), (28.5, 1)]:
            hits = y_rays.hits(x, z)
            if not any(abs(h[0]-top) < EPS for h in hits):
                step_failures.append((x, z, top, hits))
    check("personnel_entrance_half_steps", not step_failures, samples=6, failures=step_failures[:10])
    check("gallery_camera_clearance", not any(7+EPS < h[0] < 10.5 for h in y_rays.hits(39.5, 22)),
          gallery_walking_level=7, camera_eye=[39.5, 8.7, 22], roof_underside=12)

    status = "PASS" if all(c["passed"] for c in checks) else "FAIL"
    report = dict(schema=1, status=status, method="Independent axis-ray / triangle intersections with exported mesh faces",
                  mesh="render-mesh.json", meshSha256=sha256(mesh_path), buildingSha256=sha256(ROOT / "building.json"),
                  canonicalMeshSha256=canonical_sha,
                  librarySha256=sha256(ROOT / "library.json"), generatorSha256=sha256(ROOT / "source/generate.py"),
                  checkerSha256=sha256(Path(__file__).resolve()), graphNodes=len(nodes), forms=len(library["forms"]),
                  placements=bill["positions"], meshObjects=len(objects), meshFaces=sum(len(o["faces"]) for o in objects),
                  triangles=len(triangles), bounds=mesh["bounds"], checks=checks)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf8")
    print(f"{status}: {len(checks)} independent mesh checks; {len(nodes)} graph nodes, {len(triangles)} triangles")
    print(f"Mesh SHA-256: {report['meshSha256']}")
    print(f"Report: {args.report}")
    for item in checks:
        if not item["passed"]:
            print(json.dumps(item, ensure_ascii=False))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
