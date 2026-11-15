# Graphstudio-Transformationen auf Basis 0.6.4

Der Organisationseditor liegt in `TeutonStudio-Organisation/graphstudio/adapters/copycat/web`.
Dieser Fachkern wird für den Organisationseditor auf dem Branch `codex/graphstudio-core-064` veröffentlicht. Der Editor pinnt den geprüften Commit ausdrücklich. Der neuere Mod-Hauptzweig und bestehende Release-JARs werden dadurch nicht ersetzt.

## Vertrag

Neue Formgraphen verwenden bei diesen Funktionen `graph.schema: 4`, Gebäudegraphen `graph.schema: 5`.
Die äußeren Dokumentformate bleiben unverändert. Ohne neue Funktionen bleiben die bisherigen Graphschemas und Ergebnisse erhalten.
Neue optionale DTO-Felder fehlen in alten Dokumenten und werden als Standardwerte gelesen.

Jeder Knoten mit Copycat-Ausgang kann eine optionale `copycatTransforms`-Liste besitzen.
Sie läuft nach der bisherigen Quellen-/Operatorauswertung. Reihenfolge und Aktivierung sind pro Knoteninstanz gespeichert.
Auch Instanzen derselben Knotengruppendefinition behalten ihre eigenen Listen; andere Definitionseigenschaften werden weiter gemeinsam bearbeitet.
Gespeicherte Formen und andere Referenzinstanzen werden nicht verändert.

```json
{
  "copycatTransforms": [
    {"kind":"ROTATE","enabled":true,"axis":"Y","quarterTurns":1,"pivot":{"x":8,"y":8,"z":8}},
    {"kind":"MIRROR","enabled":false,"plane":"X"},
    {"kind":"POSITION","enabled":true,"offset":{"x":1,"y":0,"z":0}}
  ]
}
```

Ein Block misst 16 hB. Rotation hat 90°-Schritte um X/Y/Z und standardmäßig den Drehpunkt `(8,8,8)`.
Positionen sind relative ganzzahlige hB-Vektoren. Spiegelungen unterstützen sämtliche bestehenden Fachkernebenen.
Nach jedem aktiven Schritt prüft derselbe exakte Auswerter die Blockgrenze. Fehler bleiben reparierbare Knotendiagnosen; es wird weder abgeschnitten noch auf Nachbarblöcke verteilt.
Materialgruppen, exakte Ausschnitte und Herkunfts-/Rezeptdaten bleiben erhalten.

## Typisierte Knoten und Editoraktionen

Formgraphen ergänzen `POSITION`, `MIRRORING` und `TRANSLATE`; Rotieren und Spiegeln besitzen optionale typisierte Parametereingänge.
Gebäudegraphen ergänzen `POSITION` und `COPYCAT_POSITION` mit `positionValue`; Rotation und Spiegelung nutzen die vorhandenen Wertanschlüsse.
Ohne verbundenen Parameter gelten die Inspektorwerte. `rotationPivot` und `translation` sind optionale Knotenfelder.

`GraphDuplicates` ist der gemeinsame native und browserseitige Duplizierbefehl.
Er erhält lokale Werte und genaue Referenzversionen, vergibt neue IDs, versetzt um 32 Graphkoordinaten und verbindet nur intern ausgewählte Knoten neu.
Ergebnis-/Pflichtknoten bleiben ausgeschlossen. Vollständig ausgewählte Gruppen erhalten konsistent remappte Bindungen.
Die Aktion liegt jeweils vollständig in einem Undo-/Redo-Schritt.

## Bibliothek und Vorschau

Gespeicherte Copycats erscheinen über Ordnerreiter je Ebene, globale Suche und höchstens drei Vorschaukacheln pro Zeile.
Einzelklick wählt die genaue Vorschau; Doppelklick oder Übernahme öffnet bei Namensgruppen die separate Versionsliste.
Kachel und große Vorschau verwenden dieselbe genaue Version mit ihren Materialien.

Die Browserbibliothek migriert bestehende Einträge verlustfrei auf Version 1.
Speichern hängt unveränderliche Versionen an; Speichern unter beginnt eine neue Identität.
Graphen, Formen und Knotengruppen verwenden den gemeinsamen Dateidialog mit Typ, Speicherort, Version und vorhandener Vorschau.
Löschen zeigt abhängige Referenzen und sperrt tatsächlich verwendete Versionen. Eingebettete Kopien bleiben erhalten.
JSON-Entwürfe werden getrennt validiert. Abbrechen ändert nichts; Übernahme ändert den aktiven Graphen atomar oder speichert eine neue Bibliotheksversion.
Sicherungsimporte überschreiben keine bestehenden Versionen. Die Revisionsprüfung verhindert das Überschreiben durch konkurrierende Tabs.

Semantische Portfarben, Namen und Symbole kommen aus `graph-models.json`; sie ersetzen keine Typprüfung.
Der Materialdialog folgt dem ausdrücklich bezeichneten Vorschauziel Graph/Selektiert und verwendet native Mengen nach Registry-ID. Zink steht separat.
Sechs Seitenansichten und Einpassen bleiben in einer gemeinsamen, bei geringer Höhe mehrspaltigen Bedienfläche; Informationen scrollen in einem einzigen HUD-Bereich.
