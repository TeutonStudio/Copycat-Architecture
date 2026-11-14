import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import {newNode,previewGraph,inspectorContext,editNativeGroup} from '../src/generated/engine.mjs'
import {applyWires,connections,inspect,readGraph,replaceNodes,writeGraph} from '../src/model.js'
const example=JSON.parse(readFileSync(new URL('../../examples/building-windows.json',import.meta.url)))
const library=JSON.parse(readFileSync(new URL('../../examples/library.json',import.meta.url)))
const patch=(doc,id,next)=>replaceNodes(doc,doc.graph.nodes.map(n=>n.id===id?{...n,...next}:n))
const storey=doc=>doc.graph.nodes.find(n=>n.id==='storey')
const preview=doc=>previewGraph(true,doc,library,'storey')

test('window definition and storey instances expose typed native connections',()=>{
 const entries=inspect('building',example)
 assert.equal(entries.find(e=>e.node.id==='window').outputType,'WINDOW')
 assert.ok(entries.find(e=>e.node.id==='storey').ports.filter(p=>p.id.startsWith('semantic:window:')).every(p=>p.source==='window'&&p.types.includes('WINDOW')))
 assert.equal(newNode(true,'WINDOW','new',0,0).inputs.length,1)
 assert.deepEqual(applyWires('building',example,entries,connections(entries)),example)
 assert.throws(()=>applyWires('building',example,entries,connections(entries).map(w=>w.port==='semantic:window:0:source'?{...w,source:'glass'}:w)),/Incompatible/)
 const walls=inspectorContext(example,'storey').windowWalls
 assert.deepEqual(walls.map(w=>w.direction),[0,1,2,3])
})
test('every wall direction, independent repeated instances and roundtrip retain window geometry',()=>{
 const walls=inspectorContext(example,'storey').windowWalls
 for(const wall of walls){
  const doc=patch(example,'storey',{windows:[{...storey(example).windows[0],edge:wall.edge}]})
  const before=preview(doc)
  assert.deepEqual(preview(readGraph(writeGraph('building',doc),'building')),before)
  assert.equal(before.count,preview(patch(doc,'storey',{windows:[]})).count)
 }
 const node=storey(example)
 const moved=patch(example,'storey',{windows:node.windows.map((w,i)=>i? w :{...w,offset:3,sill:2})})
 assert.notDeepEqual(preview(moved).meshes,preview(example).meshes)
 assert.deepEqual(preview(patch(moved,'storey',{windows:node.windows})),preview(example))
})
test('invalid placement and disabled automatic opening give clear errors',()=>{
 for(const fields of [{offset:128},{sill:6},{edge:'missing'},{depthOffset:128},{autoOpening:false}]){
  const doc=patch(example,'storey',{windows:storey(example).windows.map((w,i)=>i?w:{...w,...fields})})
  assert.throws(()=>preview(doc),/Fenster/)
 }
})
test('groups duplicate the geometry definition with its segment reference',()=>{
 const grouped={...example,graph:editNativeGroup(true,example,'create','','Fenster',['glass','window'])}
 const instance={...grouped,graph:editNativeGroup(true,grouped,'instantiate',grouped.graph.nodeGroups[0].id,'',[])}
 const copy=instance.graph.nodes.find(n=>n.kind==='WINDOW'&&n.id!=='window')
 assert.ok(copy);assert.notEqual(copy.inputs[0],'glass')
 assert.deepEqual(previewGraph(true,instance,library,copy.id),previewGraph(true,example,library,'window'))
})
