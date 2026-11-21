export const nodeCategories = [
  ['sources', 'Quellen und Referenzen', 'Grundformen oder gespeicherte Inhalte als Ausgangspunkt verwenden.'],
  ['copycat', 'Copycats bearbeiten', 'Formen kombinieren, voneinander abziehen oder ausrichten.'],
  ['segments', 'Segmente bearbeiten', 'Bauteile aus Copycats aufbauen und kombinieren.'],
  ['building', 'Gebäude und Räume', 'Bauteile zu Räumen, Geschossen und Gebäuden verbinden.'],
  ['layouts', 'Flächen und Anordnungen', 'Grundrisse und die Verteilung von Bauteilen festlegen.'],
  ['values', 'Werte und Berechnungen', 'Zahlen, Positionen, Drehungen und Spiegelungen an andere Knoten weitergeben.'],
  ['landscape', 'Außenanlagen', 'Gärten und Wege für die Umgebung erstellen.'],
]

// Shared by the minimap and category markers in the node dialog.
export const nodeCategoryColors = Object.freeze({
  sources:'#4db6ac', copycat:'#66b5ff', segments:'#e8b85d', building:'#c59bff',
  layouts:'#f38fbd', values:'#8ecf70', landscape:'#c98d62',
})
export const nodeCategoryColor = kind => nodeCategoryColors[categoryOfNode(kind)]
export const miniMapNodeColor = node => nodeCategoryColor(node.data?.node?.kind)

const descriptions = {
 RESULT:'Den öffentlichen Ergebniswert direkt mit einer Erzeugungsart aufbauen oder einen verbundenen Wert übernehmen.',
 GROUP_INPUT:'Einen benannten, typisierten Eingang mit optionaler Standardquelle für alle Referenzinstanzen dieses Graphen bereitstellen.',
 GRAPH_REFERENCE:'Eine genaue gespeicherte Graphversion mit typisierten Parametereingängen referenzieren.',
  COPYCAT_OPERATOR:'Bestehende Copycat-Geometrie kombinieren, invertieren, drehen, spiegeln oder positionieren.',
  SEGMENT_OPERATOR:'Bestehende Segmentwerte exakt kombinieren und transformieren.',
  POSITION_OPERATOR:'Vorzeichenbehaftete Positionen erzeugen, berechnen und ausrichten.',
  NUMBER_OPERATOR:'Bestehende Ganzzahlwerte erzeugen und verrechnen.',
  RELATION_OPERATOR:'Zahlen vergleichen und ihre Teilbarkeit prüfen.',
  STATEMENT_OPERATOR:'Booleanwerte erzeugen und logisch miteinander verknüpfen.',

  TRANSFORMATION:'Spiegelung, Rotation und Position zu einem Transformation-Wert bündeln. Reihenfolge direkt im Knoten wählbar; Standard: Spiegeln → Rotieren → Positionieren (SRP).',
  COPYCAT_TRANSFORM:'Einen Copycat mit dem angeschlossenen Transformation-Wert spiegeln, rotieren und positionieren. Ohne Wert bleibt er unverändert.',
  SEGMENT_TRANSFORM:'Ein Segment mit dem angeschlossenen Transformation-Wert spiegeln, rotieren und positionieren. Positionen in hB; 16 hB entsprechen einem Block.',
  COPYCAT: 'Eine genaue Version aus der Bibliothek als Referenz einsetzen. Im nächsten Dialog kannst du auch eine Grundform wählen.',
  INPUT: 'Eine Copycat-Grundform erzeugen. Form, Größe und Ausrichtung stellst du im Inspektor ein.',
  SAVED: 'Eine gespeicherte Copycat-Form mit ihren Materialgruppen als Quelle verwenden.',
  SEGMENT: 'Copycats zu einem Wandsegment zusammensetzen oder entlang seiner Achsen wiederholen.',
  ROTATION_SEGMENT: 'Ein Copycat-Profil um eine Achse wiederholen, beispielsweise für eine Säule.',
  PATTERN: 'Mehrere Wandsegmente zu einem wiederholbaren Wandmuster verbinden.',
  AREA: 'Eine rechteckige Rasterfläche mit Breite und Länge für Grundrisse erstellen.',
  SEGMENT_LAYOUT: 'Segmente auf einer Fläche verteilen und ihre Ausrichtung festlegen.',
  ROOM_GEOMETRY: 'Den Grundriss eines Raums aus Flächen und Wandkanten bestimmen.',
  FLOOR: 'Eine Bodenschicht aus Copycats für den angeschlossenen Raum erzeugen.',
  CEILING: 'Eine Deckenschicht aus Copycats für den angeschlossenen Raum erzeugen.',
  ROOM: 'Wände, Boden und Decke zu einem Raum mit bebaubarem Innenraum verbinden.',
  STOREY: 'Räume zu einem Geschoss zusammenfassen und dessen Höhe bestimmen.',
  ROOF_LEVEL: 'Eine einzelne Dachebene mit ihren Dachbauteilen definieren.',
  ROOF_LAYOUT: 'Dachebenen zu einer Dachanordnung verbinden.',
  ROOF: 'Ein Dach aus Copycat-Bauteilen auf dem Gebäude aufbauen.',
  BUILDING: 'Geschosse, Dach und Außenanlagen zu einem Gebäude verbinden.',
  STAIRWELL: 'Einen Treppenschacht mit Stufen, Podesten und Schachtwänden aufbauen.',
  BOOLEAN: 'Einen Wahrheitswert für Bedingungen bereitstellen.',
  INT_COMPARE: 'Zwei Ganzzahlen oder Anzahlen vergleichen; Teilbarkeit prüft A ÷ B ∈ ℤ.',
  BOOLEAN_LOGIC: 'Wahrheitswerte mit UND, ODER, XOR oder NICHT verknüpfen.',
  CONDITIONAL: 'Bei Wahr Wert A, bei Falsch Wert B verwenden.',
  ROOF_TEMPLATE: 'Dachparameter und acht Dachbauteile als wiederverwendbare Vorlage zusammenführen.',
  SERVICE_SHAFT_SEGMENT: 'Ein Installationsschachtsegment mit dynamischer Höhe und Durchbrüchen erzeugen.',
  ELEVATOR_SHAFT_SEGMENT: 'Ein Aufzugssegment mit dynamischer Höhe und geschossbezogenen Türen erzeugen.',
  ELEVATOR_SHAFT: 'Einen Aufzugsschacht mit Öffnungen zwischen den Geschossen vorsehen.',
  SERVICE_SHAFT: 'Einen Installationsschacht für vertikale Leitungswege vorsehen.',
  ROOM_LAYOUT: 'Die Anordnung der Räume und ihrer Wandsegmente festlegen.',
  BAY_WINDOW: 'Einen vorspringenden Erker als Anbau an einer Raumwand erzeugen.',
  DORMER: 'Eine Gaube mit Front, Seiten und Dach in die Dachfläche einsetzen.',
  GARDEN: 'Eine Gartenfläche mit ihrer Bepflanzung und Oberflächenbelegung erstellen.',
  PATH: 'Einen Weg mit Breite, Verlauf und Material für die Außenanlage festlegen.',
  INT: 'Eine vorzeichenbehaftete Ganzzahl bereitstellen.',
  COUNT: 'Eine Anzahl ab 0 bereitstellen. Über die Typauswahl kann die Quelle Ganzzahlen liefern.',
  INT_ABS: 'Den Betrag |x| als Anzahl ausgeben. Die kleinste Ganzzahl meldet einen Überlauf.',
  INT_TO_COUNT: 'Eine Ganzzahl ausdrücklich als Anzahl verwenden. Negative Eingaben melden einen Fehler.',
  INT_REMAINDER: 'Den Divisionsrest berechnen; sein Vorzeichen folgt dem ersten Operanden.',
  POSITION: 'X, Y und Z zu einer Position zusammenführen. Eine Einheit entspricht 1 hB = 1/16 Block.',
  POSITION_X: 'Eine Position in drei getrennte Zahlenausgänge X, Y und Z zerlegen.',
  ROTATION: 'Achse und Vierteldrehung als wiederverwendbaren Rotationswert bereitstellen.',
  MIRRORING: 'Eine Spiegelebene als wiederverwendbaren Wert bereitstellen.',
}
const operations = {
  UNION: 'Die belegten Bereiche der verbundenen Eingänge vereinigen. Ohne Verbindung entsteht ein leerer Copycat.',
  INTERSECTION: 'Den gemeinsamen Bereich der verbundenen Eingänge behalten. Ohne Verbindung entsteht ein Copycat-Vollblock.',
  DIFFERENCE: 'Den zweiten Eingang B vom ersten Eingang A abziehen: A minus B.',
  SYMMETRIC_DIFFERENCE: 'Die Formen vereinigen und ihre gemeinsamen Bereiche entfernen.',
  ROTATE: 'Die Geometrie in 90°-Schritten um X, Y oder Z drehen. Ein Rotationswert kann die Einstellung liefern.',
  MIRROR: 'Die Geometrie an einer gewählten Ebene spiegeln. Ein Spiegelungswert kann die Einstellung liefern.',
  POSITION: 'Das Copycat um einen relativen X/Y/Z-Versatz in hB verschieben.',
}
const calculations = {ADD:'addieren',SUBTRACT:'voneinander abziehen',MULTIPLY:'multiplizieren',DIVIDE:'dividieren',MIN:'auf den kleineren Wert begrenzen',MAX:'auf den größeren Wert begrenzen'}
export function describeCatalogNode(kind) {
  if (descriptions[kind]) return descriptions[kind]
  const calculation = kind.match(/^(INT|POSITION)_(ADD|SUBTRACT|MULTIPLY|DIVIDE|MIN|MAX)$/)
  if(calculation) return `${['ADD','MULTIPLY','MIN','MAX'].includes(calculation[2]) ? 'Zwei bis sechzehn' : 'Zwei'} ${calculation[1] === 'INT' ? 'ganze Zahlen' : 'Positionen komponentenweise'} ${calculations[calculation[2]]}. Das Ergebnis lässt sich mit weiteren Knoten verbinden.`
  if(kind==='SEGMENT_UNION') return 'Die belegten Bereiche aller verbundenen Segmente vereinigen.'
  if(kind==='SEGMENT_INTERSECTION') return 'Nur den gemeinsamen Bereich der angeschlossenen Segmente behalten.'
  const operation = kind.replace(/^(COPYCAT|SEGMENT)_/, '')
  return operations[kind === 'TRANSLATE' ? 'POSITION' : operation] || 'Einen Knoten hinzufügen und seine Eigenschaften im Inspektor einstellen.'
}
export function categoryOfNode(kind = '') {
  if(['COPYCAT','INPUT','SAVED','REFERENCE'].includes(kind)) return 'sources'
  if(['GARDEN','PATH'].includes(kind)) return 'landscape'
  if(/^(COUNT|INT|POSITION)(_|$)/.test(kind) || ['ROTATION','MIRRORING','TRANSFORMATION','VECTOR_SPLIT'].includes(kind)) return 'values'
  if(['AREA','SEGMENT_LAYOUT','ROOM_GEOMETRY','ROOM_LAYOUT','ROOF_LAYOUT'].includes(kind)) return 'layouts'
  if(kind.startsWith('SEGMENT_') || ['SEGMENT','PATTERN','ROTATION_SEGMENT','SERVICE_SHAFT_SEGMENT','ELEVATOR_SHAFT_SEGMENT'].includes(kind)) return 'segments'
  if(kind.startsWith('COPYCAT_') || ['UNION','INTERSECTION','DIFFERENCE','SYMMETRIC_DIFFERENCE','ROTATE','MIRROR','TRANSLATE'].includes(kind)) return 'copycat'
  return 'building'
}
export function filterCatalog(kinds, category, query, labels) {
  const needle=query.trim().toLocaleLowerCase('de')
  return kinds.filter(kind=>(category === 'all' || categoryOfNode(kind) === category)
    && `${labels[kind] || kind} ${kind} ${describeCatalogNode(kind)}`.toLocaleLowerCase('de').includes(needle))
}
