# Entwicklungsstand und Kompatibilität

> **Stand dieses Entwicklungszweigs:** Diese Anleitung beschreibt die vollständige Überarbeitung mit Vorschau-Auswahldialog, allen sechs Copycat- und Segmentoperationen sowie dem 2D-Anordnungsraster. Der Browser unterstützt hier 10 Form- und 21 Gebäudeknotentypen. Die Änderungen sind noch nicht in master integriert oder im Live-Graphstudio bereitgestellt. Das Modrelease 0.6.0 bleibt unverändert.

Abgleich vom **9. November 2026**. Der Mod-Build und die Browseroberfläche haben unterschiedliche Versionsnummern und unterschiedliche Bedienmöglichkeiten:

| Stand | Vorhanden | Grenzen |
| --- | --- | --- |
| Veröffentlichtes Modrelease **0.6.0** | 10 Formknotentypen, 24 Gebäudeknotentypen; gespeicherte Quellen, Spiegelungen und Drehungen, räumliche Segmentanordnungen, semantische Räume, Fassaden, Dachebenen, Schächte und Außenanlagen | Enthält die acht neuen booleschen Gebäude-Knoten für Copycats und Segmente noch nicht. |
| Aktueller gemeinsamer Fachkern | 10 Formknotentypen und **32 Gebäudeknotentypen**, einschließlich Vereinigung, Schnitt, Differenz und symmetrischer Differenz für Copycat- und Segmentwerte | Für diese neuen Operator-IDs ist beim Spielimport ein Mod-Build mit der Erweiterung erforderlich. |
| **Graphstudio 0.4** | **10 Formknotentypen und 21 Gebäudeknotentypen**; Copycat-Auswahldialog mit Geometrievorschauen für Grundformen und gespeicherte Formen im Form- und Gebäudegraphen; alle sechs Operationen für Copycats und Segmente; Graph-/Selektionsvorschau, Blockrahmen und hB-Achsen, Inspektor, JSON-Austausch, persönliche Browserbibliotheken | Segmentlayout-, Raumlayout-, Schacht- und Garten-/Wegknoten sowie native Gruppen und exakte Geometrieimporte sind nicht im Browser enthalten. Die Materialbibliothek zeigt Faithful-Texturen und verlinkbare Mod-Ressourcen; eigene Modell-Loader werden nicht ausgeführt. |

## Operationen im Gebäudegraphen

| Operation | Copycat-Wert | Segmentwert |
| --- | --- | --- |
| Rotieren | `COPYCAT_ROTATE` | `SEGMENT_ROTATE` |
| Spiegeln | `COPYCAT_MIRROR` | `SEGMENT_MIRROR` |
| Vereinigung | `COPYCAT_UNION` | `SEGMENT_UNION` |
| Schnitt | `COPYCAT_INTERSECTION` | `SEGMENT_INTERSECTION` |
| Differenz A − B | `COPYCAT_DIFFERENCE` | `SEGMENT_DIFFERENCE` |
| Symmetrische Differenz | `COPYCAT_SYMMETRIC_DIFFERENCE` | `SEGMENT_SYMMETRIC_DIFFERENCE` |

Alle zwölf Kombinationen sind in der aktuellen Browseroberfläche auswählbar. Die Ausgänge bleiben mit ihrer jeweiligen Wertfamilie verbindbar. Rotation und Spiegelung verwenden im Browser X/Y/Z; diagonale Spiegelebenen benötigen den nativen Editor. Die [Graphstudio-Anleitung](Graphstudio.md) beschreibt gespeicherte Quellen, Eingänge und Materialmodi.

Teilweise geschnittene normale Vollblöcke und semantische Segmentmetadaten werden bei booleschen Segmentoperationen ausdrücklich abgewiesen. Die [Operatorreferenz](Geplante-Segmentoperatoren.md) erklärt diese Grenzen.

## Dateiformate

| Bereich | Aktueller nativer Fachkern | Graphstudio 0.4 |
| --- | --- | --- |
| Formdokument | Schema 1–4 | Schema 1–3, Export als Schema 3 |
| Formgraph | Schema 1–3 | Schema 1–2 |
| Gebäudedokument | Schema 1–2 | Graphdokumente, Export als Schema 2 |
| Gebäudegraph | Schema 1–3 | Schema 1 |

Das Schema allein genügt nicht: Eine Datei muss auch ausschließlich unterstützte Knotentypen und Eigenschaften enthalten. Im Browser nicht unterstützte Typen, exakte native Geometriedaten und Einstellungen werden abgewiesen. Umgekehrt können Browserdateien im passenden Spielstand geöffnet werden; referenzierte Bibliotheksversionen müssen vorhanden sein. Ältere Graphen werden nicht still in den neuesten Raumaufbau umgewandelt.

Eine im Copycat-Referenzknoten geladene Form wird als feste Version in der **Referenzbibliothek des Browserentwurfs** aufbewahrt. Benannte Browsergraphen und Graphstudio-Sicherungen enthalten diese Bibliothek. Der normale Minecraft-Gebäudeexport enthält weiterhin nur Referenzen wie `ID@Version`; er bündelt keine Formbibliothek und installiert sie nicht im Spiel.

## Gemeinsamer Vertrag und Prüfungen

Der [Knotenvertrag im Repository](https://github.com/TeutonStudio/copycat-architect/blob/master/src/main/resources/assets/copycat_architect/graph-models.json) legt Knotentypen, Anschlüsse, Standardwerte und Felder für Mod und Browser fest. Der zu dieser Referenz gehörende [Wiki-Snapshot](https://raw.githubusercontent.com/wiki/TeutonStudio/copycat-architect/reference/graph-models-2026-11-08.json) enthält denselben Vertrag. Die Browseroberfläche gibt nur die Typen und Felder frei, die ihr Parser und Exporter vollständig unterstützen.

Die Knotenreferenz beschreibt auch native Funktionen, die im Browser fehlen. Ein implementierter Knoten kann unvollständige Eingänge besitzen oder im Gebäudekontext unzulässig sein. Ein gültiger Typvertrag ersetzt deshalb keine erfolgreiche Ergebnisberechnung.

Der Graphstudio-Workflow prüft den gemeinsamen Fachkern mit JVM-Tests sowie die Browseradapter und erstellt den Produktionsbuild vor der Bereitstellung. Das ersetzt keine Abnahme des vollständigen Minecraft-Clients und veröffentlicht kein neues Modrelease.

## Kosten und Darstellung

Mod und Browser verwenden die gemeinsame Volumenberechnung des Fachkerns: Ein voller Copycat-Block benötigt **256 mB** Zink; Überlappungen werden im belegten Volumen einmal gezählt. Für den tatsächlichen Bau gelten der im Spiel verwendete Build, seine Materialpalette und die verfügbaren Bibliotheken.

Die Browseransicht zeigt Geometrie mit Materialfarben oder Texturvorschauen. Minecraft-Spezialrenderer, exakte Modell-UVs, CTM, Animationen und weitere Mod-Effekte werden nicht vollständig nachgebildet. Baurechte, Inventare und die Ausführung des Bauauftrags bleiben Aufgaben des Spiels.
