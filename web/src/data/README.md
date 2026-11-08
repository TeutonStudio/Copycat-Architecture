# Minecraft material metadata

`minecraft-materials.json` contains declarative blockstates and model information
extracted from the Minecraft Java 1.21.1 client already used by this project.
Its `sourceSha256` identifies that client archive. No Mojang texture files are
included. Rebuild with `web/tools/build-material-catalog.py`.

Minecraft texture previews are **Faithful 64x Java**, by **Faithful Resource Pack**:

- Website and credit: https://faithfulpack.net
- Resource repository: https://github.com/Faithful-Resource-Pack/Faithful-64x-Java/tree/1.21.1
- License: https://faithfulpack.net/license

The catalog pins each texture URL to the recorded commit. Textures load directly
from that repository when used; PNG files are not bundled or relicensed as part
of Graphstudio. Graphstudio is not an official Faithful or Mojang product.
