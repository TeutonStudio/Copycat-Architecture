import {axisLabel} from '../src/axis-labels.js'
import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {newNode,compileForm,previewGraph} from '../src/generated/engine.mjs'
import {model,replaceNodes,inspect,applyWires,connections,writeGraph} from '../src/model.js'
import {insertSavedReference,groupViews,splitVector,viewWires,resolveViewConnection,moveViewNodes,previewReferenceEntry,libraryReferenceEntries} from '../src/reference-groups.js'
import {saveNodeGroup} from '../src/node-groups.js'
import {duplicateNodes,savedReferences,referenceDisabled} from '../src/studio-actions.js'
import {mirrorNormals,mirrorPolygon} from '../src/mirror-planes.js'
import {appendVersion,dependencies} from '../src/documents.js'
const empty=()=>({schema:3,name:'Form',graph:{schema:2,nodes:[{id:'result',kind:'OUTPUT',inputs:['']}]}})
const building=()=>({schema:2,name:'Gebäude',graph:{schema:1,nodes:[{id:'empty_wall',kind:'SEGMENT',block:'minecraft:air',length:1},{id:'result',kind:'BUILDING',name:'Ergebnis'}]}})
const library=JSON.parse(readFileSync(new URL('../../examples/library.json',import.meta.url)))
const node=(kind,id,mode='form')=>newNode(mode==='building',kind,id,20,20,true)
const complete=()=>{const doc=empty(),source={...node('INPUT','source'),part:{...node('INPUT','source').part,source:'copycats:copycat_byte'}};return replaceNodes(doc,[source,{...doc.graph.nodes[0],inputs:['source']}])}

test('each form node can be inserted into a legacy graph without rotation schema errors',()=>{
  for(const {kind} of model.form.filter(n=>n.kind!=='OUTPUT')) assert.doesNotThrow(()=>inspect('form',replaceNodes(complete(),[...complete().graph.nodes,node(kind,'added')])),'Insertion '+kind)
})
test('position and mirror operators always expose their typed parameter ports',()=>{
  const moved=replaceNodes(complete(),[...complete().graph.nodes,node('TRANSLATE','move'),node('MIRROR','mirror'),node('MIRRORING','planes')])
  const rows=inspect('form',moved)
  assert.deepEqual(rows.find(e=>e.node.id==='move').ports.map(p=>p.types),[['shape'],['position']])
  assert.deepEqual(rows.find(e=>e.node.id==='mirror').ports[1].types,['mirroring'])
  assert.equal(rows.find(e=>e.node.id==='planes').outputType,'mirroring')
  const doc=replaceNodes(building(),[...building().graph.nodes,node('COPYCAT_POSITION','move','building')])
  assert.deepEqual(inspect('building',doc).find(e=>e.node.id==='move').ports.map(p=>p.id),['in:0','positionValue'])
})
test('a vector is composed from integer arithmetic and splits into a single node with X Y Z outputs',()=>{
  let doc=replaceNodes(complete(),[...complete().graph.nodes,{...node('INT','a'),countValue:2},{...node('INT','b'),countValue:3},{...node('INT_ADD','sum'),inputs:['a','b']},{...node('POSITION','vector'),inputs:['sum','a','b']}])
  assert.equal(doc.graph.schema,5)
  const split=splitVector('form',doc,'vector');doc=split.document
  const rows=inspect('form',doc),views=groupViews(doc,rows),group=views[0]
  assert.equal(group.outputs.length,3)
  assert.deepEqual(group.outputs.map(o=>o.name),['X','Y','Z'].map(axisLabel))
  assert.deepEqual(group.outputs.map(o=>o.type),['INT','INT','INT'])
  assert.equal(viewWires(connections(rows),views).filter(w=>w.viewTarget===group.id).length,1)
  assert.match(previewGraph(false,doc,{},group.outputs[0].id).value,/5/)
  const resolved=resolveViewConnection({source:'vector',target:group.id,targetHandle:group.ports[0].id},views)
  assert.equal(resolved.targets.length,3)
  const moved=moveViewNodes(doc,views,[{id:group.id,position:{x:group.position.x+32,y:group.position.y+32}}])
  assert.equal(Object.keys(moved).length,3)
  assert.ok(group.nodes.every(id=>moved[id].x===doc.graph.nodes.find(n=>n.id===id).x+32))
})
test('all numeric and vector operations evaluate the same typed values',()=>{
  for(const [operation,expected] of [['ADD',8],['SUBTRACT',4],['MULTIPLY',12],['DIVIDE',3],['MIN',2],['MAX',6]]) {
    const doc=replaceNodes(complete(),[...complete().graph.nodes,{...node('INT','a'),countValue:6},{...node('INT','b'),countValue:2},{...node('INT_'+operation,'op'),inputs:['a','b']}])
    assert.equal(previewGraph(false,doc,{},'op').value,`Ganzzahl: ${expected}`)
    const vector=replaceNodes(complete(),[...complete().graph.nodes,{...node('POSITION','a'),translation:{x:6,y:6,z:6}},{...node('POSITION','b'),translation:{x:2,y:2,z:2}},{...node('POSITION_'+operation,'op'),inputs:['a','b']}])
    assert.match(previewGraph(false,vector,{},'op').value,new RegExp(`x=${expected}, y=${expected}, z=${expected}`))
  }
})
test('all mirror previews have a visible plane, including diagonal planes',()=>{
  for(const plane of Object.keys(mirrorNormals)) {
    const polygon=mirrorPolygon(plane)
    assert.ok(polygon.length>=3,plane)
    const area=Math.abs(polygon.reduce((sum,p,i)=>{const q=polygon[(i+1)%polygon.length];return sum+p[0]*q[1]-q[0]*p[1]},0))/2
    assert.ok(area>1,plane)
  }
})
test('saved node groups become a single typed reference and lock their exact version',()=>{
  const source=replaceNodes(complete(),[...complete().graph.nodes,{...node('INT','value'),countValue:7}])
  const entry={...saveNodeGroup('form',source,['value'],'Zahl'),collection:'nodeGroups',documentId:'integerGroup',version:2}
  const inserted=insertSavedReference({mode:'form',document:complete(),library,materials:{},entry})
  const views=groupViews(inserted.document,inspect('form',inserted.document))
  assert.equal(views.length,1);assert.equal(views[0].outputs[0].type,'INT')
  assert.equal(previewGraph(false,inserted.document,{},views[0].outputs[0].id).value,'Ganzzahl: 7')
  assert.deepEqual(savedReferences(inserted.document),['integerGroup@2'])
  assert.ok(dependencies({graphs:[],nodeGroups:[],drafts:{form:{document:inserted.document}}},entry).length)
  const exported=writeGraph('form',inserted.document)
  assert.equal(exported.graph.nodeGroups[0].libraryReference.version,2)
  assert.match(referenceDisabled(entry,'integerGroup'),/eigenen/)
})
test('form groups are available as typed references inside building graphs',()=>{
  const source=replaceNodes(complete(),[...complete().graph.nodes,{...node('INT','value'),countValue:7}])
  const entry={...saveNodeGroup('form',source,['value'],'Zahl'),collection:'nodeGroups',documentId:'formNumber',version:1}
  const inserted=insertSavedReference({mode:'building',document:building(),library,materials:{},entry})
  const views=groupViews(inserted.document,inspect('building',inserted.document))
  assert.equal(views[0].outputs[0].type,'INT')
  assert.equal(previewGraph(true,inserted.document,inserted.library,views[0].outputs[0].id).value,'Ganzzahl: 7')
})
test('library modules can be previewed and referenced as segments',()=>{
  const entry=libraryReferenceEntries(library).find(e=>e.collection==='libraryModules')
  assert.ok(entry)
  assert.ok(previewReferenceEntry(entry,library).count>0)
  const inserted=insertSavedReference({mode:'building',document:building(),library,materials:{},entry})
  const view=groupViews(inserted.document,inspect('building',inserted.document))[0]
  assert.equal(view.outputs[0].type,'WALL')
  assert.ok(previewGraph(true,inserted.document,inserted.library,view.previewId).count>0)
})
test('saved whole buildings have a BUILDING reference output and isolated palette roles',()=>{
  const raw=JSON.parse(readFileSync(new URL('../../examples/building-graph-assembly.json',import.meta.url)))
  const entry={id:'saved-building',documentId:'house',version:3,name:'Haus',mode:'building',collection:'graphs',document:raw,library}
  const inserted=insertSavedReference({mode:'building',document:building(),library,materials:{},entry})
  const ref=inspect('building',inserted.document).find(e=>e.node.id===inserted.nodeId)
  assert.equal(ref.outputType,'BUILDING')
  assert.equal(ref.node.kind,'GRAPH_REFERENCE')
  assert.equal(previewGraph(true,inserted.document,inserted.library,ref.node.id).count,previewGraph(true,raw,library,'').count)
})
test('next immutable version numbers account for existing identities and versions',()=>{
  const entries=appendVersion(appendVersion([], {name:'Graph',mode:'form',document:complete()},'graph'),{name:'Graph',mode:'form',document:complete()},'graph')
  assert.equal(appendVersion(entries,{name:'Graph',mode:'form',document:complete()},'graph').at(-1).version,3)
})

test('unconnected vector split is safe to instantiate and exposes one vector socket',()=>{
  for(const mode of ['form','building']) {
    const result=splitVector(mode,mode==='form'?complete():building(),null)
    const group=groupViews(result.document,inspect(mode,result.document))[0]
    assert.equal(group.ports.length,1)
    assert.equal(group.outputs.length,3)
    assert.equal(group.ports[0].source,'')
  }
})
test('column mode uses the native 2 by 2 block footprint and preserves materials',()=>{
  const doc=JSON.parse(readFileSync(new URL('../../examples/building-rotation-segment.json',import.meta.url)))
  const overlay=previewGraph(true,doc,library,'column')
  const adjacent=replaceNodes(doc,doc.graph.nodes.map(n=>n.id==='column'?{...n,columnMode:'ADJACENT'}:n))
  assert.equal(adjacent.graph.schema,6)
  const plan=previewGraph(true,adjacent,library,'column')
  assert.equal(plan.count,overlay.count*4)
  assert.deepEqual(new Set(Object.keys(plan.materials)),new Set(Object.keys(overlay.materials)))
  assert.equal(new Set(plan.meshes.flatMap(m=>m.positions).map(p=>p[0])).size,2)
})
test('saved split groups preserve their named multi-output interface',()=>{
  const doc=replaceNodes(complete(),[...complete().graph.nodes,node('POSITION','vector')]),split=splitVector('form',doc,'vector')
  const original=groupViews(split.document,inspect('form',split.document))[0]
  const entry={...saveNodeGroup('form',split.document,original.nodes,'Zerlegen'),collection:'nodeGroups',documentId:'split',version:1}
  for(const mode of ['form','building']) {
    const inserted=insertSavedReference({mode,document:mode==='form'?complete():building(),library,materials:{},entry})
    const view=groupViews(inserted.document,inspect(mode,inserted.document))[0]
    assert.deepEqual(view.outputs.map(o=>o.name),['X','Y','Z'].map(axisLabel))
    assert.equal(view.ports.length,1)
    assert.equal(view.ports[0].targets.length,3)
  }
})

test('duplicating a reference without external connections preserves its internal recipe',()=>{
  const base=replaceNodes(complete(),[...complete().graph.nodes,{...node('INT','a'),countValue:2},{...node('INT','b'),countValue:3},{...node('INT_ADD','sum'),inputs:['a','b']}])
  const entry={...saveNodeGroup('form',base,['a','b','sum'],'Summe'),collection:'nodeGroups',documentId:'sum',version:2}
  const inserted=insertSavedReference({mode:'form',document:complete(),library,materials:{},entry}),view=groupViews(inserted.document,inspect('form',inserted.document))[0]
  const copy=duplicateNodes('form',inserted.document,view.nodes,view.nodes.map((_,i)=>'copy'+i),false),views=groupViews(copy,inspect('form',copy))
  assert.equal(views.length,2)
  assert.equal(previewGraph(false,copy,{},views[1].outputs[0].id).value,'Ganzzahl: 5')
  assert.deepEqual(savedReferences(copy),['sum@2'])
})
