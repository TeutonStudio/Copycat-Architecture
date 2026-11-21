import test from 'node:test'
import assert from 'node:assert/strict'
import {newNode,describeNode,previewGraph} from '../src/generated/engine.mjs'
import {legacyCard,newCard} from '../src/cards.js'
import {replaceNodes,editNode,model,inspect} from '../src/model.js'
import {nodePreview,pickerEntries} from '../src/node-picker.js'
import {nodeControls,configureNode,configureDocument} from '../src/node-controls.js'
import {directionLabel,regionLabel} from '../src/axis-labels.js'
import {operatorFamilies,switchOperator} from '../src/operator-families.js'
const n=(kind,id,extra={})=>({...newNode(true,kind,id,0,0,true),...extra})
test('axis names describe all six directions and segment faces edges and corners',()=>{
 assert.match(directionLabel('Y',true),/vertikale Achse oben/)
 assert.match(directionLabel('Y',false),/unten/)
 for(const [axis,positive,compass]of [['X',true,'Ost'],['X',false,'West'],['Z',true,'Süd'],['Z',false,'Nord']])assert.ok(directionLabel(axis,positive).endsWith(compass))
 assert.match(regionLabel('region:x0_yi_zi'),/^Fläche.*West/)
 assert.match(regionLabel('region:x0_y1_zi'),/^Kante.*oben/)
 assert.match(regionLabel('region:x0_y1_z1'),/^Ecke.*Süd/)
 assert.match(regionLabel('region:xb_yi_zb'),/beide Gegenseiten/)
})
test('configured picker drafts expose the exact roof and transformation sockets before insertion',()=>{
 const roof=nodePreview('building','ROOF')
 assert.equal(roof.node.roofInputMode,'TEMPLATE');assert.deepEqual(roof.ports.map(p=>p.id),['geometry','roofTemplate'])
 const draft=configureNode(roof.node,'roofInputMode','SEGMENTS')
 const direct=nodePreview('building','ROOF',draft)
 assert.equal(direct.ports.length,9)
 const t=nodePreview('building','TRANSFORMATION',configureNode(nodePreview('building','TRANSFORMATION').node,'transformationOrder','PRS'))
 assert.deepEqual(t.ports.map(p=>p.id),['positionValue','rotation','mirroring'])
 assert.equal(nodeControls('building',t.node).find(c=>c.path==='transformationOrder').value,'PRS')
})
test('operator families contain generators and positions with three scalar split outputs',()=>{
 const f=operatorFamilies('building')
 assert.ok(f.find(f=>f.id==='copycat').operations.some(([k])=>k==='COPYCAT_TRANSFORM'))
 const segments=f.find(f=>f.id==='segments').operations.map(([k])=>k)
 for(const kind of ['SEGMENT_TRANSFORM','ROTATION_SEGMENT','SERVICE_SHAFT_SEGMENT','ELEVATOR_SHAFT_SEGMENT'])assert.ok(segments.includes(kind))
 assert.ok(f.find(f=>f.id==='positions').operations.some(([k])=>k==='POSITION_X'))
 const split=nodePreview('building','POSITION_X')
 assert.deepEqual(split.outputs.map(p=>p.type),['INT','INT','INT'])
 const entries=pickerEntries(model.building,'building',{},model.nodeProperties.building)
 assert.equal(entries.filter(e=>e.family==='segments').length,1)
 assert.equal(entries.filter(e=>e.family==='positions').length,1)
})
test('conditional scalar preview selects one branch and Boolean NOT changes its signature',()=>{
 let d=legacyCard()
 d=replaceNodes(d,[...d.graph.nodes,n('INT','a',{countValue:7}),n('INT','zero',{countValue:0}),n('INT_DIVIDE','bad',{inputs:['a','zero']}),n('BOOLEAN','flag',{booleanValue:true}),n('CONDITIONAL','pick',{inputs:['a','flag','bad']}),n('BOOLEAN_LOGIC','logic',{inputs:['flag','flag']})])
 d=editNode('building',d,{...d.graph.nodes.find(n=>n.resultType),resultType:'INT',inputs:['pick']})
 assert.equal(previewGraph(true,d,{forms:{},modules:{}},'').value,'Ganzzahl: 7')
 assert.equal(describeNode(true,d,'pick').outputType,'INT')
 d=configureDocument('building',d,'logic','operation','NOT')
 assert.equal(describeNode(true,d,'logic').ports.length,1)
 assert.equal(previewGraph(true,d,{forms:{},modules:{}},'logic').value,'Boolean: Falsch')
})
test('Positionsoperator can switch into and out of the folded three-output operation',()=>{
 let d=legacyCard();d=replaceNodes(d,[...d.graph.nodes,n('POSITION','p'),n('POSITION_ADD','op',{inputs:['p','p']})])
 d=switchOperator('building',d,'op','POSITION_X')
 assert.ok(d.graph.nodeGroups.some(g=>g.id==='op'&&g.presentation==='vectorSplit'))
 assert.ok(d.graph.nodes.filter(n=>/^POSITION_[XYZ]$/.test(n.kind)).every(n=>n.inputs[0]==='p'))
 d=switchOperator('building',d,'op','POSITION_ADD')
 assert.equal(d.graph.nodes.find(n=>n.id==='op').inputs[0],'p')
 assert.ok(!d.graph.nodeGroups.some(g=>g.presentation==='vectorSplit'))
 inspect('building',d)
})

test('building picker cards are ordinary producers with the same sockets as inserted nodes',()=>{
 const p=nodePreview('building','BUILDING');assert.equal(p.node.resultType,null);assert.equal(p.outputType,'BUILDING')
 const d=replaceNodes(newCard(),[...newCard().graph.nodes,{...p.node,id:'house'}])
 assert.deepEqual(inspect('building',d).find(e=>e.node.id==='house').ports,p.ports)
})
