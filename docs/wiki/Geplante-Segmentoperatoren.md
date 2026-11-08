# Geplante boolesche Segmentoperatoren

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.3** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

**Status: geplant, noch nicht implementiert.** Diese Seite legt einen umsetzbaren Vertrag fest. Die Namen und IDs sind Vorschläge; aktuelle Mod- und Browserimporte akzeptieren sie noch nicht. Sie werden in der Knotenreferenz deshalb nicht als vorhandene Operationen geführt.

## Gemeinsamer Vertrag

Die Knoten nehmen vollständige Segmentergebnisse an: `SEGMENT`, `PATTERN`, `SEGMENT_MIRROR`, `SEGMENT_ROTATE` und später auch die vier Operatorergebnisse. Ihr Ausgang ist ein weiterverwendbarer **Segmentwert**, keine Liste voneinander unabhängiger Copycat-Ausgänge.

Die Eingänge liegen im selben lokalen Blockkoordinatensystem. Es gibt keine automatische Skalierung und keine automatische Verschiebung zum kleinsten belegten Punkt. Eine fehlende Zelle bedeutet leere Geometrie. Die boolesche Operation wird **positionsweise auf die Copycat-Geometrien innerhalb der jeweiligen Blockzelle** abgebildet. Ein bloßer Vergleich ganzer belegter Zellen wäre bei Teilformen falsch.

Normale Vollblöcke müssen für die Geometrieauswertung als volle Zellen behandelt werden. Die Umwandlung beziehungsweise Rückgabe eines solchen Ergebnisses darf nicht still die bisherige Material-, Item- oder Zinkabrechnung ändern; diese Herkunft ist Bestandteil des noch zu implementierenden Vertrags.

## SEGMENT_UNION

**Segment-Vereinigung.** Mindestens 2, höchstens 16 geordnete Segmenteingänge. Belegte Teilvolumen werden vereinigt; doppelte Belegung erzeugt keine doppelte Geometrie. Pro Zelle entspricht das dem Copycat-Operator UNION.

**Anschlüsse:** `in:0` bis `in:15`, ausschließlich Segmentwerte. **Inspektor:** Eingangszahl, Materialmodus (Separiert/Gruppiert), gemeinsame Materialrolle und optionale Rollenüberschreibungen je Eingang; allgemeiner Knotenname. **Ausdehnung:** gemeinsame umschließende Eingabeausdehnung bei unverändertem lokalen Ursprung.

## SEGMENT_INTERSECTION

**Segment-Schnitt.** Mindestens 2, höchstens 16 Segmenteingänge. Pro Zelle bleibt die gemeinsame Teilgeometrie aller Eingänge; eine dort fehlende Zelle macht das Ergebnis leer. Entspricht Copycat-INTERSECTION.

**Anschlüsse:** `in:0` bis `in:15`. **Inspektor:** Eingangszahl, Materialmodus und gemeinsame Rolle; separiert stammt die Materialherkunft vom ersten Eingang. **Ausdehnung:** geometrische Schnittausdehnung, ohne automatische Neuzentrierung.

## SEGMENT_DIFFERENCE

**Segment-Differenz A − B.** Genau zwei geordnete Segmenteingänge. Pro Zelle wird B aus A ausgeschnitten; fehlt B, bleibt A. Fehlt A, entsteht nichts. Entspricht Copycat-DIFFERENCE.

**Anschlüsse:** `in:0` = A, `in:1` = B. **Inspektor:** A/B tauschen, Materialmodus und gemeinsame Rolle; separiert bleibt die Herkunft von A erhalten. **Ausdehnung:** die deklarierte Ausdehnung von A, auch wenn Ränder vollständig verschwinden.

## SEGMENT_SYMMETRIC_DIFFERENCE

**Symmetrische Segment-Differenz.** Mindestens 2, höchstens 16 Segmenteingänge. Pro Teilvolumen bleiben ungerade Überdeckungszahlen erhalten. Bei drei identischen Eingängen bleibt die Geometrie bestehen; sie darf nicht wie bei „alles Überlappende entfernen“ verschwinden. Entspricht iteriertem Copycat-XOR.

**Anschlüsse:** `in:0` bis `in:15`. **Inspektor:** Eingangszahl, Separiert/Gruppiert, gemeinsame Rolle und optionale Eingangsrollen. **Ausdehnung:** gemeinsame umschließende Eingabeausdehnung ohne Verschieben des Ursprungs.

## Effiziente Auswertung bei gleichem Ergebnis

Die semantische Referenz ist die Abbildung auf die bestehenden Copycat-Operatoren. Die Implementierung muss dafür keine riesigen ausgeschriebenen Knotengraphen erzeugen. Bevorzugt wird:

1. Segmente einmal auswerten und als spärliche Zuordnung **Blockposition → Teilgeometrie und Herkunft** halten.
2. Nur relevante Positionen besuchen: Schlüsselvereinigung für UNION/XOR, Schlüsselschnitt für INTERSECTION, A-Schlüssel für DIFFERENCE.
3. Exakt gleiche Eingänge und leere Geometrien früh erkennen. Innerhalb einer Zelle einen passenden Bitmengen- beziehungsweise exakten Volumenalgorithmus verwenden und gleiche Teilgeometrien zwischenspeichern.
4. Normierung und teure Netz-/Vorschaubildung erst am Ergebnis durchführen. Die Laufzeit soll nicht von einer kartesischen Paarung aller Copycat-Quellen abhängen.

Eine schnellere Auswertung hat Vorrang, **wenn Geometrie, Materialien, Vordergrundauflösung, Quellenherkunft und Abrechnung gleich bleiben**. Bloß gleiche äußere Maschen reichen dafür nicht. Rationale Schnittgeometrie darf nicht auf ein gröberes Raster gerundet werden.

## Metadaten und Abnahme

Öffnungen, Säulenprofile, bewusste Freiräume und Wandanschlüsse müssen ihren ursprünglichen Quell- und Instanzbezug behalten. Die boolesche Formänderung darf weder Fensteraufträge verlieren noch entfernte Wandbereiche versehentlich als erforderliche Luft mit noch vorhandener Geometrie kollidieren lassen. Konflikte werden ausdrücklich gemeldet.

Vor einer Registrierung sind Vergleichstests gegen die positionsweise Referenzauswertung nötig: leere/disjunkte/identische Eingänge, Teilüberlappungen innerhalb einer Zelle, drei- und mehrfache XOR-Überdeckung, unterschiedliche Ausdehnungen, gedrehte/gespiegelte Quellen, Materialien und Prioritäten, Metadaten, leere Ergebnisse, JSON-Rundlauf und Mengengrenzen. Anschließend folgen Zeit-/Speichervergleiche für große, spärlich belegte Segmente. Erst mit korrektem Vertrag und Prüfungen wechselt der Status zu implementiert.
