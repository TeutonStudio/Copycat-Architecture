# Lichtwerk — Industrie-Depot am Architektentisch

Ein neuer Entwurf mit den Gebäudegraphen von Copycat Architect, ausgehend von [Sadvelys Industrial Depot (Factory)](https://createmod.com/de/schematics/industrial-depot-factory). Die Formen und der Gebäudegraph sind im Spiel gespeichert. Das Gebäude wurde über die echte **Speichern- und Kreativbauen-Schaltfläche des Architektentischs** aufgebaut; sämtliche Copycat-Blöcke besitzen dessen dauerhafte Gebäudezuordnung.

## Die Spielwelt öffnen

Die fertige Welt heißt **Lichtwerk Industrie-Depot**. Im normalen Entwicklungsclient öffnen:

```sh
./gradlew runClient --offline
```

Dann **Einzelspieler → Lichtwerk Industrie-Depot** auswählen. Der Architektentisch steht bei **X 310, Y 74, Z 268**, das Gebäude beginnt bei **X 256, Y 74, Z 256**. Der Spawnpunkt liegt beim Tisch. Die Entwurfsbibliothek gehört dem Entwicklungs-Spieler des lokalen Clients. Dort ist der Entwurf **„Lichtwerk - Industrie-Depot mit offener Galerie“** mit 69 Graphknoten und 14 Copycat-Formen gespeichert und bearbeitbar.

Die Welt liegt unter `run/saves/Lichtwerk Industrie-Depot`. Das zusätzliche Weltarchiv `build/exports/Lichtwerk-Spielwelt.zip` lässt sich in das `saves`-Verzeichnis einer passenden Minecraft-Installation entpacken. Es benötigt die in der Projekt-README angegebenen Modversionen; der Spieltest verwendet Minecraft 1.21.1 und die aktuelle Mod aus diesem Projekt.

## Echte Spielansichten

- [Außen: Vorderseite und Ladezone](views/exterior-front.png)
- [Außen: Rückseite und Seitenflügel](views/exterior-rear.png)
- [Detail: überdachte Ladezone](views/exterior-loading.png)
- [Innen: hohe Werkhalle](views/interior-hall.png)
- [Innen: Blick von der Galerie](views/interior-gallery.png)
- [Gespeicherter Gebäudegraph am gebundenen Architektentisch](views/table-graph.png)
- [Grundriss am Architektentisch](views/table-floorplan.png)

Alle Ansichten sind unveränderte Minecraft-Screenshots aus der tatsächlich gebauten Welt.

## Entwurf

Der Hauptbau einschließlich Ladezone misst **50 × 37 Blöcke**; mit der südlichen Eingangsstufe beträgt die Gesamtausdehnung **50 × 38 × 16,875 Blöcke**. Eine **36 × 28 Blöcke große, elf Blöcke hohe Halle** bildet den Mittelpunkt. Östlich liegt ein zweigeschossiger Seitenflügel mit offener Galerie und U-Treppe. Eine niedrigere überdachte Ladezone nimmt die Anlieferung auf. Ein eigener Personeneingang mit Trittstufe liegt an der Südseite.

Rötliches Mauerwerk, dunkle Dächer und profilierte Pfeiler greifen den Industriecharakter der Vorlage auf. Die Graphen ergänzen durchgehende Dachflächen mit exakter Neigung **1:4**, zwei echte Glaslichtbänder, schlanke Sprossen und flache Segmentbögen. Die Bogen-, Rahmen- und Gesimsformen werden innerhalb der Blockzellen präzise geschnitten. Glas, Metall und Stein sind eigene Materialbereiche der tatsächlich gedruckten Geometrie. Die hohe Halle bleibt für eine spätere Create-Anlage verfügbar.

Der Plan umfasst **6.662 eindeutige Copycat-Positionen** und benötigt **942.500 mB Zink**. Oberflächenitems pro Position:

| Oberfläche | Items |
| --- | ---: |
| Ziegel | 1.830 |
| Tiefenschieferfliesen | 1.730 |
| Glas | 896 |
| Polierter Andesit | 1.450 |
| Polierter Schwarzstein | 980 |
| Glatter Stein | 2.386 |

## Quellen und Prüfung

`building.json` und `forms/` enthalten den reproduzierbaren Quellexport. `library.json` bündelt dessen feste Versionsreferenzen für Prüfungen; es ist kein Ein-Klick-Bibliotheksimport. In der fertigen Spielwelt sind diese Einträge bereits am Architektentisch verfügbar.

Der separate Demonstrationslauf wird nur ausdrücklich gestartet und arbeitet ausschließlich mit einer Kopie der lokalen Testweltvorlage:

```sh
python3 tools/prepare_industrial_depot_world.py
./gradlew -I tools/industrial-depot-validation.init.gradle -PindustrialDepotSmoke runClient --offline
./gradlew -I tools/industrial-depot-validation.init.gradle -PindustrialDepotVerify runClient --offline
```

Der erste Lauf importiert die 14 Formen über die Spielverbindung, speichert den Graphen am echten Tisch und betätigt dessen Kreativbau. Er kontrolliert alle 6.662 gebauten Blöcke, Materialien, 15.416 freien Zellen sowie die vollständige Tischzuordnung mit eindeutigen Blocktokens. Der zweite Lauf startet Minecraft und den integrierten Server vollständig neu und prüft die gespeicherte Welt, Bibliothek und Tischbindung erneut. Er baut nichts neu und erzeugt keine zusätzliche Entwurfsversion. Die ursprünglichen Testwelten bleiben erhalten.

Die reinen Graphprüfungen lassen sich zusätzlich ausführen:

```sh
python3 examples/industrial-depot/source/generate.py
bash examples/industrial-depot/source/validate.sh
python3 examples/industrial-depot/source/check_mesh.py
```

`material-bill.json`, `validation-report.txt` und `qa-report.json` dokumentieren den Quellexport und dessen unabhängige Geometrieprüfung. `render-mesh.json` ist ein numerischer Polygonexport für diese Prüfung. Die tatsächlichen Spielnachweise stehen in `ingame-validation-report.txt` und `ingame-qa-report.json`.

Für einen Import in eine andere bestehende Welt können die Formen einzeln importiert und gespeichert werden. Weil der Editor dabei neue IDs vergibt, hilft `source/rebind_forms.py`: Es ordnet exportierte gespeicherte Formen anhand ihrer vollständigen „Lichtwerk/…“-Namen neu zu und aktualisiert auch die direkten Formverweise der Glasdachfamilie. Danach das erzeugte Gebäude über **Dokument → Importieren** laden und speichern. Der fertige Spielstand erspart diesen manuellen Schritt.
