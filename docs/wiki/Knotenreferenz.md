# Knotenreferenz

> **Stand dieses Entwicklungszweigs:** Diese Anleitung beschreibt die vollständige Überarbeitung mit Vorschau-Auswahldialog, allen sechs Copycat- und Segmentoperationen sowie dem 2D-Anordnungsraster. Der Browser unterstützt hier 10 Form- und 21 Gebäudeknotentypen. Die Änderungen sind noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Modrelease 0.6.0 bleibt unverändert.

> Stand: **9. November 2026**. Diese Referenz beschreibt den aktuellen gemeinsamen Knotenvertrag und **Graphstudio 0.4**. Neue boolesche Gebäudeknoten sind im Modrelease **0.6.0** noch nicht enthalten. [Entwicklungsstand und Kompatibilität](Entwicklungsstand.md)

Diese Referenz beschreibt **Anschlüsse**, **Ausgangstypen**, **Inspektoreigenschaften**, **Einheiten** und **Verfügbarkeit**. Sie ist nach Form- und Gebäudegraph getrennt, weil gleichnamige Rotations- oder Spiegelknoten unterschiedliche Porttypen besitzen.

| Nachschlagen | Inhalt |
| --- | --- |
| [Formgraph](Knoten-Formgraph.md) | 10 aktuelle Typen: Quellen, vier Mengenoperationen, Rotation, Spiegelung und Ergebnis |
| [Gebäudegraph](Knoten-Gebaeudegraph.md) | 32 aktuelle Typen, davon 21 im Browser: Flächen, Copycats, Segmente, Räume, Stockwerke, Dächer, Schächte und Außenanlagen |
| [Gemeinsame und verschachtelte Eigenschaften](Knoten-Eigenschaften.md) | Raum-/Dachkonfigurationen, Segmentmetadaten, Platzierungen und semantische Referenzen |
| [Boolesche Segmentoperatoren](Geplante-Segmentoperatoren.md) | Vier implementierte boolesche Segmentknoten mit Vertrag und Grenzen |
| [Einheiten und Ausrichtung](Einheiten-und-Ausrichtung.md) | hB, Weltachsen, Himmelsrichtungen, Montage und gespeicherte Werte |
| [Entwicklungsstand](Entwicklungsstand.md) | Modrelease, gemeinsamer Quellstand, Browser und Dateiformate |

## Formknoten

| Knoten | ID | Ausgang | Graphstudio 0.4 |
| --- | --- | --- | --- |
| [Standard-Copycat](Knoten-Formgraph.md#input) | `INPUT` | `shape` | Älterer Aufbau vorhanden |
| [Vereinigung](Knoten-Formgraph.md#union) | `UNION` | `shape` | Älterer Aufbau vorhanden |
| [Schnitt](Knoten-Formgraph.md#intersection) | `INTERSECTION` | `shape` | Älterer Aufbau vorhanden |
| [Differenz A − B](Knoten-Formgraph.md#difference) | `DIFFERENCE` | `shape` | Älterer Aufbau vorhanden |
| [Symmetrische Differenz](Knoten-Formgraph.md#symmetric_difference) | `SYMMETRIC_DIFFERENCE` | `shape` | Älterer Aufbau vorhanden |
| [Copycat rotieren](Knoten-Formgraph.md#rotate) | `ROTATE` | `shape` | Älterer Aufbau vorhanden |
| [Rotation](Knoten-Formgraph.md#rotation) | `ROTATION` | `rotation` | Älterer Aufbau vorhanden |
| [Ergebnis-Copycat](Knoten-Formgraph.md#output) | `OUTPUT` | Ergebnis | Älterer Aufbau vorhanden |
| [Copycat spiegeln](Knoten-Formgraph.md#mirror) | `MIRROR` | `shape` | Vorhanden |
| [Gespeicherter Copycat](Knoten-Formgraph.md#saved) | `SAVED` | `shape` | Vorhanden |

## Gebäudeknoten

| Knoten | ID | Ausgang | Graphstudio 0.4 |
| --- | --- | --- | --- |
| [Flächengebiet](Knoten-Gebaeudegraph.md#area) | `AREA` | `AREA` | Noch nicht vorhanden |
| [Copycat-Referenz](Knoten-Gebaeudegraph.md#copycat) | `COPYCAT` | `COPYCAT` | Gespeicherte Form laden oder Form-JSON importieren |
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
| [Segment spiegeln](Knoten-Gebaeudegraph.md#segment_mirror) | `SEGMENT_MIRROR` | `SEGMENT_MIRROR` | Vorhanden, X/Y/Z |
| [Copycat spiegeln](Knoten-Gebaeudegraph.md#copycat_mirror) | `COPYCAT_MIRROR` | `COPYCAT_MIRROR` | Vorhanden |
| [Segment rotieren](Knoten-Gebaeudegraph.md#segment_rotate) | `SEGMENT_ROTATE` | `SEGMENT_ROTATE` | Vorhanden, X/Y/Z |
| [Copycat rotieren](Knoten-Gebaeudegraph.md#copycat_rotate) | `COPYCAT_ROTATE` | `COPYCAT_ROTATE` | Vorhanden |
| [Raumanordnung](Knoten-Gebaeudegraph.md#room_layout) | `ROOM_LAYOUT` | `ROOM_LAYOUT` | Noch nicht vorhanden |
| [Segment-Vereinigung](Knoten-Gebaeudegraph.md#segment_union) | `SEGMENT_UNION` | Segment | Vorhanden |
| [Segment-Schnitt](Knoten-Gebaeudegraph.md#segment_intersection) | `SEGMENT_INTERSECTION` | Segment | Vorhanden |
| [Segment-Differenz](Knoten-Gebaeudegraph.md#segment_difference) | `SEGMENT_DIFFERENCE` | Segment | Vorhanden |
| [Symmetrische Segment-Differenz](Knoten-Gebaeudegraph.md#segment_symmetric_difference) | `SEGMENT_SYMMETRIC_DIFFERENCE` | Segment | Vorhanden |
| [Copycat-Vereinigung](Knoten-Gebaeudegraph.md#copycat_union) | `COPYCAT_UNION` | Copycat | Vorhanden |
| [Copycat-Schnitt](Knoten-Gebaeudegraph.md#copycat_intersection) | `COPYCAT_INTERSECTION` | Copycat | Vorhanden |
| [Copycat-Differenz](Knoten-Gebaeudegraph.md#copycat_difference) | `COPYCAT_DIFFERENCE` | Copycat | Vorhanden |
| [Symmetrische Copycat-Differenz](Knoten-Gebaeudegraph.md#copycat_symmetric_difference) | `COPYCAT_SYMMETRIC_DIFFERENCE` | Copycat | Vorhanden |

Damit stehen **Rotieren, Spiegeln, Vereinigung, Schnitt, Differenz und symmetrische Differenz** im Gebäudegraphen sowohl für Copycats als auch für Segmente bereit. Die [Bedienungsanleitung](Graphstudio.md) zeigt die Zuordnung und den Ablauf.

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
