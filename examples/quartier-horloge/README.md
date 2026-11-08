# Quartier de l’Horloge – Galerie und Uhrturm

Eine neue Gebäudeplanung auf Grundlage des Vorschaubildes von [Quartier de L’horloge von Kfc_dilleur](https://createmod.com/de/schematics/quartier-de-lhorloge). Die breite Industriehalle, der schlanke Uhrturm und zwei rückwärtige Häuser bleiben als Ensemble erkennbar. Die ursprüngliche Schematic wurde nicht übernommen oder vermessen.

Der Entwurf nutzt den Gebäudegraphen von Copycat Architect: wiederverwendbare Copycat-Formen, Fassadenanordnungen, Räume, Geschosse, vier unabhängige Dachkörper und vier Treppeninstanzen. Alle Quelldateien dieses Beispiels liegen in diesem Ordner.

## Entwurf

- **Restaurant:** 15 × 23 Blöcke Innenmaß, zwei Ebenen mit jeweils fünf Blöcken Raumhöhe. Ein 5 × 17 Blöcke großer Luftraum öffnet die Halle zur Galerie. Die seitlichen Galerien und der hintere Umlauf besitzen geometrisch ausgeführte Geländer.
- **Uhrturm:** 7 × 7 Blöcke Innenmaß mit fünf Ebenen, einer durchgehenden Treppenverbindung und Zifferblättern auf vier Seiten. Ein steiles Kupferwalmdach betont die Vertikale.
- **Wohnen:** zwei zweigeschossige Häuser mit jeweils 9 × 11 Blöcken Innenmaß und eigenen Treppen. Dazwischen liegt ein gepflasterter Hof.
- **Fassaden:** dünne Wand- und Glasschichten, zurückgesetzte Scheiben, schmale Fensterteilungen, vorspringende Werksteinleisten und mehrteilige Bogenfenster. Die Rundungen entstehen aus rationalen Facetten; sie sind nicht auf die Treppenform ganzer Minecraft-Blöcke beschränkt.
- **Dächer:** ein hohes Satteldach für die Halle, zwei Satteldächer für die Wohnhäuser und ein steiles Walmdach für den Turm. Die Dachhaut verwendet tatsächlich geneigte Flächen. Das Hallendach nutzt die einfachere durchgehende 1:1-Neigung, damit die exakte Materialzerlegung des aktuellen Compilers praktisch ausführbar bleibt.

Die Palette verbindet Ziegel, hellen Sandstein, dunkles Holz, Fichtenböden, Glas, Tiefenschiefer und Kupfer. Gold dient im gespeicherten Plan als warmer Metallakzent.

## Ansichten

Die drei **KI-Gestaltungsstudien** zeigen Materialwirkung, Atmosphäre und eine mögliche Einrichtung. Sie wurden mit dem eingebauten Imagegen-Werkzeug erstellt:

- [Außenperspektive](views/concept-exterior.png)
- [Hofperspektive](views/concept-courtyard.png)
- [Restaurant mit Galerie](views/concept-restaurant-gallery.png)

Diese Studien enthalten weitergehende Details wie Gauben, Schornsteine, eine Arkadenverbindung, dekorative Dachkurven und Möblierung. Diese Details gehören nicht sämtlich zur gespeicherten Graphgeometrie. Die genaue Geometrie zeigen die separat erzeugten **Blender-Planansichten**; sie sind Visualisierungen des kompilierten Plans, keine Minecraft-Screenshots. Alle Bilder und die Kameradaten liegen in [views/](views/). Die [Imagegen-Prompts](views/concept-prompts.json) sind ebenfalls gespeichert.

- [Grundrisse EG und Galerie](views/floorplans.png), [zoombare SVG-Fassung](views/floorplans.svg)
- [Vollständiger Gebäudegraph als zoombare SVG](views/building-graph.svg)

Die tatsächlichen Räume bleiben für die spätere Einrichtung offen. Das Zifferblatt ist ein geometrisches Gestaltungselement; ein bewegliches Create-Uhrwerk wurde nicht eingebaut.

## Dateien und Import

- [building.json](building.json): gemeinsamer bearbeitbarer Gebäudegraph des gesamten Ensembles.
- [forms/](forms/): einzeln importierbare Copycat-Formen einschließlich Bogen- und Zifferblattfeldern.
- [modules/](modules/): Fassadenmodule des Turms.
- [library.json](library.json): vollständiges Quellenpaket mit festen Versionsreferenzen für Reproduktion und Compilerprüfung.
- [design-intent.json](design-intent.json): Maße, Raumlagen und Treppeninstanzen.
- [reference/source.json](reference/source.json): Herkunft der visuellen Vorlage.

Der Editor vergibt beim Import neue IDs. Deshalb zuerst die Formen importieren und speichern, anschließend die Turmmodule mit den gespeicherten Formen verbinden, dann das Gebäude importieren und dessen Formen-/Modulreferenzen zuordnen. Die Namen beginnen mit `Horloge/`. `library.json` ist ein reproduzierbares Gesamtpaket; es ist kein Ein-Klick-Bibliotheksimport. Der Entwurf wurde nicht in einen Minecraft-Spielstand eingebaut.

## Reproduzieren

Aus dem Projektstamm:

```sh
python3 examples/quartier-horloge/source/generate_quartier.py
bash examples/quartier-horloge/source/compile_and_export.sh
python3 examples/quartier-horloge/render_views.py --final
python3 examples/quartier-horloge/draw_graph.py
python3 examples/quartier-horloge/draw_plan.py
```

Der Compilerhelfer verwendet die lokal gebauten Domain-Klassen des Mods sowie die vorhandenen Kotlin-/Gson-Abhängigkeiten. Der Renderer benötigt Blender und überträgt die tatsächlichen Materialflächen des Plans nach Blender; Minecraft-Y wird zur vertikalen Blender-Z-Achse. Farben, Glas, Licht und Hintergrund werden für die Darstellung festgelegt. Auf dem lokalen System erzeugt der Launcher bei Bedarf eine kompatible Farbprofilkopie im Ausgabeordner, ohne die Blender-Installation zu verändern.

Der Graph und die Quellen lassen sich unabhängig von den KI-Studien bearbeiten und erneut rendern.
