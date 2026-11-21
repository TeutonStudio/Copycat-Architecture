import test from 'node:test'
import assert from 'node:assert/strict'
import {previewGraph,duplicateGraph} from '../src/generated/engine.mjs'
import {editNode,writeGraph} from '../src/model.js'
import {appendVersion,migrateEntry,dependencies,deleteVersion} from '../src/documents.js'
import {copycatGroups,folderRows,visibleGroups} from '../src/copycat-folders.js'
const byte={id:'source',kind:'INPUT',part:{source:'copycats:copycat_byte'}}
const graph=(nodes=[byte])=>({schema:3,name:'Test',graph:{schema:4,nodes:[...nodes,{id:'result',kind:'OUTPUT',inputs:[nodes.at(-1).id]}]}})
const move={kind:'POSITION',offset:{x:2,y:0,z:0}}, rotate={kind:'ROTATE',axis:'Y',quarterTurns:1,pivot:{x:8,y:8,z:8}}, mirror={kind:'MIRROR',plane:'Z'}
test('ordered lists and typed node chain produce identical exact surfaces, receipts and material needs',()=>{
  const inline=graph([{...byte,copycatTransforms:[move,rotate,mirror]}])
  const chain=graph([byte,{id:'pos',kind:'POSITION',translation:move.offset},{id:'move',kind:'TRANSLATE',inputs:['source','pos']},{id:'rotate',kind:'ROTATE',inputs:['move',''],rotationAxis:'Y',quarterTurns:1},{id:'mirror',kind:'MIRROR',inputs:['rotate',''],mirror:{plane:'Z'}}])
  const a=previewGraph(false,inline,{},''),b=previewGraph(false,chain,{},'')
  assert.equal(a.count,b.count);assert.equal(a.raw,b.raw);assert.deepEqual(a.meshes,b.meshes);assert.deepEqual(a.materials,b.materials)
  assert.equal(writeGraph('form',inline).graph.schema,4)
  assert.equal(previewGraph(false,graph([{...byte,copycatTransforms:[{...move,enabled:false,offset:{x:-16,y:0,z:0}}]}]),{},'').count,512)
  assert.throws(()=>previewGraph(false,graph([{...byte,copycatTransforms:[{...move,offset:{x:-1,y:0,z:0}}]}]),{},''),/bounds/)
})
test('typed position has fallback, duplication retains local values and clears only external wires',()=>{
  const input=graph([byte,{id:'move',kind:'TRANSLATE',inputs:['source',''],translation:move.offset,copycatTransforms:[mirror]}])
  const duplicated=duplicateGraph(false,input,['source','move'],['copy1','copy2'])
  assert.equal(duplicated.graph.nodes.at(-1).inputs[0],'copy1');assert.equal(duplicated.graph.nodes.at(-1).x,32)
  assert.deepEqual(duplicated.graph.nodes.at(-1).copycatTransforms,[{kind:'MIRROR',enabled:true,axis:'Y',quarterTurns:1,pivot:{x:8,y:8,z:8},plane:'Z',offset:{x:0,y:0,z:0}}])
  const isolated=duplicateGraph(false,input,['move'],['copy'])
  assert.deepEqual(isolated.graph.nodes.at(-1).inputs,['','']);assert.throws(()=>duplicateGraph(false,input,['result'],['no']),/result/)
})
test('folder navigation groups names globally, exposes direct contents, and retains precise versions',()=>{
  const options=[{id:'a1',name:'Main/Sub/Byte',entry:{version:1}},{id:'a2',name:'Main/Sub/Byte',entry:{version:2}},{id:'b',name:'Main/Direct'},{id:'root',name:'Root'}]
  const groups=copycatGroups(options)
  assert.equal(groups.length,3);assert.equal(visibleGroups(groups,['Main'],'')[0].name,'Direct')
  assert.equal(visibleGroups(groups,[],'byte')[0].versions[0].id,'a2')
  assert.deepEqual(folderRows(groups,['Main','Sub']).map(r=>r.names),[['Main'],['Sub']])
})
test('migration retains identity; saving appends immutable versions; save-as starts a new identity',()=>{
  const old={id:'old',mode:'form',name:'A',document:graph()}
  assert.equal(migrateEntry(old).id,'old');assert.equal(migrateEntry(old).version,1)
  const versions=appendVersion([old],{...old,name:'B'},'old')
  assert.deepEqual(versions[0].document,old.document);assert.equal(versions[1].version,2);assert.notEqual(versions[1].id,'old')
  const savedAs=appendVersion(versions,old);assert.equal(savedAs.at(-1).version,1);assert.notEqual(savedAs.at(-1).documentId,'old')
})
test('actual references lock deletion; embedded copies and previous versions remain independent',()=>{
  const entry={id:'v1',documentId:'identity',version:1,name:'Byte'}
  const workspace={graphs:[entry,{id:'ref',name:'Consumer',references:['identity@1']}],nodeGroups:[],drafts:{}}
  assert.deepEqual(dependencies(workspace,entry),['Consumer']);assert.throws(()=>deleteVersion(workspace,'graphs','v1'),/verwendet/)
  const embedded={...workspace,graphs:[entry,{id:'copy',document:{graph:{nodes:[{form:'identity@1'}]}},library:{forms:{'identity@1':graph()}}}]}
  assert.equal(dependencies(embedded,entry).length,0);assert.equal(deleteVersion(embedded,'graphs','v1').graphs.length,1)
})

test('backups merge without overwriting an existing immutable version', async()=>{
 const {mergeVersions}=await import('../src/documents.js')
 const entry={id:'a',name:'A',mode:'form',document:graph()}
 assert.equal(mergeVersions([entry],[entry]).length,1)
 assert.throws(()=>mergeVersions([entry],[{...entry,document:graph([{...byte,part:{source:'copycats:copycat_slab'}}])}]),/kollidiert/)
 assert.deepEqual(entry.document,graph())
})

test('browser duplication preserves local extension metadata and exact reference versions',async()=>{
 const {duplicateSelection}=await import('../src/model.js')
 const document={...graph([{...byte,note:'local extension',part:{...byte.part,custom:'preserved'}}]),customDocument:{value:42}}
 const copy=duplicateSelection('form',document,['source'],['copy'])
 assert.equal(copy.graph.nodes.find(node=>node.id === 'source').note,'local extension')
 assert.equal(copy.graph.nodes.find(node=>node.id === 'copy').note,'local extension')
 assert.equal(copy.graph.nodes.find(node=>node.id === 'copy').part.custom,'preserved')
 assert.deepEqual(copy.customDocument,{value:42})
})

test('library group JSON drafts validate types and internal references before saving a version',async()=>{
 const {validateNodeGroup}=await import('../src/node-groups.js')
 const group={name:'Group',mode:'form',nodes:[byte],wires:[],groups:[{id:'body',name:'Body',role:'surface',priority:0}],materials:{}}
 assert.equal(validateNodeGroup(group),group)
 assert.throws(()=>validateNodeGroup({...group,wires:[{source:'missing',target:'source',port:'0'}]}),/fehlenden/)
 assert.throws(()=>validateNodeGroup({...group,schema:99}),/schema/)
 assert.throws(()=>validateNodeGroup({...group,nodes:[{...byte,part:{source:'missing:shape'}}]}),/shape/)
})
