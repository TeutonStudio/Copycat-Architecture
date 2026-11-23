# Dach- und Fassadenkanten

Bei einem Dach mit „Fläche, Dachvorlage“ oder „Fläche, Segmente …“ zeigt der Inspektor unter **Flächenkanten · Dach / Fassade** jede gerade Randstrecke des Grundrisses. Auch Innenhöfe und Einschnitte besitzen eigene Kanten. Der nummerierte Grundriss ordnet die Zeilen ihrer Lage zu. Die Wahl „Dach“/„Fassade“ verwendet eigene Icons.

- **Dach:** Die Dachschräge beginnt an dieser Kante. Die angeschlossene Dachvorlage beziehungsweise die gemeinsamen Dachbauteile liefern die Geometrie.
- **Fassade:** Ein eigener typisierter Segmenteingang erscheint am Dachknoten. Die vollständig ausgewertete Segmenthöhe begrenzt die Fassade. Oberhalb dieser Höhe setzt sich die Dachneigung fort und schließt den übrigen Giebelbereich. Die Höhe ist eine Obergrenze: Gegenüberliegende Dachschrägen können die tatsächliche Fassadenhöhe weiter reduzieren.

Die Fassade wiederholt ihr Segment entlang der Kante, **nicht in der Höhe**. Fenster und freie Stellen des Segments bleiben erhalten. Für exakte schräge Anschlüsse sind Copycat-Segmente mit Tiefe 1 erforderlich. Die Höhe muss fest bestimmbar sein; eine dynamische Y-Achse liefert keine verwendbare Obergrenze. Eine feste Vorschauhöhe am Segment überschreibt dessen tatsächlich ausgewertete Höhe nicht.

Damit entstehen Walmdächer, vollständige Giebel, Giebel mit oberem Walmabschluss und Pultdächer. Beispielsweise ergeben zwei gegenüberliegende Dachkanten und zwei niedrige Fassaden einen oben abgewalmten Giebel. Höhere Fassadensegmente lassen denselben Giebel bis zum First reichen. Kanten, Segmenthöhen und Dachneigung lassen sich unabhängig kombinieren.

Die erste Kantenänderung aktiviert die individuelle Berechnung. In diesem Modus bestimmen die Kanten die Dachform; Dachneigung und Dachverlängerung bleiben einstellbar. Eine manuelle Firsthöhe oder ein zusätzlicher Kniestock wird dabei nicht auf die Segmenthöhe aufgeschlagen. **Bisherige Dachform wiederherstellen** verwendet wieder die gespeicherten allgemeinen Dachparameter. Vorhandene Dächer bleiben ohne individuelle Kantenregeln geometrisch unverändert.

Beim Wechsel von Fassade zu Dach bleibt die Segmentquelle gespeichert, während ihr Anschluss ausgeblendet wird. Ein erneuter Wechsel stellt ihn wieder her. Beim Ändern des Grundrisses behalten unveränderte Kanten ihre geometrischen Kennungen. Neue Randstrecken starten im individuellen Modus als Dach; verschwundene Kanten werden inaktiv. Es erfolgt keine Zuordnung nach der wechselnden Zeilennummer. Änderungen lassen sich mit Undo/Redo zurücknehmen.

Die Regeln gehören dem verwendenden Dach, auch bei einem direkt erzeugenden Ergebnis-Knoten. Die verwendete Dachvorlage und ihre eingefrorene Version werden nicht verändert. Kantenbezogene Dächer verwenden Gebäudegraphschema **18**. Ältere Graphen bleiben lesbar.
