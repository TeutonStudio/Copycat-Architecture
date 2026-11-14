# Graphstudio und Mod: Kompatibilitätsprüfung

## Behebung in 0.6.1

Graphstudio und Mod werden gemeinsam aus demselben Kotlin-Fachkern und Katalog gebaut. Der Browser-Codec wird aus den nativen Datenklassen erzeugt. Er unterstützt alle zehn Form- und 36 Gebäudeknotentypen, Formschemata 1–4, Formgraphschemata 1–3 und Gebäudegraphschemata 1–4. Projektion, Gruppen samt benannten Schnittstellen, exakte Geometrie, Dachvorlagen und sämtliche Sonderraumfelder bleiben erhalten. Ein-/Ausgangstypen und Inspektorfelder werden vollständig aus dem nativen Vertrag übernommen und für alle 72 Gebäudeknotenvarianten geprüft. Die nativen Schnittstellenadapter sind auch im Browser eingebunden. Verbindungsänderungen aktualisieren Gruppenbindungen; Gruppeninstanzen teilen ihre Definition. Neue Formexporte verwenden die native Schemabestimmung. Unbekannte Knotentypen und künftige Schemata werden weiterhin abgewiesen.

Die folgende Prüfung dokumentiert den **Zustand vor dieser Behebung** und bleibt als historische Fehlerbeschreibung erhalten. Maßgeblich für 0.6.1 sind die automatisierten Browser-, Mod- und Austauschtests.


Stand: **13. November 2026**. Ergebnis: **nur teilweise kompatibel**. Der gemeinsame Formataufbau genügt derzeit nicht für einen allgemeinen Austausch in beide Richtungen. Die Prüfung hat keine Produktionsmodelle verändert.

## Tatsächlich verglichene Stände

| Stand | Identifikation | Rolle |
| --- | --- | --- |
| Lokale Mod | 0.5.1; Git-Basis `990754d8db44beec4ab6c1aa5c04c1bc74197f0d` plus vorhandene Arbeitskopie | Aktueller Minecraft-Code und echte JVM-Validatoren |
| Öffentliches Graphstudio | **0.4.0**; öffentlicher Quellstand `40cd4518038778c35445b87431c0d8003ba98cc8` | Tatsächlich ausgeliefertes JavaScript unter `https://teutonstudio.github.io/Copycat-Architecture/` |
| Lokaler Browser-Prototyp | `web/graph-editor`, Paketversion 0.1.0 | Separater TypeScript-Editor, der den lokalen Modkatalog direkt importiert |

Die Wiki beschreibt noch Graphstudio 0.3. Das öffentliche Repository wurde von `copycat-architect` nach `Copycat-Architecture` umbenannt. Der aktuelle Browser liegt dort unter `web/`, nicht unter `web/graph-editor/`. Version und Aufbau wurden gegen [package.json](https://github.com/TeutonStudio/Copycat-Architecture/blob/40cd4518038778c35445b87431c0d8003ba98cc8/web/package.json) und den ausgelieferten Browsercode abgeglichen.

Wichtig: Der öffentliche Katalog enthält 34 Gebäudetypen, der veröffentlichte **Browserparser bietet davon tatsächlich 23 an**. Maßgeblich für den Austausch sind die Laufzeitfähigkeiten, nicht allein die Einträge in `graph-models.json`. Siehe [Browserfreigaben und Parser](https://github.com/TeutonStudio/Copycat-Architecture/blob/40cd4518038778c35445b87431c0d8003ba98cc8/web/kotlin/GraphEngine.kt#L13).

## 1. Unterschiedliche Schemata und Gebäudeknoten

| Bereich | Lokale Mod | Öffentliches Graphstudio 0.4 |
| --- | --- | --- |
| Formdokument | Schema 1–4 | Schema 1–3; Export Schema 3 |
| Formgraph | Schema 1–3 | Schema 1–2 |
| Gebäudedokument | Schema 1–2 | Export Schema 2 |
| Gebäudegraph | Schema 1–4 | **Nur Schema 1** |
| Formknotentypen | 10 | Dieselben 10 Namen, mit zusätzlichen Einschränkungen |
| Gebäudeknotentypen | 26 | 23; davon nur 13 gemeinsam |

Die Schemaeinschränkungen sind im [Browserparser](https://github.com/TeutonStudio/Copycat-Architecture/blob/40cd4518038778c35445b87431c0d8003ba98cc8/web/kotlin/GraphEngine.kt#L29) ausdrücklich implementiert. Lokale Formgraphen mit Schema 3, Formdokumente mit Schema 4 und das native Erker-/Gaubenbeispiel mit Gebäudegraphschema 4 werden abgewiesen.

**Nur im lokalen Mod, nicht im veröffentlichten Browser:**

`AREA`, `SEGMENT_LAYOUT`, `ROOM_GEOMETRY`, `ROOF_LEVEL`, `ROOF_LAYOUT`, `STAIRWELL`, `ELEVATOR_SHAFT`, `SERVICE_SHAFT`, `GARDEN`, `PATH`, `ROOM_LAYOUT`, `BAY_WINDOW`, `DORMER`.

**Nur im veröffentlichten Browser, nicht im lokalen Mod:**

`COPYCAT_UNION`, `COPYCAT_INTERSECTION`, `COPYCAT_DIFFERENCE`, `COPYCAT_SYMMETRIC_DIFFERENCE`, `SEGMENT_UNION`, `SEGMENT_INTERSECTION`, `SEGMENT_DIFFERENCE`, `SEGMENT_SYMMETRIC_DIFFERENCE`, `ROTATION`, `MIRRORING`.

Alle zehn zuletzt genannten Typen wurden mit dem ausgelieferten Browserkern erstellt und exportiert. Jeder dieser Exporte scheitert anschließend im lokalen Mod. Der Gson-Decoder liest den unbekannten Enumwert als `null`; die Weiterverarbeitung endet derzeit mit einem Fehler zum nicht-nullbaren Parameter `BuildingNode.kind`. Die öffentlichen [Operatorprüfungen](https://github.com/TeutonStudio/Copycat-Architecture/blob/40cd4518038778c35445b87431c0d8003ba98cc8/web/test/building-operations.test.js) beschreiben diese Browserfunktionen ebenfalls.

Gemeinsam sind lediglich `COPYCAT`, `SEGMENT`, `PATTERN`, `FLOOR`, `CEILING`, `ROOM`, `STOREY`, `ROOF`, `BUILDING`, `COPYCAT_ROTATE`, `COPYCAT_MIRROR`, `SEGMENT_ROTATE` und `SEGMENT_MIRROR`. Auch bei diesen Namen müssen Schema und Eigenschaften passen.

## 2. Selbst ein älteres natives Gebäude scheitert an `projection`

Die lokale `BuildingGraph`-Klasse besitzt immer eine `BuildingProjection`; `Json.encode` schreibt sie auch bei ihren Standardwerten mit. Der veröffentlichte Browser verlangt dagegen `graph.projection == null` und keine nichtleeren `nodeGroups`.

Reproduzierter Kontrollfall:

1. `BuildingGraph.starter()` im Mod als vollständiges Gebäudedokument exportieren.
2. Im veröffentlichten Browser importieren: **abgewiesen**, `Graph groups and projections require the native editor`.
3. Ausschließlich die Standardprojektion aus diesem Testdokument entfernen: Browserimport und Browserexport funktionieren.
4. Den Browserexport im Mod strukturell prüfen und mit der Seedbibliothek vollständig auswerten: funktioniert.

Damit liegt eine Austauschhürde bereits im normalen nativen JSON-Export vor, auch ohne neue Knotentypen. Der bereinigte Kontrollfall belegt nur die getestete ältere Teilmenge; er ist keine allgemeine Konvertierung neuer Gebäude.

## 3. Gemeinsame Formknoten haben unterschiedliche Semantik

**Spiegelungen:** Die Mod unterstützt 13 Spiegelebenen. Der Browserparser akzeptiert nur X, Y und Z. Eine ansonsten kompatible Form mit `MIRROR.plane = XY` wird mit `This mirror plane requires the native editor` abgewiesen. Eine X-Spiegelung wurde dagegen erfolgreich durch Browserexport und Modauswertung geprüft. Siehe [Browser-Spiegelparser](https://github.com/TeutonStudio/Copycat-Architecture/blob/40cd4518038778c35445b87431c0d8003ba98cc8/web/kotlin/GraphEngine.kt#L93).

**Leere Copycat:** Graphstudio 0.4 bietet im Katalog eine leere gespeicherte Quelle an: `SAVED.savedCopycat.parts = []`. Diese Definition steht ausdrücklich in der öffentlichen [Copycat-Auswahl](https://github.com/TeutonStudio/Copycat-Architecture/blob/40cd4518038778c35445b87431c0d8003ba98cc8/web/src/copycat-options.js#L19). Der lokale Mod ruft für jede gespeicherte Quelle `Geometry.normalized` auf; dort sind 1–32 Teile vorgeschrieben.

Zwei im Browser erfolgreich exportierte Fälle scheitern deshalb im Mod mit `A form needs 1–32 parts`:

- Die leere Quelle ist direkt mit dem Ergebnis verbunden.
- Die leere Quelle geht zusammen mit einer nichtleeren Stufe in eine Vereinigung ein. Auch das nichtleere Endergebnis beseitigt die Inkompatibilität der Quelle nicht.

Die identischen zehn Formknotennamen bedeuten somit keine vollständige Formgraphkompatibilität.

## 4. Native Gruppen und Browserbibliotheksgruppen sind verschiedene Modelle

Der veröffentlichte Browser lehnt native `graph.nodeGroups` mit Inhalt ausdrücklich ab. Seine gespeicherten Browsergruppen sind Vorlagen aus kopierten Knoten und Verbindungen; sie implementieren nicht das native Modell mit `definitionId`, `graphInterface`, `inputConnections` und gemeinsamem Definitionslayout.

Der damalige, inzwischen durch den gemeinsamen Browsereditor ersetzte **lokale TypeScript-Prototyp** bewahrt solche Felder beim unveränderten Import/Export auf. Seine Bearbeitungslogik aktualisiert aber nicht alle nativen Gruppenbindungen. Zwei konkrete Fälle wurden mit seinem echten `graph-document.ts` und dem Modvalidator geprüft:

| Bearbeitung im lokalen Prototyp | Exportierter Zustand | Reaktion des Mods |
| --- | --- | --- |
| Einzelnen Gruppeneingang auf eine andere Quelle umverdrahten | `node.inputs` geändert, `group.inputConnections` weiterhin auf alter Quelle | `Connect named group inputs on the instance node` |
| Externe Quelle einer benannten Gruppeneingabe löschen | Normale Kante entfernt, `group.inputConnections` verweist weiter auf gelöschte ID | `Invalid group input connection` |

Betroffene lokale Stellen: `replacePort` und `removeNode` in `web/graph-editor/src/graph-document.ts`; die zusätzlichen nativen Bedingungen stehen in `GraphGroups.validate`.

Zusätzlich setzt der lokale Prototyp beim Anlegen eines `BAY_WINDOW` den Graphschemawert auf 2. Die native `withCurrentSchema()`-Regel verlangt für Erker und Gauben Schema 4. Der Mod akzeptiert niedrigere Schemawerte an dieser Stelle noch strukturell; diese Abweichung ist daher eine falsche Versionskennzeichnung, kein in der Prüfung nachgewiesener unmittelbarer Importabbruch.

## Ausgeführte Prüfungen und Grenzen

**25 gerichtete Austauschprüfungen:** acht echte native Exporte im ausgelieferten Browserkern und 17 Browser-/Prototyp-Exporte in den echten Modvalidatoren. Bei den gemeinsamen Gebäudekontrollen wurde zusätzlich `Generator.generate` mit der Seedbibliothek ausgeführt; Formkontrollen verwenden `Geometry.validate` einschließlich Graphauswertung.

- Native Exporte im veröffentlichten Browser: zwei akzeptiert, sechs wie oben beschrieben abgewiesen.
- Browser-/Prototyp-Exporte im Mod: drei Kontrollen akzeptiert; zehn unbekannte Gebäudetypen, zwei leere Formquellen und zwei fehlerhafte Gruppenbearbeitungen abgewiesen.
- Acht vorhandene lokale Browser-Domänentests bestanden; TypeScript-/Vite-Build erfolgreich.
- Isolierter JVM-Auditlauf erfolgreich: Die erwarteten Kontrollresultate und Inkompatibilitäten wurden reproduziert. „Erfolgreich“ bezeichnet hier den Audit, nicht vollständige Modellkompatibilität.

Der ausgelieferte Browserkern wurde aus `assets/index-DxF6hRjP.js` geladen. Für die Prüfung liefen dessen unveränderte Parser-, Export- und Modellfunktionen in einem isolierten JavaScript-Kontext; React-Start und Netzwerkzugriffe wurden nicht ausgeführt. Das ist eine Modellprüfung, keine neue visuelle Browserabnahme. Shader, Materialien, alle Geometriekombinationen und sämtliche Baupläne wurden nicht vollständig auf Gleichheit geprüft.

Die dauerhaft gespeicherten [maschinellen Ergebnisse und Fingerabdrücke](graph-compatibility-2026-11-13.json) enthalten alle Fälle. Temporäre Prüfdaten und der Prüfaufbau liegen unter `build/graph-compatibility-audit/`, darunter `browser-results.json`, `mod-results.json`, `native/`, `browser-exports/` und `gradle-final.log`.

Lokaler Aufruf, solange der temporäre Prüfaufbau vorhanden ist:

```sh
JAVA_HOME=/home/alex/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  ./gradlew graphCompatibilityAudit \
  -I build/graph-compatibility-audit/audit.init.gradle \
  --offline --console=plain --max-workers=1 \
  -Dorg.gradle.jvmargs=-Xmx2G \
  -Pkotlin.compiler.execution.strategy=in-process
```

## Nächste Schritte für vollständige Kompatibilität

1. Die auseinanderentwickelten Domänenstände zusammenführen: Browseroperatoren und native Gebäude-/Gruppenfunktionen benötigen einen gemeinsamen ausführbaren Vertrag.
2. Unterstützte Schemata, Knotentypen, Felder, Spiegelebenen und Leerwertsemantik als versionsbezogene Laufzeitfähigkeiten veröffentlichen; der Katalog allein ist kein Kompatibilitätsnachweis.
3. Den Browserparser und die Gruppenbearbeitung an diesen Vertrag anpassen. Unbekannte Typen im Mod vor der Enumdeserialisierung mit einer verständlichen Versionsmeldung behandeln.
4. Die Austauschfälle als dauerhafte beidseitige Regressionstests gegen denselben freigegebenen Mod-/Browserstand ausführen.
