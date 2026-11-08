#!/usr/bin/env python3
"""Prepare the isolated Quartier de l'Horloge client world.

The local Cartridge Smoke world is copied once. Existing destination worlds,
the source save, normal development worlds and existing client options are
preserved. This helper prepares files only; it never starts Minecraft.
"""
from pathlib import Path
import gzip
import hashlib
import shutil
import struct
import tempfile


ROOT = Path(__file__).resolve().parents[1]
WORLD_NAME = "Quartier de l Horloge"
SOURCE = ROOT / "run/saves/Architect Cartridge Smoke"
GAME_DIRECTORY = ROOT / "build/quartier-client"
DESTINATION = GAME_DIRECTORY / "saves" / WORLD_NAME
LEVEL_NAME_TAG = b"\x08\x00\x09LevelName"


def level_name_span(data: bytes, path: Path) -> tuple[int, int]:
    """Locate the unique NBT string while preserving all other NBT bytes."""
    if data.count(LEVEL_NAME_TAG) != 1:
        raise SystemExit(f"Expected one NBT LevelName string in {path}")
    start = data.index(LEVEL_NAME_TAG) + len(LEVEL_NAME_TAG)
    if start + 2 > len(data):
        raise SystemExit(f"Truncated NBT LevelName in {path}")
    end = start + 2 + struct.unpack_from(">H", data, start)[0]
    if end > len(data):
        raise SystemExit(f"Truncated NBT LevelName value in {path}")
    return start, end


def read_level_name(path: Path) -> str:
    data = gzip.decompress(path.read_bytes())
    start, end = level_name_span(data, path)
    return data[start + 2:end].decode("utf-8")


def rename_copied_level(path: Path) -> None:
    data = gzip.decompress(path.read_bytes())
    start, end = level_name_span(data, path)
    name = WORLD_NAME.encode("utf-8")
    renamed = data[:start] + struct.pack(">H", len(name)) + name + data[end:]
    path.write_bytes(gzip.compress(renamed, mtime=0))


def main() -> None:
    saves = DESTINATION.parent
    saves.mkdir(parents=True, exist_ok=True)
    created = False
    if DESTINATION.exists():
        level = DESTINATION / "level.dat"
        if not level.is_file() or read_level_name(level) != WORLD_NAME:
            raise SystemExit(f"Existing destination is not the expected world; left unchanged: {DESTINATION}")
    else:
        source_level = SOURCE / "level.dat"
        if not source_level.is_file():
            raise SystemExit(f"Missing local test-world template: {SOURCE}")
        source_digest = hashlib.sha256(source_level.read_bytes()).digest()
        # Publish the destination only after the complete copied save is ready.
        with tempfile.TemporaryDirectory(prefix=".quartier-prepare-", dir=saves) as temporary:
            staged = Path(temporary) / WORLD_NAME
            shutil.copytree(SOURCE, staged, ignore=shutil.ignore_patterns("session.lock"))
            for filename in ("level.dat", "level.dat_old"):
                copied_level = staged / filename
                if copied_level.is_file():
                    rename_copied_level(copied_level)
            if hashlib.sha256(source_level.read_bytes()).digest() != source_digest:
                raise SystemExit("Source template changed while copying; no destination world published")
            staged.rename(DESTINATION)
        created = True

    config = GAME_DIRECTORY / "config"
    config.mkdir(parents=True, exist_ok=True)
    options = GAME_DIRECTORY / "options.txt"
    if not options.exists():
        candidates = [ROOT / "build/building-workspace-client/options.txt",
                      ROOT / "build/t-building-client/options.txt", ROOT / "run/options.txt"]
        template = next((path for path in candidates if path.is_file()), None)
        if template:
            shutil.copy2(template, options)
    fml = config / "fml.toml"
    if not fml.exists():
        fml.write_text("earlyWindowControl = false\n", encoding="utf-8")

    print(("Created: " if created else "Preserved existing world: ") + str(DESTINATION))
    print("Game directory: " + str(GAME_DIRECTORY))
    print("Source left unchanged: " + str(SOURCE))


if __name__ == "__main__":
    main()
