import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {legacyCard,openCard} from '../src/cards.js'
import {cardResult} from '../src/card-types.js'
import {describeNode,newNode,previewGraph} from '../src/generated/engine.mjs'
import {editNode,replaceNodes,inspect,writeGraph,readGraph} from '../src/model.js'
import {saveNodeGroup} from '../src/node-groups.js'
import {insertSavedReference,groupViews} from '../src/reference-groups.js'
import {groupVersions,deleteVersions,deletionState} from '../src/version-groups.js'
const fixture=name=>JSON.parse(readFileSync(new URL(`../../examples/${name}.json`,import.meta.url)))
const library=fixture('library')
test('one card contains Copycats, integers and positions with a typed result',()=>{
 let document=legacyCard(),result=cardResult(document)
 assert.equal(document.graph.schema,8)
 assert.equal(previewGraph(true,document,library,'').count,1)
 document=replaceNodes(document,[...document.graph.nodes,{...newNode(true,'INT','value',0,0,true),countValue:7}])
 document=editNode('building',document,{...result,resultType:'INT',inputs:['value']})
 assert.equal(previewGraph(true,document,library,'').value,'Ganzzahl: 7')
 assert.deepEqual(describeNode(true,document,result.id).ports[0].types,['INT'])
 assert.throws(()=>editNode('building',document,{...cardResult(document),inputs:['source']}),/Incompatible|incompatible|input/i)
 readGraph(writeGraph('building',document),'building')
})
test('old form and building files open as independent typed cards without changing the originals',()=>{
 for(const name of ['nodegraph-form','nodegraph-rotation-xor','nodegraph-material-groups','building-graph-assembly']) {
  const document=fixture(name),before=JSON.stringify(document),isForm=!name.startsWith('building')
  const expected=previewGraph(!isForm,document,library,'')
  const opened=openCard({name,mode:isForm?'form':'building',document},library)
  const plan=previewGraph(true,opened.document,opened.library,'')
  assert.equal(plan.raw,expected.raw,name)
  assert.equal(JSON.stringify(document),before)
  assert.equal(cardResult(opened.document).resultType,isForm?'COPYCAT':'BUILDING')
 }
})
test('opening a node group creates a dedicated card and saving it does not include unrelated nodes',()=>{
 const source=fixture('building-graph-assembly')
 source.graph.nodes.push({...newNode(true,'INT','value',0,0,true),countValue:12})
 const entry={...saveNodeGroup('building',source,['value'],'Zahl',{},library),collection:'nodeGroups',documentId:'number',version:1}
 const opened=openCard(entry,library)
 assert.deepEqual(opened.document.graph.nodes.filter(n=>n.id!=='empty_wall' && !n.resultType).map(n=>n.id),['value'])
 assert.equal(cardResult(opened.document).resultType,'INT')
 assert.equal(previewGraph(true,opened.document,opened.library,'').value,'Ganzzahl: 12')
})
test('a typed scalar card can be inserted as a typed exact-version reference',()=>{
 let document=legacyCard();document=replaceNodes(document,[...document.graph.nodes,{...newNode(true,'INT','number',0,0,true),countValue:5}])
 document=editNode('building',document,{...cardResult(document),resultType:'INT',inputs:['number']})
 const result=insertSavedReference({mode:'building',document:legacyCard(),library,materials:{},entry:{id:'entry',documentId:'definition',version:3,name:'Fünf',mode:'building',collection:'graphs',document,library}})
 const ref=inspect('building',result.document).find(e=>e.node.id===result.nodeId)
 assert.equal(ref.outputType,'INT')
 assert.equal(ref.node.kind,'GRAPH_REFERENCE')
 assert.equal(previewGraph(true,result.document,result.library,ref.node.id).value,'Ganzzahl: 5')
 assert.equal(result.document.graph.nodes.filter(n=>n.resultType).length,1)
})
test('version list groups identities and deletion is atomic with exact version locks',()=>{
 const a={id:'a1',documentId:'a',version:1,name:'Karte',collection:'graphs'},b={...a,id:'a2',version:2}
 const groups=groupVersions([a,b,{...a,id:'g1',collection:'nodeGroups'}])
 assert.equal(groups.length,2);assert.deepEqual(groups[0].versions.map(v=>v.version),[2,1])
 const owner={id:'owner',name:'Verbraucher',document:{graph:{nodes:[{id:'reference',libraryReference:{documentId:'a',version:1}}]}}}
 const workspace={graphs:[a,b,owner],nodeGroups:[],drafts:{}}
 assert.equal(deletionState(workspace,[a,b])[0].references.length,1)
 assert.equal(deleteVersions(workspace,'graphs',['a2']).graphs.length,2)
 assert.throws(()=>deleteVersions(workspace,'graphs',['a1','a2']),/verwendet/)
 assert.equal(workspace.graphs.length,3)
 assert.equal(deleteVersions({...workspace,graphs:[a,b]},'graphs',['a1','a2']).graphs.length,0)
})


test('React Flow selection changes preserve multi-selection without being replaced by measurements',async()=>{
 const {selectNodes,clickNodes}=await import('../src/selection.js')
 assert.deepEqual(clickNodes(['a'],'b',true),['a','b'])
 assert.deepEqual(clickNodes(['a','b'],'b',true),['a'])
 let selected=selectNodes([],[{type:'select',id:'a',selected:true}])
 selected=selectNodes(selected,[{type:'select',id:'b',selected:true}])
 assert.deepEqual(selected,['a','b'])
 assert.equal(selectNodes(selected,[{type:'dimensions',id:'a',dimensions:{width:200,height:100}}]),selected)
 assert.deepEqual(selectNodes(selected,[{type:'select',id:'a',selected:false}]),['b'])
})


test('several pure value card references do not exhaust material roles',()=>{
 let source=legacyCard();source=replaceNodes(source,[...source.graph.nodes,{...newNode(true,'INT','number',0,0,true),countValue:5}])
 source=editNode('building',source,{...cardResult(source),resultType:'INT',inputs:['number']})
 let target={document:legacyCard(),library,materials:{}}
 const roles=Object.keys(target.document.palette)
 for(let index=0;index<3;index++) target=insertSavedReference({mode:'building',...target,entry:{id:`entry${index}`,documentId:`definition${index}`,version:1,name:`Wert ${index}`,mode:'building',collection:'graphs',document:source,library}})
 assert.deepEqual(Object.keys(target.document.palette),roles)
 const references=target.document.graph.nodes.filter(n=>n.kind==='GRAPH_REFERENCE')
 assert.equal(references.length,3)
 for(const ref of references) assert.equal(previewGraph(true,target.document,target.library,ref.id).value,'Ganzzahl: 5')
})
