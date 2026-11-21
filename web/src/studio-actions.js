import { duplicateSelection, applyWires, connections, inspect, position, model } from './model.js'
import { identityOf } from './documents.js'
import { loadBuildingCopycat, loadFormCopycatWithMaterials } from './copycat-library.js'

// A saved version's layout is a local viewing preference, never a library edit.
export function viewLayout(document, positions = {}) {
  return {...document,graph:{...document.graph,nodes:document.graph.nodes.map(node => positions[node.id] ? {...node,x:position(positions[node.id].x),y:position(positions[node.id].y)} : node)}}
}
export function duplicateNodes(mode, document, ids, copies, withConnections = true) {
  let next = duplicateSelection(mode, document, ids, copies)
  if (withConnections) {
    const mapping=new Map(ids.map((id,index)=>[id,copies[index]])), copied=new Set(copies), entries=inspect(mode,next)
    const incoming=connections(inspect(mode,document)).filter(wire=>mapping.has(wire.target)).map(wire=>({...wire,target:mapping.get(wire.target),source:mapping.get(wire.source) || wire.source}))
    return applyWires(mode,next,entries,[...connections(entries).filter(wire=>!copied.has(wire.target)),...incoming])
  }
  const originalGroups = new Set((document.graph.nodeGroups || []).map(group => group.id))
  next = {...next,graph:{...next.graph,nodeGroups:(next.graph.nodeGroups || []).map(group => originalGroups.has(group.id) ? group : {...group,definitionId:group.id,inputConnections:{}})}}
  const entries = inspect(mode,next), copied = new Set(copies)
  const internals=(next.graph.nodeGroups || []).filter(group=>!originalGroups.has(group.id) && (group.libraryReference || group.presentation==='vectorSplit')).map(group=>new Set(group.nodes))
  return applyWires(mode,next,entries,connections(entries).filter(wire => !copied.has(wire.target) || internals.some(members=>members.has(wire.source) && members.has(wire.target))))
}
export function referenceDisabled(entry, currentIdentity, readOnly = false, mode) {
  if (readOnly) return 'Gespeicherte Versionen können nur betrachtet werden. Zuerst Ungespeichert wählen.'
  if (!entry) return 'Einen Bibliothekseintrag auswählen.'
  if (identityOf(entry) === currentIdentity) return 'Keine Version des eigenen Graphen als Referenz einfügen.'
  if(mode==='form' && entry.mode==='building') return 'Diese Referenz benötigt den Gebäudegraphen.'
  if(mode==='building' && entry.collection==='nodeGroups' && entry.mode==='form') return ''
  return ''
}
export function insertFormReference({mode,document,library,materials,entry,currentIdentity,placement=null}) {
  const reason = referenceDisabled(entry,currentIdentity,false,mode)
  if (reason) throw new Error(reason)
  const result = mode === 'building' ? {...loadBuildingCopycat(document,library,entry),materials} : {...loadFormCopycatWithMaterials(document,entry,undefined,materials),library}
  let point=placement
  if(!point) {
    const occupied=inspect(mode,document).map(({node,ports})=>({x:node.x,y:node.y,height:70+ports.length*24}))
    const top=Math.min(...occupied.map(node=>node.y)), right=Math.max(...occupied.map(node=>node.x)), left=Math.min(...occupied.map(node=>node.x))
    const available=point=>point.x>=model.positionMin && point.x<=model.positionMax && point.y>=model.positionMin && point.y<=model.positionMax && occupied.every(node=>point.x+220<node.x || point.x>node.x+220 || point.y+110<node.y || point.y>node.y+node.height)
    point=[{x:right+260,y:top},{x:left-260,y:top}].find(available)
    if(!point) for(let y=model.positionMin;y<=model.positionMax && !point;y+=180) for(let x=model.positionMin;x<=model.positionMax;x+=260) if(available({x,y})) {point={x,y};break}
    if(!point) throw new Error('Kein freier Platz für den Referenzknoten.')
  }
  const reference = {documentId:identityOf(entry),version:entry.version || 1,name:entry.name}
  result.document = {...result.document,graph:{...result.document.graph,nodes:result.document.graph.nodes.map(node => node.id === result.nodeId ? {...node,...point,libraryReference:reference} : node)}}
  return result
}
export function savedReferences(document) {
 const refs=new Set(),seen=new Set()
 function visit(graph) {
  for(const group of graph.nodeGroups||[])if(group.libraryReference)refs.add(`${group.libraryReference.documentId}@${group.libraryReference.version}`)
  for(const node of graph.nodes||[]) {if(node.libraryReference)refs.add(`${node.libraryReference.documentId}@${node.libraryReference.version}`);if(node.referenceKey)refs.add(node.referenceKey)}
  for(const [key,definition]of Object.entries(graph.graphDefinitions||{}))if(!seen.has(key)){seen.add(key);visit(definition.document.graph)}
 }
 visit(document.graph);return [...refs]
}

const noMaterials=Object.freeze({})
export function versionResources(entry,draftLibrary,draftMaterials,fallbackLibrary) {
  return entry ? {library:entry.library || fallbackLibrary,materials:entry.materials || noMaterials} : {library:draftLibrary,materials:draftMaterials}
}
