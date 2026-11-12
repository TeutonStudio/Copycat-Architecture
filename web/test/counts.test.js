import test from 'node:test'
import assert from 'node:assert/strict'
import {newNode, starterBuilding, evaluateCount, previewGraph} from '../src/generated/engine.mjs'
import {inspect,applyWires,connections,readGraph,writeGraph} from '../src/model.js'
const count=(id,value)=>({...newNode(true,'INT',id,0,0),countValue:value})
function fixture(){ const d=starterBuilding();d.graph.nodes.push(count('a',2),count('b',3),{...newNode(true,'INT_ADD','sum',0,0),inputs:['a','b']});return d }
test('count expressions wire typed dimensions, color shared paths and round-trip without overwriting fallback',()=>{
 const d=fixture();const wall=d.graph.nodes.find(n=>n.id==='wall');wall.module='';wall.block='minecraft:stone';wall.inputs=[]
 let entries=inspect('building',d)
 const wired=applyWires('building',d,entries,[...connections(entries),{source:'sum',target:'wall',port:'length'}])
 assert.equal(evaluateCount(wired,'sum'),5)
 assert.equal(wired.graph.nodes.find(n=>n.id==='wall').length,wall.length)
 entries=inspect('building',wired)
 assert.equal(entries.find(e=>e.node.id==='wall').effectiveDimensions.length,5)
 assert.deepEqual(entries.find(e=>e.node.id==='a').axes,['length'])
 assert.equal(previewGraph(true,wired,{},'wall').count,5*wall.height)
 const both=applyWires('building',wired,entries,[...connections(entries),{source:'a',target:'wall',port:'depth'}])
 assert.deepEqual(inspect('building',both).find(e=>e.node.id==='a').axes,['length','depth'])
 assert.deepEqual(readGraph(JSON.parse(JSON.stringify(writeGraph('building',both))),'building'),both)
 const disconnected=applyWires('building',wired,entries,connections(entries).filter(w=>w.port!=='length'))
 assert.equal(inspect('building',disconnected).find(e=>e.node.id==='wall').effectiveDimensions.length,wall.length)
})
test('integer evaluation rejects overflow, zero divisors, cycles and non-integers',()=>{
 for(const [kind,a,b] of [['INT_ADD',2147483647,1],['INT_SUBTRACT',-2147483648,1],['INT_MULTIPLY',2147483647,2],['INT_DIVIDE',1,0],['INT_DIVIDE',-2147483648,-1]]){
  const d=fixture();d.graph.nodes=d.graph.nodes.map(n=>n.id==='a'?{...n,countValue:a}:n.id==='b'?{...n,countValue:b}:n.id==='sum'?{...n,kind}:n)
  assert.throws(()=>evaluateCount(d,'sum'),/overflow|zero/)
 }
 const d=fixture();d.graph.nodes.find(n=>n.id==='sum').inputs=['sum','a'];assert.throws(()=>evaluateCount(d,'sum'),/Cycle/)
 d.graph.nodes.find(n=>n.id==='a').countValue=1.5;assert.throws(()=>inspect('building',d),/integer/)
})
test('division uses truncation toward zero',()=>{ const d=fixture();d.graph.nodes.find(n=>n.id==='a').countValue=-7;d.graph.nodes.find(n=>n.id==='sum').kind='INT_DIVIDE';assert.equal(evaluateCount(d,'sum'),-2) })

test('explicit native layout dimensions and area conflicts round-trip through browser',()=>{
 const d=fixture();d.graph.schema=2
 const layout={...newNode(true,'SEGMENT_LAYOUT','layout',0,0),dimensions:{length:'sum'},depth:2}
 d.graph.nodes.push(layout)
 const wall=d.graph.nodes.find(n=>n.id==='wall');wall.assembly=true;wall.layout='layout'
 const lib={forms:{'full@1':{schema:3,name:'Full',parts:[{source:'copycats:copycat_block'}]}}}
 const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
 assert.equal(inspect('building',round).find(e=>e.node.id==='layout').effectiveDimensions.length,5)
 assert.equal(previewGraph(true,round,lib,'wall').count,5*4*2)
 assert.equal(previewGraph(true,round,lib,'layout').value,'5 × 4 × 2')
 const area={...newNode(true,'AREA','area',0,0),coordinates:[{x:0,y:0,z:0},{x:1,y:0,z:0}]}
 d.graph.nodes.push(area);layout.geometry='area'
 assert.throws(()=>inspect('building',d),/conflicts with layout geometry/)
})

test('deleting an operand leaves a repairable typed draft without silently compiling its fallback',()=>{
 const d=fixture();d.graph.nodes.find(n=>n.id==='wall').dimensions={length:'sum'}
 const entries=inspect('building',d)
 const removed=applyWires('building',d,entries,connections(entries).filter(w=>w.source!=='a' && w.target!=='a'),['a'])
 assert.deepEqual(removed.graph.nodes.find(n=>n.id==='wall').dimensions,{length:'sum'})
 assert.match(inspect('building',removed).find(e=>e.node.id==='wall').dimensionError,/Connect both/)
 assert.throws(()=>previewGraph(true,removed,{},'wall'),/Connect both/)
})
