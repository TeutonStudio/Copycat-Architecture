# Handelshof Grüne Gasse im Spiel

**Im Minecraft-Client über den Architektentisch gebaut und nach vollständigem Neustart bestätigt:** 4289 platzierte Positionen, davon 3887 Copycat-Bauteile und 402 native Blöcke. Speichern, Kreativbau, die vollständige Bauprüfung und die Neustartprüfung sind erfolgreich abgeschlossen.

## Spielstand und Start

Weltname: **Handelshof Gruene Gasse**.

Der fertige [Spielstand](<../../run/saves/Handelshof Gruene Gasse>) liegt unter `run/saves/Handelshof Gruene Gasse`. Die unabhängige Prüfwelt bleibt unter `build/commerce-quarter-client/saves/Handelshof Gruene Gasse` erhalten. Die lokale Vorlagenwelt `run/saves/Architect Cartridge Smoke` bleibt unverändert.

Folgender Befehl öffnet den fertigen Spielstand ohne automatischen Neubau:

```sh
./gradlew runClient -PcommerceQuarterOpen --offline
```

Zum Übertragen: [Handelshof-Gruene-Gasse-Minecraft.zip](../../build/exports/Handelshof-Gruene-Gasse-Minecraft.zip). Das geprüfte Archiv enthält den vollständigen Spielstand.

## Bau über den echten Architektentisch

Der native Prüflauf hat den platzierten Architektentisch geöffnet und die tatsächlichen Editor-Schaltflächen **Speichern** und **Kreativ bauen** verwendet. Der Speichervorgang hat die bearbeitbare Version `commerce_quarter@2` erzeugt. Formen, Module und Gebäudegraph liegen in der gespeicherten Bibliothek; das Gebäude wurde durch die Kreativbauaktion des Tisches in der Welt errichtet.

Die serverseitige Prüfung hat jede gebaute Position mit dem nativen Plan verglichen: Geometrie, Materialien, freie Raum- und Treppenbereiche sowie die Besitzzuordnung der Copycat-Bauteile stimmen überein. Der Client hat danach sämtliche Gebäudegeometrie erhalten, fünf Weltansichten aufgenommen und den gebundenen Tisch samt Gebäudegraph und Grundriss erneut geöffnet.

Nachweise aus dem tatsächlichen [Bauprotokoll](in-game/create-validation.log):

| Prüfschritt | Ergebnis |
| --- | --- |
| Speichern im Tisch-Editor | `TABLE SAVE PASSED`, `commerce_quarter@2` |
| Kreativbau durch den Tisch | `NATIVE BUILD PASSED`, 4289 Blöcke aktualisiert |
| Welt, Materialien, freie Räume und Treppe | `WORLD VERIFIED`, 3887 Copycats und 402 native Blöcke |
| Clientansichten und erneut geöffneter Editor | `CLIENT PASSED` |
| Vollständiger Neustart mit erneutem Laden | `RELOAD PASSED` |

Ein eigener zweiter Client-/Serverprozess hat den gespeicherten Spielstand vollständig neu geladen. Die [Neustartprüfung](in-game/reload-validation.log) bestätigt erneut die exakte Weltgeometrie, die Bibliothek, die Tischbindung und den geöffneten Gebäudegraphen. Die Aufnahmen nach dem Neustart dokumentieren den erhaltenen Bau und den wieder geöffneten Architektentisch.

## Koordinaten und gespeicherter Entwurf

Die Werte stammen aus der tatsächlich erzeugten [Weltmetadatei](in-game/world-metadata.json), ursprünglich `commerce-quarter-test.json` im Spielstand:

| Feld | Wert |
| --- | --- |
| Gebäudeursprung (X / Y / Z) | `56 / 80 / 56` |
| Architektentisch (X / Y / Z) | `71 / 80 / 50` |
| Gespeicherter Gebäudeschlüssel | `commerce_quarter@2` |
| Positionen im nativen Plan | `4289` |

## Echte Minecraft-Aufnahmen

Alle zehn folgenden Bilder wurden direkt im laufenden Minecraft-Client mit 1600 × 900 Pixeln aufgenommen. Die Dateien zeigen dieselbe gebaute Welt und denselben Architektentisch; die letzten beiden Aufnahmen stammen aus dem unabhängigen Neustartlauf.

| Ansicht | Bilddatei |
| --- | --- |
| Straße | [exterior-street.png](in-game/exterior-street.png) |
| Hof | [exterior-courtyard.png](in-game/exterior-courtyard.png) |
| Gasse zwischen den Flügeln | [lane.png](in-game/lane.png) |
| Gasthaus innen | [interior-inn.png](in-game/interior-inn.png) |
| Atelier innen | [interior-atelier.png](in-game/interior-atelier.png) |
| Architektentisch vor dem Bau | [table-before-build.png](in-game/table-before-build.png) |
| Gebäudegraph am gebundenen Tisch | [table-graph.png](in-game/table-graph.png) |
| Grundriss am gebundenen Tisch | [table-floorplan.png](in-game/table-floorplan.png) |
| Spielwelt nach vollständigem Neustart | [reloaded-world.png](in-game/reloaded-world.png) |
| Architektentisch nach vollständigem Neustart | [reloaded-table.png](in-game/reloaded-table.png) |

## Prüflauf wiederholen

Die Vorbereitung erhält eine bereits vorhandene isolierte Welt. Ein erneuter Bauprüflauf bearbeitet ausschließlich diese Testwelt.

```sh
python3 tools/prepare_commerce_quarter_world.py
./gradlew -I tools/commerce-quarter-validation.init.gradle runClient -PcommerceQuarterSmoke --offline
./gradlew -I tools/commerce-quarter-validation.init.gradle runClient -PcommerceQuarterVerify --offline
```

Nur beim Übertragen an ein anderes Spielerprofil: Tisch- und Bibliotheksbesitz sind an die gespeicherte Spieler-UUID gebunden. Das lokale Entwicklungsprofil ist in den Metadaten als `owner: 380df991-f603-344c-a090-369bad2a924a` gespeichert. Bei einem Profilwechsel muss die Benutzerzuordnung entsprechend übernommen oder der Entwurf im eigenen Profil importiert werden.
