export const mirrorNormals={X:[1,0,0],Y:[0,1,0],Z:[0,0,1],XY:[1,1,0],XY_NEGATIVE:[1,-1,0],XZ:[1,0,1],XZ_NEGATIVE:[1,0,-1],YZ:[0,1,1],YZ_NEGATIVE:[0,1,-1],BODY_PPP:[1,1,1],BODY_PPN:[1,1,-1],BODY_PNP:[1,-1,1],BODY_PNN:[1,-1,-1]}
export const projectMirrorPoint=([x,y,z])=>[32+x*16-z*12,30+x*4+z*5-y*17]
export function mirrorPolygon(plane) {
  const normal=mirrorNormals[plane] || mirrorNormals.X
  const dot=p=>p.reduce((s,v,i)=>s+v*normal[i],0), points=[]
  const put=p=>{if(!points.some(q=>p.every((v,i)=>Math.abs(v-q[i])<1e-8))) points.push(p)}
  for(let axis=0;axis<3;axis++) for(const a of [-1,1]) for(const b of [-1,1]) {
    const others=[0,1,2].filter(i=>i!==axis), p=[0,0,0];p[others[0]]=a;p[others[1]]=b;p[axis]=-1
    const q=[...p];q[axis]=1;const d=dot(p), e=dot(q)
    if(d===0) put(p);if(e===0) put(q)
    if(d*e<0) put(p.map((v,i)=>v+(q[i]-v)*d/(d-e)))
  }
  const projected=points.map(projectMirrorPoint), center=projected.reduce((a,p)=>[a[0]+p[0]/projected.length,a[1]+p[1]/projected.length],[0,0])
  return projected.sort((a,b)=>Math.atan2(a[1]-center[1],a[0]-center[0])-Math.atan2(b[1]-center[1],b[0]-center[0]))
}
