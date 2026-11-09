# Boolesche Segmentoperatoren

> Stand: **8. November 2026**. Beschreibt die implementierten Segmentoperatoren im gemeinsamen Fachkern und in **Graphstudio 0.4**. [Entwicklungsstand](Entwicklungsstand.md)

**Status: implementiert im aktuellen Graphstudio-Quellstand und im gemeinsamen Fachkern.** Die vier Knotentypen sind im Gebäudegraphen auswählbar und ihre Ergebnisse lassen sich mit weiteren Segmentknoten verbinden. Die veröffentlichte Mod 0.6.0 kennt diese neuen IDs noch nicht; für den Spielimport ist ein Mod-Build mit dieser Erweiterung nötig.

Copycat-Werte besitzen entsprechende eigene [Copycat-Operatoren](Knoten-Gebaeudegraph.md#copycat_union). Sie verarbeiten die Geometrie einer einzelnen Blockzelle und können anschließend als Quelle eines Segments dienen. Segmentwerte lassen sich zusätzlich mit **Segment rotieren** und **Segment spiegeln** weiterverarbeiten.

## Gemeinsamer Vertrag

Die Knoten nehmen vollständige Segmentergebnisse an: `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE` sowie die vier Operatorergebnisse. Ihr Ausgang ist ein weiterverwendbarer **Segmentwert**, keine Liste voneinander unabhängiger Copycat-Ausgänge.

Die Eingänge liegen im selben lokalen Blockkoordinatensystem. Es gibt keine automatische Skalierung und keine automatische Verschiebung zum kleinsten belegten Punkt. Eine fehlende Zelle bedeutet leere Geometrie. Die boolesche Operation wird **positionsweise auf die Copycat-Geometrien innerhalb der jeweiligen Blockzelle** abgebildet. Ein bloßer Vergleich ganzer belegter Zellen wäre bei Teilformen falsch.

Normale Vollblöcke werden geometrisch als volle Zellen behandelt. Ihre Block- und Itemherkunft bleibt erhalten; eine notwendige Teilumwandlung in Copycat-Geometrie wird ausdrücklich abgewiesen. Als ausschneidbare Quelle kann stattdessen eine gespeicherte volle Copycat-Form verwendet werden.

## SEGMENT_UNION

**Segment-Vereinigung.** Mindestens 2, höchstens 16 geordnete Segmenteingänge. Belegte Teilvolumen werden vereinigt; doppelte Belegung erzeugt keine doppelte Geometrie. Pro Zelle entspricht das dem Copycat-Operator UNION.

**Anschlüsse:** `in:0` bis `in:15`, ausschließlich Segmentwerte. **Inspektor:** Eingangszahl, Materialmodus (Separiert/Gruppiert), gemeinsame Materialrolle; allgemeiner Knotenname. **Ausdehnung:** gemeinsame umschließende Eingabeausdehnung bei unverändertem lokalen Ursprung.

## SEGMENT_INTERSECTION

**Segment-Schnitt.** Mindestens 2, höchstens 16 Segmenteingänge. Pro Zelle bleibt die gemeinsame Teilgeometrie aller Eingänge; eine dort fehlende Zelle macht das Ergebnis leer. Entspricht Copycat-INTERSECTION.

**Anschlüsse:** `in:0` bis `in:15`. **Inspektor:** Eingangszahl, Materialmodus und gemeinsame Rolle; separiert stammt die Materialherkunft vom ersten Eingang. **Ausdehnung:** gemeinsame Schnittausdehnung der Eingaberahmen, ohne automatische Neuzentrierung auf die verbleibende Geometrie.

## SEGMENT_DIFFERENCE

**Segment-Differenz A − B.** Genau zwei geordnete Segmenteingänge. Pro Zelle wird B aus A ausgeschnitten; fehlt B, bleibt A. Fehlt A, entsteht nichts. Entspricht Copycat-DIFFERENCE.

**Anschlüsse:** `in:0` = A, `in:1` = B. **Inspektor:** A/B tauschen, Materialmodus und gemeinsame Rolle; separiert bleibt die Herkunft von A erhalten. **Ausdehnung:** die deklarierte Ausdehnung von A, auch wenn Ränder vollständig verschwinden.

## SEGMENT_SYMMETRIC_DIFFERENCE

**Symmetrische Segment-Differenz.** Mindestens 2, höchstens 16 Segmenteingänge. Pro Teilvolumen bleiben ungerade Überdeckungszahlen erhalten. Bei drei identischen Eingängen bleibt die Geometrie bestehen; sie darf nicht wie bei „alles Überlappende entfernen“ verschwinden. Entspricht iteriertem Copycat-XOR.

**Anschlüsse:** `in:0` bis `in:15`. **Inspektor:** Eingangszahl, Separiert/Gruppiert, gemeinsame Rolle. **Ausdehnung:** gemeinsame umschließende Eingabeausdehnung ohne Verschieben des Ursprungs.

## Auswertung und Grenzen

Die Auswertung besucht nur relevante belegte Blockpositionen und verwendet je Zelle den bestehenden Copycat-Compiler einschließlich exakter Schnittgeometrie, Materialgruppen und Quellenbelegen. Gleiche Zellkombinationen werden innerhalb einer Operation zwischengespeichert. Leere Zwischenergebnisse dürfen weiter verbunden werden. Materialien bleiben separiert erhalten; Gruppiert setzt die gemeinsame Gebäuderolle. Die üblichen Grenzen von acht Materialgruppen und 32 Formteilen pro Zelle gelten weiter.

Unveränderte normale Vollblöcke behalten ihre Block- und Itemherkunft. Eine Operation, die einen normalen Vollblock teilweise ausschneiden oder mit Copycat-Materialien überlagern müsste, wird ausdrücklich abgewiesen. Als ausschneidbare Quelle einen gespeicherten vollen Copycat verwenden. So wird ein normaler Baublock nicht still in eine anders abgerechnete Copycat-Form umgewandelt.

Segmente mit semantischen Öffnungen, Säulenprofilen oder Wandanschlüssen werden vor boolescher Auswertung ausdrücklich abgewiesen, bis deren Topologie konsistent mitgeschnitten werden kann. Es werden keine Metadaten still entfernt.
