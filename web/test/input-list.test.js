import nativeTest from 'node:test'
import assert from 'node:assert/strict'
import {newNode, previewGraph} from '../src/generated/engine.mjs'
import {model,editInputs,inspect,connections,editNode,writeGraph,readGraph} from '../src/model.js'
import {inputListEnabled,inputOrder} from '../src/input-list.js'
const test=(name,fn)=>nativeTest(name,{skip:!model.inputEditing},fn)
const node=(kind,id,patch={},building=true)=>({...newNode(building,kind,id,0,0),...patch})
const building=nodes=>({schema:2,palette:{wall:'minecraft:stone'},graph:{schema:8,nodes:[node('SEGMENT','empty_wall',{block:'minecraft:air',length:1}),node('BUILDING','building'),...nodes]}})
const form=nodes=>({schema:3,graph:{schema:5,nodes:[node('OUTPUT','out',{},false),...nodes]}})
const get=(doc,id)=>doc.graph.nodes.find(n=>n.id===id)
const lib={forms:{'full@1':{schema:3,name:'Full',parts:[{source:'copycats:copycat_block'}]}},modules:{}}

test('the single commutativeAssociative capability enables input lists in both catalogs',()=>{
 for(const [mode,kinds] of [['form',model.form.map(n=>n.kind)],['building',model.building]]) for(const kind of kinds) {
  const properties=model.nodeProperties[mode][kind]
  assert.equal(typeof properties.commutativeAssociative,'boolean',kind)
  assert.equal('commutative' in properties,false,kind)
  assert.equal('associative' in properties,false,kind)
 }
 for(const kind of ['COPYCAT_UNION','COPYCAT_INTERSECTION','SEGMENT_SYMMETRIC_DIFFERENCE','INT_ADD','POSITION_MIN']) assert.ok(inputListEnabled(model.nodeProperties.building[kind]),kind)
 for(const kind of ['COPYCAT_DIFFERENCE','PATTERN','INT_SUBTRACT','INT_DIVIDE','ROTATION','MIRRORING','POSITION','SEGMENT','AREA']) assert.equal(inputListEnabled(model.nodeProperties.building[kind]),false,kind)
 for(const p of [null,{}, {commutative:true},{associative:true},{commutative:true,associative:true},{commutativeAssociative:'true'}]) assert.equal(inputListEnabled(p),false)
 assert.deepEqual(inputOrder(3,'move',1,-1),[1,0,2])
 assert.deepEqual(inputOrder(3,'remove',1),[0,2])
 assert.deepEqual(inputOrder(2,'add'),[0,1,-1])
})

test('add, connect, move and remove update real wires and material input groups atomically',()=>{
 let d=form([node('INPUT','a',{},false),node('INPUT','b',{},false),node('INPUT','c',{},false),node('UNION','u',{inputs:['a','b'],inputGroups:['body','other'],extension:{keep:true}},false)])
 d.groups=[{id:'body',name:'Body',role:'surface',priority:0},{id:'other',name:'Other',role:'surface',priority:1}]
 const before=structuredClone(d)
 d=editInputs('form',d,'u',[0,1,-1])
 assert.deepEqual(get(d,'u').inputs,['a','b',''])
 d=editNode('form',d,{...get(d,'u'),inputs:['a','b','c']})
 d=editInputs('form',d,'u',[2,0,1])
 assert.deepEqual(get(d,'u').inputs,['c','a','b'])
 assert.deepEqual(get(d,'u').inputGroups,['','body','other'])
 assert.deepEqual(connections(inspect('form',d)).filter(w=>w.target==='u'),[{source:'c',target:'u',port:'0'},{source:'a',target:'u',port:'1'},{source:'b',target:'u',port:'2'}])
 d=editInputs('form',d,'u',[0,2])
 assert.deepEqual(get(d,'u').inputs,['c','b'])
 assert.deepEqual(get(d,'u').inputGroups,['','other'])
 assert.deepEqual(get(d,'u').extension,{keep:true})
 assert.deepEqual(get(before,'u').inputs,['a','b'])
 d=editInputs('form',d,'u',[0])
 d=editInputs('form',d,'u',[])
 assert.deepEqual(get(d,'u').inputs,[])
 assert.equal(previewGraph(false,d,lib,'u').count,0)
})

test('segment input offsets follow their source and removal compacts them without changing geometry',()=>{
 const d=building([node('SEGMENT','a',{length:1,height:1}),node('SEGMENT','b',{length:1,height:1}),node('SEGMENT','c',{length:1,height:1}),node('SEGMENT_UNION','u',{inputs:['a','b','c'],offsets:[{x:0,y:0,z:0},{x:3,y:0,z:0},{x:7,y:0,z:0}]})])
 const before=previewGraph(true,d,lib,'u')
 const moved=editInputs('building',d,'u',[2,0,1])
 assert.deepEqual(get(moved,'u').offsets,[{x:7,y:0,z:0},{x:0,y:0,z:0},{x:3,y:0,z:0}])
 assert.deepEqual(previewGraph(true,moved,lib,'u').meshes,before.meshes)
 const removed=editInputs('building',moved,'u',[0,2])
 assert.deepEqual(get(removed,'u').inputs,['c','b'])
 assert.deepEqual(get(removed,'u').offsets,[{x:7,y:0,z:0},{x:3,y:0,z:0}])
 assert.equal(previewGraph(true,removed,lib,'u').count,2)
})

test('native guard rejects fixed operations, forged instance flags, duplicates and over-limit input changes',()=>{
 for(const kind of ['POSITION','PATTERN']) {
  const d=building([node(kind,'fixed',{commutativeAssociative:true})])
  assert.throws(()=>editInputs('building',d,'fixed',[1,0]),/commutative and associative/)
 }
 const d=building([node('INT_ADD','add')])
 for(const order of [[0,0],[0,2],[0,1,-1,-1],[]]) assert.throws(()=>editInputs('building',d,'add',order))
 let full=d
 for(let n=2;n<16;n++) full=editInputs('building',full,'add',inputOrder(n,'add'))
 assert.equal(get(full,'add').inputs.length,16)
 assert.throws(()=>editInputs('building',full,'add',inputOrder(16,'add')),/Invalid input/)
})

test('all additional integer and position operands participate in native evaluation and survive JSON',()=>{
 for(const [op,expected] of [['ADD',9],['MULTIPLY',24],['MIN',2],['MAX',4]]) {
  let d=building([node('INT','a',{countValue:2}),node('INT','b',{countValue:3}),node('INT','c',{countValue:4}),node('INT_'+op,'result',{inputs:['a','b','c']})])
  d=editInputs('building',d,'result',[2,0,1])
  assert.equal(previewGraph(true,d,lib,'result').value,`Ganzzahl: ${expected}`)
  const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
  assert.equal(previewGraph(true,round,lib,'result').value,`Ganzzahl: ${expected}`)
  const vectors=building([node('POSITION','a',{translation:{x:2,y:2,z:2}}),node('POSITION','b',{translation:{x:3,y:3,z:3}}),node('POSITION','c',{translation:{x:4,y:4,z:4}}),node('POSITION_'+op,'result',{inputs:['a','b','c']})])
  const changed=editInputs('building',vectors,'result',[1,2,0])
  assert.match(previewGraph(true,changed,lib,'result').value,new RegExp(`x=${expected}, y=${expected}, z=${expected}`))
 }
})

test('named group ports and all instances retain their own incoming wires after permutation',()=>{
 const face={inputs:[{id:'left',name:'Links',type:'INT',targets:[{member:0,port:'in:0'}]},{id:'right',name:'Rechts',type:'INT',targets:[{member:0,port:'in:1'}]}],outputs:[{id:'out',name:'Ergebnis',type:'INT',member:0}]}
 const d=building([node('INT','a',{countValue:2}),node('INT','b',{countValue:3}),node('INT_ADD','one',{inputs:['a','b']}),node('INT_ADD','two',{inputs:['b','a']})])
 d.graph.nodeGroups=[{id:'g',name:'Addition',nodes:['one'],graphInterface:face,inputConnections:{left:'a',right:'b'}},{id:'g2',name:'Addition',nodes:['two'],definitionId:'g',graphInterface:structuredClone(face),inputConnections:{left:'b',right:'a'}}]
 const moved=editInputs('building',d,'one',[1,0])
 assert.deepEqual(get(moved,'one').inputs,['b','a'])
 assert.deepEqual(get(moved,'two').inputs,['a','b'])
 for(const group of moved.graph.nodeGroups) assert.deepEqual(group.graphInterface.inputs.map(i=>i.targets[0].port),['in:1','in:0'])
 assert.deepEqual(moved.graph.nodeGroups[1].inputConnections,{left:'b',right:'a'})
 assert.equal(previewGraph(true,moved,lib,'one').value,'Ganzzahl: 5')
 assert.equal(previewGraph(true,moved,lib,'two').value,'Ganzzahl: 5')
})


test('permutations cannot change overflow through intermediate sums or products',()=>{
 for(const [op,values,expected] of [['ADD',[2147483647,1,-1],2147483647],['MULTIPLY',[2147483647,2147483647,0],0]]) {
  const d=building([...values.map((countValue,i)=>node('INT','n'+i,{countValue})),node('INT_'+op,'r',{inputs:['n0','n1','n2']})])
  for(const order of [[0,1,2],[2,0,1],[1,2,0]]) {
   const changed=editInputs('building',d,'r',order)
   assert.equal(changed.graph.schema,9)
   assert.equal(previewGraph(true,changed,lib,'r').value,`Ganzzahl: ${expected}`)
  }
 }
})

test('socket-circle permutations move binary operand wires and named terminals together',async()=>{
 const {moveInput,inputsReorderable}=await import('../src/input-list.js')
 const d=building([node('INT','a',{countValue:7}),node('INT','b',{countValue:3}),node('INT_SUBTRACT','op',{inputs:['a','b']})])
 d.graph.nodeGroups=[{id:'g',name:'Rechnung',nodes:['op'],graphInterface:{inputs:[{id:'left',name:'A',type:'INT',targets:[{member:0,port:'in:0'}]}],outputs:[{id:'out',name:'Zahl',type:'INT',member:0}]},inputConnections:{left:'a'}}]
 assert.ok(inputsReorderable(d.graph.nodes.find(n=>n.id==='op'),{}))
 const order=moveInput(2,0,1),changed=editInputs('building',d,'op',order)
 assert.deepEqual(changed.graph.nodes.find(n=>n.id==='op').inputs,['b','a'])
 assert.equal(changed.graph.nodeGroups[0].graphInterface.inputs[0].id,'left');assert.equal(changed.graph.nodeGroups[0].graphInterface.inputs[0].targets[0].port,'in:1')
 assert.equal(changed.graph.nodeGroups[0].inputConnections.left,'a')
 assert.deepEqual(d.graph.nodes.find(n=>n.id==='op').inputs,['a','b'])
 assert.deepEqual(moveInput(4,0,3),[1,2,3,0]);assert.throws(()=>moveInput(2,0,2))
})
