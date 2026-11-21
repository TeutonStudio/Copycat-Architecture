import nodeTest from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {newNode,describeNode,previewGraph,arrangementGraph,starterBuilding,authoringBuildingNode,remapBuildingReferences,graphModels} from '../src/generated/engine.mjs'
import {applyWires,connections,editNode,inspect,readGraph,replaceNodes,writeGraph,editInputs} from '../src/model.js'
import {switchOperator} from '../src/operator-families.js'
const test=(name,run)=>nodeTest(name,{skip:!graphModels().directAreas},run)
const fixture=name=>JSON.parse(readFileSync(new URL(`../../examples/${name}.json`,import.meta.url)))
const library=fixture('library'),base=()=>fixture('building-graph-assembly')
const n=(kind,id)=>({...newNode(true,kind,id,0,0,true)})
const plan=(d,id)=>previewGraph(true,d,library,id)
const positions=p=>p.meshes.flatMap(m=>m.positions).map(p=>p.join(',')).sort()
const documentWith=(...nodes)=>{const d=base();return replaceNodes(d,[...d.graph.nodes,...nodes])}

test('a direct sparse segment area matches its old converter, including depth and foreground assignments',()=>{
 const area={...n('AREA','area'),areaMode:'DRAW',coordinates:[{x:0,y:0,z:0},{x:1,y:0,z:0},{x:0,y:0,z:1}]}
 const layout={...n('SEGMENT_LAYOUT','old_layout'),geometry:'area',depth:2}
 const direct={...n('SEGMENT','direct'),layout:'area',depth:2,inputs:['wall_copycat']}
 const legacy={...direct,id:'legacy',layout:'old_layout'}
 const d=documentWith(area,layout,direct,legacy)
 assert.equal(d.graph.schema,11)
 assert.deepEqual(plan(d,'direct'),plan(d,'legacy'))
 assert.equal(plan(d,'direct').count,6)
 const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
 assert.deepEqual(plan(round,'direct'),plan(d,'direct'))
})

test('a direct room area keeps height at its consumer and reproduces the old room geometry',()=>{
 const area={...n('AREA','area'),areaMode:'RECTANGLE',length:3,height:3}
 const geometry={...n('ROOM_GEOMETRY','old_geometry'),geometry:'area',height:5}
 const old={...n('ROOM','old_room'),geometry:'old_geometry',height:5,floor:'floor',ceiling:'ceiling'}
 const direct={...old,id:'direct_room',geometry:'area'}
 const d=documentWith(area,geometry,old,direct)
 assert.deepEqual(plan(d,'old_room'),plan(d,'direct_room'))
 assert.equal(inspect('building',d).find(e=>e.node.id==='direct_room').node.height,5)
 assert.deepEqual(applyWires('building',d,inspect('building',d),connections(inspect('building',d))),d)
})

test('embedded roof areas reproduce old roof levels, retain stable area sockets and remap references',()=>{
 const area={...n('AREA','area'),areaMode:'DRAW',coordinates:Array.from({length:9},(_,i)=>({x:i%3,y:0,z:Math.floor(i/3)}))}
 const config={...n('ROOF_LEVEL','level').roofLevel,area:'area',overhang16:0}
 const level={...n('ROOF_LEVEL','level'),roofLevel:config}
 const layout={...n('ROOF_LAYOUT','layout'),inputs:['level']}
 const old={...n('ROOF','legacy_roof'),layout:'layout',inputs:[]}
 const direct={...old,id:'direct_roof',layout:'',roofSections:[{id:'west',name:'Westdach',config,dormers:[]}]}
 const d=documentWith(area,level,layout,old,direct)
 assert.deepEqual(plan(d,'direct_roof'),plan(d,'legacy_roof'))
 assert.ok(inspect('building',d).find(e=>e.node.id==='direct_roof').ports.some(p=>p.id==='area:west' && p.source==='area'))
 assert.equal(remapBuildingReferences(direct,{area:'other'}).roofSections[0].config.area,'other')
 const changed=editNode('building',d,{...direct,roofSections:[{...direct.roofSections[0],name:'Neues Dach'}]})
 assert.equal(inspect('building',changed).find(e=>e.node.id==='direct_roof').ports[0].id,'area:west')
 assert.throws(()=>editNode('building',d,{...direct,roofSections:[...direct.roofSections,...direct.roofSections]}),/sixteen|unique/)
})

for(const [kind,count] of [['SEGMENT_UNION',8],['SEGMENT_INTERSECTION',2],['SEGMENT_DIFFERENCE',4],['SEGMENT_SYMMETRIC_DIFFERENCE',6]]) test(`${kind} positions each input before evaluating the operation`,()=>{
 const a={...n('SEGMENT','a'),assembly:false,module:'',form:'full@1',length:3,height:2,depth:1}
 const b={...a,id:'b',length:2}
 const op={...n(kind,'op'),inputs:['a','b'],operandOffsets:[{x:0,y:0,z:0},{x:2,y:0,z:0}]}
 const d=documentWith(a,b,op)
 assert.equal(plan(d,'op').count,count)
 for(const plane of ['X','Y','Z']) {
  const grid=arrangementGraph(d,library,'op',plane)
  assert.equal(grid.items.length,2)
  assert.deepEqual(grid.items[1].offset,op.operandOffsets[1])
 }
 const round=readGraph(writeGraph('building',d),'building')
 assert.deepEqual(plan(round,'op'),plan(d,'op'))
 const moved=editNode('building',d,{...op,operandOffsets:[{x:-3,y:-2,z:1},{x:-1,y:-2,z:1}]})
 assert.equal(plan(moved,'op').count,count)
 assert.ok(positions(plan(moved,'op')).some(p=>p.startsWith('-')))
})

test('repeated sources have independent offsets and an empty intersection still displays the operands',()=>{
 const a={...n('SEGMENT','a'),assembly:false,module:'',form:'full@1',length:1,height:1,depth:1}
 const op={...n('SEGMENT_INTERSECTION','op'),inputs:['a','a'],operandOffsets:[{x:0,y:0,z:0},{x:4,y:0,z:0}]}
 const d=documentWith(a,op)
 assert.equal(plan(d,'op').count,0)
 assert.deepEqual(arrangementGraph(d,library,'op','Y').items.map(i=>i.cells),[[[0,0]],[[4,0]]])
 const reordered=editInputs('building',d,'op',[1,0])
 assert.deepEqual(reordered.graph.nodes.find(n=>n.id==='op').operandOffsets,[op.operandOffsets[1],op.operandOffsets[0]])
 const switched=switchOperator('building',d,'op','SEGMENT_DIFFERENCE')
 assert.deepEqual(switched.graph.nodes.find(n=>n.id==='op').operandOffsets,op.operandOffsets)
})

test('incomplete operands remain visible and unary operators provide a read-only before/after raster',()=>{
 const a={...n('SEGMENT','a'),assembly:false,module:'',form:'full@1',length:3,height:1,depth:1}
 const op={...n('SEGMENT_UNION','op'),inputs:['a','']},rotated={...n('SEGMENT_ROTATE','rotated'),inputs:['a'],quarterTurns:1}
 const d=documentWith(a,op,rotated)
 const grid=arrangementGraph(d,library,'op','Z')
 assert.ok(grid.items[0].cells.length);assert.ok(grid.items[1].error)
 const unary=arrangementGraph(d,library,'rotated','Y');assert.equal(unary.editable,false);assert.equal(unary.items.length,2)
})

test('old ignored non-union offsets stay ignored and native authoring selects the direct area contract',()=>{
 const a={...n('SEGMENT','a'),assembly:false,module:'',form:'full@1',length:1,height:1,depth:1}
 const op={...n('SEGMENT_INTERSECTION','op'),inputs:['a','a'],offsets:[{x:0,y:0,z:0},{x:4,y:0,z:0}]}
 const d=documentWith(a,op)
 assert.equal(plan(d,'op').count,1)
 assert.deepEqual(arrangementGraph(d,library,'op','Y').items.map(i=>i.offset),[{x:0,y:0,z:0},{x:0,y:0,z:0}])
 const starter=starterBuilding();assert.equal(starter.graph.schema,11)
 assert.ok(starter.graph.nodes.every(n=>!['SEGMENT_LAYOUT','ROOM_GEOMETRY','ROOF_LEVEL','ROOF_LAYOUT'].includes(n.kind)))
 const roof=authoringBuildingNode(n('ROOF','roof_new'));assert.equal(roof.roofInputMode,'TEMPLATE');assert.deepEqual(describeNode(true,{...starter,graph:{...starter.graph,nodes:[...starter.graph.nodes,roof]}},roof.id).ports.map(p=>p.id),['geometry','roofTemplate'])
})

test('multiple embedded roof areas retain individual storey bindings and stable ports after reordering',()=>{
 const d=base(),storey=d.graph.nodes.find(n=>n.id==='storey')
 const upper={...storey,id:'upper',walls:{},rectangles:[{x:0,z:0,width:2,length:3}]}
 d.graph.nodes.find(n=>n.id==='building').levels={'0':'storey','1':'upper'}
 const lowerConfig={...n('ROOF_LEVEL','_sample').roofLevel,storey:'storey_0',form:'FLAT',overhang16:0}
 const upperConfig={...lowerConfig,storey:'storey_1'}
 const roof={...n('ROOF','direct'),inputs:[],roofSections:[{id:'lower',name:'Unteres Dach',config:lowerConfig,dormers:[]},{id:'upper',name:'Oberes Dach',config:upperConfig,dormers:[]}]}
 const old={...roof,id:'legacy',roofSections:null,layout:'legacy_layout'}
 const result=replaceNodes(d,[...d.graph.nodes,upper,roof,old,{...n('ROOF_LEVEL','lower_level'),roofLevel:lowerConfig},{...n('ROOF_LEVEL','upper_level'),roofLevel:upperConfig},{...n('ROOF_LAYOUT','legacy_layout'),inputs:['lower_level','upper_level']}])
 assert.deepEqual(plan(result,'direct'),plan(result,'legacy'))
 const reordered=editNode('building',result,{...roof,roofSections:[...roof.roofSections].reverse()})
 assert.deepEqual(new Set(inspect('building',reordered).find(e=>e.node.id==='direct').ports.filter(p=>p.id.startsWith('area:')).map(p=>p.id)),new Set(['area:lower','area:upper']))
 const round=readGraph(writeGraph('building',reordered),'building')
 assert.deepEqual(round.graph.nodes.find(n=>n.id==='direct').roofSections.map(s=>s.config.storey),['storey_1','storey_0'])
})


test('switching a legacy union with vertical positions retains those positions in every boolean operator',()=>{
 const a={...n('SEGMENT','a'),assembly:false,module:'',form:'full@1',length:1,height:1,depth:1}
 const op={...n('SEGMENT_UNION','op'),inputs:['a','a'],offsets:[{x:0,y:0,z:0},{x:0,y:1,z:0}]}
 const d=documentWith(a,op),next=switchOperator('building',d,'op','SEGMENT_DIFFERENCE')
 assert.deepEqual(next.graph.nodes.find(n=>n.id==='op').operandOffsets,op.offsets)
 assert.deepEqual(next.graph.nodes.find(n=>n.id==='op').offsets,[])
 assert.equal(plan(next,'op').count,1)
})
