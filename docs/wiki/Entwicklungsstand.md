# Entwicklungsstand und Kompatibilität

Abgleich vom **8. November 2026**. Drei Stände sind auseinanderzuhalten:

| Stand | Vorhanden | Grenzen |
| --- | --- | --- |
| Lokale Mod **0.5.1**, aktuelle Arbeitskopie | 10 Formknotentypen, 24 Gebäudeknotentypen, gespeicherte Quellen, Spiegelungen, räumliche Segmentanordnungen, semantische Räume, Fassaden, Dachebenen, Schächte und Außenanlagen | Nicht alle lokalen Änderungen sind im öffentlichen Repository enthalten. Die Versionsnummer allein identifiziert diesen Stand nicht. |
| Veröffentlichtes **Graphstudio 0.3** und sein gemeinsamer öffentlicher Spielcode | 8 Formknotentypen und 9 ältere Gebäudeknotentypen; Graph-/Selektionsvorschau, Blockrahmen und hB-Achsen, Inspektor, JSON-Austausch, persönliche Browserbibliotheken | Noch keine Spiegel-, gespeicherten Form-, Segmentlayout-, Raumlayout-, Schacht- oder Garten-/Wegknoten des lokalen Standes. Materialbibliothek mit Faithful-Texturen und verlinkbaren Mod-Ressourcen; eigene Modell-Loader werden nicht ausgeführt. |
| Geplante Segmentoperatoren | [Vereinigung, Schnitt, Differenz und symmetrische Differenz für Segmente](Geplante-Segmentoperatoren.md) sind als Vertrag beschrieben | Noch keine registrierten, ausführbaren Knoten. Die vorgeschlagenen IDs sind keine gültigen Importwerte. |

## Dateiformate

| Bereich | Aktuelle lokale Mod | Graphstudio 0.3 |
| --- | --- | --- |
| Formdokument | Schema 1–4 | Älteres Format, Export als Schema 3 |
| Formgraph | Schema 1–3 | Schema 1–2 |
| Gebäudedokument | Schema 1–2 | Graphdokumente des älteren Formats, Export als Schema 2 |
| Gebäudegraph | Schema 1–3 | Schema 1 |

Das Schema allein genügt nicht: Eine Datei muss auch ausschließlich unterstützte Knotentypen und Eigenschaften enthalten. Im Browser nicht unterstützte Typen werden abgewiesen. Umgekehrt können ältere Browserdateien im passenden Spielstand geöffnet werden; referenzierte Bibliotheksversionen müssen vorhanden sein. Ältere Graphen werden nicht still in den neuesten Raumaufbau umgewandelt.

## Was als Nachweis gilt

Die Knotenreferenz wurde gegen den lokalen Knotenvertrag und die zugehörigen Inspektor- und Domänenklassen abgeglichen. Ein implementierter Knoten kann trotzdem unvollständige Eingänge besitzen oder im Gebäudekontext unzulässig sein. „Geplant“ bezeichnet dagegen fehlende Implementierung.

Der genaue Vertrags-Snapshot ist als [JSON-Datei](https://raw.githubusercontent.com/wiki/TeutonStudio/copycat-architect/reference/graph-models-2026-11-08.json) verfügbar. SHA-256: `81adda49087650beecb91615b7449490f572d2fc7fef7fc82ad5ce7f0608a069`. Er dokumentiert die lokale Arbeitskopie, nicht nur deren letzten Git-Commit.

Die Mod- und Browserprüfungen sind getrennte Prüfumfänge. Frühere Spielprüfungen stehen im lokalen Prüfprotokoll; der veröffentlichte Browserstand wird vor der Bereitstellung durch seinen GitHub-Actions-Workflow gebaut und getestet. Eine neue Spiel-/Clientabnahme wird durch diese Wiki-Überarbeitung nicht behauptet.

## Wichtige Unterschiede bei Kosten und Darstellung

Die lokale Mod berechnet Zink anhand des belegten Volumens; ein Vollblock benötigt 256 mB. Der ältere gemeinsame Browser-Rechenkern verwendet seine bisherigen Quellen-/Rezept-Zinkeinheiten. Seine Zahlen sind kein verbindlicher Materialbedarf für den neuesten lokalen Modstand. Zum Bauen immer den tatsächlichen Bedarf des verwendeten Spielbuilds prüfen.

Der Browser zeigt eine farbige Geometrievorschau. Im Spiel gelten die echten Materialien, Kollisionsformen, Baurechte, Inventare und verfügbaren Bibliotheken. Die Browser-Schalter und Größenregler werden nicht als bereits identische Minecraft-Bedienelemente dargestellt.
