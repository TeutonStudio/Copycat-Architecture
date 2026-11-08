# Copycat Architect Graphstudio 0.2

Browsereditor für Form- und Gebäudegraphen. Die Beispieldateien stammen aus
[`examples/`](../examples). Die beiden Editoren verwalten getrennte Entwürfe.

## Start

Benötigt werden Node.js 22.12 oder neuer, Java 21, Bash und curl.

```sh
cd web
npm ci
npm test
npm run dev
```

`npm run build` erzeugt die statische Website in `web/dist`. Der Kotlin-Compiler
und dessen Laufzeit werden in der gepinnten Version aus Maven Central geholt.
Die generierten Dateien gehören zum Build.
Der Build legt die Projektlizenz, die npm-Lizenzen sowie Lizenz und Herkunft
der eingebundenen Kotlin-Standardbibliothek im Ausgabeverzeichnis ab.

Der Workflow prüft außerdem mit `./gradlew test` die Minecraft-Seite der
gemeinsamen Domänenlogik. Graphstudio 0.1 entspricht dem öffentlichen
Repository-Spielstand, auf dem es gebaut wird. Zusätzliche Knoten aus einer
neueren lokalen Mod werden erst nach Übernahme ihrer Domänenlogik unterstützt.

## Austausch mit Minecraft

Form- und Gebäudedokumente verwenden die JSON-Felder des Mods. React Flow
übersetzt Verbindungen über die gemeinsame Kotlin-Domänenlogik in diese Felder.
`GraphModels.kt` definiert die Knotentypen, Graphgrenzen und Anschlüsse beider
Editoren. Die Gebäudeanschlüsse ergeben sich aus dem Grundriss, den Eingaben
und den Geschossen. `DiagramEdits` wendet Verbindungsänderungen für
beide Oberflächen an.

Der Web Worker führt `GraphCompiler` und `BuildingGraphCompiler` aus. Die
3D-Ansicht zeigt die berechneten Rasterformen und unterscheidet Materialien
farblich. Minecraft-Texturen und modabhängige Blockmodelle erscheinen als
Farbersatz. Die Bibliothek lässt sich als JSON mit `forms` und `modules` öffnen.
Für ältere Gebäudedokumente wird ein in Minecraft erzeugter Gebäudegraph benötigt.

Formexporte enthalten auch das frisch berechnete `parts`-Ergebnis. Bei Gebäuden
können unvollständige Entwürfe exportiert werden. Zum Bauen müssen die Referenzen
auflösbar und der Gebäudeplan gültig sein. Die Minecraft-Zwischenablage
akzeptiert Graphdokumente bis 28.000 Zeichen.

## GitHub Pages

In den Repository-Einstellungen unter **Pages → Source** die Option
**GitHub Actions** wählen. Der Workflow **Graphstudio** baut den Editor und
veröffentlicht erfolgreiche Builds von `master`. Die Wiki-Seite
**Graphstudio** verlinkt die Website und erläutert den Dateiaustausch.

## Vorschau und Orientierung

**Graph** wertet das endgültige Ergebnis aus, **Selektiert** den angeklickten Knoten. Die gemeinsame Domänenlogik stellt dafür `GraphCompiler.intermediate` und `BuildingGraphCompiler.Evaluation.preview` bereit. Boden/Decke verwenden einen verbundenen Grundriss, ohne andere Raumzweige auszuwerten; das Dach benötigt den Gebäudekontext. Rotationswerte werden als typisierte Werte angezeigt.

Im Formmodus bleibt der Referenzrahmen eine volle Blockzelle, mit 16 hB je Achse. X/Y/Z entsprechen den Minecraft-Weltachsen. Die Seitenleiste teilt eine feste Gesamthöhe mit dem Graphen, solange mindestens einer ihrer Bereiche offen ist. Beide Splitter unterstützen Pointer- und Tastaturbedienung.

Ausrichtungs-Enums übersetzen weiterhin in das vorhandene Quellformat. Nicht unterstützte native Neigungen bleiben deaktiviert; importierte Werte werden beim bloßen Anzeigen nicht umgeschrieben. Raumversätze werden als hB im bisherigen Ganzblockraster angezeigt und beim Export zurückgerechnet. Die neuen Bedienelemente erweitern nicht still den unterstützten Knoten-/Schemabereich.

Die vollständige deutsche [Knotenreferenz](../docs/wiki/Knotenreferenz.md) dokumentiert außerdem den neueren lokalen Modstand; jede Knotenbeschreibung kennzeichnet ihre Browserverfügbarkeit.

## Integration mit dem nativen Mod ab 0.6.0

Der Browser kompiliert den vollständigen aktuellen Fachkern. Der Knoten- und
Anschlussvertrag wird aus derselben `graph-models.json` wie im Mod geladen;
Browseradapter ersetzen JVM-Caches, Bitsets und Ganzzahlarithmetik.

Die Browseroberfläche unterstützt weiterhin die bisherigen acht Form- und neun
Gebäudeknotentypen. Erweiterte native Graphen, Gruppen, exakte Geometrien und
nicht unterstützte Knoteneinstellungen werden beim Import und vor dem Export
mit einem Hinweis auf den nativen Editor abgewiesen. Dadurch können sie nicht
unbemerkt vereinfacht oder mit fehlenden Einstellungen gespeichert werden.
