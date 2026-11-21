import nativeTest from 'node:test'
import assert from 'node:assert/strict'
import {newNode} from '../src/generated/engine.mjs'
import {inspect,connections,model} from '../src/model.js'
import {operatorFamilies,switchOperator} from '../src/operator-families.js'
const test=(name,fn)=>nativeTest(name,{skip:!model.building.includes('TRANSFORMATION')},fn)
const n=(b,kind,id,patch={})=>({...newNode(b,kind,id,0,0,true),...patch})
const doc=(b,nodes)=>({schema:b?2:3,palette:{wall:'minecraft:stone'},graph:{schema:b?10:7,nodes:b?[n(true,'SEGMENT','empty_wall',{block:'minecraft:air',length:1}),...nodes,n(true,'BUILDING','result',{resultType:'COPYCAT',inputs:['op']})]:[...nodes,n(false,'OUTPUT','result',{inputs:['op']})]}})
test('each geometry family includes Transformieren and switches without losing compatible operands or output wires',()=>{
 for(const b of [false,true]) for(const family of operatorFamilies(b?'building':'form')) {
  const mode=b?'building':'form',segment=family.id==='segments'
  if(family.id==='numbers') {
   for(const [kind] of family.operations) {
    const scalar=n(b,'COUNT','number',{countValue:2}),op=n(b,kind,'op');op.inputs=op.inputs.map(()=>'number')
    const d=doc(b,[scalar,op]);d.graph.schema=b?13:9
    const result=d.graph.nodes.find(n=>n.id==='result');result.inputs=b?['op']:[''];if(b)result.resultType='INT'
    const e=inspect(mode,d).find(e=>e.node.id==='op')
    assert.equal(e.outputType,kind==='INT_SUBTRACT'?'INT':'COUNT')
   }
   continue
  }
  if(family.id==='positions') {
   const kinds=family.operations.map(([kind])=>kind)
   for(const kind of kinds) {
    const value=n(b,'POSITION','source'),op=n(b,kind,'op')
    const d=doc(b,[value,op]);if(b)d.graph.nodes.find(n=>n.id==='result').inputs=['source'],d.graph.nodes.find(n=>n.id==='result').resultType='POSITION'
    if(!b)d.graph.nodes.find(n=>n.id==='result').inputs=['']
    const entry=inspect(mode,d).find(e=>e.node.id==='op')
    assert.equal(entry.outputType,kind==='POSITION_X'?'INT':b?'POSITION':'position')
    assert.ok(entry.ports.length>=1)
   }
   continue
  }

  const source=segment?n(true,'SEGMENT','source'):n(b,b?'COPYCAT':'INPUT','source')
  const [union]=family.operations[0],transform=family.operations.find(([kind])=>kind.endsWith('_TRANSFORM'))[0]
  const d=doc(b,[source,n(b,union,'op',{inputs:['source',''],name:'Meine Operation'})])
  if(segment) d.graph.nodes.find(n=>n.id==='result').resultType='WALL'
  let next=switchOperator(mode,d,'op',transform)
  assert.equal(next.graph.nodes.find(n=>n.id==='op').name,'Meine Operation')
  assert.ok(connections(inspect(mode,next)).some(w=>w.source==='source' && w.target==='op'))
  assert.ok(connections(inspect(mode,next)).some(w=>w.source==='op' && w.target==='result'))
  for(const [kind] of family.operations) {
   if(['ROTATION_SEGMENT','SERVICE_SHAFT_SEGMENT','ELEVATOR_SHAFT_SEGMENT'].includes(kind)) {
    const generated=switchOperator(mode,next,'op',kind),entry=inspect(mode,generated).find(e=>e.node.id==='op')
    assert.equal(entry.outputType,'WALL')
    assert.ok(connections(inspect(mode,generated)).some(w=>w.source==='op'&&w.target==='result'))
    assert.ok(entry.ports.every(port=>port.types.includes('COPYCAT')||port.types.includes('INT')))
    continue
   }
   next=switchOperator(mode,next,'op',kind)
   const entry=inspect(mode,next).find(e=>e.node.id==='op')
   assert.equal(entry.node.kind,kind)
   assert.equal(entry.ports[0].source,'source',kind)
  }
  assert.equal(JSON.stringify(d.graph.nodes.find(n=>n.id==='op').inputs),'["source",""]')
 }
})
test('changing operators retains matching parameter types and removes incompatible parameter connections',()=>{
 for(const b of [false,true]) {
  const mode=b?'building':'form',kind=b?'COPYCAT_ROTATE':'ROTATE'
  let d=doc(b,[n(b,b?'COPYCAT':'INPUT','source'),n(b,'ROTATION','rotation'),n(b,kind,'op',b?{inputs:['source'],rotation:'rotation'}:{inputs:['source','rotation']})])
  d=switchOperator(mode,d,'op',b?'COPYCAT_MIRROR':'MIRROR')
  if(!b)d.graph.nodes.find(n=>n.id==='result').inputs=['']
    const entry=inspect(mode,d).find(e=>e.node.id==='op')
  assert.equal(entry.ports[0].source,'source');assert.equal(entry.ports[1].source,'')
  assert.throws(()=>switchOperator(mode,d,'op','TRANSFORMATION'),/Operatorfamilie/)
 }
})
test('named operand group ports survive a family operation switch',()=>{
 for(const b of [false,true]) {
  const mode=b?'building':'form',port=b?'in:0':'0',union=b?'COPYCAT_UNION':'UNION'
  const d=doc(b,[n(b,b?'COPYCAT':'INPUT','source'),n(b,union,'op',{inputs:['source','']})])
  d.graph.nodeGroups=[{id:'group',name:'Operator',nodes:['op'],graphInterface:{inputs:[{id:'operand',name:'Quelle',type:b?'COPYCAT':'shape',targets:[{member:0,port}]}],outputs:[{id:'result',name:'Form',type:b?'COPYCAT':'shape',member:0}],positions:[{x:0,y:0}]},inputConnections:{operand:'source'}}]
  const next=switchOperator(mode,d,'op','COPYCAT_TRANSFORM')
  assert.equal(next.graph.nodeGroups[0].graphInterface.inputs[0].targets[0].port,port)
  assert.equal(next.graph.nodeGroups[0].inputConnections.operand,'source')
 }
})

test('family switches keep shared group instances valid with independent external parameter sources',()=>{
 for(const b of [false,true]) {
  const mode=b?'building':'form',kind=b?'COPYCAT_ROTATE':'ROTATE'
  const d=doc(b,[n(b,b?'COPYCAT':'INPUT','source'),n(b,'ROTATION','r1'),n(b,'ROTATION','r2'),n(b,kind,'op',b?{inputs:['source'],rotation:'r1'}:{inputs:['source','r1']}),n(b,kind,'peer',b?{inputs:['source'],rotation:'r2'}:{inputs:['source','r2']})])
  d.graph.nodeGroups=[{id:'one',definitionId:'shared',name:'Eins',nodes:['op']},{id:'two',definitionId:'shared',name:'Zwei',nodes:['peer']}]
  const next=switchOperator(mode,d,'op',b?'COPYCAT_MIRROR':'MIRROR')
  for(const id of ['op','peer']) {
   const entry=inspect(mode,next).find(e=>e.node.id===id)
   assert.equal(entry.node.kind,b?'COPYCAT_MIRROR':'MIRROR')
   assert.equal(entry.ports[0].source,'source');assert.equal(entry.ports[1].source,'')
  }
 }
})
