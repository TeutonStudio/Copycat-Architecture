## Entwicklung 0.7.0: versionierte Referenzen und Druck-/Bauübernahme (2026-11-12)

Dieser Stand erweitert #13, ist aber **keine vollständige Abnahme von #13–16 und
kein Release 0.7.0**. Die noch fehlende Browserabdeckung ist in
[IMPLEMENTATION.md](IMPLEMENTATION.md) benannt; die technischen Modi fehlen weiter.

- Vollständiger Java-21-Build: **563 JUnit-Tests, 561 bestanden, zwei bestehende
  optionale Snapshot-Skips, keine Fehler**. Geprüft sind mehrere benannte Ausgänge,
  Grenzbindungen, Versionspinning, parallele Versionen, Verschachtelung und Limits,
  defekte Ports, unveränderte alte Kanten, Konfliktprüfung, atomare Bibliotheksimporte,
  wiederaufnehmbare Übertragung und Unicode/SHA-256. Druck-/Gebäudevorlagen erhalten
  ihre Referenzpakete über JSON; ungültige gespeicherte Formteile werden vor dem
  Druck neu berechnet. Mehrdeutige Druckausgänge werden abgewiesen.
- `runGameTestServer -PgraphWorldSmoke`: **alle 64 erforderlichen GameTests
  bestanden**, einschließlich tatsächlichem Druck und Bau referenzierter Quellen,
  Konfliktverweigerung vor Materialverbrauch/Weltänderungen, Drucker-NBT,
  Druckerhistorie und Tisch-Eigentumsnachweisen. Der Graph-Turtle führt **603 reale
  Platzierungen** einschließlich Nachladen, Reboot, Unter-/Erd-/Obergeschoss und
  Teppichböden aus.
- Die große klassische Turtle-Prüfung wird mit `-PclassicWorldSmoke` getrennt von
  der Graph-Turtle ausgeführt. Die beiden Optionen schließen sich aus; jede Option
  führt den jeweiligen Weltlauf tatsächlich aus und überspringt den anderen.
  Ein zusätzlicher kombinierter Lauf blieb bei der klassischen Turtle stehen und
  wurde beendet. Die ursprünglichen Default-Tests werden nicht abgeschwächt.
  Der getrennte klassische Lauf besteht ebenfalls **alle 64 erforderlichen
  GameTests** und baut mit Reboot, Pause/Fortsetzung und Materialnachladen
  **1115 reale Positionen**. Beide Varianten prüfen die fertige Welt vollständig.
- `runClient -PversionedGraphSmoke`: echter KilaGraph, Auswahl auslagern, zwei
  Ausgangsslots, identische Geometrie, atomare Undo/Redo-Schritte, expliziter
  Portvergleich, erhaltene inkompatible Kante, reparierbares Paket und Menüübernahme
  in eine ausführbare Druckquelle. Die Quelle lässt sich vom Architektentisch aus
  wieder als Referenzdokument öffnen. Beide GUI-Skalierungen wurden bedient.
- Browser-Fachtests und Produktionsbuild bestanden. Die bestehende Playwright-
  Prüfung besteht einschließlich Raster, Anzahlfarben, Quellenreparatur und
  gespeicherter Formauswahl. Die neue Prüfung bedient echte React-Flow-Auswahl,
  Auslagerung mit zwei Ausgängen, Veröffentlichung von Version 2, unveränderte
  Version-1-Instanz, bewussten Wechsel, Undo, Speichern/Neuladen sowie Übernahme,
  Export und erneutes Öffnen der ausführbaren Quelle. Desktop, Tablet und Mobilgerät
  wurden geprüft; keine Browser-Laufzeitfehler.
- Ressourcen, Sprachschlüssel, ROM-Lua und `git diff --check` ohne Befund. Der
  ursprüngliche lokale Änderungsstand stimmt weiterhin bytegenau mit seiner
  Sicherung überein.

Aufnahmen: [native Ansicht](release-0.7.0/images/architect-versioned-normal.png),
[kompakte Ansicht](release-0.7.0/images/architect-versioned-compact.png),
[CC-Versionsdialog](release-0.7.0/images/architect-versioned-version-compact.png),
[Browser](release-0.7.0/images/subgraph-desktop.png),
[Tablet](release-0.7.0/images/subgraph-tablet.png),
[Mobilgerät](release-0.7.0/images/subgraph-mobile.png).

# Tatsächlich ausgeführte Abnahme

## Entwicklungsstand für 0.7.0: Raster, Anzahl und CC-Unterdialoge, 12.11.2026

Dieser Stand ist **kein veröffentlichtes Release 0.7.0**. Implementiert sind die
Copycat-Anordnung im Wandsegment (#11), Anzahl-Eingänge (#12) und die gemeinsame
CC-Gestaltung der Unterdialoge. Versionierte Untergraphen (#13), Züge (#14),
Bewegungsmittel (#15) und Contraptions (#16) fehlen; diese Issues bleiben offen.

- Abschließender einzelner Java-21-`build` erfolgreich: **542 Tests, 540 bestanden,
  zwei bestehende optionale Snapshot-Tests übersprungen, keine Fehler**.
- Alle **62 verpflichtenden Minecraft-GameTests** bestanden. Der reale Turtle-Lauf
  baut 603 Platzierungen mit Kellern, mehreren Räumen, Nachversorgung und Neustart.
- Browser-Fachtests und Produktionsbuild erfolgreich. Playwright/Chromium prüft
  Desktop, Tablet und Mobilgerät sowie tatsächliche Rasterbedienung, Zahlenpfade,
  neutrale Mehrfachverwendung, Undo/Redo und gelöschte Zahlenquellen.
- Die nativen Clientabnahmen `countArrangementSmoke` und `workshopSmoke` melden
  jeweils `PASSED`. Geprüft sind zwei GUI-Größen, echtes Ziehen und Tastaturbewegung,
  unveränderte Fallback-Maße, atomare Übernahme, Abbrechen, Undo/Redo, Modul- und
  Plattenansichten, Materialien, Bauansichten, Werteingaben und Katalogfilter.
- Ressourcen-, Sprachschlüssel-, ROM-Lua-Prüfung und `git diff --check` erfolgreich.

Die Fachtests decken außerdem Überlauf, Division durch null, Rundung gegen null,
Zyklen, Typfehler, Dimensions- und Flächenkonflikte, das Volumenlimit, alle 128
Segmentspalten, lokale Materialien, explizite Luftzellen und JSON-Rundläufe ab.
Gelöschte Anzahl-Operanden hinterlassen reparierbare Entwürfe mit erhaltenen
Verbindungen; die tatsächliche Planberechnung bleibt bis zur Reparatur gesperrt.

Die Unterdialoge verwenden die native CC-Rahmentextur und dieselben dunklen
Inhaltsflächen wie die Hauptfenster. Die normalen und kompakten Aufnahmen wurden
visuell geprüft: [Raster](release-0.7.0/images/architect-count-arrangement-normal.png),
[kompaktes Raster](release-0.7.0/images/architect-count-arrangement-compact.png),
[Werteingabe](release-0.7.0/images/architect-workshop-compact-number-valid.png),
[Katalog](release-0.7.0/images/architect-workshop-compact-catalog.png) und
[Graphstudio](release-0.7.0/images/count-arrangement-desktop.png).

Der ursprüngliche lokale Arbeitsstand wurde vor dem Abgleich mit Master als
Binärpatch und Archiv neuer Dateien gesichert. Seine nativen Quellen entsprechen
weitgehend dem integrierten Release 0.6.0; spätere Browser-Erweiterungen aus Master
bleiben erhalten. Die lokale CC:Tweaked-Kompatibilität bis 1.120.2 wurde übernommen.
Die ursprüngliche Arbeitskopie und ihre ungesicherten Dateien wurden nicht ersetzt.


## Release-Integration 0.6.0, 08.11.2026

Der lokale Mod-Stand wurde in einer getrennten Arbeitskopie mit GitHub-Master
`b5a03f425b50e7cece7d128539b77ace43d30750` zusammengeführt. Die fünf Konflikte
im Fachkern wurden unter Erhalt der nativen Erweiterungen und der bereits
integrierten Graphstudio-Funktionen aufgelöst. Version und Modmetadaten lauten
übereinstimmend **0.6.0**. Anschließend wurde der parallel veröffentlichte
Master `9d6359b65f4d950c6a48a21168817ad10f8331e5` (PR #6) integriert,
einschließlich seiner unveränderten Wiki-Dateien und Browserbibliotheken.
Der native Quellstand blieb bei diesem zweiten Abgleich unverändert.

Der Java-21-Build und die JUnit-Prüfung bestehen: **508 erfolgreiche Tests,
keine Fehler; zwei optionale Snapshot-Tests ohne externe Weltdateien übersprungen**.
Alle **62 verpflichtenden NeoForge-Spieltests** bestehen, einschließlich
eines vollständigen Turtle-Baus mit 1.115 Blöcken. Die Browserprüfung besteht
nach zusätzlicher Integration von PR #6 mit **38 Graph-, Bibliotheks-, Material-
und Referenzprüfungen**; der Vite-Produktionsbuild ist erfolgreich. Neue Regressionen
prüfen sechs Treppenmontagen mit unverändertem Volumen, kontextabhängige
Plattenvorschauen sowie die ausdrückliche Ablehnung nicht verlustfrei
unterstützter nativer Graphen vor Browserimport und -export.

JSON-Ressourcen, Sprachschlüssel, ROM-Lua und `git diff --check` bestehen.
Die Release-JAR enthält die Versionsmetadaten 0.6.0 und die erforderliche
Molten-Metals-Abhängigkeit, aber keine zusätzlich gebündelten Kotlin-/Gson-Klassen.
Die bereits vorhandenen Wiki-Dateien, Wiki-Bilder und `.github`-Konfiguration
sind gegenüber dem GitHub-Master unverändert. Prüfaufnahmen der Mod-Dokumentation
liegen separat unter `docs/release-0.6.0/images`.

## Mehrfachauswahl nach Klick-Halten-Loslassen, 07.11.2026

Der neue native Regressionstest reproduziert vor der Korrektur den Verlust der Mehrfachauswahl bereits zwischen Mausklick und Loslassen (`build/graph-selection-before.log`, Modus 0, Schritt 15). Der Inspektorabgleich übernimmt jetzt nur geänderte Inspektorentscheidungen; sein letzter Stand bleibt beim Neuaufbau erhalten. Additive Shift-Klicks behalten die beim Drücken geltende Auswahlabsicht auch dann, wenn Shift vor der Maustaste losgelassen wird.

Der vollständige Offline-Build besteht mit **455 JUnit-Tests ohne Fehler oder übersprungene Tests** (`build/graph-selection-build.log`). Der getrennte Lauf `jar runClient -PgraphSelectionSmoke -I build/graph-selection-validation.init.gradle --offline --console=plain --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1G -Pkotlin.compiler.execution.strategy=in-process` besteht mit allen **sechs Clientvarianten**: Form-, Gebäude- und Druckereditor bei 854 × 480 und 427 × 240 GUI-Pixeln. Echte Screen-Ereignisse prüfen gehaltene Shift-Klicks über mehrere Renderframes, frühes Loslassen von Shift, Auswahlrahmen, Einzel- und Leerklicks, Neuaufbau bei Mehrfachauswahl sowie explizite Inspektorauswahl nach Neuaufbau. Die Dokumente bleiben unverändert. Protokoll: `build/graph-selection-final-client.log`; aktualisierte JAR und SHA-256 unter `build/libs/`. Der anfängliche gleichzeitige Build-/Clientlauf wurde mit Exitcode 137 beendet; die getrennten Läufe mit begrenztem Speicher sind erfolgreich.

## Namensgruppen und separater Versionsdialog, 07.11.2026

`build runClient -PsavedCopycatSmoke -I build/copycat-versions-validation.init.gradle --offline --console=plain --continue` besteht mit **455 JUnit-Tests ohne Fehler oder übersprungene Tests**. Die sieben Fachtests für `FormLibraryTree` prüfen einen Eintrag je vollständigem Namenspfad, Gruppenzählung in Ordnern, numerisch absteigende Versionen, gleiche Namen über verschiedene IDs, gleiche Versionsnummern mit unterscheidbaren Schlüsseln, getrennte Ordner/Umbenennungen, stabile Momentaufnahmen sowie Suche nach älteren exakten Schlüsseln. Ein ergänzender gezielter Lauf prüft insbesondere, dass `@1` gegenüber `@12` bei exakter Suche bevorzugt wird, zusammen mit den zwölf bisherigen Tests für gespeicherte Formquellen.

Die native Bedienprüfung besteht mit **16 Versionen desselben Namens** im Gebäudeeditor, am Architektentisch und im Drucker bei **854 × 480** und **427 × 240 GUI-Pixeln**. Sie prüft genau eine Namenszeile, die neueste Vorauswahl für neue Quellen, die unveränderte Vorauswahl bereits verwendeter älterer Versionen, einen eigenständigen Versionsdialog, versionsabhängige Gold-/Kupfergeometrie, exakte Übernahme von Version 7, Escape zurück zur unveränderten Namensliste, Suche, Scrollen und Größenwechsel. Das erneute Bestätigen derselben Version verändert den Entwurf nicht; die bisherigen Quellenwechsel-, Rotations- und Undo-/Redo-Prüfungen bleiben erfolgreich. Die vollständige Quellbibliothek bleibt unverändert.

Visuell geprüft: [Namensgruppe](release-0.6.0/images/saved-copycat-names-normal.png), [Versionsdialog](release-0.6.0/images/saved-copycat-versions-normal.png) und [kompakte Versionswahl](release-0.6.0/images/saved-copycat-versions-compact.png). Protokolle: `build/copycat-versions-validation.log` und `build/copycat-versions-final-tests.log`. Klassen und Spielverzeichnis sind unter `build/copycat-versions-build/` und `build/copycat-versions-client/` isoliert; JAR und SHA-256 liegen unter `build/copycat-versions-build/libs/`. Ressourcenprüfung und `git diff --check` bestehen.

## Gespeicherte Copycats als Quellen neuer Formen, 06.11.2026

`build runClient -PsavedCopycatSmoke -I build/saved-copycat-validation.init.gradle --offline --console=plain --continue` besteht mit **450 JUnit-Tests ohne Fehler oder übersprungene Tests**. Zwölf neue Fachtests prüfen gespeicherte Quellen mit mehreren Materialgruppen, unveränderte Vorlagen, abgeschnittene und rationale Geometrien, weitere Boolean-Operationen, Rotation/Spiegelung, wiederholte Wiederverwendung ohne verschachtelte Graphen, JSON-Persistenz ohne Quellbibliothek, Herkunft und Zinkbedarf, unabhängige Quittungsnamensräume, Materialzusammenlegung, Quellenwechsel ohne Gruppenleck, gemeinsame Knotengruppeninstanzen sowie Gruppen- und Quellenlimits. Auch eine Form mit allen acht Materialgruppen passt in einen leeren Entwurf.

Die erweiterte native Bibliotheksabnahme besteht am Architektentisch bei **854 × 480** und im Drucker bei **427 × 240 GUI-Pixeln**. Sie öffnet die Quelle über das echte KilaGraph-Kontextmenü, prüft Ordner/Suche, Abbruch ohne Dokument- oder Historienänderung, exakte Versionswahl, Einfügekoordinaten, übernommene Kupferverkleidung, Verbindung mit dem Ergebnis, Quellenwechsel bei erhaltenen Anschlüssen und Z-Drehung. Einfügen, Quellenwechsel und Materialien bestehen atomare Undo-/Redo-Prüfungen; die Bibliothek bleibt bytegleich.

`runClient -PgraphNodePreviewSmoke` besteht zusätzlich einschließlich des neuen Quellentyps: lokale Vorschauen, unabhängiges Ein-/Ausklappen, unveränderte Dokumente und normale/kompakte Darstellung bleiben geprüft. Protokoll: `build/saved-copycat-preview-final.log`. Im eigenen Test-Spielverzeichnis wurde NeoForges frühes Ladefenster für diesen Lauf deaktiviert.

Visuell geprüft: [Quelle am Tisch](release-0.6.0/images/saved-copycat-source-normal.png) und [kompakte Druckeransicht](release-0.6.0/images/saved-copycat-source-compact.png). Der Lauf verwendet getrennte Klassen unter `build/saved-copycat-build/` und das eigene Spielverzeichnis `build/saved-copycat-client/`. Protokoll: `build/saved-copycat-final-validation.log`. JAR und SHA-256 liegen unter `build/saved-copycat-build/libs/`. Ressourcenprüfung und `git diff --check` bestehen.

## Eigene Materialauswahl je Copycat-Gruppe am Knoten, 06.11.2026

Der Copycat-Knoten zeigt jede Formgruppe mit einer eigenen Materialzeile. Jede Zeile öffnet den Materialdialog direkt für die angeklickte Gruppe. Gruppen mit gleicher geerbter Rolle bleiben unabhängig; die Auswahl verändert nur die lokalen Gruppenmaterialien dieses Knotens. Drehungsfelder und Zurücksetzen folgen der dynamischen Gruppenliste.

**40 gezielte JUnit-Tests ohne Fehler** (`BuildingSourceEditorsTest`, `BuildingGraphTest`) bestehen. `jar runClient -PmaterialSelectionSmoke -I build/copycat-node-material-validation.init.gradle --offline --console=plain` besteht ebenfalls: Die erweiterte native Clientprüfung klickt die zweite und dritte Gruppenzeile in kompakten Inspektoren und im Definitionsbedienfeld direkt an. Sie prüft getrennte Gruppen trotz gleicher Rollen, Anwendungsvorschau, angeschlossene Wandplatzierungen, Abbruch, atomare Übernahme, Undo/Redo und JSON-Persistenz. Beide Materialdialog-Prüfungen melden `PASSED`.

Visuell geprüft: [Gruppenzeilen normal](release-0.6.0/images/copycat-node-groups-normal.png) und [kompakter, scrollbarer Inspektor](release-0.6.0/images/copycat-node-groups-compact.png). Protokolle: `build/copycat-group-material-tests.log` und `build/copycat-group-material-final.log`. Ressourcenprüfung und `git diff --check` bestehen. JAR und SHA-256 unter `build/libs/` sind aktualisiert.

## Segmentvereinigung bei Überlagerungen, 06.11.2026

`./gradlew build runClient -PsegmentArrangementSmoke -I build/segment-union-validation.init.gradle --offline --console=plain` besteht mit **426 JUnit-Tests ohne Fehler oder übersprungene Tests**. Überlagerte Copycats werden auch bei Luftzellen anderer Segmente vereinigt. Die Regressionstests prüfen beide Eingangsreihenfolgen, gedrehte Luftvolumen, erhaltene Freiraumzellen, getrennte Materialgruppen mit kollidierenden IDs, individuelle Gold-/Kupfer-/Glaszuweisungen, geometrische Vereinigung ohne mehrfach gezähltes Volumen und JSON-Rundlauf. Die bestehende Prüfung zugeschnittener Copycats bestätigt weiterhin getrennte Verkleidungen und Quittungen.

Die native Anordnungsprüfung verschiebt Copycats auf dieselbe Blockposition und bestätigt den aktiven Übernehmen-Knopf sowie die erhaltenen Materialgruppen im ausgewerteten Ergebnis. Unvereinbare native Blöcke bleiben als korrigierbarer Konflikt geprüft. Normale und kompakte Bedienung, X/Y/Z-Ansichten, atomare Übernahme, Undo/Redo und erneutes Öffnen bestehen weiterhin. Die Aufnahmen im separaten Spielverzeichnis `build/segment-union-client/screenshots/` wurden visuell kontrolliert. Protokolle: `build/segment-union-tests.log` und `build/segment-union-validation.log`. Ressourcenprüfung und `git diff --check` bestehen; JAR und SHA-256 unter `build/libs/` sind aktualisiert.

## Korrektur der sichtbaren Connected-Texture-Vorschauen, 06.11.2026

Die erste CT-Abnahme prüfte Modellkoordinaten und eine kleine 3×3-Testfläche. Sie erfasste weder die zusätzlich sichtbaren Rückseiten des verwendeten zweiseitigen Entity-Renderlayers noch das Ausdünnen großer Knotenvorschauen. `Meshes.draw` verwendet jetzt `entityTranslucentCull` wie die native Blockmodellreferenz; die doppelten Glasrahmen entfallen. `BuildingNodePreviews` behält den vollständigen Plan, damit Geometrie, Flächenausblendung und CT-Nachbarschaft auch oberhalb von 512 Platzierungen erhalten bleiben.

`./gradlew --offline jar runClient -PconnectedTextureSmoke -I build/connected-texture-validation.init.gradle --console=plain` besteht. Die erweiterte Clientprüfung erzeugt eine 24×24-Wand über den tatsächlichen `BuildingNodePreviews`-Auswerter und zeichnet sie mit `BuildingNodePreviewRenderer`. Alle **576 Platzierungen** bleiben erhalten; die mittlere Zelle hat dieselben CT-Koordinaten wie ihre vollständige native Nachbarschaft. Eine zusätzliche Bildprüfung zeichnet die Copycat-Vorschau und unabhängig davon die unveränderten nativen BakedQuads nebeneinander: bei **854×480 GUI-Pixeln 0 von 173.442 abweichenden Pixeln**, bei **427×240 GUI-Pixeln 1 von 143.416**; der Vergleich toleriert Farbkanalabweichungen bis 8. Beide GUI-Größen sind visuell geprüft.

Prüfbilder: [Copycat und natives Modell](release-0.6.0/images/copycat-connected-native-normal.png), [kompakter Vergleich](release-0.6.0/images/copycat-connected-native-compact.png), [große Knotenvorschau](release-0.6.0/images/copycat-connected-large-normal.png), [kompakte Knotenvorschau](release-0.6.0/images/copycat-connected-large-compact.png). Protokoll: `build/connected-texture-preview-fix.log`. Die Mod-JAR und SHA-256 unter `build/libs/` sind aktualisiert. Ressourcenprüfung und `git diff --check` bestehen.

## Vertikale, klappbare Inspektorabschnitte, 06.11.2026

`./gradlew -I build/inspector-workflow-validation.init.gradle runClient -PinspectorSectionsSmoke --offline --console=plain` besteht bei **854 × 480 und 427 × 240 GUI-Pixeln**. Gebäude- und Copycat-Inspektoren verwenden unabhängig klappbare Abschnitte. Die Prüfung bedient echte Überschriften, hält 2D und 3D gleichzeitig offen, wechselt über native Knotenüberschriften zu anderen Definitionen und zu Knoten mit vorübergehend fehlenden Abschnitten und prüft denselben Zustand nach einer GUI-Skalierung. Mausrad-Ereignisse über Vorschauen scrollen den Inspektor; die 3D-Drehung bleibt je Knoten erhalten. JSON sowie Undo-/Redo-Verlauf bleiben unverändert. Die Aufnahmen beider Inspektoren wurden visuell geprüft.

`InspectorSectionsTest` prüft unabhängige Klappzustände, verschobene Inhalts- und Trefferbereiche, zeitweise fehlende Abschnitte und getrennte Ansichtsinstanzen. Protokolle: `build/inspector-sections-tests.log` und `build/inspector-sections-client.log`.

Der vollständige Build besteht mit **424 JUnit-Tests ohne Fehler oder übersprungene Tests**. Die bestehenden `inspectorWorkflowSmoke`- und `buildingPreviewSmoke`-Läufe bestehen ebenfalls: Gruppenzuordnung, individuelle Materialauswahl, Quellbibliothek, atomare Änderungen, lokale Segment-/Raum-/Stockwerks-/Dachvorschauen, kompakte Bedienfelder, Eingabemenüs sowie Undo/Redo bleiben geprüft. Die Vorschauhöhe passt sich auch den kürzeren Definitionsbereichen an. Protokolle: `build/inspector-sections-final-build.log`, `build/inspector-sections-workflow-client.log` und `build/inspector-sections-preview-client.log`. Ressourcenprüfung und `git diff --check` sind erfolgreich.

[Alle Gebäudeabschnitte eingeklappt](release-0.6.0/images/inspector-sections-building-collapsed-compact.png), [Copycat-Abschnitte eingeklappt](release-0.6.0/images/inspector-sections-copycat-collapsed-compact.png).

Prüfbilder: [Gebäudeinspektor](release-0.6.0/images/inspector-sections-building-normal.png), [kompakt](release-0.6.0/images/inspector-sections-building-compact.png), [Copycat-Inspektor](release-0.6.0/images/inspector-sections-copycat-normal.png), [Copycat-Eigenschaften kompakt](release-0.6.0/images/inspector-sections-copycat-properties-compact.png).

## Interaktive Segmentanordnung, 06.11.2026

`./gradlew test jar runClient -PsegmentArrangementSmoke -I build/explicit-assembly-validation.init.gradle --offline --console=plain` besteht mit **421 JUnit-Tests ohne Fehler oder übersprungene Tests**. Acht neue Fachtests prüfen die drei Blickrichtungen, vorzeichenrichtige Rasterverschiebungen, unveränderte ausgeblendete Koordinaten, gedrehte asymmetrische Geometrie, negative Positionen, verschieden hohe und tiefe Eingänge, Luftvolumen, Konfliktkorrektur, Grenzen und JSON-Rundläufe.

Die native Bedienprüfung verschiebt drei unterschiedlich große Segmente durch echte Mausereignisse in X-, Y- und Z-Ansicht. Sie prüft Konfliktsperre und Korrektur, stabile Rasterprojektion, Zoom und Schwenken, fremde Loslasstasten, Esc-Abbruch, unverändertes Gebäudedokument bis zur Übernahme, einen gemeinsamen Undo-/Redo-Schritt und erneutes Öffnen. Die Oberfläche wurde bei **854 × 480** und **427 × 240 GUI-Pixeln** geprüft; beide Aufnahmen wurden visuell kontrolliert: [normal](release-0.6.0/images/segment-arrangement-normal.png), [kompakt](release-0.6.0/images/segment-arrangement-compact.png). Ressourcenprüfung und `git diff --check` sind erfolgreich. Protokoll: `build/segment-arrangement-validation.log`. Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

## Gruppenknoten, individuelle Copycat-Materialien und Segmentausrichtung, 06.11.2026

`./gradlew -I build/inspector-workflow-validation.init.gradle runClient -PinspectorWorkflowSmoke --offline --console=plain` besteht im isolierten Clientverzeichnis bei **854 × 480 und 427 × 240 GUI-Pixeln**. Tatsächliche Gruppenkopf-Klicks im Gebäude- und Formgraphen erhalten die Gruppenidentität im Inspektor; die Mitgliederauswahl öffnet die Gruppe ohne Dokumentänderung. Das native Untermenü **Geometrie** fügt ein Flächengebiet an und Undo stellt das vollständige JSON wieder her. Ein verschobenes asymmetrisches L-Flächengebiet zeigt seine breite Kante oben im Segment. Alle zugehörigen Aufnahmen wurden visuell geprüft.

Die angeschlossene Materialprüfung wählt am tatsächlichen Copycat-Knoten Kupfer, Glas und Gold für drei Gruppen, darunter zwei mit derselben bisherigen Materialrolle. Andere Copycat-Knoten, Gebäudepalette und Quellbibliothek bleiben erhalten. Nachgelagerte Wandplatzierungen verwenden die individuellen Materialien. Normale/kompakte Vorschaupixel, Abbruch, atomare Übernahme, unveränderte Übernahme, Undo/Redo und JSON-Rundlauf sind geprüft. Protokoll: `build/inspector-workflow-client.log`.

Der abschließende vollständige Build mit eigenen Testberichten besteht: **421 JUnit-Tests, keine Fehler oder übersprungenen Tests**. Die bestehende `graphGroupSmoke`-Prüfung besteht ebenfalls mit Gruppeninstanzen, gemeinsam bearbeiteten Einstellungen, unabhängigen äußeren Anschlüssen, Ein-/Ausklappen, atomarem Verschieben und Undo/Redo. Ressourcenprüfung und `git diff --check` sind erfolgreich. Weitere Protokolle: `build/inspector-fixes-final-build.log` und `build/inspector-groups-client.log`; Testberichte: `build/inspector-fixes-test-results`.

Prüfbilder: [Gruppeninspektor](release-0.6.0/images/inspector-group-building-normal.png), [kompakt](release-0.6.0/images/inspector-group-building-compact.png), [Formgruppe](release-0.6.0/images/inspector-group-copycat-compact.png), [Geometrie-Menü](release-0.6.0/images/inspector-add-geometry-compact.png), [Segmentausrichtung](release-0.6.0/images/inspector-segment-area-compact.png), [individuelle Materialien](release-0.6.0/images/inspector-copycat-material-compact.png).

## Native Connected Textures auf Copycats und Vorschauen, 06.11.2026

Der Offline-Build besteht mit **408 bestehenden JUnit-Tests ohne Fehler oder übersprungene Tests** (`build/connected-texture-full-build.log`). Anschließend bestehen die drei neuen isolierten `SurfaceConnectionsTest`-Regressionstests für gemeinsame Kanten, diagonale Ecken und exakte schräge Dreiecke. Der abschließende Lauf `./gradlew --offline test --tests '*SurfaceConnectionsTest' jar runClient -PconnectedTextureSmoke -I build/connected-texture-validation.init.gradle --console=plain` ist erfolgreich (`build/connected-texture-final.log`).

Die echte Clientabnahme vergleicht mit geladenem Texturatlas die endgültigen nativen Create-CT-Koordinaten auf **allen sechs Blockseiten** mit Copycat-Vorschauen und tatsächlichen `CompositeEntity`-Nachbarinformationen. Sie prüft Umfärben, Halbblöcke, unterschiedliche Flächenhöhen, Lücken, sichtbare Materialgruppen, native Materialnachbarn, diagonale Anschlüsse und davorliegende Hindernisse. Die native Gegenabfrage `getAppearance` erkennt Copycat-Beschichtungen in jeder Richtung und lehnt eine halbhohe Oberseite als Anschluss an eine volle native Oberseite ab.

Die gerenderte 3×3-Glasfläche besitzt einen durchgehenden Außenrahmen ohne innere Blockränder. Eine anders beschichtete mittlere Zelle erzeugt wieder den passenden inneren Rahmen. Beide Ansichten sind bei **854 × 480 und 427 × 240 GUI-Pixeln** aufgenommen und visuell geprüft: [normal](release-0.6.0/images/copycat-connected-textures-normal.png), [kompakt](release-0.6.0/images/copycat-connected-textures-compact.png). Die finale JAR samt aktualisierter SHA-256 liegt unter `build/libs/copycat_architect-0.5.1.jar`. Ressourcenprüfung und `git diff --check` bestehen.

## Optionale Materialvorschau im Tisch-Copycat-Graph, 06.11.2026

`./gradlew -I build/explicit-assembly-validation.init.gradle jar runClient -PbuildingWorkspaceSmoke --offline --console=plain` ist erfolgreich. Der Tisch-Copycat-Graph startet mit untexturierten, neutral schattierten Formen; **V – Materialvorschau** schaltet die gewählten Materialien in Graph und Inspektor ein. Die Einstellung bleibt lokale Ansichtssache, auch beim Wechsel zwischen Gebäude und Copycat sowie beim Ändern der GUI-Skalierung.

Der echte Client prüft bei **854 × 480 und 427 × 240 GUI-Pixeln** im Ergebnis-Viewport neutrale Geometriepixel ohne Goldtextur und nach dem Umschalten tatsächlich zugewiesene Goldmaterialpixel. Dokument, Materialzuordnungen und beide History-Stapel bleiben beim Umschalten erhalten. Alle vier Aufnahmen wurden visuell geprüft. Die bestehende Navigationsprüfung berücksichtigt jetzt auch leere Gebäudebereiche korrekt. Ressourcenprüfung und `git diff --check` sind erfolgreich. Protokoll: `build/table-material-preview-client.log`.

## Rechtecke in Flächengebieten zeichnen, 06.11.2026

`./gradlew build --offline --console=plain` ist erfolgreich: **408 JUnit-Tests ohne Fehler oder übersprungene Tests**. Die Raster von Flächengebiet und Raumgeometrie ergänzen per Linksziehen ein gefülltes Rechteck und entfernen per Rechtsziehen Zellen. Einzelklicks bleiben möglich; Loslassen übernimmt genau einen Undo-Schritt und Esc verwirft die Vorschau.

`./gradlew -I build/explicit-assembly-validation.init.gradle runClient -PexplicitAssemblySmoke --offline --console=plain` besteht bei **854 × 480 und 427 × 240 GUI-Pixeln**. Geprüft sind leere Flächenentwürfe, belegte Startzellen, Ziehen in Gegenrichtung, rechteckiges Entfernen, unverändertes Dokument und Historie während der Vorschau, stabile Rasterprojektion beim Rendern und Scrollen, fremde Loslasstasten, Esc samt anschließendem Loslassen, gemeinsam verwendete Raumgeometrien und vollständiges JSON-Undo/Redo. Die neuen Vorschauaufnahmen wurden visuell geprüft. Ressourcenprüfung und `git diff --check` sind erfolgreich. Protokolle: `build/area-rectangle-build.log` und `build/area-rectangle-client.log`.

## Copycat spiegeln und Segmentumfang, 06.11.2026

`./gradlew build runClient -PcopycatMirrorSmoke --offline --console=plain` ist erfolgreich: **408 JUnit-Tests ohne Fehler oder übersprungene Tests**. Neun neue Geometrietests, sieben Formgraph-Tests und zehn Gebäudegraph-Tests prüfen die 13 Spiegelebenen, unabhängig berechnete Householder-Punktabbildungen, rationale Drittelkoordinaten, doppelte Spiegelungen, sichtbare Ausschnitte, Blockgrenzen, Materialbesitz und Herkunft. Die Gebäudeprüfung enthält die Reihenfolge aus Quelldrehung, Spiegelung, Anordnung und Montage, tatsächliche gespiegelte Dachprofile und Wandvorlagen, passende Treppenstufen, sämtliche Copycat-Anschlüsse, Freiraum, native Blockablehnung, alte JSON-Dokumente und gemeinsam bearbeitete Gruppeninstanzen. Beide Segmentmodi sind einschließlich unveränderter Zellmetadaten bei „Nur Blockpositionen“ geprüft.

Die neue native Bedienprüfung wählt **alle 13 Ebenen im Drucker und am Architektentisch**, jeweils bei 854 × 480 und 427 × 240 GUI-Pixeln. Sie prüft reale Dropdowns, rationale Vorschaugeometrie, JSON und Rückgängig/Wiederholen, beide Segmentumfänge nach einer Quelldrehung sowie das Einfügen beider Copycat-Spiegelknoten über die tatsächlichen Graph-Kontextmenüs. Raumdiagonale Ebenen verwenden die gewählte Diagonale als Normale durch die Blockmitte; sichtbare Ergebnisse außerhalb des Blocks erzeugen einen Fehler und werden nicht abgeschnitten. Die Rohmaterialberechnung bleibt beim vorhandenen konservativen Raster exakter Formen.

`./gradlew runClient -PgraphNodePreviewSmoke --offline --console=plain` besteht ebenfalls einschließlich der neuen Knoten: nur Ergebnisse haben standardmäßig offene 3D-Vorschauen, jede Knotenart besitzt eine lokale Vorschau, und Falten, Größenwechsel und schreibgeschützte Ansichten verändern kein Dokument. Materialbildpunkte in normalen und kompakten Vorschaufenstern sind geprüft. `python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich.

Prüfbilder: [Blockdrucker](release-0.6.0/images/copycat-mirror-printer-normal.png), [Architektentisch](release-0.6.0/images/copycat-mirror-table-normal.png), [kompakt](release-0.6.0/images/copycat-mirror-table-compact.png). Protokolle: `build/copycat-mirror-final.log` und `build/copycat-mirror-graph-previews.log`. Die vorübergehende Client-Konfiguration wurde wiederhergestellt; Mod-JAR und SHA-256 unter `build/libs` sind aktualisiert.

## Segment spiegeln, 06.11.2026

`./gradlew build runClient -PbuildingPreviewSmoke --offline --console=plain` ist erfolgreich: **382 JUnit-Tests ohne Fehler oder übersprungene Tests**. Der neue Verarbeitungsknoten besitzt einen festen Segmenteingang und dieselben typisierten Ausgänge wie Segmente und Segmentzusammensetzungen. Die Fachtests spiegeln alle drei Achsen, vollständige und dünne Copycat-Geometrien, versetzte Ausschnitte, rationale Dachformen, Materialgruppen und Herkunft. Verschachtelte Drehungen/Spiegelungen, doppelte Spiegelung, Freiraumzellen, Wand-/Boden-/Deckeneinbau, Dachvorlagen, JSON und wiederverwendbare Knotengruppen sind geprüft.

Die native Bedienprüfung fügt „Segment spiegeln“ über das echte Graph-Kontextmenü hinzu, zeigt die asymmetrische Segmentvorschau in 2D und 3D, bedient die X/Y/Z-Achsenauswahl im kompakten Inspektor und prüft Rückgängig/Wiederholen. Spiegelungen erhalten Zink- und Materialbedarf. Der zuvor gewählte Inspektorreiter bleibt beim Knotenwechsel erhalten. `python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich.

Prüfbilder: [3D normal](release-0.6.0/images/segment-mirror-3d-normal.png), [2D normal](release-0.6.0/images/segment-mirror-2d-normal.png), [3D kompakt](release-0.6.0/images/segment-mirror-3d-compact.png).

## Inspektorwahl beim Knotenwechsel, 06.11.2026

`./gradlew assemble runClient -PbuildingPreviewSmoke --offline --console=plain` ist erfolgreich. Die native Bedienprüfung wählt Parameter, 2D und 3D und wechselt anschließend zwischen bereits besuchten Segment-, Raum-, Stockwerk- und Dachknoten. Der zuletzt gewählte Reiter bleibt auch bei normaler/kompakter GUI-Skalierung erhalten; die 3D-Drehwinkel bleiben unabhängig je Knoten. Breite Inspektoren zeigen die Parameter neben der Vorschau und bewahren die Parameterwahl für die kompakte Ansicht. Gebäude-JSON und Undo-/Redo-Historie bleiben unverändert. Ressourcenprüfung und `git diff --check` sind erfolgreich.

## Kompakte Graphen und wiederverwendbare Knotengruppen, 06.11.2026

- `./gradlew build runClient -PgraphGroupSmoke --offline --console=plain`: erfolgreich, **367 JUnit-Tests ohne Fehler oder übersprungene Tests**. Die native Clientprüfung erstellt eine Gruppe über Strg+G und den Namensdialog, öffnet/schließt Gruppen, erzeugt Instanzen über das Kontextmenü, verschiebt vollständige Instanzen mit einem Undo-/Redo-Schritt und prüft normale sowie kompakte GUI-Skalierung. Gemeinsame Einstellungen und interne Anschlüsse bleiben gekoppelt; äußere Eingänge und Positionen sind unabhängig. Neue Instanzen erhalten einen freien Platz neben dem vorhandenen Graphen.
- `graphNodePreviewSmoke` mit den gezielten Gruppen-, Projektions- und Vorschauzustandstests: erfolgreich. Nur Ergebnisse starten mit geöffneter Vorschau. Alle Form- und Gebäudeknoten einschließlich der neuen Dach-/Erschließungsknoten behalten eigene lokale Vorschauen; manuelle Entscheidungen überstehen Neuaufbau, GUI-Skalierung und schreibgeschützte Ansichten ohne Dokumentänderung. Die Pixelprüfung bestätigt Materialgeometrie in normalen und kompakten Vorschaufenstern.
- Fachtests prüfen JSON-Rundlauf und ältere Graphen ohne Gruppenfelder, geteilte Definitionen, typisierte Randanschlüsse, unveränderte verdeckte interne Kabel, begrenzte Gruppenbewegung, einzelne Instanzlöschung und gemeinsame Mitgliedslöschung. Änderungen geteilter Raumgeometrien erhalten unabhängige Wandzuweisungen, aktualisieren alle abhängigen Räume und lehnen Überschneidungen ab.
- `python3 tools/verify_resources.py` und `git diff --check`: erfolgreich. Reproduzierbare Beispielgraphen ergänzen ausschließlich das neue leere Feld `nodeGroups`.

Prüfbilder: [geöffnete Gruppe](release-0.6.0/images/node-groups-open-normal.png), [mehrere Forminstanzen](release-0.6.0/images/node-group-instances-normal.png), [kompakter Gruppenknoten](release-0.6.0/images/node-groups-compact.png), [Gebäudeinstanzen mit getrennten Quellen](release-0.6.0/images/building-group-instances-normal.png). Neue Gruppenmitglieder erfordern derzeit Auflösen und Neugruppieren; Verschachtelung und eine separate graphübergreifende Gruppenbibliothek sind nicht enthalten.

## Modulare Dächer und vertikale Gebäudeanschlüsse, 05.11.2026

Die Dachauswertung erzeugt eigene rationale Polyeder für Flach-, Pult-, Sattel-, Walm-, Mansarden- und verbundene Kreuzdächer. Dachebenen beziehen sich auf stabile Geschossinstanzen und deren tatsächliche Deckenoberkante. Höhenmodus, Wandaufstockung, freiliegende untere Teildächer, Überstände in 1/16-Schritten und unveränderliche Vorlagenperioden verwenden denselben Compiler wie Vorschau und Bauplan. Klassische Dachdefinitionen behalten ihren bisherigen Verarbeitungspfad.

Der zusätzliche exakte Geometriepfad erhält Flächen, Materialbesitz und Zuschnitte bei booleschen Operationen und Drehungen. Kollision und Zinkbedarf entstehen konservativ aus der 1/16-Rasterbelegung. Die Fachtests vergleichen die exakte Polygonberechnung zusätzlich mit einer unabhängigen Schnittberechnung aus Ebenentripeln. Sie prüfen sämtliche elf Steigungen über vollständige Wiederholungsperioden und alle horizontalen Drehungen sowie transparente Materialgrenzen innerhalb eines Blocks und zwischen benachbarten Blöcken.

Treppenhäuser, vorbereitete Aufzugsschächte und Versorgungsschächte besitzen unabhängige Anschlussinstanzen. Kernkörper, Durchbrüche und Kopffreiheit werden vor der endgültigen Raum-/Plattenerzeugung reserviert. Die Abnahme umfasst gerade, L- und U-Treppen, Links-/Rechtslauf, sämtliche Vierteldrehungen, Keller bis Obergeschoss, wiederverwendete Geschossdefinitionen, Haltestellenauswahl, mehrere Kerne und ungeeignete Vorlagen. Die Aufzugsvorbereitung erzeugt den Schacht samt Zugängen; ein beweglicher Aufzug gehört nicht zu dieser Definition.

Die importierbare Datei [modular-roofs-and-access.json](../examples/modular-roofs-and-access.json) verbindet ein unteres Pultdach mit einem abschließenden Satteldach und allen drei Kernarten. Die Implementierung verwendet eigene Geometrie und Modelle; Code und Assets aus Domum Ornamentum wurden nicht übernommen. Die Projektlizenz bleibt MIT.

Builtin-Dachhaut, Unterseite, Rahmen, Traufe, Ortgang, First, Grat, Kehle und Knickanschlüsse behalten getrennte semantische Materialgruppen; die vorhandene Grenze von acht Gruppen pro Block gilt weiterhin. Eigene Wandsegmente werden periodisch ausgerichtet und bis zur Dachunterseite zugeschnitten. Unstetige Anschlüsse werden an sämtlichen rationalen Profilknicken geprüft; die Mindesthöhenmeldung nennt den nächsten einstellbaren 1/16-Schritt. Mehrere gleichzeitig gelöschte Kernanschlüsse erhalten die Identität und Platzierung der überlebenden Einsetzungen.

Die tatsächliche `saveForm`-Anfrage akzeptiert die prozedurale Dachquelle ohne nativen Blockzustand. Formversionen, Druckhistorien, Materialeinstellungen und eingefrorene Baupläne behalten ihre genaue Geometrie auch nach Entfernen der ursprünglichen Bibliotheksreferenzen. Große JSON-Geometriefelder verwenden oberhalb der NBT-Stringgrenze UTF-8-ByteArrays; bisherige Stringdaten bleiben lesbar. Die unveränderten Historien-/Bibliothekslimits werden beim Spieler- und Turtle-Druck vor Welt- oder Materialänderungen geprüft.

**331 JUnit-Tests ohne Fehler oder übersprungene Fälle** bestehen im vollständigen `test build runClient -PstructuralSmoke`-Lauf. Der echte Client bestätigt bei **854 × 480** und **427 × 240 GUI-Pixeln** Änderungen von Dachsteigung und Treppenform, gemeinsames Übernehmen und Undo/Redo, lokale Dachvorschau sowie unabhängige Kernplatzierung durch tatsächliche Mausereignisse. Kompakte Eigenschaftsseiten werden per Mausrad gescrollt. Visuell geprüft: [Dacheinstellungen](release-0.6.0/images/structural-roof-level-settings-normal.png), [Dachgeometrie](release-0.6.0/images/structural-roof-3d-normal.png), [kompakte Dacheinstellungen](release-0.6.0/images/structural-roof-level-settings-compact.png), [Treppenparameter](release-0.6.0/images/structural-stairs-settings-normal.png), [kompakte Kernplatzierung](release-0.6.0/images/structural-core-placement-compact.png) und [gescrollte Aufzugsvorbereitung](release-0.6.0/images/structural-elevator-settings-compact.png).

**Alle 61 erforderlichen Minecraft-GameTests bestehen** im abschließenden `jar runGameTestServer`-Lauf. Der tatsächliche Spieler bewegt sich ohne Sprünge über gerade, L- und U-Treppen mit beiden Laufrichtungen und allen Vierteldrehungen. Die Treppenarena liegt räumlich getrennt oberhalb des übrigen Testfelds, damit die nur 4×4 große Standard-Testvorlage keine Kollision mit benachbarten Bauten verursacht. Rationale Dachflächen werden mit bezahlten Materialien platziert, an allen 4.096 Kollisionsrasterpunkten geprüft und mitsamt Oberflächen und Belegen wieder geladen. Weitere Welttests prüfen echte Form-Speicheranfragen, Versionsstände, Historien, eingefrorene Aufträge, komprimierte NBT-Rundläufe großer JSON-Geometrien sowie Material-/Welterhalt bei überschrittenen Historienlimits über Spieler und Turtle. Die bisherigen vollständigen Turtle-Bauten mit 603 und 1.115 Platzierungen bestehen ebenfalls.

Protokolle: `build/tmp/structural-client-verified-tests.log` für den vollständigen Fachtest-/Build-/Clientlauf, `build/tmp/structural-client.log` für den abschließenden Clientbildnachweis und `build/tmp/roof-final-server.log` für alle 61 Welttests. Die Prüfläufe verwenden Java 21, vorhandene Offline-Abhängigkeiten und getrennte Spielverzeichnisse. Finale JAR und aktualisierte SHA-256 liegen unter `build/libs/`. JAR-Integrität, `python3 tools/verify_resources.py` und `git diff --check` bestehen.

## Kreativbau mit Blockzuordnung und versetzbarem Tisch, 05.11.2026

Im Reiter **Bauen** bietet der geöffnete Architektentisch Kreativspielern **Im Kreativ bauen**. Der Server prüft Spielmodus, konkrete Tischbindung, Entfernung, Eigentümer, laufende Aufträge, geladene Chunks, Weltgrenzen, belegte Ziel-/Freiraumpositionen und Schutzrechte. Der Bau kostet keine Materialien. Die Bereinigung entfernt ausschließlich geänderte oder entfallene eigene Copycats; native Landschaftsblöcke werden auf freien Positionen gesetzt oder bei Übereinstimmung verwendet. Vorhandenes Gelände wird nicht automatisch ausgegraben.

Der Tisch besitzt jetzt eine Blockentität. In `ArchitectData` bleiben Gebäudeentwurf, Weltursprung, Drehung, Tischposition, Aufstellstatus und Blockliste je Gebäude gespeichert. Jeder tatsächlich neu gebaute Copycat erhält Gebäude-ID und individuellen Block-Token. Eine Position oder identische Geometrie begründet keine Zugehörigkeit. Fremde Copycats werden auch bei Übereinstimmung nicht adoptiert; spätere fremde Ersatzblöcke an alten Positionen werden nicht gelöscht. Neue Tisch-Turtle-Aufträge protokollieren ihre tatsächlich platzierten Copycats. Ältere Aufträge lassen sich nur mit genau passender gespeicherter Gebäudevorlage und echten `job:index`-Belegen übernehmen.

Das Tisch-Item bewahrt seine Gebäude-ID im normalen Loot. Abbauen markiert das Gebäude ausdrücklich als vom Tisch getrennt. Dadurch bleibt es auch nach Entladen des früheren Tisch-Chunks wieder zuordenbar. Ein frischer Tisch desselben Besitzers erkennt ein eindeutiges nahes, abgetrenntes Gebäude; ein vorhandener Tisch, anderer Besitzer oder mehrere mögliche Gebäude verhindert die automatische Übernahme. Beim Umstellen bleibt das Gebäude unverändert in der Welt stehen; der lokale Tischversatz wird unter Erhalt von Ursprung und Drehung neu berechnet. Die horizontale Tischreferenz erlaubt jetzt bis zu 512 Modellblöcke. Beim Öffnen lädt der Tisch seinen eigenen Entwurf; gewöhnliche Syncs überschreiben keinen ungespeicherten Entwurf.

Kreativ-Rebuilds prüfen Löschschutz vor der ersten Änderung und sichern betroffene Blockzustände samt Blockentitätsdaten. Bei späterer Platzierungsablehnung werden Geometrie, Oberflächen, bezahlte Materialbelege und Zugehörigkeit vollständig wiederhergestellt, auch wenn der Blocktyp unverändert ist. Rebuilds erzeugen keine Materialrückzahlungen oder Itemduplikate. Größere Antworten können Bibliothek und Tischentwurf gemeinsam komprimiert übertragen, mit getrennten begrenzten Antwortgrößen; die bestehenden Request-Grenzen bleiben erhalten.


**285 JUnit-Tests ohne Fehler** bestehen im finalen `build runClient -PlandscapeSmoke`-Lauf. Die neuen Fachtests prüfen alte Daten ohne fälschlich abgeleitete Zugehörigkeit, dauerhafte Block-Tokens und abgetrennte Tischzuordnung, Dokument-/Job-Rundläufe, den stabilen Weltursprung nach mehreren Tischbewegungen bei allen vier Drehungen und größere begrenzte Antworten mit Bibliothek und Tischentwurf. Die echte Clientabnahme bestätigt bei **854 × 480** und **427 × 240 GUI-Pixeln** die nur für Kreativspieler sichtbare Bauaktion, ihre benötigte Tischbindung, überlappungsfreie Bedienelemente, das Laden des Tischentwurfs und unveränderte Weltkoordinaten nach dem Umstellen. Die bestehende gemeinsame Gebäude-/Garten-/Weganordnung besteht ebenfalls.

**Alle 54 erforderlichen Minecraft-GameTests bestehen**, darunter neun neue Szenarien: fremde identische und später eingesetzte Copycats, Entfernen alter eigener Positionen, idempotenter Rebuild, unveränderte Welt bei fremden Hindernissen und gefälschten Survival-Anfragen, Rückrollen nach später Platzierungsablehnung samt bezahlten Belegen ohne Itemduplikate, Löschschutz vor der ersten Änderung, echte Tisch-Loot-/BlockItem-Umplatzierung und NBT-Rundläufe, Schutz vorhandener Tische und anderer Besitzer, beleggestützte Altauftragserkennung, native Gartenböden sowie Wiederzuordnung nach Entladen des früheren Tisch-Chunks und ungültige vertikale Referenzen. Die vollständigen Turtle-Bauten mit 603 und 1.115 Platzierungen sind eingeschlossen.

Protokolle: `build/creative-complete-validation.log` für Build, 285 Unit-Tests und Clientabnahme sowie `build/creative-server-complete.log` für die abschließenden 54 Servertests. Java 21, vorhandene Offline-Abhängigkeiten und getrennte Build-/Spielverzeichnisse wurden verwendet. Die finale JAR und SHA-256 liegen unter `build/creative-ui-check/libs/`. JAR-Integrität, `python3 tools/verify_resources.py` und `git diff --check` bestehen.

## Gemeinsame Anordnung im Gebäude-Knoten, 05.11.2026

**Anordnung und Wegpunkte bearbeiten** am Gebäude-Knoten öffnet die gemeinsame Anordnung von Gebäudekörper, angeschlossenen Gärten und Wegen. Platzierungsregler und Ziehfunktion sind aus den Garten-/Weg-Quellknoten entfernt. Der Gebäudekörper bewegt alle Stockwerke samt Dach; Gärten und Wege werden als einzelne Anschlussinstanzen platziert. Wegpunkte besitzen ziehbare Griffe, Einfügen/Löschen, Punktauswahl und Höhenfelder. Der Gebäude-Knoten speichert `buildingOffset`, die Anschlussversätze in `offsets` und individuelle Pfadverläufe in `sitePaths`. Bestehende Quellversätze bleiben kompatibel; sie werden genau einmal berücksichtigt. Gemeinsame Quellen bleiben unverändert, auch bei wiederholten Eingängen derselben Quelle. Neuverbinden und Löschen beseitigen veraltete Pfadzuordnungen.

`build runClient -PlandscapeSmoke` besteht mit **281 JUnit-Tests ohne Fehler**. Sechs neue Fachtests prüfen unabhängige Garteninstanzen, die Verschiebung aller Gebäudeschichten einschließlich Dach, unveränderte Außenbereiche beim Verschieben des Gebäudes, eigene Wegpunktverläufe je Anschluss, alte Quellpositionen und Höhen, JSON-Rundläufe, Mengen, veraltete Zuordnungen nach Anschlussänderungen sowie ungültige Entwürfe. Die T-Haus-Exportdateien sind mit neutralen Standardfeldern aktualisiert.

Die echte Clientprüfung öffnet die Anordnung über den Gebäude-Knoten bei **854 × 480** und **427 × 240 GUI-Pixeln**. Mausereignisse verschieben Garten und Gebäudekörper unabhängig und ziehen Weggriffe. Einfügen, Löschen, Y-Höhe und numerische X-/Z-Platzierung werden über die echten Bedienelemente geprüft. Vor dem Loslassen bleiben Dokument und Historie unverändert; ein gemeinsames Undo stellt den Ausgangszustand wieder her, Redo wiederholt die Änderung. Escape verwirft einen laufenden Zug. Kein Quellenknoten wird dabei verändert. Jeder fertige Zustand wird außerdem als vollständiger Bauplan ausgewertet. Die bisherigen Flächen-, Zaun-, Breiten- und Bodenoptionen bestehen weiterhin.

Visuell geprüft: [gemeinsame Anordnung](release-0.6.0/images/building-site-normal.png), [kompakte Anordnung](release-0.6.0/images/building-site-compact.png) und [Garten während des Verschiebens](release-0.6.0/images/building-site-garden-drag-normal.png). Protokolle: `build/building-site-validation.log` und `build/building-site-client-final.log`. Java 21, Offline-Abhängigkeiten und getrennte Build-/Spielverzeichnisse wurden verwendet. JAR und SHA-256 liegen unter `build/landscape-ui-check/libs/`. `python3 tools/verify_resources.py` und `git diff --check` bestehen.

## Garten relativ zum Gebäude verschieben, 05.11.2026

*Dieser Zwischenstand wurde durch die gemeinsame Anordnung im Gebäude-Knoten ersetzt; siehe den aktuellen Abschnitt oben.*

Garten und Weg besitzen jetzt einen eigenen gespeicherten X-/Z-Versatz (`landscapeOffset`), unabhängig von den Diagrammkoordinaten und lokalen Flächen-/Pfadpunkten. **Position X / Position Z** im Parameterbereich ändern diesen Versatz direkt. Im Raster verschiebt **Ziehen mit der mittleren Maustaste** den gesamten Außenbereich; der graue Erdgeschossgrundriss zeigt den Bezug zum Gebäude. Während des Ziehens bleibt die Projektion stabil, die Bewegung ist zunächst eine Vorschau. Loslassen schreibt einen Undo-Schritt, Escape verwirft ihn. Gemeinsame Flächenquellen und andere Gebäudeknoten bleiben erhalten; Flächenbearbeitung und Pfadpunkte rechnen Mausklicks in die unveränderten lokalen Koordinaten zurück.

`build` besteht mit **275 JUnit-Tests ohne Fehler**. Drei neue Regressionstests prüfen die identische Verschiebung von Oberfläche, Zaun, Untergrund und Freiraum; getrennte Instanzen einer gemeinsamen Fläche; alte JSON-Standardwerte und Rundläufe; unveränderte Diagramm- und Gebäudekoordinaten; die Auflösung einer anfänglichen Gebäudeüberschneidung; unveränderte Mengen sowie die anschließende Drehung/Weltprojektion ohne doppelte Verschiebung. Die reproduzierbaren T-Haus-Beispiele sind um den neutralen Positionsstandard ergänzt.

`runClient -PlandscapeSmoke` besteht erneut bei **854 × 480** und **427 × 240 GUI-Pixeln**. Der Prüflauf verbindet beide Außenbereiche mit einem vollständigen Gebäude, ändert X und Z über die echten Eingabedialoge und prüft den vollständigen Plan. Echte mittlere Mausklicks und Ziehereignisse bewegen den Garten um drei beziehungsweise minus zwei Blöcke. Vor dem Loslassen bleiben Dokument und Historie unverändert; anschließend stellt ein einzelnes Undo den vollständigen Ausgangszustand wieder her. Redo, Graph-/Bedienfeldwechsel und Escape-Abbruch bestehen ebenfalls. Die bisherigen Außenbereichseinstellungen bleiben geprüft. Visuell geprüft: [normale Platzierung](release-0.6.0/images/garden-position-normal.png) und [kompakte Platzierung](release-0.6.0/images/garden-position-compact.png).

Protokolle: `build/landscape-placement-build.log` und `build/landscape-placement-client.log`. Java 21, Offline-Abhängigkeiten und getrennte Build-/Spielverzeichnisse wurden verwendet. Die aktuelle JAR und SHA-256 liegen unter `build/landscape-ui-check/libs/`. `python3 tools/verify_resources.py` und `git diff --check` bestehen.

## Garten und Weg, 05.11.2026

Der Gebäudegraph und der neue Bedienfeldabschnitt **Außenbereich** bieten **Garten** als Rasterfläche mit optional geteilter Flächenquelle sowie **Weg** als geordneten Pfad mit X/Y/Z-Höhenpunkten. Oberfläche, Bodenhöhe, Untergrundmaterial, Bodenaufbau und Freiraum sind konfigurierbar. Der Garten bietet zusätzlich Zaun an/aus, Material und Höhe; der Weg eine Breite von 1–16 Blöcken. Optionale Außenbereichseingänge am Gebäude-Ergebnis übernehmen die Knoten auch ohne separates Dach in den gemeinsamen Bauplan. Vorschauen, Materialbedarf, typisierte Verbindungen, Löschen und JSON-Speicherung verwenden dieselbe Auswertung.

Der getrennte `build runGameTestServer`-Lauf besteht mit **272 JUnit-Tests ohne Fehler** und **allen 45 erforderlichen Minecraft-GameTests**. Zwölf neue Fachtests prüfen unregelmäßige Gartenflächen einschließlich Löchern, Zaunränder ohne doppelte Ecken, geordnete und ansteigende Pfade, Breiten, Untergrund-/Freiraumkoordinaten, ungültige Maße und Kreuzungen, Copycat-Oberflächen, gemeinsame Flächenquellen, Gebäudeeinbindung, Anschluss-/Löschregeln, Kollisionen, alte JSON-Standardwerte und vollständige Rundläufe. Der neue Welttest baut Garten und Weg mit exakten Survival-Vorräten, prüft die tatsächlichen Zaunverbindungen, freie Pfadzellen und Wiederholung ohne erneuten Itemverbrauch. Native Zäune und Mauern berücksichtigen beim Setzen ihre Nachbarn und erkennen verbundene Zustände beim erneuten Prüfen wieder.

`runClient -PlandscapeSmoke` besteht bei **854 × 480** und **427 × 240 GUI-Pixeln**. Echte Maus-/Tastaturereignisse ändern Zaunstatus und -höhe, Wegbreite, Bodenaufbau, Freiraum und die geordnete dreidimensionale Koordinatenliste. Graph-/Bedienfeldwechsel und Undo/Redo erhalten alle Daten; lokale 3D-Vorschauen werden tatsächlich gerendert. Visuell geprüft: [Garten normal](release-0.6.0/images/garden-node-normal.png), [Garten kompakt](release-0.6.0/images/garden-node-compact.png), [Weg mit Höhenverlauf](release-0.6.0/images/path-node-3d-normal.png) und [kompakte Wegvorschau](release-0.6.0/images/path-node-3d-compact.png). Die bestehende allgemeine `buildingPanelSmoke`-Prüfung erwartet bei Copycat-Quellen noch das frühere Dropdown und scheitert dort vor den Außenbereichsschritten; der neue Schalter prüft die Außenbereichsbedienung unabhängig davon.

`runClient -PgraphNodePreviewSmoke` besteht ebenfalls: alle Knotentypen einschließlich Garten und Weg besitzen lokale, einklappbare 3D-Vorschauen; normale und kompakte Ansichten, Faltzustand, Dokumenterhalt und die tatsächlichen Materialbildpunkte werden geprüft. Die [Garten-/Weg-Knotenvorschau](release-0.6.0/images/garden-path-graph-normal.png) zeigt einen verbundenen Gartenzaun und den eigenen Wegbelag; die [kompakte Ansicht](release-0.6.0/images/garden-path-graph-compact.png) ist ebenfalls visuell geprüft.

Protokolle: `build/landscape-validation.log`, `build/landscape-client-final.log` und `build/landscape-graph-preview-final.log`. Die Prüfläufe verwenden Java 21, vorhandene Offline-Abhängigkeiten, eigene Build-/Gradle-Projektcache-Verzeichnisse und getrennte Spielverzeichnisse. Die aktuelle JAR samt SHA-256 liegt unter `build/landscape-ui-check/libs/`. Reproduzierbare T-Haus-JSON-Dateien sind um die neuen Standardfelder aktualisiert; ihre Geometrie und Mengen bleiben identisch. `python3 tools/verify_resources.py` und `git diff --check` bestehen. Freiraum ist weiterhin eine Luftanforderung; vorhandenes Gelände muss vor dem Bau vorbereitet sein.

## Materialdialog direkt am Copycat-Knoten des Gebäudes, 05.11.2026

Der Materialeintrag des `BuildingKind.COPYCAT`-Knotens öffnet jetzt direkt `MaterialSelectionScreen` im Graphinspektor und im Definitionsbedienfeld. Der vorherige Dropdownpfad wurde entfernt. Der Dialog verwendet die Gruppen und die bereits gedrehte Geometrie der tatsächlichen Copycat-Quelle. Gruppen mit `surface` verwenden die Rolle des Knotens; andere Gruppen verwenden ihre eigene Rolle. Gemeinsame Rollen bleiben im Entwurf synchron, und **Übernehmen** schreibt alle geänderten Rollen gemeinsam in die Gebäudepalette. Quellformen und Graphgeometrie bleiben erhalten.

`build runClient -PmaterialSelectionSmoke` besteht mit **272 JUnit-Tests ohne Fehler**. Die ergänzte `BuildingCopycatMaterialSmoke` klickt exakt die Materialzeile des Knotens im echten Tisch-Graphinspektor sowie im kompakten Definitionsbedienfeld an. Sie prüft den direkten Dialog ohne altes Dropdown, tatsächliche Materialbildpunkte in Block- und Anwendungsvorschau bei **854 × 480** und **427 × 240 GUI-Pixeln**, isolierte Gruppen einer um X gedrehten Quelle, gemeinsame Rollen, angeschlossene Wandplatzierungen, Abbruch, gemeinsames Übernehmen, unveränderte Auswahl ohne zusätzlichen Undo-Schritt, Undo/Redo, JSON-Persistenz sowie unveränderte Quellbibliotheken. Die vollständige bisherige Materialdialog-Abnahme läuft weiterhin davor und besteht ebenfalls.

Protokoll: `build/copycat-node-material-validation.log`. Java 21, Offline-Abhängigkeiten und ein separates Clientverzeichnis `build/copycat-node-material-ui` mit 1 GiB Spielheap wurden verwendet; Konfiguration: `build/copycat-node-material-validation.init.gradle`. Die durch parallel ergänzte Knoten-Standardfelder veralteten T-Gebäude-Fixtures wurden über `ARCHITECT_WRITE_T_BUILDING_EXAMPLE=true` mit dem vorhandenen Exporter aktualisiert. Ressourcenprüfung und `git diff --check` bestehen. Aktuelle Aufnahmen: [Materialauswahl am Gebäudeknoten](release-0.6.0/images/copycat-node-material-normal.png) und [kompakte Auswahl](release-0.6.0/images/copycat-node-material-compact.png).

## Materialauswahl pro Copycat-Gruppe, 05.11.2026

`MaterialSelectionScreen` bietet einen durchsuchbaren Katalog der vom vorhandenen `CopycatMaterials`-Vertrag erlaubten Blockitems. Die Materialblockvorschau verwendet das tatsächliche Blockmodell; die Anwendungsvorschau verwendet die reale Copycat-Geometrie mit den gewählten Gruppenmaterialien. Die isolierte Ansicht berücksichtigt die aufgelöste Vordergrundpriorität. Änderungen bleiben bis **Übernehmen** lokal und werden gemeinsam als ein Undo-Schritt bestätigt. Der Tisch besitzt einen rechten M-Reiter; Formeditor, Gruppen- und Druckinspektor verwenden denselben Dialog. Am Tisch werden die Gruppenmaterialien und die Drehung mit der Formversion gespeichert und über den gemeinsamen Ladedialog wiederhergestellt.

`build` besteht mit **258 JUnit-Tests ohne Fehler**. `runClient -PmaterialSelectionSmoke` besteht bei **854 × 480** und **427 × 240 GUI-Pixeln**: echte Katalogklicks, Suche nach Registry-ID, Ausschluss von Nichtblöcken und BlockEntity-Materialien, Scrollen, Tastatur, Größenwechsel, Materialbildpunkte in beiden Vorschaufenstern, isolierte und leere Gruppen, unveränderte Geometrie, gemeinsames Übernehmen mehrerer Gruppen, Abbruch, unveränderte Auswahl ohne weiteren Undo-Schritt, Undo/Redo, unverkleideter Druck sowie tatsächliches Laden der gespeicherten Materialien/Drehung mit atomarem Undo/Redo. Die bestehende `runClient -PprinterSmoke`-Abnahme besteht ebenfalls einschließlich Menü-Host, Gruppeninspektor, Historie, Druckinventar, 43 begrenzten Slot-Klickflächen und GPU-Spiegelung.

Protokolle: `build/material-selection-final.log`, `build/material-selection-client-final.log` und `build/material-selection-printer-regression.log`. Verwendet wurden Java 21, Offline-Abhängigkeiten, das getrennte Clientverzeichnis aus `build/explicit-assembly-validation.init.gradle` und höchstens 2 GiB Spielheap. Visuell geprüft: [Materialauswahl](release-0.6.0/images/material-selection-normal.png) und [kompakte Materialauswahl](release-0.6.0/images/material-selection-compact.png). `python3 tools/verify_resources.py` und `git diff --check` bestehen. Serverseitige Itemtransaktionen wurden nicht verändert; Server-GameTests wurden für diese UI-Ergänzung nicht erneut ausgeführt.

## Oberer Graphübersicht-Reiter, 04.11.2026

Der Architektentisch zeigt vorerst ausschließlich **Graphübersicht** als oberen G-Reiter. Oben, links und rechts verwenden denselben nativen CC-Sidebar-Renderer; die obere Textur ist um 90° gedreht, das Symbol bleibt aufrecht. Die Raumdefinitionen und weitere Funktions-Tabs sind zurückgestellt.

`build` besteht mit **258 JUnit-Tests ohne Fehler**. `runClient -PbuildingWorkspaceSmoke` prüft den einzelnen oberen Reiter, Maße und Anschluss an den Rahmen, echte Klicks bei **854 × 480** und **427 × 240 GUI-Pixeln**, die Rückkehr aus dem Copycat-Editor sowie erhaltene Dokumente und Undo-/Redo-Stapel. Die bestehende Graph-, Grundriss-, Definitions- und klassische Navigation besteht ebenfalls. `runClient -PworkshopSmoke` prüft nach den kompakten Abstandskorrekturen erneut Modulraster/3D, Material-, Platten- und Bauansichten samt tatsächlicher Bedienung. Visuell geprüft sind insbesondere der obere Reiter und die kompakte Bausteuerung ohne Beschriftungs- oder Fußzeilenüberdeckung.

Protokolle: `build/architect-top-tabs-validation.log` und `build/architect-top-tabs-workshop-validation.log`. Beide Läufe verwenden Java 21, vorhandene Offline-Abhängigkeiten und das getrennte Clientverzeichnis `build/building-workspace-client`; temporäre Compilerdateien liegen wegen der ausgeschöpften `/tmp`-Quota im Projekt. `python3 tools/verify_resources.py` und `git diff --check` bestehen.

Aktuelle Aufnahmen: [Graphübersicht normal](release-0.6.0/images/architect-table-top-tab-normal.png), [Graphübersicht kompakt](release-0.6.0/images/architect-table-top-tab-compact.png) und [kompakte Bausteuerung](release-0.6.0/images/architect-table-top-tab-build-compact.png).

## Dreigeschossiges T-Testgebäude in einer gespeicherten Spielwelt, 02.11.2026

`TBuildingExample` und `examples/t-building/` enthalten einen vollständigen, bearbeitbaren Gebäudegraph mit acht Copycat-Formen. Jedes der drei Geschosse besitzt denselben echten T-Grundriss: 15 × 5 Querflügel und mittiger 5 × 10 Stamm, 125 Innenzellen bei fünf Blöcken Höhe. Die äußere Wandhülle misst 17 × 17 Blöcke. Gebaut werden 1.902 Copycat-Positionen, darunter 214 Fensterzellen; 1.875 Innenzellen und drei Eingangszellen bleiben frei. Steinziegel, Ziegel, weiße Terrakotta, Quarz, dunkle Eichenholzrahmen, Glas, Eichenholzböden und Tiefenschiefer-Flachdach ergeben acht tatsächliche Materialien. Die Fassade enthält 180 erhabene Quarzquadrate, alternierende Friesfelder, senkrechte Pilaster und umlaufende Bänder.

**258 JUnit-Tests ohne Fehler** im abschließenden `build runClient -PtBuildingVerify`-Lauf; darunter sechs neue Anforderungsprüfungen für T-Kontur, Geschosse, Fenster ohne opake Rückwand, Material-/Reliefgeometrie, JSON-Exporte und komprimierte Speicherung des vollständigen Entwurfs. **Alle 44 erforderlichen Minecraft-GameTests bestehen.** Der neue Welttest setzt sämtliche 1.902 Positionen mit exakt berechneten Survival-Vorräten, vergleicht Materialvoxel, Zahlungsbelege und NBT an jedem Block und prüft alle 33 verschiedenen Kollisionsformen vollständig in beiden Abfragepfaden. Alle Geschosse besitzen Fenster in jeder Himmelsrichtung; die beiden T-Rücksprünge bleiben auch unter dem Dach offen. Der Gesamtbedarf beträgt 286.608 mB Zink und die in `examples/t-building/material-bill.json` angegebenen Oberflächenitems. Serverprotokoll: `build/t-building-server.log`.

Die echte Clientwelt wurde als separate Kopie der lokalen Testwelt unter `build/t-building-client` vorbereitet; die Ausgangswelt bleibt erhalten. Der integrierte Server hat das komplette Bauwerk aufgebaut und Formen sowie Gebäude für den lokalen Spieler Dev gespeichert. Der wirkliche Architektentisch öffnet dessen Graph und T-Grundriss. Ein Klick auf seine Speichern-Schaltfläche sendet erfolgreich einen komprimierten Befehl mit mehr als 30.000 JSON-Zeichen und erhält die neue gespeicherte Version **t_house@2**. Dafür wurde die veraltete rohe 30.000-Zeichen-Sperre in `ClientState.send` durch die vorhandene begrenzte Transportprüfung ersetzt. Der Paketcodec verwendet weiterhin `CommandTransport`. Tatsächliche Client-/Netzwerkausgabe: `build/t-building-client-final.log`.

Ein anschließend frisch gestarteter Client/Server lädt die gespeicherte Welt und bestätigt alle 1.902 tatsächlichen Blockgeometrien/Materialien, die freie Innenfläche, die gespeicherte Bibliothek und den Tischbezug erneut. Vollständiger Abschlusslauf: `build/t-building-reload.log` (**BUILD SUCCESSFUL**). Die geprüfte Welt liegt auch unter `run/saves/Architekt T-Testhaus`; das geprüfte ZIP liegt unter `build/exports/Architekt-T-Testhaus.zip`. Der Tisch steht bei **63 / 81 / 75**, der Gebäudeursprung bei **56 / 81 / 56**. Der Weltordner enthält die zugehörige Prüfmetadatei und eine kurze Anleitung. Das ZIP wurde auf Eintragsintegrität und übereinstimmende Metadaten geprüft.

Visuell geprüft sind ausschließlich echte Spielaufnahmen: [Außenansicht](release-0.6.0/images/t-building-exterior.png), [Rückseite](release-0.6.0/images/t-building-rear.png), [T-Draufsicht](release-0.6.0/images/t-building-top.png), [Fassadendetails](release-0.6.0/images/t-building-facade.png), [Eingang](release-0.6.0/images/t-building-entrance.png), [Graph im tatsächlichen Tisch](release-0.6.0/images/t-building-graph.png) und [vollständiger T-Grundriss](release-0.6.0/images/t-building-floorplan.png). Java 21, Offline-Abhängigkeiten und höchstens 2 GiB Spielheap wurden verwendet. `python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich; JAR und SHA-256 sind aktualisiert.

## Gemeinsamer Copycat-Graph, Auswahl und einklappbare Knotenvorschauen, 02.11.2026

Der Architektentisch bietet einen eigenen Copycat-Graph-Reiter mit erhaltenem Entwurf, Ansicht und Undo-/Redo-Verlauf. Drucker und Tisch wählen dieselben versionierten Formen über `SavedCopycatSelectionScreen`: tatsächliche Blockvorschau mit gespeicherten Verkleidungen, `/`-Ordner, globale Suche, Scrollen und Tastaturbedienung. Copycat-Quellen besitzen eigene Achse/Vierteldrehung im Inspektor; die Auswertung berücksichtigt diese vor allen anschließenden Segment-, Wand-, Boden-, Decken-, Eck- und Dachtransformationen. Bestehende JSON-Dokumente behalten ihre bisherigen Standardwerte.

Alle Form- und Gebäudeknoten haben einen unabhängigen 3D-Ausklapper direkt im Graphen. Der Zustand bleibt reine Ansicht und überlebt Neuaufbau sowie Größenwechsel. Geometriequellen zeigen genaue Drahtvolumen; Materialknoten verwenden den gemeinsamen Compiler. Eine explizit noch ausstehende Dachdefinition bleibt als solche erkennbar. Die transformierte Graphvorschau verwendet die native Clip-Fläche; die Inspektordarstellung behält ihre bisherige Begrenzung. Rasterinhalte sind zentriert, Gitterlinien füllen den sichtbaren Bereich und Zeichnen/Klickprüfung verwenden dieselbe Projektion. Kompakte Quelleninspektoren öffnen sich am Raster.

Mit Java 21 und vorhandenen Offline-Abhängigkeiten bestehen **252 JUnit-Tests ohne Fehler** sowie **alle 43 erforderlichen Minecraft-GameTests**, einschließlich der vollständigen Survival-Turtle-Bauten mit 603 und 1115 Platzierungen. Protokolle: `build/copycat-final-build.log` und `build/copycat-shared-server.log`. Neue Fachtests prüfen Pfadhierarchien, exakte Versionsschlüssel, alte JSON-Daten, Quellenrotation und deren Zusammensetzung, identische isolierte Boden-/Deckenproben, Material-/Luft-/Teppichfälle sowie Zentrierung, negative Koordinaten und inverse Raster-Hitprüfung.

Die tatsächlichen Clientläufe verwenden `-I build/explicit-assembly-validation.init.gradle`, getrennte Spielverzeichnisse und höchstens 2 GiB Spielheap. `savedCopycatSmoke` prüft die Auswahl aus dem echten Copycat-Inspektor, Vorschau, Ordner, Suche, Abbrechen, Quellenwechsel und X-/Z-Rotation mit Undo/Redo. `graphNodePreviewSmoke` bedient native Schalter für sämtliche Knotentypen, bestätigt unabhängige Zustände ohne Dokumentänderung und prüft tatsächliche Materialbildpunkte innerhalb der gezoomten Vorschauflächen. `explicitAssemblySmoke` verlangt zusätzlich, dass alle belegten Rasterzellmitten vollständig im sichtbaren Inspektor liegen. `buildingWorkspaceSmoke` ergänzt normale/kompakte Copycat-Tabwechsel, Rotations-Undo/Redo und dieselbe Auswahl nach Knoteneinfügung. Die bestehenden `printerSmoke`, `kilaGraphSmoke` und `buildingPreviewSmoke` prüfen Drucker-/Inventarhost, Kabel, Quellenkatalog, Raum-/Stockwerksansichten und die bisherigen Inspektorvorschauen. Protokolle: `build/copycat-final-<Smoke-Name>.log`.

Visuell geprüft: [gemeinsame Auswahl](release-0.6.0/images/copycat-library-normal.png), [kompakte Auswahl](release-0.6.0/images/copycat-library-compact.png), [Gebäudeknoten](release-0.6.0/images/building-node-previews-normal.png), [einzeln eingeklappt](release-0.6.0/images/building-node-previews-folded.png), [kompakte Gebäudeknoten](release-0.6.0/images/building-node-previews-compact.png), [Copycat-Graph am Tisch](release-0.6.0/images/architect-table-copycat-normal.png), [kompakter Tisch](release-0.6.0/images/architect-table-copycat-compact.png), [zentriertes Raumraster](release-0.6.0/images/explicit-assembly-geometry-normal.png) und [kompaktes Segmentraster](release-0.6.0/images/explicit-assembly-layout-compact.png). Ressourcenprüfung und `git diff --check` bestehen; die abschließende JAR und SHA-256 stehen unter `build/libs/copycat_architect-0.5.1.jar`.

## 2D-/3D-Vorschauen für Segmente, Räume und Dächer, 02.11.2026

`BuildingGraphCanvas` bietet im Definitionsbedienfeld und Graphinspektor feste Schalter für Parameter, 2D und 3D. Die Vorschau zeigt die Geometrie des gewählten Segments, Musters, Raums oder Dachs unabhängig vom Gesamtgebäude. Segmentfront, Raum-/Dachdraufsicht, ausgeblendete Raumdecke samt Innenraumkontur und eigene Kameradrehungen verwenden die tatsächlichen Material- und Copycat-Daten. Die noch offene explizite Dachanordnung zeigt ihren ausstehenden Zustand in beiden Modi; ältere Dächer zeigen ihre berechnete Form und kennzeichnen bei Bedarf die Beispielfläche.

Der abschließende `build runClient -PexplicitAssemblySmoke` besteht mit **205 JUnit-Tests ohne Fehler** und der bestehenden echten Baukettenabnahme. Die sechs neuen Fachtests prüfen isolierte Sparse-Segmente, Muster und Luftzellen, erhaltene Gruppen/Verkleidungen/Rotationen, lokale Räume trotz fehlerhaftem Gesamtgebäude, Dachgrundflächen ohne funktionierende Wand-/Plattenrezepte und exakt unabhängig konstruierte alte Flach-/Giebeldächer einschließlich Gesamtplan und Kosten. Die Minecraft-Serverabnahme besteht weiterhin mit **allen 43 erforderlichen GameTests**. Protokolle: `build/building-previews-explicit-regression-validation.log` und `build/building-previews-server-validation.log`.

`runClient -PbuildingPreviewSmoke` besteht abschließend bei **854 × 480** und **427 × 240 GUI-Pixeln**. Die neue Abnahme bedient echte Abschnitts- und Definitionslisten, beide Vorschautasten, die kompakten Parameterseiten und Rechtsklickziehen in den tatsächlichen 3D-Flächen. Sie prüft unterschiedliche lokale Pläne trotz absichtlich unvollständigem Haus, unabhängige und erhaltene Kamerawinkel, geschützte Tablücken, begrenzte Zeichenflächen sowie unverändertes vollständiges JSON und beide bereits gefüllten Undo-/Redo-Stacks. Alle vier ausstehenden Dachansichten bleiben konsistent. Protokoll: `build/building-previews-final-client-validation.log`.

Alle Läufe verwenden Java 21, Offline-Abhängigkeiten und das getrennte Spielverzeichnis aus `build/explicit-assembly-validation.init.gradle`. Visuell geprüft sind die 16 tatsächlichen Clientaufnahmen, darunter [Segment 2D](release-0.6.0/images/building-preview-segment-2d-normal.png), [Raum 2D](release-0.6.0/images/building-preview-room-2d-normal.png), [Dach 3D](release-0.6.0/images/building-preview-roof-3d-normal.png), [kompakter Raum 3D](release-0.6.0/images/building-preview-room-3d-compact.png) und [kompaktes ausstehendes Dach](release-0.6.0/images/building-preview-roof-pending-3d-compact.png). `python3 tools/verify_resources.py` und `git diff --check` bestehen. Das JAR samt SHA-256 ist aktualisiert.

## Explizite Baukette Copycat → Gebäude, 02.11.2026

Neue Tischentwürfe verbinden Copycats mit einer Segmentanordnung, Segmente mit einer separaten Raumgeometrie aus belegten ganzzahligen X/Z-Zellen zu Räumen und diese zu Stockwerken und dem Tischgebäude. Boden und Decke verwenden horizontal eingebaute Segmente. Die noch nicht definierte Dachanordnung bleibt als eigener typisierter Quellknoten sichtbar; der neue Startgraph schließt das Gebäude vorerst durch die oberste Decke ab.

`build runGameTestServer -PgraphWorldSmoke` mit Java 21 besteht mit **199 JUnit-Tests ohne Fehler** und **allen 43 erforderlichen Minecraft-Servertests**. Die neuen Fachtests prüfen explizite Quellen und Fehlverbindungen, sparse Copycat-Zuordnung und Rotation, horizontal gekippte asymmetrische Paneele mit erhaltenen Verkleidungen und Quellbelegen, unregelmäßige Hüllen, große Segmentfelder und Muster, gemeinsame Raumgeometrien, beide Quellwechselpfade mit Überlappungsprüfung, alte JSON-Dokumente und den ausstehenden Dachvertrag. Ein geclipptes zweigruppiges Panel behält **576 Voxel** sowie beide Quellbelege; der Boden liegt lokal bei Y=13…15 und die Decke bei Y=0…2. Parser- und Transporttests prüfen vollständige Integerlisten, ungültige Topologien, 4.096-Zellen-Requests einschließlich JSON-Escaping, unveränderte kleine Requests, Größen-/UTF-8-Grenzen und begrenzte Dekomprimierung. Protokoll: `build/explicit-assembly-server-validation.log`.

Die neue tatsächliche Clientabnahme `runClient -PexplicitAssemblySmoke` besteht bei **854 × 480** und **427 × 240 GUI-Pixeln**. Sie bedient native Ports, Inspector-Listen und Raster, verwirft falsche Copycat-Verbindungen und ungültige Integerentwürfe, bearbeitet eine gemeinsame Quelle für zwei Räume, verbindet zwei Stockwerke und prüft vollständiges JSON-Undo/Redo nach Koordinaten-, Raster- und Anordnungsänderungen. Vorlagen, Ansichtswechsel und Dachplatzhalter bleiben konsistent.

Die bestehenden `floorPlanSmoke`, `buildingPanelSmoke`, `buildingWorkspaceSmoke`, `workshopSmoke` und `kilaGraphSmoke` bestehen ebenfalls. Nach den abschließenden Transport- und Quellwechselkorrekturen wurden `explicitAssemblySmoke`, `floorPlanSmoke` und die vollständige `kilaGraphSmoke`-Abnahme erneut erfolgreich ausgeführt. Letztere enthält weiterhin die tatsächlichen Tischprojektionsfelder, klassische Gebäude, typisierte Kabel, Raum-/Stockwerksinspektoren und die gemeinsame Historie. Protokolle: `build/explicit-assembly-{buildingPanelSmoke,buildingWorkspaceSmoke,workshopSmoke}-validation.log` sowie `build/explicit-assembly-final-{explicitAssemblySmoke,floorPlanSmoke,kilaGraphSmoke}-validation.log`.

Alle Läufe verwendeten `--offline --console=plain`, vorhandene gepinnte Abhängigkeiten und höchstens 2 GiB Spielheap. Die Abschlussläufe verwendeten `-I build/explicit-assembly-validation.init.gradle` mit getrennten Client-/Serververzeichnissen. Die regulären Spieleinstellungen blieben erhalten.

Visuell geprüft: [vollständige Kette](release-0.6.0/images/explicit-assembly-graph-normal.png), [kompakter Graph](release-0.6.0/images/explicit-assembly-graph-compact.png), [Raumgeometrie](release-0.6.0/images/explicit-assembly-geometry-normal.png), [kompakte Raumgeometrie](release-0.6.0/images/explicit-assembly-geometry-compact.png), [Segmentanordnung](release-0.6.0/images/explicit-assembly-layout-normal.png), [kompakte Anordnung](release-0.6.0/images/explicit-assembly-layout-compact.png), [Dachplatzhalter](release-0.6.0/images/explicit-assembly-roof-normal.png) und [kompakter Dachplatzhalter](release-0.6.0/images/explicit-assembly-roof-compact.png).

`python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich. JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

## Architektentisch im Druckerrahmen, 02.11.2026

Der Architektentisch verwendet den CC-Computerrahmen und die Randreiter des 3D-Druckers. Graph, zuschaltbarer Inspektor, Definitionsbedienfeld, Grundriss, Module, Materialpalette und Bausteuerung sind bei **854 × 480** und **427 × 240 GUI-Pixeln** geprüft. Der Graph wird beim ersten Öffnen eingepasst. Die kompakte Bausteuerung ordnet Auftragsaktionen in zwei Spalten an; Modulraster und 3D-Vorschauen bleiben innerhalb des Arbeitsbereichs.

Mit Java 21 und den vorhandenen Offline-Abhängigkeiten erfolgreich ausgeführt:

- `build`: **170 JUnit-Tests ohne Fehler**, aktuelle Mod-JAR erzeugt.
- `runClient -PbuildingWorkspaceSmoke`: tatsächliche Randtab-/Menüklicks, kompletter Graph und einblendbarer Inspektor, alle fünf Definitionsabschnitte, Grundriss, Module, Palette, Bauansicht sowie neue klassische Dokumente und ihre Graph-/Bedienfeldwechsel. Navigation erhält das vollständige JSON und beide Undo-/Redo-Stapel.
- `runClient -PbuildingPanelSmoke`: verschachtelte Segmente, Definitionslisten, echte Panel-/Graph-Ergänzungen, wiederholte Rauminstanzen, Verschiebungen und abgewiesene Überlappungen.
- `runClient -PfloorPlanSmoke`: Rasterbearbeitung, Rechtecke, Wände, Raumfarben, Pan/Zoom, ungültige Konturen und Undo/Redo.
- `runClient -PworkshopSmoke`: echte Modulrasterklicks, klassische Materialien/Platten/Bauen, validierte Werteingabe und 3D-Vorschau. Nach der letzten Raster-/Hinweisabstandskorrektur zusammen mit `build` erneut erfolgreich ausgeführt.
- `runClient -PkilaGraphSmoke`: vollständige vorhandene Graphabnahme einschließlich klassischer Gebäude, Raum-/Stockwerksinspektoren, typisierter Kabel, Quellen, gemeinsamer Historie und tatsächlicher Tischprojektionsfelder.

Alle Aufrufe verwendeten `--offline --console=plain` und für Clientläufe `-I build/building-workspace-client.init.gradle`. Die separate Testkonfiguration unter `build/building-workspace-client` deaktiviert das frühe Ladefenster und VSync; die regulären Spieleinstellungen bleiben erhalten. Der Spielheap ist auf 2 GiB begrenzt. Protokolle: `build/building-workspace-final-validation.log`, `build/building-workspace-panel-validation.log` und `build/building-workspace-{floorPlanSmoke,workshopSmoke,kilaGraphSmoke,buildingWorkspaceSmoke}-validation.log`. Diese Bedienprüfungen benötigen keine geladene Welt; die Server-GameTests wurden für die UI-Änderung nicht erneut ausgeführt.

Visuell geprüft: [Graph](release-0.6.0/images/architect-table-graph-normal.png), [kompakter Graph](release-0.6.0/images/architect-table-graph-compact.png), [kompaktes Bedienfeld](release-0.6.0/images/architect-table-panel-compact.png), [Grundriss](release-0.6.0/images/architect-table-floorplan-compact.png), [Bausteuerung](release-0.6.0/images/architect-table-build-compact.png), [Modulraster](release-0.6.0/images/architect-table-module-compact.png), [3D-Modul](release-0.6.0/images/architect-table-module-3d.png) und [klassisches Gebäude](release-0.6.0/images/architect-table-classic-compact.png).

`python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich. JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

## Eigener Historien-Tab und Kartuschen im Drucken-Tab, 01.11.2026

Die vollständige `printerSmoke`-Clientabnahme besteht bei **854 × 480** und **427 × 240 GUI-Pixeln**. Echte Screen-Ereignisse prüfen den linken Historien-Tab exakt 24 Pixel unter „Karte öffnen“, die getrennte Historienansicht, das Öffnen als zusätzliche Karte mit erhaltenen Druckeinstellungen, unveränderte bestehende Entwürfe und deaktivierte Aktionen bei leerer Historie. Alle **43 Slot-Mittelpunkte** – sieben Kartuschen und 36 Spielerplätze – liegen nach kompaktem Scrollen im sichtbaren Druckinspektor und werden richtig getroffen. Verdeckte Slotbereiche nehmen keine Klicks in der Werkzeugzeile an.

Das native Inventarmenü bleibt über Karten-, Graph- und Historienwechsel aktiv. Geprüft sind die gleiche `ShapeScreen`-Instanz im Menü-Host, weiterhin bedienbare native Knoten, unveränderte Sitzung, sichtbarer getragener Stack und GPU-Spiegelung. Der direkte Speicherzugriff am aufgestellten Drucker richtet nach seinem Snapshot ebenfalls den Drucken-Tab ein. Die bisherige gemeinsame Speicherschaltfläche fehlt in der Arbeitsfläche. Protokoll: `build/printerSmoke-client-validation.log`.

Auch das Scrollen durch 24 Einträge und die Ablehnung von Klicks in die leere Restfläche unterhalb sichtbarer Zeilen bestehen. Die bestehende `kilaGraphSmoke`-Abnahme besteht ebenfalls vollständig.

Der abschließende Lauf `./gradlew build runGameTestServer -PgraphWorldSmoke --offline --console=plain` besteht mit **170 JUnit-Tests ohne Fehler** und **allen 43 erforderlichen Server-GameTests**. Darunter sind die autoritativen Kartuschentransaktionen, Cursor-/Drag-/Shift-Klick-Gesten, gesperrte Geräteslots und der Turtle-Bau mit 603 Platzierungen. Protokoll: `build/printer-tabs-server-validation.log`. Ressourcenprüfung und `git diff --check` sind erfolgreich. Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

Visuell geprüft: [Historie](release-0.6.0/images/printer-history-normal.png), [Kartuschen im Drucken-Tab](release-0.6.0/images/printer-cartridges-print-normal.png), [kompaktes Inventar](release-0.6.0/images/printer-cartridges-print-compact.png) und [kompakte Historie mit Item am Mauszeiger](release-0.6.0/images/printer-history-compact.png).

## 3D-Zwischenergebnisse pro Formknoten, 01.11.2026

**170 JUnit-Tests ohne Fehler** mit Java 21. Sechs neue Fachtests prüfen unveränderte Quellgeometrie vor nachfolgenden Ausschnitten, alle booleschen Operationen, gültige Vorschauen bei unvollständigem Endknoten, leere Zwischengeometrie gegenüber fehlenden Eingängen, angeschlossene Rotationswerte sowie Material- und Cache-Trennung zwischen Knoten und Graphrevisionen. Leere fertige Formen werden weiterhin abgewiesen.

Die vollständige `printerSmoke`-Clientabnahme besteht. Jeder tatsächliche native Knoten besitzt eine angeordnete 3D-Vorschau; Quelle, gedrehte Quelle und nachfolgende Operation enthalten nachweislich unterschiedliche Geometrie. Der Rotationsknoten zeigt Z/90° als räumliche Achsenanzeige. Der kompakte Inspektor wird per Knotenklick geöffnet und gescrollt; sein Modell entspricht der ausgewählten Quelle und unterscheidet sich vom Endergebnis. Kartenwechsel, Mehrfachauswahl, Undo/Redo, Kontextmenü, Drucksteuerung, Weltblick und GPU-Spiegelung bestehen weiterhin. Protokoll: `build/printerSmoke-client-validation.log`.

Die vollständigen bestehenden Clientabnahmen `kilaGraphSmoke` und `stairsSmoke` bestehen ebenfalls. Damit sind native Kabel-/Quellenbedienung, normale/kompakte Ansichten, erhaltene Dokumente und Historien sowie die sechs einzelnen Byte-Vorschauen der schreibgeschützten Treppendefinition geprüft. Protokolle: `build/kilaGraphSmoke-client-validation.log` und `build/stairsSmoke-client-validation.log`.

Der abschließende Lauf `./gradlew build runGameTestServer -PgraphWorldSmoke --offline --console=plain` besteht mit **170 JUnit-Tests** und **allen 43 erforderlichen Server-GameTests**, einschließlich des Turtle-Baus mit 603 Platzierungen. Protokoll: `build/node-preview-server-validation.log`. Ressourcenprüfung und `git diff --check` sind erfolgreich; Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

Visuell geprüft: [Knoten mit eigenen 3D-Zwischenergebnissen](release-0.6.0/images/printer-node-previews.png) und [ausgewähltes Zwischenmodell im kompakten Inspektor](release-0.6.0/images/printer-node-inspector-compact.png).

## Texturen aufgestellter Kartuschen, 01.11.2026

Aufgestellte Kartuschen zeigen pro Platz silberne (leer), goldene (normal) oder Amethyst-Deckel (kreativ). Gemischte Ein- bis Viererstapel behalten ihre individuellen Texturen; Füllen, Entleeren und empfangene BlockEntity-Daten aktualisieren das Modell.

Mit Java 21 und vorhandenen Offline-Abhängigkeiten geprüft: **170 JUnit-Tests ohne Fehler**, **alle 43 erforderlichen Minecraft-GameTests bestanden**, vollständige bestehende Kartuschen-/Ponder-Clientabnahme und zusätzlich **alle 120 Variantenkombinationen** für ein bis vier aufgestellte Kartuschen, Client-Update-Tags sowie Texturwechsel durch Befüllen/Entleeren. Der Client lief mit eigener Konfiguration und einer Kopie der Testwelt unter `build/cartridge-texture-client`; die ursprüngliche Welt und ihre Einstellungen wurden nicht verändert. Der letzte Build verwendet wegen paralleler Arbeiten einen eigenen Testberichtordner über `build/cartridge-texture-test-output.init.gradle`.

Protokolle: `build/cartridge-texture-build.log`, `build/cartridge-texture-server-validation.log` (43 GameTests erfolgreich; der anschließende JUnit-Berichtzugriff kollidierte mit einem parallelen Build und wurde im separaten Berichtordner erfolgreich wiederholt), `build/cartridge-texture-client-validation.log`. Die [Einzel- und Mischstapel](release-0.6.0/images/architect-cartridges-11-placed-variants.png) wurden visuell geprüft. Ressourcenprüfung und `git diff --check` erfolgreich; JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` aktualisiert.

## Blockdrucker mit CC-Rahmen, Randtabs und mehreren Karten, 01.11.2026

`./gradlew build --offline --console=plain` mit Java 21 erfolgreich: **164 JUnit-Tests ohne Fehler**. Vier neue Fachtests prüfen Mehrfachduplikate mit internen und externen Kabeln, Schutz des Ergebnisknotens, vollständiges Trennen gelöschter Referenzen, Ausrichten ohne Geometrieänderung und gemeinsame Materialzuweisung ohne Verlust anderer Knotendaten.

Die vollständige `./gradlew runClient -PprinterSmoke --offline --console=plain`-Abnahme besteht bei **854 × 480** und **427 × 240 GUI-Pixeln**. Echte native Mausereignisse prüfen Shift-Ergänzung über Drücken und Loslassen, Auswahlrahmen bei geschlossenem Inspektor, gemeinsame Duplizieren-Aktion mit atomarem Undo, linke Kartenumschaltung mit getrennten History-Stapeln, erhaltene Ansicht/Auswahl/Verkleidung/Drehung, Kartenschließen und nur bei Kartenauswahl sichtbaren Drucken-Tab. Im kompakten Fenster sind Kontextmenü-Einfügung an der umgerechneten Klickposition, Undo und Scrollzugriff auf die letzte Kartenaktion geprüft. Der bestehende Augentest mit eingefrorener GPU-Spiegelung, sieben Kartuschenplätzen, Speichermenü und tatsächlichem Druckermodell besteht ebenfalls. Protokoll: `build/printerSmoke-client-validation.log`.

Die vollständige bestehende `kilaGraphSmoke`-Abnahme besteht ebenfalls: Form- und Gebäudegraphen, semantische Anschlüsse, native Kabeländerungen, Quellkatalog, klassische Dokumente, Raumraster, Stockwerksinspektoren, Tischbezug und gemeinsame Undo-/Redo-Wege. Protokoll: `build/kilaGraphSmoke-client-validation.log`. Ressourcenprüfung und `git diff --check` sind erfolgreich. Die aktuelle Mod-JAR und SHA-256 stehen unter `build/libs/copycat_architect-0.5.1.jar`.

Visuell geprüft: [Graph im erweiterten CC-Rahmen](release-0.6.0/images/printer-workspace-graph.png), [Gruppenauswahl](release-0.6.0/images/printer-workspace-selection.png), [kompakter Karteninspektor](release-0.6.0/images/printer-workspace-card-compact.png), [kompakter Drucken-Tab](release-0.6.0/images/printer-workspace-print-compact.png) und [kompakter Graph](release-0.6.0/images/printer-workspace-graph-compact.png). Die ursprünglichen Fenster-/GUI-Einstellungen werden nach der Abnahme wiederhergestellt.

## Bedienfeld mit Segment-, Raum-, Stockwerks-, Gebäude- und Dachdefinitionen, 31.10.2026

`./gradlew build runGameTestServer -PgraphWorldSmoke --offline --console=plain` mit Java 21 erfolgreich: **160 JUnit-Tests ohne Fehler** und **alle 43 erforderlichen Minecraft-GameTests bestanden**, einschließlich des vollständigen Turtle-Baus mit 603 Platzierungen. Sechs neue Fachtests prüfen verschachtelte horizontale/vertikale Segmentmuster, Eingangsreihenfolge, Luftzwischenlagen bis in den Raumplan, Materialrollen, zulässige Abmessungen, alte JSON-Dokumente ohne `patternAxis`, unverändertes Graphschema, Raumaußenmaße/Gesamthöhe und die oberste Decke als Flachdach ohne zusätzliche Dachplatte. Protokolle: `build/building-panel-build.log`, `build/building-panel-server-validation.log`.

`./gradlew runClient -PbuildingPanelSmoke --offline --console=plain` besteht die native Bedienprüfung bei **854 × 480** und **427 × 240 GUI-Pixeln**. Echte Screen-Maus-/Tastaturereignisse prüfen alle fünf Abschnitte, vertikale Definitionslisten, Auswahl ohne Dokumentänderung, verschachtelte Segmentanordnung im Dropdown, Hinzufügen über Bedienfeld und nativen Graph, vollständige JSON-Erhaltung beim Ansichtswechsel sowie gemeinsame Undo-/Redo-Historie. Die dritte Rauminstanz einer mehrfach verwendeten Raumdefinition lässt sich unabhängig verschieben; die Vorschau verändert das Dokument erst beim Loslassen. Abgewiesene Innenraumüberlappungen erhalten beide History-Stapel. Kompakt sind Grundriss und Stockwerkseigenschaften über ihre echten Schaltflächen erreichbar. Protokoll: `build/buildingPanelSmoke-client-validation.log`.

Visuell geprüft: [Segmente](release-0.6.0/images/building-panel-segments-normal.png), [Räume mit Innen-/Außenmaßen und Gesamthöhe](release-0.6.0/images/building-panel-rooms-normal.png), [Stockwerksgrundriss](release-0.6.0/images/building-panel-storeys-normal.png), [Gebäude](release-0.6.0/images/building-panel-building-normal.png), [Dach und Füllung](release-0.6.0/images/building-panel-roof-normal.png) sowie die kompakten Ansichten für [Segmente](release-0.6.0/images/building-panel-segments-compact.png), [Räume](release-0.6.0/images/building-panel-rooms-compact.png), [Stockwerke](release-0.6.0/images/building-panel-storeys-compact.png), [Gebäude](release-0.6.0/images/building-panel-building-compact.png) und [Dach](release-0.6.0/images/building-panel-roof-compact.png). Fenster-/GUI-Einstellungen wurden nach dem Clientlauf wiederhergestellt.

Die vollständige bestehende `kilaGraphSmoke`-Clientabnahme besteht ebenfalls: typisierte Kabel, gemeinsame Dokumenthistorie, klassische Gebäude ohne Konvertierung, alte Direktwand-Stockwerke, Raumraster, normale/kompakte Stockwerksinspektoren, Tischbezug und Bauprojektion. Die Stockwerksregression öffnet den Standardinspektor über die neue tatsächliche „Eigenschaften“-Schaltfläche. Protokoll: `build/kilaGraphSmoke-client-validation.log`.

Auch die unveränderte `floorPlanSmoke`-Abnahme des separaten Kontureditors besteht vollständig: normale/kompakte Raumlisten, Zeichnen/Löschen, Quellzuordnung, wiederholte Rauminstanzen, atomare Historie, Abbruch und abgewiesene Kontur-/Überlappungsänderungen. Protokoll: `build/floorPlanSmoke-client-validation.log`. Ressourcenprüfung und `git diff --check` erfolgreich; die aktuelle Mod-JAR und SHA-256 stehen unter `build/libs/copycat_architect-0.5.1.jar`.

## Treppen aus Bytes und schreibgeschützte Definitionskarte, 31.10.2026

Mit Java 21 erfolgreich geprüft: **154 JUnit-Tests ohne Fehler**, darunter sechs neue Definitionstests mit allen 128 gespeicherten Treppenorientierungen, X-/Y-/Z-Drehungen, alten JSON-Formen, booleschen Schnittmasken und getrennten Materialgruppen. **Alle 43 erforderlichen Minecraft-GameTests bestanden**; der Quellzustandsvergleich prüft zusammengesetzte Treppen gegen die veröffentlichten Blockzustände ihrer sechs Bytes. Serverprotokoll: `build/stairs-validation.log`.

Der abschließende Lauf `./gradlew build runClient -PgraphWorldSmoke -PstairsSmoke --offline --console=plain` besteht vollständig. Der echte Client betätigt Z-Rotation und Definitionsauge, prüft sechs Byte-Knoten mit Vereinigung und Ergebnis, abgewiesene Quellen-/Eigenschafts-/Kabel-/Lösch-/Verschiebeänderungen sowie unveränderte Form und Undo-/Redo-Historie nach dem Schließen. Normale **854 × 480** und kompakte **427 × 240 GUI-Pixel** sind geprüft; die kompakte Karte besitzt getrennte Graph-/Bedienfeld-Tabs. Esc schließt zuerst ein geöffnetes Kontextmenü und anschließend die Karte. Die Knoten stehen ohne Überlappung in zwei Byte-Spalten. Clientprotokoll: `build/stairs-client-validation.log`.

Visuell geprüft: [Definitionskarte](release-0.6.0/images/stairs-definition-normal.png), [kompakte Karte](release-0.6.0/images/stairs-definition-compact.png) und [Auge im Inspektor](release-0.6.0/images/stairs-inspector-normal.png). Ressourcenprüfung und `git diff --check` sind erfolgreich; Fenster-/GUI-Einstellungen wurden wiederhergestellt. Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

## Grundriss mit Rauminspektor, 31.10.2026

`./gradlew build runClient -PfloorPlanSmoke --offline --console=plain` mit Java 21 erfolgreich: **148 JUnit-Tests ohne Fehler** und vollständiger Grundriss-Clienttest. Die sechs neuen Fachtests prüfen Kantenzusammenhang, eingeschlossene und diagonal zugeklemmte Löcher, vollständiges Löschen, rechteckige Ergänzungen/Ausschnitte, Koordinatengrenzen, unveränderte Wandzuordnungen und atomare Ablehnung ungültiger Änderungen.

Der echte Minecraft-Client bedient bei **854 × 480** und **427 × 240 GUI-Pixeln** die Raumliste und das Raster über Screen-Mausereignisse. Geprüft sind eigene, bei Raumlöschung stabile Farben; Zellen, ergänzende und löschende Rechtecke; Vorschau ohne Dokumentänderung; ein Undo-Schritt je Geste und vollständiges Redo; Abbruch mit Esc; Raster-Pan und Zoom um den Mauszeiger; unveränderte Historie nach abgewiesenen Löchern, getrennten Zellen und Raumüberlappungen. Raumversätze und spätere Instanzen derselben Raumquelle werden beim Anklicken berücksichtigt. Hinzufügen verbindet einen neuen Raum außerhalb vorhandener Raumhüllen; Löschen entfernt auch seine Stockwerkseingänge mit den zugehörigen Versätzen. Der verbleibende Bauplan lässt sich weiterhin kompilieren. Dynamische Wandlisten, ihre Sollbreiten, typisierte Quellenwahl sowie unveränderte andere Raumdefinitionen sind ebenfalls geprüft.

Visuell geprüft: [Grundriss und Raumliste](release-0.6.0/images/floorplan-normal.png), [dynamische Wandliste](release-0.6.0/images/floorplan-walls.png), [Rechteckvorschau](release-0.6.0/images/floorplan-rectangle-preview.png), [Wandquellen](release-0.6.0/images/floorplan-wall-sources.png) und [kompakter Grundriss](release-0.6.0/images/floorplan-compact.png). Die Mod-JAR und ihre SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

Auch `./gradlew runClient -PkilaGraphSmoke --offline --console=plain` besteht vollständig, einschließlich der bisherigen Raum-/Stockwerksinspektoren, klassischer Gebäude, nativer Graphanschlüsse und gemeinsamer Undo-/Redo-Historie. `python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich. Die vorübergehende Ladefenster-Einstellung und GUI-Skalierung sind wiederhergestellt.

## Molten Metals, Volumenverbrauch und Kartuschenanimation, 31.10.2026

Molten Metals – Neoforged **1.0.1** (Modrinth-Dateiversion `dCzZU1My`, interne Modversion 1.0.0) ist auf Client und Server erforderlich. Eigene Flüssigkeit, Flüssigkeitsblock, Eimer, Fluidgrafik und Schmelzrezepte sind entfernt. Die 47 Recyclingrezepte, die direkte Kartuschenherstellung und die Inventarversorgung verwenden den externen Maßstab **144 mB pro Zinkbarren**. Die Standardkartusche fasst **256 mB**. Druckkosten zählen die belegten Voxel der fertigen 16³-Geometrie und runden pro Block auf ganze mB auf: Vollblock 256, Treppe 192, Halbplatte 128, 3/16-Panel 48 mB. Überlappungen, Materialaufteilungen und ausgeschnittene Luft werden korrekt berücksichtigt.

Mit Java 21, den gepinnten Abhängigkeiten und höchstens 2 GiB Spielheap erfolgreich ausgeführt:

- `./gradlew build --offline --console=plain`: **142 JUnit-Tests ohne Fehler**, einschließlich fünf neuer Volumentests und angepasster Graph-/Geometrietests.
- `./gradlew runGameTestServer -PgraphWorldSmoke --offline --console=plain`: **alle 43 erforderlichen Minecraft-GameTests bestanden**. Dabei echte Create-Ausguss-/Abflussaufrufe, Fluidleitungen, 144-mB-Barrenversorgung, direktes Kartuschenrezept, Rückgaben, Druckertransaktionen, Recycling und Turtle-Bau mit 603 Platzierungen geprüft.
- `./gradlew build runClient -PcartridgeSmoke --offline --console=plain`: **vollständige Ponder-/Modellabnahme erfolgreich**. Der Lauf verwendete eine separate Kopie einer lokalen Testwelt namens `Architect Cartridge Smoke`, ohne die Ausgangswelt zu verändern.
- `./gradlew build runClient -PprinterSmoke --offline --console=plain`: **Druckerabnahme erfolgreich**. Die 128-mB-Kosten der geprüften XOR-Geometrie bleiben in normalen und kompakten Graphen sichtbar; Inventar, Display und Mausbedienung bestehen weiterhin.
- `./gradlew runClient -PkilaGraphSmoke --offline --console=plain`: **vollständige native Graphabnahme erfolgreich**, einschließlich normaler/kompakter Gebäudegraphen mit dauerhaft sichtbarer mB-Gesamtsumme, boolescher Gruppen, Raumraster, Stockwerksanordnung und Tabellen-Weltzuordnung.

Die Ponder-Prüfung führt die tatsächlichen Storyboards in einer geladenen Clientwelt aus. Sie kontrolliert Mischer, beheiztes Becken, Pumpe, verbundene Rohre, 256 mB externes Zink im Ausguss, die aktive Gießphase einschließlich renderbarer Flüssigkeit, leere/volle/kreative Depotitems, 32 mB Beckenrest, vier aufgestellte Kartuschen sowie Drucker und Architektentisch. Alle drei Itemmodelle besitzen sechs Flächenrichtungen, gültige Texturen und volumetrische Modelle. Die Prüfung rendert die tatsächlichen Create-Band- und Depotrenderer aus zwei Blickrichtungen; die Kartuschen richten sich nicht als flache Fluiditems zur Kamera aus.

Alle zehn Aufnahmen wurden visuell geprüft: [Schmelzen](release-0.6.0/images/architect-cartridges-01-melting.png), [Ausguss und leere Kartusche](release-0.6.0/images/architect-cartridges-02-spout-and-empty.png), [sichtbarer Zinkstrahl](release-0.6.0/images/architect-cartridges-03-pouring.png), [volle Kartusche](release-0.6.0/images/architect-cartridges-04-filled.png), [kreative Kartusche](release-0.6.0/images/architect-cartridges-05-creative.png), [vier Kartuschen lagern](release-0.6.0/images/architect-cartridges-06-storage.png), [3D auf Band und Depot](release-0.6.0/images/architect-cartridges-07-belt-depot-models.png), [zweite Blickrichtung](release-0.6.0/images/architect-cartridges-08-models-other-side.png), [Druckeranleitung](release-0.6.0/images/architect-cartridges-09-printer-scene.png) und [Architektentischanleitung](release-0.6.0/images/architect-cartridges-10-table-scene.png). Zusätzliche Sichtprüfung der mB-Anzeige: [normaler Formgraph](release-0.6.0/images/zinc-volume-graph-normal.png), [kompakter Formgraph](release-0.6.0/images/zinc-volume-graph-compact.png), [Druckerspeicher](release-0.6.0/images/zinc-volume-storage-normal.png), [normaler Gebäudegraph](release-0.6.0/images/zinc-volume-building-native.png) und [kompakter Gebäudegraph](release-0.6.0/images/zinc-volume-compact-building-native.png).

Der externe Port protokolliert weiterhin Gussform-Modellwarnungen und Parsefehler für optionale Fremdmod-/Gussformrezepte. Der verwendete Zink-/Kartuschenpfad und sämtliche Abnahmetests funktionieren; diese Meldungen stammen aus der unveränderten Molten-Metals-JAR. `python3 tools/verify_resources.py` und `git diff --check` sind erfolgreich. Die lokalen Ladefenster-Einstellungen wurden nach jedem Clientlauf wiederhergestellt. Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

## Gemeinsame Panelwände und Tischprojektion (31.10.2026)

Abschließend erfolgreich mit Java 21 und den bisherigen gepinnten Abhängigkeiten geprüft: **137 JUnit-Tests ohne Fehler**, **alle 42 erforderlichen Minecraft-GameTests** und vollständiger nativer `kilaGraphSmoke`-Clientlauf. Ressourcen, Sprachschlüssel und ROM-Lua bestehen `python3 tools/verify_resources.py`. Der geprüfte Build liegt als `build/libs/copycat_architect-0.5.1-table-preview.jar` mit SHA-256 daneben.

Während der Arbeit kamen unabhängige Kartuschen-, Rezept- und Abhängigkeitsänderungen im gemeinsamen Checkout hinzu. Daher wurde diese Funktion in `build/table-preview-verification` separat mit dem anfänglichen Projektstand geprüft. Wandgeometrie und Tischprojektion sind enthalten; die parallele Umstellung von Materialkosten und Kartuschen wurde weder zurückgesetzt noch in diesen Prüf-Build aufgenommen. Die Mengenangaben in diesem Abschnitt beziehen sich daher auf den isolierten Prüfstand. Ausgabe: `build/table-preview-validation.log`. Befehl: `./gradlew build runGameTestServer runClient -PgraphWorldSmoke -PkilaGraphSmoke --offline --console=plain`.

Neue Fachtests prüfen gemeinsame und getrennte Wandblöcke, Verkleidungen und lokale Gruppen-IDs, getrennte Quellquittungen, gemischte Eckformen, Vorrang früherer Rauminstanzen, drei zusammentreffende Räume und Freiraum-/Blockkonflikte. Projektionstests decken vier Drehungen, Tischhöhe, Platzierungen und Freiraumforderungen, unveränderte Kosten, alte JSON-Dokumente und Eingabegrenzen ab.

Der neue Welttest baut zwei geschlossene Räume mit **124 Survival-Platzierungen**. Eine gemeinsame Wandzelle enthält zwei 3/16-Panel-Flächen mit Steinziegeln und Eichenholz und einen 10/16-Zwischenraum. Alle Innenzellen bleiben Luft. Beide Kollisionspfade, Materialauswahl an beiden Seiten, NBT, Zahlungsquittungen und kostenfreie Wiederholung sind geprüft. Tatsächlicher Abbau erstattet exakt 128 Rohmaterialeinheiten und je ein Materialitem.

Die Clientabnahme bedient Tischmarker, Verschiebung, validiertes Y-Feld, Vierteldrehung und Undo/Redo über echte Screen-Ereignisse bei beiden GUI-Skalierungen. Weitere Prüfungen verwenden die Baukoordinatenfelder und verlustfreie Tisch-Synchronisierung einschließlich vorübergehend ungültiger Eingaben. Visuell geprüft: [Grundriss mit Tisch](release-0.6.0/images/storey-table-layout.png), [Tischkoordinaten](release-0.6.0/images/storey-table-controls.png), [kompakter Grundriss](release-0.6.0/images/storey-table-layout-compact.png) und [kompakte Tischkoordinaten](release-0.6.0/images/storey-table-controls-compact.png).

Die transparente Projektion wurde **noch nicht in einer geladenen Clientwelt visuell geprüft**; der Clientlauf arbeitet ohne geladene Welt. Weltvorschau und Bauauftrag verwenden dieselbe getestete Transformation.

## Werkstattoberfläche, 30.10.2026

Gemeinsame UI-Überarbeitung für Form- und Gebäudeeditor, native KilaGraph-Knoten/-Menüs, Inspektoren, Modul-/Material-/Bauseiten, Druckerinventar, Bauteilekatalog, Wertedialoge und Kreativüberschriften. Java 21 und die vorhandenen Offline-Abhängigkeiten wurden verwendet; die Minecraft-Prozesse hatten höchstens 2 GiB Heap.

`./gradlew build runClient -PworkshopSmoke --offline --console=plain` erfolgreich: **123 JUnit-Tests ohne Fehler** sowie die neue Werkstattabnahme bestanden. Der echte Client prüft normale und kompakte Modul-, Material- und Bauansichten, 3D-Modulvorschau und Platten-/Aussparungsraster, Widgetgrenzen, eingepasste Raster-/Klickpositionen, Materialmenüs, Zahlenfelder und gesperrtes Speichern bei ungültigen Werten. Katalogsuche, Kategorie, leere Trefferlisten, Tastaturauswahl und Größenänderung verändern kein Dokument; die bestehende Katalogabnahme prüft weiterhin alle 20 Grundformen samt atomarem Undo/Redo.

`./gradlew runClient -PprinterSmoke --offline --console=plain` erfolgreich: native typisierte Graphen, vollständige Druckereinstellungen mit Undo/Redo, normale/kompakte Materialgruppen, tatsächlicher Eye-Hover mit eingefrorener GPU-Aufnahme, sieben Kartuschenplätze, echtes Blockmodell und Displaydarstellung geprüft. Der Speicher zeigt in beiden Größen alle 36 Inventarplätze, permanenten Rohmaterialvorrat, Slotzustände und Druckbibliothek.

`./gradlew runClient -PkilaGraphSmoke --offline --console=plain` ebenfalls erfolgreich. Der vollständige native Testlauf bestätigt typisierte Kabel, Rotationen, getrennte Materialeingänge, Katalogquellen, Raumzellen einschließlich der Rasterdarstellung, mehrfach verwendete Rauminstanzen und exakte Dokument-Rundläufe mit Undo/Redo.

Visuell geprüft: [Druckerspeicher](release-0.6.0/images/workshop-printer-storage.png), [kompakter Speicher mit Maus-Item](release-0.6.0/images/workshop-printer-storage-compact.png), [Formgraph](release-0.6.0/images/workshop-form-graph.png), [Raumraster](release-0.6.0/images/workshop-room-grid.png), [Katalog](release-0.6.0/images/workshop-catalog.png), [kompakte Katalogfilter](release-0.6.0/images/workshop-catalog-filtered-compact.png), [ungültige Werteingabe](release-0.6.0/images/workshop-value-validation.png), [kompaktes Modulraster](release-0.6.0/images/workshop-module-grid-compact.png) und [kompakte Bauübersicht](release-0.6.0/images/workshop-build-compact.png), [3D-Modul](release-0.6.0/images/workshop-module-3d.png) und [kompakte Plattenansicht](release-0.6.0/images/workshop-plates-compact.png).

Die Ressourcenprüfung und `git diff --check` sind erfolgreich. Die JAR und SHA-256-Datei unter `build/libs/copycat_architect-0.5.1.jar` wurden aktualisiert. Diese Clientläufe arbeiten ohne Weltverbindung; Server-GameTests wurden für die UI-Änderung nicht erneut ausgeführt. Die vorübergehende Ladefenster-Einstellung wurde nach den Clientläufen wiederhergestellt.


## Mausbedienung im Druckerinventar, 30.10.2026

Mit Java 21 und den vorhandenen Offline-Abhängigkeiten erfolgreich geprüft: **123 JUnit-Tests ohne Fehler**, **alle 41 erforderlichen Minecraft-GameTests bestanden** und vollständiger `printerSmoke`-Clientlauf erfolgreich. Die Tasks `build`, `runGameTestServer -PgraphWorldSmoke` und `runClient -PprinterSmoke` verwendeten temporär getrennte Build-, Gradle-Projektcache- und Spielverzeichnisse, damit parallele Projektläufe keine Klassen oder Startparameter überschreiben. Die Spielprozesse liefen mit höchstens 2 GiB Heap.

Neun neue Inventar-GameTests prüfen die echten Minecraft-Menütransaktionen und die vom Screen verwendeten Mausgesten: sofortige Aufnahme am Mauszeiger, Klickablage und direktes Ziehen, Kartuschentausch mit vollständigen Komponenten, Rechtsklick-Teilung und Einzelablage, Verteilung per Links-/Rechtsziehen, Umschalt-Klick, Kartuschenfilter, persistente Änderungen bei gehaltenem und aufgestelltem Drucker sowie die Sperre des geöffneten Handgeräts einschließlich Hotbar-/Nebenhandtausch. Schließen gibt den Cursor genau einmal zurück; bei vollem Inventar und ungültig gewordener Sitzung entsteht genau ein unverändertes Item als Drop.

Der echte Minecraft-Client stellt das native Menü mit 43 Slots, Spielerinventar und benannter Kartusche am Mauszeiger in normaler und kompakter Größe dar. Die Screenshots wurden visuell geprüft: [kompakte Ansicht mit Maus-Item](release-0.6.0/images/printer-storage-cursor-compact.png), [normale Ansicht](release-0.6.0/images/printer-storage-normal.png). Dieser Clientlauf arbeitet ohne Weltverbindung; Inventartransaktionen und Persistenz werden von den Server-GameTests geprüft. `python3 tools/verify_resources.py` und `git diff --check` sind ebenfalls erfolgreich. Die Mod-JAR und ihre SHA-256-Datei unter `build/libs/copycat_architect-0.5.1.jar` wurden aktualisiert.

## Raumraster und Stockwerksanordnung, 30.10.2026

`./gradlew build runClient -PkilaGraphSmoke --offline --console=plain` mit Java 21 erfolgreich: **123 JUnit-Tests ohne Fehler** und die vollständige native KilaGraph-Bedienprüfung bestanden. Neue Entwürfe verbinden orange Wandsegmente/Boden/Decke mit einem grünen Raum und diesen mit einem weißen Stockwerk aus Räumen. Gespeicherte direkt aus Wänden aufgebaute Stockwerke bleiben durch eine eigene Client-Testvorlage geprüft.

Die Fachtests prüfen angeklickte Raumblöcke einschließlich Aussparungen, dynamische Wandanschlüsse mit je einem Boden-/Deckeneingang, verlustfreie Rechteckkodierung über 16 Teilrechtecke hinaus, die Grenze von 64 Wandläufen, unveränderte und geteilte Wandzuordnungen, Bearbeitung vorhandener Grundrisse bis Koordinate 63, JSON-Rundläufe und unabhängige Versätze mehrfacher Rauminstanzen. Der Client bedient tatsächliche Rasterzellen und native Anschlüsse, prüft die neue Kontur mit sechs Wandläufen, schließt falsche Anschlussarten aus und verschiebt den zweiten Raum einer Stockwerksgrafik um einen Block. Rückgängig/Wiederholen stellt jeweils die vollständige Gebäudedefinition wieder her.

Bei 854 × 480 und 427 × 240 GUI-Pixeln sind die Zellen beim Öffnen des Inspektors sichtbar; im kleinen Fenster wird die Grundrissgrafik direkt eingepasst. Die fünf Screenshots wurden visuell kontrolliert: [Hierarchie](release-0.6.0/images/kilagraph-room-storey-hierarchy.png), [Raumraster](release-0.6.0/images/kilagraph-room-cell-grid.png), [Stockwerksgrafik](release-0.6.0/images/kilagraph-storey-room-layout.png), [kompaktes Raumraster](release-0.6.0/images/kilagraph-compact-room-cell-grid.png) und [kompakte Stockwerksgrafik](release-0.6.0/images/kilagraph-compact-storey-room-layout.png). Die temporäre Ladefenster-Einstellung wurde wiederhergestellt. Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert. Minecraft-Welttests wurden für diese Änderung nicht erneut ausgeführt.

## Kreativkategorie „Speziell“, 29.10.2026

`./gradlew build --offline --console=plain` mit Java 21 erfolgreich: **117 JUnit-Tests ohne Fehler**. Die Katalogtests prüfen die funktionalen Copycats einschließlich Türen, Rohren, Wellen, Redstone-Teilen, Leitern und Geisterblöcken sowie sechs Aeroworks-/CC-Aeroworks-Steuerungsvarianten; Grundformen und dekorative Copycats behalten ihre bisherigen Abschnitte.

`./gradlew runClient -PprinterSmoke --offline --console=plain` ebenfalls erfolgreich. Der echte Minecraft-Client prüft die 19 registrierten funktionalen Copycats im neuen Abschnitt, dessen deutsche und englische Übersetzung, die getrennten Katalogeinträge und **fünf Überschriftszeilen**. Aeroworks und CC-Aeroworks sind in diesem Clientlauf nicht installiert; ihre Zuordnung ist über die Fachtests geprüft. Das Glasrohr ist in Copycats+ eine Blockvariante ohne eigenes Item. `python3 tools/verify_resources.py` bestätigt JSON, Sprachschlüssel und ROM-Lua. Die vorübergehende Fensterkonfiguration wurde wiederhergestellt; Mod-JAR und SHA-256 unter `build/libs/copycat_architect-0.5.1.jar` sind aktualisiert.

## Panelgebäude mit bebaubarem Innenraum, 28.10.2026

Der konkrete Entwurf `PanelBuildingExample` ist erfolgreich geprüft. Er besteht aus **188 Copycat-Positionen** mit vier Panelwänden von **3/16 Dicke**, Schichtfenstern von **2/16 Dicke**, Leistenrahmen und senkrechten Eckleisten mit **2 × 2/16 Querschnitt**. Waagerechte Leisten schließen die oberen und unteren Fassadenkanten ab. Die Fenster liegen an der Blockkante, nicht als Scheibe in der Blockmitte. Hinter dem Glas verbleibt kein opakes Panel. Boden und Decke lassen **4 × 4 × 4 = 64 Innenzellen** vollständig frei.

Der abschließende Befehl `./gradlew build runGameTestServer runClient -PgraphWorldSmoke -PpanelBuildingSmoke --offline --console=plain` war erfolgreich: **115 JUnit-Tests ohne Fehler**, **alle 32 erforderlichen Minecraft-GameTests bestanden** und der vollständige native Panelhaus-Clienttest erfolgreich. Java 21 und die gepinnten Offline-Abhängigkeiten wurden verwendet. `earlyWindowControl` wurde für den Client vorübergehend deaktiviert und anschließend wiederhergestellt. `python3 tools/verify_resources.py` prüft die Ressourcen und Sprachen weiterhin erfolgreich.

Der neue Welttest baut das gesamte Haus im Überlebensmodus mit exakt **33.024 Rohmaterialeinheiten = 129 Zinkbarren**, 136 Eichenholzbrettern, 48 Steinziegeln, 16 Glas und 36 Tiefenschieferfliesen. Er prüft beide tatsächlichen Minecraft-Kollisionsabfragen an allen **770.048 Rasterpunkten** der 188 Blöcke, die Materialzuordnung und Quittung jeder BlockEntity, ihren NBT-Rundlauf sowie eine kostenfreie idempotente Wiederholung. Alle vier Panel-/Fensterrichtungen und sämtliche Eck- und Fassadenleisten behalten ihre genaue Stärke. Die 64 Innenzellen bleiben Luft. Ein Survival-Spieler steht danach im Innenraum und platziert ohne Schleichen eine echte Werkbank, einen Ofen und eine Truhe auf dem Copycat-Boden. Ofen und Truhe besitzen ihre normalen funktionalen BlockEntities; die drei Einbauten verändern keinen Teil der Gebäudehülle.

Dabei wurden zwei Produktlücken behoben: Raum und direkt aus Wänden aufgebautes Stockwerk bieten nun den optionalen typisierten Eingang **Eckleisten-Copycat** mit passender Drehung an allen konvexen Ecken. Ohne explizite Quelle vereinigt die automatische Eckfüllung die beiden kompatiblen Wandflächen. Außerdem reichen fertig verkleidete Copycats Blockitems zur gewöhnlichen Platzierung weiter, sodass sie normale Innenausbauten erlauben. Rohe Gruppen nehmen geeignete Materialien weiterhin als Verkleidung an. Die bisherigen Tests für Materialauftragen, Schraubenschlüssel, Drucker, Rückgaben, Kartuschen und Turtle bestehen weiterhin.

Die neue Clientprüfung lädt den tatsächlichen Grapheditor mit denselben Formen und Modulen wie der Welttest. Sie bestätigt den sichtbaren typisierten Eckanschluss und die exakte Planübereinstimmung in normalen und kompakten Graph-/Inspektoransichten. Die beiden Außenansichten und eine gesondert bezeichnete Schnittdarstellung werden mit echten Texturen und Minecraft-Blockrenderern erzeugt. Die Schnittansicht blendet Dach, Decke und zwei Vorderwände nur zum Betrachten aus; der geprüfte Bauplan bleibt unverändert.

Beispiel und Nachbauanleitung: [Panelgebäude](../examples/panel-building/README.md), vollständiges Referenzpaket [library.json](../examples/panel-building/library.json). Das geschlossene Prüfgebäude enthält keine Tür; freie Eingangszellen lassen sich im Wandmodul vorsehen. Einzelimporte vergeben neue IDs und erfordern danach die Auswahl der gespeicherten Formen/Module; das Referenzpaket ist kein Gesamtbibliotheksimport.

Visuell geprüft: [Außenansicht 35°](release-0.6.0/images/panel-building-exterior-35.png), [Außenansicht 215°](release-0.6.0/images/panel-building-exterior-215.png), [bebaubarer Innenraum mit Einbauten](release-0.6.0/images/panel-building-interior-furnished-cutaway.png), [Baugraph](release-0.6.0/images/panel-building-graph-normal.png), [Eckleisten-Inspektor](release-0.6.0/images/panel-building-corner-inspector-normal.png), [kompakter Graph](release-0.6.0/images/panel-building-graph-compact.png) und [kompakter Inspektor](release-0.6.0/images/panel-building-corner-inspector-compact.png).

Die aktualisierte Mod liegt unter `build/libs/copycat_architect-0.5.1.jar`; daneben befindet sich ihre SHA-256-Prüfsumme.

## Blockdrucker 0.5.0, 28.10.2026

Mit Eclipse Temurin Java 21 und den vorhandenen Offline-Abhängigkeiten geprüft:

| Prüfung | Ergebnis |
| --- | --- |
| `./gradlew build runGameTestServer -PgraphWorldSmoke --offline --console=plain` | Erfolgreich; **106 JUnit-Tests und alle 31 erforderlichen Minecraft-GameTests bestanden**. |
| `./gradlew build runClient -PprinterSmoke --offline --console=plain` | Abschließender Lauf erfolgreich, einschließlich GPU-Displayspiegelung und kompakter Layoutkorrekturen. |
| `./gradlew runClient -PkilaGraphSmoke --offline --console=plain` | Vollständige bestehende native Graph-Bedienprüfung erfolgreich. |
| `lua src/test/lua/architect_printer_test.lua` | Alle zehn Protokoll-, Aufräum-, Historien- und bisherigen Auftragsszenarien bestanden. |
| `python3 tools/verify_resources.py` | JSON, identische DE-/EN-Sprachschlüssel, Lua-Syntax und Übereinstimmung des ROM-Programms erfolgreich. |

Die Welttests verwenden tatsächliche Item-/Spielmodusinteraktionen für Druckerplatzierung und Kartuschenstapel. Sie prüfen sieben benannte Kartuschenplätze samt Itemkomponenten, Survival und Kreativ, Abbruch, erfolgreichen Druck, Materialmangel, Oberflächenverbrauch, gezielte Schraubenschlüsselentfernung und bezahlte Rückgaben. Abgewiesene Platzierungen und ein absichtlich fehlerwerfender Schutzmod-Hook erhalten Weltzustand und Inventare. Abgewiesenes Aufstellen oder Stapeln erzeugt keine zusätzlichen Drops. Entzogene Baurechte blockieren Änderungen am aufgestellten Gerät; die eigene gehaltene Speicherverwaltung bleibt erreichbar. Der Speicherwechsel synchronisiert den aktuellen Entwurf auch unmittelbar nach einer Vorschau. Benannte Historieneinträge behalten Materialien und Drehung, einschließlich Bibliotheks- und Item-NBT-Rundlauf; alte Bibliotheken ohne Druckeinstellungen bleiben lesbar.

Die Create-Prüfungen befüllen Kartuschen über die echte `GenericItemFilling`-API, prüfen Teilmengen und Entleerung sowie platzierte Einzel- und Viererstapel. Die 47 geladenen Zink-Recyclingrezepte werden gegen die Herstellungskosten der vorhandenen Copycats geprüft. Die Turtle-Prüfungen decken physische Geräte, sieben Plätze, Laden/Entnehmen, Besitzer und Sitzungen, Druck, Abbruch, volle Rückgabeinventare, Schutzabbruch sowie Historie und benannte Speicherung ab. Der bestehende Gebäudebau mit **603 tatsächlichen Turtle-Platzierungen**, Materialnachschub und Neustart besteht weiterhin; der ältere 1.115-Platzierungen-Test wird mit dem Graph-Schalter übersprungen.

Der Drucker-Clienttest läuft ohne geladene Welt mit tatsächlichen Minecraft-Ressourcen, KilaGraph-Widgets und Block-/BlockEntity-Renderern. Er bedient Gruppenansicht und Undo/Redo über echte Screen-Ereignisse und bestätigt die vollständige Wiederherstellung von Form, Materialien und Drehung. Er prüft normale und kompakte Graph-, Inspektor- und Speicheransichten, importierte Materialien außerhalb der Standardpalette, sieben sichtbare Plätze, gespeicherte Form-Icons sowie die vier Kreativkatalogabschnitte und ihre leeren Überschriftszeilen. Der Render-Hover wird über Minecraft-Mauskoordinaten ausgelöst. Die GPU-Aufnahme bleibt während des Augen-Hovers unverändert, läuft danach weiter und ist auf maximal 512 Pixel je Kante begrenzt. Die Modellansicht zeigt die echte unverzerrte UI-Textur auf dem Display und das transparente Druckergebnis im Rahmen.

Alle drei Ponder-Anleitungen sind registriert und werden mit ihren echten Schematic-Ressourcen und Storyboards komponiert: Drucker mit 30, Kartuschen mit 31 und Architektentisch mit 110 Instructions. **Die Ausführung und Darstellung dieser Ponder-Animationen in einer geladenen Clientwelt wurde in diesem weltlosen Lauf nicht geprüft.** Die lokale NeoForge-Einstellung `earlyWindowControl` wurde für die Clientläufe vorübergehend deaktiviert und danach wiederhergestellt.

Visuell geprüft: [Drucker mit gespiegelter UI](release-0.6.0/images/printer-frame-and-ui-display.png), [Innenraum und Vorschau](release-0.6.0/images/printer-frame-interior-preview.png), [Graph](release-0.6.0/images/printer-graph-normal.png), [kompakter Graph](release-0.6.0/images/printer-graph-compact.png), [Augen-Hover](release-0.6.0/images/printer-eye-world-peek.png), [Gruppen](release-0.6.0/images/printer-groups-normal.png), [kompakter Inspektor](release-0.6.0/images/printer-inspector-compact.png), [Speicherverwaltung](release-0.6.0/images/printer-storage-normal.png) und [kompakter Speicher](release-0.6.0/images/printer-storage-compact.png).

Die auslieferbare Datei ist `build/libs/copycat_architect-0.5.0.jar`; daneben liegt ihre SHA-256-Prüfsumme.

## Gruppen, Rotation, Gebäudeaufbau und Pistolenplatzierung, 28.10.2026

Mit Java 21 und vorhandenem Gradle-Cache geprüft:

- Abschließender `./gradlew build runClient -PkilaGraphSmoke --offline --console=plain`: erfolgreich; **100 JUnit-Tests ohne Fehler** und vollständige native Clientprüfung. Die aktualisierte Mod-JAR liegt unter `build/libs/copycat_architect-0.4.0.jar`; ihre SHA-256-Datei wurde neu erzeugt.
- Die Aufgabe `runGameTestServer -PgraphWorldSmoke` bestand **alle zwölf erforderlichen Minecraft-GameTests**, einschließlich des echten Turtle-Baus mit 603 Platzierungen, Neustart und eingefrorenen Vorlagen. Der ältere 1.115-Platzierungen-Turtle-Test wurde mit diesem Schalter übersprungen.
- `python3 tools/verify_resources.py`: JSON, DE-/EN-Sprachschlüssel und ROM-Lua erfolgreich geprüft.

Die Fachtests prüfen symmetrische Differenz mit überlappenden Körpern, verschiedene Eingangsgruppen, gemeinsame Gruppen, geschachtelte Materialvererbung und Rezeptkosten. Masken und Herkunft bleiben bei Rotationen erhalten. Schnitt und Differenz übernehmen eindeutig das Material ihres ersten Operanden; gespeicherte frühere Gruppierungs-Overrides bleiben lesbar. Die Gebäudeprüfungen decken Copycat-Sequenzen, Stockwerke direkt aus Wänden/Boden/Decke, Dachdeckung und Giebelsegmente, Luftgiebel, Attika, JSON-Rundläufe und das Entfernen zusätzlicher Copycat-Eingänge ab.

Die native Prüfung bedient tatsächliche KilaGraph-Anschlüsse, Kabel, Menüs und Inspektorfelder. Sie bestätigt semantische Copycat-/Rotation-Typen und die Ablehnung falscher Kabeltypen, einen eigenen Rotationsknoten samt Achsen-/Winkelwahl, Eingangsgruppen und Gleichheitswarnung sowohl für Vereinigung als auch symmetrische Differenz, passende Materialübernahme beim Gruppieren sowie vollständiges Rückgängig/Wiederholen. Eine eigene Gebäudevorlage prüft die expliziten Copycat-/Wand-/Stockwerk-/Dachanschlüsse, Segmenterstellung, echte Kabelentfernung, dynamische Eingänge und Stockwerksmaße. Alle neuen Ansichten werden auch kompakt bei 427 × 240 GUI-Pixeln geprüft. Die Testschritte warten nach tatsächlichen Aktionen auf zwei Renderframes, damit native Layout- und Popupbegrenzungen abgeschlossen sind.

Visuell kontrollierte Prüfbilder: [Rotationsknoten](release-0.6.0/images/kilagraph-disc-axis-controls.png), [kompakte Rotation](release-0.6.0/images/kilagraph-compact-disc-axis-controls.png), [Gruppenwarnung](release-0.6.0/images/kilagraph-union-uniform-warning.png), [symmetrische Differenz mit getrennten Eingangsgruppen](release-0.6.0/images/kilagraph-symmetric-difference-separated-input-groups.png), [kompakte XOR-Warnung](release-0.6.0/images/kilagraph-compact-symmetric-difference-uniform-warning.png), [expliziter Gebäudeaufbau](release-0.6.0/images/kilagraph-assembly-building-native.png), [Stockwerk aus Wänden, Boden und Decke](release-0.6.0/images/kilagraph-assembly-storey-controls.png), [Dachanschlüsse](release-0.6.0/images/kilagraph-assembly-roof-controls.png), [kompakte Stockwerksanschlüsse](release-0.6.0/images/kilagraph-compact-assembly-storey-connections.png), [kompakte Dacheigenschaften](release-0.6.0/images/kilagraph-compact-assembly-roof-controls.png) und [begrenztes Untermenü am Fensterrand](release-0.6.0/images/kilagraph-compact-context-last-source.png).

Der neue Pistolen-GameTest verwendet echte Raycasts und `ServerPlayerGameMode.useItemOn` für alle sechs Seiten eines vollständigen und eines halben Komposit-Copycats. Er prüft den freien Nachbarplatz, den Erhalt des angeklickten Blocks, Survival-Verbrauch und idempotente Wiederholung. Die Kompositblöcke verwenden dynamische BlockState-Formen; der Scheiben-Welttest vergleicht beide Minecraft-Kollisionsabfragen an sämtlichen 4.096 Rasterpunkten. Die NeoForge-Entwicklungseinstellung `earlyWindowControl` wurde für den Clientlauf vorübergehend deaktiviert und anschließend wiederhergestellt.

## Erweiterter Grundformkatalog, 27.10.2026

Der Katalog umfasst jetzt **18 Grundformen**. Neu sind Balken, Byte, Create-Leiste, vertikale Leiste, halbes Panel, mittiges Flachpanel, Byte-Panel sowie Halb-Schicht, vertikale Halb-Schicht und gestapelte Halb-Schicht. Das Inventar wächst passend auf zwei Reihen. Die Extrusionsausbeuten wurden aus den Stonecutting-Rezepten der gepinnten Mod-JARs übernommen und im ausgelieferten Datapack ergänzt.

`./gradlew build runGameTestServer -PgraphWorldSmoke --offline --console=plain` war mit Java 21 erfolgreich: **73 JUnit-Tests ohne Fehler**, **alle zehn erforderlichen Minecraft-GameTests bestanden**, einschließlich des Gebäudegraph-Turtle-Baus mit 603 Platzierungen. Der Quellenvergleich prüft jetzt jede zulässige Größe, Boden-/Deckenmontage, alle vier horizontalen Drehungen und bei neigbaren Quellen alle vier Neigungen. Damit wurden **872 Quellzustände an jeweils 4.096 Rasterpunkten** mit der tatsächlichen Minecraft-Form verglichen. Die neuen Fachtests prüfen außerdem die mittige Balken-/Flachpanel-Geometrie, acht Bytes als Vollblock, vier Byte-Panels als Panel, korrekte Rezeptkosten, Halb-Schichtgrößen und vollständige JSON-Rundläufe.

Der abschließende `./gradlew build runClient -PkilaGraphSmoke --offline --console=plain` war ebenfalls erfolgreich. Die native Bedienprüfung wählt alle 18 Einträge über echte Inventarslots, prüft die quellabhängige Rücksetzung, unveränderte Fremdfelder und Rückgängig/Wiederholen für jeden Eintrag. Tastaturnavigation in die zweite Reihe, Auswahl des letzten Eintrags und dessen Fokus nach erneutem Öffnen und Resize sind geprüft. Das Rechtsklickmenü ist in vier Gruppen mit maximal zehn Einträgen gegliedert. Im kompakten Fenster wurden die letzte Quelle, ihre Position auf dem Bildschirm, die korrekte Einfügeposition und Rückgängig geprüft. Die Position des Hauptmenüs wird an den GUI-Rand begrenzt; die Menüs werden über der Fußzeile gezeichnet.

Visuell geprüft: [18 Grundformen im Inventar](release-0.6.0/images/copycat-inventory.png), [kompakte Inventaransicht](release-0.6.0/images/copycat-inventory-compact.png) und [kompaktes Rechtsklickmenü bis zur letzten Quelle](release-0.6.0/images/copycat-context-compact.png). Die lokale NeoForge-Einstellung `earlyWindowControl` wurde für den Clientlauf vorübergehend deaktiviert und anschließend wiederhergestellt. `python3 tools/verify_resources.py` ist ebenfalls erfolgreich.

## Grundcopycat-Inventarauswahl, 27.10.2026

Mit Eclipse Temurin Java 21.0.7 und dem vorhandenen Gradle-Cache geprüft:

- `./gradlew build --offline --console=plain`: erfolgreich, **67 JUnit-Tests ohne Fehler**; aktualisierte Mod-JAR erzeugt.
- `./gradlew runClient -PkilaGraphSmoke --offline --console=plain`: vollständige native KilaGraph-Abnahme erfolgreich, einschließlich der neuen Inventarauswahl.
- `python3 tools/verify_resources.py`: JSON, DE-/EN-Sprachschlüssel und ROM-Lua erfolgreich geprüft.

Die neue Clientprüfung klickt den echten Button am KilaGraph-Eingangsknoten und den Grundform-Button im Bedienfeld. Alle acht unterstützten Formen besitzen registrierte Item-Symbole und erreichbare Inventarslots. Esc und die erneute Auswahl derselben Form erhalten Dokument und Undo-/Redo-Verfügbarkeit exakt. Der Wechsel setzt nur die bisher schon quellspezifischen Werte Größe, Versatz und Neigung zurück; Drehung, Montage, Materialgruppe, Verbindungen und andere Dokumentfelder bleiben erhalten. Ein einzelnes Rückgängig/Wiederholen stellt den vollständigen Zustand wieder her. Home, Pfeiltasten, Eingabe und Resize wurden ebenfalls über echte Screen-Ereignisse geprüft.

Visuell kontrolliert: [Auswahlbuttons am Eingangsknoten](release-0.6.0/images/copycat-source-nodes.png), [Inventar mit Minecraft-Rahmen](release-0.6.0/images/copycat-inventory.png) und [kompakte Inventaransicht bei 427 × 240 GUI-Pixeln](release-0.6.0/images/copycat-inventory-compact.png). Die Ansichten zeigen die tatsächlichen Copycat-Itemmodelle und die markierte aktuelle Grundform.

Ein zusätzlicher Clientstart blieb vor dem Spielstart bei NeoForges Ladefenster-Übergabe hängen. Der abschließende erfolgreiche Lauf verwendete vorübergehend `earlyWindowControl=false` in der lokalen Entwicklungskonfiguration; die Einstellung wurde anschließend wiederhergestellt. Welt-/Turtle-Tests wurden für diese reine Clientänderung nicht erneut ausgeführt.

## KilaGraph-Überarbeitung, 27.10.2026

Abschließend mit Eclipse Temurin Java 21.0.7, Gradle 9.8.0, KilaGraph 21.1.0.16 und LDLib2 2.2.44 geprüft:

| Befehl | Ergebnis |
| --- | --- |
| `./gradlew build runClient -PkilaGraphSmoke --offline --console=plain` | Erfolgreich; aktuelle Mod-JAR, **67 JUnit-Tests ohne Fehler** und vollständige native KilaGraph-Abnahme. |
| `./gradlew runGameTestServer -PgraphWorldSmoke --console=plain` | Erfolgreicher dedizierter Java-21-Serverstart; **alle 10 erforderlichen GameTests bestanden**. |
| `python3 tools/verify_resources.py` | JSON, deutsche/englische Sprachschlüssel und ROM-Lua erfolgreich geprüft. |

Der Client verwendet den tatsächlichen KilaGraph-Canvas. Die Abnahme bedient native Knoten, Anschlüsse, Kabel und Menüs über Maus-/Tastaturereignisse und kontrolliert:

- Exakte JSON-Gleichheit beim wiederholten Wechsel Graph/Bedienfeld, einschließlich unverbundener Entwurfszweige, Versionen, Namen, Materialien, Koordinaten sowie Undo-/Redo-Verfügbarkeit.
- Unveränderte Geometrie und Kosten beim Verschieben von Formknoten; identische Gebäudepläne beim Verschieben von Gebäudeknoten. Pan, Zoom und Einpassen ändern ausschließlich den Ansichtsstand.
- Echte Kabelverbindung, geschützte Ergebnisknoten, Zyklus- und Typablehnung sowie Auswahl/Entfernen eines Kabels mit gemeinsamem Rückgängig/Wiederholen.
- Benannte Neuanlage einer Gruppe direkt aus der Gruppenauswahl eines Knotens, richtige Zuordnung und vollständiges Rückgängig mit einem Schritt.
- Direkte Zahlen-/Texteingabe, ungültige Werte, Überlauf, Resize während einer Eingabe, Abbruch ohne Änderungen, Übernahme mit Eingabe und Korrektur nach abgewiesener Bearbeitung.
- Ältere graphlose Formen sowie klassische Gebäude bleiben ohne automatische Konvertierung erhalten. Beim klassischen Gebäude bleiben auch Aussparungen, Wandmuster, Leisten und der komplette berechnete Bauplan exakt gleich.
- Kompakte Ansicht bei 427 × 240 GUI-Pixeln: beide Darstellungen, scrollbare Eigenschaften, unveränderte Dokumente und lesbare, sich nicht überlagernde Übersichtsbeschriftungen.
- Rechtsklick am unteren/rechten Graphenrand: echtes Untermenü außerhalb der Zeichenfläche, Auswahl über dem Eigenschaftenbereich, Einfügen an den korrekten transformierten Graphkoordinaten und Rückgängig. Ein Außenklick schließt das Menü, ohne Eigenschaften darunter zu bedienen.
- Die vorhandene Mesh-Prüfung kontrolliert eindeutige Außenflächen, Materialprioritäten und die opake Grenzfläche hinter Glas erneut.

Die **13 nativen Prüfbilder wurden visuell kontrolliert**: [Formgraph](release-0.6.0/images/kilagraph-shape.png), [Form-Bedienfeld](release-0.6.0/images/kilagraph-shape-controls.png), [Gruppentextfelder](release-0.6.0/images/kilagraph-groups.png), [Gebäudegraph](release-0.6.0/images/kilagraph-building.png), [Gebäude-Bedienfeld](release-0.6.0/images/kilagraph-building-controls.png), [klassische Gebäudeübersicht](release-0.6.0/images/kilagraph-classic-building.png), [klassische Bedienung](release-0.6.0/images/kilagraph-classic-controls.png), [ältere Form](release-0.6.0/images/kilagraph-legacy-shape.png), [kompakter Formgraph](release-0.6.0/images/kilagraph-compact-shape.png), [kompaktes Form-Bedienfeld](release-0.6.0/images/kilagraph-compact-controls.png), [kompakter Gebäudegraph](release-0.6.0/images/kilagraph-compact-building.png), [kompaktes Gebäude-Bedienfeld](release-0.6.0/images/kilagraph-compact-building-controls.png) und [Rand-Untermenü](release-0.6.0/images/kilagraph-context-menu.png).

Der Serverlauf führte den echten Gebäudegraph-Turtle-Bau mit **603 Platzierungen** und **54 Teppichbelägen** aus, einschließlich Unter-/Erd-/Obergeschoss, je zwei Räumen, eingefrorenen Vorlagen, Computerneustart, Nachversorgung und abschließendem Welt-/Depotvergleich. `-PgraphWorldSmoke` überspringt ausdrücklich den älteren 1.115-Platzierungen-Bau; dieser wurde für die KilaGraph-Überarbeitung **nicht erneut ausgeführt**. Der dedizierte Entwicklungsserver startete in diesem Lauf mit den beiden zusätzlichen Bibliotheken; die Modmetadaten verlangen sie ausschließlich auf dem Client.

Die älteren Client-Schalter `-PclientSmoke`, `-PgraphSmoke` und `-PbuildingGraphSmoke` führen jetzt dieselbe aktuelle KilaGraph-Abnahme aus. Die folgenden Abschnitte dokumentieren vorherige Abnahmen der früheren Oberfläche.

Datum: 26.10.2026. Plattform: Linux, Eclipse Temurin Java 21.0.7, Gradle 8.13. Exakte Modkombination: [DEPENDENCIES.md](DEPENDENCIES.md).

## Befehle und Ergebnisse

| Befehl | Ergebnis |
| --- | --- |
| `./gradlew build` | Erfolgreich; Mod-JAR und 54 bestandene JUnit-Fachtests. |
| `./gradlew runGameTestServer` (auch zusammen mit `build` ausgeführt) | Echter dedizierter NeoForge-Testserver mit sämtlichen Abhängigkeiten; alle 10 erforderlichen GameTests bestanden. |
| `./gradlew runClient -PclientSmoke` | Erfolgreicher Minecraft-Clientstart; beide Editoren und ihre Teilansichten tatsächlich gerendert, Screenshots erzeugt und visuell geprüft. |
| `./gradlew build runClient -PbuildingGraphSmoke` | Gebäudegraph nativ gerendert; Rechteckgriffe, L-Raum, Raumpositionierung, belegte Raumgrenzen und dynamische Ober-/Untergeschosse geprüft. |
| `./gradlew build runClient -PgraphSmoke` | Abschließender Build und gezielte native Graph-Bedienprüfung. |
| `python3 tools/verify_resources.py` | JSON-Ressourcen, identische Sprachschlüssel und identisches ROM-/Quell-Lua geprüft. |
| `lua tools/test_turtle.lua lua/architect.lua` | Das ausgelieferte Bauprogramm hält bei fehlendem Treibstoff und unerwartetem Hindernis mit Rückmeldung an. |
| `luac -p lua/architect.lua` | Lua-Syntax gültig. |

Die Befehle wurden mit Java 21 über `JAVA_HOME` ausgeführt. Das standardmäßig vorhandene System-Java 25 wurde nicht als Minecraft-Ziel verwendet.

## Fachtests

Die 54 Tests prüfen unter anderem:

- L-Innenecke aus zwei Leisten, Drehungen, Boden-/Deckenmontage und Entfernen eines Schenkels.
- Identische und enthaltene Teile; keine kostenlos erhaltenen Restteile.
- Segmentmuster, Zwischenstücke, W=11/W=12, n=0/n=1, Folgen, feste Abschlüsse und variable Zwischenräume.
- Eindeutige Ecken, Öffnungen, Konflikte und alle vier Wandrichtungen.
- Geteilte Geschossplatten, Flachdach und Satteldach in beiden Richtungen, gerade/ungerade Breiten und L-Grundriss.
- Deterministische Beispieldaten mit 1.115 Platzierungen.
- Materialgruppen als disjunkte Voxelpartition, Prioritätswechsel, deckungsgleiche Gruppen und normalisierte verdeckte Teile.
- Zwei Slab-Materialien, konkrete Materialbedarfe und gleiche Weltanforderungen trotz umbenannter lokaler Gruppen-IDs.
- Gruppenrollen durch den gemeinsamen Gebäudegenerator bis zur Materialliste.
- Zehn grundlegende Graphfälle: exakte Boolesche Geometrie, dynamische Eingänge, geschachtelte Differenzen, geteilte Quellen, Materialvererbung, Maskendrehung, JSON-Rundlauf, unvertraute Ergebnisdaten, Zyklen/ungültige Ports und Migration alter Formschemata.
- Fünf weitere Materialfälle: alle drei Operationen mit Gruppiert/Separiert, verschachtelte Neuzuweisung, ein gemeinsames Quellrezept über Materialzweige, unveränderte Kosten und Geometrie bei Vorschaufiltern sowie Persistenz und alte Graphen.
- 16 Gebäudegraphfälle: verschachtelte feste Muster, Rechteckvereinigungen und Wandläufe, adaptive Luft, belegte Raumgrenzen, tragfähige Teppichschichten, Raumversätze, gemeinsame Platten, Ober-/Untergeschosse, temporäre Anschlüsse, Typ-/Zyklusablehnung, allgemeines orthogonales Satteldach, persistierte Graphen und ausgelieferte Beispiele.

## Zehn Tests in einer echten Minecraft-Welt

1. Survivalplatzierung einer L-Form: exakt 32 Rohmaterialeinheiten, ein Oberflächenblock, wiederholter Auftrag ohne erneute Abbuchung, BlockEntity-NBT-Rundlauf.
2. Abhängigkeiten und Persistenz: alle angebotenen Quell-IDs vorhanden, Upgrade registriert, Bibliothek und begonnener Auftrag durch tatsächlich komprimiertes und wieder gelesenes NBT gespeichert. Größere Aufträge verwenden Bytefelder und überschreiten nicht mehr das NBT-Stringlimit.
3. Vollständiger Turtle-Bau: **1.115 Positionen**, zwei Geschosse, L-Grundriss, Fensteröffnungen, Leisten, getrennte Boden-/Deckenplatten und Satteldach. Die Platten werden durch einen Graphen mit **Differenz, Schnitt und Vereinigung** erzeugt, einschließlich explizit gruppierter Verarbeitungsknoten, und verwenden zwei Materialgruppen mit **Stein und Eichenbrettern**. Eine reale CC:Tweaked-Turtle führt das ausgelieferte Lua-Programm aus, fährt benachbarte Arbeitspositionen an, lädt aus der Depotkiste nach und kehrt am Ende zum Depot zurück. Der Computer wird während des Baus neu gestartet; zusätzlich wird pausiert und nach erneuter Baustellenprüfung fortgesetzt. Abschließend werden **alle Weltpositionen und alle Freiraumanforderungen** mit dem eingefrorenen Plan verglichen.
4. Fehlerfälle: fehlendes Material, blockierte Position und fehlender Platz für Rohstoffreste ändern weder Inventar noch Welt.
5. Geometrieadapter: die tatsächlichen Blockstates der gepinnten Create-/Copycats+-Version werden gegen das 16³-Fachmodell verglichen, einschließlich Drehung, Montagehälfte und Größenparametern.
6. Mehrmaterialverbrauch: fehlende zweite Materialart verhindert jede Abbuchung; erfolgreiche Platzierung verbraucht genau 288 Einheiten sowie je einen Stein- und Holzblock. Wiederholung ist kostenlos und idempotent. Gruppen, Priorität, Palette und Quittung überstehen NBT. Abbau gibt genau die bezahlten Ressourcen zurück; ein anderer Vordergrund wird nicht als identischer Zielzustand akzeptiert.

7. Graphplatzierung: Ein Vollblock erhält einen echten Ausschnitt durch Differenz mit einer vertikalen Leiste. Nach 90°-Drehung werden nur die verbleibende Quelle und eine Oberfläche berechnet; die wieder geladene BlockEntity besitzt an allen 4.096 Rasterpunkten die erwartete Kollisionsform. Wiederholung ist idempotent und die Quittung enthält keine Cutter-Rückgabe.

8. Knotengruppierung: Eine Vollblockquelle verzweigt in obere Differenz und unteren Schnitt. Der Schnitt erhält über `GROUPED` die Holzgruppe; die Vereinigung bleibt `SEPARATED`. Es werden exakt 256 Quelleneinheiten und zwei Oberflächen bezahlt. Vorschauauswahl „Holz“ beeinflusst die vollständige Platzierung nicht. Materialpartition, gemeinsame Rezeptreferenz und Quittung überstehen NBT; die Wiederholung belastet kein Material und beim Abbau werden genau die bezahlten Ressourcen erstattet.

9. Native Teppichschichten: Ein Teppich ohne Tragschicht wird vor Weltänderung oder Abbuchung abgewiesen. Nach Platzierung der tragenden Copycat-Schicht entsteht ein echter Vanilla-Teppich; ein Teppichitem und ausschließlich die Rohstoffe der Tragform werden verbraucht. Wiederholung ist idempotent.
10. Vollständiger **Gebäudegraph-Turtle-Bau mit 603 Positionen**: verschachteltes festes Wandmuster A + BC, Untergeschoss/Erdgeschoss/Obergeschoss mit je zwei Rauminstanzen, gemeinsam benutzte Platten, **54 echte Teppichbeläge**, eigene Decken und separates Dach. Der gesamte Graph und seine Geschossschlüssel überstehen den SavedData-Rundlauf. Während des Baus werden die Formvorlagen im Test entfernt und der Computer neu gestartet; der eingefrorene Auftrag baut dennoch fertig. Alle Ziel- und Freiraumpositionen werden mit der Welt verglichen; die Turtle kehrt zum Depot zurück.

Der abschließende reguläre Serverlauf mit allen Tests meldete: `All 10 required tests passed`. Auch alle 54 JUnit-Tests sind erfolgreich.

## Client- und Renderprüfung

Der automatische Testclient öffnet Formeditor, Modulraster, Modul-3D-Vorschau, Wandmuster, Gebäudedarstellung und Auftragsansicht. Opaque Oberfläche, gerichtete Holztextur und Glas werden dargestellt. Eine zusätzliche Mesh-Prüfung kontrolliert beim überlagerten Vollblock:

- genau 1.536 äußere 1/16-Rasterflächen, jede genau einmal;
- Material der oberen Fläche entspricht der vorderen Gruppe;
- beim Prioritätswechsel gehört die gesamte Oberfläche der anderen Gruppe;
- hinter einer Glasschicht existiert genau die benötigte opake Grenzfläche, ohne deckungsgleiche Glas-Rückfläche.

Der native Nodegraph wurde ebenfalls tatsächlich im Minecraft-Client gerendert. Die Bedienprüfung erweitert eine Vereinigung auf drei Eingänge, verbindet den neuen Anschluss über echte Screen-Mausereignisse, verschiebt einen Knoten und prüft Rückgängig/Wiederholen, Hinzufügen/Löschen, den Wechsel zur Teilebearbeitung, Zyklusablehnung, Paketbegrenzung, Zoom sowie den JSON-Rundlauf. Ein fehlender Anschluss erzeugt eine konkrete Meldung im Ergebnisknoten. Die Ergebnisvorschau zeigt die vereinigte ausgeschnittene Stein-/Holzgeometrie. Seit 0.3.0 betätigt die native Prüfung außerdem den Gruppiert-/Separiert-Schalter, wählt die gemeinsame Zielgruppe aus dem Dropdown und schaltet den Ergebnisfilter über beide Materialgruppen wieder auf alles. Dabei werden sichtbare Voxelpartition und unveränderte Gesamtkosten kontrolliert. Der finale gezielte Clientlauf wurde mit `./gradlew runClient -PgraphSmoke` ausgeführt und erfolgreich beendet.

Prüfbilder: [Nodegraph](release-0.6.0/images/nodegraph.png), [Dropdown](release-0.6.0/images/nodegraph-dropdown.png), [einzelne Gruppe](release-0.6.0/images/nodegraph-group.png), [unverbundener Eingang](release-0.6.0/images/nodegraph-error.png), [Gebäude](release-0.6.0/images/building.png), [Materialgruppen](release-0.6.0/images/material-groups.png), [geänderter Vordergrund](release-0.6.0/images/material-groups-background.png), [Glasgruppe](release-0.6.0/images/material-groups-glass.png).

Die native Gebäudegraph-Abnahme arbeitet ebenfalls über echte Screen-Mausereignisse: Rechteck an der Ecke vergrößern, Wandverbindungen erhalten, Rückgängig/Wiederholen, Stockwerk an Ober-/Untergeschoss anschließen und unbenutzte Zusatzanschlüsse ausblenden, zweite Rauminstanz automatisch platzieren und per Ziehen verschieben. Eine zusätzliche Runde fügt ein zweites Raumrechteck hinzu, verschiebt es zu einem L und verbindet passende adaptive Luftwände. JSON-Rundlauf und derselbe Generator werden geprüft. Screenshots wurden im Minecraft-Client aufgenommen und visuell kontrolliert: [Graph](release-0.6.0/images/building-graph.png), [mehrere Geschosse](release-0.6.0/images/building-graph-levels.png), [temporäre Anschlüsse](release-0.6.0/images/building-graph-level-sockets.png), [L-Raum und Raumversätze](release-0.6.0/images/building-graph-room-union.png).

Für die Entwicklung existiert `-PgraphWorldSmoke`: Hierbei wird der bisherige 1.115-Positionen-Test ausgelassen und der neue Graphbau gezielt geprüft. Der oben genannte abschließende `runGameTestServer`-Lauf verwendete diese Option **nicht**; beide vollständigen Bauten liefen.

## Grenzen der Abnahme

Kein manuelles Mehrspieler-Langzeitspiel und keine Prüfung mit zusätzlichen Schutzmods. Ein ordentlicher Welt-Speicher-/Laderundlauf ist getestet; ein Prozessabbruch mitten in unabhängigen Minecraft-Chunk-/Inventarschreibvorgängen ist kein getestetes Transaktionsverfahren. Create-/Copycats-Connected-Textures zwischen Kompositblöcken sind nicht implementiert. Die angebotene Materialpalette enthält zehn Vanilla-Vollblöcke; klassische Wände sind eine Zelle dick, räumliche Graphsegmente besitzen eine eigene Tiefe; klassische L-Dächer auf die dokumentierte Zwei-Flügel-Anordnung begrenzt. Der neue Gebäudegraph verwendet die beschriebenen endlichen orthogonalen Vereinigungen. Untergeschosse werden relativ zur tiefsten Konstruktion gestapelt; automatischer Erdaushub ist nicht implementiert. Diese Beschränkungen werden in [IMPLEMENTATION.md](IMPLEMENTATION.md) beschrieben und nicht als bestandene weitergehende Tests ausgegeben.


## UI-Überarbeitung, 27.10.2026

Mit Java 21 und dem vorhandenen Gradle-Cache wurden `./gradlew build --offline`, `./gradlew runClient -PgraphSmoke --offline`, `./gradlew runClient -PbuildingGraphSmoke --offline` und `python3 tools/verify_resources.py` erfolgreich ausgeführt. Alle 54 Fachtests sind erfolgreich. Die nativen Prüfungen arbeiten mit Screen-Maus- und Tastaturereignissen und kontrollieren zusätzlich:

- Rechtsklickmenü, Einfügen an den korrekten Graphkoordinaten nach Zoom/Verschieben, Rechtsziehen ohne Einfügen, Esc und Außenklick.
- Direkte Dropdown-Auswahl für Eigenschaften und Verbindungen; passende Quellen, Materialmodus und Ergebnisfilter.
- Exakte Gleichheit des Graphen vor/nach dem Ansichtswechsel sowie unveränderte Layoutkoordinaten beim Bearbeiten über das Bedienfeld; ansichtsübergreifendes Rückgängig.
- Oberflächen- und Gruppenwahl in der gemeinsamen Fußleiste ohne Änderung von Graph oder Geometrie; Tastaturbedienung und modale Menüs.
- Kompakte Ansicht bei 427 × 240 GUI-Pixeln: Kopf und Fußleiste bleiben sichtbar, der Inhalt scrollt ohne Überlappung oder Entwurfsänderung. Große Vorschauen sind bei dieser Höhe abschnittsweise sichtbar.

Beide gezielten Clientläufe endeten erfolgreich. Aktuelle native Prüfbilder wurden visuell kontrolliert: [Formgraph](release-0.6.0/images/nodegraph-ui.png), [Form-Bedienfeld mit Ergebnisvorschau](release-0.6.0/images/nodegraph-controls.png), [Einfügen per Rechtsklick](release-0.6.0/images/nodegraph-context.png), [Gebäude-Bedienfeld mit Ergebnisvorschau](release-0.6.0/images/building-graph-controls.png), [Gebäude-Knotenkatalog](release-0.6.0/images/building-graph-context.png). Die früher dokumentierten Welt- und Turtle-Prüfungen wurden für diese UI-Änderung nicht erneut ausgeführt.

### Korrektur der Weltvorschau beim Augen-Hover (2026-11-01)

`./gradlew build --offline --console=plain` und der native `printerSmoke`-Clientlauf sind erfolgreich; 170 JUnit-Tests ohne Fehler. Die Renderabnahme zeichnet einen einfarbigen Prüfhintergrund und vergleicht sämtliche Framebufferpixel außerhalb des Auges. Geprüft sind Graph mit Mehrfachauswahl-Inspektor, kompakter Drucken-Tab mit echtem Menü und Kartuscheninventar sowie die Druckhistorie. Alle drei Ansichten lassen während des Hovers den Hintergrund vollständig frei. Das Auge bleibt sichtbar, das Druckerdisplay behält seine letzte Aufnahme und die Oberfläche samt GPU-Aufnahme kehrt beim Verlassen zurück. Die Prüfung läuft ohne geladene Welt; der grüne Hintergrund im [Hover-Screenshot](release-0.6.0/images/printer-eye-world-peek.png) ist die Prüffläche.

### Untermenüs im Kontextmenü (2026-11-01)

Der Drucker-Clienttest bewegt den Mauszeiger jetzt über den echten Screen und Minecraft-Mauscallback auf „Knoten hinzufügen“ und die Kategorie „Grundformen“. Vor der Korrektur scheitert der Lauf bei Tick 77, weil das Kategorie-Untermenü nicht existiert (`build/printer-context-hover-before-fix.log`). `EditorScreen.mouseMoved` leitet Bewegungen nun an seine Widgets weiter. Damit öffnet LDLib beide Untermenüebenen; ein Klick auf die Quelle fügt den Knoten an der ursprünglichen Kontextposition ein, Rückgängig stellt den vorherigen Entwurf wieder her. Der [kompakte Untermenü-Screenshot](release-0.6.0/images/printer-add-node-submenu-compact.png) zeigt die geöffneten Kategorien und Quellen. Gehaltene Hover-Testeingaben werden vor jedem Renderframe erneut eingespeist, damit native Cursorcallbacks zwischen Ticks die Weltvorschau-Abnahme nicht unterbrechen.

Abschluss: `printerSmoke` und `./gradlew build --offline --console=plain` erfolgreich; 170 JUnit-Tests ohne Fehler, Ressourcenprüfung und `git diff --check` ohne Befund.


### Räumliche Segmente, Flächengebiete und Stockwerkvorschau (2026-11-02)

`./gradlew build --offline --console=plain` ist erfolgreich: **236 JUnit-Tests**, keine Fehler oder übersprungenen Tests. Neue Fachprüfungen decken disjunkte 3D-Randbereiche, zusammenfallende Anschlüsse und deren Wiedererweiterung, das Löschen verdeckter Quellen, Vierteldrehungen und Verschiebungen, überlagerte Copycats mit getrennten Materialien/Quittungen, Luftlücken über mehrere Tiefenschichten, tiefe Wandecken und mehrschichtige Platten ab. Geprüft sind außerdem allgemeine Flächengebiete, Raumregeln, geteilte Höhen und Geometrien, leere Quelldrafts sowie atomare Neuverbindungen ohne Eingriff in unabhängige Entwürfe.

Drei tatsächliche Clientprüfungen sind bestanden, jeweils mit normaler und kompakter Oberfläche:

- `buildingPreviewSmoke`: lokales 3×4×3-Segment mit 27 eindeutigen Anschlüssen und verschiedenen Flächen-/Eck-Copycats; Stockwerke mit zwei Rauminstanzen, direkter 3D-Zugang, 2D/3D-Wechsel, Kamera und Rückkehr zu Grundriss/Eigenschaften ohne Dokument- oder Historyänderung.
- `buildingPanelSmoke`: Luft als Copycat-Quelle, sichtbare Materialauswahl und gemeinsame Palette mit Rückgängig/Wiederholen.
- `explicitAssemblySmoke`: vorhandene Koordinaten-/Raster- und Baukettenbedienung sowie die neue Tiefeneingabe; ungültige Werte 0/129 werden verworfen, Tiefe 3 erzeugt die richtigen Dimensionen und 27 Anschlüsse, vollständiges JSON-Undo/Redo.

Die 3D-Segment- und Stockwerkbilder wurden visuell geprüft. Logs liegen unter `build/storey-preview-client-validation.log`, `build/building-source-panel-client-validation.log` und `build/segment-geometry-client-validation.log`. Die vorübergehende Client-Starteinstellung wurde wiederhergestellt. Ressourcen-/Sprachprüfung und `git diff --check` sind sauber. Die früher dokumentierten Welt-/Turtle-Läufe wurden für diese Änderungen nicht erneut ausgeführt.


## Gebäudegraph: Segment und Copycat rotieren (2026-11-06)

`BuildingRotationTest` prüft alle X/Y/Z-Achsen und Vierteldrehungen, inverse Drehungen, asymmetrische Segmentabmessungen, verkettete Spiegelungen, Materialerhalt, Kosten, feste typisierte Eingänge, unvollständige Entwürfe, Zyklen, JSON-Rundläufe und synchronisierte Gruppeninstanzen. Zusammen mit `BuildingGraphTest`, `SegmentMirrorGraphTest`, `CopycatMirrorNodeTest`, `ExplicitBuildingAssemblyTest`, `SegmentCompositionTest` und `BuildingSourceEditorsTest` sind **80 Tests ohne Fehler** durchgelaufen.

`./gradlew runClient -PbuildingRotationSmoke --offline --console=plain` erfolgreich: echte Dropdowns für beide Knoten mit allen drei Achsen und vier Winkeln, normale und kompakte Inspektoren, geometrische Vorschauen, JSON-Rundläufe, Undo/Redo pro Bedienelement sowie Einfügen über die nativen Kategorien **Copycats** und **Segmente**. Aufnahmen unter `docs/images/copycat-rotation-{normal,compact}.png` und `docs/images/segment-rotation-{normal,compact}.png`. Für die Prüfung waren VSync und NeoForges Startfensterübernahme vorübergehend deaktiviert; die ursprünglichen Testeinstellungen wurden anschließend wiederhergestellt.

## Vertikale Raumkanten und Segmentmontage (2026-11-06)

Der abschließende isolierte `build`-Lauf besteht mit **451 JUnit-Tests ohne Fehler oder übersprungene Tests**. JAR-Integrität, Ressourcenprüfung und `git diff --check` sind erfolgreich.

Der Regressionstest zur gespiegelten 3/16-Schichtwand schlug vor der Korrektur beim Einbau am Raum fehl. Die Raummontage bildet die Tiefenreihenfolge nun passend zur geometrischen Drehung ab; Boden und Decke verwenden ebenfalls eine gemeinsame Abbildung für Blockpositionen und lokale Copycat-Geometrie. Fachtests vergleichen die Materialien und Geometrien mit einer unabhängig räumlich transformierten Segmentvorschau auf allen vier Raumseiten sowie bei asymmetrischen Boden-/Deckenmustern.

Die neuen Kantenprüfungen decken getrennte typisierte Segmenteingänge an konvexen und konkaven Eckpunkten, gedrehte Kantenprofile, tiefe Querschnitte, explizite Luft, falsche Höhen, Geometrieänderungen, Löschung, JSON, native Diagrammverbindungen und wiederverwendete Knotengruppen ab.

Der isolierte Clientlauf `runClient -ProomMountingSmoke` besteht. Er prüft die tatsächlichen nativen Porttypen und bedient den Kantenanschluss im Rauminspektor mit echter Auswahl, Undo/Redo und JSON-Rundlauf. Die Aufnahmen bei 854 × 480 und 427 × 240 GUI-Pixeln sind visuell kontrolliert: [vier vertikale Kanten](release-0.6.0/images/room-vertical-edges-compact.png), [zusammenhängende Schichten](release-0.6.0/images/room-joined-layers-normal.png).

`runGameTestServer -PgraphWorldSmoke` besteht alle **62 erforderlichen Minecraft-GameTests**, einschließlich des neuen Weltvergleichs. Dieser setzt die Schichtwand und ein 2 × 2 × 2 großes Kantensegment tatsächlich, prüft sechs direkt aufeinanderfolgende Wandpixel, je drei Pixel der beiden Verkleidungen, echte Kollision, NBT, kostenfreie Wiederholung und freie Raumzellen. Der bestehende Turtle-Bau mit 603 Platzierungen besteht ebenfalls; der ältere 1.115-Platzierungen-Test wird mit diesem Schalter weiterhin übersprungen.

Die Laufkonfiguration `tools/room-mounting-validation.init.gradle` trennt Klassen, Testberichte, Clientoptionen und Welten von anderen Entwicklungsläufen. Die JUnit-Abnahme verwendet zusätzlich `--project-cache-dir build/room-mounting-gradle`. Die Vorlagenwelt wurde für die Fehlerkorrektur nicht verändert; bestehende Bauten erhalten die neue Montage beim erneuten Bauen am Tisch.

## Gemeinsame Wand-Endreihen am Raum (2026-11-07)

Die vollständige isolierte JUnit-Suite besteht mit **463 Tests ohne Fehler oder übersprungene Tests**. `RoomWallJointsTest` prüft sieben Fälle: unveränderte Segmentlängen bei gemeinsamer Eckmontage, Vereinigung der tatsächlichen beiden Endgeometrien samt Verkleidungen, gespiegelte Schichtwände auf allen vier Seiten, Kantensegmente und explizite Luft in den gemeinsamen Zellen, einspringende Ecken mit korrekten Portbreiten, tiefe Kantenquerschnitte und die Ablehnung von Wandtiefen außerhalb der Kontur. Größere Boden- und Deckensegmente werden auf die neue Hülle begrenzt. Die historischen T-Haus-Exporte enthalten ausdrücklich `joinWallEnds=false`; ihre 1.902 Positionen bleiben unverändert.

Zusätzlich läuft `VorlagenRoomSnapshotTest` mit `ARCHITECT_ROOM_SNAPSHOT` gegen einen nur lesend exportierten Datenstand der Vorlagenwelt: Die gespeicherte Lagerhalle kompiliert mit unveränderten Wandquellen zu **23 × 37 Außenblöcken und 4.390 Baupositionen**. Alle vier Wandpaare treffen sich auf ihren Endreihen; es entstehen keine außerhalb liegenden Eckpfeiler. Der vollständige Testbefehl verwendet `-I tools/room-mounting-validation.init.gradle --project-cache-dir build/room-mounting-gradle test --offline --console=plain --max-workers=2`.

Der erneute `runGameTestServer -PgraphWorldSmoke` besteht alle **62 erforderlichen GameTests**. Der Schichtwandtest baut nun ausdrücklich die gemeinsame Eckmontage: keine Position außerhalb der 5 × 5 Kontur, sechs zusammenhängende Pixel mit je drei Pixeln Stein und Holz, ein 2 × 2 × 2 Kantensegment innerhalb der Endstreifen, echte Kollision, NBT und idempotentes Platzieren. Der Turtle-Test mit 603 Platzierungen besteht ebenfalls.

`runClient -ProomMountingSmoke` besteht mit echten Kantenanschlüssen, Auswahl, Undo/Redo, JSON und normaler sowie kompakter Oberfläche. Die [Raumvorschau mit gemeinsamen Eckzellen](release-0.6.0/images/room-shared-corners-normal.png), [Kanteneingänge](release-0.6.0/images/room-vertical-edges-compact.png) und [gespiegelte Schichtwand](release-0.6.0/images/room-joined-layers-normal.png) sind visuell geprüft. Die Vorschau hält die 7 × 7 Kontur ein; Boden und Decke ragen nicht über die zusammengesetzten Wände hinaus.

Protokolle: `build/room-mounting-validation-logs/joints-{unit-full,server,client}.log`; JUnit-Zählung: `joints-unit-summary.json`. Ressourcenprüfung und `git diff --check` sind sauber. Die originale Vorlagenwelt wurde nicht verändert. Nach dem Laden der aktualisierten Mod wird der neue Plan am Architektentisch erneut gebaut; der vorhandene Eigentumsnachweis entfernt dabei ersetzte eigene Baupositionen.
