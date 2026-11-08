"""Extract declarative material data; no Mojang or Faithful PNGs are copied.

Usage: python3 web/tools/build-material-catalog.py CLIENT_JAR FAITHFUL_TREE_JSON COMMIT
FAITHFUL_TREE_JSON is GitHub's recursive tree response for the 1.21.1 branch.
COMMIT is the corresponding commit SHA (not an unpinned branch name).
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

client, tree_file, commit = sys.argv[1:]
assert re.fullmatch(r"[a-f0-9]{40}", commit), "Expected a full commit SHA"
tree = json.loads(Path(tree_file).read_text())
assert not tree.get("truncated"), "Incomplete Faithful resource tree"
models, blocks = {}, {}
with zipfile.ZipFile(client) as jar:
    for path in jar.namelist():
        if path.startswith("assets/minecraft/models/block/") and path.endswith(".json"):
            data = json.loads(jar.read(path))
            models["minecraft:" + path.split("/models/")[1][:-5]] = {
                key: value for key, value in data.items() if key in ("parent", "textures", "elements")
            }
        if path.startswith("assets/minecraft/blockstates/") and path.endswith(".json"):
            blocks["minecraft:" + path.split("/blockstates/")[1][:-5]] = json.loads(jar.read(path))
textures = {
    "minecraft:" + item["path"].split("/textures/")[1][:-4]:
    "https://raw.githubusercontent.com/Faithful-Resource-Pack/Faithful-64x-Java/" + commit + "/" + item["path"]
    for item in tree["tree"]
    if item["path"].startswith("assets/minecraft/textures/block/") and item["path"].endswith(".png")
}
result = {"version": "1.21.1", "sourceSha256": hashlib.sha256(Path(client).read_bytes()).hexdigest(),
          "faithfulCommit": commit, "models": models, "blocks": blocks, "textures": textures}
Path(__file__).resolve().parents[1].joinpath("src/data/minecraft-materials.json").write_text(
    json.dumps(result, separators=(",", ":")) + "\n"
)
print(f"{len(blocks)} blocks, {len(models)} models, {len(textures)} Faithful texture references")
