# Räume aus Segmenten und gemeinsame Wände

Der Architektentisch kann Raumgrenzen, Fensteröffnungen und Bauteilanschlüsse bis zur Auswertung des ganzen Gebäudes erhalten. Die Segmentanordnung arbeitet mit vollständigen Segmenten im Blockraster. Die Copycat-Geometrie innerhalb dieser Segmente behält ihre genaue Form.

## Einen Raum aus Segmenten anlegen

1. Im Gebäudegraphen eine **Raumanordnung** hinzufügen. Der Knotentyp heißt `ROOM_LAYOUT`; die Anordnung innerhalb eines Wandsegments heißt weiterhin `SEGMENT_LAYOUT`.
2. Wand-, Fenster-, Tür- oder Säulensegmente beziehungsweise Muster mit den Eingängen der Raumanordnung verbinden. Diese Eingänge bilden die Segmentpalette.
3. Im Inspektor **Segmente in der Draufsicht anordnen** öffnen und eine Palettequelle auswählen. Ein Klick auf eine freie Rasterzelle platziert eine Instanz. Dieselbe Quelle lässt sich mehrfach verwenden.
4. Platzierte Instanzen auswählen und ziehen. **R** oder Rechtsklick dreht um 90°, **Entf** entfernt die Auswahl. Die mittlere Maustaste verschiebt die Ansicht; das Mausrad zoomt. **Platzierte Segmenteigenschaften** erlaubt genaue X-/Y-/Z-Koordinaten und die Auswahl der Wandregel und des Raumbeitrags.
5. Einen **Raum** anlegen. In **Raumgrenzen und Innenausbau** unter **Raum aus** den Wert **Segmenten** wählen und die Raumanordnung zuweisen. Boden und Decke bleiben eigene Eingänge des Raums.
6. Den Raum mit einem Stockwerk verbinden und dort anordnen. Ein Stockwerk kann dieselbe Raumdefinition mehrmals verwenden. In der allgemeinen Grundrissansicht öffnet ein Klick auf einen Segmentraum dessen Segmentraster; diese Raumfläche wird dort nicht mit dem alten Rechteckwerkzeug bemalt.

Im Raster beendet ein Klick auf eine freie Stelle zunächst eine bestehende Auswahl. Der folgende Klick platziert eine weitere Instanz. **Strg+Z**, **Strg+Y** und **Strg+Umschalt+Z** bearbeiten den lokalen Rasterentwurf. **Übernehmen** übergibt die gesamte Änderung an die normale Undo-/Redo-Historie des Gebäudeeditors; **Abbrechen** verwirft den Rasterentwurf.

Die Instanzposition bezeichnet die minimale Ecke ihres gedrehten Begrenzungskastens. Die helle Kante zeigt die logische Vorderkante des Segments. Beim ungedrehten Wandsegment verläuft diese entlang X; die lokale +Z-Seite weist in den Raum.

### Raumbeitrag, Größe und Höhe

| Raumbeitrag | Verwendung |
|---|---|
| Raumgrenze | Gewöhnliche Wand; beteiligt sich an Umriss und Höhenvorschlag. |
| Fenster / Tür | Unterbricht die logische Raumgrenze nicht, bestimmt aber nicht die automatische Raumhöhe. |
| Tragendes Bauteil | Beispielsweise eine freistehende Säule innerhalb der Anordnung. |
| Dekoration | Geometrie ohne Beitrag zum logischen Umriss oder zur Raumhöhe. |

Breite und Tiefe werden aus der Anordnung ermittelt. Eine nutzbare Innenfläche entsteht, sobald die logische Raumgrenze eindeutig geschlossen ist. Normale Wandsegmente schlagen die Höhe vor; Öffnungen und Dachverlängerungen erhöhen sie nicht. Unter **Eigene Raumhöhe** kann der Vorschlag überschrieben werden. Die Angabe verändert keine profilierten Segmente durch Strecken.

### Offene Räume und fehlende Abschnitte

Eine offene Segmentanordnung ist zulässig. Ohne geschlossenen logischen Umriss werden zunächst weder ein flächiger Boden noch eine flächige Decke erzeugt.

**Fehlende Grenzen vorschlagen** zeigt mögliche Verbindungen. Die Vorschau verändert den Entwurf erst nach einer ausdrücklichen Übernahme:

- **Vorschlag als offene Grenze** schließt den logischen Umriss und markiert die ergänzten Abschnitte als bewusst offen.
- **Vorschlag mit Wand ergänzen** verwendet eine ausgewählte Füllvorlage für die fehlenden Abschnitte.
- Bei mehrdeutigen Vorschlägen die **Raumgrenze zeichnen**: Rasterecken in Reihenfolge anklicken, dann die erste Ecke erneut anklicken oder **Raumgrenze übernehmen** verwenden. Ein Rechtsklick entfernt die letzte Ecke.

Unter **Raumgrenzen bearbeiten** können Grenzkoordinaten eingegeben und virtuelle Abschnitte einzeln auf **Vom Stockwerk erben** oder **Bewusst offen** gestellt werden. Damit erhält ein überdachter Außenraum eine definierte Fläche für Boden und Decke, während seine Seiten offen bleiben.

## Wer erzeugt welche Wand?

Der Raum beschreibt seine logischen Grenzen, platzierte Segmente und seine Innenoberflächen. Bei eingeschalteter gemeinsamer Wandauswertung entscheidet das Stockwerk anhand aller Raumgrenzen über Außenwände und gemeinsame Trennwände.

| Wandregel | Verhalten |
|---|---|
| Vom Stockwerk erben (`AUTO`) | Das Stockwerk liefert den gemeinsamen Wandkörper beziehungsweise die Außenfassade. Das Raumsegment liefert seine raumseitige Oberfläche. |
| Eigenes Segment (`EXPLICIT`) | Das vollständig platzierte Segment bleibt maßgeblich, einschließlich seiner Tiefe und Profile. Es wird in diesem Abschnitt nicht durch eine Standardfassade ersetzt. |
| Bewusst offen (`OPEN`) | Der Abschnitt bleibt ohne Wand; eine Fassadenvorlage darf ihn nicht schließen. |

Neu platzierte Instanzen beginnen mit **Eigenes Segment**. So bleiben vorhandene Fensterrahmen und räumliche Profile vollständig erhalten. Für einen Raum, dessen innere Wandseite vom Raum und dessen äußere Seite vom Stockwerk kommen soll, die betreffenden Instanzen ausdrücklich auf **Vom Stockwerk erben** stellen.

Im Stockwerk **Fassade und gemeinsame Wände** öffnen und **Wände im Stockwerk zusammenführen** einschalten. **Außenfassade** bestimmt die äußeren Standardabschnitte. **Wand zum Ergänzen** dient als Trennwandstandard; bei leerer Auswahl wird dafür die Fassadenvorlage verwendet. Im Raum lassen sich ergänzend eine **Innere Oberfläche** und eine **Trennwandvorlage** zum Schließen eigener Lücken zuweisen.

Boden und Decke gehören weiterhin zum Raum. Ein Wandanschluss einer Decke verlängert deren Geometrie, verändert jedoch weder Raumgrundriss noch Bodenfläche.

## Fenster und Türen durch alle Wandlagen

Am Segment beziehungsweise Muster **Öffnungen und Säulenabschlüsse** öffnen und eine **Öffnung hinzufügen**. Position, Breite und Höhe beziehen sich auf das lokale Koordinatensystem dieses Bauteils.

- Ohne **Freie Öffnungsform** entsteht ein rechteckiger Durchbruch.
- Für eine freie Form eine Copycat-Quelle auswählen. Deren genaue XY-Silhouette beschreibt den Querschnitt; Breite und Höhe legen seine Ausdehnung fest. Dreh- und Spiegelknoten für Copycats sind ebenfalls auswählbar.
- **Rahmen / Einsetzsegment** wählt eine Copycat-Quelle für den Einbau, beispielsweise Glas. Die Quelle wird innerhalb des Öffnungsquerschnitts eingesetzt; die **Einsetztiefe für Glas / Rahmen** bestimmt die Anzahl der lokalen Tiefenlagen.
- **Vorhandenes Segment erhalten** eignet sich für ein bereits vollständig modelliertes Fenstersegment: Seine eigenen Rahmen- und Glasteile bleiben stehen, während die anderen Wandlagen durchbrochen werden. Bei einer zunächst massiven Wand diese Option ausschalten.
- Für einen offenen Türdurchgang keine Einsetzquelle wählen und die vorhandene massive Geometrie nicht erhalten.

Das Stockwerk legt zuerst Wandkörper, Innenoberflächen und Fassade fest. Anschließend wird die Öffnung durch sämtliche betroffenen Wandlagen geschnitten und die Einsetzgeometrie ergänzt. Ein Wechsel der Fassadenvorlage verschließt das Fenster deshalb nicht wieder. Identische Einbauten werden zusammengeführt; widersprüchliche Einbauten melden einen Konflikt.

Öffnungsaufträge bleiben beim Zusammensetzen, Wiederholen, Drehen und Spiegeln der Segmente erhalten. Ein Luftblock allein enthält diese Information nicht.

## Säulen bis an die Dachunterseite

Die Einstellung gehört an das ursprüngliche Säulen-Teilsegment. Der Raum muss die Säule später nicht aus einer vereinigten Wand zurückerkennen.

1. Am Säulensegment **Öffnungen und Säulenabschlüsse** öffnen und einen **Säulenabschluss hinzufügen**.
2. Unter **Wiederholbarer Schaft ab Y** und **… bis Y (exklusiv)** den wiederholbaren Höhenabschnitt festlegen. Geometrie darunter ist der Sockel; Geometrie darüber bildet den oberen Abschluss.
3. Optional ein eigenes **Schaftprofil** wählen. Seine Breite und Tiefe müssen zur Säule passen. Bei leerer Auswahl wird der markierte Abschnitt des Säulensegments selbst wiederholt.
4. Ein konkretes **Zieldach** wählen. Gemeint ist ein Dachlevel mit einer ausgewerteten Unterseite.
5. Bei mehreren Einbauten das Ziel im Stockwerk eindeutig bestimmen. Ein optionaler **Abstand** wird in Schritten von 1/16 Block angegeben.

Der Schaft wird wiederholt und über seine gesamte Breite und Tiefe exakt an der Dachunterseite abgeschnitten. Sockel und oberer Abschluss werden gesondert behandelt. Die verlängerte Säule fließt nicht erneut in die Raum- oder Stockwerkhöhe ein; das Dach wandert dadurch nicht mit nach oben.

## Vordach an eine Wand anschließen

Am Deckenplattenknoten beziehungsweise dem als Platte verwendeten Segment **Kante an Wand anschließen** öffnen:

1. Einen **Wandanschluss hinzufügen** und die zu verlängernde Kante auswählen: Norden, Osten, Süden oder Westen.
2. Als **Zielraum der Wand** die Halle beziehungsweise den Raum der Zielwand wählen.
3. Das Ziel im Stockwerk bestimmen und die gewünschte **Wandfläche** wählen. Die Himmelsrichtungen bezeichnen zusammengefasste Wandseiten; bei älteren Räumen sind zusätzlich deren einzelne Wandlauf-IDs auswählbar.
4. Bei wiederholter Verwendung derselben Raumdefinition die konkrete Raumplatzierung in **Ziel im Stockwerk** auswählen. Die Auswahl unterscheidet auch mehrere Verwendungen innerhalb desselben Stockwerks. **Genaue Platzierung** erlaubt alternativ die direkte Eingabe eines Pfads, beispielsweise `storey_0/room:0`: der erste Raumeingang der Stockwerkinstanz `storey_0`. Die Stockwerk-IDs bleiben bei einer Umordnung der Geschosse erhalten.

Die gewählte Kante wird bis zur tatsächlichen Wandoberfläche ergänzt. Profile und Wandtiefe bestimmen den Kontakt. Das Ziel muss über die gesamte Kante auf Höhe der Platte geschlossen vorhanden sein. Ein Fenster, eine fehlende Wand oder eine mehrdeutige Zielauswahl wird als nicht erfüllter Anschluss gemeldet.

Einzelne Segmentvorschauen zeigen die lokale Ausgangsgeometrie und weisen auf ausstehende Anschlüsse hin. Erst die vollständige Gebäudeberechnung kann Dach- und Wandkontakte auflösen.

## Vorhandene Vorlagen bewusst umstellen

Vorhandene Graphen der Schemata 1 und 2 behalten ohne Umstellung ihre bisherige Auswertung. Neue Raum- und Anschlussangaben verwenden Graphschema 3; das übrige Gebäudeformat bleibt kompatibel.

Bei einem Stockwerk aus Räumen **Stockwerk für gemeinsame Wände vorbereiten** verwenden. Die Aktion erstellt für bisherige Raumdefinitionen eigene Entwurfskopien und verbindet nur das gewählte Stockwerk mit diesen Kopien. Die vollständigen vorhandenen Wandrezepte bleiben dabei maßgeblich. Andere Verwendungen der ursprünglichen Räume werden nicht stillschweigend geändert. Verknüpfte Knotengruppen dieses Stockwerks werden für die Umstellung gelöst, damit sie keine Änderungen in andere Stockwerke übertragen.

Diese Vorbereitung erkennt weder Fenster noch Säulen aus fertiger Geometrie. Solche Öffnungen und Anschlüsse müssen ausdrücklich am passenden ursprünglichen Teilsegment ergänzt werden. Der Raummodus bleibt zunächst **Grundriss**; ein Wechsel zu **Segmenten** und das Anlegen einer Raumanordnung sind eigene Arbeitsschritte.

Die Umstellung ist im Editor rückgängig zu machen. **Speichern** legt wie üblich eine neue Bibliotheksversion an; ein tatsächlicher Umbau erfolgt erst über die Bauaktion am Architektentisch. Die ältere Stockwerkvariante, die unmittelbar eigene Wände statt Raumeingänge definiert, wird nicht automatisch in Räume zerlegt.

## Aktuelle Grenzen

- Das Raumraster verwendet ganze Blöcke und 90°-Drehungen. Die Form innerhalb einer Copycat-Zelle bleibt exakt; eine beliebige Drehung kompletter Rauminstanzen gehört nicht zu diesem Modus.
- Raumgrenzen müssen orthogonal sein. Eine offene oder mehrdeutige Anordnung erfindet keine Innenfläche. Eine eigene Grenze wird als ein geschlossener Umriss eingegeben.
- Eine Raumanordnung unterstützt bis zu 256 Instanzen; das Gebäude weiterhin höchstens 96 Graphknoten. X/Z-Koordinaten liegen im Raster von −128 bis +128, Y-Positionen zwischen −64 und +128.
- Fassaden- und Trennwandstandards sind einen Block breit. Ihre Höhe beträgt einen Block oder entspricht der Raumhöhe. Lückenfüller eines Raums müssen einen Block breit sein und zur Raumhöhe passen. Größere profilierte Module werden ausdrücklich platziert und nicht gestreckt.
- Zusätzliche Innenoberflächen verwenden eine Quelle mit einem Block Breite und Tiefe; ihre Höhe beträgt einen Block oder entspricht der Raumhöhe.
- Freie Öffnungen, schräge Säulenenden und genaue Wandkontakte benötigen Copycat-Geometrie. Ein gewöhnlicher Minecraft-Block kann nicht teilweise beschnitten werden.
- Säulen müssen aufrecht stehen; ihr vollständiger Querschnitt muss unter dem ausgewählten Dach liegen. Die Unterseite des Dachs muss ausreichend Platz für Sockel, Schaft und Abschluss lassen.
- Vordachanschlüsse sind horizontale Plattenverlängerungen. Ihr Kontakt muss innerhalb von 128 Blöcken erreichbar und über die gesamte Kante eindeutig geschlossen sein.
- Ein offener Außenraum darf unmittelbar an eine geschlossene Hallenwand angrenzen. Seine offene Seite entfernt die Hallenwand nicht. Überlappende Innenflächen und widersprüchliche Einbauten werden gemeldet.
- Die Innenfläche der einzelnen Raumvorschau berücksichtigt ihre eigenen Wandsegmente. Zusätzliche Wandtiefe aus dem Stockwerk wird erst bei dessen gemeinsamer Auswertung berücksichtigt.

Die vorhandenen Speicher- und Netzwerkgrenzen gelten auch für diese Angaben. Segmentinstanzen, Öffnungsbeschreibungen und Zielbindungen werden gespeichert; daraus berechnete Wandtopologie und fertige Kontaktvolumen bleiben Laufzeitdaten.

## Prüfung vom 7. November 2026

Die Gesamttests umfassen 510 Fälle: 509 bestanden, ein optionaler älterer Weltschnappschusstest wurde übersprungen. Nach den letzten Anschlusskorrekturen bestanden zusätzlich 64 gezielte Tests. Der neue Schnappschusstest prüfte die echte exportierte Lagerhalle: Die bewusste Umstellung von 42 auf 44 Knoten erhielt dieselben 4.390 Baublöcke, 7.524 Luftzellen und Materialzuweisungen.

In einer isolierten Kopie der Vorlagenwelt wurden am tatsächlichen Architektentisch Speichern, Kreativ bauen und erneutes Öffnen ausgeführt. Nach einem vollständigen Clientneustart wurden der gespeicherte Graph, alle 220 Baublöcke, 105 Luftzellen und 220 Besitzmarken erneut geprüft. Der native Raumeditor wurde mit Verschieben, Drehen, Löschen, Grenzzeichnen und Abbrechen eines Entwurfs bedient. Außen-, Innen-, Anschluss- und Editoraufnahmen liegen unter `build/semantic-building-client/screenshots/`.

Alle 40 gesicherten Dateien der Originalwelt blieben nach Prüfsummenvergleich unverändert. Ergebnisse und Bibliotheksexport liegen unter `build/semantic-building-acceptance/`, die isolierte Testkonfiguration unter `tools/semantic-building-validation.init.gradle`.
