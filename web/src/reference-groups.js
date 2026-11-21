import {axisLabel} from './axis-labels.js'
import { previewCopycatOption } from './copycat-options.js'
import { newNode,previewGraph,remapBuildingReferences,compileForm,normalizeDocument,wireModels } from './generated/engine.mjs'
import { applyWires,connections,inspect,model,position,replaceNodes,editNode } from './model.js'
import { identityOf } from './documents.js'
import { insertNodeGroup,mergeLibraries } from './node-groups.js'
import { insertFormReference,referenceDisabled } from './studio-actions.js'

const fresh=prefix=>`${prefix}_${crypto.randomUUID().replaceAll('-','').slice(0,14)}`
const anchor=nodes=>({x:Math.min(...nodes.map(n=>n.x || 0)),y:Math.min(...nodes.map(n=>n.y || 0))})
export function groupViews(document,entries) {
  const byId=new Map(entries.map(entry=>[entry.node.id,entry])), wires=connections(entries)
  return (document.graph.nodeGroups || []).filter(group=>group.libraryReference || group.presentation==='vectorSplit').map(group=>{
    const members=group.nodes.map(id=>byId.get(id)).filter(Boolean), ids=new Set(group.nodes),face=group.graphInterface
    const ports=face ? face.inputs.flatMap(input=>{
      const target=input.targets[0], member=members[target?.member],port=member?.ports.find(p=>p.id===target.port)
      return port?[{...port,id:`${member.node.id}/${port.id}`,name:input.name,targetNode:member.node.id,targetPort:port.id,targets:input.targets.map(t=>({target:members[t.member]?.node.id,port:t.port})).filter(t=>t.target)}]:[]
    }) : members.flatMap(({node,ports})=>ports.filter(port=>!ids.has(port.source)).map(port=>({...port,id:`${node.id}/${port.id}`,name:port.name,targetNode:node.id,targetPort:port.id})))
    const outputs=face ? face.outputs.flatMap(output=>{
      const member=members[output.member];return member?[{id:member.node.id,name:output.name,type:member.outputType}]:[]
    }) : members.filter(member=>member.outputType && (!wires.some(w=>w.source===member.node.id && ids.has(w.target)) || wires.some(w=>w.source===member.node.id && !ids.has(w.target)))).map(member=>({id:member.node.id,name:member.node.name || member.node.id,type:member.outputType}))
    return {...group,members,ports,outputs,position:anchor(members.map(member=>member.node)),previewId:outputs[0]?.id || members[0]?.node.id}
  })
}
export function resolveViewConnection(connection,groups) {
  const sourceGroup=groups.find(g=>g.id===connection.source), targetGroup=groups.find(g=>g.id===connection.target)
  const port=targetGroup?.ports.find(p=>p.id===connection.targetHandle)
  return {source:sourceGroup?.outputs.find(output=>output.id===connection.sourceHandle)?.id || (sourceGroup?sourceGroup.outputs[0]?.id:connection.source),target:port?.targetNode || connection.target,port:port?.targetPort || connection.targetHandle,targets:port?.targets}
}
export function viewWires(wires,groups) {
  const owners=new Map(groups.flatMap(group=>group.nodes.map(id=>[id,group])))
  const rendered=[]
  for(const wire of wires) {
    const source=owners.get(wire.source),target=owners.get(wire.target)
    if(source && source===target) continue
    const port=target?.ports.find(port=>port.targets?.some(t=>t.target===wire.target && t.port===wire.port) || port.targetNode===wire.target && port.targetPort===wire.port)
    if(target && !port) continue
    const row={...wire,viewSource:source?.id || wire.source,viewTarget:target?.id || wire.target,sourceHandle:source?wire.source:'out',targetHandle:port?.id || wire.port}
    if(!rendered.some(old=>old.viewSource===row.viewSource && old.viewTarget===row.viewTarget && old.sourceHandle===row.sourceHandle && old.targetHandle===row.targetHandle)) rendered.push(row)
  }
  return rendered
}
export function moveViewNodes(document,groups,moved) {
  const updates={}
  for(const item of moved) {
    const group=groups.find(g=>g.id===item.id)
    if(!group) {updates[item.id]={x:position(item.position.x),y:position(item.position.y)};continue}
    const nodes=group.members.map(m=>m.node)
    const dx=Math.max(model.positionMin-Math.min(...nodes.map(n=>n.x)),Math.min(model.positionMax-Math.max(...nodes.map(n=>n.x)),Math.round(item.position.x-group.position.x)))
    const dy=Math.max(model.positionMin-Math.min(...nodes.map(n=>n.y)),Math.min(model.positionMax-Math.max(...nodes.map(n=>n.y)),Math.round(item.position.y-group.position.y)))
    for(const node of nodes) updates[node.id]={x:node.x+dx,y:node.y+dy}
  }
  return updates
}
function reference(entry) {return {documentId:identityOf(entry),version:entry.version || 1,name:entry.name,collection:entry.collection || 'graphs'}}
function scopeLibrary(entry,namespace,currentLibrary) {
  const scalarTypes=new Set(['INT','COUNT','POSITION','ROTATION','MIRRORING','BOOLEAN','TRANSFORMATION'])
  const result=entry.document?.graph?.nodes.find(node=>node.resultType)
  const scalar=entry.collection==='nodeGroups'
    ? entry.graphInterface?.outputs?.length ? entry.graphInterface.outputs.every(output=>scalarTypes.has(output.type)) : entry.nodes?.length && entry.nodes.every(node=>/^(COUNT|BOOLEAN|INT|POSITION|NUMBER_OPERATOR|RELATION_OPERATOR|STATEMENT_OPERATOR)(_|$)/.test(node.kind) || ['ROTATION','MIRRORING','TRANSFORMATION'].includes(node.kind))
    : scalarTypes.has(result?.resultType)
  const source=entry.library || {},palette=scalar?{}:entry.document?.palette || entry.palette || wireModels().Building.default.palette,roles=Object.fromEntries(Object.keys(palette).map(key=>[key,`${namespace}_${key}`]))
  const formIds=Object.fromEntries(Object.keys(source.forms || {}).map(key=>[key,`${namespace}_${key}`])),moduleIds=Object.fromEntries(Object.keys(source.modules || {}).map(key=>[key,`${namespace}_${key}`]))
  function scope(value,key) {
    if(Array.isArray(value)) return value.map(item=>scope(item))
    if(value && typeof value==='object') return Object.fromEntries(Object.entries(value).map(([key,item])=>[key,scope(item,key)]))
    if(key==='form') return formIds[value] || value
    if(key==='module') return moduleIds[value] || value
    if(['role','material','overrideRole'].includes(key)) return roles[value] || value
    return value
  }
  const library=mergeLibraries(currentLibrary,{forms:Object.fromEntries(Object.entries(source.forms || {}).map(([key,value])=>[formIds[key],scope(value)])),modules:Object.fromEntries(Object.entries(source.modules || {}).map(([key,value])=>[moduleIds[key],scope(value)]))})
  return {scope,library,palette:Object.fromEntries(Object.entries(palette).map(([key,value])=>[roles[key],value]))}
}
export function libraryReferenceEntries(library) {
  return [...Object.entries(library.forms || {}).map(([key,document])=>({id:`libraryForm:${key}`,documentId:`libraryForm:${key}`,key,name:document.name || key,version:document.version || 1,mode:'form',document,collection:'libraryForms',type:'Form'})),...Object.entries(library.modules || {}).map(([key,module])=>({id:`libraryModule:${key}`,documentId:`libraryModule:${key}`,key,name:module.name || key,version:module.version || 1,mode:'building',module,collection:'libraryModules',type:'Segment'})),...Object.entries(library.graphs || {}).map(([key,definition])=>({id:`libraryGraph:${key}`,documentId:key.replace(/@[^@]+$/,''),version:Number(key.match(/@(\d+)$/)?.[1]||1),key,name:definition.document.name||key,mode:'building',document:definition.document,library:{forms:definition.forms,modules:definition.modules},collection:'libraryGraphs',type:definition.document.graph.nodes.some(n=>n.kind==='GROUP_INPUT')?'Gruppe':'Graphdefinition'}))]
}
function bareBuilding() {return {schema:2,name:'Vorschau',graph:{schema:1,nodes:[{id:'empty_wall',kind:'SEGMENT',block:'minecraft:air',length:1},{id:'result',kind:'BUILDING',name:'Ergebnis'}]}}}
export function previewReferenceEntry(entry,library) {
  if(entry.preview) return entry.preview
  if(entry.collection==='nodeGroups'&&!entry.document?.graph) {
    const base=entry.mode==='building'?bareBuilding():{schema:3,name:'Vorschau',graph:{schema:2,nodes:[{id:'result',kind:'OUTPUT',inputs:['']}]}}
    const inserted=insertNodeGroup(base,entry),added=inserted.document.graph.nodes.filter(node=>!base.graph.nodes.some(old=>old.id===node.id)), used=new Set(connections(inspect(entry.mode,inserted.document)).map(w=>w.source))
    const terminal=added.find(node=>!used.has(node.id)) || added.at(-1)
    return previewGraph(entry.mode==='building',inserted.document,entry.library || library,terminal.id,entry.materials || {})
  }
  if(entry.document && entry.mode==='form') return previewCopycatOption(entry)
  if(entry.document) return previewGraph(entry.mode==='building',entry.document,entry.library || library,'',entry.materials || {})
  if(entry.collection==='libraryModules') {
    const doc=bareBuilding(),node=newNode(true,'SEGMENT','preview',0,0,false)
    const next=replaceNodes(doc,[...doc.graph.nodes,{...node,module:entry.key}])
    return previewGraph(true,next,library,'preview')
  }
  return null
}
function buildingInputPort(kind,slot) {
  if(kind==='TRANSFORMATION') return ['mirroring','rotation','positionValue'][slot]
  if(slot===1) return {ROTATE:'rotation',MIRROR:'mirroring',TRANSLATE:'positionValue',COPYCAT_TRANSFORM:'transformationValue'}[kind] || `in:${slot}`
  return `in:${slot}`
}
export function buildingTemplate(entry) {
  const forms={...(entry.library?.forms || {})},ids=new Map(entry.nodes.map(n=>[n.id,n])), remapKind={INPUT:'COPYCAT',SAVED:'COPYCAT',ROTATE:'COPYCAT_ROTATE',MIRROR:'COPYCAT_MIRROR',TRANSLATE:'COPYCAT_POSITION',UNION:'COPYCAT_UNION',INTERSECTION:'COPYCAT_INTERSECTION',DIFFERENCE:'COPYCAT_DIFFERENCE',SYMMETRIC_DIFFERENCE:'COPYCAT_SYMMETRIC_DIFFERENCE'}
  const nodes=entry.nodes.map(node=>{
    const target=newNode(true,remapKind[node.kind] || node.kind,node.id,node.x || 0,node.y || 0,true)
    if(['INPUT','SAVED'].includes(node.kind)) {
      const key=`${fresh('form')}@1`,id=key.split('@')[0],form={schema:3,id,version:1,name:node.name || entry.name,groups:entry.groups,graph:{schema:entry.document?.graph?.schema || 2,nodes:[structuredClone(node),{id:'ref_output',kind:'OUTPUT',inputs:[node.id]}]}}
      const source=replaceNodes(form,form.graph.nodes)
      // Resolve only this leaf; operators and external inputs remain native nodes.
      forms[key]={...source,parts:compileForm(source)}
      const surfaces=Object.fromEntries(Object.entries(entry.materials || {}).filter(([,value])=>value))
      return {...target,form:key,surfaces:Object.keys(surfaces).length?surfaces:null}
    }
    if(['ROTATE','ROTATION'].includes(node.kind) && node.rotationAxis==='NORMAL' && node.quarterTurns) throw new Error('Die ältere lokale Rotationsachse dieser Gruppe zuerst auf X, Y oder Z umstellen.')
    const common={...target,name:node.name || "",materialMode:node.materialMode || "SEPARATED",role:node.materialGroup || "wall",inputs:(node.inputs || []).map(()=>''),rotationAxis:node.rotationAxis==='NORMAL'?'Y':node.rotationAxis || 'Y',quarterTurns:node.quarterTurns || 0,rotationPivot:node.rotationPivot,translation:node.translation,transformationOrder:node.transformationOrder,countValue:node.countValue,operation:node.operation,numberValue:node.numberValue,comparisonValue:node.comparisonValue,booleanValue:node.booleanValue,copycatTransforms:node.copycatTransforms,mirror:node.mirror}
    if(node.kind==='ROTATION') return {...common,inputs:[],rotations:[{axis:common.rotationAxis,quarterTurns:common.quarterTurns}]}
    if(node.kind==='MIRRORING') return {...common,inputs:[],mirrors:[node.mirror || {plane:'X'}]}
    if(['ROTATE','MIRROR','TRANSLATE','COPYCAT_TRANSFORM'].includes(node.kind)) common.inputs=['']
    if(node.kind==='TRANSFORMATION') common.inputs=[]
    return common
  })
  const wires=entry.wires.map(wire=>{
    const target=ids.get(wire.target),slot=Number(wire.port)
    return {...wire,port:buildingInputPort(target.kind,slot)}
  })
  const graphInterface=entry.graphInterface && {...entry.graphInterface,inputs:entry.graphInterface.inputs.map(input=>({...input,type:{shape:'COPYCAT',position:'POSITION',rotation:'ROTATION',mirroring:'MIRRORING',transformation:'TRANSFORMATION'}[input.type] || input.type,targets:input.targets.map(target=>({...target,port:buildingInputPort(entry.nodes[target.member].kind,Number(target.port))}))})),outputs:entry.graphInterface.outputs.map(output=>({...output,type:{shape:'COPYCAT',position:'POSITION',rotation:'ROTATION',mirroring:'MIRRORING',transformation:'TRANSFORMATION'}[output.type] || output.type}))}
  return {...entry,graphInterface,mode:'building',nodes,wires,library:{...(entry.library || {}),forms},palette:{...wireModels().Building.default.palette,...Object.fromEntries((entry.groups || []).map(group=>[group.id,entry.materials?.[group.id] || 'minecraft:stone']))}}
}
function insertGroup({mode,document,library,materials,entry,placement}) {
  const groupId=fresh('reference'), meta=reference(entry)
  if(entry.collection==='nodeGroups') {
    const source=entry.mode===mode?entry:mode==='building' && entry.mode==='form'?buildingTemplate(entry):null
    if(!source) throw new Error('Diese Knotengruppe benötigt den anderen Graphtyp.')
    const scoped=mode==='building'?scopeLibrary(source,'r_'+groupId.slice(-8),library):null
    const template=scoped?{...source,nodes:source.nodes.map(node=>scoped.scope(node)),library:scoped.library}:source
    const result=insertNodeGroup(document,template,undefined,placement), oldIds=new Set(document.graph.nodes.map(n=>n.id)), ids=result.document.graph.nodes.filter(n=>!oldIds.has(n.id)).map(n=>n.id)
    const group={id:groupId,name:entry.name,nodes:ids,libraryReference:meta,...(source.graphInterface?{graphInterface:source.graphInterface,inputConnections:{}}:{})}
    const palette=scoped?{...normalizeDocument(true,result.document).palette,...scoped.palette}:result.document.palette
    const next={...result.document,...(palette?{palette}:{}),graph:{...result.document.graph,nodes:result.document.graph.nodes.map(node=>scoped && ids.includes(node.id)?scoped.scope(node):node),nodeGroups:[...(result.document.graph.nodeGroups || []),group]}}
    inspect(mode,next)
    return {document:next,library:scoped?.library || mergeLibraries(library,entry.library),materials:{...materials,...result.materials},nodeId:groupId}
  }
  const namespace='r_'+groupId.slice(-8),scoped=scopeLibrary(entry,namespace,library), source=entry.collection==='libraryModules'?[{...newNode(true,'SEGMENT','module',0,0,false),module:entry.key}]:entry.document.graph.nodes.filter(node=>node.id!=='empty_wall' && !node.resultType)
  const ids=Object.fromEntries(source.map(node=>[node.id,fresh('refnode')])),origin=anchor(source),right=Math.max(...document.graph.nodes.map(n=>n.x || 0))+260
  const nodes=source.map(node=>{
    const x=(placement?.x ?? right)+(node.x || 0)-origin.x,y=(placement?.y ?? 40)+(node.y || 0)-origin.y
    if(placement && (x<model.positionMin || x>model.positionMax || y<model.positionMin || y>model.positionMax)) throw new Error('Die Referenz passt an dieser Knotenposition nicht in den Graphbereich.')
    return {...scoped.scope(node),...scoped.scope(remapBuildingReferences(node,ids)),id:ids[node.id],x:position(x),y:position(y)}
  })
  const terminal=entry.document?.graph?.nodes.find(node=>node.resultType),resultIndex=terminal?source.findIndex(node=>node.id===terminal.inputs[0]):nodes.findIndex(node=>node.kind==='BUILDING'),group={id:groupId,name:entry.name,nodes:nodes.map(n=>n.id),libraryReference:meta,...((resultIndex>=0 || entry.collection==='libraryModules')?{graphInterface:{inputs:[],outputs:[{id:'result',name:entry.name,type:resultIndex>=0?(terminal?.resultType || 'BUILDING'):'WALL',member:resultIndex>=0?resultIndex:0}]}}:{})}
  const candidate={...document,palette:{...normalizeDocument(true,document).palette,...scoped.palette},graph:{...document.graph,nodes:[...document.graph.nodes,...nodes],nodeGroups:[...(document.graph.nodeGroups || []),group]}}
  const next=replaceNodes(candidate,candidate.graph.nodes);inspect(mode,next)
  return {document:next,library:scoped.library,materials,nodeId:groupId}
}
export function insertSavedReference({mode,document,library,materials,entry,currentIdentity,targetId}) {
  const reason=referenceDisabled(entry,currentIdentity,false,mode)
  if(reason) throw new Error(reason)
  if(mode==='building' && entry.document?.graph && entry.mode!=='form') return insertGraphReference({document,library,materials,entry,targetId})
  const originalEntries=inspect(mode,document), originalGroups=groupViews(document,originalEntries), oldGroup=originalGroups.find(group=>group.id===targetId)
  const oldNode=originalEntries.find(entry=>entry.node.id===targetId)?.node,placement=oldGroup?.position || (oldNode?{x:oldNode.x,y:oldNode.y}:null)
  if(targetId && !placement) throw new Error('Der zu ersetzende Knoten existiert nicht mehr.')
  const removed=new Set(oldGroup?.nodes || (targetId?[targetId]:[])), originalWires=connections(originalEntries)
  const outgoing=originalWires.filter(wire=>removed.has(wire.source) && !removed.has(wire.target))
  const incoming=originalWires.filter(wire=>!removed.has(wire.source) && removed.has(wire.target))
  const base=removed.size?applyWires(mode,document,originalEntries,originalWires.filter(wire=>!removed.has(wire.source) && !removed.has(wire.target)),[...removed]):document
  let result=entry.mode==='form' && entry.collection!=='nodeGroups'?insertFormReference({mode,document:base,library,materials,entry,currentIdentity,placement}):insertGroup({mode,document:base,library,materials,entry,placement})
  const nextEntries=inspect(mode,result.document),newGroup=groupViews(result.document,nextEntries).find(group=>group.id===result.nodeId), outputs=newGroup?.outputs || [{id:result.nodeId,type:nextEntries.find(e=>e.node.id===result.nodeId).outputType}],ports=newGroup?.ports || nextEntries.find(e=>e.node.id===result.nodeId).ports.map(port=>({...port,targetNode:result.nodeId,targetPort:port.id}))
  const wires=connections(nextEntries)
  for(const wire of outgoing) {
    const port=nextEntries.find(e=>e.node.id===wire.target)?.ports.find(p=>p.id===wire.port)
    const source=outputs.find(output=>port?.accepts.includes(nextEntries.find(e=>e.node.id===output.id)?.node.kind))
    if(source) wires.push({...wire,source:source.id})
  }
  for(const wire of incoming) {
    const kind=nextEntries.find(e=>e.node.id===wire.source)?.node.kind
    const port=ports.find(port=>!port.source && port.accepts.includes(kind) && !wires.some(w=>w.target===port.targetNode && w.port===port.targetPort))
    if(port) wires.push({source:wire.source,target:port.targetNode,port:port.targetPort})
  }
  if(removed.size) result.document=applyWires(mode,result.document,nextEntries,wires)
  return result
}
export function splitVector(mode,document,nodeId,origin={x:40,y:40}) {
  const source=document.graph.nodes.find(n=>n.id===nodeId),ids=['X','Y','Z'].map(axis=>fresh(`vector_${axis}`)),groupId=fresh('vector_split')
  const nodes=ids.map((id,i)=>({...newNode(mode==='building',`POSITION_${'XYZ'[i]}`,id,position(source?source.x+260:origin.x),position((source?.y ?? origin.y)+i*110),true),inputs:[nodeId || '']}))
  const vectorType=mode==='building'?'POSITION':'position',inputPort=mode==='building'?'in:0':'0'
  const group={id:groupId,name:'Position zerlegen',nodes:ids,presentation:'vectorSplit',graphInterface:{inputs:[{id:'vector',name:'Position · Vektor3',type:vectorType,targets:ids.map((_,member)=>({member,port:inputPort}))}],outputs:ids.map((_,member)=>({id:'XYZ'[member],name:axisLabel('XYZ'[member]),type:'INT',member}))},inputConnections:nodeId?{vector:nodeId}:{}}
  const candidate={...document,graph:{...document.graph,nodes:[...document.graph.nodes,...nodes],nodeGroups:[...(document.graph.nodeGroups || []),group]}}
  return {document:replaceNodes(candidate,candidate.graph.nodes),nodeId:groupId}
}

// Frozen native definitions carry their resource and nested-definition closure.
function insertGraphReference({document,library,materials,entry,targetId}) {
 const result=entry.document.graph.nodes.find(n=>n.resultType) || entry.document.graph.nodes.find(n=>n.kind==='BUILDING')
 const type=result.resultType || 'BUILDING',key=`${identityOf(entry)}@${entry.version||1}`
 const before=document.graph.nodes.find(n=>n.id===targetId)
 if(targetId && !before)throw new Error('Die Referenz ist nicht mehr vorhanden.')

 const id=before?.id || fresh('reference'),point=before?{x:before.x,y:before.y}:{x:position(Math.max(...document.graph.nodes.map(n=>n.x||0))+280),y:100}
 const parameters=entry.document.graph.nodes.filter(n=>n.kind==='GROUP_INPUT')
 const ordered=[...(entry.document.graph.groupInputOrder||[]),...parameters.map(n=>n.id)].filter((v,i,a)=>a.indexOf(v)===i)
 const oldDefinition=document.graph.graphDefinitions?.[before?.referenceKey]
 const oldParameters=oldDefinition?.document.graph.nodes.filter(n=>n.kind==='GROUP_INPUT') || []
 const compatible=(a,b)=>a===b || a==='COUNT'&&b==='INT'
 const currentTypes=new Map(inspect('building',document).map(e=>[e.node.id,e.outputType]))
 const argumentsMap=Object.fromEntries(ordered.map(id=>{const source=before?.arguments?.[id]||'',actual=currentTypes.get(source);return [id,source&&(!actual||compatible(actual,parameters.find(p=>p.id===id)?.valueType))?source:'']}))
 const node={...newNode(true,'GRAPH_REFERENCE',id,point.x,point.y,true),name:entry.name,valueType:type,referenceKey:key,arguments:argumentsMap,libraryReference:reference(entry)}
 const resolvedNode=before?.resultType?{...before,producerKind:'GRAPH_REFERENCE',resultMode:'CONSTRUCT',referenceKey:key,valueType:type,arguments:argumentsMap,libraryReference:reference(entry),inlinePart:null,roofSettings:null,roofInputMode:null}:node
 const definition={document:structuredClone(entry.document),forms:structuredClone(entry.library?.forms || library.forms || {}),modules:structuredClone(entry.library?.modules || library.modules || {})}
 const existing=document.graph.graphDefinitions?.[key]
 const graph={...document.graph,schema:14,graphDefinitions:{...document.graph.graphDefinitions,[key]:existing||definition},nodes:before?document.graph.nodes.map(n=>n.id===id?resolvedNode:n):[...document.graph.nodes,resolvedNode]}
 // Native type editing prunes incompatible outgoing wires on version changes.
 const candidate={...document,graph}
 const checked=before?editNativeReference(document,candidate,resolvedNode):replaceNodes(candidate,graph.nodes)
 return {document:checked,library:{...library,graphs:{...library.graphs,[key]:existing||definition}},materials,nodeId:id}
}
function editNativeReference(before,candidate,node) {
 // Retain the frozen definition during native type propagation.
 return editNode('building',{...before,graph:{...before.graph,graphDefinitions:candidate.graph.graphDefinitions}},node)
}
