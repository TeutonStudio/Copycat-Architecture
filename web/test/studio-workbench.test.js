import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {connections,inspect,readGraph,writeGraph} from '../src/model.js'
import {editNativeGroup,previewGraph} from '../src/generated/engine.mjs'
import {dependencies,deleteVersion} from '../src/documents.js'
import {duplicateNodes,insertFormReference,referenceDisabled,savedReferences,viewLayout,versionResources} from '../src/studio-actions.js'
import {compassDirections,axisColors} from '../src/orientation.js'
const fixture=name=>JSON.parse(readFileSync(new URL(`../../examples/${name}.json`,import.meta.url)))
const source=()=>readGraph({schema:3,name:'Byte',parts:[{source:'copycats:copycat_byte'}]},'form')

test('saved-version layout changes are isolated from saved content, transforms and draft',()=>{
 const saved=source(), original=structuredClone(saved),id=saved.graph.nodes[0].id
 const view=viewLayout(saved,{[id]:{x:183.7,y:22.4}})
 assert.deepEqual(saved,original)
 assert.equal(view.graph.nodes[0].x,184)
 assert.equal(view.graph.nodes[0].y,22)
 assert.deepEqual(writeGraph('form',view).parts,writeGraph('form',saved).parts)
 assert.equal(previewGraph(false,view,{},'').count,previewGraph(false,saved,{},'').count)
})
test('both duplicate commands preserve local data; only connected duplicates retain internal wires',()=>{
 const document=readGraph(fixture('nodegraph-form'),'form'),ids=document.graph.nodes.filter(n=>!['OUTPUT'].includes(n.kind)).map(n=>n.id),copies=ids.map((_,i)=>`copy${i}`),original=structuredClone(document)
 const linked=duplicateNodes('form',document,ids,copies,true),isolated=duplicateNodes('form',document,ids,copies,false)
 const targets=wires=>wires.filter(wire=>copies.includes(wire.target))
 assert.ok(targets(connections(inspect('form',linked))).length>0)
 assert.equal(targets(connections(inspect('form',isolated))).length,0)
 assert.deepEqual(document,original)
 for(const [index,id] of ids.entries()) {
  const original=document.graph.nodes.find(n=>n.id===id),copy=isolated.graph.nodes.find(n=>n.id===copies[index])
  assert.equal(copy.x,(original.x||0)+32);assert.deepEqual(copy.part,original.part)
 }
})
test('disconnected copies own their group definition and leave original instances unchanged',()=>{
 let document=readGraph(fixture('nodegraph-form'),'form')
 const ids=document.graph.nodes.filter(n=>n.kind !== 'OUTPUT').map(n=>n.id)
 document={...document,graph:editNativeGroup(false,document,'create','','Original',ids)}
 const original=structuredClone(document),copies=ids.map((_,i)=>`copy${i}`)
 const next=duplicateNodes('form',document,ids,copies,false)
 assert.deepEqual(document,original)
 assert.deepEqual(next.graph.nodes.filter(n=>ids.includes(n.id)),original.graph.nodes.filter(n=>ids.includes(n.id)))
 const group=next.graph.nodeGroups.find(g=>g.nodes.includes(copies[0]))
 assert.equal(group.definitionId,group.id)
 assert.equal(connections(inspect('form',next)).filter(w=>copies.includes(w.target)).length,0)
})
test('every version of the current identity is excluded from reference insertion',()=>{
 for(const version of [1,2,3]) assert.match(referenceDisabled({id:`v${version}`,documentId:'self',version,mode:'form'},'self'),/eigenen/)
 assert.match(referenceDisabled({id:'other',mode:'form'},'self',true),/betrachtet/)
 assert.equal(referenceDisabled({id:'other',mode:'form'},'self'),'')
})
test('form references pin the exact version and materials, stay independent and block deletion',()=>{
 const entry={id:'entry-v2',documentId:'external',version:2,mode:'form',name:'Library byte',document:source(),materials:{body:'minecraft:copper_block'}}
 const original=structuredClone(entry),result=insertFormReference({mode:'form',document:source(),library:{forms:{},modules:{}},materials:{},entry,currentIdentity:'self'})
 const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
 assert.equal(node.savedCopycat.version,2);assert.equal(node.libraryReference.documentId,'external')
 assert.deepEqual(savedReferences(result.document),['external@2'])
 assert.ok(Object.values(result.materials).includes('minecraft:copper_block'))
 assert.deepEqual(entry,original)
 const data={graphs:[entry],nodeGroups:[],drafts:{form:{document:result.document}}}
 assert.equal(dependencies(data,entry).length,1)
 assert.throws(()=>deleteVersion(data,'graphs',entry.id),/verwendet/)
 assert.deepEqual(dependencies(data,{...entry,id:'v1',version:1}),[])
 const roundtrip=writeGraph('form',result.document)
 assert.deepEqual(roundtrip.graph.nodes.find(n=>n.id===result.nodeId).libraryReference,node.libraryReference)
})
test('building reference inserts a Copycat node with a frozen exact library version',()=>{
 const entry={id:'external-v3',documentId:'external',version:3,mode:'form',name:'Byte',document:source(),materials:{body:'minecraft:stone'}}
 const result=insertFormReference({mode:'building',document:readGraph(fixture('building-graph-assembly'),'building'),library:fixture('library'),materials:{},entry,currentIdentity:'building'})
 const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
 assert.equal(node.form,'browser_external@3');assert.equal(node.libraryReference.version,3)
 assert.equal(result.library.forms[node.form].version,3)
})
test('compass cardinal anchors and axis colors agree with Minecraft coordinates',()=>{
 assert.equal(compassDirections.length,4)
 for(const d of compassDirections) {
  assert.equal(d.x*d.x+d.z*d.z,1)
  assert.ok(axisColors[d.axis]);assert.ok(d.label.includes(d.axis.toUpperCase()))
 }
 assert.equal(compassDirections.find(d=>d.label.startsWith('Nord')).z,-1)
 assert.equal(compassDirections.find(d=>d.label.startsWith('Ost')).x,1)
})

test('legacy version previews never inherit the current draft material mappings or library',()=>{
 const fallback={forms:{old:{version:1}}},draftLibrary={forms:{new:{version:2}}},draftMaterials={body:'minecraft:gold_block'}
 const saved=versionResources({document:{}},draftLibrary,draftMaterials,fallback)
 assert.equal(saved.library,fallback);assert.deepEqual(saved.materials,{})
 const entry={library:{forms:{exact:{version:3}}},materials:{body:'minecraft:copper_block'}}
 assert.equal(versionResources(entry,draftLibrary,draftMaterials,fallback).materials,entry.materials)
 assert.equal(versionResources(null,draftLibrary,draftMaterials,fallback).materials,draftMaterials)
})

test('duplicate with connections keeps incoming external sources and remaps only selected sources',()=>{
 const document=readGraph(fixture('nodegraph-form'),'form'), original=structuredClone(document)
 const next=duplicateNodes('form',document,['in0','op'],['source_copy','operator_copy'],true)
 const inputs=connections(inspect('form',next)).filter(wire=>wire.target === 'operator_copy')
 assert.deepEqual(new Set(inputs.map(wire=>wire.source)),new Set(['source_copy','in1']))
 assert.deepEqual(document,original)
 assert.deepEqual(next.graph.nodes.find(n=>n.id === 'result'),original.graph.nodes.find(n=>n.id === 'result'))
 const isolated=duplicateNodes('form',document,['op'],['isolated'],false)
 assert.equal(connections(inspect('form',isolated)).filter(wire=>wire.target === 'isolated').length,0)
})

test('inserted reference is placed clear of existing graph nodes, within graph bounds',()=>{
 const document=readGraph(fixture('nodegraph-form'),'form'),entry={id:'reference',mode:'form',version:1,name:'External',document:source()}
 const result=insertFormReference({mode:'form',document,entry,library:{forms:{},modules:{}},materials:{},currentIdentity:'self'})
 const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
 assert.ok(node.x>Math.max(...inspect('form',document).map(e=>e.node.x))+220)
 assert.ok(node.x<=4096 && node.x>=-4096 && node.y<=4096 && node.y>=-4096)
})
