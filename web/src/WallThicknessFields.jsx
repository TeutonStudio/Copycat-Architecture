import { IconSelect } from './IconSelect.jsx'
import { useState } from 'react'
export function WallThicknessFields({ path, value, change, context }) {
  const [section,setSection] = useState('')
  const [invalid,setInvalid] = useState('')
  const numeric = (key,title) => {
    const raw = key == null ? value : value?.[key]
    const update = next => key == null ? change(next) : change(Object.keys({...value,[key]:next}).reduce((out,k) => { const v=k===key?next:value[k];if(v!=null)out[k]=v;return out },{}))
    return <div key={key || 'facade'}><label className="check"><input type="checkbox" checked={raw != null} onChange={event => update(event.target.checked ? 8 : null)} />Eigene Stärke: {title}</label>
      {raw != null && <label>{title} · Blöcke<input aria-label={`${title} · Blöcke`} type="number" min="0" max="128" step="0.0625" value={raw/16} onChange={event => {
        const n=event.target.valueAsNumber*16
        if(Number.isInteger(n)&&n>=0&&n<=2048){update(n);setInvalid('')}else setInvalid('0–128 Blöcke in Schritten von 1/16 eingeben.')
      }} /></label>}</div>
  }
  if(path==='facade.thickness') return <fieldset><legend>Fassadenstärke von außen</legend>{numeric(null,'Fassade von außen')}<p className="hint">Nur außen; bei Überlappung bleibt die Raumseite sichtbar. Die Quelle begrenzt die Tiefe.</p>{invalid&&<p role="alert">{invalid}</p>}</fieldset>
  return <fieldset><legend>Wandstärke von innen</legend>
    {[['north','Norden'],['east','Osten'],['south','Süden'],['west','Westen']].map(([key,title])=>numeric(key,title))}
    <label>Einzelne Wand / Grenzabschnitt<IconSelect domain="wallSection" aria-label="Einzelne Wand / Grenzabschnitt" value={section} onChange={event=>setSection(event.target.value)}><option value="">Wand auswählen</option>{(context.wallSections || []).map(item=><option key={item.key} value={item.key}>{item.label}</option>)}</IconSelect></label>
    {/^(wall|boundary):.+$/.test(section)&&numeric(section,'Einzelne Wand')}
    {Object.keys(value || {}).filter(key=>key.startsWith('wall:')||key.startsWith('boundary:')).filter(key=>key!==section).map(key=>numeric(key,key))}
    <p className="hint">Einzelne Wände überschreiben ihre Himmelsrichtung. Null entfernt die Raumschicht. Die Quelle begrenzt die Tiefe; Profile werden beschnitten.</p>
    {invalid&&<p role="alert">{invalid}</p>}
  </fieldset>
}
