import {splitVector} from './reference-groups.js'
import {describeNode,newNode,remapBuildingReferences} from './generated/engine.mjs'
import {editNode,inspect,model,replaceNodes} from './model.js'

const operations=[['UNION','∪ Vereinigung'],['INTERSECTION','∩ Schnitt'],['DIFFERENCE','− Differenz'],['SYMMETRIC_DIFFERENCE','△ Symmetrische Differenz'],['ROTATE','Rotieren'],['MIRROR','Spiegeln'],['POSITION','Positionieren'],['TRANSFORM','Transformieren']]
export function operatorFamilies(mode) {
  const available=new Set(mode==='building'?model.building:model.form.map(n=>n.kind))
  const copycat=operations.map(([op,label])=>[mode==='building'?`COPYCAT_${op}`:({POSITION:'TRANSLATE',TRANSFORM:'COPYCAT_TRANSFORM'}[op] || op),label]).filter(([kind])=>available.has(kind))
  const segment=[...operations.map(([op,label])=>[`SEGMENT_${op}`,label]).filter(([kind])=>available.has(kind)),...[['ROTATION_SEGMENT','Rotationssegment'],['SERVICE_SHAFT_SEGMENT','Schachtsegment'],['ELEVATOR_SHAFT_SEGMENT','Aufzugssegment']].filter(([kind])=>available.has(kind))]
  const positions=[['POSITION','Zusammenführen'],...['ADD','SUBTRACT','MULTIPLY','DIVIDE','MIN','MAX'].map((op,i)=>[`POSITION_${op}`,['Addieren','Subtrahieren','Multiplizieren','Dividieren','Minimum','Maximum'][i]]),['POSITION_X','Zerlegen']].filter(([kind])=>available.has(kind))
  const numbers=[['COUNT','Wert'],['INT_ADD','Addition'],['INT_SUBTRACT','Subtraktion'],['INT_MULTIPLY','Multiplikation'],['INT_DIVIDE','Ganzzahldivision'],['INT_REMAINDER','Divisionsrest'],['INT_MIN','Minimum'],['INT_MAX','Maximum'],['INT_ABS','Betrag |x|'],['INT_TO_COUNT','Als Anzahl']].filter(([kind])=>available.has(kind))
  return [{id:'numbers',label:'Zahlenoperator',operations:numbers}, {id:'copycat',label:'Copycat-Operator',operations:copycat},...(mode==='building'?[{id:'segments',label:'Segment-Operator',operations:segment}]:[]),{id:'positions',label:'Positionsoperator',operations:positions}]
}
export const operatorFamily=(mode,kind)=>operatorFamilies(mode).find(family=>family.operations.some(([candidate])=>candidate===(kind==='VECTOR_SPLIT'?'POSITION_X':kind==='INT'?'COUNT':kind)))
export const operatorLabel=(mode,kind)=>operatorFamily(mode,kind)?.operations.find(([candidate])=>candidate===(kind==='INT'?'COUNT':kind))?.[1]

// The family UI uses the native operation kinds, retaining existing graph files.
// Match sockets by type and keep each operand once when the operation changes.
export function switchOperator(mode,document,id,kind) {
  const split=document.graph.nodeGroups?.find(group=>group.id===id && group.presentation==='vectorSplit')
  if(split)return joinSplitOperation(mode,document,split,kind)
  const entries=inspect(mode,document),before=entries.find(entry=>entry.node.id===id)
  const family=operatorFamily(mode,before?.node.kind)
  if(!family || !family.operations.some(([candidate])=>candidate===(kind==='VECTOR_SPLIT'?'POSITION_X':kind==='INT'?'COUNT':kind))) throw new Error('Unpassende Operatorfamilie.')
  if(before.node.kind===kind) return document
  if(family.id==='positions' && kind==='POSITION_X')return splitPosition(mode,document,id)
  const building=mode==='building',old=document.graph.nodes.find(node=>node.id===id)
  let next=newNode(building,kind,id,old.x,old.y,true)
  for(const key of ['name','materialMode','materialGroup','previewGroup','role','preview','rotationAxis','quarterTurns','rotationPivot','copycatTransforms','countValue']) if(key in old) next[key]=structuredClone(old[key])
  if(kind.endsWith('MIRROR') || kind==='MIRROR') next.mirror=old.mirror
  if(kind.endsWith('POSITION') || kind==='TRANSLATE') next.translation=old.translation
  const limits=building?model.buildingInputs[kind]:model.form.find(n=>n.kind===kind)
  const geometryPorts=before.ports.filter(port=>port.types.some(type=>['shape','COPYCAT','WALL','POSITION','position','INT','COUNT'].includes(type)))
  if(limits && limits.min!==limits.max) next.inputs=Array.from({length:Math.max(limits.min,Math.min(limits.max,geometryPorts.length))},()=> '')
  const preview=describeNode(building,{...document,graph:{...document.graph,nodes:document.graph.nodes.map(n=>n.id===id?next:n)}},id)
  const remaining=[...before.ports],mapping=new Map(),matches=[]
  for(const port of preview.ports) {
    const sameType=oldPort=>port.types.some(type=>oldPort.types.includes(type))
    const match=remaining.find(oldPort=>sameType(oldPort) && oldPort.source) || remaining.find(sameType)
    if(!match) continue
    remaining.splice(remaining.indexOf(match),1);mapping.set(match.id,port.id);matches.push([match,port])
    if(building && !port.id.startsWith('in:')) next[port.id]=match.source || null
    else next.inputs[Number(port.id.replace('in:',''))]=match.source
  }
  if(!building) next.inputGroups=next.inputs.map((_,i)=>old.inputGroups?.[Number(matches.find(([,port])=>Number(port.id)===i)?.[0].id)] || '')
  if(building && model.directAreas && /^SEGMENT_(UNION|INTERSECTION|DIFFERENCE|SYMMETRIC_DIFFERENCE)$/.test(kind)) {
    const values=old.operandOffsets ?? (old.kind==='SEGMENT_UNION'?old.offsets:[]) ?? []
    next.operandOffsets=next.inputs.map((_,i)=>values[Number(matches.find(([,port])=>port.id===`in:${i}`)?.[0].id.replace('in:',''))] ?? {x:0,y:0,z:0})
    next.offsets=[]
  }
  if(building && next.operandOffsets==null) for(const key of ['offsets']) if(old[key]?.length) next[key]=next.inputs.map((_,i)=>old[key][Number(matches.find(([,port])=>port.id===`in:${i}`)?.[0].id.replace('in:',''))] ?? {x:0,y:0,z:0})
  // Named group inputs keep their socket identities as well. Removed sockets
  // disappear from the interface; unrelated targets and outputs stay in place.
  const owner=document.graph.nodeGroups?.find(group=>group.nodes.includes(id))
  const member=owner?.nodes.indexOf(id)
  const nodeGroups=document.graph.nodeGroups?.map(group=>{
    if(!group.graphInterface || !owner || (group.definitionId || group.id)!==(owner.definitionId || owner.id)) return group
    const face=group.graphInterface
    const inputs=face.inputs.map(input=>({...input,targets:input.targets.flatMap(target=>target.member!==member?[target]:mapping.has(target.port)?[{...target,port:mapping.get(target.port)}]:[])})).filter(input=>input.targets.length)
    return {...group,graphInterface:{...face,inputs},inputConnections:Object.fromEntries(Object.entries(group.inputConnections || {}).filter(([key])=>inputs.some(input=>input.id===key)))}
  })
  return editNode(mode,nodeGroups?{...document,graph:{...document.graph,nodeGroups}}:document,next)
}


function detachMembers(mode,document,removed,groupId) {
 const ids=Object.fromEntries(document.graph.nodes.map(node=>[node.id,removed.includes(node.id)?'':node.id]))
 const nodes=document.graph.nodes.filter(node=>!removed.includes(node.id)).map(node=>mode==='building'
  ? {...node,...remapBuildingReferences(node,ids)}
  : {...node,inputs:node.inputs.map(source=>removed.includes(source)?'':source)})
 return {...document,graph:{...document.graph,nodes,nodeGroups:(document.graph.nodeGroups||[]).filter(group=>group.id!==groupId).map(group=>({...group,nodes:group.nodes.filter(id=>!removed.includes(id)),inputConnections:Object.fromEntries(Object.entries(group.inputConnections||{}).map(([key,value])=>[key,removed.includes(value)?'':value]))})).filter(group=>group.nodes.length)}}
}
function splitPosition(mode,document,id) {
 const old=document.graph.nodes.find(n=>n.id===id),before=inspect(mode,document).find(e=>e.node.id===id)
 const source=before.ports.find(p=>p.types.some(t=>t.toLowerCase()==='position'))?.source || null
 const clean=detachMembers(mode,document,[id])
 const result=splitVector(mode,clean,source,{x:old.x,y:old.y})
 return {...result.document,graph:{...result.document.graph,nodeGroups:result.document.graph.nodeGroups.map(group=>group.id===result.nodeId?{...group,id,name:old.name||'Positionsoperator'}:group)}}
}
function joinSplitOperation(mode,document,group,kind) {
 if(kind==='POSITION_X')return document
 if(!operatorFamilies(mode).find(f=>f.id==='positions').operations.some(([candidate])=>candidate===kind))throw new Error('Unpassende Operatorfamilie.')
 const members=group.nodes.map(id=>document.graph.nodes.find(node=>node.id===id)),source=group.inputConnections?.vector||members[0]?.inputs[0]||''
 const clean=detachMembers(mode,document,group.nodes,group.id)
 const next=newNode(mode==='building',kind,group.id,Math.min(...members.map(n=>n.x)),Math.min(...members.map(n=>n.y)),true)
 if(kind!=='POSITION' && source)next.inputs[0]=source
 return replaceNodes(clean,[...clean.graph.nodes,next])
}
