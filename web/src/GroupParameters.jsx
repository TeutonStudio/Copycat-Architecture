import {useState} from 'react'
import {cardTypeLabel} from './card-types.js'
export function GroupParameters({parameters=[],onOrder}) {
 const [dragged,setDragged]=useState(null)
 if(!parameters.length)return null
 const move=(id,to)=>{const order=parameters.map(p=>p.id),from=order.indexOf(id);if(to<0||to>=order.length||to===from)return;order.splice(to,0,order.splice(from,1)[0]);onOrder?.(order)}
 return <details className="node-parameters nodrag nopan" onPointerDown={e=>e.stopPropagation()}><summary>Gruppeneingänge · {parameters.length}</summary><p className="hint">Kreise ziehen oder mit ↑/↓ umsortieren.</p>{parameters.map((p,i)=><div className="port" key={p.id} onDragOver={e=>{if(dragged)e.preventDefault()}} onDrop={e=>{e.preventDefault();if(dragged)move(dragged,i);setDragged(null)}}><span>{p.name} · {cardTypeLabel(p.type)}</span><button disabled={!onOrder} type="button" className={`port-status port-sort-handle ${p.defaultSource?'connected':''}`} draggable={!!onOrder} onDragStart={()=>setDragged(p.id)} onDragEnd={()=>setDragged(null)} onKeyDown={e=>{if(['ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();e.stopPropagation();move(p.id,i+(e.key==='ArrowUp'?-1:1))}}} aria-label={`${p.name} in Gruppenschnittstelle umsortieren`}>{p.defaultSource?'●':'○'}</button></div>)}</details>
}
