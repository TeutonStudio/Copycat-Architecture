import test from 'node:test'
import assert from 'node:assert/strict'
import {newCard} from '../src/cards.js'
import {cardResult} from '../src/card-types.js'
import {newNode,previewGraph,graphParameters,orderGraphParameters} from '../src/generated/engine.mjs'
import {configureDocument,nodeControls} from '../src/node-controls.js'
import {replaceNodes,inspect,applyWires,connections,editNode,readGraph,writeGraph} from '../src/model.js'
import {insertSavedReference} from '../src/reference-groups.js'
const library={forms:{},modules:{}},n=(kind,id,extra={})=>({...newNode(true,kind,id,0,0,true),...extra})
const configure=(d,path,v)=>configureDocument('building',d,cardResult(d).id,path,v)
function definition(){let d=configure(newCard(),'resultType','COUNT');d=replaceNodes(d,[...d.graph.nodes,n('COUNT','fallback',{countValue:3}),n('GROUP_INPUT','argument',{name:'Menge',valueType:'COUNT',defaultSource:'fallback'})]);d=configure(d,'resultMode','VALUE');const rows=inspect('building',d);return applyWires('building',d,rows,[...connections(rows),{source:'argument',target:cardResult(d).id,port:'resultSource'}])}
test('new cards generate their native default directly and share all eight roof-template ports',()=>{
 let d=newCard();assert.equal(cardResult(d).kind,'RESULT');assert.equal(cardResult(d).resultMode,'CONSTRUCT');assert.equal(d.graph.nodes.length,2)
 assert.equal(previewGraph(true,d,library,'').count,1)
 d=configure(d,'resultType','ROOF_TEMPLATE')
 assert.deepEqual(inspect('building',d).find(e=>e.node.id===cardResult(d).id).ports.map(p=>p.id),Array.from({length:8},(_,i)=>`in:${i}`))
 assert.equal(previewGraph(true,d,library,'').value,'Dachvorlage · 0 / 8 Bauteile')
 const before=JSON.stringify(d);d=configure(configure(d,'resultMode','VALUE'),'resultMode','CONSTRUCT');assert.equal(JSON.stringify(d),before)
 assert.equal(nodeControls('building',cardResult(d)).find(c=>c.path==='producerKind').value,'ROOF_TEMPLATE')
})
test('native references expose stable named arguments and evaluate defaults and two overrides independently',()=>{
 const source=definition(),entry={id:'version-1',documentId:'quantity',version:1,name:'Menge',mode:'building',collection:'graphs',document:source,library}
 let result=insertSavedReference({mode:'building',document:newCard(),library,materials:{},entry})
 const first=result.nodeId;result=insertSavedReference({mode:'building',...result,entry});const second=result.nodeId
 let d=replaceNodes(result.document,[...result.document.graph.nodes,n('COUNT','five',{countValue:5}),n('COUNT','nine',{countValue:9})])
 let rows=inspect('building',d);assert.equal(rows.find(e=>e.node.id===first).ports[0].name,'Menge')
 assert.equal(previewGraph(true,d,result.library,first).value,'Anzahl: 3')
 d=applyWires('building',d,rows,[...connections(rows),{source:'five',target:first,port:'arg:argument'},{source:'nine',target:second,port:'arg:argument'}])
 assert.equal(previewGraph(true,d,result.library,first).value,'Anzahl: 5');assert.equal(previewGraph(true,d,result.library,second).value,'Anzahl: 9')
 const exported=writeGraph('building',d),restored=readGraph(JSON.parse(JSON.stringify(exported)),'building')
 assert.equal(previewGraph(true,restored,{},first).value,'Anzahl: 5');assert.ok(exported.graph.graphDefinitions['quantity@1'])
})
test('version changes preserve argument identities through renaming and ordering',()=>{
 const source=definition(),entry={id:'version-1',documentId:'quantity',version:1,name:'Menge',mode:'building',collection:'graphs',document:source,library}
 let result=insertSavedReference({mode:'building',document:newCard(),library,materials:{},entry})
 let d=replaceNodes(result.document,[...result.document.graph.nodes,n('COUNT','five',{countValue:5})]),rows=inspect('building',d)
 d=applyWires('building',d,rows,[...connections(rows),{source:'five',target:result.nodeId,port:'arg:argument'}])
 const renamed=editNode('building',source,{...source.graph.nodes.find(n=>n.id==='argument'),name:'Neue Menge'})
 result=insertSavedReference({mode:'building',document:d,library:result.library,materials:{},entry:{...entry,id:'version-2',version:2,document:renamed},targetId:result.nodeId})
 assert.equal(result.document.graph.nodes.find(n=>n.id===result.nodeId).arguments.argument,'five');assert.equal(previewGraph(true,result.document,result.library,result.nodeId).value,'Anzahl: 5')
 assert.equal(inspect('building',result.document).find(e=>e.node.id===result.nodeId).ports[0].name,'Neue Menge')
})
test('open parameters remain editable and explicit ordering survives rename deletion and serialization',()=>{
 let d=replaceNodes(newCard(),[...newCard().graph.nodes,n('GROUP_INPUT','a',{name:'Erster'}),n('GROUP_INPUT','b',{name:'Zweiter'})])
 d={...d,graph:orderGraphParameters(d,['b','a'])};assert.deepEqual(graphParameters(d).map(p=>p.id),['b','a'])
 d=editNode('building',d,{...d.graph.nodes.find(n=>n.id==='b'),name:'Neu'})
 assert.deepEqual(graphParameters(d).map(p=>p.id),['b','a'])
 assert.throws(()=>previewGraph(true,d,library,'a'),/Gruppeneingang/)
 const before=d;d=applyWires('building',d,inspect('building',d),[],['a','b'])
 assert.equal(graphParameters(d).length,0);assert.equal(graphParameters(before).length,2)
})
