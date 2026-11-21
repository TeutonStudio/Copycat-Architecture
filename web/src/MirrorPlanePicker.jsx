import { mirrorNormals,mirrorPolygon,projectMirrorPoint } from './mirror-planes.js'
const planeLabel=plane=>mirrorNormals[plane].flatMap((sign,index)=>sign?[`${sign<0?'−':index && mirrorNormals[plane].slice(0,index).some(Boolean)?'+':''}${'XYZ'[index]}`]:[]).join('')
export function MirrorPlanePicker({value='X',onChange,label='Spiegelebene',planes=Object.keys(mirrorNormals)}) {
  const vertices=Array.from({length:8},(_,i)=>[i&1?1:-1,i&2?1:-1,i&4?1:-1]), edges=vertices.flatMap((p,i)=>vertices.flatMap((q,j)=>j>i && p.filter((v,k)=>v!==q[k]).length===1?[[p,q]]:[]))
  const navigate=(event,index)=>{
    const buttons=[...event.currentTarget.parentElement.querySelectorAll('[role="radio"]')]
    const columns=buttons.filter(button=>Math.abs(button.getBoundingClientRect().top-buttons[0].getBoundingClientRect().top)<1).length
    const delta={ArrowLeft:-1,ArrowRight:1,ArrowUp:-columns,ArrowDown:columns}[event.key]
    if(delta===undefined && !['Home','End'].includes(event.key)) return
    event.preventDefault()
    const target=event.key==='Home'?0:event.key==='End'?planes.length-1:(index+delta+planes.length)%planes.length
    buttons[target].focus();onChange(planes[target])
  }
  return <fieldset className="mirror-picker"><legend>{label}</legend><div className="mirror-choices" role="radiogroup" aria-label={label}>{planes.map((plane,index)=><button key={plane} type="button" role="radio" aria-checked={value===plane} tabIndex={value===plane || !planes.includes(value) && index===0?0:-1} aria-label={`${label} ${plane}`} title={`${planeLabel(plane)} · Normale (${mirrorNormals[plane].join(', ')})`} onClick={()=>onChange(plane)} onKeyDown={event=>navigate(event,index)}><svg viewBox="0 0 64 60" aria-hidden="true">{edges.map(([p,q],i)=><path key={i} d={`M${projectMirrorPoint(p).join(',')}L${projectMirrorPoint(q).join(',')}`} fill="none" stroke="#8b9298" strokeWidth="1"/>)}<polygon points={mirrorPolygon(plane).map(p=>p.join(',')).join(' ')} fill="#b19ce888" stroke="#dfceff" strokeWidth="1.5"/></svg><span aria-hidden="true">{planeLabel(plane)}</span></button>)}</div></fieldset>
}
