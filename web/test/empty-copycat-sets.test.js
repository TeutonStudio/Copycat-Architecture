import nativeTest from 'node:test'
import assert from 'node:assert/strict'
import {newNode, previewGraph, compileForm} from '../src/generated/engine.mjs'
import {model,editInputs,editNode,writeGraph,readGraph,inspect,connections,applyWires} from '../src/model.js'
const test=(name,fn)=>nativeTest(name,{skip:model.form.find(n=>n.kind==='UNION').min>0},fn)
const get=(d,id)=>d.graph.nodes.find(n=>n.id===id)
const node=(building,kind,id,patch={})=>({...newNode(building,kind,id,0,0),...patch})
const form=(kind,patch={})=>({schema:3,graph:{schema:5,nodes:[node(false,kind,'op',patch),node(false,'OUTPUT','out',{inputs:['op']})]}})
const building=(kind,patch={})=>({schema:2,palette:{wall:'minecraft:bricks'},graph:{schema:8,nodes:[node(true,'SEGMENT','empty_wall',{block:'minecraft:air',length:1}),node(true,kind,'op',patch),node(true,'BUILDING','result',{resultType:'COPYCAT',inputs:['op']})]}})
const volume=p=>p.meshes.reduce((sum,m)=>sum+(m.boxes || []).reduce((s,b)=>s+(b[3]-b[0])*(b[4]-b[1])*(b[5]-b[2])*m.positions.length,0),0)
const preview=(isBuilding,d,id='op')=>previewGraph(isBuilding,d,{},id)

for(const [kind,expected] of [['UNION',0],['INTERSECTION',1]]) test(`${kind}: no sockets and unconnected sockets have the requested identity in form and building graphs`,()=>{
 for(const isBuilding of [false,true]) {
  const mode=isBuilding?'building':'form',k=isBuilding?'COPYCAT_'+kind:kind
  let d=isBuilding?building(k):form(k)
  assert.equal(preview(isBuilding,d).count,expected*(isBuilding?1:4096))
  for(let count=get(d,'op').inputs.length;count>0;count--) d=editInputs(mode,d,'op',Array.from({length:count-1},(_,i)=>i))
  assert.deepEqual(get(d,'op').inputs,[])
  const result=preview(isBuilding,d)
  assert.equal(result.count,expected*(isBuilding?1:4096))
  assert.equal(volume(result),expected*4096)
  assert.equal(d.graph.schema,isBuilding?9:6)
  const round=readGraph(JSON.parse(JSON.stringify(writeGraph(mode,d))),mode)
  assert.deepEqual(preview(isBuilding,round),result)
  d=editInputs(mode,d,'op',[-1])
  assert.equal(preview(isBuilding,d).count,expected*(isBuilding?1:4096))
  if(isBuilding && expected) assert.deepEqual(result.materials,{'minecraft:bricks':'1'})
 }
})

test('an empty union is a valid form result and survives exact transformations and receipts',()=>{
 for(const kind of ['UNION','INTERSECTION']) {
  let d=form(kind)
  d=editNode('form',d,{...get(d,'op'),copycatTransforms:[{kind:'ROTATE',axis:'Y',quarterTurns:0}]})
  const parts=compileForm(d)
  assert.equal(parts.length,kind==='UNION'?0:1)
  if(parts.length) assert.equal(parts[0].source,'copycats:copycat_block')
  assert.equal(preview(false,d).count,kind==='UNION'?0:4096)
 }
})

test('open slots are ignored, but a connected empty Copycat is an actual intersection operand',()=>{
 for(const isBuilding of [false,true]) {
  const mode=isBuilding?'building':'form'
  let d=isBuilding?building('COPYCAT_INTERSECTION'):form('INTERSECTION')
  d.graph.nodes.push(node(isBuilding,isBuilding?'COPYCAT':'INPUT','source',isBuilding?{inlinePart:{source:'copycats:copycat_byte'}}:{part:{source:'copycats:copycat_byte'}}))
  d=editNode(mode,d,{...get(d,'op'),inputs:['','source','']})
  assert.deepEqual(preview(isBuilding,d).meshes,preview(isBuilding,d,'source').meshes)
  d.graph.nodes.push(node(isBuilding,isBuilding?'COPYCAT_UNION':'UNION','empty'))
  d=editNode(mode,d,{...get(d,'op'),inputs:['source','empty']})
  assert.equal(preview(isBuilding,d).count,0)
 }
})

test('removing all inputs and reconnecting from zero updates wires and source ownership',()=>{
 let d=form('UNION');d.groups=[{id:'body',name:'Body',role:'surface',priority:0},{id:'other',name:'Other',role:'surface',priority:1}]
 d.graph.nodes.push(node(false,'INPUT','source',{part:{source:'copycats:copycat_byte'}}))
 d=editNode('form',d,{...get(d,'op'),inputs:['','source'],inputGroups:['body','other']})
 assert.equal(compileForm(d)[0].group,'other')
 d=editInputs('form',d,'op',[1]);d=editInputs('form',d,'op',[])
 assert.equal(connections(inspect('form',d)).filter(w=>w.target==='op').length,0)
 assert.equal(compileForm(d).length,0)
 d=editInputs('form',d,'op',[-1]);const entries=inspect('form',d)
 d=applyWires('form',d,entries,[...connections(entries),{source:'source',target:'op',port:'0'}])
 assert.equal(compileForm(d).length,1)
 assert.equal(get(d,'op').inputs[0],'source')
})
