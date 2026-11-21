import test from 'node:test'
import assert from 'node:assert/strict'
import {areaTools,paintArea,rasterRectangle} from '../src/area-grid.js'
const bounds=cells=>({width:Math.max(...cells.map(p=>p.x))-Math.min(...cells.map(p=>p.x))+1,depth:Math.max(...cells.map(p=>p.z))-Math.min(...cells.map(p=>p.z))+1})
test('area drawing offers painting and actual rectangle and square drag tools',()=>{
 assert.deepEqual(areaTools.map(([,label])=>label),['Malen','Rechteck ziehen','Quadrat ziehen'])
 for(const dx of [-4,4]) for(const dz of [-2,2]) {
  const from={x:-3,z:2},to={x:from.x+dx,z:from.z+dz}
  const rectangle=rasterRectangle(from,to),square=rasterRectangle(from,to,true)
  assert.equal(rectangle.length,15);assert.deepEqual(bounds(rectangle),{width:5,depth:3})
  assert.equal(square.length,25);assert.deepEqual(bounds(square),{width:5,depth:5})
  assert.ok(rectangle.some(p=>p.x===to.x && p.z===to.z))
 }
})
test('rectangle previews use the original mask so shrinking and erasing do not leave a trail',()=>{
 const base=[{x:-1,y:0,z:-1}],start={x:0,z:0}
 const large=paintArea(base,rasterRectangle(start,{x:4,z:3}),true)
 const small=paintArea(base,rasterRectangle(start,{x:1,z:1}),true)
 assert.equal(large.length,21);assert.equal(small.length,5)
 assert.ok(!small.some(p=>p.x===4));assert.deepEqual(base,[{x:-1,y:0,z:-1}])
 assert.deepEqual(paintArea(small,rasterRectangle(start,{x:1,z:1}),false),base)
 assert.equal(rasterRectangle(start,start,true).length,1)
})
test('dragged shapes retain square proportions at boundaries and respect the native cell limit',()=>{
 for(const from of [{x:126,z:126},{x:-127,z:-127},{x:127,z:-128}]) for(const offset of [{x:5,z:5},{x:-5,z:-5},{x:3,z:-2}]) {
  const to={x:from.x+offset.x,z:from.z+offset.z}
  const cells=rasterRectangle(from,to,true),size=bounds(cells)
  assert.equal(size.width,size.depth)
  assert.ok(cells.every(p=>p.x>=-128 && p.x<=127 && p.z>=-128 && p.z<=127))
 }
 assert.equal(rasterRectangle({x:0,z:0},{x:127,z:127},true).length,16384)
 assert.throws(()=>rasterRectangle({x:-128,z:-128},{x:127,z:127}),/16384/)
 assert.throws(()=>rasterRectangle({x:-128,z:-128},{x:127,z:127},true),/16384/)
 assert.throws(()=>rasterRectangle({x:.5,z:0},{x:1,z:1}),/ganzzahlig/)
})
