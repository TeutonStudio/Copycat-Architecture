# Entwicklungsstand und Kompatibilität

Stand: **13. November 2026 · Mod und Graphstudio 0.6.1**.

Mod und Browser werden aus demselben Kotlin-Fachkern und demselben Knotenvertrag gebaut.
Alle **zehn Form- und 36 Gebäudeknotentypen** stehen in beiden Editoren zur Verfügung.
Dazu gehören Copycat-/Segmentoperationen, Grundrisse, Anordnungen, semantische Räume,
Fassaden, Dachebenen, Treppenhäuser, Schächte, Außenanlagen, Erker und Gauben.

| Dokument | Unterstützte Schemata |
| --- | --- |
| Form | 1–4 |
| Formgraph | 1–3 |
| Gebäude | 1–2, für den Browseraustausch mit Graph |
| Gebäudegraph | 1–4 |

Native Knotengruppen mit benannten Schnittstellen und gemeinsamen Instanzdefinitionen,
Architektentisch-Projektion sowie exakte Geometrie und Dachvorlagen bleiben erhalten.
Die Oberfläche bearbeitet zusätzliche Eigenschaften unter **Knoteneinstellungen**.
Zusammengehörige Änderungen werden gemeinsam übernommen und nach den Modregeln geprüft.
Ältere Graphen behalten ihren Raumaufbau; neue Entwürfe verwenden den expliziten Aufbau.

## Austauschprüfung

Der Browser-Codec wird aus 52 nativen Datenklassen erzeugt. Die CI prüft den JVM-Fachkern,
alle Browserregressionen und sieben gerichtete Dokumentfälle **JVM → Browser → JVM**.
Dazu gehören eine rationale Diagonalspiegelung, eine Dachvorlage, ein leerer Copycat,
eine benannte Gruppe, ein expliziter Grundriss mit Projektion, Erker/Gauben und das T-Haus.
Zusätzlich werden alle nativen Form- und Gebäudeknotenfabriken durch den Browsercodec gelesen.

Das [öffentliche Manifest](https://teutonstudio.github.io/Copycat-Architecture/compatibility.json)
nennt Release, Knotentypen, Schemata und SHA-256-Fingerabdrücke von Fachkern und Katalog.
Die [historische Fehlerprüfung](../GRAPH_COMPATIBILITY.md) dokumentiert die zuvor
auseinanderentwickelten Stände und die Behebung.

## Bibliotheken und Darstellung

Gebäudeexporte enthalten Referenzen wie `ID@Version`. Die entsprechenden Form- und
Modulversionen müssen im Spiel vorhanden sein. Persönliche Browsergraphen und
Graphstudio-Sicherungen bewahren die verwendete Referenzbibliothek mit auf.

Die 3D-Vorschau verwendet Materialfarben oder Texturvorschauen. Exakte Schrägflächen
werden konservativ im 16er-Raster dargestellt; die exportierten rationalen Ebenen
bleiben vollständig erhalten. Minecraft-Spezialrenderer, exakte Modell-UVs, CTM und
Animationen werden nicht vollständig nachgebildet. Bauaufträge und Inventare werden
weiterhin im Spiel ausgeführt.


Ein- und Ausgangstypen werden direkt aus den nativen Anschlüssen übernommen.
Die Paritätsprüfung vergleicht alle 72 Gebäudeknotenvarianten und sämtliche
Inspektorfelder einschließlich Typen, Auswahlwerten und Grenzen. Referenzfelder
bieten passende native Quellknoten an. Abgeleitete Maße öffnen ihre gemeinsame
Quelle. Benannte Gruppenschnittstellen verwenden denselben Adapter wie der
Architektentisch; Eingänge, Ausgänge, Typen und Bindungen lassen sich bearbeiten.
