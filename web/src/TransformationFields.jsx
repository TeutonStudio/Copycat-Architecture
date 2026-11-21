import { IconSelect } from './IconSelect.jsx'
import {axisLabel} from './axis-labels.js'
import { MirrorPlanePicker } from './MirrorPlanePicker.jsx'
import { IntegerField } from './IntegerField.jsx'
export const transformationOrders=['SRP','SPR','RSP','RPS','PRS','PSR']
const names={S:'Spiegeln',R:'Rotieren',P:'Positionieren'}
export function TransformationFields({node,edit,mode}) {
  const order=node.transformationOrder || 'SRP'
  const linked=letter=>Boolean(mode==='form'?node.inputs?.['SRP'.indexOf(letter)]:node[{S:'mirroring',R:'rotation',P:'positionValue'}[letter]])
  const point=node.translation || {x:0,y:0,z:0}
  return <section className="transformation-fields">
    <h3>Transformation</h3>

    <p className="hint">Verbundene Werte ersetzen die jeweilige Einstellung. Ohne Änderung ist dies die Identität.</p>
    {[...order].map((letter,index)=><fieldset key={letter} disabled={linked(letter)}><legend>{index+1} · {names[letter]}{letter==='P'?' (hB)':''}{linked(letter)?' · verbunden':''}</legend>
      {letter==='S' && <><label className="check"><input type="checkbox" checked={Boolean(node.mirror)} onChange={event=>edit({mirror:event.target.checked?{plane:'X'}:null})}/>Spiegelung aktiv</label>{node.mirror && <MirrorPlanePicker value={node.mirror.plane} onChange={plane=>edit({mirror:{plane}})}/>}</>}
      {letter==='R' && <><label>Achse<IconSelect domain="axis" value={node.rotationAxis==='NORMAL'?'Y':node.rotationAxis} onChange={event=>edit({rotationAxis:event.target.value})}>{['X','Y','Z'].map(axis=><option key={axis} value={axis}>{axisLabel(axis)}</option>)}</IconSelect></label><label>Winkel<IconSelect domain="angle" value={node.quarterTurns} onChange={event=>edit({quarterTurns:Number(event.target.value)})}>{[0,1,2,3].map(q=><option key={q} value={q}>{q*90}°</option>)}</IconSelect></label></>}
      {letter==='P' && <><p className="hint">16 hB = 1 Block. Copycats bleiben innerhalb ihres Blocks; Segmente können Blockgrenzen überschreiten.</p>{['x','y','z'].map(axis=><IntegerField key={axis} label={axisLabel(axis.toUpperCase())} ariaLabel={`Transformation Position ${axis.toUpperCase()}`} value={point[axis]} min={-2048} max={2048} onChange={value=>edit({translation:{...point,[axis]:value}})}/>)}</>}
    </fieldset>)}
  </section>
}
