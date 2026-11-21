import test from 'node:test'
import assert from 'node:assert/strict'
import {newNode,previewGraph} from '../src/generated/engine.mjs'
import {legacyCard} from '../src/cards.js'
import {connections,inspect,model,readGraph,replaceNodes,writeGraph} from '../src/model.js'
import {insertSavedReference,groupViews} from '../src/reference-groups.js'
import {saveNodeGroup} from '../src/node-groups.js'

const library={forms:{},modules:{}}
const form=()=>readGraph({schema:3,name:'Stufe',parts:[{source:'copycats:copycat_slab'}]},'form')
const formEntry=()=>({id:'form-v2',documentId:'external',version:2,name:'Gewählte Form',mode:'form',collection:'graphs',document:form(),materials:{}})
function sourceDocument(mode,x,y) {
  if(mode==='form') {
    const document=form(),source=document.graph.nodes.find(node=>node.kind==='INPUT')
    return {document:replaceNodes(document,document.graph.nodes.map(node=>node.id===source.id?{...node,x,y}:node)),targetId:source.id}
  }
  const document=legacyCard()
  return {document:replaceNodes(document,document.graph.nodes.map(node=>node.id==='source'?{...node,x,y}:node)),targetId:'source'}
}

for(const mode of ['form','building']) test(`${mode}: selecting a reference keeps the existing node position, wires and other nodes`,()=>{
  for(const [x,y] of [[-713,263],[0,0],[model.positionMax,model.positionMax]]) {
    const {document,targetId}=sourceDocument(mode,x,y),original=structuredClone(document)
    const result=insertSavedReference({mode,document,library,materials:{},entry:formEntry(),targetId})
    const selected=result.document.graph.nodes.find(node=>node.id===result.nodeId)
    assert.deepEqual([selected.x,selected.y],[x,y])
    assert.equal(selected.libraryReference.version,2)
    assert.ok(!result.document.graph.nodes.some(node=>node.id===targetId))
    const outgoing=connections(inspect(mode,result.document)).filter(wire=>wire.source===selected.id)
    assert.ok(outgoing.some(wire=>result.document.graph.nodes.find(node=>node.id===wire.target)?.kind===(mode==='form'?'OUTPUT':'BUILDING')))
    for(const node of original.graph.nodes.filter(node=>node.id!==targetId)) {
      const unchanged=result.document.graph.nodes.find(other=>other.id===node.id)
      assert.deepEqual([unchanged.x,unchanged.y],[node.x,node.y])
    }
    const restored=readGraph(JSON.parse(JSON.stringify(writeGraph(mode,result.document))),mode)
    const round=restored.graph.nodes.find(node=>node.id===selected.id)
    assert.deepEqual([round.x,round.y],[x,y])
    assert.deepEqual(document,original)
  }
})

function integerCard() {
  const document=legacyCard()
  return replaceNodes(document,[document.graph.nodes.find(node=>node.id==='empty_wall'),
    {...newNode(true,'INT','a',70,-10,true),countValue:2},
    {...newNode(true,'INT','b',310,140,true),countValue:3},
    {...newNode(true,'INT_ADD','sum',580,25,true),inputs:['a','b']},
    {...document.graph.nodes.find(node=>node.resultType),resultType:'INT',inputs:['sum']},
  ])
}
test('whole-card and node-group replacements keep the visible anchor and relative layout across repeated selections',()=>{
  const source=integerCard()
  const entries=[
    {id:'card-v1',documentId:'card',version:1,name:'Karte',mode:'building',collection:'graphs',document:source,library},
    {...saveNodeGroup('building',source,['a','b','sum'],'Gruppe',{},library),documentId:'group',version:2,collection:'nodeGroups'},
  ]
  for(const entry of entries) {
    let document=legacyCard()
    document=replaceNodes(document,document.graph.nodes.map(node=>node.id==='source'?{...newNode(true,'INT','source',-900,340,true),countValue:8}:node.resultType?{...node,resultType:'INT'}:node))
    let targetId='source'
    for(let iteration=0;iteration<2;iteration++) {
      const result=insertSavedReference({mode:'building',document,library,materials:{},entry,targetId})
      const view=groupViews(result.document,inspect('building',result.document)).find(group=>group.id===result.nodeId)
      const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
      assert.deepEqual(view?.position || {x:node.x,y:node.y},{x:-900,y:340})
      if(view)assert.deepEqual(view.members.map(({node})=>[node.x+900,node.y-340]),[[0,0],[240,150],[510,35]])
      else assert.equal(node.kind,'GRAPH_REFERENCE')
      const output=view?.outputs[0].id || node.id
      assert.equal(result.document.graph.nodes.find(node=>node.resultType).inputs[0],output)
      assert.equal(previewGraph(true,result.document,result.library,output).value,'Ganzzahl: 5')
      document=result.document;targetId=result.nodeId
    }
    const result=insertSavedReference({mode:'building',document,library,materials:{},entry:formEntry(),targetId})
    const node=result.document.graph.nodes.find(node=>node.id===result.nodeId)
    assert.deepEqual([node.x,node.y],[-900,340])
  }
})

test('inserting without a target still chooses free space and leaves every existing node in place',()=>{
  const {document}=sourceDocument('building',-713,263),original=structuredClone(document)
  const result=insertSavedReference({mode:'building',document,library,materials:{},entry:formEntry()})
  const added=result.document.graph.nodes.find(node=>node.id===result.nodeId)
  assert.notDeepEqual([added.x,added.y],[-713,263])
  assert.equal(result.document.graph.nodes.length,document.graph.nodes.length+1)
  for(const node of document.graph.nodes) assert.deepEqual(result.document.graph.nodes.find(other=>other.id===node.id),node)
  assert.deepEqual(document,original)
})
test('a stale target cannot silently turn a selection into a new insertion',()=>{
  const {document}=sourceDocument('building',120,320)
  assert.throws(()=>insertSavedReference({mode:'building',document,library,materials:{},entry:formEntry(),targetId:'deleted'}),/existiert nicht mehr/)
})
test('a frozen reference keeps its target position at the graph boundary',()=>{
  const document=integerCard(),template={id:'wide',documentId:'wide',version:1,name:'Breit',mode:'building',collection:'graphs',document,library}
  const {document:target,targetId}=sourceDocument('building',model.positionMax,model.positionMax),original=structuredClone(target)
  const result=insertSavedReference({mode:'building',document:target,library,materials:{},entry:template,targetId})
  const node=result.document.graph.nodes.find(n=>n.id===result.nodeId)
  assert.deepEqual([node.x,node.y],[model.positionMax,model.positionMax])
  assert.deepEqual(target,original)
})
