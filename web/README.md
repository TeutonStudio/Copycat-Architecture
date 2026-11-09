# Copycat Architect Graphstudio 0.4

Browsereditor für Form- und Gebäudegraphen. Die Beispieldateien stammen aus
[`examples/`](../examples). Die beiden Editoren verwalten getrennte Entwürfe.

## Start

Benötigt werden Node.js 22.12 oder neuer, Java 21, Bash und curl.

```sh
cd web
npm ci
npm test
npm run dev
```

`npm run build` erzeugt die statische Website in `web/dist`. Der Kotlin-Compiler
und dessen Laufzeit werden in der gepinnten Version aus Maven Central geholt.
Die generierten Dateien gehören zum Build.
Der Build legt die Projektlizenz, die npm-Lizenzen sowie Lizenz und Herkunft
der eingebundenen Kotlin-Standardbibliothek im Ausgabeverzeichnis ab.

Der Workflow prüft außerdem mit `./gradlew test` die Minecraft-Seite der
gemeinsamen Domänenlogik. Der Browser wird aus dem Fachkern desselben
Repository-Stands gebaut. Seine Oberfläche gibt nur Knoten und Einstellungen
frei, die der Browser verlustfrei lesen, bearbeiten und exportieren kann.

## Austausch mit Minecraft

Form- und Gebäudedokumente verwenden die JSON-Felder des Mods. React Flow
übersetzt Verbindungen über die gemeinsame Kotlin-Domänenlogik in diese Felder.
`GraphModels.kt` definiert die Knotentypen, Graphgrenzen und Anschlüsse beider
Editoren. Die Gebäudeanschlüsse ergeben sich aus dem Grundriss, den Eingaben
und den Geschossen. `DiagramEdits` wendet Verbindungsänderungen für
beide Oberflächen an.

Der Web Worker führt `GraphCompiler` und `BuildingGraphCompiler` aus. Die
3D-Ansicht zeigt die berechneten Rasterformen mit Materialfarben oder
Texturvorschauen. Minecraft-Spezialrenderer werden nicht nachgebildet.
Die Bibliothek lässt sich als JSON mit `forms` und `modules` öffnen.
Für ältere Gebäudedokumente wird ein in Minecraft erzeugter Gebäudegraph benötigt.

Formexporte enthalten auch das frisch berechnete `parts`-Ergebnis. Bei Gebäuden
können unvollständige Entwürfe exportiert werden. Zum Bauen müssen die Referenzen
auflösbar und der Gebäudeplan gültig sein. Die Minecraft-Zwischenablage
akzeptiert Graphdokumente bis 28.000 Zeichen.

## GitHub Pages

In den Repository-Einstellungen unter **Pages → Source** die Option
**GitHub Actions** wählen. Der Workflow **Graphstudio** baut den Editor und
veröffentlicht erfolgreiche Builds von `master`. Die Wiki-Seite
**Graphstudio** verlinkt die Website und erläutert den Dateiaustausch.

## Vorschau und Orientierung

**Graph** wertet das endgültige Ergebnis aus, **Selektiert** den angeklickten Knoten. Die gemeinsame Domänenlogik stellt dafür `GraphCompiler.intermediate` und `BuildingGraphCompiler.Evaluation.preview` bereit. Boden/Decke verwenden einen verbundenen Grundriss, ohne andere Raumzweige auszuwerten; das Dach benötigt den Gebäudekontext. Rotationswerte werden als typisierte Werte angezeigt.

Im Formmodus bleibt der Referenzrahmen eine volle Blockzelle, mit 16 hB je Achse. X/Y/Z entsprechen den Minecraft-Weltachsen. Die Seitenleiste teilt eine feste Gesamthöhe mit dem Graphen, solange mindestens einer ihrer Bereiche offen ist. Beide Splitter unterstützen Pointer- und Tastaturbedienung.

Ausrichtungs-Enums übersetzen weiterhin in das vorhandene Quellformat. Nicht unterstützte native Neigungen bleiben deaktiviert; importierte Werte werden beim bloßen Anzeigen nicht umgeschrieben. Raumversätze werden als hB im bisherigen Ganzblockraster angezeigt und beim Export zurückgerechnet. Die neuen Bedienelemente erweitern nicht still den unterstützten Knoten-/Schemabereich.

Die vollständige deutsche [Knotenreferenz](../docs/wiki/Knotenreferenz.md) dokumentiert den gemeinsamen Knotenvertrag einschließlich zusätzlicher nativer Funktionen; jede Knotenbeschreibung kennzeichnet ihre Browserverfügbarkeit.

## Integration mit dem nativen Mod ab 0.6.0

Der Browser kompiliert den vollständigen aktuellen Fachkern. Der Knoten- und
Anschlussvertrag wird aus derselben `graph-models.json` wie im Mod geladen;
Browseradapter ersetzen JVM-Caches, Bitsets und Ganzzahlarithmetik.

Die Browseroberfläche unterstützt zehn Form- und einundzwanzig
Gebäudeknotentypen; der gemeinsame Katalog enthält insgesamt 32 Gebäudetypen.
Erweiterte native Graphen, Gruppen, exakte Geometrien und
nicht unterstützte Knoteneinstellungen werden beim Import und vor dem Export
mit einem Hinweis auf den nativen Editor abgewiesen. Dadurch können sie nicht
unbemerkt vereinfacht oder mit fehlenden Einstellungen gespeichert werden.

## Material and personal libraries

Graphstudio includes a declarative Minecraft 1.21.1 blockstate/model catalog and loads **Faithful 64x Java** texture previews directly from the pinned 1.21.1 repository commit. Credit: [Faithful Resource Pack](https://faithfulpack.net); [license](https://faithfulpack.net/license). No PNG textures are bundled. See `src/data/README.md` for provenance and the catalog rebuild tool.

Public GitHub mod repositories can be linked with a branch/tag/commit and optional resource root. Resource paths, Fabric/Forge/NeoForge metadata, model inheritance, texture aliases, state variants and multipart conditions are read as data; no mod/build code runs. Missing model/texture dependencies are reported and can be provided by linking another repository. GitHub limits and repositories without checked-in generated assets are explicit error cases. Texture previews approximate surfaces on Copycat geometry; custom renderers, CTM, biome tint, exact model UV rotation and animation are not a Minecraft rendering implementation.

IndexedDB holds current drafts, named graphs (including loaded copycat libraries and preview assignments), reusable selected-node templates and pinned mod links. Resources are cached separately. Saves are ordered, failures are visible, and optimistic revisions prevent older tabs from overwriting newer changes. Backup JSON exports/imports workspaces without changing the native Minecraft JSON schema. Group material/state assignments are browser-preview metadata; native form exports retain their existing role-based material contract, and building palettes export block IDs.

[German usage guide](../docs/wiki/Materialbibliothek.md).

## Gespeicherte Copycats und Gebäudeoperationen

**Copycat auswählen** öffnet in der Werkzeugleiste und an den Formquellen
`INPUT` und `SAVED` sowie der Gebäudequelle `COPYCAT` den Auswahldialog.
**Grundformen** enthält 19 Quellen einschließlich Dachvorlage;
**Gespeicherte Formen** enthält persönliche Formgraphen und geladene
Bibliotheksformen mit Herkunft oder Versionsschlüssel. Suchbare Karten zeigen
die berechnete Geometrie; die größere Vorschau lässt sich drehen und zoomen.
**Auswahl übernehmen** ersetzt die ausgewählte Quelle oder erzeugt einen neuen
Quellknoten, wenn keine Quelle ausgewählt ist. **Abbrechen** und Escape ändern
nichts. Der Wechsel zwischen `INPUT` und `SAVED` erhält Knoten-ID und Verbindungen.
Gespeicherte Geometrie und Materialgruppen werden übernommen. Zusätzliche
Drehwerte am Quellknoten werden zurückgesetzt; nachgeschaltete Transformationen
bleiben erhalten.

Im Formgraphen mit **Graph speichern** einen persönlichen Eintrag anlegen.
Im Gebäudegraphen verwendet die Auswahl einer Bibliotheksform ihren genauen
Versionsschlüssel; persönliche Formgraphen und Grundformen werden als feste
Version in die Referenzbibliothek des Gebäudeentwurfs aufgenommen.
Materialzuordnungen übernehmen wie die Gebäudepalette die reine Block-ID.
**Formdatei laden** im Inspektor oder **Copycat laden** in der Werkzeugleiste
importiert weiterhin eine einzelne Form-JSON.

Benannte Browsergraphen und Graphstudio-Sicherungen enthalten die geladene
Referenzbibliothek. Der normale Minecraft-Gebäudeexport enthält nur die
Formreferenzen; deren Versionen müssen im Spiel vorhanden sein.

Alle sechs Operationen sind im Gebäudegraphen für beide Wertfamilien vorhanden:

| Operation | Copycat | Segment |
| --- | --- | --- |
| Rotieren | `COPYCAT_ROTATE` | `SEGMENT_ROTATE` |
| Spiegeln | `COPYCAT_MIRROR` | `SEGMENT_MIRROR` |
| Vereinigung | `COPYCAT_UNION` | `SEGMENT_UNION` |
| Schnitt | `COPYCAT_INTERSECTION` | `SEGMENT_INTERSECTION` |
| Differenz A − B | `COPYCAT_DIFFERENCE` | `SEGMENT_DIFFERENCE` |
| Symmetrische Differenz | `COPYCAT_SYMMETRIC_DIFFERENCE` | `SEGMENT_SYMMETRIC_DIFFERENCE` |

Rotation und Spiegelung bieten im Browser X/Y/Z; Segmentspiegelung kann
wahlweise nur Positionen oder Positionen samt Zellgeometrie spiegeln.
Die booleschen Knoten akzeptieren 2–16 geordnete Eingänge, Differenz genau zwei.
Copycat-Ergebnisse bleiben mit Copycat-Eingängen verbindbar, Segmentergebnisse
mit Segmenteingängen. Leere Zwischenergebnisse bleiben verwendbar.

Die gemeinsame Auswertung schneidet Copycat-Teilformen je Blockposition.
Teilumwandlungen normaler Vollblöcke und semantische Segmentmetadaten werden
ausdrücklich abgewiesen. Für die acht neuen booleschen Operator-IDs ist beim
Spielimport ein aktualisierter Mod-Build erforderlich; Modrelease 0.6.0
enthält sie noch nicht.

## 2D-Anordnungsraster

Das 2D-Raster im Inspektor ordnet Segmente am Wandmuster (X/Y, X/Z, Z/Y) und
Rauminstanzen am Geschoss (X/Z) an. Ziehen und Pfeiltasten verwenden ganze
Blöcke. Segmentinstanzen besitzen zusätzlich Drehachse und Vierteldrehung.
Übernehmen prüft das Ergebnis und erzeugt einen gemeinsamen Undo-Schritt;
Verwerfen lässt das Dokument unverändert. Native `transforms`, `patternAxis`
und `offsets` bleiben beim JSON-Austausch erhalten.


Der gesamte hier beschriebene Stand ist auf diesem Entwicklungszweig implementiert, aber noch nicht in master integriert oder live bereitgestellt.
