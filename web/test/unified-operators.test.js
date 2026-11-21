import { openCard } from '../src/cards.js'
import assert from 'node:assert/strict'
import test from 'node:test'
import { newNode,previewGraph,describeNode } from '../src/generated/engine.mjs'
import { inspect,writeGraph,readGraph,applyWires,connections } from '../src/model.js'
import { families,draftNode,changeOperator,signature,matchesRule,filterCatalog,insertionPosition,catalogKinds } from '../src/operators.js'
const n=(kind,id,patch={})=>({...newNode(true,kind,id,0,0,true),...patch})
const document=nodes=>({schema:2,name:'Operators',palette:{wall:'minecraft:stone'},graph:{schema:9,nodes:[n('SEGMENT','empty_wall',{block:'minecraft:air',length:1,height:1}),...nodes,n('BUILDING','building')]}})
const lib={forms:{'full@1':{schema:3,name:'Full',parts:[{source:'copycats:copycat_block'}]},'slab@1':{schema:3,name:'Slab',parts:[{source:'copycats:copycat_slab'}]}},modules:{}}
const p=(doc,id)=>previewGraph(true,doc,lib,id)
const result=(doc,id)=>p(doc,id).value
function valueNode(family,id,operation,inputs=[],patch={}) {return n(family,id,{operation,inputs,...patch})}
test('every family and operation exposes its real sockets and round trips without a graph insertion',()=>{
 const base=document([]),serialized=JSON.stringify(base)
 for(const [kind,family] of Object.entries(families))for(const [operation,spec] of Object.entries(family.operations)) {
  const node=valueNode(kind,'op',operation,Array(spec.min).fill(''))
  const doc={...base,graph:{...base.graph,nodes:[...base.graph.nodes,node]}}
  const row=describeNode(true,doc,'op');assert.equal(row.ports.length,spec.min,`${kind}/${operation}`)
  assert.deepEqual(readGraph(writeGraph('building',doc),'building').graph.nodes.find(n=>n.id==='op').inputs,node.inputs)
 }
 assert.equal(JSON.stringify(base),serialized)
 const kinds=catalogKinds(['INT','INT_ADD','COPYCAT_ROTATE','COPYCAT_UNION','POSITION','POSITION_ADD'])
 assert.deepEqual(kinds,['NUMBER_OPERATOR','COPYCAT_OPERATOR','POSITION_OPERATOR'])
})
test('operator changes retain compatible operands, all output wires, IDs and placement',()=>{
 let doc=document([n('COPYCAT','a'),n('COPYCAT','b'),n('ROTATION','r'),valueNode('COPYCAT_OPERATOR','op','UNION',['a','b'],{x:91,y:123,name:'Custom'}),n('SEGMENT','wall',{inputs:['op']})])
 doc=changeOperator('building',doc,'op','ROTATE')
 let op=doc.graph.nodes.find(n=>n.id==='op');assert.deepEqual(op.inputs,['a','']);assert.equal(op.x,91);assert.equal(op.name,'Custom');assert.equal(doc.graph.nodes.find(n=>n.id==='wall').inputs[0],'op')
 const rows=inspect('building',doc)
 doc=applyWires('building',doc,rows,[...connections(rows),{source:'r',target:'op',port:'in:1'}])
 doc=changeOperator('building',doc,'op','DIFFERENCE');op=doc.graph.nodes.find(n=>n.id==='op');assert.deepEqual(op.inputs,['a','']);assert.equal(op.y,123)
})
test('relations include divisibility, signed integers, fallback B and zero errors',()=>{
 const base=[valueNode('NUMBER_OPERATOR','a','VALUE',[],{numberValue:12}),valueNode('NUMBER_OPERATOR','b','VALUE',[],{numberValue:3})]
 assert.equal(result(document([...base,valueNode('RELATION_OPERATOR','op','DIVISIBLE',['a','b'])]),'op'),'true')
 assert.equal(result(document([...base,valueNode('RELATION_OPERATOR','op','DIVISIBLE',['a',''],{comparisonValue:5})]),'op'),'false')
 assert.throws(()=>p(document([...base,valueNode('RELATION_OPERATOR','op','DIVISIBLE',['a',''])]),'op'),/zero/)
})
test('statement truth tables include either-or, implication and equivalence',()=>{
 for(const a of [false,true])for(const b of [false,true])for(const [op,expected] of [['AND',a&&b],['OR',a||b],['XOR',a!==b],['IMPLIES',!a||b],['EQUIVALENT',a===b]]) {
  const doc=document([valueNode('STATEMENT_OPERATOR','a','VALUE',[],{booleanValue:a}),valueNode('STATEMENT_OPERATOR','b','VALUE',[],{booleanValue:b}),valueNode('STATEMENT_OPERATOR','op',op,['a','b'])])
  assert.equal(result(doc,'op'),String(expected),`${op}(${a},${b})`)
 }
 assert.equal(families.STATEMENT_OPERATOR.operations.XOR.label,'Entweder oder')
})
test('legacy integer values feed modern arithmetic and modern counts feed existing dimensions',()=>{
 const doc=document([n('INT','a',{countValue:5}),valueNode('NUMBER_OPERATOR','b','VALUE',[],{numberValue:3}),valueNode('NUMBER_OPERATOR','op','ADD',['a','b']),n('COPYCAT','copy'),n('ROTATION_SEGMENT','column',{inputs:['copy'],dimensions:{length:'op'}})])
 assert.equal(result(doc,'op'),'8');assert.ok(p(doc,'column').count>0)
})
test('segment set operators match Copycat set operators, including partial volumes',()=>{
 for(const operation of ['UNION','INTERSECTION','DIFFERENCE','SYMMETRIC_DIFFERENCE']) {
  const doc=document([n('COPYCAT','a',{form:'full@1'}),n('COPYCAT','b',{form:'slab@1'}),n('SEGMENT','sa',{inputs:['a'],length:1,height:1,assembly:false}),n('SEGMENT','sb',{inputs:['b'],length:1,height:1,assembly:false}),valueNode('COPYCAT_OPERATOR','copy',operation,['a','b']),valueNode('SEGMENT_OPERATOR','segment',operation,['sa','sb'])])
  assert.deepEqual(p(doc,'segment'),p(doc,'copy'),operation)
 }
})
test('compound socket filters, sorting and drag grip coordinates',()=>{
 const entry={node:{kind:'COPYCAT_OPERATOR',operation:'ROTATE'},outputType:'COPYCAT',ports:[{types:['COPYCAT']},{types:['ROTATION']}]}
 const group={op:'AND',children:[{direction:'output',type:'shape'},{op:'OR',children:[{direction:'input',type:'INT'},{direction:'input',type:'rotation'}]}]}
 assert.equal(matchesRule(entry,group),true);assert.equal(matchesRule({...entry,ports:[]},group),false)
 const entries=[{...entry,node:{kind:'COPYCAT_OPERATOR',name:'Z'}},{...entry,node:{kind:'COPYCAT_OPERATOR',name:'A'}}]
 assert.deepEqual(filterCatalog(entries,{rule:group,sort:'name',descending:true},n=>n.name).map(e=>e.node.name),['Z','A'])
 for(const zoom of [.2,1,1.7]) {const pan={x:71,y:-43},screen={x:500,y:300},grip={x:31,y:24};const flow={screenToFlowPosition:({x,y})=>({x:(x-pan.x)/zoom,y:(y-pan.y)/zoom})};const position=insertionPosition(screen,grip,flow);assert.ok(Math.abs((position.x+grip.x)*zoom+pan.x-screen.x)<=zoom/2);assert.ok(Math.abs((position.y+grip.y)*zoom+pan.y-screen.y)<=zoom/2)}
})

test('opening a modern value form as a graph card retains the operator configuration',()=>{
 const form={schema:5,name:'Logic',parts:[],graph:{schema:6,nodes:[{...newNode(false,'INPUT','source',0,0),part:{source:'copycats:copycat_slab'}},{id:'copy',kind:'COPYCAT_OPERATOR',operation:'INVERT',inputs:['source']},{id:'value',kind:'STATEMENT_OPERATOR',operation:'VALUE',booleanValue:true,inputs:[]},{id:'out',kind:'OUTPUT',inputs:['copy']}]}}
 const opened=openCard({mode:'form',document:form},lib)
 const value=opened.document.graph.nodes.find(n=>n.kind==='STATEMENT_OPERATOR')
 assert.equal(value.operation,'VALUE');assert.equal(value.booleanValue,true)
 assert.equal(opened.document.graph.nodes.find(n=>n.id==='copy').operation,'INVERT');assert.equal(previewGraph(true,opened.document,opened.library,value.id).value,'true')
})

test('modern exact geometry remains editable through existing Copycat transformations and booleans',()=>{
 const doc=document([n('COPYCAT','a',{form:'full@1'}),n('COPYCAT','b',{form:'slab@1'}),valueNode('COPYCAT_OPERATOR','op','DIFFERENCE',['a','b']),n('COPYCAT_ROTATE','legacy',{inputs:['op'],quarterTurns:1}),n('COPYCAT_UNION','combined',{inputs:['op','b']})])
 assert.equal(p(doc,'legacy').count,1);assert.equal(p(doc,'combined').count,1)
})

test('reusable ordered rotations and empty transformation lists keep their meaning in modern operators',()=>{
 const doc=document([n('COPYCAT','a',{form:'slab@1'}),n('SEGMENT','s',{inputs:['a'],length:1,height:1,assembly:false}),n('ROTATION','r',{rotations:[{axis:'X',quarterTurns:1},{axis:'Y',quarterTurns:1}]}),valueNode('COPYCAT_OPERATOR','copy','ROTATE',['a','r']),n('COPYCAT_ROTATE','old',{inputs:['a'],rotation:'r'}),valueNode('SEGMENT_OPERATOR','actual','ROTATE',['s','r']),valueNode('SEGMENT_OPERATOR','first','ROTATE',['s',''],{rotationAxis:'X',quarterTurns:1}),valueNode('SEGMENT_OPERATOR','expected','ROTATE',['first',''],{rotationAxis:'Y',quarterTurns:1})])
 assert.deepEqual(p(doc,'copy'),p(doc,'old'));assert.deepEqual(p(doc,'actual'),p(doc,'expected'))
 doc.graph.nodes.find(n=>n.id==='r').rotations=[]
 assert.deepEqual(p(doc,'actual'),p(doc,'s'));assert.deepEqual(p(doc,'copy'),p(doc,'a'))
})
