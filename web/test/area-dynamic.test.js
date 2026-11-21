import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {areaGraph,describeNode,newNode,previewGraph} from '../src/generated/engine.mjs'
import {editNode,writeGraph,readGraph} from '../src/model.js'
import {areaModePatch,fitArea,paintArea,rasterLine} from '../src/area-grid.js'
const fixture=name=>JSON.parse(readFileSync(new URL(`../../examples/${name}.json`,import.meta.url)))
const library=fixture('library')
const base=()=>fixture('building-graph-assembly')
const addArea=()=>{const document=base();document.graph.schema=7;document.graph.nodes.push(newNode(true,'AREA','area',0,0,true));return document}

test('area factory offers one Anzahl socket for square, two for rectangle and none for drawing',()=>{
 let document=addArea();let area=document.graph.nodes.find(n=>n.id==='area')
 assert.equal(area.areaMode,'SQUARE');assert.equal(areaGraph(document,'area').points.length,9)
 assert.deepEqual(describeNode(true,document,'area').ports.map(p=>p.id),['length'])
 document=editNode('building',document,{...area,areaMode:'RECTANGLE',length:4,height:2})
 assert.deepEqual(describeNode(true,document,'area').ports.map(p=>p.id),['length','height'])
 assert.equal(areaGraph(document,'area').points.length,8)
 area=document.graph.nodes.find(n=>n.id==='area')
 document=editNode('building',document,{...area,...areaModePatch(area,'DRAW',areaGraph(document,'area').points)})
 assert.equal(areaGraph(document,'area').points.length,8)
 assert.deepEqual(describeNode(true,document,'area').ports,[])
 document=editNode('building',document,{...document.graph.nodes.find(n=>n.id==='area'),coordinates:[]})
 assert.deepEqual(areaGraph(document,'area').points,[])
 readGraph(writeGraph('building',document),'building')
})
test('area modes detach incompatible counts and preserve the exact drawn mask when switching',()=>{
 const node={areaMode:'RECTANGLE',dimensions:{length:'x',height:'z'}}
 assert.deepEqual(areaModePatch(node,'SQUARE',[]).dimensions,{length:'x'})
 assert.equal(areaModePatch(node,'DRAW',[]).dimensions,null)
 assert.deepEqual(areaModePatch({areaMode:'DRAW'},'DRAW',[{x:1,y:0,z:2}]),{areaMode:'DRAW',dimensions:null})
})
test('drawing is bounded, deduplicated and can erase the last cell; fast strokes fill intermediate cells',()=>{
 const cells=rasterLine({x:0,z:0},{x:5,z:3})
 assert.equal(cells.length,6)
 const points=paintArea([],cells,true)
 assert.equal(points.length,6)
 assert.deepEqual(paintArea(points,cells,true),points)
 assert.deepEqual(paintArea(points,cells,false),[])
 assert.deepEqual(paintArea([],[{x:128,z:0},{x:-129,z:0},{x:127,z:-128}],true),[{x:127,y:0,z:-128}])
 assert.throws(()=>paintArea([],Array.from({length:16385},(_,i)=>({x:i%256-128,z:Math.floor(i/256)-128})),true),/16384/)
 assert.deepEqual(fitArea([]),[-3,-3,12,12])
})
test('dynamic segment has only interior and retains dormant fixed-region assignments',()=>{
 const doc=base();doc.graph.schema=7
 doc.graph.nodes.push({...newNode(true,'SEGMENT','dynamic',0,0,true),dynamicAxes:['X','Y'],inputs:['roof_copycat'],regionCopies:{'region:x0_y0_zb':'wall_copycat'}})
 const entry=describeNode(true,doc,'dynamic')
 assert.deepEqual(entry.ports.map(p=>p.id),['in:0'])
 assert.deepEqual(entry.dynamicAxes,['X','Y'])
 const saved=writeGraph('building',doc)
 assert.equal(saved.graph.schema,7)
 assert.equal(saved.graph.nodes.find(n=>n.id==='dynamic').regionCopies['region:x0_y0_zb'],'wall_copycat')
 const plan=previewGraph(true,doc,library,'dynamic')
 assert.equal(plan.count,12)
})
test('adaptive roof exposes arrangement and all five typed segment slots from the start',()=>{
 const doc=base();doc.graph.schema=7;doc.graph.nodes.push(newNode(true,'ROOF','automatic',0,0,true))
 const ports=describeNode(true,doc,'automatic').ports
 assert.deepEqual(ports.map(p=>p.id),['layout','in:0','in:1','in:2','in:3','in:4'])
 assert.ok(ports.slice(1).every(p=>p.types.every(type=>['WALL','ROOF'].includes(type))))
 assert.equal(doc.graph.nodes.find(n=>n.id==='roof').adaptiveRoof,undefined)
})
