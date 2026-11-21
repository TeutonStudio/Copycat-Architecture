import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { arrangementGraph, previewGraph, starterBuilding, wireModels } from '../src/generated/engine.mjs'
import { readGraph, writeGraph } from '../src/model.js'
const library=JSON.parse(readFileSync(new URL('../../examples/library.json',import.meta.url)))
function fixture(offset=5) {
  const d=starterBuilding()
  for(const n of d.graph.nodes) {
    if(n.id==='wall_layout')n.depth=2
    if(n.id==='wall')n.depth=2
    if(n.id==='floor' || n.id==='ceiling')n.depth=2
    if(n.id==='plate_layout'){n.length=7;n.height=7;n.depth=2}
    if(n.id==='room')n.joinWallEnds=false
    if(n.id==='storey'){n.inputs=['room','room'];n.offsets=[{x:0,y:0,z:0},{x:offset,y:0,z:0}]}
  }
  return d
}
test('native wire codec exposes nullable wall and facade thickness fields',()=>{
  const models=wireModels()
  assert.equal(models.RoomConfig.fields.wallThickness,'Map<String,Int>?')
  assert.equal(models.FacadeConfig.fields.thickness,'Int?')
})
test('two block wall intrusion is visible and blocks preview while the room grid stays repairable',()=>{
  const d=fixture(4)
  const model=arrangementGraph(d,library,'storey','Y')
  assert.equal(model.items.length,2)
  assert.ok(model.items.every(item=>item.cells.length>0 && item.walls.length>0))
  assert.ok(model.conflicts.some(c=>c.kind==='WALL_IN_INTERIOR' && c.first!==c.second))
  assert.ok(model.conflicts.some(c=>c.cells.some(([x,z])=>x===4&&z===0)))
  assert.throws(()=>previewGraph(true,d,library,'storey'),/intrudes into the interior/)
  d.graph.nodes.find(n=>n.id==='storey').offsets[1].x=5
  assert.deepEqual(arrangementGraph(d,library,'storey','Y').conflicts,[])
  assert.ok(previewGraph(true,d,library,'storey').count>0)
})
test('room wall and facade strengths survive export and import without unit conversion',()=>{
  const d=fixture()
  d.graph.nodes.find(n=>n.id==='room').roomConfig={mode:'FOOTPRINT',heightOverride:null,interiorSource:'',defaultPartition:'',wallThickness:{north:24,east:32,'wall:0':8}}
  d.graph.nodes.find(n=>n.id==='storey').facade={source:'',filler:'',thickness:12}
  const restored=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
  assert.deepEqual(restored.graph.nodes.find(n=>n.id==='room').roomConfig.wallThickness,{north:24,east:32,'wall:0':8})
  assert.equal(restored.graph.nodes.find(n=>n.id==='storey').facade.thickness,12)
  assert.deepEqual(arrangementGraph(restored,library,'storey','Y').conflicts,[])
})
test('overlapping interiors receive a separate conflict kind',()=>{
  const model=arrangementGraph(fixture(1),library,'storey','Y')
  assert.ok(model.conflicts.some(c=>c.kind==='INTERIORS'))
})
