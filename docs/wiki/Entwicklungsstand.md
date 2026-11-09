# Entwicklungsstand und Kompatibilität

> **Entwicklungszweig `codex/segment-transforms-arrangement-grid`:** Segmentrotation und -spiegelung sowie ein 2D-Raster für Segmentanordnungen am Wandmuster und Raumplatzierungen am Geschoss sind auf diesem Zweig implementiert. Der Browserumfang beträgt hier 17 Gebäudeknotentypen. Diese Änderungen sind noch nicht in master integriert oder im Live-Graphstudio bereitgestellt; die folgende Übersicht beschreibt den bisherigen öffentlichen Stand. [Bedienung](Graphstudio.md#2d-raster-für-anordnungen)

Abgleich vom **8. November 2026**. Drei Stände sind auseinanderzuhalten:

| Stand | Vorhanden | Grenzen |
| --- | --- | --- |
| Lokale Mod **0.5.1**, aktuelle Arbeitskopie | 10 Formknotentypen, 24 Gebäudeknotentypen, gespeicherte Quellen, Spiegelungen, räumliche Segmentanordnungen, semantische Räume, Fassaden, Dachebenen, Schächte und Außenanlagen | Nicht alle lokalen Änderungen sind im öffentlichen Repository enthalten. Die Versionsnummer allein identifiziert diesen Stand nicht. |
| Veröffentlichtes **Graphstudio 0.4** und sein gemeinsamer öffentlicher Spielcode | 10 Formknotentypen und 15 Gebäudeknotentypen; Graph-/Selektionsvorschau, Blockrahmen und hB-Achsen, Inspektor, JSON-Austausch, persönliche Browserbibliotheken | Gespeicherte Copycats und Copycat-Spiegelung/-Rotation sind vorhanden. Segmentlayout-, Raumlayout-, Schacht- und Garten-/Wegknoten sind weiterhin nicht im Browser enthalten. Materialbibliothek mit Faithful-Texturen und verlinkbaren Mod-Ressourcen; eigene Modell-Loader werden nicht ausgeführt. |
| Boolesche Segmentoperatoren | [Vereinigung, Schnitt, Differenz und symmetrische Differenz für Segmente](Geplante-Segmentoperatoren.md) sind im aktuellen Browser und Fachkern implementiert | Teilweise geschnittene normale Vollblöcke und semantische Segmentmetadaten werden ausdrücklich abgewiesen. Für den Spielimport der neuen Operator-IDs ist ein aktualisierter Mod-Build nötig; die veröffentlichte Mod 0.6.0 enthält sie noch nicht. |

## Dateiformate

| Bereich | Aktuelle lokale Mod | Graphstudio 0.4 |
| --- | --- | --- |
| Formdokument | Schema 1–4 | Älteres Format, Export als Schema 3 |
| Formgraph | Schema 1–3 | Schema 1–2 |
| Gebäudedokument | Schema 1–2 | Graphdokumente des älteren Formats, Export als Schema 2 |
| Gebäudegraph | Schema 1–3 | Schema 1 |

Das Schema allein genügt nicht: Eine Datei muss auch ausschließlich unterstützte Knotentypen und Eigenschaften enthalten. Im Browser nicht unterstützte Typen werden abgewiesen. Umgekehrt können ältere Browserdateien im passenden Spielstand geöffnet werden; referenzierte Bibliotheksversionen müssen vorhanden sein. Ältere Graphen werden nicht still in den neuesten Raumaufbau umgewandelt.

## Was als Nachweis gilt

Die Knotenreferenz wurde gegen den lokalen Knotenvertrag und die zugehörigen Inspektor- und Domänenklassen abgeglichen. Ein implementierter Knoten kann trotzdem unvollständige Eingänge besitzen oder im Gebäudekontext unzulässig sein. „Geplant“ bezeichnet dagegen fehlende Implementierung.

Der genaue Vertrags-Snapshot ist als [JSON-Datei](https://raw.githubusercontent.com/wiki/TeutonStudio/copycat-architect/reference/graph-models-2026-11-08.json) verfügbar. SHA-256: `4cff87c95490eecb6c86f9c559b632586c7f17e68c8626747913ae6373513dfd`. Er dokumentiert die lokale Arbeitskopie, nicht nur deren letzten Git-Commit.

Die Mod- und Browserprüfungen sind getrennte Prüfumfänge. Frühere Spielprüfungen stehen im lokalen Prüfprotokoll; der veröffentlichte Browserstand wird vor der Bereitstellung durch seinen GitHub-Actions-Workflow gebaut und getestet. Eine neue Spiel-/Clientabnahme wird durch diese Wiki-Überarbeitung nicht behauptet.

## Wichtige Unterschiede bei Kosten und Darstellung

Die lokale Mod berechnet Zink anhand des belegten Volumens; ein Vollblock benötigt 256 mB. Der ältere gemeinsame Browser-Rechenkern verwendet seine bisherigen Quellen-/Rezept-Zinkeinheiten. Seine Zahlen sind kein verbindlicher Materialbedarf für den neuesten lokalen Modstand. Zum Bauen immer den tatsächlichen Bedarf des verwendeten Spielbuilds prüfen.

Der Browser zeigt eine farbige Geometrievorschau. Im Spiel gelten die echten Materialien, Kollisionsformen, Baurechte, Inventare und verfügbaren Bibliotheken. Die Browser-Schalter und Größenregler werden nicht als bereits identische Minecraft-Bedienelemente dargestellt.
