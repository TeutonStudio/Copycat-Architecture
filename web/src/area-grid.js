export const areaModes = [['SQUARE', 'Quadrat'], ['RECTANGLE', 'Rechteck'], ['DRAW', 'Anklicken']]
export const areaKey = (x,z) => `${x},${z}`
export function areaModePatch(node, mode, points) {
  if (!areaModes.some(([value])=>value===mode)) throw new Error('Unbekannter Flächenmodus')
  return { areaMode:mode, dimensions: mode==='DRAW' ? null : Object.fromEntries(Object.entries(node.dimensions || {}).filter(([key])=>key==='length' || mode==='RECTANGLE' && key==='height')),
    ...(mode==='DRAW' && node.areaMode!=='DRAW' ? {coordinates:points} : {}) }
}
export function paintArea(points, cells, selected) {
  const occupied=new Map(points.map(p=>[areaKey(p.x,p.z),{x:p.x,y:0,z:p.z}]))
  for(const {x,z} of cells) {
    if(!Number.isInteger(x) || !Number.isInteger(z) || x < -128 || x > 127 || z < -128 || z > 127) continue
    if(selected) occupied.set(areaKey(x,z),{x,y:0,z});else occupied.delete(areaKey(x,z))
  }
  if(occupied.size>16384) throw new Error('Eine Fläche darf höchstens 16384 Zellen enthalten.')
  return [...occupied.values()].sort((a,b)=>a.z-b.z || a.x-b.x)
}
export function fitArea(points) {
  if(!points.length) return [-3,-3,12,12]
  const minX=Math.min(...points.map(p=>p.x)),minZ=Math.min(...points.map(p=>p.z))
  const width=Math.max(...points.map(p=>p.x))-minX+1,depth=Math.max(...points.map(p=>p.z))-minZ+1
  const size=Math.max(8,width+4,depth+4)
  return [minX-(size-width)/2,minZ-(size-depth)/2,size,size]
}
export function rasterLine(from,to) {
  const steps=Math.max(Math.abs(to.x-from.x),Math.abs(to.z-from.z))
  return Array.from({length:steps+1},(_,i)=>({x:Math.round(from.x+(to.x-from.x)*i/(steps || 1)),z:Math.round(from.z+(to.z-from.z)*i/(steps || 1))}))
}


export const areaTools=[['PAINT','Malen'],['RECTANGLE','Rechteck ziehen'],['SQUARE','Quadrat ziehen']]
// Include both endpoints and retain a square in every drag direction, including
// at the coordinate boundary. No partial shape is returned past the cell limit.
export function rasterRectangle(from,to,square=false) {
  if(![from.x,from.z,to.x,to.z].every(Number.isInteger)) throw new Error('Rasterpunkte müssen ganzzahlig sein.')
  const clamp=value=>Math.max(-128,Math.min(127,value))
  const start={x:clamp(from.x),z:clamp(from.z)},end={x:clamp(to.x),z:clamp(to.z)}
  if(square) {
    const dx=Math.sign(end.x-start.x) || 1,dz=Math.sign(end.z-start.z) || 1
    const side=Math.min(Math.max(Math.abs(end.x-start.x),Math.abs(end.z-start.z))+1,dx>0?128-start.x:start.x+129,dz>0?128-start.z:start.z+129)
    end.x=start.x+dx*(side-1);end.z=start.z+dz*(side-1)
  }
  const minX=Math.min(start.x,end.x),minZ=Math.min(start.z,end.z),width=Math.abs(end.x-start.x)+1,depth=Math.abs(end.z-start.z)+1
  if(width*depth>16384)throw new Error('Eine Fläche darf höchstens 16384 Zellen enthalten.')
  return Array.from({length:width*depth},(_,i)=>({x:minX+i%width,z:minZ+Math.floor(i/width)}))
}
