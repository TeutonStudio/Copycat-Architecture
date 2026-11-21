import nativeTest from 'node:test'
import assert from 'node:assert/strict'
import {newNode,previewGraph,compileForm} from '../src/generated/engine.mjs'
import {saveNodeGroup} from '../src/node-groups.js'
import {insertSavedReference,groupViews} from '../src/reference-groups.js'
import {model,inspect,connections,applyWires,writeGraph,readGraph,duplicateSelection,editNode} from '../src/model.js'
const test=(name,fn)=>nativeTest(name,{skip:!model.building.includes('TRANSFORMATION')},fn)
const n=(building,kind,id,patch={})=>({...newNode(building,kind,id,0,0,true),...patch})
const form=nodes=>({schema:3,graph:{schema:7,nodes:[...nodes,n(false,'OUTPUT','out',{inputs:['op']})]}})
const building=nodes=>({schema:2,palette:{wall:'minecraft:bricks',frame:'minecraft:oak_planks'},graph:{schema:10,nodes:[n(true,'SEGMENT','empty_wall',{block:'minecraft:air',length:1}),...nodes,n(true,'BUILDING','result',{resultType:'COPYCAT',inputs:['op']})]}})
const preview=(b,d,id='op')=>previewGraph(b,d,{},id)
function points(plan) {
 const result=new Set()
 for(const m of plan.meshes) for(const [px,py,pz] of m.positions) {
  for(const [x,y,z,X,Y,Z] of m.boxes) for(let a=x;a<X;a++)for(let b=y;b<Y;b++)for(let c=z;c<Z;c++)result.add(`${16*px+a},${16*py+b},${16*pz+c}`)
  if(m.faces) for(let x=0;x<16;x++)for(let y=0;y<16;y++)for(let z=0;z<16;z++) {
   let hits=0
   for(const f of m.faces) {
    const [nx,ny,nz]=f.normal;if(Math.abs(nx)<1e-9)continue
    const v=f.vertices,[a,b,c]=v[0],rayX=a-(ny*(y+.5-b)+nz*(z+.5-c))/nx
    if(rayX<=x+.5)continue
    let inside=false
    for(let i=0,j=v.length-1;i<v.length;j=i++) {
     const yi=v[i][1],zi=v[i][2],yj=v[j][1],zj=v[j][2]
     if((zi>z+.5)!==(zj>z+.5) && y+.5<(yj-yi)*(z+.5-zi)/(zj-zi)+yi)inside=!inside
    }
    if(inside)hits++
   }
   if(hits%2)result.add(`${16*px+x},${16*py+y},${16*pz+z}`)
  }
 }
 return result
}
const shift=(set,p)=>new Set([...set].map(key=>key.split(',').map((v,i)=>Number(v)+[p.x,p.y,p.z][i]).join(',')))
const byte={source:'copycats:copycat_byte',offset:{x:4,y:4,z:4}}

test('both catalogs expose typed SRP values and Copycat operators; building also exposes segment operators',()=>{
 for(const b of [false,true]) {
  const value=n(b,'TRANSFORMATION','value'),op=n(b,'COPYCAT_TRANSFORM','op',{inputs:['source'],...(b?{transformationValue:'value'}:{inputs:['source','value']})})
  const d=b?building([n(true,'COPYCAT','source',{inlinePart:byte}),value,op]):form([n(false,'INPUT','source',{part:byte}),value,op])
  const entries=inspect(b?'building':'form',d)
  assert.equal(entries.find(e=>e.node.id==='value').ports.length,3)
  assert.equal(entries.find(e=>e.node.id==='value').outputType,b?'TRANSFORMATION':'transformation')
  assert.equal(preview(b,d).count,preview(b,d,'source').count)
 }
 assert.equal(n(true,'SEGMENT_TRANSFORM','op').inputs.length,1)
})

test('Copycat SRP matches explicit mirror, rotate, position and differs from another order',()=>{
 for(const b of [false,true]) {
  const source=n(b,b?'COPYCAT':'INPUT','source',b?{inlinePart:byte}:{part:byte})
  const value=n(b,'TRANSFORMATION','value',{mirror:{plane:'X'},rotationAxis:'Y',quarterTurns:1,translation:{x:1,y:0,z:0}})
  const mirror=n(b,b?'COPYCAT_MIRROR':'MIRROR','mirror',{inputs:b?['source']:['source',''],mirror:{plane:'X'}})
  const rotate=n(b,b?'COPYCAT_ROTATE':'ROTATE','rotate',{inputs:b?['mirror']:['mirror',''],rotationAxis:'Y',quarterTurns:1})
  const position=n(b,b?'COPYCAT_POSITION':'TRANSLATE','position',{inputs:b?['rotate']:['rotate',''],translation:{x:1,y:0,z:0}})
  const op=n(b,'COPYCAT_TRANSFORM','op',b?{inputs:['source'],transformationValue:'value'}:{inputs:['source','value']})
  const d=b?building([source,value,mirror,rotate,position,op]):form([source,value,mirror,rotate,position,op])
  assert.deepEqual(points(preview(b,d)),points(preview(b,d,'position')))
  assert.notDeepEqual(points(preview(b,d)),points(preview(b,d,'source')))
  const round=readGraph(JSON.parse(JSON.stringify(writeGraph(b?'building':'form',d))),b?'building':'form')
  assert.deepEqual(points(preview(b,round)),points(preview(b,d)))
  if(!b) assert.ok(compileForm(d).length)
 }
})

test('linked components override inline fields, propagate edits and reject incompatible wiring',()=>{
 for(const b of [false,true]) {
  const nodes=[n(b,b?'COPYCAT':'INPUT','source',b?{inlinePart:byte}:{part:byte}),n(b,'MIRRORING','m',b?{mirrors:[{plane:'X'}]}:{mirror:{plane:'X'}}),n(b,'ROTATION','r',b?{rotations:[{axis:'Y',quarterTurns:1}]}:{rotationAxis:'Y',quarterTurns:1}),n(b,'POSITION','p',{translation:{x:1,y:0,z:0}}),n(b,'TRANSFORMATION','value',b?{mirroring:'m',rotation:'r',positionValue:'p',translation:{x:99,y:99,z:99}}:{inputs:['m','r','p'],translation:{x:99,y:99,z:99}}),n(b,'COPYCAT_TRANSFORM','op',b?{inputs:['source'],transformationValue:'value'}:{inputs:['source','value']}),n(b,'INT','number',{countValue:3})]
  const mode=b?'building':'form',d=b?building(nodes):form(nodes)
  assert.ok(preview(b,d).count>0)
  const entries=inspect(mode,d)
  assert.throws(()=>applyWires(mode,d,entries,[...connections(entries).filter(w=>!(w.target==='op' && w.port===(b?'transformationValue':'1'))),{source:'number',target:'op',port:b?'transformationValue':'1'}]),/Incompatible|compatible|Invalid/)
  const removed=applyWires(mode,d,entries,connections(entries).filter(w=>w.source!=='value' && w.target!=='value'),['value'])
  assert.deepEqual(points(preview(b,removed)),points(preview(b,removed,'source')))
 }
})

test('segment SRP preserves geometry across signed and sub-block positions and subsequent operators',()=>{
 const d=building([n(true,'COPYCAT','source',{inlinePart:{source:'copycats:copycat_block'}}),n(true,'SEGMENT','segment',{inputs:['source'],length:2,height:1,assembly:false,module:''}),n(true,'TRANSFORMATION','value',{translation:{x:-1,y:0,z:0}}),n(true,'SEGMENT_TRANSFORM','op',{inputs:['segment'],transformationValue:'value'}),n(true,'SEGMENT_ROTATE','after',{inputs:['op'],rotationAxis:'Y',quarterTurns:0})])
 d.graph.nodes.find(n=>n.id==='result').resultType='WALL'
 const source=points(preview(true,d,'segment')),actual=points(preview(true,d))
 assert.deepEqual(actual,shift(source,{x:-1,y:0,z:0}))
 assert.deepEqual(points(preview(true,d,'after')),actual)
 let previous='op'
 for(let i=0;i<4;i++){const id='turn'+i;d.graph.nodes.push(n(true,'SEGMENT_ROTATE',id,{inputs:[previous],rotationAxis:'Y',quarterTurns:1}));previous=id}
 assert.deepEqual(points(preview(true,d,previous)),actual)
 const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
 assert.deepEqual(points(preview(true,round)),actual)
 const positioned=structuredClone(d);positioned.graph.nodes.find(n=>n.id==='value').translation={x:32,y:16,z:-16}
 assert.deepEqual(points(preview(true,positioned)),shift(source,{x:32,y:16,z:-16}))
})

test('segment mirror and rotate precede its position and work as inputs to further composition',()=>{
 const d=building([n(true,'COPYCAT','source',{inlinePart:byte}),n(true,'SEGMENT','segment',{inputs:['source'],length:2,height:1,assembly:false,module:''}),n(true,'TRANSFORMATION','value',{mirror:{plane:'X'},rotationAxis:'Y',quarterTurns:1,translation:{x:16,y:0,z:0}}),n(true,'SEGMENT_TRANSFORM','op',{inputs:['segment'],transformationValue:'value'}),n(true,'SEGMENT_MIRROR','m',{inputs:['segment'],rotationAxis:'X'}),n(true,'SEGMENT_ROTATE','r',{inputs:['m'],rotationAxis:'Y',quarterTurns:1}),n(true,'SEGMENT_UNION','u',{inputs:['segment','op']})])
 d.graph.nodes.find(n=>n.id==='result').resultType='WALL'
 assert.deepEqual(points(preview(true,d)),shift(points(preview(true,d,'r')),{x:16,y:0,z:0}))
 assert.deepEqual(points(preview(true,d,'u')),new Set([...points(preview(true,d)),...points(preview(true,d,'segment'))]))
})

test('named transformation sockets and duplication keep actual source connections',()=>{
 let d=building([n(true,'COPYCAT','source',{inlinePart:byte}),n(true,'TRANSFORMATION','value',{quarterTurns:1}),n(true,'COPYCAT_TRANSFORM','op',{inputs:['source'],transformationValue:'value'})])
 d.graph.nodeGroups=[{id:'g',name:'Transform',nodes:['op'],graphInterface:{inputs:[{id:'t',name:'Transformation',type:'TRANSFORMATION',targets:[{member:0,port:'transformationValue'}]}],outputs:[{id:'out',name:'Copycat',type:'COPYCAT',member:0}]},inputConnections:{t:'value'}}]
 inspect('building',d)
 d.graph.nodeGroups=[]
 const duplicated=duplicateSelection('building',d,['source','value','op'],['source_copy','value_copy','op_copy'])
 assert.equal(duplicated.graph.nodes.find(n=>n.id==='op_copy').transformationValue,'value_copy')
 assert.deepEqual(points(preview(true,duplicated,'op_copy')),points(preview(true,d)))
})


test('saved form transformation groups can be used in building graphs with typed component wires intact',()=>{
 const d=form([n(false,'INPUT','source',{part:byte}),n(false,'MIRRORING','mirror',{mirror:{plane:'X'}}),n(false,'ROTATION','rotation',{rotationAxis:'Y',quarterTurns:1}),n(false,'POSITION','position',{translation:{x:1,y:0,z:0}}),n(false,'TRANSFORMATION','value',{inputs:['mirror','rotation','position'],transformationOrder:'PRS'}),n(false,'COPYCAT_TRANSFORM','op',{inputs:['source','value']})])
 const entry={...saveNodeGroup('form',d,['source','mirror','rotation','position','value','op'],'SRP'),collection:'nodeGroups',documentId:'srpGroup',version:1}
 const target=building([n(true,'COPYCAT','op',{inlinePart:byte})])
 const result=insertSavedReference({mode:'building',document:target,library:{forms:{},modules:{}},materials:{},entry})
 const view=groupViews(result.document,inspect('building',result.document))[0]
 assert.ok(view)
 const actual=previewGraph(true,result.document,result.library,view.outputs[0].id)
 assert.deepEqual(points(actual),points(preview(false,d)))
 const value=result.document.graph.nodes.find(node=>view.nodes.includes(node.id) && node.kind==='TRANSFORMATION')
 assert.ok(value.rotation && value.mirroring && value.positionValue)
 assert.equal(value.transformationOrder,'PRS')
 const op=result.document.graph.nodes.find(node=>view.nodes.includes(node.id) && node.kind==='COPYCAT_TRANSFORM')
 assert.equal(op.transformationValue,value.id)
})


test('incomplete position computations remain editable when a segment uses the transformation',()=>{
 const d=building([n(true,'COPYCAT','source',{inlinePart:byte}),n(true,'SEGMENT','segment',{inputs:['source'],length:2,height:1,assembly:false,module:''}),n(true,'POSITION_ADD','position'),n(true,'TRANSFORMATION','value',{positionValue:'position'}),n(true,'SEGMENT_TRANSFORM','op',{inputs:['segment'],transformationValue:'value'})])
 d.graph.nodes.find(node=>node.id==='result').resultType='WALL'
 assert.doesNotThrow(()=>inspect('building',d))
 assert.throws(()=>preview(true,d),/Connect every input/)
})

const orders=['SRP','SPR','RSP','RPS','PRS','PSR']
test('all six orders evaluate as explicit chains and move typed sockets without changing their sources',()=>{
 for(const b of [false,true]) {
  const mode=b?'building':'form'
  const nodes=[n(b,b?'COPYCAT':'INPUT','source',b?{inlinePart:byte}:{part:byte}),n(b,'MIRRORING','m',b?{mirrors:[{plane:'X'}]}:{mirror:{plane:'X'}}),n(b,'ROTATION','r',b?{rotations:[{axis:'Y',quarterTurns:1}]}:{rotationAxis:'Y',quarterTurns:1}),n(b,'POSITION','p',{translation:{x:1,y:0,z:0}}),n(b,'TRANSFORMATION','value',b?{mirroring:'m',rotation:'r',positionValue:'p'}:{inputs:['m','r','p']}),n(b,'COPYCAT_TRANSFORM','op',b?{inputs:['source'],transformationValue:'value'}:{inputs:['source','value']})]
  let d=b?building(nodes):form(nodes)
  assert.equal(inspect(mode,d).find(e=>e.node.id==='value').node.transformationOrder,'SRP')
  const expectedWires=connections(inspect(mode,d))
  const results=[]
  for(const order of orders) {
   d=editNode(mode,d,{...d.graph.nodes.find(n=>n.id==='value'),transformationOrder:order})
   const ports=inspect(mode,d).find(e=>e.node.id==='value').ports
   assert.deepEqual(ports.map(p=>p.source),[...order].map(letter=>({S:'m',R:'r',P:'p'}[letter])))
   assert.deepEqual(ports.map(p=>p.id),[...order].map(letter=>b?{S:'mirroring',R:'rotation',P:'positionValue'}[letter]:String('SRP'.indexOf(letter))))
   assert.deepEqual(new Set(connections(inspect(mode,d)).map(JSON.stringify)),new Set(expectedWires.map(JSON.stringify)))
   let previous='source';const chain=[]
   for(const letter of order) {
    const id='chain'+chain.length
    const kind=b?{S:'COPYCAT_MIRROR',R:'COPYCAT_ROTATE',P:'COPYCAT_POSITION'}[letter]:{S:'MIRROR',R:'ROTATE',P:'TRANSLATE'}[letter]
    const component={S:'m',R:'r',P:'p'}[letter],key={S:'mirroring',R:'rotation',P:'positionValue'}[letter]
    chain.push(n(b,kind,id,b?{inputs:[previous],[key]:component}:{inputs:[previous,component]}));previous=id
   }
   const withChain={...d,graph:{...d.graph,nodes:[...d.graph.nodes,...chain]}}
   const actual=points(preview(b,d));results.push([...actual].sort().join(';'))
   assert.deepEqual(actual,points(preview(b,withChain,previous)),`${mode} ${order}`)
   const round=readGraph(JSON.parse(JSON.stringify(writeGraph(mode,d))),mode)
   assert.equal(round.graph.nodes.find(n=>n.id==='value').transformationOrder,order)
   assert.deepEqual(points(preview(b,round)),actual)
  }
  assert.ok(new Set(results).size>=4,'order affects noncommuting geometry')
  const legacy=structuredClone(d);delete legacy.graph.nodes.find(n=>n.id==='value').transformationOrder
  assert.deepEqual(points(preview(b,legacy)),points(preview(b,editNode(mode,d,{...d.graph.nodes.find(n=>n.id==='value'),transformationOrder:'SRP'}))))
 }
})

test('segment orders apply their operations sequentially, including a sub-block position first',()=>{
 const source=n(true,'COPYCAT','source',{inlinePart:byte}),segment=n(true,'SEGMENT','segment',{inputs:['source'],length:2,height:1,assembly:false,module:''})
 for(const order of orders) {
  const value=n(true,'TRANSFORMATION','value',{transformationOrder:order,mirror:{plane:'X'},rotationAxis:'Y',quarterTurns:1,translation:{x:1,y:0,z:0}})
  const op=n(true,'SEGMENT_TRANSFORM','op',{inputs:['segment'],transformationValue:'value'})
  const direct=building([source,segment,value,op]);direct.graph.nodes.find(n=>n.id==='result').resultType='WALL'
  let previous='segment';const chain=[source,segment]
  for(const letter of order) {
   const id='step'+chain.length
   if(letter==='P') {chain.push(n(true,'TRANSFORMATION','position',{translation:value.translation}));chain.push(n(true,'SEGMENT_TRANSFORM',id,{inputs:[previous],transformationValue:'position'}))}
   else chain.push(n(true,letter==='S'?'SEGMENT_MIRROR':'SEGMENT_ROTATE',id,{inputs:[previous],rotationAxis:letter==='S'?'X':'Y',quarterTurns:1}))
   previous=id
  }
  const explicit=building([...chain,n(true,'SEGMENT_TRANSFORM','op',{inputs:[previous]})]);explicit.graph.nodes.find(n=>n.id==='result').resultType='WALL'
  assert.deepEqual(points(preview(true,direct)),points(preview(true,explicit)),order)
 }
})
