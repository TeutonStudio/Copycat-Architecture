import test from 'node:test'
import assert from 'node:assert/strict'
import {newNode,previewGraph,arrangementGraph} from '../src/generated/engine.mjs'
import {inspect,applyWires,connections,readGraph,writeGraph} from '../src/model.js'
import {listCopycatOptions,previewCopycatOption} from '../src/copycat-options.js'
import {saveNodeGroup,insertNodeGroup} from '../src/node-groups.js'
import {applyCopycatOption} from '../src/copycat-selection.js'
const node=(kind,id,patch={})=>({...newNode(true,kind,id,0,0),...patch})
const doc=(nodes)=>({schema:2,palette:{wall:'minecraft:stone'},graph:{schema:1,nodes:[node('SEGMENT','empty_wall',{block:'minecraft:air'}),node('BUILDING','building'),...nodes]}})
const lib={forms:{'full@1':{schema:3,name:'Full',parts:[{source:'copycats:copycat_block'}]},'byte@1':{schema:3,name:'Byte',parts:[{source:'copycats:copycat_byte'}]}},modules:{}}
const preview=(d,id)=>previewGraph(true,d,lib,id)
const positions=p=>p.meshes.flatMap(m=>m.positions.map(p=>Array.from(p))).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)))

test('building rotation and mirroring values have typed ports and survive wiring and JSON',()=>{
 const d=doc([node('COPYCAT','copy'),node('ROTATION','r'),node('MIRRORING','m'),node('COPYCAT_ROTATE','rotate',{inputs:['copy']}),node('COPYCAT_MIRROR','mirror',{inputs:['rotate']})])
 const entries=inspect('building',d)
 assert.deepEqual(entries.find(e=>e.node.id==='rotate').ports.find(p=>p.id==='rotation').accepts,['ROTATION'])
 assert.deepEqual(entries.find(e=>e.node.id==='mirror').ports.find(p=>p.id==='mirroring').accepts,['MIRRORING'])
 const changed=applyWires('building',d,entries,[...connections(entries),{source:'r',target:'rotate',port:'rotation'},{source:'m',target:'mirror',port:'mirroring'}])
 const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',changed))),'building')
 assert.equal(round.graph.nodes.find(n=>n.id==='rotate').rotation,'r')
 assert.equal(round.graph.nodes.find(n=>n.id==='mirror').mirroring,'m')
 assert.match(preview(round,'r').value,/90°/)
 assert.equal(preview(round,'mirror').count,1)
 assert.throws(()=>applyWires('building',d,entries,[...connections(entries),{source:'m',target:'rotate',port:'rotation'}]),/Incompatible/)
 const removed=applyWires('building',changed,inspect('building',changed),connections(inspect('building',changed)).filter(w=>w.source!=='r'),['r'])
 assert.equal(removed.graph.nodes.find(n=>n.id==='rotate').rotation,'')
})

test('ordered rotation lists match chained operators and empty lists are identity',()=>{
 const d=doc([node('COPYCAT','copy',{form:'byte@1'}),node('SEGMENT','segment',{inputs:['copy'],length:2,height:3}),
 node('ROTATION','r',{rotations:[{axis:'X',quarterTurns:1},{axis:'Y',quarterTurns:1}]}),
 node('SEGMENT_ROTATE','actual',{inputs:['segment'],rotation:'r'}),
 node('SEGMENT_ROTATE','first',{inputs:['segment'],rotationAxis:'X',quarterTurns:1}),
 node('SEGMENT_ROTATE','expected',{inputs:['first'],rotationAxis:'Y',quarterTurns:1}),
 node('COPYCAT_ROTATE','copyActual',{inputs:['copy'],rotation:'r'}),
 node('COPYCAT_ROTATE','copyFirst',{inputs:['copy'],rotationAxis:'X',quarterTurns:1}),
 node('COPYCAT_ROTATE','copyExpected',{inputs:['copyFirst'],rotationAxis:'Y',quarterTurns:1})])
 assert.deepEqual(preview(d,'actual'),preview(d,'expected'))
 assert.deepEqual(preview(d,'copyActual'),preview(d,'copyExpected'))
 const empty=structuredClone(d);empty.graph.nodes.find(n=>n.id==='r').rotations=[]
 assert.deepEqual(preview(empty,'actual'),preview(empty,'segment'))
 const reversed=structuredClone(d);reversed.graph.nodes.find(n=>n.id==='r').rotations.reverse()
 assert.notDeepEqual(preview(reversed,'actual').meshes,preview(d,'actual').meshes)
})

test('ordered mirroring lists work for Copycats and segments, including identity',()=>{
 const d=doc([node('COPYCAT','copy',{form:'byte@1'}),node('SEGMENT','segment',{inputs:['copy'],length:2,height:3}),node('MIRRORING','m',{mirrors:[{plane:'X'},{plane:'Y'}]}),
 ...['COPYCAT','SEGMENT'].flatMap((family,i)=>[node(family+'_MIRROR','actual'+i,{inputs:[i?'segment':'copy'],mirroring:'m'}),node(family+'_MIRROR','first'+i,{inputs:[i?'segment':'copy'],mirror:{plane:'X'},rotationAxis:'X'}),node(family+'_MIRROR','expected'+i,{inputs:['first'+i],mirror:i?null:{plane:'Y'},rotationAxis:'Y'})])])
 d.graph.nodes.filter(n=>n.kind==='SEGMENT_MIRROR').forEach(n=>n.mirror=null)
 for(const i of [0,1]) assert.deepEqual(preview(d,'actual'+i),preview(d,'expected'+i))
 d.graph.nodes.find(n=>n.id==='m').mirrors=[]
 for(const i of [0,1]) assert.deepEqual(preview(d,'actual'+i),preview(d,i?'segment':'copy'))
})

test('segment union offsets affect the output geometry and every arrangement plane',()=>{
 const d=doc([node('SEGMENT','a',{length:1,height:1}),node('SEGMENT','b',{length:1,height:1}),node('SEGMENT_UNION','u',{inputs:['a','b'],offsets:[{x:0,y:0,z:0},{x:2,y:1,z:-1}]})])
 assert.deepEqual(positions(preview(d,'u')),[[0,0,1],[2,1,0]])
 const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
 assert.deepEqual(preview(round,'u'),preview(d,'u'))
 assert.deepEqual(arrangementGraph(d,lib,'u','Y').items[1].cells,[[2,-1]])
 assert.deepEqual(arrangementGraph(d,lib,'u','Z').items[1].cells,[[2,-2]])
 assert.deepEqual(arrangementGraph(d,lib,'u','X').items[1].cells,[[-1,-2]])
 const overlap=structuredClone(d);overlap.graph.nodes.find(n=>n.id==='u').offsets=[]
 assert.equal(preview(overlap,'u').count,1)
})

test('empty Copycat can be previewed, selected, persisted and reused as an air source',()=>{
 const option=listCopycatOptions().find(o=>o.kind==='empty')
 assert.ok(option);assert.equal(previewCopycatOption(option).count,0)
 const base=doc([node('COPYCAT','copy')])
 const selected=applyCopycatOption({mode:'building',document:base,library:lib,nodeId:'copy',option})
 assert.equal(previewGraph(true,selected.document,selected.library,'copy').count,0)
 const persisted=JSON.parse(JSON.stringify(selected))
 assert.equal(previewGraph(true,persisted.document,persisted.library,'copy').raw,0)
 const filled=applyCopycatOption({...selected,mode:'building',nodeId:'copy',option:listCopycatOptions().find(o=>o.source==='copycats:copycat_block')})
 assert.equal(previewGraph(true,filled.document,filled.library,'copy').count,1)
 const form={schema:3,name:'Form',graph:{schema:2,nodes:[{id:'out',kind:'OUTPUT',inputs:['']}]}}
 const inserted=applyCopycatOption({mode:'form',document:form,option})
 inserted.document.graph.nodes.find(n=>n.id==='out').inputs=[inserted.nodeId]
 assert.equal(previewGraph(false,inserted.document,{},'').count,0)
 assert.equal(writeGraph('form',inserted.document).parts.length,0)
})


test('saved node groups reconnect transformation values to their new local IDs',()=>{
 const d=doc([node('COPYCAT','copy'),node('ROTATION','r'),node('MIRRORING','m'),node('COPYCAT_ROTATE','rotate',{inputs:['copy'],rotation:'r'}),node('COPYCAT_MIRROR','mirror',{inputs:['rotate'],mirroring:'m'})])
 const group=saveNodeGroup('building',d,['r','m','rotate','mirror'],'Transformations')
 let count=0
 const inserted=insertNodeGroup(d,group,()=>`new${++count}`).document
 assert.equal(inserted.graph.nodes.find(n=>n.id==='new3').rotation,'new1')
 assert.equal(inserted.graph.nodes.find(n=>n.id==='new4').mirroring,'new2')
 assert.deepEqual(inserted.graph.nodes.find(n=>n.id==='new4').inputs,['new3'])
 assert.deepEqual(inserted.graph.nodes.find(n=>n.id==='new3').inputs,[''])
})
