# Reproduzierbare Beispiele

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.3** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

Die Beispiele decken verschiedene Generationen des Editors ab. Ihr Dateiformat und ihre Bibliotheksreferenzen sind wichtiger als ihr sichtbarer Name. Die zugehörigen README-Dateien beschreiben den geprüften Entwurf; daraus wird hier keine neue Spielprüfung abgeleitet.

| Beispiel | Geeigneter Stand | Inhalt |
| --- | --- | --- |
| [Differenzform](../../examples/nodegraph-form.json) | Browser und passende Mod | Vollblock mit Ausschnitt |
| [Rotation / XOR](../../examples/nodegraph-rotation-xor.json) | Browser und passende Mod | Rotationswert und symmetrische Differenz |
| [Materialgruppen](../../examples/nodegraph-material-groups.json) | Browser und passende Mod | Getrennte Gruppen; Kosten nach tatsächlich verwendetem Spielstand prüfen |
| [Älterer Gebäudegraph](../../examples/building-graph.json) | Browser und passende Mod | Räume, Stockwerke und direktes Dach |
| [Untergeschosse](../../examples/building-graph-basements.json) | Browser und passende Mod | Wiederverwendete Räume und Geschosse |
| [Direkter Stockwerkaufbau](../../examples/building-graph-assembly.json) | Browser und passende Mod | Copycats, Wandsegmente, Boden, Decke und Dach |
| [Panelgebäude](Panelgebaeude.md) | Älterer Modulaufbau in der Mod | Dünne Wände, Schichtfenster und Leisten; zugehörige Bibliothek erforderlich |
| [T-Gebäude](../../examples/t-building/building.json) | Aktuelle lokale Mod | Explizite Bauteile und Raumgeometrie; [Bibliothek](../../examples/t-building/library.json) |
| [Handelshof](../../examples/commerce-quarter/building.json) | Aktuelle lokale Mod | Größerer Gebäudeentwurf; [Bibliothek](../../examples/commerce-quarter/library.json) |
| [Industriedepot](../../examples/industrial-depot/building.json) | Aktuelle lokale Mod | Größeres Beispiel mit eigener [Bibliothek](../../examples/industrial-depot/library.json) |

Bibliotheken sind Referenzpakete mit exakten ID@Version-Schlüsseln. Im Browser werden sie über **Bibliothek öffnen** geladen. Im Spiel werden Formen/Module einzeln importiert und die abhängigen Quellen wieder verbunden. Größere aktuelle Dokumente sind kein automatisch kompatibles Browserbeispiel. [Importablauf](Beispiele-und-Import.md)
