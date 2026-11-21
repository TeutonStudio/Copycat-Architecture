import test from 'node:test'
import assert from 'node:assert/strict'
import {newNode,previewGraph,transformDiagnostics} from '../src/generated/engine.mjs'
import {legacyCard} from '../src/cards.js'
import {replaceNodes,editNode,inspect,promoteNumbers,applyWires,connections,writeGraph,readGraph} from '../src/model.js'
import {configureDocument,configureNode} from '../src/node-controls.js'
import {nodePreview,pickerEntries} from '../src/node-picker.js'
import {operatorFamilies,switchOperator} from '../src/operator-families.js'
import {acceptsValue} from '../src/value-compatibility.js'
const n=(kind,id,patch={})=>({...newNode(true,kind,id,0,0,true),...patch})
const doc=(...nodes)=>{let d=promoteNumbers('building',legacyCard());return replaceNodes(d,[...d.graph.nodes,...nodes])}
const entry=(d,id)=>inspect('building',d).find(e=>e.node.id===id)
const lib={forms:{},modules:{}}
test('number drafts default to Count zero and scalar operations have exact signatures',()=>{
 const f=operatorFamilies('building').find(f=>f.id==='numbers');assert.equal(f.operations[0][0],'COUNT')
 const p=nodePreview('building','COUNT');assert.equal(p.node.countValue,0);assert.equal(p.outputType,'COUNT')
 const signed=nodePreview('building','COUNT',configureNode(p.node,'$numberType','INT'));assert.equal(signed.node.kind,'INT');assert.equal(signed.outputType,'INT')
 for(const k of ['INT_ABS','INT_TO_COUNT'])assert.equal(nodePreview('building',k).ports.length,1)
 assert.equal(nodePreview('building','INT_ADD').outputType,null)
})
test('derived numeric types use operand types and Count widens to Integer',()=>{
 let d=doc(n('COUNT','a',{countValue:7}),n('COUNT','b',{countValue:3}),n('INT_ADD','op',{inputs:['a','b']}),n('POSITION','p',{inputs:['a','','']}))
 assert.equal(entry(d,'op').outputType,'COUNT');assert.ok(acceptsValue(entry(d,'p').ports[0],entry(d,'a')))
 d=configureDocument('building',d,'a','$numberType','INT');assert.equal(entry(d,'op').outputType,'INT');assert.equal(entry(d,'p').ports[0].source,'a')
 d=editNode('building',d,{...entry(d,'op').node,inputs:['','']});assert.equal(entry(d,'op').outputType,null)
})
test('absolute value, division and remainder use native signed arithmetic and repairable diagnostics',()=>{
 for(const [a,b]of [[-7,3],[7,-3],[-7,-3],[7,3]]) {
  const d=doc(n('INT','a',{countValue:a}),n('INT','b',{countValue:b}),n('INT_DIVIDE','q',{inputs:['a','b']}),n('INT_REMAINDER','r',{inputs:['a','b']}),n('INT_ABS','abs',{inputs:['a']}))
  assert.equal(previewGraph(true,d,lib,'q').value,`Ganzzahl: ${Math.trunc(a/b)}`)
  assert.equal(previewGraph(true,d,lib,'r').value,`Ganzzahl: ${a-Math.trunc(a/b)*b}`)
  assert.equal(previewGraph(true,d,lib,'abs').value,`Anzahl: ${Math.abs(a)}`)
 }
 let d=doc(n('INT','a',{countValue:-2147483648}),n('INT_ABS','abs',{inputs:['a']}))
 assert.match(transformDiagnostics(true,d,lib).find(e=>e.nodeId==='abs').message,/overflow/)
 d=editNode('building',d,{...entry(d,'a').node,countValue:-5});assert.ok(!transformDiagnostics(true,d,lib).some(e=>e.nodeId==='abs'))
 d=switchOperator('building',d,'abs','INT_TO_COUNT');assert.match(transformDiagnostics(true,d,lib).find(e=>e.nodeId==='abs').message,/negative/)
})
test('old dimension links migrate explicitly and source type switches keep an undoable prior snapshot',()=>{
 let d=legacyCard();d=replaceNodes(d,[...d.graph.nodes,n('INT','old',{countValue:3}),n('AREA','area',{areaMode:'SQUARE',dimensions:{length:'old'}})])
 const before=structuredClone(d),upgraded=promoteNumbers('building',d)
 assert.equal(before.graph.schema,8);assert.equal(upgraded.graph.schema,13)
 const adapter=upgraded.graph.nodes.find(n=>n.kind==='INT_TO_COUNT');assert.ok(adapter);assert.deepEqual(adapter.inputs,['old']);assert.equal(entry(upgraded,'area').ports[0].source,adapter.id)
 assert.equal(entry(before,'area').ports[0].source,'old')
 let modern=doc(n('COUNT','count',{countValue:4}),n('AREA','area',{areaMode:'SQUARE',dimensions:{length:'count'}}))
 const snapshot=structuredClone(modern);modern=configureDocument('building',modern,'count','$numberType','INT')
 assert.equal(entry(modern,'area').ports[0].source,'');assert.equal(entry(snapshot,'area').ports[0].source,'count')
 assert.throws(()=>applyWires('building',modern,inspect('building',modern),[...connections(inspect('building',modern)),{source:'count',target:'area',port:'length'}]),/Incompatible/)
})
test('conditional numeric branches join to Integer and all fields round-trip',()=>{
 let d=doc(n('COUNT','a',{countValue:3}),n('INT','b',{countValue:-2}),n('BOOLEAN','flag'),n('CONDITIONAL','pick',{inputs:['a','flag','b']}))
 assert.equal(entry(d,'pick').outputType,'INT');assert.equal(previewGraph(true,d,lib,'pick').value,'Ganzzahl: -2')
 d=editNode('building',d,{...d.graph.nodes.find(n=>n.resultType),resultType:'INT',inputs:['pick']})
 const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building');assert.equal(entry(round,'pick').outputType,'INT')
})
test('named Count group inputs release incompatible sources and preserve terminal identity',()=>{
 let d=doc(n('COUNT','count',{countValue:3}),n('INT_ABS','abs',{inputs:['count']}))
 d.graph.nodeGroups=[{id:'group',name:'Betrag',nodes:['abs'],graphInterface:{inputs:[{id:'number',name:'Anzahl',type:'COUNT',targets:[{member:0,port:'in:0'}]}],outputs:[{id:'out',name:'Betrag',type:'COUNT',member:0}]},inputConnections:{number:'count'}}]
 d=configureDocument('building',d,'count','$numberType','INT')
 assert.equal(d.graph.nodeGroups[0].graphInterface.inputs[0].id,'number');assert.equal(d.graph.nodeGroups[0].inputConnections.number,undefined);assert.equal(entry(d,'abs').ports[0].source,'')
 inspect('building',d)
})

test('divisibility uses A divided by B including signed operands and a repairable zero divisor',()=>{
 for(const [a,b,expected]of [[6,3,true],[7,3,false],[-6,3,true],[6,-3,true],[-2147483648,-1,true]]) {
  const d=doc(n('INT','a',{countValue:a}),n('INT','b',{countValue:b}),n('INT_COMPARE','cmp',{inputs:['a','b'],operation:'DIVISIBLE'}))
  assert.equal(previewGraph(true,d,lib,'cmp').value,`Boolean: ${expected?'Wahr':'Falsch'}`)
 }
 const d=doc(n('INT','a',{countValue:6}),n('COUNT','b',{countValue:0}),n('INT_COMPARE','cmp',{inputs:['a','b'],operation:'DIVISIBLE'}))
 assert.match(transformDiagnostics(true,d,lib).find(e=>e.nodeId==='cmp').message,/Divisor 0/)
})
