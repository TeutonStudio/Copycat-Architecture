# Dreigeschossiges T-Testgebäude

Dieser konkrete Entwurf hat auf allen drei Geschossen denselben **T-Grundriss**: einen 15 × 5 Blöcke großen Querflügel und einen mittig anschließenden, 5 × 10 Blöcke großen Stamm. Das sind **125 freie Innenzellen pro Grundriss**, bei fünf Blöcken Raumhöhe insgesamt **1.875 bebaubare Innenzellen**. Die Wandhülle misst außen höchstens 17 × 17 Blöcke; die beiden seitlichen Rücksprünge bleiben offen. Das Haus besitzt ein Erdgeschoss mit Eingang und zwei Obergeschosse.

```text
XXXXXXXXXXXXXXX
XXXXXXXXXXXXXXX
XXXXXXXXXXXXXXX
XXXXXXXXXXXXXXX
XXXXXXXXXXXXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
     XXXXX
```

Die Fenster sitzen auf allen vier Fassadenrichtungen und auf jedem Geschoss. Sie bestehen aus einer 2/16 dicken Glasschicht mit dunklem Eichenholzrahmen; hinter der Scheibe liegt keine undurchsichtige Wand. Die Fassadenbuchten sind fünf Blöcke breit: Steinziegelsockel mit Quarzband, senkrechte Quarzpilaster, zwei Fensterreihen, ein abwechselnd aus Ziegeln und weißer Terrakotta gesetzter Fries mit **erhabenen 8 × 8/16-Quarzquadraten** und ein oberes Gesims. Eigene Quarzleisten schließen die senkrechten Außenecken. Eichenholzböden und ein dunkles Tiefenschiefer-Flachdach ergänzen die acht Oberflächenmaterialien.

Der geprüfte Plan umfasst **1.902 eindeutige Copycat-Positionen**, darunter **214 Fensterzellen**, und benötigt **286.608 mB Zink**. Die drei freien Zellen des Eingangs kommen zu den 1.875 freien Innenraumzellen hinzu.

| Oberfläche | Items |
| --- | ---: |
| Eichenholzbretter | 756 |
| Quarzblock | 743 |
| Steinziegel | 305 |
| Weiße Terrakotta | 246 |
| Glas | 214 |
| Schwarzeichenholzbretter | 214 |
| Tiefenschieferfliesen | 189 |
| Ziegel | 102 |

## Fertiges Gebäude öffnen

Der gebaute und nach einem vollständigen Neustart geprüfte Spielstand liegt unter `run/saves/Architekt T-Testhaus`. Mit dem normalen Entwicklungsclient öffnen:

```sh
./gradlew runClient --offline
```

Im Hauptmenü **Einzelspieler → Architekt T-Testhaus** auswählen. Das Gebäude beginnt bei **X 56, Y 81, Z 56**. Der echte **Architektentisch bei X 63, Y 81, Z 75** steht vor dem Eingang. Seine Bibliothek enthält für den Entwicklungs-Spieler den im Spiel gespeicherten Entwurf **`t_house@2`** mit dem Namen **„T-Haus mit drei Stockwerken und Relief-Fassade“**. Dort lassen sich Gebäudegraph, T-Grundriss und Copycat-Formen öffnen und weiterbearbeiten. Der Tischbezug des gespeicherten Entwurfs stimmt mit diesem fertigen Bau überein.

Eine vollständige Kopie des Spielstands liegt außerdem in `build/exports/Architekt-T-Testhaus.zip`. Zum Übernehmen in eine andere Minecraft-Installation den enthaltenen Ordner `Architekt T-Testhaus` in deren `saves`-Ordner entpacken und die in der [Projekt-README](../../README.md) aufgeführten Modversionen verwenden. Das gebaute Haus ist Bestandteil der Welt; die gespeicherte Entwurfsbibliothek gehört zum Entwicklungs-Spieler.

Ansichten des tatsächlichen Spielstands: [Gebäude](../../docs/release-0.6.0/images/t-building-exterior.png), [T-Dachkontur von oben](../../docs/release-0.6.0/images/t-building-top.png), [Fassade mit Fenstern und Relief](../../docs/release-0.6.0/images/t-building-facade.png), [gespeicherter Gebäudegraph](../../docs/release-0.6.0/images/t-building-graph.png) und [T-Grundriss am Architektentisch](../../docs/release-0.6.0/images/t-building-floorplan.png).

## Dateien und Verwendung

- `building.json` ist der vollständige Gebäudegraph mit eigenständigen Raster-, Segment-, Raum-, Geschoss- und Dachquellen. Dieser reproduzierbare Quellexport bleibt bei **Version 1**; die bereits gebaute Welt enthält zusätzlich die über den echten Speichern-Knopf erzeugte **Version 2** mit ihrem Tischbezug.
- `forms/` enthält die acht Copycat-Formen. Ihre Namen verwenden die gemeinsame Auswahlhierarchie `T-Haus/Fassade/…`, `T-Haus/Fenster/…` beziehungsweise `T-Haus/Bauteile/…`.
- `library.json` bündelt Gebäude und Formen mit festen Versionsreferenzen. Eigene gespeicherte Wandmodule sind nicht erforderlich: Die Muster sind im Graphen sichtbar und bearbeitbar.
- `material-bill.json` enthält die aus dem fertigen Druckplan ermittelten Blockpositionen, Zinkmenge in mB und Oberflächenitems.

Der bestehende Editor importiert einzelne Entwürfe und vergibt dabei neue IDs. Für einen manuellen Import zuerst die Formen aus `forms/` laden und speichern, dann `building.json` importieren und in dessen Copycat-Knoten die neu gespeicherten Formen über ihre Namen auswählen. `library.json` dient als reproduzierbares Gesamtpaket für Prüfungen; es ist kein Ein-Klick-Bibliotheksimport. Der Drucker und der Copycat-Reiter des Architektentischs verwenden dieselbe gespeicherte Formenbibliothek.

## Reproduzieren und prüfen

Die Quelle des Beispiels ist `TBuildingExample`. Seine Anforderungsprüfungen kontrollieren die tatsächliche T-Kontur einschließlich beider Rücksprünge, alle drei Geschosse, die freien Innenzellen, durchsichtige Fenster auf jeder Fassadenrichtung, geometrisch erhabene Muster, Eckleisten, Materialbedarf, überschneidungsfreie Platzierungen und den vollständigen JSON-Import.

```sh
./gradlew test --tests '*TBuildingExampleTest' --offline
```

Die JSON-Dateien werden nur in einem ausdrücklich angeforderten Schreibdurchlauf neu erzeugt. Normale Testläufe prüfen zusätzlich, dass die Exporte zur aktuellen Quelle passen:

```sh
ARCHITECT_WRITE_T_BUILDING_EXAMPLE=true ./gradlew test --tests '*TBuildingExampleTest' --rerun-tasks --offline
```

Für einen erneuten automatisierten Aufbau bereitet `python3 tools/prepare_t_building_world.py` eine separate Kopie der lokalen Testweltvorlage vor. `./gradlew runClient -PtBuildingSmoke -I tools/t-building-validation.init.gradle --offline` erstellt dort das Testgebäude neu. `./gradlew runClient -PtBuildingVerify -I tools/t-building-validation.init.gradle --offline` lädt diesen getrennten Spielstand, prüft alle 1.902 realen Blöcke, Materialien, freien Innenzellen und den gespeicherten Entwurf und beendet den Client anschließend. Der abschließende Prüflauf bestätigte außerdem das Speichern des großen Graphen über die echte Client-Server-Verbindung. Die sechs Beispieltests und alle 44 Server-GameTests bestanden.

Das Bauwerk ist ein Test der Geometrie und Materialien. Der Eingang ist eine freie Öffnung; Fenster sind zusammengesetzte Copycat-Geometrie. Treppen und funktionale Türen sind nicht Teil dieses Entwurfs.

Der historische Entwurf verwendet ausdrücklich `joinWallEnds=false`, damit seine dokumentierten freien Innenflächen und 1.902 Baupositionen erhalten bleiben. Neue explizite Räume und bestehende Räume ohne diese Vorgabe verbinden ihre Wandsegmente über gemeinsame Endreihen.
