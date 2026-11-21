import { IconSelect } from './IconSelect.jsx'
import { IntegerField } from './IntegerField.jsx'
import { MirrorPlanePicker } from './MirrorPlanePicker.jsx'
import { model } from './model.js'
import { useEffect, useState } from 'react'
const labels = { ROTATE: 'Drehen', MIRROR: 'Spiegeln', POSITION: 'Bewegen' }
const point = (value, change, label, min = 0, max = 16) => {
  const coordinates=value || {x:0,y:0,z:0}
  return <fieldset className="coordinate-grid" style={{ '--coordinate-count': 3 }}><legend>{label}</legend>{['x','y','z'].map(key=><IntegerField key={key} label={key.toUpperCase()} ariaLabel={`${label} ${key.toUpperCase()}`} value={coordinates[key] ?? 0} min={min} max={max} onChange={n=>change({...coordinates,[key]:n})}/>)}</fieldset>
}
export function CopycatTransforms({ node, edit, primary = false }) {
  const [steps, setSteps] = useState(node.copycatTransforms || [])
  useEffect(() => setSteps(node.copycatTransforms || []), [node])
  const change = (index, patch) => setSteps(steps.map((step, i) => i === index ? { ...step, ...patch } : step))
  const move = (index, delta) => { const next = [...steps]; next.splice(index + delta, 0, next.splice(index, 1)[0]); setSteps(next) }
  return <details className="transform-list" open><summary>Copycat-Transformationen</summary>
    <p className="hint">Nach der Knotenauswertung · Reihenfolge über alle Abschnitte · hB = 1/16 Block</p>
    {primary && ['ROTATE','ROTATION','COPYCAT_ROTATE','POSITION','TRANSLATE','COPYCAT_POSITION'].includes(node.kind) && <fieldset><legend>Operator / Parameterwert</legend>
      {['ROTATE','ROTATION','COPYCAT_ROTATE'].includes(node.kind) && point(node.rotationPivot || { x:8,y:8,z:8 }, value => edit({ rotationPivot:value }), 'Drehpunkt (hB)', 0,16)}
      {['POSITION','TRANSLATE','COPYCAT_POSITION'].includes(node.kind) && point(node.translation, value => edit({ translation:value }), 'Position (hB)', 0,node.kind === 'POSITION' ? 2147483647 : 16)}
    </fieldset>}
    {!['ROTATION','MIRRORING','POSITION'].includes(node.kind) && <>
    {Object.entries(labels).map(([kind, label]) => <fieldset key={kind}><legend>{label}</legend>
      {steps.map((step, index) => step.kind === kind && <div className="transform-step" key={index}>
        <label className="check"><input type="checkbox" checked={step.enabled !== false} onChange={e => change(index, { enabled:e.target.checked })} />Schritt {index + 1}</label>
        {kind === 'ROTATE' && <><label>Achse<IconSelect domain="axis" value={step.axis || 'Y'} onChange={e => change(index,{axis:e.target.value})}>{['X','Y','Z'].map(axis => <option key={axis}>{axis}</option>)}</IconSelect></label>
          <label>Winkel<IconSelect domain="angle" value={step.quarterTurns ?? 1} onChange={e => change(index,{quarterTurns:Number(e.target.value)})}>{[0,1,2,3].map(q => <option key={q} value={q}>{q*90}°</option>)}</IconSelect></label>
          {point(step.pivot || {x:8,y:8,z:8},value => change(index,{pivot:value}),'Drehpunkt (hB)',0,16)}</>}
        {kind === 'MIRROR' && <MirrorPlanePicker value={step.plane || 'X'} onChange={plane=>change(index,{plane})}/>}
        {kind === 'POSITION' && point(step.offset,value => change(index,{offset:value}),'Versatz (hB)',0,16)}
        <button onClick={() => setSteps(steps.filter((_,i) => i !== index))}>Schritt {index + 1} entfernen</button>
      </div>)}
      <button disabled={steps.length >= 64} onClick={() => setSteps([...steps, { kind, enabled:true, axis:'Y', quarterTurns:1, pivot:{x:8,y:8,z:8}, plane:'X', offset:{x:0,y:0,z:0} }])}>+ {label}</button>
    </fieldset>)}
    <fieldset><legend>Gemeinsame Reihenfolge</legend>{steps.map((step,index) => <div className="transform-order" key={index}><span>{index+1}. {labels[step.kind]}{step.enabled === false ? ' (inaktiv)' : ''}</span><button aria-label={`Schritt ${index+1} nach oben`} disabled={!index} onClick={() => move(index,-1)}>↑</button><button aria-label={`Schritt ${index+1} nach unten`} disabled={index === steps.length-1} onClick={() => move(index,1)}>↓</button></div>)}</fieldset>
    <button className="primary" disabled={JSON.stringify(steps) === JSON.stringify(node.copycatTransforms || [])} onClick={() => edit({copycatTransforms:steps.length ? steps : null})}>Transformationen übernehmen</button>
    <button onClick={() => setSteps(node.copycatTransforms || [])}>Entwurf verwerfen</button>
    </>}
  </details>
}
