# Graphstudio

> **Stand dieses Entwicklungszweigs:** Diese Anleitung beschreibt die vollständige Überarbeitung mit Vorschau-Auswahldialog, allen sechs Copycat- und Segmentoperationen sowie dem 2D-Anordnungsraster. Der Browser unterstützt hier 10 Form- und 21 Gebäudeknotentypen. Die Änderungen sind noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Modrelease 0.6.0 bleibt unverändert.

> Stand: **9. November 2026**. Beschreibt **Graphstudio 0.4** und den zugehörigen gemeinsamen Fachkern. Die neuen booleschen Gebäudeknoten benötigen im Spiel einen aktualisierten Mod-Build; das veröffentlichte Modrelease **0.6.0** enthält sie noch nicht. [Entwicklungsstand](Entwicklungsstand.md)

[**Gebäudegraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=building) · [**Formgraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=form)

Graphstudio **0.4** ist der Browsereditor für Copycat-Formen und Gebäudegraphen. Er bietet **10 Formknotentypen und 21 Gebäudeknotentypen** und verwendet die gemeinsame Berechnungslogik des Mods. Die Links öffnen eine eigene interaktive Webseite. In der [Knotenreferenz](Knotenreferenz.md) steht für jeden Typ, ob er im Browser vorhanden ist; der native Editor besitzt zusätzliche Knoten und Einstellungen.

## Einen Entwurf bearbeiten

1. Unter **Beispiel öffnen** einen Entwurf auswählen oder über **JSON öffnen** eine unterstützte Form- oder Gebäudedatei laden.
2. Einen **Knotentyp** auswählen und mit **+ Knoten** einfügen. Knoten verschieben, Anschlüsse verbinden oder die Quelle im Inspektor auswählen. Falsche Porttypen und Zyklen werden abgewiesen.
3. Im Inspektor einer Copycat-Quelle **Copycat auswählen** öffnen, eine Grundform oder gespeicherte Form in der Vorschau prüfen und mit **Auswahl übernehmen** einsetzen. **Copycat laden** importiert eine einzelne Form-JSON. Bei vorhandenen Referenzpaketen liest **Bibliothek öffnen** deren `forms` und/oder `modules` ein.
4. Änderungen mit **Rückgängig/Wiederholen** beziehungsweise **Strg+Z / Strg+Umschalt+Z** prüfen. **Entf** entfernt die Auswahl. Die Ergebnis- und Standard-Leerwandknoten bleiben erhalten.
5. Über **JSON exportieren** eine Datei sichern oder über **Minecraft-JSON kopieren** den vollständigen Text in den passenden Spielimport übernehmen.

Form- und Gebäudeansicht behalten getrennte Entwürfe und speichern ihren letzten Stand automatisch im Browser. Eine **Sicherung** unter **Meine Bibliotheken** bewahrt auch die geladenen Referenzformen und Vorschau-Materialien auf; der normale Minecraft-JSON-Export enthält bei Gebäuden nur die Referenzen auf diese Formen.

## Copycat auswählen: Grundformen und gespeicherte Formen

**Copycat auswählen** öffnet im Browser einen Auswahldialog. Die Schaltfläche steht in der Werkzeugleiste und im Inspektor der Formquellen **Grundform** (`INPUT`) und **Gespeicherter Copycat** (`SAVED`) sowie der Gebäudequelle **Copycat-Referenz** (`COPYCAT`). Ist eine solche Quelle ausgewählt, ersetzt die Übernahme ihren Inhalt. Ohne ausgewählte Quelle legt **Copycat auswählen** in der Werkzeugleiste beim Bestätigen einen neuen Quellknoten an.

| Reiter | Inhalt |
| --- | --- |
| **Grundformen** | 19 Standardformen einschließlich **Dachvorlage**, jeweils mit gerenderter Geometrievorschau. |
| **Gespeicherte Formen** | Persönliche Formgraphen mit der Herkunft **Meine gespeicherten Formen** sowie Formen der geladenen Bibliothek mit ihrem Versionsschlüssel. |

Unter **Formen suchen** nach Name oder Bibliotheksreferenz suchen. Eine Karte markieren und die berechnete Geometrie in der größeren Vorschau prüfen: Ziehen dreht die Ansicht, Scrollen zoomt. Erst **Auswahl übernehmen** ändert den Graphen. **Abbrechen** oder **Escape** schließt den Dialog ohne Änderung, auch wenn zuvor eine andere Karte markiert wurde. Nicht auswertbare Formen zeigen eine Fehlermeldung und können nicht übernommen werden.

Gespeicherte Formen übernehmen ihre Geometrie mit den Materialgruppen. Eine neue Auswahl setzt die zusätzlichen Drehwerte des Quellknotens zurück, damit seine Ausrichtung der Dialogvorschau entspricht. Nachgeschaltete Dreh- und Spiegelknoten bleiben erhalten. Bei Grundformen können Größe und Ausrichtung anschließend im Formgraph-Inspektor angepasst werden.

Im Formgraphen kann dieselbe Quelle zwischen Grundform und gespeicherter Form wechseln: Der Knoten wird entsprechend `INPUT` oder `SAVED`; seine ID und vorhandene Verbindungen bleiben erhalten. Im Gebäudegraphen bleibt er eine Copycat-Referenz. Eine Bibliotheksform übernimmt dort ihren bestehenden Versionsschlüssel; persönliche Formgraphen und Grundformen werden als feste Version in die Referenzbibliothek des Gebäudeentwurfs aufgenommen.

## Gespeicherte Formen im Gebäude verwenden

1. Im **Formgraphen** die Form fertig verbinden, einen Namen eingeben und **Graph speichern** wählen. Dieser benannte Eintrag erscheint anschließend im Reiter **Gespeicherte Formen**.
2. Zum **Gebäudegraphen** wechseln, einen **Copycat-Referenz**-Knoten einfügen und auswählen.
3. Im Inspektor **Copycat auswählen** öffnen, unter **Gespeicherte Formen** den gewünschten persönlichen Eintrag markieren und die Vorschau prüfen. Mit **Auswahl übernehmen** werden Geometrie und Materialbelegung als feste Formversion in die Referenzbibliothek dieses Gebäudeentwurfs übernommen.
4. Den Ausgang der Referenz mit einem Copycat-Operator oder einem Copycat-Eingang eines Segments verbinden. **Selektiert** zeigt das lokale Ergebnis bereits vor dem Anschluss an ein vollständiges Gebäude.

Eine vorhandene Formdatei lässt sich direkt im Referenzknoten mit **Formdatei laden** oder über **Copycat laden** in der Werkzeugleiste öffnen. Ist eine Copycat-Referenz ausgewählt, wird diese befüllt; andernfalls entsteht ein neuer Quellknoten. Das allgemeine **JSON öffnen** lädt dagegen einen vollständigen Entwurf.

Spätere Änderungen oder das Löschen des gespeicherten Formgraphen verändern bereits geladene Referenzen nicht. Erneutes Laden übernimmt die gewählte Version ausdrücklich. Die übernommenen Materialbelegungen speichern Block-IDs; Vorschau-Blockzustände wie `axis=x` werden dabei nicht Teil des Gebäudeschemas. Ohne eigene Belegung bleiben die Materialrollen der Form maßgeblich.

Für die Wiederaufnahme in einem anderen Browser die **Graphstudio-Sicherung** verwenden. Für den Minecraft-Import müssen die referenzierten Formen und Module unter den gespeicherten Versionsschlüsseln in der Spielbibliothek verfügbar sein; ein Gebäude-JSON ist kein vollständiges Bibliothekspaket.

## Alle sechs Operationen für Copycats und Segmente

Im Gebäudegraphen gibt es je einen passenden Knotentyp für beide Wertarten:

| Operation | Copycat-Knoten | Segmentknoten |
| --- | --- | --- |
| Rotieren | `COPYCAT_ROTATE` | `SEGMENT_ROTATE` |
| Spiegeln | `COPYCAT_MIRROR` | `SEGMENT_MIRROR` |
| Vereinigung | `COPYCAT_UNION` | `SEGMENT_UNION` |
| Schnitt | `COPYCAT_INTERSECTION` | `SEGMENT_INTERSECTION` |
| Differenz A − B | `COPYCAT_DIFFERENCE` | `SEGMENT_DIFFERENCE` |
| Symmetrische Differenz | `COPYCAT_SYMMETRIC_DIFFERENCE` | `SEGMENT_SYMMETRIC_DIFFERENCE` |

Copycat-Operatoren bearbeiten die Geometrie innerhalb einer Blockzelle und liefern wieder Copycat-Werte. Segmentoperatoren bearbeiten ein vollständiges Bauteil und liefern wieder Segmentwerte. Beide Familien lassen sich jeweils weiter verbinden: etwa **Copycat-Referenz → Copycat rotieren → Copycat-Differenz → Wandsegment → Segment spiegeln**.

Rotation verwendet die Weltachsen **X, Y oder Z** und **0°, 90°, 180° oder 270°**. Spiegelung verwendet eine mittige Ebene mit X, Y oder Z als Normale. Beim Segment wählt **Nur Blockpositionen spiegeln**, ob nur die Blockanordnung oder zusätzlich die Copycat-Geometrie in jeder Zelle gespiegelt wird; standardmäßig wird beides gespiegelt. Die diagonalen Spiegelebenen des nativen Editors sind im Browser nicht freigeschaltet.

Vereinigung, Schnitt und symmetrische Differenz erlauben **2–16 Eingänge**, die Differenz genau **A und B**. **A / B tauschen** kehrt die Abzugsrichtung um. Symmetrische Differenz verwendet ungerade Überdeckung: Zwei identische Eingänge verschwinden, drei identische Eingänge bleiben erhalten. Leere Zwischenergebnisse können weiterverarbeitet werden. **Separiert** erhält die Materialherkunft; **Gruppiert** verwendet die gewählte gemeinsame Gebäuderolle.

**Wandmuster** (`PATTERN`) setzt Segmente in Reihenfolge zusammen. Für das boolesche Zusammenführen überlagerter Geometrien **Segment-Vereinigung** verwenden. Details, insbesondere zum Schneiden normaler Vollblöcke und semantischer Segmente, stehen bei den [booleschen Segmentoperatoren](Geplante-Segmentoperatoren.md).

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

Copycat-Quellen und Segmente werden lokal angezeigt, Räume und Stockwerke als eigene Teilpläne. Boden und Decke benötigen ihren Grundriss: Der Browser verwendet den ersten angeschlossenen Raum beziehungsweise ein direkt aus Wänden aufgebautes Stockwerk und benennt den Kontext. Das ausgewählte Dach wird im Kontext des obersten Geschosses ausgewertet. Ohne den benötigten Kontext wird eine Meldung gezeigt.

Bei einem ungültigen Entwurf bezeichnet **Letzter gültiger Stand** ausdrücklich die ältere Vorschau desselben Ziels. Sie ist kein Beleg, dass der aktuelle Entwurf gebaut werden kann.

## Blockrand und Einheiten

Im Formeditor bleibt der große Referenzwürfel **1 × 1 × 1 Block = 16 × 16 × 16 hB** groß, auch wenn die Form kleiner ist. Seine zwölf Kanten zeigen die Blockgrenze. Vorderkanten sind hell, hintere Kanten schwächer; diese Zuordnung folgt der gedrehten Ansicht.

**Jede Kante** trägt drei orange Marken ohne Zahlenbeschriftung: bei **8 hB** einen längeren, kräftigeren Strich, bei **4 hB** und **12 hB** jeweils einen kürzeren, schmaleren Strich. Die Achsenlegende verwendet durchgehend Minecraft-X/Y/Z: X Ost/West, Y Höhe, Z Süd/Nord. [Einheiten und Ausrichtung](Einheiten-und-Ausrichtung.md)

Der Blockrahmen und seine Marken erscheinen bei einzelnen Copycat-Formen: im Formgraphen und beim ausgewählten Copycat-Knoten im Gebäudegraphen. Vorschauen von Segmenten, Räumen, Stockwerken und ganzen Gebäuden erhalten keinen Rahmen. Geometrie über mehr als einen Block wird ebenfalls ohne Blockrahmen dargestellt. **3D einpassen** setzt die Kamera auf die aktuelle Vorschau zurück. Die Ansicht lässt sich mit der Maus drehen und zoomen. WebGL 2 ist erforderlich.

## Seitenleiste anpassen

Graph und die Spalte aus **3D-Vorschau** und **Inspektor** besitzen auf dem Desktop dieselbe Gesamthöhe. Der senkrechte Griff verändert ihre Breite; der waagerechte Griff zwischen Vorschau und Inspektor verändert deren Höhenverhältnis. Beide Griffe lassen sich mit Tab fokussieren und mit den passenden Pfeiltasten verändern; Home/End springen an die Grenzen. Doppelklick stellt das Standardverhältnis wieder her.

Beide Bereiche sind einzeln einklappbar. Ein offener Bereich übernimmt den freien Platz. Sind beide eingeklappt, darf die Seitenleiste kürzer als der Graph sein. Bei schmalen Fenstern stehen Graph und Seitenleiste untereinander.

## Kompatibilität

Unterstützt: **Formgraph-Schema 1–2**, **Gebäudegraph-Schema 1** und die ausdrücklich als Browserknoten markierten Typen. Zusätzliche Knoten, neuere Schemata, native exakte Geometriedaten und nicht unterstützte Einstellungen werden ausdrücklich abgewiesen. Die neuen Copycat- und Segment-Mengenoperationen benötigen auch im Spiel einen Mod-Build mit diesen IDs. Referenzbibliotheken und die Grenzen der Texturvorschau sind im [Entwicklungsstand](Entwicklungsstand.md) erläutert.

## Materialien und eigene Bibliotheken

Die [Materialbibliothek](Materialbibliothek.md) stellt Minecraft- und Mod-Materialien für die Vorschau bereit. **Meine Bibliotheken** verwaltet verlinkte Mod-Repositories und Sicherungen. Graphen und Knotengruppen können über die Leiste oberhalb des Editors gespeichert und wiederverwendet werden; der letzte Entwurf wird automatisch im Browser aufbewahrt.
