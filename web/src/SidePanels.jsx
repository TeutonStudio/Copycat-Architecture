import { Children, Fragment, useRef, useState } from 'react'

export function SidePanels({children,collapsed,showArrangement}) {
  const keys=['preview',...(showArrangement?['arrangement']:[]),'inspector']
  const labels={preview:'3D-Vorschau',arrangement:'2D-Anordnung',inspector:'Inspektor'}
  const nextOpen=index=>keys.slice(index+1).find(key=>!collapsed[key])
  const panels=Children.toArray(children),drag=useRef(null),root=useRef(null)
  const [weights,setWeights]=useState({preview:1,arrangement:1,inspector:1})
  const rows=[]
  keys.forEach((key,index)=>{rows.push(collapsed[key]?'auto':`minmax(0,${weights[key]}fr)`);if(index<keys.length-1 && !collapsed[key] && nextOpen(index)) rows.push('8px')})
  const resize=(first,second,delta,initial=weights)=>setWeights(previous=>{
    const total=initial[first]+initial[second],value=Math.max(total*.12,Math.min(total*.88,initial[first]+delta))
    return {...previous,[first]:value,[second]:total-value}
  })
  return <div ref={root} className="side resizable-side" style={{gridTemplateRows:rows.join(' '),alignContent:keys.every(key=>collapsed[key])?'start':'stretch'}}>{keys.map((key,index)=><Fragment key={key}>{panels[index]}
    {index<keys.length-1 && !collapsed[key] && nextOpen(index) && <div className="split-handle split-y" role="separator" tabIndex={0} aria-orientation="horizontal" aria-label={`Größe von ${labels[key]} und ${labels[nextOpen(index)]}`} aria-valuemin={12} aria-valuemax={88} aria-valuenow={Math.round(100*weights[key]/(weights[key]+weights[nextOpen(index)]))}
      onPointerDown={event=>{event.preventDefault();drag.current={y:event.clientY,weights:{...weights},height:root.current.clientHeight};event.currentTarget.setPointerCapture(event.pointerId)}}
      onPointerMove={event=>{if(drag.current)resize(key,nextOpen(index),(event.clientY-drag.current.y)/Math.max(1,drag.current.height)*keys.filter(k=>!collapsed[k]).reduce((sum,k)=>sum+drag.current.weights[k],0),drag.current.weights)}}
      onPointerUp={event=>{drag.current=null;event.currentTarget.releasePointerCapture(event.pointerId)}} onPointerCancel={()=>{drag.current=null}} onLostPointerCapture={()=>{drag.current=null}}
      onDoubleClick={()=>setWeights(previous=>({...previous,[key]:1,[nextOpen(index)]:1}))}
      onKeyDown={event=>{if(['ArrowUp','ArrowDown'].includes(event.key)){event.preventDefault();event.stopPropagation();resize(key,nextOpen(index),(event.key==='ArrowDown'?1:-1)*.08)}}}><span/></div>}
  </Fragment>)}</div>
}
