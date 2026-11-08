#!/usr/bin/env python3
"""Export the verified, actual Minecraft world and its in-game screenshots."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "Quartier de l Horloge"
WORLD = ROOT / "build/quartier-client/saves" / NAME
PACKAGE = ROOT / "examples/quartier-horloge"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verified-log", type=Path, default=ROOT / "build/quartier-reload.log")
    args = parser.parse_args()
    log = args.verified_log.read_text(errors="replace")
    if "ARCHITECT QUARTIER RELOAD PASSED:" not in log:
        raise SystemExit("A successful fresh-load verification is required before export")
    fixture = json.loads((WORLD / "quartier-test.json").read_text())
    source_sha = hashlib.sha256((PACKAGE / "library.json").read_bytes()).hexdigest()
    if fixture["sourceSha256"] != source_sha:
        raise SystemExit("The verified world's source differs from the current source library")

    destination = ROOT / "run/saves" / NAME
    if not destination.exists():
        shutil.copytree(WORLD, destination, ignore=shutil.ignore_patterns("session.lock"))
    elif not (destination / "quartier-test.json").exists():
        raise SystemExit(f"Preserving an existing unrelated world: {destination}")

    images = PACKAGE / "views/game"
    images.mkdir(parents=True, exist_ok=True)
    screenshots = ROOT / "build/quartier-client/screenshots"
    tags = ["exterior-front", "exterior-rear", "courtyard", "interior-hall", "interior-gallery",
            "interior-tower-stairs", "table-graph", "table-build", "table-bound-graph",
            "table-floorplan-ground", "table-floorplan-gallery", "reloaded-world", "reloaded-table"]
    exported = []
    for tag in tags:
        candidates = list(screenshots.glob(f"architect-quartier-{tag}*.png"))
        if not candidates:
            raise SystemExit(f"Missing actual Minecraft screenshot: {tag}")
        source = max(candidates, key=lambda p: p.stat().st_mtime_ns)
        target = images / f"{tag}.png"
        shutil.copy2(source, target)
        exported.append(str(target.relative_to(PACKAGE)))

    archive = ROOT / "build/exports/Quartier-de-l-Horloge.zip"
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for source in sorted(WORLD.rglob("*")):
            if source.is_file() and source.name != "session.lock":
                zf.write(source, Path(NAME) / source.relative_to(WORLD))
    report = {
        "validation": "Actual Minecraft client: real table Save @1 and Creative Build; all world blocks, clear cells and table ownership verified after fresh client/server load",
        "world": str(destination),
        "archive": str(archive),
        "fixture": fixture,
        "screenshots": exported,
    }
    (PACKAGE / "game-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
