import { nativeChoiceDomain } from './choice-icons.js'
import { IconSelect } from './IconSelect.jsx'
import {nodeControls} from './node-controls.js'
import {axisLabel} from './axis-labels.js'
import { IntegerField } from './IntegerField.jsx'
import { MirrorPlanePicker } from './MirrorPlanePicker.jsx'
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { inspectorContext, wireModels, editNativeGroup, groupInterfaceContext, editGroupInterface } from './generated/engine.mjs'

import { contract, getPath, inspectorFields, setPath } from './inspector-model.js'
import { TransformationFields } from './TransformationFields.jsx'
import { TransformValues } from './TransformValues.jsx'
import { WallThicknessFields } from './WallThicknessFields.jsx'
import {copycatShapeName} from './copycat-options.js'
import { model } from './model.js'
import { MaterialField } from './Workspace.jsx'

const models = wireModels()
const contextCarpets = model.carpets
const Context = createContext({})
const choiceLabels = { ...Object.fromEntries(model.shapes.map(shape=>[shape.id,copycatShapeName({source:shape.id})])), SQUARE:'Quadrat', RECTANGLE:'Rechteck', DRAW:'Anklicken', OVERLAY:'Vier Rotationen überlagern', ADJACENT:'Rotiert aneinander · 2×2 Blöcke', SEPARATED: 'Separiert', GROUPED: 'Gruppiert', NORMAL: 'Lokale Normale', FOOTPRINT: 'Aus Grundriss', SEGMENTS: 'Aus Segmenten', FLAT: 'Flachdach', PENT: 'Pultdach', GABLE: 'Satteldach', HIP: 'Walmdach', MANSARD: 'Mansarddach', CROSS:'Kreuzdach', HEIGHT: 'Firsthöhe', ANGLE: 'Dachneigung', rooms: 'Aus Räumen', walls: 'Aus Wänden', horizontal: 'Horizontal', vertical: 'Vertikal', depth: 'Tiefe', transform: 'Frei anordnen', flat: 'Flachdach', gable: 'Satteldach', AUTO: 'Automatisch', EXPLICIT: 'Explizit', OPEN: 'Offen', BOUNDARY: 'Raumgrenze', OPENING: 'Öffnung', STRUCTURE: 'Tragwerk', DECORATION: 'Dekoration' }
const labels = {
  columnMode: 'Säulenaufbau', countValue: 'Anzahl', roofType: 'Dachart', ridge: 'Firstrichtung', coordinates: 'Koordinaten', layoutCells: 'Zellenbelegung', assembly: 'Expliziter Aufbau', depth: 'Tiefe',
  roofSettings:'Dachparameter',booleanValue:'Booleanwert',operation:'Operation',roofInputMode:'Dachaufbau',roofTemplate:'Dachvorlage',roofSections:'Dachflächen', config:'Dachparameter', regionCopies: 'Copycats je Bereich', roofLevel: 'Dachebene', roofConfig: 'Dachaufbau', levelIds: 'Geschosskennungen',
  stairwell: 'Treppenhaus', elevatorShaft: 'Aufzugsschacht', serviceShaft: 'Installationsschacht',
  accessInstances: 'Vertikale Erschließung', roomLayout: 'Raumanordnung', roomConfig: 'Raumaufbau', facade: 'Fassade',
  features: 'Öffnungen und Erweiterungen', attachments: 'Wandanschlüsse', bayWindow: 'Erker', dormer: 'Gaube',
  bayWindows: 'Erkerinstanzen', dormers: 'Gaubeninstanzen', joinWallEnds: 'Wandenden verbinden',
  fenced: 'Einfrieden', fenceBlock: 'Zaunblock', fenceHeight: 'Zaunhöhe', pathWidth: 'Wegbreite',
  groundDepth: 'Bodentiefe', groundBlock: 'Bodenblock', clearance: 'Freiraum', elevation: 'Höhenversatz',
  landscapeOffset: 'Geländeversatz', buildingOffset: 'Gebäudeversatz', sitePaths: 'Wegeverläufe',
  discCut: 'Scheibenanteil', top: 'Oben', rise: 'Anstieg', run: 'Lauflänge', phase: 'Phase', slice: 'Periodenschnitt', variant: 'Dachvariante', projection: 'Position am Architektentisch', table: 'Tischposition', turn: 'Drehung', exact: 'Exakte Geometrie',
  exactClip: 'Exakter Ausschnitt', roof: 'Dachgeometrie', x: 'X', y: 'Y', z: 'Z', width: 'Breite', height: 'Höhe',
  length: 'Länge', offset:'Versatz',tilt:'Kippen',spin:'Eigenrotation', quarterTurns: 'Vierteldrehungen',
  instances: 'Instanzen', boundary: 'Umriss', boundaryRules: 'Randregeln', filler: 'Füllsegment',
  mode: 'Modus', heightOverride: 'Eigene Raumhöhe', interiorSource: 'Innenraumquelle', defaultPartition: 'Trennwand',
  front: 'Front', wall: 'Wand', floor: 'Boden', ceiling: 'Decke', geometry: 'Geometriequelle', layout: 'Anordnung',
  rotationAxis: 'Drehachse', materialMode: 'Materialmodus', role: 'Materialrolle', surfaces: 'Materialgruppen', module: 'Segmentquelle', block: 'Oberflächenblock', thickness: 'Dicke', occupied: 'Raumhöhe belegen', finish: 'Bodenbelag', offsets: 'Platzierungsversatz', rectangles: 'Grundrissflächen', patternAxis: 'Anordnung', storeyMode: 'Zusammensetzung', mirrorPositionsOnly: 'Nur Blockpositionen spiegeln', mirror: 'Spiegelung', plane: 'Spiegelebene', rotations: 'Rotationen', mirrors: 'Spiegelungen', p: 'Anstieg', q: 'Lauflänge', axis: 'Ausrichtung', slope: 'Dachneigung', upperSlope: 'Obere Dachneigung', breakRatio: 'Mansardknick', height16: 'Firsthöhe (hB)', knee16: 'Kniestock (hB)', overhang: 'Dachverlängerung (Blöcke)', overhang16: 'Dachverlängerung (hB)', storey: 'Zielgeschoss', area: 'Fläche', enclosed: 'Hülle', headroom: 'Durchgangshöhe', landingDepth: 'Podesttiefe', clockwise: 'Im Uhrzeigersinn', tread: 'Stufenquelle', landing: 'Podestquelle', enclosure: 'Schachtwandquelle', fromStorey: 'Ab Geschoss', toStorey: 'Bis Geschoss', stops: 'Haltestellen', sourceIndex: 'Quelleneingang', openings: 'Öffnungen', extensions: 'Säulenerweiterungen', position: 'Position', size: 'Abmessungen', policy: 'Wandregel', contribution: 'Raumbeitrag',
  inputs: 'Eingänge', outputs: 'Ausgänge', targets: 'Gebundene Anschlüsse', member: 'Gruppenmitglied', port: 'Anschluss', positions: 'Definitionslayout',
  type: 'Typ', name: 'Name', source: 'Quelle', id: 'Kennung', form: 'Form', family: 'Dachfamilie',
}
const label = key => ['x','y','z','X','Y','Z'].includes(key)?axisLabel(key.toUpperCase()):labels[key] || key.replace(/([a-z])([A-Z])/g, '$1 $2')
function initial(type) {
  if (type.endsWith('?')) return initial(type.slice(0, -1))
  if (/^(List|Set)</.test(type)) return []
  if (type.startsWith('Map<')) return {}
  if (type === 'String') return ''
  if (type === 'Boolean') return false
  if (models.enums[type]) return models.enums[type][0]
  const value = structuredClone(models[type]?.default ?? 0)
  if (value && typeof value === 'object' && (value.id === '' || ['RoofTemplateFamily','RoofSection'].includes(type))) value.id = crypto.randomUUID()
  return value
}
function args(type) {
  const body = type.slice(type.indexOf('<') + 1, -1)
  let depth = 0
  for (let i = 0; i < body.length; i++) {
    if (body[i] === '<') depth++
    if (body[i] === '>') depth--
    if (body[i] === ',' && !depth) return [body.slice(0, i), body.slice(i + 1)]
  }
  return [body]
}
function Value({ type, value, change, title, path = '', settings = {} }) {
  const context = useContext(Context)
  const choices = settings.choices || context.references?.[path]
  const optional = type.endsWith('?')
  const base = type.replace(/\?$/, '')
  const pick = options => <label>{title}<IconSelect domain={nativeChoiceDomain(base,path,context)} aria-label={title} value={value ?? (base==='RotationSegmentMode'?'OVERLAY':'')} onChange={event => change(['Int', 'Long'].includes(base) ? Number(event.target.value) : event.target.value)}>
    {options.map(option => { const key = typeof option === 'object' ? option.value : option; return <option key={String(key)} value={key} iconMeta={typeof option==='object'?{kind:option.kind,type:option.outputType}:undefined}>{typeof option === 'object' ? option.label : choiceLabels[option] || option}</option> })}
  </IconSelect></label>
  if(base === 'RotationSegmentMode') return pick([{value:'OVERLAY',label:'Vier Rotationen überlagern'},{value:'ADJACENT',label:'Rotiert aneinander · 2×2 Blöcke'}].map(option=>({...option})))
  if(path==='dynamicAxes') return <fieldset><legend>Dynamische Maße</legend>{[['X','Länge X'],['Y','Höhe Y'],['Z','Tiefe Z']].map(([axis,label])=><label className="check" key={axis}><input type="checkbox" checked={value?.includes(axis) || false} onChange={e=>change(e.target.checked?[...(value || []),axis]:(value || []).filter(item=>item!==axis))}/>{label}</label>)}</fieldset>
  if(path==='countValue') return <IntegerField label={title} value={value ?? 0} min={-2147483648} max={2147483647} onChange={change}/>
  if(base==='CopycatMirrorPlane') return <MirrorPlanePicker label={title} value={value || 'X'} onChange={change}/>
  if(base==='RotationAxis' && context.node?.kind==='SEGMENT_MIRROR') return <MirrorPlanePicker label="Spiegelachse" value={value || 'X'} planes={['X','Y','Z']} onChange={change}/>
  if (context.group && base === 'String' && path.endsWith('.type')) return pick(context.graphTypes)
  if (context.group && base === 'Int' && path.endsWith('.member')) {
    const output = path.startsWith('outputs.')
    const type = output ? getPath(context.face, path.replace(/\.member$/, '.type')) : null
    return pick([...(output ? [{ value: -1, label: 'Nicht verbunden' }] : []), ...context.members.filter(member => !output || member.outputType === type).map(member => ({ value: member.index, label: member.label, outputType:member.outputType }))])
  }
  if (context.group && path.endsWith('.port')) {
    const member = getPath(context.face, path.replace(/\.port$/, '.member'))
    const type = getPath(context.face, path.split('.').slice(0, 2).join('.') + '.type')
    return pick((context.members[member]?.ports || []).filter(port => port.types.includes(type)).map(port => port.key))
  }
  if (base === 'Int' && path.endsWith('.sourceIndex') && context.node) return pick(context.node.kind === 'SEGMENT_LAYOUT' ? Array.from({ length: 16 }, (_, index) => ({ value: index, label: `Copycat ${index + 1}` })) : context.node.inputs.map((source, index) => ({ value: index, label: `${index + 1}: ${source || 'Nicht verbunden'}` })))
  if (base === 'Int' && path === 'ridge') return pick([{ value: 0, label: 'Nord–Süd (Z)' }, { value: 1, label: 'Ost–West (X)' }])
  if (base === 'Int' && (path.endsWith('quarterTurns') || path === 'part.turn' || path === 'part.spin')) return pick([0, 1, 2, 3].map(value => ({ value, label: `${value * 90}°` })))
  if (choices && !optional) return pick(context.references?.[path] ? [{ value: '', label: 'Nicht verbunden' }, ...choices] : choices)
  if (base === 'String' && context.mode === 'building') {
    if (path === 'form' || /\.form$/.test(path)) return pick([{ value: '', label: 'Keine Form' }, ...Object.entries(context.library?.forms || {}).map(([value, form]) => ({ value, label: `${form.name || value} · ${value}` }))])
    if (/\.(storey|fromStorey|toStorey)$/.test(path)) return pick([{ value: '', label: 'Automatisch' }, ...(context.storeys || [])])
    if (path === 'block' && ['GARDEN', 'PATH'].includes(context.node?.kind)) return pick(context.surfaceBlocks || [])
    if (path === 'groundBlock') return pick(context.groundBlocks || [])
    if (path === 'fenceBlock') return pick(context.fenceBlocks || [])
    if (path === 'role') return pick(Object.keys(context.palette || {}).map(value => ({ value, label: ({ wall: 'Wand', frame: 'Rahmen', glass: 'Glas', roof: 'Dach', gable: 'Giebel', trim: 'Zierleiste' })[value] || value })))
    if (path === 'finish') return pick([{ value: '', label: 'Kein Belag' }, ...context.carpets])
  }
  if (path === 'roomConfig.wallThickness' || path === 'facade.thickness') return <WallThicknessFields {...{path,value,change,context}} />
  if (optional) return <div className="native-optional">
    <label className="check"><input type="checkbox" checked={value != null} onChange={event => change(event.target.checked ? initial(base) : null)} />{title}</label>
    {value != null && <Value {...{ value, change, title, path, settings }} type={base} />}
  </div>
  if (/^(List|Set)</.test(type)) return <fieldset><legend>{title}</legend>
    {(value || []).map((item, i) => <div className="native-item" key={i}>
      <Value type={args(type)[0]} value={item} path={`${path}.${i}`} title={`${title} ${i + 1}`} change={next => change(value.map((old, j) => j === i ? next : old))} />
      <button type="button" aria-label={`${title} ${i + 1} entfernen`} onClick={() => change(value.filter((_, j) => j !== i))}>Entfernen</button>
    </div>)}
    <button type="button" disabled={value?.length >= (settings.maxItems || 4096)} onClick={() => { const item = initial(args(type)[0]); if (context.group && item && typeof item === 'object' && 'type' in item) item.type = context.graphTypes[0]; change([...(value || []), item]) }}>+ Eintrag</button>
  </fieldset>
  if (type.startsWith('Map<')) return <fieldset><legend>{title}</legend>
    {Object.entries(value || {}).map(([key, item]) => <div className="native-item" key={key}>
      <Value type={args(type)[1]} value={item} path={`${path}.${key}`} title={key} change={next => change({ ...value, [key]: next })} />
      <button type="button" onClick={() => change(Object.fromEntries(Object.entries(value).filter(([id]) => id !== key)))}>Entfernen</button>
    </div>)}
    <MapAdd numeric={args(type)[0] === 'Int'} onAdd={key => { if (!['__proto__', 'constructor', 'prototype'].includes(key) && !(key in (value || {}))) change({ ...value, [key]: initial(args(type)[1]) }) }} />
  </fieldset>
  if (type === 'RoofSlope' && !path.endsWith('breakRatio') && context.slopes) return <label>{title}<IconSelect domain="slope" value={JSON.stringify(value)} onChange={event => change(JSON.parse(event.target.value))}>
    {context.slopes.map(slope => <option key={`${slope.p}:${slope.q}`} value={JSON.stringify(slope)}>{slope.p}:{slope.q} · {(Math.atan(slope.p / slope.q) * 180 / Math.PI).toFixed(2)}°</option>)}
  </IconSelect></label>
  if (type === 'RoofPartParameters') {
    const parameters = value || models.RoofPartParameters.default
    return <fieldset><legend>{title}</legend>
      <label>Dachneigung<IconSelect domain="slope" value={`${parameters.rise}:${parameters.run}`} onChange={event => { const [rise, run] = event.target.value.split(':').map(Number); change({ ...parameters, rise, run, phase: 0, slice: 0 }) }}>
        {(context.slopes || model.roofSlopes).map(slope => <option key={`${slope.p}:${slope.q}`} value={`${slope.p}:${slope.q}`}>{slope.p}:{slope.q}</option>)}
      </IconSelect></label>
      {['phase', 'slice', 'thickness', 'variant'].map(key => <Value key={key} type={models.RoofPartParameters.fields[key]} value={parameters[key]} title={label(key)} path={`${path}.${key}`} settings={key === 'variant' ? { choices: ['slope', 'eave', 'edge', 'ridge', 'hip', 'valley', 'corner', 'cap'] } : { min: key === 'slice' ? -1 : key === 'phase' ? 0 : 1, max: key === 'slice' ? parameters.rise : key === 'phase' ? parameters.run - 1 : 16 }} change={next => change({ ...parameters, [key]: next })} />)}
    </fieldset>
  }
  if (models[type]) {
    const object = value || models[type].default || {}
    let fields = Object.entries(models[type].fields)
    if(type==='RoomConfig')fields=fields.filter(([key])=>key!=='mode')
    const horizontalPoint = type === 'P' && (['buildingOffset', 'landscapeOffset'].includes(path) || /^accessInstances\.\d+\.position$/.test(path) || /^offsets\.\d+$/.test(path) && ['STOREY', 'BUILDING'].includes(context.node?.kind) || /^coordinates\.\d+$/.test(path) && ['AREA', 'ROOM_GEOMETRY', 'GARDEN'].includes(context.node?.kind))
    if (horizontalPoint) fields = fields.filter(([key]) => key !== 'y')
    if (type === 'RoofLevelConfig' || type === 'RoofTemplateSettings') fields = fields.filter(([key]) => !(['upperSlope', 'breakRatio'].includes(key) && object.form !== 'MANSARD') && !(key === 'height16' && object.mode !== 'HEIGHT') && !(key === 'knee16' && object.mode !== 'ANGLE'))
    return <fieldset className={type === 'P' ? 'coordinate-grid' : undefined} style={type === 'P' ? {'--coordinate-count':fields.length} : undefined}><legend>{title}</legend>{fields.filter(([key]) => !((context.group || type==='RoofSection') && key === 'id')).map(([key, field]) =>
      <Value key={key} type={field} path={path ? `${path}.${key}` : key} settings={contract.structures?.[type]?.[key]} value={object[key] ?? models[type].default?.[key]} title={label(key)} change={next => change({ ...object, [key]: next })} />)}</fieldset>
  }
  if(type==='CopycatMirrorPlane') return <MirrorPlanePicker label={title} value={value || 'X'} onChange={change}/>
  if (models.enums[type]) return pick(models.enums[type].map(value=>({value,label:type==='RotationAxis'&&value!=='NORMAL'?axisLabel(value):choiceLabels[value]||value})))
  if (type === 'Boolean') return <label className="check"><input type="checkbox" checked={value || false} onChange={event => change(event.target.checked)} />{title}</label>
  return <label>{title}<input type={type === 'String' ? 'text' : 'number'} value={value ?? ''} step="1" min={settings.min} max={settings.max} onChange={event => {
    const next = type === 'String' ? event.target.value : Number(event.target.value)
    if (type === 'String' || Number.isSafeInteger(next)) change(next)
  }} /></label>
}
function MapAdd({ numeric, onAdd }) {
  const [key, setKey] = useState('')
  return <div className="button-row"><input aria-label="Neue Kennung" placeholder="Kennung" value={key} type={numeric ? 'number' : 'text'} onChange={event => setKey(event.target.value)} />
    <button type="button" disabled={!key || (numeric && !/^-?\d+$/.test(key))} onClick={() => { onAdd(key); setKey('') }}>+ Eintrag</button></div>
}
function DraftFields({ value, fields, types, onApply, title }) {
  const [draft, setDraft] = useState(() => structuredClone(value))
  useEffect(() => setDraft(structuredClone(value)), [JSON.stringify(value)])
  if (!fields.length) return null
  return <details className="native-fields"><summary>{title}</summary>
    {fields.map(key => <Value key={key} type={types[key]} value={draft[key]} title={label(key)} change={next => setDraft({ ...draft, [key]: next })} />)}
    <button type="button" className="primary" disabled={JSON.stringify(draft) === JSON.stringify(value)} onClick={() => onApply(Object.fromEntries(fields.map(key => [key, draft[key]])))}>Übernehmen</button>
  </details>
}
// These controls are already rendered by the native-style source/material widgets.
const formWidgets = {
  INPUT: ['part.source', 'part.discCut', 'part.size', 'part.group', 'part.turn', 'part.offset', 'part.tilt', 'part.spin'],
  SAVED: ['savedCopycat'], UNION: ['materialMode', 'materialGroup', 'inputGroups'],
  SYMMETRIC_DIFFERENCE: ['materialMode', 'materialGroup', 'inputGroups'],
  ROTATE: ['rotationAxis', 'quarterTurns'], ROTATION: ['rotationAxis', 'quarterTurns'], MIRROR: ['mirror.plane'], OUTPUT: ['previewGroup'],
}
export function NativeFields({ mode, node: rawNode, edit, document, library, onInspectNode }) {
  const node=rawNode.kind==='RESULT'&&rawNode.resultMode!=='VALUE'?{...rawNode,kind:rawNode.producerKind,resultType:null}:rawNode
  const [draft, setDraft] = useState(() => structuredClone(node))
  useEffect(() => setDraft(structuredClone(node)), [JSON.stringify(node)])
  const structural=new Set(nodeControls(mode,node).map(control=>control.path))
  const fields = inspectorFields(mode, node).filter(field=>!(rawNode.kind==='COPYCAT'&&field.path.startsWith('inlinePart.')&&!['inlinePart.roof','inlinePart.discCut'].includes(field.path))).filter(field=>!(node.inlinePart && field.path==='form')).filter(field=>!structural.has(field.path)).filter(field=>!(node.kind==='ROOF'&&node.roofInputMode!=null&&(['roofConfig','roofSections','roofType','ridge','overhang'].includes(field.path)||field.path==='roofSettings'&&node.roofInputMode==='TEMPLATE'))).filter(field=>!(draft.dynamicAxes?.length && ['module','block'].includes(field.path))).filter(field => !(['INPUT','SAVED','COPYCAT'].includes(node.kind) && ['rotationAxis','quarterTurns','rotationPivot','translation','offset','turn','tilt','spin','top','part.top'].includes(field.path))).filter(field => !(['rotationPivot','translation'].includes(field.path) || field.path==='roofSections' && (!model.directAreas || draft.roofSections==null) || draft.roofSections!=null && ['roofType','ridge','overhang'].includes(field.path))).filter(field => !(mode === 'form' && formWidgets[node.kind]?.includes(field.path)))
  const context = useMemo(() => {
    if (mode !== 'building') return { mode, library, node: draft, slopes: model.roofSlopes }
    try {
      const altered = { ...document, graph: { ...document.graph, nodes: document.graph.nodes.map(item => item.id === node.id ? draft : item) } }
      return { ...inspectorContext(altered, node.id), mode, library, node: draft, carpets: contextCarpets }
    } catch { return { mode, library, node: draft, references: {}, carpets: [] } }
  }, [mode, document, library, draft])
  if(node.resultType) return null
  if(node.kind==='TRANSFORMATION') return <TransformationFields node={node} edit={edit} mode={mode}/>
  if(mode==='building' && node.kind==='AREA') return <p className="hint">Flächenmodus und Maße direkt am Knoten einstellen. Gezeichnete Flächen in der 2D-Vorschau bearbeiten.</p>
  if (mode === 'building' && ['ROTATION', 'MIRRORING'].includes(node.kind)) return <TransformValues {...{ node, edit }} />
  if (!fields.length) return null
  const groups = library?.forms?.[draft.form]?.groups || [{ id: 'body', name: 'Body', role: 'surface' }]
  const patch = (path, next) => setDraft(setPath(draft, path, next))
  return <Context.Provider value={context}><details open className="native-fields"><summary>{mode === 'building' ? 'Knoteneinstellungen' : 'Ausrichtung und Formeinstellungen'}</summary>
    {node.kind==='SEGMENT' && draft.dynamicAxes?.length>0 && <p className="hint">Dynamische Achsen werden erst vom verwendenden Raum oder Dach aufgelöst. Die eingestellten Maße dienen als Vorschau. Ein Copycat-Eingang belegt das gesamte Innere; Flächen-, Kanten- und Eckanschlüsse entfallen.</p>}
    {node.kind==='ROOF' && draft.roofSections!=null && <p className="hint">Flächen direkt am Dach anschließen. Jede Dachfläche besitzt eigene Einstellungen und einen Geschossbezug; ohne Fläche wird die freiliegende Geschossfläche verwendet.</p>}
    {node.kind==='ROOF' && node.adaptiveRoof && <p className="hint">Das Dach ermittelt Fläche, First sowie innere und äußere Ecken automatisch. Dynamische Segmente erhalten die benötigten Maße. Ohne Vorlage wird die entsprechende Dachgeometrie erzeugt.</p>}
    {node.kind === 'ROTATION_SEGMENT' && <p className="hint">{draft.columnMode==='ADJACENT'?'Vier um Y gedrehte Copycats bilden eine Lage mit 2×2 Blöcken. Die Länge bestimmt die Anzahl der Lagen.':'Die Copycat wird bei 0°, 90°, 180° und 270° um Y vereinigt. Die Länge wiederholt dieses Profil entlang Y zur Säule.'}</p>}
    {(draft.kind === 'ROOF' && !draft.assembly && draft.roofType === 'gable' || draft.kind === 'ROOF_LEVEL' && ['GABLE', 'CROSS', 'PENT'].includes(draft.roofLevel?.form)) && <p className="hint">Die Dachverlängerung wächst entlang des Firsts über die Giebelseiten hinaus. Dachbreite und Mauern bleiben am Gebäudegrundriss.</p>}
    {fields.map(field => <div key={field.path} data-inspector-field={field.path}>
      {context.derived?.[field.path] ? <div><label>{label(field.path)}<output>{Array.isArray(getPath(context.resolved, field.path)) ? `${getPath(context.resolved, field.path).length} Einträge` : String(getPath(context.resolved, field.path))}</output></label><button type="button" onClick={() => onInspectNode?.(context.derived[field.path])}>Quelle bearbeiten</button></div>
        : field.path === 'module'  && node.kind === 'SEGMENT' ? <label>Segmentquelle<IconSelect domain="form" value={draft.block === 'minecraft:air' ? '_air' : draft.module || '_solid'} onChange={event => {
        const value = event.target.value
        setDraft({ ...draft, module: value.startsWith('_') ? '' : value, block: value === '_air' ? 'minecraft:air' : '' })
      }}><option value="_solid">Massive Wand</option><option value="_air">Leere Wand</option>{Object.entries(library?.modules || {}).map(([id, item]) => <option key={id} value={id} iconMeta={{type:"WALL"}}>{item.name || id}</option>)}</IconSelect></label>
        : field.path === 'block' && node.kind === 'SEGMENT' ? null
        : field.path === 'surfaces' ? <fieldset><legend>Materialgruppen</legend>{groups.map(group => <MaterialField key={group.id} blockOnly label={group.name} value={draft.surfaces?.[group.id] || context.palette?.[group.role === 'surface' ? draft.role : group.role] || ''} onChange={value => patch('surfaces', Object.fromEntries(groups.map(item => [item.id, item.id === group.id ? value : draft.surfaces?.[item.id] || context.palette?.[item.role === 'surface' ? draft.role : item.role] || 'minecraft:stone'])))} />)}<button type="button" onClick={() => patch('surfaces', null)}>Gebäudepalette verwenden</button></fieldset>
        : <Value type={field.path==='roofSections'?'List<RoofSection>':field.type} value={getPath(draft, field.path)} path={field.path} settings={field.path==='roofSections'?{...field,maxItems:16}:field} title={label(field.path.split('.').at(-1))} change={next => patch(field.path, next)} />}
    </div>)}
    <button type="button" className="primary" disabled={JSON.stringify(draft) === JSON.stringify(node)} onClick={() => edit(Object.fromEntries(fields.map(field => field.path.split('.')[0]).map(key => [key, draft[key]])))}>Übernehmen</button>
  </details></Context.Provider>
}
function GroupInterfaceFields({ mode, document, group, onApply }) {
  const [face, setFace] = useState(() => structuredClone(group.graphInterface))
  useEffect(() => setFace(structuredClone(group.graphInterface)), [JSON.stringify(group.graphInterface)])
  const context = { ...groupInterfaceContext(mode === 'building', document, group.id), group: true, face }
  return <Context.Provider value={context}><div className="native-fields">
    <Value type="GraphGroupInterface" value={face} path="" title="Benannte Schnittstelle" change={setFace} />
    <button type="button" className="primary" onClick={() => onApply(face)}>Schnittstelle übernehmen</button>
  </div></Context.Provider>
}
export function NativeDocumentFields({ mode, document, edit, selection = [] }) {
  const [groupName, setGroupName] = useState('Gruppe')
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const act = (action, id = '') => {
    try {
      const graph = editNativeGroup(mode === 'building', document, action, id, groupName, selection)
      edit({ ...document, graph }); setError('')
      if (action === 'initialize') setEditing(id)
    } catch (error) { setError(error.message) }
  }
  return <>
    {mode === 'building' && <DraftFields value={document.graph} fields={['projection']} types={models.BuildingGraph.fields} title="Architektentisch" onApply={patch => edit({ ...document, graph: { ...document.graph, ...patch } })} />}
    <details><summary>Knotengruppen im Graphen</summary>
      <label>Gruppenname<input value={groupName} onChange={event => setGroupName(event.target.value)} maxLength={64} /></label>
      <button type="button" disabled={!selection.length} onClick={() => act('create')}>Auswahl gruppieren</button>
      {(document.graph.nodeGroups || []).map(group => <fieldset key={group.id}><legend>{group.name}</legend>
        <small>{group.nodes.length} Knoten · {group.graphInterface?.inputs.length || 0} benannte Eingänge</small>
        <div className="button-row"><button type="button" onClick={() => act('instantiate', group.id)}>Instanz erstellen</button>
          <button type="button" onClick={() => act('rename', group.id)}>Umbenennen</button>
          <button type="button" onClick={() => act('initialize', group.id)}>Schnittstelle bearbeiten</button>
          <button type="button" onClick={() => act('ungroup', group.id)}>Auflösen</button></div>
        {editing === group.id && group.graphInterface && <GroupInterfaceFields {...{ mode, document, group }} onApply={face => {
          try { edit({ ...document, graph: editGroupInterface(mode === 'building', document, group.id, face) }); setError('') }
          catch (error) { setError(error.message) }
        }} />}
      </fieldset>)}
      {error && <p className="error" role="status">{error}</p>}
    </details>
  </>
}
