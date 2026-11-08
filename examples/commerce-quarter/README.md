# Handelshof an der grünen Gasse

**Im Spiel umgesetzt:** Der Entwurf wurde am echten Architektentisch als `commerce_quarter@2` gespeichert und über dessen Aktion **„Kreativ bauen“** mit 4.289 Blöcken errichtet. Ein vollständiger Neustart bestätigte Gebäude, Materialien, Tischbindung und gespeicherten Graphen.

Der fertige lokale Spielstand heißt **`Handelshof Gruene Gasse`** und liegt unter `run/saves/`. Der Architektentisch steht bei **X 71, Y 80, Z 50**, das Gebäude beginnt bei **X 56, Y 80, Z 56**. Öffnen mit `./gradlew runClient -PcommerceQuarterOpen --offline`.

**[Echte Minecraft-Aufnahmen, Spielstand und Prüfbelege](IN_GAME.md)** · [Spielstand als ZIP](../../build/exports/Handelshof-Gruene-Gasse-Minecraft.zip)

Die aktuellen Außen- und Innenaufnahmen liegen in `in-game/`. Die weiter unten dokumentierten Blender-Dateien waren die ursprüngliche Entwurfsdarstellung; für das fertige Ergebnis gelten die Spielaufnahmen und der gebundene Architektentisch.

Eigenständiger Weiterentwurf von [„Quartier avec commerce“ von Kfc_dilleur](https://createmod.com/de/schematics/quartier-avec-commerce). Die Vorlage verbindet Gasthaus, Handel und Wohnen in einem industriell geprägten Quartier. Der neue Entwurf übernimmt die Materialfamilie aus grauem Stein, rötlichen Fassaden, dunklem Holz und dunklen Dächern. Er wird aus einem nativen Gebäudegraphen erzeugt.

## Entwurfsentscheidungen

- **Zwei unterschiedlich hohe Flügel:** links Gasthaus und kleiner Handel, rechts im Erdgeschoss Handel und Werkstatt und darüber ein Wohnatelier. Die Staffelung erhält den Charakter eines gewachsenen Quartiers.
- **Fünf Blöcke lichte Gasse:** die beiden Flügel besitzen eigene Zugänge von der Straße und von der Gasse. Der hintere Bereich öffnet sich zu einem begrünten Hof.
- **Feinere Fassade:** 3/16 dicke Wandpanels, echte dünne Glasscheiben, dunkle Holzrahmen und schmale Gesimse ersetzen grobe Vollblockprofile. Hinter den Scheiben steht keine undurchsichtige Wand.
- **Eigene Portalgeometrie:** die zwei Blöcke breiten Eingänge besitzen facettierte Rundbogenköpfe aus exakten konvexen Teilvolumen. Die Rundung ist polygonal; unter dem Bogen bleiben zwei volle Blöcke Durchgangshöhe.
- **Durchgehende Dachflächen:** die Satteldächer verwenden eine Neigung von 2:3 und einen Überstand von 6/16 Block. Zwei hohe Schornsteine setzen die industriellen Akzente.
- **Innenräume aus demselben Plan:** Tische, Hocker, Regale und die Liegebank sind echte Graphgeometrie. Eine native U-Treppe erschließt das Obergeschoss des rechten Flügels.

Die beiden Wohn- und Nutzgeschosse besitzen fünf Blöcke Raumhöhe. Die zusätzliche technische Graph-Ebene enthält ausschließlich die beiden unbewohnten Kaminschächte; sie ist kein drittes Wohngeschoss. Portale sind offene Durchgänge und die Schornsteine statische Geometrie.

## Geprüftes Modell

Der Kernbau misst **33 × 24 Blöcke**. Mit Dachüberständen, Straßenpflaster und Hof beträgt die tatsächliche Geometrieausdehnung **33,75 × 21,125 × 29 Blöcke** (X × Y × Z). Der Druckplan belegt **4.289 eindeutige Positionen**. Die fünf gezielten Tests prüfen Türen und Wege, die Treppenerschließung, die durchsichtigen Fenster, eindeutige Platzierungen und Materialmengen sowie den vollständigen JSON-Reimport. Alle fünf Prüfungen bestanden.

Die Raumkonturen werden direkt in den jeweiligen `ROOM`-Knoten über ihre Rechtecke definiert. Möbelpositionen sind dort ausgespart und über die inneren Wandanschlüsse mit Einrichtungsmodulen besetzt; darunter bleiben tragende Geschossplatten erhalten. Die zusätzlichen `AREA`-Knoten zeigen die Konturen zur Orientierung, steuern diese direkt definierten Räume aber nicht automatisch. Die Treppe ist offen und besitzt keine Brüstung.

| Oberfläche | Items |
| --- | ---: |
| Fichtenholzbretter | 1.452 |
| Tiefenschieferfliesen | 1.141 |
| Schwarzeichenholzbretter | 849 |
| Hellgraue Terrakotta | 464 |
| Polierter Andesit | 424 |
| Ziegel | 384 |
| Glas | 374 |
| Steinziegel | 363 |
| Tiefenschieferziegel | 130 |
| Moosblöcke | 43 |

Die gedruckten Copycat-Positionen benötigen zusätzlich **541.666 mB Zink**. Die Mengen stammen aus dem tatsächlichen Druckplan; pro Copycat-Position zählen auch tatsächlich verwendete unterschiedliche Oberflächenmaterialien.

## Dateien

- `building.json`: Gebäudeentwurf mit bearbeitbarem Gebäudegraphen.
- `forms/`: referenzierte Copycat-Formen.
- `modules/`: Wand-, Fenster- und Einrichtungsraster mit festen Formreferenzen.
- `library.json`: reproduzierbares Paket mit festen Versionsreferenzen.
- `material-bill.json`: Materialbedarf des tatsächlich kompilierten Druckplans.
- `scene.json`: Materialpolygone des kompilierten Plans in Minecraft-Koordinaten; Y ist die Höhe.
- `views/`: Außen-, Innen- und Schnittansichten desselben Modells.

| Ansicht | Datei |
| --- | --- |
| Straße und Fassaden | [exterior-street.png](views/exterior-street.png) |
| Hof und Rückseiten | [exterior-courtyard.png](views/exterior-courtyard.png) |
| Gasthaus innen | [interior-inn.png](views/interior-inn.png) |
| Wohnatelier innen | [interior-atelier.png](views/interior-atelier.png) |
| Schnittaxonometrie | [section-axon.png](views/section-axon.png) |
| Grundrisse aus echten Horizontalschnitten | [grundrisse.png](views/grundrisse.png) |

## Import

Der Editor importiert einzelne Entwürfe und vergibt dabei neue IDs. Zuerst die Formen aus `forms/` importieren und speichern. Dann die Wandmodule aus `modules/` importieren, ihre Formreferenzen auf die neu gespeicherten Formen setzen und die Module speichern. Anschließend `building.json` importieren und die Modul- beziehungsweise Copycat-Referenzen im Graphen über ihre Namen neu zuweisen. `library.json` ist ein reproduzierbares Prüf- und Archivpaket; der Editor bietet dafür derzeit keinen Ein-Klick-Gesamtimport.

## Ansichten

Die fünf Perspektiven werden in Blender mit 1.800 × 1.125 Pixeln aus `scene.json` gerendert. Sie sind Modellvisualisierungen, keine Minecraft-Screenshots. Gebäudegeometrie und Materialzuordnung stammen aus dem kompilierten Graphen. Beleuchtung, Straßenumfeld und entfernte Bäume dienen der Darstellung und liegen in einer getrennten Inszenierungskollektion. Für die Schnittansicht wird eine Kopie der Geometrie gekappt und Dach, oberste Decken und Giebel werden ausgeblendet; der gespeicherte Entwurf bleibt vollständig. Die Grundrisse schneiden dieselben Materialpolygone bei Y = 2,375 und Y = 8,375; niedrige Möbel werden projiziert und die tatsächliche Treppenfläche wird gestrichelt eingezeichnet.

## Reproduzieren

Die native Quelle liegt in `CommerceQuarterExample.kt`; `CommerceQuarterExampleTest.kt` prüft und exportiert den Entwurf. Der Renderer liegt in `tools/render_commerce_quarter.py`.

```sh
./gradlew test --tests '*CommerceQuarterExampleTest' --offline
```

Java 21 muss für den Gradle-Wrapper aktiv sein. Die Exporte werden nur bei einem ausdrücklich angeforderten Schreibdurchlauf neu erstellt:

```sh
ARCHITECT_WRITE_COMMERCE_QUARTER_EXAMPLE=true ./gradlew test --tests '*CommerceQuarterExampleTest' --rerun-tasks --offline
python3 tools/render_commerce_quarter.py --blend
python3 tools/draw_commerce_quarter_plans.py
```

Der Renderer benötigt Blender. Er verwendet, falls vorhanden, die originalen Pixeltexturen aus dem lokal gecachten Minecraft-1.21.1-Client-JAR; deren Rechte liegen bei Mojang/Microsoft. Die Blender-Datei wird unter `build/exports/commerce-quarter.blend` gespeichert. `views/render-manifest.json` protokolliert Kameras, Auflösung, Renderparameter und Herkunft. Für die Grundrisse werden Pillow und Shapely benötigt.
