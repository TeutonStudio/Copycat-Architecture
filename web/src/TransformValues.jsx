import { IconSelect } from './IconSelect.jsx'
import { MirrorPlanePicker } from './MirrorPlanePicker.jsx'
import { model } from './model.js'
export function TransformValues({node,edit}) {
  const rotation=node.kind==='ROTATION'
  const field=rotation?'rotations':'mirrors'
  const steps=node[field] ?? []
  const label=rotation?'Rotation':'Spiegelung'
  const change=(index,patch)=>edit({[field]:steps.map((step,i)=>i===index?{...step,...patch}:step)})
  const move=(index,delta)=>{const next=[...steps];[next[index],next[index+delta]]=[next[index+delta],next[index]];edit({[field]:next})}
  return <fieldset className="transform-values"><legend>{rotation?'Rotationen':'Spiegelungen'} · Reihenfolge</legend>
    <p className="hint">Die Schritte werden von oben nach unten ausgeführt. Eine leere Liste lässt die Geometrie unverändert.</p>
    {steps.map((step,index)=><div className="transform-value-row" key={index}>
      {rotation?<label>{index+1}. Achse<IconSelect domain="axis" aria-label={`${label} ${index+1} Achse`} value={step.axis} onChange={e=>change(index,{axis:e.target.value})}>{['X','Y','Z'].map(axis=><option key={axis}>{axis}</option>)}</IconSelect></label>:<MirrorPlanePicker label={`${label} ${index+1}`} value={step.plane} onChange={plane=>change(index,{plane})}/>}
      {rotation&&<label>Winkel<IconSelect domain="angle" aria-label={`Rotation ${index+1} Winkel`} value={step.quarterTurns} onChange={e=>change(index,{quarterTurns:Number(e.target.value)})}>{[0,1,2,3].map(n=><option key={n} value={n}>{n*90}°</option>)}</IconSelect></label>}
      <button type="button" aria-label={`${label} ${index+1} nach oben`} disabled={index===0} onClick={()=>move(index,-1)}>↑</button>
      <button type="button" aria-label={`${label} ${index+1} nach unten`} disabled={index===steps.length-1} onClick={()=>move(index,1)}>↓</button>
      <button type="button" aria-label={`${label} ${index+1} entfernen`} onClick={()=>edit({[field]:steps.filter((_,i)=>i!==index)})}>×</button>
    </div>)}
    <button type="button" disabled={steps.length>=64} onClick={()=>edit({[field]:[...steps,rotation?{axis:'Y',quarterTurns:1}:{plane:'X'}]})}>+ {label}</button>
  </fieldset>
}
