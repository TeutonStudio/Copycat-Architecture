import {useState} from 'react'
import {graphParameters,orderGraphParameters} from './generated/engine.mjs'
import {cardTypeLabel} from './card-types.js'
export function GroupParameters({document,edit,disabled=false}) {
 const parameters=graphParameters(document),[dragged,setDragged]=useState(null)
 if(!parameters.length)return null
 const move=(id,to)=>{const order=parameters.map(p=>p.id),from=order.indexOf(id);if(to<0||to>=order.length||to===from)return;order.splice(to,0,order.splice(from,1)[0]);edit({...document,graph:orderGraphParameters(document,order)})}
 return <details open><summary>Gruppeneingänge · {parameters.length}</summary><p className="hint">Kreise ziehen oder mit ↑/↓ umsortieren. Referenzen behalten die Anschlusskennung.</p>{parameters.map((p,i)=><div className="port" key={p.id} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();if(dragged)move(dragged,i);setDragged(null)}}><span>{p.name} · {cardTypeLabel(p.type)}</span><button disabled={disabled} type="button" className="port-status port-sort-handle" draggable onDragStart={()=>setDragged(p.id)} onDragEnd={()=>setDragged(null)} onKeyDown={e=>{if(['ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();move(p.id,i+(e.key==='ArrowUp'?-1:1))}}} aria-label={`${p.name} in Gruppenschnittstelle umsortieren`}>○</button></div>)}</details>
}
