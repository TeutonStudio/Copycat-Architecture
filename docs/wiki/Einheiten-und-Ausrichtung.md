# Einheiten und Ausrichtung

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.2** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

## hB und Minecraft-Achsen

**1 hB (Hexadezimalblock) = 1/16 Minecraft-Block.** Damit sind 8 hB ein halber und 16 hB ein ganzer Block. Die Zahlen werden dezimal geschrieben; `16 hB` bedeutet eine Blocklänge, nicht die Hexadezimalzahl `0x16`.

| Achse | Positiv | Negativ |
| --- | --- | --- |
| X | Osten | Westen |
| Y | Oben | Unten |
| Z | Süden | Norden |

Eine Kopie bleibt innerhalb einer Blockzelle. hB bezeichnet eine Länge; Zink wird in mB beziehungsweise in den Einheiten des jeweiligen Rechenkerns angezeigt. Ein Volumen ist dreidimensional: ein Vollblock umfasst 16³ hB³.

## Drehung und Neigung im Browser

| Einstellung | Werte | Bedeutung |
| --- | --- | --- |
| Drehung um Y | Nord, Ost, Süd, West | Vier Himmelsrichtungen um die vertikale Minecraft-Achse. Das ältere Speicherformat verwendet Süd=0, West=1, Nord=2, Ost=3. |
| Neigung | Oben (+Y), Unten (−Y), Seitlich +X, Seitlich −X, Seitlich +Z, Seitlich −Z | Richtet die lokale Normale der Quelle an der ausgewählten Weltachse aus. |
| Drehung um die lokale Normale | 0°, 90°, 180°, 270° | Dreht das Profil auf seiner montierten Fläche. |
| Rotationsachse eines Operators | X, Y, Z, lokale Normale | Wählt die geometrische Drehachse des verbundenen Zweigs. |
| Drehwinkel | 0°, 90°, 180°, 270° | Ein diskreter Vierteldrehungswert. |

Nicht jede native Quelle des älteren Spielkerns besitzt alle Montagezustände. Unzulässige seitliche Neigungen sind bei diesen Quellen ausgegraut; die Form wird nicht still durch einen anderen Typ ersetzt. Beispielsweise unterstützt die Stufe alle sechs Normalen, eine native Treppe im Browserkern nur oben/unten. Eine Änderung der Y-Drehung dreht auch eine zuvor seitlich montierte Form; entsprechend ändert sich ihre Welt-Normale.

Die aktuelle lokale Minecraft-Oberfläche kann dieselben gespeicherten Winkel noch als Gradwerte anbieten. Die neuen beschrifteten Browser-Enums ändern keine bereits gespeicherten Zahlenwerte beim bloßen Öffnen.

## Versatz und Raster

| Feld | Anzeige / Bedeutung | Gespeichertes Format |
| --- | --- | --- |
| `part.offset.x/y/z` | Copycat-Versatz in hB, −15 bis +15; zusätzlich muss die gesamte Form innerhalb der Blockgrenze bleiben | Ganzzahl in Sechzehnteln |
| Raumversatz im Browser | hB, derzeit **16-hB-Raster**; die ältere Raumplatzierung erlaubt nur ganze Blöcke | `offsets.x/z` in ganzen Blöcken, Y=0 |
| Raum-/Segmentdimensionen und Grundrisszellen | Blockraster; eine Zelle ist 16 hB breit | In den jeweiligen älteren JSON-Feldern ganze Blöcke |
| Dachhöhe/-überhang mit `…16`, Anschlussabstand `gap` der lokalen Mod | hB | Ganzzahl in Sechzehnteln |
| Knotenposition `x/y` im Graphdokument | Nur die Lage des Knotens auf der Zeichenfläche | Diagrammkoordinate, weder hB noch Weltblock |

Der Browser rechnet den Raumversatz vor dem Export zurück in das bestehende Blockformat. Die Anzeige von hB erzeugt dort keine bisher nicht unterstützte Unterblockplatzierung. Die Referenz benennt für jedes Feld seine tatsächliche Einheit.

## Spiegelebenen der lokalen Mod

Die Bezeichnung benennt die **Normale** einer Ebene durch die Blockmitte. X bedeutet somit eine zur X-Achse senkrechte Ebene, nicht eine Ebene, die die X-Achse enthält.

| Enum | Normalenvektor |
| --- | --- |
| `X`, `Y`, `Z` | (1,0,0), (0,1,0), (0,0,1) |
| `XY`, `XY_NEGATIVE` | (1,1,0), (1,−1,0) |
| `XZ`, `XZ_NEGATIVE` | (1,0,1), (1,0,−1) |
| `YZ`, `YZ_NEGATIVE` | (0,1,1), (0,1,−1) |
| `BODY_PPP`, `BODY_PPN` | (1,1,1), (1,1,−1) |
| `BODY_PNP`, `BODY_PNN` | (1,−1,1), (1,−1,−1) |

Die vier BODY-Ebenen können rationale Schnittkoordinaten erzeugen und verwenden deshalb die exakte Auswertung. Sie gehören zur lokalen Mod, nicht zum älteren Browserkatalog.
