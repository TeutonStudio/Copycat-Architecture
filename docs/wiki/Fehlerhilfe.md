# Fehlerhilfe

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.2** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

Lies zuerst die Meldung im Editor oder Turtle-Terminal. Sie nennt häufig die betroffene Position oder das fehlende Material. Bei einem unterbrochenen Bau bleiben fertige Blöcke erhalten. Behebe die Ursache, wähle am Architektentisch **Weiter** und starte `architect AUFTRAGS-ID` erneut. Der vollständige Ablauf steht unter [Turtle-Bau](Turtle-Bau.md).

## Turtle und Bauaufträge

Die folgenden englischen Meldungen stammen aus dem Bauprogramm beziehungsweise seiner Schnittstelle.

| Problem oder Meldung | Ursache | Lösung |
|---|---|---|
| `Equip a Copycat Architect block printer upgrade` | Die Turtle findet das Drucker-Peripheral nicht. | [Blockdrucker](Blockdrucker.md) im Inventar auswählen und mit `turtle.equipLeft()` oder `turtle.equipRight()` ausrüsten. |
| `First claim requires the turtle at its configured dock` | Die erste automatische Zuweisung erfolgt außerhalb des Depots. | Turtle genau auf die eingetragene Depotposition setzen. Die Kiste gehört darunter. |
| `Job already assigned` oder `Job is assigned to a different turtle` | Der Auftrag ist an eine andere Computer-ID gebunden. | Die bereits zugewiesene Turtle verwenden und die ID prüfen. |
| `Job not found` oder `Job unavailable to this turtle` | ID, Besitzer oder Dimension passen nicht. | `architect list` auf der vorgesehenen Turtle ausführen und den passenden Auftrag verwenden. |
| `Put a chest or barrel directly below the dock` | Unter dem Depot fehlt ein unterstützter Materialbehälter. | Kiste oder Fass unmittelbar unter der Turtle-Standposition aufstellen. |
| `Work space blocked` | Ein Block belegt Baufläche, Freiraum oder Flugweg. | Angegebene Position freiräumen. Passende, bereits gebaute Zielblöcke dürfen bleiben. |
| `Movement blocked` | Eine tatsächliche Bewegung ist gescheitert. | Flugweg prüfen und Hindernis entfernen; die Turtle gräbt niemals. |
| `Work space chunk unloaded` oder `Target chunk is not loaded` | Ein benötigter Bereich ist nicht geladen. | Arbeitsbereich laden, dann am Tisch **Weiter** wählen. |
| `Missing surface material` | Ein benötigter Verkleidungsblock fehlt. | Das genannte Item im Depot nachlegen; Gruppenpalette und Materialbedarf prüfen. |
| `Missing zinc` | Rohmaterial reicht nicht aus. | Zinkbarren oder gefüllte Kartuschen nachlegen. Bedarf und Einheiten erklärt [Kartuschen und Materialien](Kartuschen-und-Materialien.md). |
| `No inventory slot for remaining zinc` | Für die Restkartusche fehlt Platz. | Turtle-Inventar aufräumen und mindestens einen Platz freimachen. |
| `Missing fuel` | Treibstoff oder Rückwegreserve reicht nicht. | Kohle, Holzkohle oder Kohleblöcke nachlegen; bei einer leergefahrenen Turtle direkt in deren Inventar. |
| `Placement denied` oder `Placement denied by protection` | Der Bereich erlaubt die Platzierung nicht. | Baurechte und Gebietsschutz prüfen. |
| `Target occupied by an entity` | Ein Spieler oder anderes Wesen steht im Zielfeld. | Zielfeld räumen und erneut starten. |

Bei einer ungültigen Depotposition wähle 2–8 Blöcke Abstand nordwestlich außerhalb der tatsächlichen Gebäudefläche und setze Depot-Y auf Bauursprung-Y plus eins. Prüfe auch die Drehung. Für große Entwürfe gilt ein Limit von 200.000 Zellen im Arbeitsraum; die [Gebäudeplanung](Gebaeudeplanung.md) hilft beim Verkleinern und Positionieren.

## Einzeldruck und Inventar

`architect print` benötigt neben dem ausgerüsteten Upgrade ein **zusätzliches Druckergerät im Turtle-Inventar**. Das Zielfeld muss leer sein. Vor dem Drucken oder Abholen muss außerdem ein Inventarplatz für das zurückgegebene Gerät frei bleiben.

Scheitert ein Druck, versucht das Programm den gesetzten Drucker zurückzuholen. Falls auch das scheitert, meldet es `Printer remains` samt Richtung und Sitzung. Gerät und Inhalt bleiben in der Welt. Schaffe Inventarplatz oder behebe die Zugriffsursache. Behalte die angegebene Sitzung für die Wiederaufnahme über die [Lua-API](../LUA_API.md); bewege oder drehe die Turtle dabei nur, wenn die verwendete Richtung weiterhin auf denselben Drucker zeigt.

Ein leeres Rohmaterialreservoir und fehlende Verkleidungsblöcke sind unterschiedliche Ursachen. Eine kreative Kartusche ersetzt im Überlebensmodus weiterhin keine ausgewählten Verkleidungsblöcke.

## Formen, Graphen und Import

**Leere Dachanordnung:** Verbinde mindestens eine Dachebene mit der Dachanordnung und diese mit dem Dach. Ein angeschlossenes, aber unvollständiges Dach verhindert die vollständige Gebäudeberechnung. Die Dachfunktion selbst ist inzwischen implementiert.

**Falsche Segmentmaße:** Wandsegmente müssen zur angezeigten Wandbreite und Raumhöhe passen. Boden und Decke brauchen das Außenmaß samt Wandhülle: Bei 3 × 3 Innenzellen sind das 5 × 5. Nach einer Grundrissänderung gegebenenfalls die Segmentanordnungen anpassen.

**Fehlende Raumgeometrie:** Verbinde am Raum eine Raumgeometrie-Quelle und belege dort mindestens eine zusammenhängende Innenfläche. Beim aktuellen Graphen wird die Kontur an dieser Quelle bearbeitet.

Ein Graph braucht ein verbundenes, nicht leeres Ergebnis, bevor er als Formversion gespeichert werden kann. Prüfe bei einem leeren Ergebnis zuerst Verbindungen und die gewählte Mengenoperation. Ein Ergebnis-Gruppenfilter verändert lediglich die Editoransicht; er reduziert weder Materialbedarf noch gebaute Geometrie.

Bei unbekannten Formschlüsseln die aktuelle Liste mit `forms()` aus der Lua-API lesen. Importierte Formen erhalten eine neue ID; Beispielschlüssel müssen daher nicht zu deiner Bibliothek passen. Prüfe bei Palettenfehlern, ob jede Gruppen-ID tatsächlich zur gewählten Formversion gehört.

Anleitungen und geprüfte Beispieldateien findest du unter [Formeditor](Formeditor.md) und [Beispiele und Import](Beispiele-und-Import.md). Wenn schon der Spielstart oder das Laden des Mods scheitert, beginne mit der [Installation](Installation.md).

## Spielstart und Bedienoberfläche

Prüfe bei einer Meldung über fehlende Mods zunächst die [Versionsliste](Installation.md): Molten Metals gehört auf Client und Server; KilaGraph und LDLib2 werden zusätzlich auf dem Client benötigt. Java 21 und NeoForge für Minecraft 1.21.1 müssen zum Modpack passen.

Fehlt der Drucken-Reiter, klicke im Formgraphen ins Leere, um die Karte auszuwählen. In kleinen Fenstern den Drucken-Bereich nach unten scrollen, um alle Kartuschen- und Inventarplätze zu erreichen. Bei abweichenden Menüs vergleiche den verwendeten Build mit dem dokumentierten Entwicklungsstand.

[Zur Wiki-Startseite](Home.md)

## Graphstudio und Versionsunterschiede

| Meldung / Verhalten | Bedeutung und nächster Schritt |
| --- | --- |
| Unbekanntes Schema oder unbekannter Knotentyp | Die Datei stammt möglicherweise aus der neueren lokalen Mod. In der [Knotenreferenz](Knotenreferenz.md) Browserverfügbarkeit prüfen; die Datei nicht durch Löschen unbekannter Felder umdeuten. |
| Selektiert ohne Knoten | Einen Knoten anklicken oder das Vorschauziel auf Graph stellen. |
| Boden/Decke ohne Grundriss | Die Platte braucht einen angeschlossenen Raum oder ein direkt aus Wänden aufgebautes Stockwerk. |
| Vorschau meldet einen fehlerhaften Ausgang, obwohl eine Quelle korrekt ist | Zum Prüfen der Quelle auf Selektiert wechseln. Das endgültige Graph-Ergebnis muss zum Export einer Form trotzdem verbunden und nicht leer sein. |
| Neigungsoption ausgegraut | Die native Quelle des älteren Browserkerns unterstützt diese Orientierung nicht. Zulässige Quelle wählen; ihre Geometrie wird nicht automatisch ersetzt. |
| JSON konnte nicht kopiert werden | Browserzugriff auf die Zwischenablage prüfen oder JSON exportieren im normalen Browser verwenden. |
| WebGL 2 nicht verfügbar | Hardwarebeschleunigung beziehungsweise einen geeigneten Browser verwenden; die Graphdaten können trotzdem als JSON gesichert werden. |
| Letzter gültiger Stand | Angezeigte Geometrie stammt aus der vorherigen gültigen Berechnung dieses Vorschauziels. Die aktuelle Meldung zuerst beheben. |

[Einheiten und Ausrichtung](Einheiten-und-Ausrichtung.md) · [Entwicklungsstand](Entwicklungsstand.md)
