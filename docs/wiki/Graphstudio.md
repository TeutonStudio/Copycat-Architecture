# Graphstudio

[**Gebäudegraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=building) · [**Formgraph öffnen**](https://teutonstudio.github.io/copycat-architect/?editor=form)

Im Graphstudio bearbeitest du Copycat-Formen und Gebäudegraphen im Browser. Über **Beispiel öffnen** stehen unter anderem ein Formgraph mit Rotation und ein Gebäude mit Untergeschossen zur Verfügung.

Die Links öffnen den interaktiven Editor als eigene Webseite. Formen und Gebäude haben getrennte Entwürfe; du kannst zwischen beiden wechseln. Zum Sichern lade deinen Entwurf als JSON herunter, bevor du die Seite schließt oder neu lädst.

1. Öffne ein Beispiel oder importiere ein Minecraft-Dokument mit **JSON öffnen**.
2. Wähle einen Knotentyp und füge ihn ein. Ziehe die Anschlüsse aufeinander oder wähle die Quelle im Inspektor.
3. Bei eigenen Formen und Wandmodulen lade die zugehörige JSON-Bibliothek über **Bibliothek öffnen**. Der Editor löst die gespeicherten Referenzen gegen diese Bibliothek auf.
4. Prüfe das Ergebnis in der drehbaren 3D-Vorschau. Sie zeigt Geometrie, Materialfarben und den berechneten Bedarf.
5. Über **JSON exportieren** erhältst du ein Dokument für den Formeditor oder den Bauplantisch. Mit **Minecraft-JSON kopieren** kannst du den vollständigen JSON-Text direkt in den Zwischenablageimport im Spiel übernehmen. Für einen Gebäudeentwurf müssen alle benötigten Formen und Module im Spiel verfügbar sein.

Die 3D-Vorschau benötigt WebGL 2. Ihre Materialfarben stehen stellvertretend für Minecraft-Texturen und modabhängige Blockmodelle.

## Unterstützter Spielstand

Graphstudio **0.1** teilt seine Graph- und Geometrieberechnung mit dem öffentlichen Repository-Spielstand. Unterstützt sind Formgraph-Schema **1–2** und Gebäudegraph-Schema **1**. Die Wiki beschreibt zusätzlich die neuere lokale Mod-Entwicklung: deren zusätzliche Knoten wie Segmentanordnung, Raumgeometrie oder Spiegelung sind in Graphstudio 0.1 noch nicht enthalten. Nicht unterstützte Dateien werden mit einer Fehlermeldung abgewiesen. Die mitgelieferten Beispiele funktionieren in beiden Richtungen mit dem passenden Spielstand.

**Bedienung:** Knoten ziehen, Anschlüsse verbinden oder die Quelle im Inspektor auswählen. **Entf** entfernt den ausgewählten Knoten beziehungsweise die Verbindung. Mit **Rückgängig/Wiederholen** oder **Strg+Z/Strg+Umschalt+Z** kannst du Änderungen zurücknehmen und wiederherstellen. Raumgrundrisse, Geschosse, Materialien und Rotationen bearbeitest du im Inspektor.

[Formeditor](Formeditor) · [Gebäudeplanung](Gebaeudeplanung) · [Beispiele und Import](Beispiele-und-Import)
