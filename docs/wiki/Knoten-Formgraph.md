# Knotenreferenz: Formgraph

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.4** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

[Übersicht](Knotenreferenz.md) · [Gemeinsame und verschachtelte Eigenschaften](Knoten-Eigenschaften.md) · [Einheiten](Einheiten-und-Ausrichtung.md)

Die Anschlusskennungen sind die gespeicherten API-Schlüssel. Ein `*` bezeichnet mehrere oder dynamisch bestimmte Anschlüsse; deren konkrete Kennungen liefert die Auswertung. Die Aufzählung von Feldern beschreibt den lokalen Knotenvertrag. Leere Verbindungen bleiben im Entwurf erlaubt, können aber die Ergebnisberechnung verhindern.

## INPUT

**Standard-Copycat.** Eine geometrische Standard-Copycat-Quelle innerhalb einer Blockzelle.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `shape`.

### Anschlüsse

Keine Eingänge.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `rotationAxis` | Drehachse in Minecraft-X/Y/Z; NORMAL bezeichnet die lokale Normale. | `X`, `Y`, `Z`, `NORMAL` |
| `quarterTurns` | Drehwinkel als Vierteldrehungen: 0, 1, 2, 3 entsprechen 0°, 90°, 180°, 270°. | 0…3 |
| `part.source` | Registry-ID der gewählten Grundform; Dachquelle und Größenparameter hängen vom Typ ab. | `copycats:copycat_block`, `copycats:copycat_slab`, `copycats:copycat_stairs`, `create:copycat_panel`, `copycats:copycat_slice`, `copycats:copycat_vertical_slice`, `copycats:copycat_corner_slice`, `copycats:copycat_layer`, `copycats:copycat_beam`, `copycats:copycat_byte`, `create:copycat_step`, `copycats:copycat_vertical_step`, `copycats:copycat_half_panel`, `copycats:copycat_flat_pane`, `copycats:copycat_byte_panel`, `copycats:copycat_half_layer`, `copycats:copycat_vertical_half_layer`, `copycats:copycat_stacked_half_layer`, `copycat_architect:copycat_roof` |
| `part.discCut` | Mittige Scheibe: 0 ganz, 1 halb, 2 Viertel; verkleinert die Fläche, nicht die Dicke. | 0…2 |
| `part.size` | Quellenspezifische Größenstufe; die maximale Stufe hängt von der Grundform ab. | 1…8 |
| `part.top` | Gespeicherter oberer/unterer Montagezustand des älteren Formats. Im Browser wird die Ausrichtung über Neigung angeboten. | Vom Typ / Quellwert abhängig |
| `part.group` | Materialgruppen-ID der Quelle im Formdokument. | Vom Typ / Quellwert abhängig |
| `part.turn` | Gespeicherte Y-Drehung. Der Browser bezeichnet 2/3/0/1 als Nord/Ost/Süd/West. | 0…3 |
| `part.offset` | Versatz X/Y/Z in hB; jede Komponente −15…15 und die gesamte Form muss innerhalb des Blocks bleiben. | Vom Typ / Quellwert abhängig |
| `part.tilt` | Montage-Vierteldrehung im gespeicherten Quellformat; nur zulässige Orientierungen des jeweiligen Typs. | 0…3 |
| `part.spin` | Drehung um die lokale Quellnormale vor der Montage. | 0…3 |

**Zusätzliche Dachquellen-Einstellungen:** Bei `part.source = copycat_architect:copycat_roof` öffnet der Inspektor die [Parameter `part.roof`](Knoten-Eigenschaften.md#roofpart). Das sind eigene Eingabefelder der lokalen Mod und keine bereits verfügbaren Browserfelder.

## UNION

**Vereinigung.** Vereinigt die belegten Bereiche aller Eingänge; Überlappung wird einmal belegt.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `shape`.

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0`…`15` | `shape` | 2 erforderliche bis 16 Eingänge. |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `materialMode` | SEPARATED erhält eingehende Materialzuordnungen, GROUPED verwendet die gewählte gemeinsame Gruppe/Rolle. | `SEPARATED`, `GROUPED` |
| `materialGroup` | Gemeinsame Ergebnisgruppe bei GROUPED; muss im Formdokument existieren. | Vom Typ / Quellwert abhängig |
| `inputGroups` | Optionale Materialgruppen je Eingang. Leerer Eintrag übernimmt die eingehende Gruppe. | Vom Typ / Quellwert abhängig |

**Aktionen:** Eingang hinzufügen beziehungsweise letzten Eingang entfernen, innerhalb der vorgegebenen Mindest-/Höchstzahl.

## INTERSECTION

**Schnitt.** Behält nur die gemeinsame Geometrie aller Eingänge. Die eingehende Materialherkunft stammt bei separierter Auswertung vom ersten Eingang.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `shape`.

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0`…`15` | `shape` | 2 erforderliche bis 16 Eingänge. |

### Inspektoreigenschaften

Keine zusätzlichen typspezifischen Eingabefelder im Knotenvertrag. Verbindungen, allgemeine Knoteneinstellungen und die nachfolgend genannten Aktionen bleiben davon unabhängig.

**Aktionen:** Eingang hinzufügen beziehungsweise letzten Eingang entfernen, innerhalb der vorgegebenen Mindest-/Höchstzahl.

## DIFFERENCE

**Differenz A − B.** Berechnet A minus B. Ein Tausch der beiden Eingänge ändert das Ergebnis.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `shape`.

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0` (A) | `shape` | Ausgangskörper. |
| `1` (B) | `shape` | Abzuziehender Körper. |

### Inspektoreigenschaften

Keine zusätzlichen typspezifischen Eingabefelder im Knotenvertrag. Verbindungen, allgemeine Knoteneinstellungen und die nachfolgend genannten Aktionen bleiben davon unabhängig.

**Aktion:** A/B tauschen kehrt die Reihenfolge um.

## SYMMETRIC_DIFFERENCE

**Symmetrische Differenz.** Behält Bereiche mit ungerader Überdeckungszahl. Bei zwei Eingängen verschwindet die gemeinsame Überlappung.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `shape`.

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0`…`15` | `shape` | 2 erforderliche bis 16 Eingänge. |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `materialMode` | SEPARATED erhält eingehende Materialzuordnungen, GROUPED verwendet die gewählte gemeinsame Gruppe/Rolle. | `SEPARATED`, `GROUPED` |
| `materialGroup` | Gemeinsame Ergebnisgruppe bei GROUPED; muss im Formdokument existieren. | Vom Typ / Quellwert abhängig |
| `inputGroups` | Optionale Materialgruppen je Eingang. Leerer Eintrag übernimmt die eingehende Gruppe. | Vom Typ / Quellwert abhängig |

**Aktionen:** Eingang hinzufügen beziehungsweise letzten Eingang entfernen, innerhalb der vorgegebenen Mindest-/Höchstzahl.

## ROTATE

**Copycat rotieren.** Dreht den verbundenen Copycat-Zweig. Eine angeschlossene Rotation übersteuert den lokalen Achsen-/Winkelwert.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `shape`.

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0` | `shape` | Zu drehende Geometrie, erforderlich. |
| `1` | `rotation` | Optionaler externer Rotationswert; leer verwendet die lokalen Werte. |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `rotationAxis` | Drehachse in Minecraft-X/Y/Z; NORMAL bezeichnet die lokale Normale. | `X`, `Y`, `Z`, `NORMAL` |
| `quarterTurns` | Drehwinkel als Vierteldrehungen: 0, 1, 2, 3 entsprechen 0°, 90°, 180°, 270°. | 0…3 |

## ROTATION

**Rotation.** Ein typisierter Wert aus Achse und Winkel; liefert keine Geometrie.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** `rotation`.

### Anschlüsse

Keine Eingänge.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `rotationAxis` | Drehachse in Minecraft-X/Y/Z; NORMAL bezeichnet die lokale Normale. | `X`, `Y`, `Z`, `NORMAL` |
| `quarterTurns` | Drehwinkel als Vierteldrehungen: 0, 1, 2, 3 entsprechen 0°, 90°, 180°, 270°. | 0…3 |

## OUTPUT

**Ergebnis-Copycat.** Einziger Form-Ergebnisknoten. Ein leeres oder unverbundenes Ergebnis ist nicht druckbar.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden, mit älterem Funktionsumfang.

**Ausgang:** kein weiterführender Ausgang; dies ist das Ergebnis..

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0` | `shape` | 1 erforderliche bis 1 Eingänge. |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `previewGroup` | Nur sichtbare Vorschaugruppe; verändert weder Ausgabegeometrie noch den Bauauftrag. | Vom Typ / Quellwert abhängig |

## MIRROR

**Copycat spiegeln.** Spiegelt Geometrie an einer ausgewählten Ebene durch die Blockmitte; diagonale Körpernormalen verwenden die exakte Geometrieauswertung.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden (Spiegelebenen X/Y/Z im Browser).

**Ausgang:** `shape`.

### Anschlüsse

| Kennung | Typ | Verwendung |
| --- | --- | --- |
| `0` | `shape` | 1 erforderliche bis 1 Eingänge. |

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `mirror.plane` | Spiegelebene durch die Blockmitte, bezeichnet über ihre Normale. Siehe Ebenentabelle in den [Einheiten](Einheiten-und-Ausrichtung.md). | `X`, `Y`, `Z`, `XY`, `XY_NEGATIVE`, `XZ`, `XZ_NEGATIVE`, `YZ`, `YZ_NEGATIVE`, `BODY_PPP`, `BODY_PPN`, `BODY_PNP`, `BODY_PNN` |

## SAVED

**Gespeicherter Copycat.** Eine unabhängige Kopie einer gespeicherten Formversion als neue Quelle.

**Verfügbarkeit:** lokale Mod: implementiert; Graphstudio 0.4: vorhanden.

**Ausgang:** `shape`.

### Anschlüsse

Keine Eingänge.

### Inspektoreigenschaften

| Feld | Bedeutung | Werte / Grenze |
| --- | --- | --- |
| `savedCopycat` | Unabhängige Momentaufnahme mit ID, Version, Name und Teilen einer gespeicherten Form; die Bibliotheksvorlage wird nicht verändert. | Vom Typ / Quellwert abhängig |
| `rotationAxis` | Drehachse in Minecraft-X/Y/Z; NORMAL bezeichnet die lokale Normale. | `X`, `Y`, `Z`, `NORMAL` |
| `quarterTurns` | Drehwinkel als Vierteldrehungen: 0, 1, 2, 3 entsprechen 0°, 90°, 180°, 270°. | 0…3 |
