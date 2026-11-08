# Installation und Rezepte

> Stand: **8. November 2026**. Diese Spielanleitung beschreibt die **lokale Mod 0.5.1**. Der kleinere Funktionsumfang des **Graphstudio 0.2** ist im [Entwicklungsstand](Entwicklungsstand.md) getrennt ausgewiesen.

Diese Anleitung gilt für den Entwicklungsstand **Copycat Architect 0.5.1**, **Minecraft 1.21.1**, **NeoForge 21.1.251** und **Java 21**. Alle beteiligten Mods müssen für Minecraft 1.21.1 und den passenden Loader bestimmt sein.

## Benötigte Mods

| Mod | Verwendete Version | Client | Server |
| --- | --- | :---: | :---: |
| Copycat Architect | 0.5.1 | ja | ja |
| Create | 6.0.10 | ja | ja |
| Create: Molten Metals – Neoforged | 1.0.1 | ja | ja |
| Copycats+ | 3.0.9 | ja | ja |
| CC:Tweaked | 1.120.0 | ja | ja |
| KotlinForForge NeoForge | 5.10.0 | ja | ja |
| KilaGraph | 21.1.0.16 | ja | nein |
| LDLib2 | 2.2.44, Variante `all` | ja | nein |

Installiere NeoForge für Minecraft 1.21.1 und lege die Mod-JARs in den jeweiligen `mods`-Ordner. Auch im Einzelspieler werden die Client-Abhängigkeiten benötigt. Auf einem dedizierten Server sind KilaGraph und LDLib2 nicht erforderlich. Copycat Architect bündelt die aufgeführten Mods nicht selbst.

JEI, Jade und Neat sind optionale Hilfsmods. JEI ist zum Nachschlagen von Rezepten nützlich. Create liefert seine Begleitbibliotheken mit; eine zweite Kotlin-Standardbibliothek muss nicht zusätzlich eingebaut werden.

## Aus dem Quellcode bauen

Auf GitHub ist derzeit keine fertige Release-JAR veröffentlicht. Im passenden Quellstand lässt sich die Mod mit installiertem **JDK 21** bauen:

```sh
./gradlew build
```

Unter Windows:

```bat
gradlew.bat build
```

Die fertige Datei heißt im Stand 0.5.1 `build/libs/copycat_architect-0.5.1.jar`. Installiere diese Datei zusammen mit den Abhängigkeiten. Der erste Build benötigt eine Netzwerkverbindung für Gradle und die Modbibliotheken.

Für einen Entwicklungsclient dient `./gradlew runClient`. Dieser Lauf lädt zusätzlich die im Projekt gepinnten Versionen von JEI, Jade und Neat. Er ersetzt die Installation in einem eigenen Modpack nicht.

Die Wiki beschreibt auch Änderungen der aktuellen Arbeitskopie, die im öffentlichen Repository noch fehlen können. Die Versionsnummer allein garantiert daher nicht, dass ein älterer 0.5.1-Build bereits dieselben Menüs enthält. Die exakten Projektkoordinaten stehen in der [Abhängigkeitsmatrix](../DEPENDENCIES.md); maßgeblich sind die Builddatei und die Modmetadaten des verwendeten Quellstands.

## Blockdrucker herstellen

Du benötigst **2 Zinkbarren, 1 Messingbarren und 2 Eisenbarren**. Das Werkbankmuster lautet:

```text
· Z Z
B I ·
· I ·
```

`Z` = Zinkbarren, `B` = Messingbarren, `I` = Eisenbarren, `·` = leer. Die technische Item-ID ist aus Kompatibilitätsgründen weiterhin `copycat_architect:extrusion_gun`. Der Gegenstand heißt im Spiel **Blockdrucker**.

## Architektentisch herstellen

Du benötigst **3 Papier, 4 Eichenbretter und 1 Werkbank**:

```text
P P P
E W E
E · E
```

`P` = Papier, `E` = Eichenbretter, `W` = Werkbank. Die Eichenbretter sind im Rezept ausdrücklich vorgegeben. Der technische Name lautet `copycat_architect:blueprint_table`; ältere Texte nennen ihn auch Bauplantisch.

## Kartuschen herstellen

| Rezept | Ergebnis |
| --- | --- |
| Zwei Papier nebeneinander: `P P` | eine leere, wiederbefüllbare Kartusche |
| Papier – Zinkbarren – Papier: `P Z P` | eine Kartusche mit **144 mB** Zink |
| Leere Kartusche unter dem Create-Ausguss mit 256 mB geschmolzenem Zink | eine volle Standardkartusche mit **256 mB** |

Verwendet wird ausschließlich **`molten_metals:molten_zinc`**. Zum Schmelzen und Befüllen siehe [Kartuschen und Materialien](Kartuschen-und-Materialien.md).

Für das Turtle-Upgrade wird der Blockdrucker selbst ausgerüstet. Ein zusätzliches Spezialrezept ist dafür nicht nötig; die Schritte stehen unter [Turtle-Bau](Turtle-Bau.md).

Weiter zum [ersten Druck](Blockdrucker.md). Bei Startproblemen hilft die [Fehlerhilfe](Fehlerhilfe.md).

[Entwicklungsstand und Kompatibilität](Entwicklungsstand.md) · [Knotenreferenz](Knotenreferenz.md) · [Einheiten](Einheiten-und-Ausrichtung.md)
