# Reproduzierbare Beispiele

Die JSON-Dateien werden von den bestandenen Fachtests aus denselben Modellen erzeugt, die der Mod verwendet.

- `library.json`: vollständige Startbibliothek als Referenzpaket.
- `inner-corner.json`: L-Innenecke aus zwei rechtwinkligen Leisten.
- `window-module.json`: drei Blöcke breites Fenstersegment mit Freiraumzellen.
- `interlude-module.json`: einblockbreites Zwischenstück mit explizitem Füllbereich.
- `l-building.json`: zwei Geschosse, L-Grundriss, Wände, Platten, Leisten und Satteldach.
- `flat-building.json`: rechteckige Variante mit separatem Flachdach.
- `multi-material-form.json`: Stein-Grundkörper und vordere Deckschicht in separaten Gruppen.

Für einen Einzelentwurf die entsprechende JSON-Datei kopieren und im passenden Editor bei nicht fokussiertem Textfeld Strg+V verwenden, dann speichern. Die Mod prüft Referenzen und vergibt beim Import eine neue ID. `library.json` ist ein Referenzpaket; der Importknopf erwartet jeweils eine einzelne Form, ein einzelnes Modul oder ein Gebäude, nicht das gesamte Paket.

- `nodegraph-form.json`: Vollblock minus vertikale Eckleiste, als bearbeitbarer Differenzgraph. Im Formeditor mit Strg+V importieren und „Graph“ öffnen; danach speichern.

- `nodegraph-material-groups.json`: Eine geteilte Vollblockquelle mit zwei Materialien. Die Vereinigung weist ihren beiden Eingängen die Gruppen Stone und Wood zu. Am Ergebnisknoten „Alles“, „Stone“ oder „Wood“ auswählen. In der Handpistole die beiden Gruppenmaterialien einstellen. Die Quelle benötigt insgesamt 256 Zinkeinheiten.

- `building-graph.json`: Direkt baubares Gebäudegraph-Startbeispiel mit festen Wandsegmenten, Boden, Decke, Raum, Stockwerk und Dach.
- `building-graph-basements.json`: A und BC werden zum festen Wandmuster verbunden. Ein Raum wird je Stockwerk zweimal angeordnet, das Stockwerk für Untergeschoss/Erdgeschoss/Obergeschoss wiederverwendet. Weiße Teppiche belegen die unterste Raumzelle; die Konstruktion umfasst 603 Positionen. Gesamten JSON-Text im Gebäudeeditor mit Strg+V importieren, „Graph“ öffnen und speichern. Die Standardform `full@1` muss vorhanden sein.

- `nodegraph-rotation-xor.json`: Eigener Rotationsknoten mit angeschlossenem Rotation-Wert und symmetrischer Differenz.
- `building-graph-assembly.json`: Copycat-Quellen, Wandsegmente, Boden und Decke bilden direkt ein Stockwerk; das Dach erhält Copycat-Deckung und Giebelsegmente.

- `panel-building/`: geprüftes Panelhaus mit 3/16-Wänden, 2/16-Schichtfenstern, Kantenleisten, vollständigen Quellformen und Modulen. Bedienung, Materialliste und Importhinweise stehen in [der zugehörigen Anleitung](panel-building/README.md).

- `t-building/`: dreigeschossiges T-Testhaus mit acht Materialien, 214 Fensterzellen, erhabenen Quarzmustern, vollständigen Formen und Gebäudegraph. Die [Anleitung](t-building/README.md) beschreibt auch die tatsächlich gebaute Testwelt und ihre erneute Prüfung nach dem Laden.

- `modular-roofs-and-access.json`: zwei unterschiedlich breite Geschosse mit eigenem unterem Teildach und oberem Satteldach, exakter Steigung 1:2, U-Treppenhaus, vorbereitetem Aufzugsschacht und Versorgungsschacht. Verwendet die integrierten Formen `full@1` und `stair@1`. Im Gebäudeeditor importieren; unter **Dach** die Dachebenen und unter **Erschließung** die Kerne bearbeiten. **Gebäude/Außenanlagen platzieren** zeigt die einzeln verschiebbaren Kernflächen.

- `building-roof-peak.json`: einfaches Satteldach mit separater optionaler Copycat-Dachspitze bei ungerader Breite. In Graphstudio als Beispiel auswählbar; Dachspitze im Inspektor auf **Offen** setzen und per Undo vergleichen. Verwendet `full@1` und `bar@1` aus `library.json`. Dachebenen nutzen weiterhin ihre vorhandene First-Segmentvorlage.
