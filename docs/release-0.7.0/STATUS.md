# 0.7.0 – Entwicklungsstand und offene Freigaben

Die Versionsangaben bleiben bis zur vollständigen Abnahme auf dem bisherigen
Stand. Es gibt kein 0.7.0-Release; die Issues bleiben offen.

| Bereich | Stand |
| --- | --- |
| #11 Wandsegment-Raster | Implementiert; ursprüngliche Prüfungen und Browserregression bestehen. |
| #12 Anzahl und Maße | Implementiert; Fachtests und Browserregression bestehen. |
| CC-Unterdialoge | Gemeinsamer CC-Rahmen einschließlich neuer Referenz- und Versionsdialoge. |
| #13 Untergraphen | Versionen, Referenzen, mehrere Ausgänge, Bibliothek, Pakete und Druck-/Bauübernahme implementiert und geprüft; vollständige Browser-Codecs und alle Bearbeitungs-/Fehlerabläufe noch nicht abgenommen. |
| #14 Züge | Noch nicht implementiert; Create-Bahnhofsmontage und Referenzzug fehlen. |
| #15 Bewegungsmittel | Noch nicht implementiert; optionale Adapter und echte Fahrzeugmontage fehlen. |
| #16 Contraptions | Noch nicht implementiert; Mechanismusadapter und Referenzaufbauten fehlen. |
| Veröffentlichung | Gesperrt bis zur vollständigen Abnahme aller sechs Issues. |

Nächste Umsetzungsschritte bleiben in der vereinbarten Reihenfolge:

1. #13 vervollständigen: alle nativen Daten im Browser verlustfrei lesen,
   bearbeiten und exportieren; vollständige Parameter-/Schnittstellenbedienung,
   fehlende Referenzen und Reparaturpfade einschließlich Speichern prüfen.
2. Gemeinsamen technischen Vertrag und nachgewiesene, optional ladbare Adapter
   schaffen; die vereinbarten Zusatzmod-Versionen samt Prüfsummen festhalten.
3. #14, #15 und #16 mit tatsächlicher serverseitiger Montage und getrennten
   Weltaktionen umsetzen; Referenzaufbauten und Kombinationsmatrix prüfen.
4. Alle fünf Modi und alte Bauabläufe aus dem endgültigen Commit abnehmen.
5. Erst danach Version 0.7.0 setzen, Mod und Graphstudio gemeinsam bauen,
   Release mit Prüfsumme und Prüfbelegen veröffentlichen und Issues schließen.

Prüfdetails und Aufnahmen stehen in [VALIDATION.md](../VALIDATION.md).


Geprüft sind 563 Fachtests (561 bestanden, zwei optionale Snapshot-Skips),
beide getrennten GameTest-Läufe mit jeweils 64 bestandenen erforderlichen Tests,
echte Turtle-Bauten mit 603 beziehungsweise 1115 Platzierungen, native
Referenzdialoge in beiden GUI-Größen und die bisherigen sowie neuen
Browserabläufe auf Desktop, Tablet und Mobilgerät.
