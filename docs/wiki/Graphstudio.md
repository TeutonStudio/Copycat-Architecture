# Graphstudio

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.2** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

[**Gebäudegraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=building) · [**Formgraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=form)

Graphstudio **0.2** ist der Browsereditor für den älteren öffentlichen Graphstand. Die Links öffnen eine eigene interaktive Webseite. In der [Knotenreferenz](Knotenreferenz.md) steht für jeden Typ, ob er im Browser vorhanden ist. Die neuesten lokalen Modknoten werden noch nicht vollständig abgebildet.

## Einen Entwurf bearbeiten

1. Unter **Beispiel öffnen** einen Entwurf auswählen oder über **JSON öffnen** eine unterstützte Form- oder Gebäudedatei laden.
2. Einen **Knotentyp** auswählen und mit **+ Knoten** einfügen. Knoten verschieben, Anschlüsse verbinden oder die Quelle im Inspektor auswählen. Falsche Porttypen und Zyklen werden abgewiesen.
3. Beim Gebäude bei Bedarf **Bibliothek öffnen** verwenden. Diese Browserfunktion liest ein Referenzpaket mit `forms` und/oder `modules`. Sie ersetzt nicht den Einzelentwurfsimport im Spiel.
4. Änderungen mit **Rückgängig/Wiederholen** beziehungsweise **Strg+Z / Strg+Umschalt+Z** prüfen. **Entf** entfernt die Auswahl. Die Ergebnis- und Standard-Leerwandknoten bleiben erhalten.
5. Über **JSON exportieren** eine Datei sichern oder über **Minecraft-JSON kopieren** den vollständigen Text in den passenden Spielimport übernehmen.

Form- und Gebäudeansicht behalten während des Wechsels getrennte Entwürfe. Vor Schließen oder Neuladen als JSON sichern; der Browser ist keine dauerhafte Weltbibliothek.

## 3D-Vorschau: Graph oder Selektiert

| Auswahl | Darstellung |
| --- | --- |
| **Graph** | Das endgültige Ergebnis des Formgraphen beziehungsweise das vollständige Gebäude. Die Knotenauswahl verändert dieses Vorschauziel nicht. |
| **Selektiert** | Das Zwischenergebnis des angeklickten Knotens. Eine Standardquelle kann deshalb angezeigt werden, obwohl der Ergebnisknoten noch nicht verbunden ist. |
| Selektiert, aber kein Knoten gewählt | Aufforderung zur Knotenauswahl; es wird kein Gebäude-Endergebnis als Ersatz ausgegeben. |
| Rotationswert | Achse und Winkel; dieser Port liefert keine Geometrie. |

Copycat-Quellen und Segmente werden lokal angezeigt, Räume und Stockwerke als eigene Teilpläne. Boden und Decke benötigen ihren Grundriss: Der ältere Browserkern verwendet den ersten angeschlossenen Raum beziehungsweise ein direkt aus Wänden aufgebautes Stockwerk und benennt den Kontext. Das ausgewählte Dach wird im Kontext des obersten Geschosses ausgewertet. Ohne den benötigten Kontext wird eine Meldung gezeigt.

Bei einem ungültigen Entwurf bezeichnet **Letzter gültiger Stand** ausdrücklich die ältere Vorschau desselben Ziels. Sie ist kein Beleg, dass der aktuelle Entwurf gebaut werden kann.

## Blockrand und Einheiten

Im Formeditor bleibt der große Referenzwürfel **1 × 1 × 1 Block = 16 × 16 × 16 hB** groß, auch wenn die Form kleiner ist. Seine zwölf Kanten zeigen die Blockgrenze. Vorderkanten sind hell, hintere Kanten schwächer; diese Zuordnung folgt der gedrehten Ansicht.

**Jede Kante** trägt drei orange Marken ohne Zahlenbeschriftung: bei **8 hB** einen längeren, kräftigeren Strich, bei **4 hB** und **12 hB** jeweils einen kürzeren, schmaleren Strich. Die Achsenlegende verwendet durchgehend Minecraft-X/Y/Z: X Ost/West, Y Höhe, Z Süd/Nord. [Einheiten und Ausrichtung](Einheiten-und-Ausrichtung.md)

Der Blockrahmen und seine Marken erscheinen bei einzelnen Copycat-Formen: im Formgraphen und beim ausgewählten Copycat-Knoten im Gebäudegraphen. Vorschauen von Segmenten, Räumen, Stockwerken und ganzen Gebäuden erhalten keinen Rahmen. Geometrie über mehr als einen Block wird ebenfalls ohne Blockrahmen dargestellt. **3D einpassen** setzt die Kamera auf die aktuelle Vorschau zurück. Die Ansicht lässt sich mit der Maus drehen und zoomen. WebGL 2 ist erforderlich.

## Seitenleiste anpassen

Graph und die Spalte aus **3D-Vorschau** und **Inspektor** besitzen auf dem Desktop dieselbe Gesamthöhe. Der senkrechte Griff verändert ihre Breite; der waagerechte Griff zwischen Vorschau und Inspektor verändert deren Höhenverhältnis. Beide Griffe lassen sich mit Tab fokussieren und mit den passenden Pfeiltasten verändern; Home/End springen an die Grenzen. Doppelklick stellt das Standardverhältnis wieder her.

Beide Bereiche sind einzeln einklappbar. Ein offener Bereich übernimmt den freien Platz. Sind beide eingeklappt, darf die Seitenleiste kürzer als der Graph sein. Bei schmalen Fenstern stehen Graph und Seitenleiste untereinander.

## Kompatibilität

Unterstützt: **Formgraph-Schema 1–2**, **Gebäudegraph-Schema 1** und die ausdrücklich als Browserknoten markierten Typen. Zusätzliche Knoten und neuere Schemata werden nicht automatisch herunterkonvertiert. Referenzen, Kostenmodell und Minecraft-Texturen unterscheiden sich vom neuesten lokalen Modstand; siehe [Entwicklungsstand](Entwicklungsstand.md).
