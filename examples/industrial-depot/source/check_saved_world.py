#!/usr/bin/env python3
"""Audit the closed, real Minecraft depot save without changing any world file.

Uses Python's standard library to read SavedData NBT and Anvil region palettes /
block entities. No Minecraft, Gradle, world loader, or generated mesh is used.

Example (run from the repository root, after both client processes have ended):
  python3 examples/industrial-depot/source/check_saved_world.py \
    'build/industrial-depot-client/saves/Lichtwerk Industrie-Depot' \
    --create-log build/industrial-depot-client-create.log \
    --verify-log build/industrial-depot-client-verify.log

Use --compare-with a previous report to verify that the saved library, graph and
complete per-block ownership ledger have survived a separate process restart.
The report is written beside library.json by default, never inside the world.
"""
from __future__ import annotations

import argparse
import collections
import copy
import gzip
import hashlib
import io
import json
import struct
import zlib
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
FINGERPRINTS = ("architectDataSha256", "metadataSha256", "savedLibrarySha256",
                "savedGraphSha256", "ownershipLedgerSha256")


class Nbt:
    """Small reader for the standard NBT tag types used by Minecraft saves."""

    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def read(self, length):
        value = self.stream.read(length)
        if len(value) != length:
            raise ValueError("Truncated NBT")
        return value

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.read(struct.calcsize(">" + fmt)))[0]

    def string(self):
        return self.read(self.number("H")).decode("utf-8", errors="surrogatepass")

    def payload(self, kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.read(self.number("i"))
        if kind == 8:
            return self.string()
        if kind == 9:
            subtype = self.number("B")
            return [self.payload(subtype) for _ in range(self.number("i"))]
        if kind == 10:
            values = {}
            while True:
                subtype = self.number("B")
                if subtype == 0:
                    return values
                name = self.string()
                values[name] = self.payload(subtype)
        if kind in (11, 12):
            return [self.number("i" if kind == 11 else "q") for _ in range(self.number("i"))]
        raise ValueError(f"Unsupported NBT tag {kind}")

    def root(self):
        kind = self.number("B")
        self.string()  # Root name, normally empty.
        return self.payload(kind)


def nbt_file(path):
    return Nbt(gzip.decompress(path.read_bytes())).root()


def decode_json(value):
    return json.loads(value.decode("utf-8") if isinstance(value, bytes) else value)


def coords(value):
    return value["x"], value["y"], value["z"]


def contains_fields(actual, expected):
    """Compare all authored graph fields; runtime serialization adds defaults."""
    if isinstance(expected, dict):
        return isinstance(actual, dict) and all(
            key in actual and contains_fields(actual[key], value) for key, value in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and len(actual) == len(expected) and all(
            contains_fields(a, b) for a, b in zip(actual, expected))
    return actual == expected


def serialized_form(source):
    """Expand omitted Part defaults and Geometry.validate's canonical schema."""
    form = copy.deepcopy(source)
    defaults = dict(source="copycats:copycat_slice", turn=0, top=False, size=1,
                    offset=dict(x=0, y=0, z=0), tilt=0, group="body", cutMask="",
                    recipeSource="", spin=0, discCut=0)
    form["parts"] = [dict(copy.deepcopy(defaults), **part) for part in form["parts"]]
    form["schema"] = 4 if any(
        part.get("exact") is not None or part.get("exactClip") is not None or
        part["source"] == "copycat_architect:copycat_roof" for part in form["parts"]) else 3
    return form


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def digest_json(value):
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    return hashlib.sha256(encoded).hexdigest()


def saved_entities_and_states(world, positions):
    """Read only the Anvil chunks containing the table or ledger positions."""
    by_region = collections.defaultdict(lambda: collections.defaultdict(set))
    for position in positions:
        x, _, z = position
        index = ((z // 16) % 32) * 32 + (x // 16) % 32
        by_region[x // 512, z // 512][index].add(position)
    entities, states, chunk_count = {}, {}, 0
    for (rx, rz), chunks in sorted(by_region.items()):
        path = world / "region" / f"r.{rx}.{rz}.mca"
        with path.open("rb") as stream:
            header = stream.read(8192)
            for index, chunk_positions in sorted(chunks.items()):
                sector = int.from_bytes(header[index * 4:index * 4 + 3], "big")
                if not sector:
                    raise ValueError(f"Missing saved chunk {path.name}:{index}")
                stream.seek(sector * 4096)
                length = struct.unpack(">I", stream.read(4))[0]
                compression = stream.read(1)[0]
                data = stream.read(length - 1)
                if compression not in (1, 2, 3):
                    raise ValueError(f"Unsupported Anvil compression {compression} in {path}")
                data = {1: gzip.decompress, 2: zlib.decompress, 3: lambda v: v}[compression](data)
                chunk = Nbt(data).root()
                chunk = chunk.get("Level", chunk)
                chunk_count += 1
                for position in chunk_positions:
                    x, y, z = position
                    section = next(section for section in chunk["sections"] if section["Y"] == y // 16)
                    block_states = section["block_states"]
                    palette = block_states["palette"]
                    voxel = (y % 16) * 256 + (z % 16) * 16 + x % 16
                    bits = max(4, (len(palette) - 1).bit_length())
                    per_long = 64 // bits
                    selected = 0 if len(palette) == 1 else (
                        block_states["data"][voxel // per_long] >> ((voxel % per_long) * bits)
                    ) & ((1 << bits) - 1)
                    states[position] = palette[selected]["Name"]
                for entity in chunk.get("block_entities", chunk.get("TileEntities", [])):
                    entities[entity["x"], entity["y"], entity["z"]] = entity
    return entities, states, chunk_count


def audit(args):
    world = args.world.resolve()
    checks = []

    def require(name, passed, **detail):
        checks.append(dict(name=name, passed=bool(passed), **detail))
        if not passed:
            raise ValueError(f"Failed saved-world check: {name}")

    data_file = world / "data/copycat_architect.dat"
    metadata_file = world / "industrial-depot-test.json"
    data = decode_json(nbt_file(data_file)["data"]["architect"])
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    source = json.loads(args.expected.read_text(encoding="utf-8"))
    key = metadata["buildingKey"]
    table, origin = coords(metadata["table"]), coords(metadata["origin"])
    owners = [owner for owner, library in data["libraries"].items() if key in library["buildings"]]
    require("one_saved_library_owner", len(owners) == 1)
    owner = owners[0]
    library = data["libraries"][owner]
    building = library["buildings"][key]
    records = [record for record in data["tableBuildings"].values() if coords(record["table"]) == table]
    require("one_saved_table_record", len(records) == 1)
    record = records[0]
    require("table_record_identity", record["id"] == metadata["tableBuildingId"] and record["attached"]
            and record["owner"] == owner == metadata["owner"] and coords(record["origin"]) == origin
            and record["dimension"] == "minecraft:overworld" and record["turn"] == 0,
            buildingId=record["id"])
    require("table_and_library_building_identity", record["building"] == building)
    projection = coords(building["graph"]["projection"]["table"])
    require("table_projection_origin", tuple(a - b for a, b in zip(table, projection)) == origin
            and building["graph"]["projection"]["turn"] == 0)
    require("saved_source_forms", all(library["forms"].get(key) == serialized_form(form)
            for key, form in source["forms"].items()), depotForms=len(source["forms"]))
    source_building = next(value for value in source["buildings"].values() if value["id"] == building["id"])
    require("authored_graph_fields_and_palette", contains_fields(building["graph"]["nodes"],
            source_building["graph"]["nodes"]) and building["palette"] == source_building["palette"],
            graphNodes=len(building["graph"]["nodes"]))
    ledger = record["blocks"]
    require("complete_unique_block_ledger", len(ledger) == metadata["placements"] == 6662
            and len({coords(entry["pos"]) for entry in ledger}) == len(ledger))
    require("unique_nonempty_ownership_tokens", all(entry["token"] for entry in ledger)
            and len({entry["token"] for entry in ledger}) == len(ledger))
    positions = {coords(entry["pos"]) for entry in ledger}
    entities, states, chunks = saved_entities_and_states(world, positions | {table})
    require("actual_saved_architect_table", states[table] == "copycat_architect:blueprint_table"
            and entities[table]["buildingId"] == record["id"])
    require("actual_saved_copycat_states", all(states[position] == "copycat_architect:composite"
            for position in positions), blocks=len(positions), savedChunks=chunks)
    require("actual_saved_copycat_ownership", all(
        entities[coords(entry["pos"])]["buildingId"] == record["id"]
        and entities[coords(entry["pos"])]["buildingToken"] == entry["token"]
        and entities[coords(entry["pos"])]["owner"] == owner for entry in ledger))
    require("saved_copycat_geometry_and_material_fields", all(
        decode_json(entities[position]["parts"]) and decode_json(entities[position]["groups"])
        and isinstance(decode_json(entities[position]["surfaces"]), dict)
        and entities[position]["material"] for position in positions))

    report = dict(schema=1, status="PASS", method="Independent read-only SavedData NBT and Anvil palette/block-entity audit",
                  world=str(world), metadata=metadata, savedForms=len(library["forms"]),
                  depotForms=len(source["forms"]), graphNodes=len(building["graph"]["nodes"]),
                  ownedCopycats=len(ledger), savedRegionChunks=chunks, buildingId=record["id"],
                  architectDataSha256=sha256(data_file), metadataSha256=sha256(metadata_file),
                  sourceLibrarySha256=sha256(args.expected), checkerSha256=sha256(Path(__file__).resolve()),
                  savedLibrarySha256=digest_json(library), savedGraphSha256=digest_json(building["graph"]),
                  ownershipLedgerSha256=digest_json(sorted(ledger, key=lambda entry: coords(entry["pos"]))),
                  checks=checks)
    if args.compare_with:
        previous = json.loads(args.compare_with.read_text(encoding="utf-8"))
        require("restart_preserves_saved_data_library_graph_and_ownership", all(
            report[field] == previous[field] for field in FINGERPRINTS)
            and report["buildingId"] == previous["buildingId"])
        report["beforeRestartFingerprints"] = {field: previous[field] for field in FINGERPRINTS}
    # Newer clients serialize BuildingGraph.nodeGroups=[] even for older saved
    # graphs. Preserve the concrete size comparison separately from the strict
    # persisted-data fingerprints; an empty runtime default is never a waiver
    # for changed world data, geometry or ownership.
    current_defaults = copy.deepcopy(library)
    added_fields = []
    for library_key, value in current_defaults["buildings"].items():
        graph = value.get("graph")
        if graph is not None and "nodeGroups" not in graph:
            graph["nodeGroups"] = []
            added_fields.append(dict(path=f"buildings/{library_key}/graph/nodeGroups", value=[]))
    compact = lambda value: json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    report["empty_graph_groups_default_comparison"] = dict(
        persistedLibraryCharacters=len(compact(library)),
        currentDefaultSerializationCharacters=len(compact(current_defaults)),
        addedFields=added_fields)
    for label, path, marker in (
            ("create", args.create_log, "ARCHITECT INDUSTRIAL DEPOT CLIENT PASSED:"),
            ("reload", args.verify_log, "ARCHITECT INDUSTRIAL DEPOT RELOAD PASSED:")):
        if path:
            log = path.read_text(encoding="utf-8", errors="replace")
            require(f"{label}_client_process_completed", marker in log and "BUILD SUCCESSFUL" in log
                    and "ARCHITECT INDUSTRIAL DEPOT CLIENT FAILED" not in log)
            report.setdefault("runtimeLogs", {})[label] = dict(path=str(path.resolve()), sha256=sha256(path),
                milestones=[line for line in log.splitlines() if line.startswith("ARCHITECT INDUSTRIAL DEPOT")])
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("world", type=Path, help="Closed Minecraft world directory")
    parser.add_argument("--expected", type=Path, default=PACKAGE / "library.json")
    parser.add_argument("--report", type=Path, default=PACKAGE / "ingame-qa-report.json")
    parser.add_argument("--compare-with", type=Path, help="Read fingerprints from a previous audit report")
    parser.add_argument("--create-log", type=Path)
    parser.add_argument("--verify-log", type=Path)
    args = parser.parse_args()
    if args.report.resolve().is_relative_to(args.world.resolve()):
        parser.error("The audit report must be outside the world; this checker never changes world files")
    report = audit(args)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {len(report['checks'])} saved-world checks, {report['ownedCopycats']} real Copycats, "
          f"{report['depotForms']} saved depot forms, {report['graphNodes']} graph nodes")
    print(f"Table building ID: {report['buildingId']}")
    print(f"Report: {args.report.resolve()}")


if __name__ == "__main__":
    main()
