import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { inspect, readGraph, writeGraph, applyWires, connections } from '../src/model.js'
import { loadBuildingCopycat, loadFormCopycat } from '../src/copycat-library.js'
const library = JSON.parse(readFileSync(new URL('../../examples/library.json', import.meta.url)))
const source = { schema: 3, name: 'Meine halbe Form', groups: [{ id: 'body', name: 'Body', role: 'surface', priority: 0 }], parts: [{ source: 'copycats:copycat_slab' }] }
const entry = { id: 'my-copycat', name: 'Gespeicherte Form', mode: 'form', document: source, materials: { body: 'minecraft:gold_block' } }
const base = () => ({ schema: 2, name: 'Test', palette: { wall: 'minecraft:stone', frame: 'minecraft:glass' }, graph: { schema: 1, nodes: [
  { id: 'empty_wall', kind: 'SEGMENT', block: 'minecraft:air', length: 1, height: 1 }, newNode(true, 'BUILDING', 'building', 0, 0),
] } })
const volume = plan => plan.meshes.reduce((sum, mesh) => sum + mesh.positions.length * mesh.boxes.reduce((v, [x,y,z,X,Y,Z]) => v+(X-x)*(Y-y)*(Z-z),0),0)
function doc(kind, refs = ['a','b']) {
  const d=base()
  d.graph.nodes.push({ id: 'a', kind: 'SEGMENT', length: 1, height: 1, module: '', form: 'full@1' },
    { id: 'b', kind: 'SEGMENT', length: 1, height: 1, module: '', form: 'test_slab@1' },
    { ...newNode(true, kind, 'op', 0, 0), inputs: refs })
  return d
}
const lib = { ...library, forms: { ...library.forms, 'test_slab@1': source } }
for(const [kind, expected] of [['SEGMENT_UNION',4096],['SEGMENT_INTERSECTION',2048],['SEGMENT_DIFFERENCE',2048],['SEGMENT_SYMMETRIC_DIFFERENCE',2048]]) {
  test(`${kind}: partial geometry, wiring, preview and JSON round-trip`,()=>{
    const d=doc(kind)
    assert.equal(volume(previewGraph(true,d,lib,'op')),expected)
    const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
    assert.deepEqual(previewGraph(true,round,lib,'op'),previewGraph(true,d,lib,'op'))
    assert.deepEqual(applyWires('building',d,inspect('building',d),connections(inspect('building',d))),d)
    assert.throws(()=>applyWires('building',d,inspect('building',d),[{source:'op',target:'op',port:'in:0'}]), /Cycle/)
  })
}
test('XOR retains three identical inputs and cancels two; empty intermediates compose',()=>{
  assert.equal(volume(previewGraph(true,doc('SEGMENT_SYMMETRIC_DIFFERENCE',['a','a','a']),lib,'op')),4096)
  assert.equal(volume(previewGraph(true,doc('SEGMENT_SYMMETRIC_DIFFERENCE',['a','a']),lib,'op')),0)
  const d=doc('SEGMENT_DIFFERENCE',['a','a'])
  d.graph.nodes.push({...newNode(true,'SEGMENT_UNION','outer',0,0),inputs:['op','b']})
  assert.equal(volume(previewGraph(true,d,lib,'outer')),2048)
})
test('saved Copycat loads into building with a stable snapshot and material assignment',()=>{
  const result=loadBuildingCopycat(base(),library,entry)
  const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
  assert.equal(volume(previewGraph(true,result.document,result.library,node.id)),2048)
  assert.equal(node.surfaces.body,'minecraft:gold_block')
  const changed=loadBuildingCopycat(result.document,result.library,{...entry,document:{...source,parts:[{source:'copycats:copycat_block'}]}},node.id)
  assert.notEqual(changed.document.graph.nodes.find(n=>n.id===node.id).form,node.form)
  assert.deepEqual(changed.library.forms[node.form].parts,result.library.forms[node.form].parts)
  assert.equal(volume(previewGraph(true,result.document,changed.library,node.id)),2048)
})
test('saved source can be loaded into formgraph and exported losslessly',()=>{
  const d={schema:3,name:'Target',groups:source.groups,graph:{schema:2,nodes:[newNode(false,'SAVED','saved',0,0),{...newNode(false,'OUTPUT','result',0,0),inputs:['saved']}]}}
  const loaded=loadFormCopycat(d,entry,'saved')
  const round=readGraph(JSON.parse(JSON.stringify(writeGraph('form',loaded))),'form')
  assert.equal(volume(previewGraph(false,round,{},'')),2048)
})
test('Copycat transforms accept saved library forms',()=>{
  const result=loadBuildingCopycat(base(),library,entry)
  for(const kind of ['COPYCAT_MIRROR','COPYCAT_ROTATE']) {
    const d=structuredClone(result.document)
    d.graph.nodes.push({...newNode(true,kind,'transform',0,0),inputs:[result.nodeId],quarterTurns:1})
    assert.equal(volume(previewGraph(true,d,result.library,'transform')),2048)
  }
})
test('saved source receipts use the same UUID v3 identity as Java', async()=>{
  const { createHash } = await import('node:crypto')
  const { compileForm } = await import('../src/generated/engine.mjs')
  const bytes=createHash('md5').update('saved/receipt:r').digest()
  bytes[6]=(bytes[6]&15)|48; bytes[8]=(bytes[8]&63)|128
  const hex=bytes.toString('hex');const expected=`${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
  const saved={...newNode(false,'SAVED','saved',0,0),savedCopycat:{name:'Two materials',parts:[
    {source:'copycats:copycat_byte',group:'left',recipeSource:'r'},
    {source:'copycats:copycat_byte',group:'right',recipeSource:'r',offset:{x:8,y:0,z:0}},
  ]}}
  const d={schema:3,name:'Receipt test',groups:[{id:'left',name:'Left',role:'surface',priority:0},{id:'right',name:'Right',role:'surface',priority:1}],
    graph:{schema:2,nodes:[saved,{...newNode(false,'OUTPUT','out',0,0),inputs:['saved']}]}}
  assert.deepEqual([...new Set(compileForm(d).map(p=>p.recipeSource))],[expected])
})

test('plain forms without explicit groups and preview block states can be loaded',()=>{
  const result=loadBuildingCopycat(base(),library,{...entry,document:{schema:3,name:'Plain',parts:[{source:'copycats:copycat_slab'}]},materials:{body:'minecraft:oak_log[axis=x]'}})
  const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
  assert.equal(node.surfaces.body,'minecraft:oak_log')
  assert.equal(volume(previewGraph(true,result.document,result.library,node.id)),2048)
})
