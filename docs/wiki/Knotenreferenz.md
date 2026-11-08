# Knotenreferenz

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.3** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

Diese Referenz beschreibt **Anschlüsse**, **Ausgangstypen**, **Inspektoreigenschaften**, **Einheiten** und **Verfügbarkeit**. Sie ist nach Form- und Gebäudegraph getrennt, weil gleichnamige Rotations- oder Spiegelknoten unterschiedliche Porttypen besitzen.

| Nachschlagen | Inhalt |
| --- | --- |
| [Formgraph](Knoten-Formgraph.md) | 10 aktuelle Typen: Quellen, vier Mengenoperationen, Rotation, Spiegelung und Ergebnis |
| [Gebäudegraph](Knoten-Gebaeudegraph.md) | 24 aktuelle Typen: Flächen, Copycats, Segmente, Räume, Stockwerke, Dächer, Schächte und Außenanlagen |
| [Gemeinsame und verschachtelte Eigenschaften](Knoten-Eigenschaften.md) | Raum-/Dachkonfigurationen, Segmentmetadaten, Platzierungen und semantische Referenzen |
| [Geplante Segmentoperatoren](Geplante-Segmentoperatoren.md) | Vier noch nicht implementierte boolesche Segmentknoten, einschließlich ihres Auswertungsvertrags |
| [Einheiten und Ausrichtung](Einheiten-und-Ausrichtung.md) | hB, Weltachsen, Himmelsrichtungen, Montage und gespeicherte Werte |
| [Entwicklungsstand](Entwicklungsstand.md) | Lokale Mod, öffentlicher Browser und Dateiformate |

## Formknoten

| Knoten | ID | Ausgang | Graphstudio 0.3 |
| --- | --- | --- | --- |
| [Standard-Copycat](Knoten-Formgraph.md#input) | `INPUT` | `shape` | Älterer Aufbau vorhanden |
| [Vereinigung](Knoten-Formgraph.md#union) | `UNION` | `shape` | Älterer Aufbau vorhanden |
| [Schnitt](Knoten-Formgraph.md#intersection) | `INTERSECTION` | `shape` | Älterer Aufbau vorhanden |
| [Differenz A − B](Knoten-Formgraph.md#difference) | `DIFFERENCE` | `shape` | Älterer Aufbau vorhanden |
| [Symmetrische Differenz](Knoten-Formgraph.md#symmetric_difference) | `SYMMETRIC_DIFFERENCE` | `shape` | Älterer Aufbau vorhanden |
| [Copycat rotieren](Knoten-Formgraph.md#rotate) | `ROTATE` | `shape` | Älterer Aufbau vorhanden |
| [Rotation](Knoten-Formgraph.md#rotation) | `ROTATION` | `rotation` | Älterer Aufbau vorhanden |
| [Ergebnis-Copycat](Knoten-Formgraph.md#output) | `OUTPUT` | Ergebnis | Älterer Aufbau vorhanden |
| [Copycat spiegeln](Knoten-Formgraph.md#mirror) | `MIRROR` | `shape` | Noch nicht vorhanden |
| [Gespeicherter Copycat](Knoten-Formgraph.md#saved) | `SAVED` | `shape` | Noch nicht vorhanden |

## Gebäudeknoten

| Knoten | ID | Ausgang | Graphstudio 0.3 |
| --- | --- | --- | --- |
| [Flächengebiet](Knoten-Gebaeudegraph.md#area) | `AREA` | `AREA` | Noch nicht vorhanden |
| [Copycat](Knoten-Gebaeudegraph.md#copycat) | `COPYCAT` | `COPYCAT` | Älterer Aufbau vorhanden |
| [Segmentgeometrie](Knoten-Gebaeudegraph.md#segment_layout) | `SEGMENT_LAYOUT` | `SEGMENT_LAYOUT` | Noch nicht vorhanden |
| [Segment](Knoten-Gebaeudegraph.md#segment) | `SEGMENT` | `SEGMENT` | Älterer Aufbau vorhanden |
| [Segmente vereinigen](Knoten-Gebaeudegraph.md#pattern) | `PATTERN` | `PATTERN` | Älterer Aufbau vorhanden |
| [Raumgeometrie](Knoten-Gebaeudegraph.md#room_geometry) | `ROOM_GEOMETRY` | `ROOM_GEOMETRY` | Noch nicht vorhanden |
| [Boden](Knoten-Gebaeudegraph.md#floor) | `FLOOR` | `FLOOR` | Älterer Aufbau vorhanden |
| [Decke](Knoten-Gebaeudegraph.md#ceiling) | `CEILING` | `CEILING` | Älterer Aufbau vorhanden |
| [Raum](Knoten-Gebaeudegraph.md#room) | `ROOM` | `ROOM` | Älterer Aufbau vorhanden |
| [Stockwerk](Knoten-Gebaeudegraph.md#storey) | `STOREY` | `STOREY` | Älterer Aufbau vorhanden |
| [Dachebene](Knoten-Gebaeudegraph.md#roof_level) | `ROOF_LEVEL` | `ROOF_LEVEL` | Noch nicht vorhanden |
| [Dachanordnung](Knoten-Gebaeudegraph.md#roof_layout) | `ROOF_LAYOUT` | `ROOF_LAYOUT` | Noch nicht vorhanden |
| [Dach](Knoten-Gebaeudegraph.md#roof) | `ROOF` | `ROOF` | Älterer Aufbau vorhanden |
| [Treppenhaus](Knoten-Gebaeudegraph.md#stairwell) | `STAIRWELL` | `STAIRWELL` | Noch nicht vorhanden |
| [Aufzugsvorbereitung](Knoten-Gebaeudegraph.md#elevator_shaft) | `ELEVATOR_SHAFT` | `ELEVATOR_SHAFT` | Noch nicht vorhanden |
| [Versorgungsschacht](Knoten-Gebaeudegraph.md#service_shaft) | `SERVICE_SHAFT` | `SERVICE_SHAFT` | Noch nicht vorhanden |
| [Gebäude](Knoten-Gebaeudegraph.md#building) | `BUILDING` | Ergebnis | Älterer Aufbau vorhanden |
| [Garten](Knoten-Gebaeudegraph.md#garden) | `GARDEN` | `GARDEN` | Noch nicht vorhanden |
| [Weg](Knoten-Gebaeudegraph.md#path) | `PATH` | `PATH` | Noch nicht vorhanden |
| [Segment spiegeln](Knoten-Gebaeudegraph.md#segment_mirror) | `SEGMENT_MIRROR` | `SEGMENT_MIRROR` | Noch nicht vorhanden |
| [Copycat spiegeln](Knoten-Gebaeudegraph.md#copycat_mirror) | `COPYCAT_MIRROR` | `COPYCAT_MIRROR` | Noch nicht vorhanden |
| [Segment rotieren](Knoten-Gebaeudegraph.md#segment_rotate) | `SEGMENT_ROTATE` | `SEGMENT_ROTATE` | Noch nicht vorhanden |
| [Copycat rotieren](Knoten-Gebaeudegraph.md#copycat_rotate) | `COPYCAT_ROTATE` | `COPYCAT_ROTATE` | Noch nicht vorhanden |
| [Raumanordnung](Knoten-Gebaeudegraph.md#room_layout) | `ROOM_LAYOUT` | `ROOM_LAYOUT` | Noch nicht vorhanden |

## Anschlussregeln

Ein Eingang nimmt genau eine Quelle des angegebenen Typs an. Eine Quelle darf mehrere Ziele versorgen. `shape` und `rotation` sind im Formgraph unterschiedliche Werttypen. Im Gebäudegraph sind beispielsweise `COPYCAT`, `SEGMENT`, `ROOM` und `STOREY` ebenfalls verschiedene Ergebnisse. Nur weil zwei Knoten dieselbe sichtbare Geometrie zeigen, sind ihre Anschlüsse nicht austauschbar.

Leere Eingänge sind als unfertiger Entwurf zulässig, soweit die Strukturregeln sie erlauben. Die Berechnung meldet fehlende benötigte Quellen. Zyklen und unbekannte Referenzen werden abgewiesen. Ein Formgraph hat genau einen OUTPUT, ein Gebäudegraph genau einen BUILDING-Knoten; `empty_wall` ist eine geschützte Standardquelle.

## Grenzen und allgemeine Eigenschaften

| Bereich | Grenze / Bedeutung |
| --- | --- |
| Formgraph | höchstens 64 Knoten und 32 Standard-/gespeicherte Quellen |
| Gebäudegraph | höchstens 96 Knoten; üblicherweise höchstens 16 Sequenzeingänge je Knoten |
| Stockwerke | Erdgeschoss 0, höchstens acht Ober- und acht Untergeschosse |
| Materialgruppen | höchstens acht im Formdokument; Prioritäten lösen Überdeckungen auf |
| `id` | Eindeutiger technischer Knotenbezeichner; Verbindungen speichern diese ID |
| `name` | Anzeigename eines Gebäudeknotens |
| `x`, `y` | Lage auf der Diagrammfläche, kein Weltversatz |
| Graphgruppen | Gemeinsame Gruppendefinitionen und verknüpfte Instanzen; keine zusätzlichen geometrischen Porttypen |

Die Tabellen listen den Datenvertrag; ein Feld kann im Inspektor nur im zugehörigen Modus sichtbar sein. Werte einer verbundenen Geometriequelle können lokale Maße übersteuern. Gemeinsame Auswahl-, Gruppierungs-, Undo-/Redo- und Kartenaktionen gehören zur Editorbedienung, nicht zu den geometrischen Eingängen eines Knotens.

Die Feldgrenzen stammen aus dem Knotenvertrag. Zusätzliche Zusammenhänge werden erst bei der Auswertung geprüft, etwa passende Wandmaße, geschlossene Raumgrenzen, ausreichend hohe Räume oder vollständige Dachperioden. Ein Wert innerhalb seines Zahlenbereichs garantiert allein noch keinen gültigen Gesamtentwurf.
