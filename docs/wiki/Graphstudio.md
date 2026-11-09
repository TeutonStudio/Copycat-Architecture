# Graphstudio

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.4** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

[**Gebäudegraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=building) · [**Formgraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=form)

Graphstudio **0.4** ist der Browsereditor für den älteren öffentlichen Graphstand. Die Links öffnen eine eigene interaktive Webseite. In der [Knotenreferenz](Knotenreferenz.md) steht für jeden Typ, ob er im Browser vorhanden ist. Die neuesten lokalen Modknoten werden noch nicht vollständig abgebildet.

## Einen Entwurf bearbeiten

1. Unter **Beispiel öffnen** einen Entwurf auswählen oder über **JSON öffnen** eine unterstützte Form- oder Gebäudedatei laden.
2. Einen **Knotentyp** auswählen und mit **+ Knoten** einfügen. Knoten verschieben, Anschlüsse verbinden oder die Quelle im Inspektor auswählen. Falsche Porttypen und Zyklen werden abgewiesen.
3. Gespeicherte Formen über den Copycat-Knoten (**Gespeicherten Copycat laden**) auswählen oder eine einzelne Form mit **Copycat laden** importieren. Im Formgraphen stehen **Gespeicherter Copycat** und **Copycat spiegeln** bereit; im Gebäudegraphen auch Copycat-Spiegelung/-Rotation und die vier [booleschen Segmentoperatoren](Geplante-Segmentoperatoren.md). Beim Gebäude bei Bedarf **Bibliothek öffnen** verwenden. Diese Browserfunktion liest ein Referenzpaket mit `forms` und/oder `modules`. Sie ersetzt nicht den Einzelentwurfsimport im Spiel.
4. Änderungen mit **Rückgängig/Wiederholen** beziehungsweise **Strg+Z / Strg+Umschalt+Z** prüfen. **Entf** entfernt die Auswahl. Die Ergebnis- und Standard-Leerwandknoten bleiben erhalten.
5. Über **JSON exportieren** eine Datei sichern oder über **Minecraft-JSON kopieren** den vollständigen Text in den passenden Spielimport übernehmen.

Form- und Gebäudeansicht behalten während des Wechsels getrennte Entwürfe. Vor Schließen oder Neuladen als JSON sichern; der Browser ist keine dauerhafte Weltbibliothek.

## Segmente rotieren und spiegeln

**Status:** Im nativen Fachkern vorhanden. Die Bedienung dieser beiden Knoten im Graphstudio-Browser ist auf diesem Entwicklungszweig implementiert; sie ist noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Die folgende Anleitung gilt für diesen erweiterten Browserstand.

1. Ein **Wandsegment** (`SEGMENT`), **Wandmuster** (`PATTERN`) oder das Ergebnis einer booleschen Segmentoperation als Quelle verwenden.
2. **Segment rotieren** (`SEGMENT_ROTATE`) oder **Segment spiegeln** (`SEGMENT_MIRROR`) einfügen und die Quelle mit `in:0` verbinden.
3. Zum Rotieren **X**, **Y** oder **Z** und **0°, 90°, 180° oder 270°** wählen. Die Drehung verändert die Blockpositionen und die Ausrichtung der enthaltenen Copycat-Formen gemeinsam. X und Z können ein stehendes Segment in eine liegende Anordnung drehen.
4. Zum Spiegeln die Normale der Spiegelebene **X**, **Y** oder **Z** wählen. Ohne **Nur Blockpositionen spiegeln** werden sowohl die Anordnung als auch die Copycat-Formen gespiegelt. Mit dieser Option wechseln nur die Zellpositionen; die Formen selbst behalten ihre Ausrichtung.
5. Unter **Selektiert** das Ergebnis prüfen. Der Ausgang bleibt ein **Segmentwert** und kann beispielsweise in weitere Segmentoperationen oder einen passenden Raumanschluss fließen.

Beispiel: **Wandsegment → Segment rotieren (Y, 90°) → Segment spiegeln (X) → Segment-Differenz**. Die Bearbeitung des vollständigen Bauteils erfolgt dabei an den Segmentknoten; die einzelnen Quellen müssen dafür nicht separat transformiert werden. [Segmentrotation nachschlagen](https://github.com/TeutonStudio/copycat-architect/wiki/Knoten-Gebaeudegraph#segment_rotate) · [Segmentspiegelung nachschlagen](https://github.com/TeutonStudio/copycat-architect/wiki/Knoten-Gebaeudegraph#segment_mirror)

## 2D-Raster für Anordnungen

**Status: auf dem Entwicklungszweig lokal implementiert, noch nicht in master integriert oder im Live-Graphstudio bereitgestellt.** Diese Browserfunktion ergänzt die getrennten Raster im Minecraft-Editor.

| Knoten | Raster und Bearbeitung |
| --- | --- |
| **Wandmuster** (`PATTERN`) | Angeschlossene Segmente räumlich anordnen. **Vorne · X/Y**, **Oben · X/Z** und **Seite · Z/Y** zeigen dieselbe Anordnung aus drei Richtungen. Pro Eingang sind X/Y/Z-Versatz sowie Drehachse und Vierteldrehung einstellbar. |
| **Geschoss** (`STOREY`), Zusammensetzung **Aus Räumen** | Angeschlossene Rauminstanzen in der X/Z-Draufsicht platzieren. Die Raumquelle bleibt erhalten; jeder Eingang hat seinen eigenen Versatz. |

Alle Eingänge zuerst mit gültigen Quellen verbinden. Im Inspektor **2D-Raster** öffnen, den gewünschten **Eingang** wählen oder seine belegte Fläche anklicken und ziehen. Eine Zelle entspricht **1 Block = 16 hB**. Die **Pfeiltasten** bewegen den markierten Eingang um einen Block. Bei einer Vorder- oder Seitenansicht liegt positives Y nach oben. Ein Ansichtswechsel erhält die dritte, gerade unsichtbare Koordinate.

**X/Y/Z · Blöcke** erlauben genaue Versätze; im Raumraster sind nur X/Z veränderbar. Segmentversätze liegen zwischen −128 und 128, Raumversätze zwischen −64 und 64 Blöcken. Wiederholt angeschlossene Quellen lassen sich pro Eingang getrennt platzieren. Beim Wandmuster startet das Raster mit der bisherigen Reihenfolge; die erste Übernahme speichert die expliziten `transforms` mit `patternAxis=transform`.

Die mittlere Maustaste verschiebt die Ansicht. **Vergrößern**, **Verkleinern** und **Einpassen** ändern nur die Darstellung. **Escape** verwirft eine laufende Ziehbewegung. **Verwerfen** entfernt alle noch nicht übernommenen Rasteränderungen. Erst **Anordnung übernehmen** prüft das Ergebnis und speichert es als einen Schritt in der gemeinsamen Rückgängig-/Wiederholen-Historie. Fehlerhafte Anordnungen zeigen eine Meldung und werden nicht übernommen. Der JSON-Export bewahrt Versätze und Segmentdrehungen im vorhandenen Minecraft-Format.

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

## Materialien und eigene Bibliotheken

Die [Materialbibliothek](Materialbibliothek.md) stellt Minecraft- und Mod-Materialien für die Vorschau bereit. **Meine Bibliotheken** verwaltet verlinkte Mod-Repositories und Sicherungen. Graphen und Knotengruppen können über die Leiste oberhalb des Editors gespeichert und wiederverwendet werden; der letzte Entwurf wird automatisch im Browser aufbewahrt.
