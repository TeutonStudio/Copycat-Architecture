# Knotenreferenz: Gebäudegraph

> **Stand dieses Entwicklungszweigs:** Diese Anleitung beschreibt die vollständige Überarbeitung mit Vorschau-Auswahldialog, allen sechs Copycat- und Segmentoperationen sowie dem 2D-Anordnungsraster. Der Browser unterstützt hier 10 Form- und 21 Gebäudeknotentypen. Die Änderungen sind noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Modrelease 0.6.0 bleibt unverändert.

> Stand: **9. November 2026**. Beschreibt den aktuellen gemeinsamen Knotenvertrag. **Graphstudio 0.4** bietet 21 der 32 Gebäudeknotentypen; neue boolesche Knoten benötigen im Spiel einen aktualisierten Build gegenüber dem Modrelease **0.6.0**. [Entwicklungsstand](Entwicklungsstand.md)

[Übersicht](Knotenreferenz.md) · [Gemeinsame und verschachtelte Eigenschaften](Knoten-Eigenschaften.md) · [Einheiten](Einheiten-und-Ausrichtung.md)

Die Anschlusskennungen sind die gespeicherten API-Schlüssel. Ein `*` bezeichnet mehrere oder dynamisch bestimmte Anschlüsse; deren konkrete Kennungen liefert die Auswertung. Die Aufzählung von Feldern beschreibt den gemeinsamen Knotenvertrag; native Zusatzfelder sind im Browser nur verfügbar, wenn dies ausdrücklich genannt wird. Leere Verbindungen bleiben im Entwurf erlaubt, können aber die Ergebnisberechnung verhindern.

## Wertfamilien

Die Anschlusstabellen verwenden diese Familien, damit auch verkettete Operationen eindeutig zugeordnet sind:

| Familie | Zulässige Knotenausgänge |
| --- | --- |
| **Copycat-Werte** | `COPYCAT`, `COPYCAT_MIRROR`, `COPYCAT_ROTATE`, `COPYCAT_UNION`, `COPYCAT_INTERSECTION`, `COPYCAT_DIFFERENCE`, `COPYCAT_SYMMETRIC_DIFFERENCE` |
| **Segmentwerte** | `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE` |

Copycat-Werte beschreiben eine einzelne Blockzelle; Segmentwerte beschreiben ein Bauteil mit seiner lokalen Ausdehnung. Ein Operatorergebnis behält seine Familie und kann wieder mit den entsprechenden Quellen und Operatoren verbunden werden.

## AREA

**Flächengebiet.** Wiederverwendbare zweidimensionale Rasterfläche. Anders als ein Raum darf sie auch Löcher oder getrennte Teile enthalten.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `AREA`.

### Anschlüsse

Keine direkten Graph-Eingänge.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `coordinates` | Rasterpunkte. Flächen/Raumgrundrisse verwenden X/Z, Wege eine geordnete X/Y/Z-Punktfolge. Eine Rastereinheit entspricht einem Block. | Vom Typ / Quellwert abhängig |

## COPYCAT

**Copycat-Referenz.** Referenziert eine konkrete gespeicherte Formversion und ihre Materialrollen.

Im Browser öffnet **Copycat auswählen** den Dialog mit **Grundformen** und **Gespeicherte Formen**. Die gespeicherten Einträge stammen aus persönlichen Formgraphen (**Graph speichern**) oder der geladenen Referenzbibliothek; jede Karte nennt ihre Herkunft und zeigt eine gerenderte Geometrievorschau. Die Suche grenzt die Auswahl ein. Eine Karte markieren, die größere Vorschau drehen und mit **Auswahl übernehmen** einsetzen. **Abbrechen** oder **Escape** lässt die bisherige Referenz unverändert. [Auswahldialog](Graphstudio.md#copycat-auswählen-grundformen-und-gespeicherte-formen)

Eine Bibliotheksform behält ihren genauen Versionsschlüssel. Persönliche Formgraphen und Grundformen werden als feste Formversion in die Referenzbibliothek des Gebäudeentwurfs übernommen. Materialzuordnungen verwenden Block-IDs ohne Vorschauzustände. Spätere Änderungen oder das Löschen eines persönlichen Formgraphen ändern bestehende Quellen nicht. Eine Graphstudio-Sicherung enthält diese Bibliothek. Beim normalen Gebäude-JSON-Export bleiben es Formreferenzen, die im Spiel unter denselben Versionsschlüsseln auflösbar sein müssen.

**Copycat auswählen** in der Werkzeugleiste ersetzt die ausgewählte Copycat-Quelle oder erzeugt beim Bestätigen einen neuen Quellknoten, wenn keine Quelle ausgewählt ist. **Formdatei laden** im Inspektor importiert weiterhin eine einzelne Form-JSON. **Copycat laden** in der Werkzeugleiste verwendet denselben Import und erzeugt bei Bedarf einen Quellknoten.

**Verfügbarkeit:** Mod: implementiert; Graphstudio 0.4: vorhanden, mit Vorschau-Auswahldialog für Grundformen und gespeicherte Formen sowie Einzeldatei-Import.

**Ausgang:** `COPYCAT`.

### Anschlüsse

Keine direkten Graph-Eingänge.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `form` | Fester Bibliotheksschlüssel ID@Version; eine neue Bibliotheksversion ändert die Referenz nicht automatisch. | Vom Typ / Quellwert abhängig |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |
| `surfaces` | Materialrollen-/Oberflächenzuordnungen für die einzelnen Gruppen der Copycat-Quelle. | Vom Typ / Quellwert abhängig |

## SEGMENT_LAYOUT

**Segmentgeometrie.** Beschreibt räumliche Belegung und Regionen eines Bauteils; die XY-Fläche kann von AREA stammen, die Tiefe verläuft in Z.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `SEGMENT_LAYOUT`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `geometry` | `AREA` | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `length` | Lokale Ausdehnung in X in Blöcken; bei verbundenem Layout aus dieser Quelle abgeleitet. | 1…128 |
| `height` | Lokale Höhe in Y in Blöcken; bei Raumgeometrie die Innenhöhe, bei Segmenten die geometrische Ausdehnung. | 1…128 |
| `depth` | Lokale Tiefe in Z in Blöcken; Profile dürfen echte räumliche Tiefe besitzen. | 1…128 |
| `layoutCells` | Explizite Segmentzellen mit X/Y, Quellenindex und Drehung. Bei leerer Liste verwendet die Auswertung das vorgegebene Füll-/Regionsverfahren. | Vom Typ / Quellwert abhängig |

## SEGMENT

**Segment.** Füllt eine Anordnung mit Copycat-Quellen und Regionszuordnungen. Der geschützte Sonderknoten empty_wall bleibt eine freie Wandquelle.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `SEGMENT`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `layout` | `SEGMENT_LAYOUT` | `assembly` gleich `true`; `id` ungleich `empty_wall` |
| `in:*` | [Copycat-Werte](#wertfamilien) | `id` ungleich `empty_wall`; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |
| `region:*` | [Copycat-Werte](#wertfamilien) | `assembly` gleich `true`; `id` ungleich `empty_wall`; dynamisch: `segmentRegions` |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `length` | Lokale Ausdehnung in X in Blöcken; bei verbundenem Layout aus dieser Quelle abgeleitet. | 1…128 |
| `height` | Lokale Höhe in Y in Blöcken; bei Raumgeometrie die Innenhöhe, bei Segmenten die geometrische Ausdehnung. | 1…128 |
| `depth` | Lokale Tiefe in Z in Blöcken; Profile dürfen echte räumliche Tiefe besitzen. | 1…128 |
| `module` | Ältere feste Modulreferenz ID@Version; im neuen expliziten Segment ist stattdessen die Anordnung maßgeblich. | Vom Typ / Quellwert abhängig |
| `block` | Registry-ID eines normalen Baublocks; air beziehungsweise Leerwand hat besondere Freiraumsemantik. | Vom Typ / Quellwert abhängig |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |
| `materialMode` | SEPARATED erhält eingehende Materialzuordnungen, GROUPED verwendet die gewählte gemeinsame Gruppe/Rolle. | `SEPARATED`, `GROUPED` |
| `features` | Öffnungen und Säulenverlängerungen; [alle Unterfelder](Knoten-Eigenschaften.md#segmentfeatures). | Vom Typ / Quellwert abhängig |
| `attachments` | Wandanschlüsse von Kanten; [alle Unterfelder](Knoten-Eigenschaften.md#attachments). | Vom Typ / Quellwert abhängig |

## PATTERN

**2D-Anordnung im Browser:** Auf dem Entwicklungszweig lokal implementiert, noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Raster platziert Segmentinstanzen in X/Y, X/Z oder Z/Y. [Bedienung und Übernahme](https://github.com/TeutonStudio/copycat-architect/wiki/Graphstudio#2d-raster-für-anordnungen)

**Segmente vereinigen.** Ordnet mehrere Segmente in Eingangsreihenfolge aneinander oder transformiert sie frei. Dies ist keine boolesche Vereinigung; überlappende Bauteile unterliegen den vorhandenen Zusammenführungsregeln.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `PATTERN`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:*` | [Segmentwerte](#wertfamilien) | Immer, sofern strukturell gültig; höchstens 16, mindestens 2 |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `patternAxis` | Musterfolge horizontal/X, vertikal/Y, Tiefe/Z oder frei transformiert. Reihenfolge der Eingänge bleibt relevant. | `horizontal`, `vertical`, `depth`, `transform` |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |
| `materialMode` | SEPARATED erhält eingehende Materialzuordnungen, GROUPED verwendet die gewählte gemeinsame Gruppe/Rolle. | `SEPARATED`, `GROUPED` |
| `transforms` | Für jeden Mustereingang Versatz, Weltachse und Vierteldrehung; [Transformationsfelder](Knoten-Eigenschaften.md#transforms). | Vom Typ / Quellwert abhängig |
| `features` | Öffnungen und Säulenverlängerungen; [alle Unterfelder](Knoten-Eigenschaften.md#segmentfeatures). | Vom Typ / Quellwert abhängig |

**Aktionen:** Eingang hinzufügen beziehungsweise letzten Eingang entfernen, innerhalb der vorgegebenen Mindest-/Höchstzahl.

## ROOM_GEOMETRY

**Raumgeometrie.** Definiert Innenfläche und Höhe eines grundrissbasierten Raums; kann eine gemeinsame AREA-Quelle verwenden.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `ROOM_GEOMETRY`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `geometry` | `AREA` | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `coordinates` | Rasterpunkte. Flächen/Raumgrundrisse verwenden X/Z, Wege eine geordnete X/Y/Z-Punktfolge. Eine Rastereinheit entspricht einem Block. | Vom Typ / Quellwert abhängig |
| `height` | Lokale Höhe in Y in Blöcken; bei Raumgeometrie die Innenhöhe, bei Segmenten die geometrische Ausdehnung. | 1…128 |

## FLOOR

**Boden.** Älterer Bodenplattenknoten. Neue Entwürfe verwenden normalerweise SEGMENT als Boden.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang. **Älterer Plattenknoten**, nicht der bevorzugte neue Aufbau.

**Ausgang:** `FLOOR`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:*` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `form` | Fester Bibliotheksschlüssel ID@Version; eine neue Bibliotheksversion ändert die Referenz nicht automatisch. | Vom Typ / Quellwert abhängig |
| `thickness` | Plattendicke in ganzen Blöcken; die ältere Quelle erlaubt 1–4 Lagen. | 1…4 |
| `occupied` | Belegt eine Lage der nutzbaren Raumhöhe statt vollständig außerhalb der Innenhöhe zu liegen. | Vom Typ / Quellwert abhängig |
| `finish` | Optionaler Teppichblock als oberster Bodenbelag; benötigt eine ausreichende tragende Lage und passende vollständige Oberseite. | Vom Typ / Quellwert abhängig |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |

## CEILING

**Decke.** Älterer Deckenplattenknoten. Neue Entwürfe verwenden normalerweise SEGMENT als Decke.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang. **Älterer Plattenknoten**, nicht der bevorzugte neue Aufbau.

**Ausgang:** `CEILING`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:*` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `form` | Fester Bibliotheksschlüssel ID@Version; eine neue Bibliotheksversion ändert die Referenz nicht automatisch. | Vom Typ / Quellwert abhängig |
| `thickness` | Plattendicke in ganzen Blöcken; die ältere Quelle erlaubt 1–4 Lagen. | 1…4 |
| `occupied` | Belegt eine Lage der nutzbaren Raumhöhe statt vollständig außerhalb der Innenhöhe zu liegen. | Vom Typ / Quellwert abhängig |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |
| `attachments` | Wandanschlüsse von Kanten; [alle Unterfelder](Knoten-Eigenschaften.md#attachments). | Vom Typ / Quellwert abhängig |

## ROOM

**Raum.** Verbindet Grundriss oder räumlich platzierte Segmente mit Boden, Decke und Wandsemantik. Im Segmentmodus darf der Umriss offen sein; dann entstehen ohne logische Flächenschließung keine vollständigen Platten.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `ROOM`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `layout` | `ROOM_LAYOUT` | `roomConfig.mode` gleich `SEGMENTS` |
| `floor` | `FLOOR`, [Segmentwerte](#wertfamilien) | `roomConfig.mode` gleich `SEGMENTS` |
| `ceiling` | `CEILING`, [Segmentwerte](#wertfamilien) | `roomConfig.mode` gleich `SEGMENTS` |
| `geometry` | `ROOM_GEOMETRY` | `roomConfig.mode` ungleich `SEGMENTS`; `assembly` gleich `true` |
| `wall:*` | [Segmentwerte](#wertfamilien) | `roomConfig.mode` ungleich `SEGMENTS`; leer verwendet `empty_wall`; dynamisch: `wallRuns` |
| `floor` | `FLOOR`, [Segmentwerte](#wertfamilien) | `roomConfig.mode` ungleich `SEGMENTS`; `assembly` gleich `true` |
| `floor` | `FLOOR` | `roomConfig.mode` ungleich `SEGMENTS`; `assembly` ungleich `true` |
| `ceiling` | `CEILING`, [Segmentwerte](#wertfamilien) | `roomConfig.mode` ungleich `SEGMENTS`; `assembly` gleich `true` |
| `ceiling` | `CEILING` | `roomConfig.mode` ungleich `SEGMENTS`; `assembly` ungleich `true` |
| `corner` | [Copycat-Werte](#wertfamilien) | `roomConfig.mode` ungleich `SEGMENTS` |
| `edge:*` | [Segmentwerte](#wertfamilien) | `roomConfig.mode` ungleich `SEGMENTS`; dynamisch: `roomEdges` |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `height` | Lokale Höhe in Y in Blöcken; bei Raumgeometrie die Innenhöhe, bei Segmenten die geometrische Ausdehnung. | 1…128 |
| `rectangles` | Ältere Grundrissrechtecke mit x, z, width, length in ganzen Blöcken. Bei neuer Geometrie wird der Grundriss aus der Quelle bestimmt. | Vom Typ / Quellwert abhängig |
| `roomConfig` | Raumart, optionale Höhe und innere Wandquellen; [Unterfelder](Knoten-Eigenschaften.md#roomconfig). | Vom Typ / Quellwert abhängig |
| `roomConfig.mode` | FOOTPRINT verwendet einen Grundriss, SEGMENTS eine platzierte Raumanordnung. | `FOOTPRINT`, `SEGMENTS` |
| `joinWallEnds` | Verbindet Wandendbereiche in expliziten Räumen. Fehlende Altwerte und ein ausdrückliches false können unterschiedliche Altsemantik erhalten. | Vom Typ / Quellwert abhängig |

## STOREY

**2D-Anordnung im Browser:** Auf dem Entwicklungszweig lokal implementiert, noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Raster platziert Rauminstanzen in X/Z bei Zusammensetzung **Aus Räumen**. [Bedienung und Übernahme](https://github.com/TeutonStudio/copycat-architect/wiki/Graphstudio#2d-raster-für-anordnungen)

**Stockwerk.** Platziert Raumquellen und löst gemeinsame Wände sowie Fassaden auf. Alternativ bleibt der ältere Direkt-Wandmodus verfügbar.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `STOREY`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `wall:*` | [Segmentwerte](#wertfamilien) | `storeyMode` gleich `walls`; leer verwendet `empty_wall`; dynamisch: `wallRuns` |
| `floor` | `FLOOR`, [Segmentwerte](#wertfamilien) | `storeyMode` gleich `walls`; `assembly` gleich `true` |
| `floor` | `FLOOR` | `storeyMode` gleich `walls`; `assembly` ungleich `true` |
| `ceiling` | `CEILING`, [Segmentwerte](#wertfamilien) | `storeyMode` gleich `walls`; `assembly` gleich `true` |
| `ceiling` | `CEILING` | `storeyMode` gleich `walls`; `assembly` ungleich `true` |
| `corner` | [Copycat-Werte](#wertfamilien) | `storeyMode` gleich `walls` |
| `in:*` | `ROOM` | `storeyMode` ungleich `walls`; höchstens 16, mindestens 1; zusätzlicher leerer Anschluss wächst bei Belegung |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `storeyMode` | rooms setzt Raumquellen zusammen; walls ist der ältere direkte Aufbau aus Wänden und Platten. | `rooms`, `walls` |
| `height` | Lokale Höhe in Y in Blöcken; bei Raumgeometrie die Innenhöhe, bei Segmenten die geometrische Ausdehnung. | 1…128 |
| `rectangles` | Ältere Grundrissrechtecke mit x, z, width, length in ganzen Blöcken. Bei neuer Geometrie wird der Grundriss aus der Quelle bestimmt. | Vom Typ / Quellwert abhängig |
| `offsets` | Platzierungsversatz je Eingang in ganzen Blöcken. Raumplätze verwenden X/Z bei Y=0; der Browser zeigt dafür hB im 16-hB-Raster. | Vom Typ / Quellwert abhängig |
| `facade` | Gemeinsame Wand-/Fassadenauswertung im Stockwerk; [Unterfelder](Knoten-Eigenschaften.md#facade). | Vom Typ / Quellwert abhängig |

## ROOF_LEVEL

**Dachebene.** Definiert eine konkrete Dachfläche mit Bezugsgeschoss, Form, Steigung, Höhe und Überhang.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `ROOF_LEVEL`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `area` | `AREA` | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `roofLevel` | Form, Bezug, Steigung und hB-Maße einer Dachebene; [Unterfelder](Knoten-Eigenschaften.md#rooflevel). | Vom Typ / Quellwert abhängig |
| `roofLevel.form` | FLAT flach, PENT Pult, GABLE Sattel, HIP Walm, MANSARD Mansarde. CROSS ist im Modell reserviert und im aktuellen Inspektor nicht wählbar. | `FLAT`, `PENT`, `GABLE`, `HIP`, `MANSARD`, `CROSS` |
| `roofLevel.mode` | HEIGHT gibt eine Firsthöhe vor; ANGLE verwendet die exakte Steigung und gegebenenfalls den Kniestock. | `HEIGHT`, `ANGLE` |

**Statusdetail:** Die Dachebenenberechnung ist implementiert. Nur der Enumwert `CROSS` ist im aktuellen Dachform-Menü ausgeblendet; eine leere Dachanordnung darf nicht mit fehlender Dachimplementierung verwechselt werden.

## ROOF_LAYOUT

**Dachanordnung.** Sammelt bis zu 16 Dachebenen zu einer Dachanordnung. Eine leere Anordnung ist noch kein auswertbares Dach.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `ROOF_LAYOUT`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:*` | `ROOF_LEVEL` | Immer, sofern strukturell gültig; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |

### Inspektoreigenschaften

Keine zusätzlichen typspezifischen Eingabefelder im Knotenvertrag. Verbindungen, allgemeine Knoteneinstellungen und die nachfolgend genannten Aktionen bleiben davon unabhängig.

## ROOF

**Dach.** Verknüpft Dachanordnung und Deckungs-/Füllvorlagen. Im alten Aufbau sind direkte Flach-/Satteldächer weiterhin möglich.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `ROOF`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `layout` | `ROOF_LAYOUT` | `assembly` gleich `true` |
| `in:*` | [Segmentwerte](#wertfamilien) | `assembly` gleich `true`; höchstens 6; zusätzlicher leerer Anschluss wächst bei Belegung |
| `in:*` | [Copycat-Werte](#wertfamilien) | `assembly` ungleich `true`; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |
| `wall:*` | [Segmentwerte](#wertfamilien) | `assembly` ungleich `true`; dynamisch: `roofWalls` |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |
| `roofType` | Älteres direktes Dach: flat oder gable; keine Dachlevel-Konfiguration. | `flat`, `gable` |
| `ridge` | Ältere Firstrichtung: 0 entlang Z (Nord/Süd), 1 entlang X (Ost/West). | 0…1 |
| `overhang` | Älterer Dachüberhang in ganzen Blöcken. | 0…2 |
| `roofConfig` | Deckungsfamilien, Varianten und Wandfüllung; [Unterfelder](Knoten-Eigenschaften.md#roofconfig). | Vom Typ / Quellwert abhängig |

## STAIRWELL

**Treppenhaus.** Wiederverwendbare Treppenhausdefinition; die tatsächlichen Geschosshöhen kommen von der Gebäudeinstanz.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `STAIRWELL`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `tread` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |
| `landing` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |
| `enclosure` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `stairwell` | Treppenform, Breite, Podest, Kopffreiheit und Hülle; [Unterfelder](Knoten-Eigenschaften.md#stairwell). | Vom Typ / Quellwert abhängig |
| `stairwell.layout` | STRAIGHT gerade, L viertelgewendet, U halbgewendet. | `STRAIGHT`, `L`, `U` |

## ELEVATOR_SHAFT

**Aufzugsvorbereitung.** Definiert einen Schacht mit Haltestellenöffnungen. Ein beweglicher Aufzug wird nicht erzeugt.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `ELEVATOR_SHAFT`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `enclosure` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `elevatorShaft` | Breite, Tiefe, Kopffreiheit und Hülle; erzeugt Schachtgeometrie, keine Aufzugsmaschine. | Vom Typ / Quellwert abhängig |

## SERVICE_SHAFT

**Versorgungsschacht.** Definiert einen Versorgungsschacht und seine Hülle.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `SERVICE_SHAFT`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `enclosure` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `serviceShaft` | Breite, Tiefe und optionale Schachthülle. | Vom Typ / Quellwert abhängig |

## BUILDING

**Gebäude.** Einziger Gebäude-Ergebnisknoten. Ordnet Stockwerksinstanzen, Dach, vertikale Erschließungen und Außenanlagen zum Bauplan.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** kein weiterführender Ausgang; dies ist das Ergebnis..

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `roof` | `ROOF` | Immer, sofern strukturell gültig |
| `access:*` | `STAIRWELL`, `ELEVATOR_SHAFT`, `SERVICE_SHAFT` | Immer, sofern strukturell gültig; höchstens 16; dynamisch: `accessInstances` |
| `level:*` | `STOREY` | Geschossnummer −8…+8, Erdgeschoss 0; höchstens 17 Geschossplätze; dynamisch: `storeyLevels`. |
| `in:*` | `GARDEN`, `PATH` | Immer, sofern strukturell gültig; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `offsets` | Platzierungsversatz je Eingang in ganzen Blöcken. Raumplätze verwenden X/Z bei Y=0; der Browser zeigt dafür hB im 16-hB-Raster. | Vom Typ / Quellwert abhängig |
| `buildingOffset` | Gemeinsamer Lageversatz des Gebäudes im Grundstück, in ganzen Blöcken. | Vom Typ / Quellwert abhängig |
| `sitePaths` | Geordnete Wegpunkte je angeschlossenem Wegeingang, im Gebäudekontext bearbeitbar. | Vom Typ / Quellwert abhängig |
| `accessInstances` | Konkrete Schacht-/Treppenplatzierungen mit stabilen Stockwerksbezügen; [Unterfelder](Knoten-Eigenschaften.md#accessinstances). | Vom Typ / Quellwert abhängig |

## GARDEN

**Garten.** Erzeugt Oberfläche und Bodenaufbau einer Gartenfläche; optional mit Zaun an Außen- und Lochrändern.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `GARDEN`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `geometry` | `AREA` | Immer, sofern strukturell gültig |
| `floor` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `block` | Registry-ID eines normalen Baublocks; air beziehungsweise Leerwand hat besondere Freiraumsemantik. | Vom Typ / Quellwert abhängig |
| `coordinates` | Rasterpunkte. Flächen/Raumgrundrisse verwenden X/Z, Wege eine geordnete X/Y/Z-Punktfolge. Eine Rastereinheit entspricht einem Block. | Vom Typ / Quellwert abhängig |
| `fenced` | Erzeugt einen Zaun auf den Randzellen der Gartenfläche, einschließlich Lochrändern. | Vom Typ / Quellwert abhängig |
| `fenceBlock` | Registry-ID des Zaunblocks. | Vom Typ / Quellwert abhängig |
| `fenceHeight` | Anzahl der Zaunlagen in ganzen Blöcken. | 1…4 |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |

**Zusätzliche Gelände-Einstellungen:** `elevation` (Bodenhöhe), `landscapeOffset` (lokaler X/Y/Z-Versatz), `groundDepth` (1–16), `groundBlock` und `clearance` werden ebenfalls vom Geländeinspektor verwaltet. Die Längen liegen im Blockraster. `elevation`: −64…64 Blöcke; `landscapeOffset.x/z`: −128…128 bei Y=0; `clearance`: 0…16 Blöcke. Wege verwenden 2–64 geordnete Punkte und nur zulässige Oberflächen-/Untergrundblöcke.

## PATH

**Weg.** Erzeugt einen Weg entlang einer geordneten X/Y/Z-Punktfolge; maximal eine Blockstufe pro Rasterabschnitt.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `PATH`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `floor` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `block` | Registry-ID eines normalen Baublocks; air beziehungsweise Leerwand hat besondere Freiraumsemantik. | Vom Typ / Quellwert abhängig |
| `coordinates` | Rasterpunkte. Flächen/Raumgrundrisse verwenden X/Z, Wege eine geordnete X/Y/Z-Punktfolge. Eine Rastereinheit entspricht einem Block. | Vom Typ / Quellwert abhängig |
| `pathWidth` | Wegbreite 1–16 Blöcke; bei gerader Breite liegt der Zusatzrand auf der positiven X-/Z-Seite. | Vom Typ / Quellwert abhängig |
| `groundBlock` | Normaler Baublock unterhalb der Oberfläche. | Vom Typ / Quellwert abhängig |
| `groundDepth` | Gesamter Bodenaufbau einschließlich Oberfläche, 1–16 Blöcke. | Vom Typ / Quellwert abhängig |
| `clearance` | Erforderlicher freier Raum über dem Boden; ein Freiraumauftrag gräbt nicht automatisch vorhandenes Gelände ab. | Vom Typ / Quellwert abhängig |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |

**Zusätzliche Gelände-Einstellungen:** `elevation` (Bodenhöhe), `landscapeOffset` (lokaler X/Y/Z-Versatz), `groundDepth` (1–16), `groundBlock` und `clearance` werden ebenfalls vom Geländeinspektor verwaltet. Die Längen liegen im Blockraster. `elevation`: −64…64 Blöcke; `landscapeOffset.x/z`: −128…128 bei Y=0; `clearance`: 0…16 Blöcke. Wege verwenden 2–64 geordnete Punkte und nur zulässige Oberflächen-/Untergrundblöcke.

## SEGMENT_MIRROR

**Segment spiegeln.** Spiegelt ein vollständiges Segment oder Muster, wahlweise nur dessen Zellpositionen.

**Verfügbarkeit:** Mod und Graphstudio 0.4: implementiert. Der Browser unterstützt X/Y/Z; native semantische Einstellungen benötigen weiterhin den Spiel-Editor.

**Ausgang:** `SEGMENT_MIRROR`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:0` | [Segmentwerte](#wertfamilien) | Immer, sofern strukturell gültig |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `rotationAxis` | Normale der Spiegelebene durch die Segmentmitte; im Browser als Spiegelebene gewählt. | `X`, `Y`, `Z` |
| `mirrorPositionsOnly` | **Nur Blockpositionen spiegeln**: true spiegelt nur die Blockanordnung; false spiegelt zusätzlich jede Copycat-Form in ihrer Zelle. | `true` / `false`; fehlend oder Standard: `false` |
| `materialMode` | SEPARATED erhält eingehende Materialzuordnungen, GROUPED verwendet die gewählte gemeinsame Gruppe/Rolle. | `SEPARATED`, `GROUPED` |
| `role` | Materialrolle der Gebäudepalette, etwa wall oder roof. | Vom Typ / Quellwert abhängig |
| `features` | Öffnungen und Säulenverlängerungen; [alle Unterfelder](Knoten-Eigenschaften.md#segmentfeatures). | Vom Typ / Quellwert abhängig |

## COPYCAT_MIRROR

**Copycat spiegeln.** Spiegelt die Quelle innerhalb ihrer Blockzelle; resultiert wieder in einem Copycat-Anschluss.

**Verfügbarkeit:** Mod und Graphstudio 0.4: implementiert.

**Ausgang:** `COPYCAT_MIRROR`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:0` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `mirror.plane` | Spiegelebene durch die Blockmitte, bezeichnet über ihre Normale. Siehe Ebenentabelle in den [Einheiten](Einheiten-und-Ausrichtung.md). | `X`, `Y`, `Z`, `XY`, `XY_NEGATIVE`, `XZ`, `XZ_NEGATIVE`, `YZ`, `YZ_NEGATIVE`, `BODY_PPP`, `BODY_PPN`, `BODY_PNP`, `BODY_PNN` |

Der Browser bietet **X, Y und Z**. Die weiteren aufgeführten Spiegelebenen gehören zum nativen Editor; entsprechende native Dateien werden im Browser ausdrücklich abgewiesen.

## SEGMENT_ROTATE

**Segment rotieren.** Dreht Segmentpositionen und enthaltene Copycats gemeinsam um eine Weltachse.

**Verfügbarkeit:** Mod und Graphstudio 0.4: implementiert. Der Browser unterstützt X/Y/Z; native semantische Einstellungen benötigen weiterhin den Spiel-Editor.

**Ausgang:** `SEGMENT_ROTATE`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:0` | [Segmentwerte](#wertfamilien) | Immer, sofern strukturell gültig |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `rotationAxis` | Drehachse in Minecraft-X/Y/Z. | `X`, `Y`, `Z` |
| `quarterTurns` | Drehwinkel als Vierteldrehungen: 0, 1, 2, 3 entsprechen 0°, 90°, 180°, 270°. | 0…3 |
| `features` | Öffnungen und Säulenverlängerungen; [alle Unterfelder](Knoten-Eigenschaften.md#segmentfeatures). | Vom Typ / Quellwert abhängig |

## COPYCAT_ROTATE

**Copycat rotieren.** Dreht eine Copycat-Quelle um ihre Blockmitte.

**Verfügbarkeit:** Mod und Graphstudio 0.4: implementiert.

**Ausgang:** `COPYCAT_ROTATE`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:0` | [Copycat-Werte](#wertfamilien) | Immer, sofern strukturell gültig |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `rotationAxis` | Drehachse in Minecraft-X/Y/Z. | `X`, `Y`, `Z` |
| `quarterTurns` | Drehwinkel als Vierteldrehungen: 0, 1, 2, 3 entsprechen 0°, 90°, 180°, 270°. | 0…3 |

## ROOM_LAYOUT

**Raumanordnung.** Platziert Segmentinstanzen mit Position, Drehung und Raumbeitrag; beschreibt darüber hinaus virtuelle Raumgrenzen und Füllvorlagen.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: nicht vorhanden.

**Ausgang:** `ROOM_LAYOUT`.

### Anschlüsse

| Kennung | Akzeptierte Ausgänge | Bedingung / Anzahl |
| --- | --- | --- |
| `in:*` | [Segmentwerte](#wertfamilien) | Immer, sofern strukturell gültig; höchstens 16; zusätzlicher leerer Anschluss wächst bei Belegung |

Zusätzliche Quellen aus Raum-, Fassaden-, Öffnungs- oder Säuleneinstellungen erscheinen als [semantische Anschlüsse](Knoten-Eigenschaften.md#semantische-anschluesse), sofern die jeweilige Konfiguration sie verwendet. Zieloberflächen werden teils erst im Gebäudekontext aufgelöst.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `roomLayout` | Platzierte Segmente, logische Grenzpolygonpunkte und Füllregeln; [alle Unterfelder](Knoten-Eigenschaften.md#roomlayout). | Vom Typ / Quellwert abhängig |


## SEGMENT_UNION

**Segment-Vereinigung.** Positionsweise Operation auf Copycat-Teilformen. [Vertrag und Grenzen](Geplante-Segmentoperatoren.md).

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `SEGMENT_UNION`, weiterverwendbarer Segmentwert.

**Anschlüsse:** `in:*` akzeptiert [Segmentwerte](#wertfamilien). 2–16 geordnete Eingänge; im Inspektor erweiterbar.

**Inspektoreigenschaften:** `materialMode` (SEPARATED/GROUPED), `role` (gemeinsame Materialrolle).

## SEGMENT_INTERSECTION

**Segment-Schnitt.** Positionsweise Operation auf Copycat-Teilformen. [Vertrag und Grenzen](Geplante-Segmentoperatoren.md).

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `SEGMENT_INTERSECTION`, weiterverwendbarer Segmentwert.

**Anschlüsse:** `in:*` akzeptiert [Segmentwerte](#wertfamilien). 2–16 geordnete Eingänge; im Inspektor erweiterbar.

**Inspektoreigenschaften:** `materialMode` (SEPARATED/GROUPED), `role` (gemeinsame Materialrolle).

## SEGMENT_DIFFERENCE

**Segment-Differenz A − B.** Positionsweise Operation auf Copycat-Teilformen. [Vertrag und Grenzen](Geplante-Segmentoperatoren.md).

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `SEGMENT_DIFFERENCE`, weiterverwendbarer Segmentwert.

**Anschlüsse:** `in:*` akzeptiert [Segmentwerte](#wertfamilien). Genau zwei Eingänge: A und B; A/B sind tauschbar.

**Inspektoreigenschaften:** `materialMode` (SEPARATED/GROUPED), `role` (gemeinsame Materialrolle).

## SEGMENT_SYMMETRIC_DIFFERENCE

**Symmetrische Segment-Differenz.** Positionsweise Operation auf Copycat-Teilformen. [Vertrag und Grenzen](Geplante-Segmentoperatoren.md).

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `SEGMENT_SYMMETRIC_DIFFERENCE`, weiterverwendbarer Segmentwert.

**Anschlüsse:** `in:*` akzeptiert [Segmentwerte](#wertfamilien). 2–16 geordnete Eingänge; im Inspektor erweiterbar.

**Inspektoreigenschaften:** `materialMode` (SEPARATED/GROUPED), `role` (gemeinsame Materialrolle).

## COPYCAT_UNION

**Copycat-Vereinigung.** Vereinigt die belegten Teilvolumen aller Eingänge innerhalb derselben Blockzelle. Überlappungen werden geometrisch einmal belegt; die Materialherkunft folgt dem gewählten Materialmodus.

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio 0.4: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `COPYCAT_UNION`, weiterverwendbarer Copycat-Wert.

**Anschlüsse:** `in:*` akzeptiert [Copycat-Werte](#wertfamilien). 2–16 geordnete Eingänge; im Inspektor erweiterbar.

**Inspektoreigenschaften:** `materialMode` (`SEPARATED` erhält die Materialherkunft, `GROUPED` verwendet eine gemeinsame Rolle), `role` (Rolle aus der Gebäudepalette).

Drehungen und Spiegelungen vor oder nach der Operation bleiben wirksam. Ein leeres Zwischenergebnis kann als Eingabe weiterer Copycat-Operationen dienen. Das Ergebnis lässt sich an die Copycat-Eingänge von Segmenten, Böden, Decken und Dächern anschließen. Die Grenzen von acht Materialgruppen und 32 Formteilen pro Blockzelle gelten weiterhin.

## COPYCAT_INTERSECTION

**Copycat-Schnitt.** Behält die gemeinsame Teilgeometrie aller Eingänge. Im separierten Materialmodus stammt die Materialherkunft vom ersten Eingang.

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio 0.4: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `COPYCAT_INTERSECTION`, weiterverwendbarer Copycat-Wert.

**Anschlüsse:** `in:*` akzeptiert [Copycat-Werte](#wertfamilien). 2–16 geordnete Eingänge; im Inspektor erweiterbar.

**Inspektoreigenschaften:** `materialMode` (`SEPARATED` erhält die Materialherkunft, `GROUPED` verwendet eine gemeinsame Rolle), `role` (Rolle aus der Gebäudepalette).

Drehungen und Spiegelungen vor oder nach der Operation bleiben wirksam. Ein leeres Zwischenergebnis kann als Eingabe weiterer Copycat-Operationen dienen. Das Ergebnis lässt sich an die Copycat-Eingänge von Segmenten, Böden, Decken und Dächern anschließen. Die Grenzen von acht Materialgruppen und 32 Formteilen pro Blockzelle gelten weiterhin.

## COPYCAT_DIFFERENCE

**Copycat-Differenz A − B.** Schneidet die Geometrie des zweiten Eingangs B aus dem ersten Eingang A aus. Im separierten Materialmodus bleibt die Materialherkunft von A erhalten.

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio 0.4: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `COPYCAT_DIFFERENCE`, weiterverwendbarer Copycat-Wert.

**Anschlüsse:** `in:*` akzeptiert [Copycat-Werte](#wertfamilien). Genau zwei geordnete Eingänge: `in:0` = A, `in:1` = B. **A / B tauschen** kehrt die Abzugsrichtung um.

**Inspektoreigenschaften:** `materialMode` (`SEPARATED` erhält die Materialherkunft, `GROUPED` verwendet eine gemeinsame Rolle), `role` (Rolle aus der Gebäudepalette).

Drehungen und Spiegelungen vor oder nach der Operation bleiben wirksam. Ein leeres Zwischenergebnis kann als Eingabe weiterer Copycat-Operationen dienen. Das Ergebnis lässt sich an die Copycat-Eingänge von Segmenten, Böden, Decken und Dächern anschließen. Die Grenzen von acht Materialgruppen und 32 Formteilen pro Blockzelle gelten weiterhin.

## COPYCAT_SYMMETRIC_DIFFERENCE

**Symmetrische Copycat-Differenz.** Behält Teilvolumen mit ungerader Überdeckungszahl. Zwei identische Eingänge ergeben ein leeres Ergebnis, drei identische Eingänge behalten die Form.

**Verfügbarkeit:** aktueller Mod-Quellstand und Graphstudio 0.4: implementiert; veröffentlichte Mod 0.6.0: noch nicht enthalten.

**Ausgang:** `COPYCAT_SYMMETRIC_DIFFERENCE`, weiterverwendbarer Copycat-Wert.

**Anschlüsse:** `in:*` akzeptiert [Copycat-Werte](#wertfamilien). 2–16 geordnete Eingänge; im Inspektor erweiterbar.

**Inspektoreigenschaften:** `materialMode` (`SEPARATED` erhält die Materialherkunft, `GROUPED` verwendet eine gemeinsame Rolle), `role` (Rolle aus der Gebäudepalette).

Drehungen und Spiegelungen vor oder nach der Operation bleiben wirksam. Ein leeres Zwischenergebnis kann als Eingabe weiterer Copycat-Operationen dienen. Das Ergebnis lässt sich an die Copycat-Eingänge von Segmenten, Böden, Decken und Dächern anschließen. Die Grenzen von acht Materialgruppen und 32 Formteilen pro Blockzelle gelten weiterhin.
