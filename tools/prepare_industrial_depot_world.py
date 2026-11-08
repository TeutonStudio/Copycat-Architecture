#!/usr/bin/env python3
"""Prepare an isolated copy for the opt-in real architect-table demonstration."""
from pathlib import Path
import gzip
import shutil
import struct

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "run/saves/Architect Cartridge Smoke"
DESTINATION = ROOT / "build/industrial-depot-client/saves/Lichtwerk Industrie-Depot"

if not DESTINATION.exists():
    if not (SOURCE / "level.dat").is_file():
        raise SystemExit(f"Missing local template: {SOURCE}")
    shutil.copytree(SOURCE, DESTINATION, ignore=shutil.ignore_patterns("session.lock"))
    for filename in ("level.dat", "level.dat_old"):
        target = DESTINATION / filename
        if not target.exists():
            continue
        data = gzip.decompress(target.read_bytes())
        marker = b"\x08\x00\x09LevelName"
        if data.count(marker) != 1:
            raise SystemExit(f"Expected one NBT LevelName in {target}")
        start = data.index(marker) + len(marker)
        old_length = struct.unpack_from(">H", data, start)[0]
        name = "Lichtwerk Industrie-Depot".encode("utf-8")
        data = data[:start] + struct.pack(">H", len(name)) + name + data[start + 2 + old_length:]
        target.write_bytes(gzip.compress(data))

game_directory = DESTINATION.parent.parent
(game_directory / "config").mkdir(parents=True, exist_ok=True)
options = ROOT / "build/t-building-client/options.txt"
if options.exists() and not (game_directory / "options.txt").exists():
    shutil.copy2(options, game_directory / "options.txt")
fml = game_directory / "config/fml.toml"
if not fml.exists():
    fml.write_text("earlyWindowControl = false\n")
print(DESTINATION)
