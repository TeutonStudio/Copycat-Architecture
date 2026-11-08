# Create: Copycat Architect

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.2** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

**Copycat-Formen entwerfen, Gebäude aus Bauteilen zusammensetzen und mit einer Turtle bauen.**

Die Wiki trennt die aktuelle lokale Mod, das veröffentlichte Graphstudio und geplante Funktionen. Eine identische Mod-Versionsnummer bedeutet noch keinen identischen Quellstand. Beginne bei [Entwicklungsstand und Kompatibilität](Entwicklungsstand.md), wenn Menüs oder Knotentypen bei dir abweichen.

## Dein Einstieg

| Du möchtest … | Anleitung |
| --- | --- |
| Die Mod installieren | [Installation und Rezepte](Installation.md) |
| Einen eigenen Block drucken | [Blockdrucker](Blockdrucker.md) |
| Formen vereinigen, schneiden, drehen oder spiegeln | [Formeditor](Formeditor.md) |
| Zink und Oberflächen bereitstellen | [Kartuschen und Materialien](Kartuschen-und-Materialien.md) |
| Segmente, Räume, Fassaden, Stockwerke und Dächer planen | [Gebäudeplanung](Gebaeudeplanung.md) |
| Raumgrenzen, Fensterdurchbrüche und Wandanschlüsse verstehen | [Raumaufbau und Fassaden](Raumaufbau-und-Fassaden.md) |
| Graphen im Browser bearbeiten | [Graphstudio](Graphstudio.md) |
| Einen Knoten, Anschluss oder Inspektorwert nachschlagen | [Knotenreferenz](Knotenreferenz.md) |
| Ein Gebäude in der Welt errichten | [Turtle-Bau](Turtle-Bau.md) |
| JSON-Entwürfe austauschen | [Beispiele und Import](Beispiele-und-Import.md) |
| Eine Meldung beheben | [Fehlerhilfe](Fehlerhilfe.md) |

## Der Arbeitsablauf

1. Im **Formeditor** erzeugst du die Geometrie eines einzelnen Blocks. Standardquellen und gespeicherte Copycats lassen sich zu neuen Formen verbinden. Materialgruppen trennen ihre sichtbaren Oberflächen.
2. Am **Architektentisch** werden Copycat-Quellen über Segmentanordnungen zu Bauteilen. Räume verwenden entweder einen Grundriss oder platzierte Segmente. Stockwerke verbinden Räume; Fassaden und gemeinsame Wände werden im Gebäudekontext ausgewertet.
3. **Dachebenen** beschreiben Form, Neigung, Höhe und Bezugsgeschoss. Eine Dachanordnung sammelt sie; der Dachknoten wählt Deckung und Vorlagen. Treppenhäuser und Schächte werden als eigene Definitionen über konkrete Gebäudeinstanzen eingebaut.
4. Der Bauplan enthält Geometrie, Materialbedarf und erforderliche Freiräume. Eine **CC:Tweaked-Turtle mit Drucker-Upgrade** setzt ihn in einem vorbereiteten, geladenen Arbeitsraum um.

## Vor dem ersten Bau

- In der aktuellen lokalen Mod enthält eine Standardkartusche **256 mB** Zink; ein Barren liefert **144 mB**. Die Zinkanzeige des älteren Browser-Rechenkerns ist damit nicht gleichzusetzen.
- Neue Dächer sind inzwischen implementiert. Eine leere Dachanordnung besitzt aber noch keine Dachebene. Ohne Dachverbindung bleibt die oberste Decke der Abschluss.
- Der Drucker baut Geometrie und Oberflächen. Inventare, Maschinenfunktionen und ein beweglicher Aufzug entstehen dadurch nicht. Die Turtle entfernt keine Hindernisse.

![Panelgebäude mit dünnen Wänden und Schichtfenstern](../images/panel-building-exterior-35.png)

Das [Panelgebäude](Panelgebaeude.md) bleibt als älteres Modulbeispiel dokumentiert. Seine feste Materialliste gilt nur für den unveränderten Entwurf und den dort genannten Spielstand.
