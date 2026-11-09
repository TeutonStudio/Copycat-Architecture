# Materialbibliothek und persönliche Bibliotheken

> **Stand dieses Entwicklungszweigs:** Diese Anleitung beschreibt die vollständige Überarbeitung mit Vorschau-Auswahldialog, allen sechs Copycat- und Segmentoperationen sowie dem 2D-Anordnungsraster. Der Browser unterstützt hier 10 Form- und 21 Gebäudeknotentypen. Die Änderungen sind noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Modrelease 0.6.0 bleibt unverändert.

Graphstudio **0.4** speichert Entwürfe und Bibliotheken lokal in deinem Browser. Die Materialbibliothek ist über **Meine Bibliotheken** erreichbar.

## Minecraft-Materialien

Der mitgelieferte Katalog beschreibt **1.062 Blöcke aus Minecraft Java 1.21.1** mit Blockstates und Modellverweisen. Texturen stammen aus **Faithful 64× Java**, Zweig 1.21.1, von [Faithful Resource Pack](https://faithfulpack.net). [Repository](https://github.com/Faithful-Resource-Pack/Faithful-64x-Java/tree/1.21.1) · [Lizenz](https://faithfulpack.net/license).

Im Formgraphen:

1. Im Inspektor **Dokument und Materialgruppen** aufklappen.
2. Bei der gewünschten Gruppe **Wählen** öffnen und nach einer Block-ID suchen, etwa `minecraft:stone_bricks`.
3. Einen Block wählen. Bei Blöcken mit Zuständen können beispielsweise `axis`, `facing` oder `waterlogged` eingestellt werden.
4. **Dieses Material verwenden** übernimmt die Belegung für die Gruppe. Die 3D-Vorschau zeigt die zugehörigen Oberflächentexturen.

Bei Gebäuden besitzt jede Palette-Rolle ebenfalls eine Materialauswahl. Das bisherige Gebäudeschema speichert **Block-IDs**, keine frei gewählten Blockzustände. Dort dient die Zustandsanzeige zum Nachschlagen; die Übernahme verwendet die Block-ID.

Die Materialbelegung der Formgruppen ist eine **Graphstudio-Vorschauzuordnung**. Sie wird mit dem Browserentwurf, dem gespeicherten Graphen, einer Knotengruppe und in der Graphstudio-Sicherung aufbewahrt. Das bisherige Minecraft-Formformat enthält Materialrollen statt dieser Belegung; der normale Minecraft-JSON-Export erweitert dieses Format nicht. Im Spiel werden Materialien über dessen Materialrollen und Paletten zugeordnet.

Ein Eintrag im Ressourcen-Katalog belegt noch nicht, dass Copycats+ ihn als Material erlaubt. Die Materialzulässigkeit und Verfügbarkeit einer Mod prüft der verwendete Spielstand.

## Mod-Repositories verlinken

Unter **Meine Bibliotheken → Verlinkte Mods**:

1. Die Hauptadresse eines öffentlichen GitHub-Repositories angeben, beispielsweise `https://github.com/Autor/Mod`.
2. Optional einen passenden Zweig, Tag oder Commit einstellen. Ohne Angabe wird der Standardzweig verwendet. Die Mod-Version muss zu deinem Spielstand passen.
3. Falls das Repository mehrere Plattformen oder Ressourcenpakete enthält, optional den gewünschten Ressourcenordner eingrenzen, etwa `common/src/main/resources`.
4. **Mod verlinken** liest die Ressourcenliste und die vorhandenen Mod-Metadaten ein. Die Zahl der erkannten Blöcke und der festgehaltene Commit werden angezeigt.

Erkannt werden `assets/<namespace>/blockstates/*.json`, Modelle und PNG-Texturen. Auch eingecheckte Dateien in generierten Ressourcenordnern werden gefunden. Modell-Eltern, `#texture`-Verweise und Ressourcen aus anderen verlinkten Namespaces werden verfolgt. Blockstates mit Varianten und Multipart-Bedingungen werden ausgewertet. Bei gewichteten Zufallsvarianten wird für eine stabile Vorschau die erste Variante verwendet.

`fabric.mod.json`, `META-INF/mods.toml` und `META-INF/neoforge.mods.toml` liefern Mod-IDs und deklarierte Abhängigkeiten. Fehlende erforderliche Mods werden aufgelistet; ihre Ressourcen lassen sich durch ein weiteres Repository ergänzen. Versionsplatzhalter aus Build-Skripten bleiben als solche erkennbar. Die Bibliothek führt weder Gradle noch Mod-Code aus und installiert keine Mods. Es wird kein vollständiger Modloader-Abhängigkeitslöser ausgeführt.

**Aktualisieren** liest den gewählten Zweig erneut ein und hält dessen neuen Commit fest. Ohne Aktualisierung bleiben die Ressourcenadressen auf dem bisherigen Commit. **Verknüpfung entfernen** entfernt das Repository aus der Materialauswahl. Eine im Graphen gespeicherte Block-ID wird dadurch nicht still ersetzt.

## Grenzen der Vorschau

Die Texturen werden auf die berechnete Copycat-Geometrie aufgebracht. Das ist keine vollständige Minecraft-Rendering-Engine: Entity-Renderer, eigene Java-Modell-Loader, CTM, Shader, biomeabhängige Farben, bewegte Texturen und alle Spezialfälle der Modell-UV-Rotation werden nicht nachgebildet. Von Animationen wird das erste Bild verwendet. Fehlende Modelle oder Texturen werden gemeldet; die Vorschau bleibt bei Bedarf bei der Materialfarbe.

Die Repository-Verknüpfung benötigt öffentlich lesbare, bereits vorhandene Ressourcen. Bei ausschließlich während des Mod-Builds erzeugten Dateien kann der Browser die Materialien nicht vollständig rekonstruieren. GitHub-Zugriffslimits, Netzwerkfehler und unvollständige Ressourcenbäume werden angezeigt. Es werden keine GitHub-Zugangsdaten abgefragt oder gespeichert.

## Graphen speichern

Die Leiste über dem Graphen besitzt ein Namensfeld, **Graph speichern** und **Gespeicherte Graphen**. Ein gespeicherter Eintrag enthält Graph, geladene Copycat-Bibliothek und Vorschau-Materialbelegungen. Derselbe Name im selben Editor aktualisiert den vorhandenen Eintrag. Form- und Gebäudegraphen werden getrennt angeboten.

Gespeicherte Formgraphen erscheinen unter **Copycat auswählen → Gespeicherte Formen**. Der Dialog ist im Formgraphen an den Quellen **Grundform** und **Gespeicherter Copycat** sowie im Gebäudegraphen an der **Copycat-Referenz** verfügbar. Neben persönlichen Formgraphen stehen dort auch Formen aus der geladenen Referenzbibliothek; die Vorschaukarten kennzeichnen die Herkunft. Suche, gerenderte Karten und eine größere drehbare Vorschau helfen bei der Auswahl. Erst **Auswahl übernehmen** setzt die gewählte Quelle.

Im Gebäudegraphen lädt ein persönlicher Eintrag eine feste Formversion einschließlich der Materialbelegung als Block-IDs. Spätere Änderungen des benannten Formgraphen verändern diese Referenz nicht; erneutes Übernehmen lädt die neue Version ausdrücklich. Bibliotheksformen verwenden ihren bestehenden Versionsschlüssel. **Formdatei laden** im Inspektor oder **Copycat laden** in der Werkzeugleiste importiert alternativ eine einzelne Formdatei. [Auswahldialog und Operatoren](Graphstudio.md)

Zusätzlich wird der zuletzt bearbeitete Entwurf beider Editoren automatisch gespeichert. Nach dem Neuladen wird dieser Entwurf wiederhergestellt. Undo/Redo-Verlauf und eine laufende Knotenauswahl werden nicht als dauerhafter Verlauf gespeichert.

## Knotengruppen wiederverwenden

Mit **Umschalt** mehrere Knoten auswählen oder einen einzelnen Knoten anklicken, einen Namen vergeben und **Knotengruppe speichern** wählen. Die gespeicherte Gruppe enthält die Knoten, ihre internen Verbindungen, benötigte Materialgruppen und deren Vorschau-Materialbelegungen. Gebäude-Knotengruppen bewahren außerdem ihre geladene Copycat-Bibliothek auf. Beim Einfügen werden deren Formen und Module ergänzt; derselbe Versionsschlüssel mit anderem Inhalt wird als Konflikt gemeldet.

**Knotengruppe einfügen** erzeugt neue Knoten-IDs und setzt die Gruppe neben den bestehenden Graphen. Interne Verbindungen bleiben erhalten; externe Eingänge werden nicht an zufällige vorhandene Knoten angeschlossen. Sie können anschließend verbunden werden. Ergebnis-Knoten und die geschützte Standard-Leerwand werden nicht dupliziert. Bei Namenskonflikten von Materialgruppen werden neue IDs und eindeutige Prioritäten vergeben. Die bisherigen Knotengrenzen und höchstens acht Materialgruppen gelten weiterhin.

Diese Browser-Knotengruppen sind wiederverwendbare Vorlagen. Sie sind keine Implementierung der neueren verknüpften Gruppeninstanzen des lokalen Spiel-Editors.

## Browserspeicher und Sicherungen

Graphstudio verwendet **IndexedDB** statt Cookies: Graphen, Bibliotheken und Ressourcenverweise sind für Cookies zu groß. Die Daten gehören zu diesem Browserprofil und dieser Website. Es gibt kein Benutzerkonto und keine automatische Synchronisierung zwischen Geräten.

Unter **Meine Bibliotheken → Gespeicherte Inhalte** können benannte Graphen und Knotengruppen entfernt sowie **Sicherung exportieren** und **Sicherung importieren** verwendet werden. Die Sicherung enthält auch die aktuellen Entwürfe und verlinkten Mods. Beim Import werden benannte Inhalte zusammengeführt; die gesicherten Entwürfe erscheinen als zusätzliche wiederhergestellte Graphen. Aktuell offene Entwürfe werden dabei nicht überschrieben.

Die Sicherung bewahrt auch die in Gebäudeentwürfe geladenen Formversionen auf. Ein normaler Minecraft-Gebäudeexport speichert lediglich deren Referenzschlüssel; er bündelt die Formen nicht. Für den Spielimport müssen die entsprechenden Bibliotheksversionen dort verfügbar sein.

Blockstate- und Modell-JSONs werden nach dem ersten Abruf lokal zwischengespeichert. Bilder nutzen den normalen Browsercache; eine vollständige Offline-Verfügbarkeit aller Texturen wird nicht zugesichert. Das Löschen von Websitedaten, ein privates Browserfenster oder eine Speicherbereinigung des Browsers kann lokale Daten entfernen. Eine exportierte Sicherung bewahrt den eigenen Stand unabhängig davon auf.

Die Statuszeile zeigt, ob der Stand gespeichert wurde. Bei vollem oder gesperrtem Speicher wird ein Fehler mit Sicherungsmöglichkeit angezeigt. Wenn ein anderer Tab den gespeicherten Stand verändert hat, überschreibt ein älterer Tab ihn nicht: Eigene Arbeit sichern und die Seite neu laden.
