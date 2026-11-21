import { editNativeGroup,importGraphInterface, legacyStarterCard, starterCard, newNode, normalizeDocument, remapBuildingReferences, cardForm } from './generated/engine.mjs'
import {saveNodeGroup} from './node-groups.js'
import { buildingTemplate } from './reference-groups.js'
import { applyWires, connections, formGroups, inspect, readGraph, replaceNodes, writeGraph } from './model.js'
import { cardResult } from './card-types.js'

const fresh=prefix=>`${prefix}_${crypto.randomUUID().replaceAll('-','').slice(0,14)}`
export const newCard=()=>starterCard()
export const legacyCard=()=>legacyStarterCard()
function attachResult(document, type, source='') {
  if(document.graph.nodes.some(n=>n.resultType)) return document
  const result={...newNode(true,'BUILDING',fresh('result'),600,100,true),name:'Ergebnis',resultType:type,inputs:[source],levels:{},roof:''}
  const graph={...document.graph,schema:8,nodes:[...document.graph.nodes,result]}
  return replaceNodes({...document,graph},graph.nodes)
}
export function openCard(entry, fallbackLibrary={forms:{},modules:{}}) {
  const library=structuredClone(entry.library || fallbackLibrary),materials=structuredClone(entry.materials || {})
  if(entry.collection==='nodeGroups'&&Array.isArray(entry.nodes)) {
    const template=entry.mode==='form'?buildingTemplate(entry):structuredClone(entry)
    let document=newCard();document={...document,name:entry.name,palette:{...document.palette,...template.palette},cardInterface:template.graphInterface,
      graph:{...document.graph,graphDefinitions:template.graphDefinitions || null,nodes:[document.graph.nodes.find(n=>n.id==='empty_wall'),...template.nodes,{...document.graph.nodes.find(n=>n.resultType),id:fresh('result'),inputs:['']}],nodeGroups:template.nodeGroups || []}}
    const ids=new Set(template.nodes.map(n=>n.id))
    // A definition keeps its own IDs and local values; only external references are detached.
    document.graph.nodeGroups=document.graph.nodeGroups.map(group=>({...group,inputConnections:Object.fromEntries(Object.entries(group.inputConnections || {}).filter(([,source])=>ids.has(source)))}))
    document.graph.nodes=document.graph.nodes.map(n=>ids.has(n.id)?remapBuildingReferences(n,Object.fromEntries([...ids].map(id=>[id,id]))):n)
    if(template.graphInterface?.inputs?.length) {document={...document,cardInterface:null,graph:importGraphInterface(document,template.graphInterface,template.nodes.map(n=>n.id))}}
    document=replaceNodes(document,document.graph.nodes)
    const existing=inspect('building',document)
    document=applyWires('building',document,existing,[...connections(existing),...template.wires])
    const rows=inspect('building',document),used=new Set(template.wires.map(w=>w.source))
    const output=template.graphInterface?.outputs.find(o=>o.member>=0)
    const source=output?template.nodes[output.member]?.id:template.nodes.find(n=>!used.has(n.id))?.id || template.nodes.at(-1)?.id
    const declared={shape:'COPYCAT',segment:'WALL',position:'POSITION',rotation:'ROTATION',mirroring:'MIRRORING',transformation:'TRANSFORMATION'}[output?.type] || output?.type
    const type=rows.find(row=>row.node.id===source)?.outputType || declared || 'COPYCAT'
    const result=cardResult(document)
    document=replaceNodes(document,document.graph.nodes.map(n=>n.id===result.id?{...n,resultType:type,resultMode:'VALUE',resultSource:source||'',inputs:source?[source]:[]}:n))
    return {document,library:{...library,forms:{...library.forms,...template.library?.forms},modules:{...library.modules,...template.library?.modules}},materials}
  }
  const raw=entry.document || entry
  const isForm=entry.mode==='form' || raw.graph?.nodes.some(n=>n.kind==='OUTPUT') || !raw.graph && raw.parts
  if(!isForm) {
    let document=readGraph(raw,'building');const result=cardResult(document)
    document=attachResult(document,'BUILDING',result.id)
    if(document.cardInterface?.inputs?.length&&!document.graph.nodes.some(n=>n.kind==='GROUP_INPUT'))document={...document,graph:importGraphInterface(document,document.cardInterface,(entry.nodes||document.graph.nodes.filter(n=>n.id!=='empty_wall'&&!n.resultType)).map(n=>n.id)),cardInterface:null}
    return {document,library,materials}
  }
  const form=readGraph(raw,'form'),rows=inspect('form',form),output=form.graph.nodes.find(n=>n.kind==='OUTPUT')
  const template=buildingTemplate({name:form.name,mode:'form',document:form,nodes:form.graph.nodes.filter(n=>n.kind!=='OUTPUT'),wires:connections(rows).filter(w=>w.target!==output.id),groups:formGroups(form),materials,library})
  const result=openCard({...template,mode:'building',collection:'nodeGroups'},library)
  const terminal=cardResult(result.document)
  const source=output.inputs[0] || ''
  result.document=replaceNodes(result.document,result.document.graph.nodes.map(n=>n.id===terminal.id?{...n,resultMode:'VALUE',resultSource:source,resultType:rows.find(row=>row.node.id===source)?.outputType==='shape'?'COPYCAT':rows.find(row=>row.node.id===source)?.outputType || 'COPYCAT',inputs:[source]}:n))
  result.document.name=form.name
  return result
}
export function graphCardEntry(entry,library) {
  return {...entry,mode:'building',...openCard(entry,library)}
}

export function copycatCardEntry(entry,fallbackLibrary) {
 if(entry.mode==='form')return entry
 const result=cardForm(entry.document,entry.library || fallbackLibrary)
 return {...entry,mode:'form',document:result.form,materials:result.materials}
}

// New saved selections use the same document and public interface as graph cards.
export function selectionCard(mode,document,ids,name,materials,library) {
 let template=saveNodeGroup(mode,document,ids,name,materials,library)
 if(!template.graphInterface) {
  const grouped={...document,graph:editNativeGroup(mode==='building',document,'create','',name,ids)}
  const group=grouped.graph.nodeGroups.at(-1)
  const initialized={...grouped,graph:editNativeGroup(mode==='building',grouped,'initialize',group.id,'',[])}
  template=saveNodeGroup(mode,initialized,ids,name,materials,library)
  template.graphInterface={...template.graphInterface,inputs:template.graphInterface.inputs.map((input,i)=>({...input,name:`Eingang ${i+1}`}))}
 }
 const opened=openCard({...template,collection:'nodeGroups'},library)
 return {...opened,mode:'building',name:template.name,isGroup:opened.document.graph.nodes.some(n=>n.kind==='GROUP_INPUT')}
}
