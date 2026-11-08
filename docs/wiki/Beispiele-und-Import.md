# Beispiele und Import

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.2** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

Mit JSON kannst du Formen, Module und Gebäude austauschen. Die Beispiele zeigen unterschiedliche Werkzeuge; insbesondere verwenden die älteren Gebäudeentwürfe noch ihren bestehenden, weiterhin unterstützten Graph- oder Modulaufbau. Sie werden beim Öffnen nicht automatisch in den neuen expliziten Gebäudegraphen umgewandelt.

Die älteren, unterstützten Graphbeispiele lassen sich auch im [Graphstudio](Graphstudio.md) öffnen. Neue lokale Raum-, Spiegel- und Dachlevelknoten werden dort noch nicht unterstützt. Vor einem Austausch den [Entwicklungsstand](Entwicklungsstand.md) prüfen.

## Einen Entwurf importieren

1. Öffne die passende Art von Editor: den [Formeditor](Formeditor.md) für eine Form, die Modulwerkstatt am [Architektentisch](Gebaeudeplanung.md) für ein Modul oder den Gebäudeeditor für ein Gebäude.
2. Kopiere den **vollständigen JSON-Inhalt** der einzelnen Beispieldatei in die Zwischenablage.
3. Klicke aus Textfeldern heraus und drücke **Strg+V**. Im Gebäudeeditor sind zusätzlich Import-/Export-Schaltflächen vorhanden.
4. Prüfe Vorschau, Materialgruppen beziehungsweise Palette und Referenzen. Beim Import erhält der Entwurf eine neue ID.
5. Speichere ihn in deiner Bibliothek. Merke dir bei abhängigen Entwürfen die tatsächlich neu gespeicherte Form- oder Modulversion.

**Strg+C** exportiert den aktuellen Entwurf, wenn kein Textfeld fokussiert ist. Bei einem aktiven Textfeld betreffen die Tastenkürzel stattdessen den Text. Speichere den kopierten JSON-Inhalt in einer eigenen Datei, wenn du ihn außerhalb des Spiels aufbewahren möchtest.

## Welche Datei eignet sich wofür?

| Beispiel | Was du daran lernst |
| --- | --- |
| [Differenzform](../../examples/nodegraph-form.json) | Einen Ausschnitt aus einem Vollblock entfernen. |
| [Rotation und symmetrische Differenz](../../examples/nodegraph-rotation-xor.json) | Einen Rotationswert verbinden und gedrehte Stufen per XOR kombinieren. |
| [Zwei Materialgruppen](../../examples/nodegraph-material-groups.json) | Einen Block in getrennte Gruppen aufteilen und anschließend wieder vereinigen. |
| [Mehrmaterialform](../../examples/multi-material-form.json) | Grundkörper und Deckschicht mit unterschiedlichen Prioritäten verwenden. |
| [Gebäudegraph](../../examples/building-graph.json) | Einen bestehenden Graph mit Raum, Boden, Decke, Stockwerk und Dach ansehen. |
| [Gebäude mit Untergeschossen](../../examples/building-graph-basements.json) | Mehrfach verwendete Räume und Stockwerke sowie verschachtelte Wandmuster kennenlernen. |
| [Direkter Stockwerkaufbau](../../examples/building-graph-assembly.json) | Den älteren Direktaufbau aus Wandsegmenten, Boden, Decke und Dach betrachten. |
| [Klassisches L-Gebäude](../../examples/l-building.json) | Einen klassischen Entwurf mit Modulwänden und Satteldach verwenden. |
| [Panelgebäude](../../examples/panel-building/README.md) | Dünne Wände, Schichtfenster und Eckleisten aus mehreren Vorlagen zusammensetzen. |

Das [Beispielverzeichnis](Beispielverzeichnis.md) unterscheidet ältere und aktuelle lokale Entwürfe.

## Referenzen richtig wiederverbinden

Eine Form wird mit **ID und Versionsnummer** referenziert, beispielsweise `full@1`. Der sichtbare Name allein reicht nicht aus. Wenn du eine Datei importierst, erhält sie eine neue ID. Ein anschließend importiertes Modul kennt diese neue ID nicht automatisch.

Importiere bei einem Paket deshalb zuerst die **Formen**, danach die **Module** und zuletzt das **Gebäude**. Wähle in den abhängigen Entwürfen jeweils die zuvor neu gespeicherte Quelle erneut aus. Die Standardbibliothek enthält unter anderem `full@1`, `inner@1`, `bar@1` und `stair@1`; Beispiele mit anderen festen Referenzen benötigen ihre eigenen Quellen.

Die Dateien **`library.json` sind Referenzpakete für das Gesamtbeispiel**. Der Editor importiert einzelne Entwürfe; ein Gesamtpaket ist kein Ein-Klick-Import. Einfach den gesamten Bibliotheksinhalt in den Formeditor einzufügen funktioniert daher nicht.

Beim Ändern einer Form entsteht eine neue Version. Ein bereits gespeichertes Modul oder Gebäude verwendet weiterhin seine gewählte alte Version, bis du die Quelle dort ausdrücklich wechselst. Ein gestarteter Bauauftrag enthält einen eingefrorenen Plan; nachträgliche Änderungen am Entwurf verändern ihn nicht.

## Beispiel: Panelgebäude

Das Panelgebäude kombiniert **3/16-Panelwände**, **2/16-Schichtfenster** und **2 × 2/16-Leisten**. Hinter den Fenstern liegt kein geschlossenes opakes Panel. Die Innenfläche bleibt mit **4 × 4 × 4 Zellen** vollständig bebaubar; Boden und Decke liegen außerhalb.

![Schnittansicht eines eingerichteten Panelgebäudes](../images/panel-building-interior-furnished-cutaway.png)

*Zur Veranschaulichung sind Dach, Decke und zwei Vorderwände ausgeblendet. Der gespeicherte Bauplan enthält sie weiterhin.*

Der vollständige Entwurf umfasst **188 Copycat-Positionen** bei einer äußeren Blockhülle von **6 × 6 × 7**. Er ist ein geschlossenes Prüfgebäude; plane für einen Eingang entsprechende freie Zellen in einem Wandmodul ein.

| Vorrat für den unveränderten Entwurf | Menge |
| --- | ---: |
| Zink | 30.528 mB, entsprechend 212 Barren |
| Eichenbretter | 136 |
| Steinziegel | 48 |
| Glas | 16 |
| Tiefenschieferfliesen | 36 |

Die Summe der Oberflächenblöcke liegt über der Zahl der Druckpositionen, weil ein Block mehrere Materialarten verwenden kann. Brennstoff für die Turtle kommt hinzu. Die [ausführliche Panelhaus-Anleitung](../../examples/panel-building/README.md) beschreibt Formen, Module und Reihenfolge.

Für einen neuen Entwurf mit getrennten Segmentanordnungen und Raumgeometrien folge der [Gebäudeplanung](Gebaeudeplanung.md). Probleme beim Import oder bei fehlenden Referenzen behandelt die [Fehlerhilfe](Fehlerhilfe.md).

## Größere lokale Entwürfe

Die aktuellen Spielbuilds können beim Zwischenablageexport außerdem ein komprimiertes Transportformat mit dem Präfix `CAA1:` verwenden. Dieser Text ist kein JSON: direkt im Spiel importieren. Graphstudio liest JSON und dekodiert dieses Spieltransportformat nicht. Für den Browser einen unkomprimierten, unterstützten Graphentwurf verwenden. Der lokale Transport begrenzt entpacktes JSON auf 250.000 und den Transporttext auf 30.000 Zeichen; der ältere öffentliche Spielkern besitzt andere Grenzen.

Neue Beispiele und ihre Bibliotheken sind im [Beispielverzeichnis](Beispielverzeichnis.md) mit ihrem benötigten Stand aufgeführt. `library.json` lässt sich im Browser als Referenzbibliothek laden; im Spiel ersetzt dies nicht die Einzelform-/Modulimporte.
