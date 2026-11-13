# Gemeinsame und verschachtelte Inspektoreigenschaften

> Stand: 13. November 2026 · Gemeinsamer Modellstand von Mod und Graphstudio 0.6.1.

[Knotenreferenz](Knotenreferenz.md) · [Einheiten](Einheiten-und-Ausrichtung.md)

Die folgenden Strukturen ergänzen die Knotentabellen. Referenzfelder sind konkrete Knoten-IDs oder stabile Bibliotheksschlüssel; ein Anzeigename ersetzt diese Kennungen nicht. Änderungen können erst im vollständigen Gebäude ausgewertet werden.

## roomlayout

**Raumanordnung · roomLayout**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `instances[].id / sourceIndex` | Stabile Instanz-ID und Index des verwendeten Segmenteingangs. |
| `instances[].position` | X/Y/Z-Platzierung in ganzen Blöcken; die lokale Segmentgeometrie bleibt unverändert. |
| `instances[].quarterTurns` | 0–3 Vierteldrehungen um Y. |
| `instances[].policy` | AUTO vom Stockwerk erben, EXPLICIT eigenes Segment, OPEN bewusst offen. |
| `instances[].contribution` | BOUNDARY Raumgrenze, OPENING Fenster/Tür, STRUCTURE tragendes Bauteil, DECORATION Dekoration. |
| `boundary` | Geordnete Eckpunkte eines logischen Grenzpolygons im Blockraster; letzter Punkt schließt zum ersten. |
| `boundaryRules` | Start-/Endpunkte virtueller Grenzabschnitte und deren Wandregel. |
| `filler` | Segmentquelle für zu ergänzende Wandabschnitte. |

## roomconfig

**Raum · roomConfig**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `mode` | FOOTPRINT: Innenfläche aus Geometrie; SEGMENTS: platzierte Segmentanordnung. |
| `heightOverride` | Optionale eigene Raumhöhe 1–128 Blöcke. Leer verwendet den Vorschlag aus der Anordnung; Profile werden nicht gestreckt. |
| `interiorSource` | Segmentquelle für die raumseitige Oberfläche bei gemeinsamer Wandauswertung. |
| `defaultPartition` | Standardsegment zum Ergänzen von Trennwänden beziehungsweise Lücken. |

## facade

**Stockwerk · facade**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `Konfiguration vorhanden` | Schaltet gemeinsame Wand- und Fassadenauswertung ein; fehlende Konfiguration behält die bisherige Auswertung. |
| `source` | Segmentquelle der Außenfassade. |
| `filler` | Trennwand-/Ergänzungsquelle; leer kann die Fassadenquelle übernehmen. |

## segmentfeatures

**SegmentFeatures · Öffnungen und Säulen**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `openings[].id` | Stabile Kennung des Öffnungsauftrags. |
| `openings[].position` | Lokaler X/Y/Z-Anfang im Blockraster. |
| `openings[].size` | Breite/Höhe und Einsetztiefe in Blöcken. |
| `openings[].maskSource` | Optionale Copycat-Quelle für eine freie XY-Öffnungssilhouette; leer bedeutet Rechteck. |
| `openings[].insetSource` | Optionale Copycat-Quelle für Glas/Rahmen im Durchbruch. |
| `openings[].retainSource` | Erhält die bereits vorhandene Quellgeometrie im Öffnungsbereich, etwa ein fertiges Fenstersegment. |
| `extensions[].id` | Stabile Kennung der Säulenverlängerung. |
| `extensions[].shaftMinY / shaftMaxY` | Wiederholbarer lokaler Schaftabschnitt; obere Grenze exklusiv. Sockel und Abschluss bleiben getrennt. |
| `extensions[].profileSource` | Optionales Segmentprofil statt des markierten Quellabschnitts. |
| `extensions[].roof` | Zieldachebene mit optionalem Instanzpfad; Ziel ist die tatsächliche Dachunterseite. |
| `extensions[].gap` | Signierter Abstand in hB. |

## attachments

**Attachments · Kante an Wand anschließen**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `id` | Stabile Anschlusskennung. |
| `edge` | Lokale Kante: Nord=0, Ost=1, Süd=2, West=3. |
| `target.node` | Zielraum der tatsächlichen Wandoberfläche. |
| `target.instance` | Konkreter Stockwerks-/Rauminstanzpfad; leer verlangt eine eindeutige automatische Auswahl. |
| `target.face` | Wandseite north/east/south/west oder eine gültige ältere Wandlaufkennung. |
| `gap` | Signierter Abstand in hB. Die Kante muss auf voller Länge zur gewählten Oberfläche passen. |

## rooflevel

**Dachebene · roofLevel**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `storey` | Stabile Stockwerksinstanz-ID; leer bezieht sich automatisch auf die oberste passende Unterstützung. |
| `area` | Optionale AREA-Quelle; leer verwendet die exponierte Geschossfläche. |
| `form` | FLAT, PENT, GABLE, HIP oder MANSARD im aktuellen Inspektor. CROSS ist derzeit ausgeblendet. |
| `axis` | Vier Orientierungen: Z/+X, X/+Z, Z/−X, X/−Z; nicht mit dem alten Zweierwert ridge verwechseln. |
| `mode` | HEIGHT gibt Firsthöhe vor; ANGLE arbeitet mit Steigungsverhältnis und Kniestock. |
| `slope.p / slope.q` | Exaktes vertikales/horizontales Blockverhältnis; unterstützte reduzierte Presets reichen von 1:4 bis 4:1. |
| `height16 / knee16` | Firsthöhe beziehungsweise Kniestock in hB, strukturell 0–2048; der Höheninspektor fordert einen positiven First. |
| `overhang16` | Überhang 0–128 hB. |
| `upperSlope` | Oberes Steigungsverhältnis einer Mansarde. |
| `breakRatio` | Bruchposition innerhalb des Mansardenlaufs, z. B. 1/3; muss zwischen 0 und 1 liegen. |
| `family` | Deckungsfamilie übernehmen, builtin oder explizite Familie des zugehörigen Dachs. |

## roofconfig

**Dach · roofConfig**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `family` | Aktive Vorlagenfamilie; builtin verwendet die eingebauten Quellen. |
| `variants` | Optionale Copycat-Quellen für slope, eave, edge, ridge, hip und valley. |
| `wall` | Segment-/Musterquelle für Giebel- beziehungsweise Dachwandfüllung. |
| `families[].id / name` | Stabile Kennung und Anzeigename einer eigenen Dachfamilie. |
| `families[].slopes` | Von der Familie unterstützte exakte Steigungsverhältnisse. |
| `families[].thickness16` | Deckungsdicke 1–16 hB. |
| `families[].cells` | Periodische Vorlagen je Steigung, Variante, horizontaler Phase, vertikalem Schnitt und gespeicherter Formversion. |

## stairwell

**Treppenhaus · stairwell**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `layout` | STRAIGHT, L oder U. |
| `width` | 1–8 Blöcke Laufbreite. |
| `landingDepth` | Podesttiefe von Laufbreite bis 16 Blöcke. |
| `headroom` | 2–8 Blöcke Kopffreiheit. |
| `clockwise` | Legt die Wendungsrichtung fest. |
| `enclosed` | Baut die Hülle mit. |
| `tread / landing / enclosure` | Optionale Copycat-Quellen für Stufen, Podeste und Hülle; leere Quellen verwenden den eingebauten Standard. |

## elevatorshaft

**Aufzugschacht · elevatorShaft**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `width / depth` | Je 1–16 Blöcke. |
| `headroom` | 2–8 Blöcke. |
| `enclosed` | Optionale Hülle. |
| `enclosure` | Optionale Copycat-Hüllvorlage. Die Instanz bestimmt, welche Stockwerke Öffnungen erhalten. |

## serviceshaft

**Versorgungsschacht · serviceShaft**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `width / depth` | Je 1–16 Blöcke. |
| `enclosed` | Optionale Hülle. |
| `enclosure` | Optionale Copycat-Quelle der Hülle. |

## accessinstances

**Gebäude · accessInstances**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `id / source` | Stabile Instanzkennung und gewählte Treppen-/Schachtdefinition. |
| `position` | X/Z im Bereich −128…128 Blöcke; Y wird aus den Geschossen bestimmt und muss 0 bleiben. |
| `quarterTurns` | 0–3 Vierteldrehungen um Y. |
| `fromStorey / toStorey` | Erstes und letztes betroffenes Geschoss als stabile Instanz-ID, nicht als wiederverwendbare STOREY-Quell-ID. |
| `stops` | Haltestelleninstanzen des Aufzugschachts; leere Liste bezeichnet die passenden Geschosse im Bereich. |

## transforms

**Muster · transforms**

| Feld | Bedeutung / Einheit |
| --- | --- |
| `offset` | X/Y/Z-Versatz je Eingang in ganzen Blöcken. |
| `axis` | Minecraft-X/Y/Z. |
| `quarterTurns` | 0–3 Vierteldrehungen. Dreht Blockpositionen und enthaltene Formen gemeinsam. |

## semantische-anschluesse

Diese Quellen werden aus den Inspektorstrukturen als zusätzliche Graphanschlüsse abgeleitet. `*` steht für den jeweiligen Listenindex.

| Anschluss | Quelle | Ursprung |
| --- | --- | --- |
| `semantic:room:filler` | [Segmentwerte](Knoten-Gebaeudegraph.md#wertfamilien) | `roomLayout.filler` |
| `semantic:room:interior` | [Segmentwerte](Knoten-Gebaeudegraph.md#wertfamilien) | `roomConfig.interiorSource` |
| `semantic:room:partition` | [Segmentwerte](Knoten-Gebaeudegraph.md#wertfamilien) | `roomConfig.defaultPartition` |
| `semantic:facade:source` | [Segmentwerte](Knoten-Gebaeudegraph.md#wertfamilien) | `facade.source` |
| `semantic:facade:filler` | [Segmentwerte](Knoten-Gebaeudegraph.md#wertfamilien) | `facade.filler` |
| `semantic:opening:*:mask` | [Copycat-Werte](Knoten-Gebaeudegraph.md#wertfamilien) | `features.openings.*.maskSource` |
| `semantic:opening:*:inset` | [Copycat-Werte](Knoten-Gebaeudegraph.md#wertfamilien) | `features.openings.*.insetSource` |
| `semantic:column:*:profile` | [Segmentwerte](Knoten-Gebaeudegraph.md#wertfamilien) | `features.extensions.*.profileSource` |

Zielwände und Dachunterseiten sind spät gebundene Oberflächenreferenzen. Sie dürfen keine Raum→Dach→Raum-Auswertungszyklen erzeugen. Ein Schema-3-Dokument trägt diese Absichten weiter, bis der vollständige Gebäudekontext vorhanden ist. [Praktischer Ablauf](Raumaufbau-und-Fassaden.md)

## roofpart

**Dach-Grundform · part.roof** (lokale Mod)

| Feld | Bedeutung / Einheit |
| --- | --- |
| `rise / run` | Exaktes Steigungsverhältnis; eines der angebotenen Dach-Presets. |
| `phase` | Horizontale Periodenposition von 0 bis run−1. |
| `slice` | Vertikaler Periodenschnitt von −1 bis rise. Ein vollständig leerer Schnitt wird nicht übernommen. |
| `thickness` | Deckungsdicke von 1–16 hB. |
| `variant` | slope, eave, edge, ridge, hip, valley, corner oder cap. |

Diese Quelle erzeugt exakte schräge Geometrie. `exact` und `exactClip` sind geometrische Ergebnisdaten, keine frei einzustellenden Zahlenfelder des Inspektors.

## Zusätzliche semantische Anschlüsse

Die Verbindungsauswahl bietet die folgenden typisierten Sonderraumanschlüsse an:

| Anschluss | Akzeptierte Quellen |
| --- | --- |
| `semantic:bay:wall` | `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |
| `semantic:bay:front` | `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |
| `semantic:bay:floor` | `FLOOR`, `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |
| `semantic:bay:ceiling` | `CEILING`, `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |
| `semantic:dormer:wall` | `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |
| `semantic:dormer:front` | `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |
| `semantic:dormer:roof` | `COPYCAT`, `COPYCAT_MIRROR`, `COPYCAT_ROTATE`, `COPYCAT_UNION`, `COPYCAT_INTERSECTION`, `COPYCAT_DIFFERENCE`, `COPYCAT_SYMMETRIC_DIFFERENCE` |
| `semantic:bay:*:source` | `BAY_WINDOW` |
| `semantic:dormer:*:source` | `DORMER` |
