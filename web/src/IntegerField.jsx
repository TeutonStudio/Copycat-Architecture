import { useEffect, useState } from 'react'

export function IntegerField({label, value = 0, onChange, min = 0, max = 2147483647, className = '', ariaLabel = label, disabled = false}) {
  const [draft,setDraft]=useState(String(value ?? 0))
  useEffect(()=>setDraft(String(value ?? 0)),[value])
  const apply=text=>{
    if(!/^[-+]?\d+$/.test(text.trim())) {setDraft(String(value ?? 0));return}
    const next=Number(text)
    if(!Number.isSafeInteger(next) || next<min || next>max) {setDraft(String(value ?? 0));return}
    if(next!==value) onChange(next)
  }
  const step=delta=>{const next=Math.max(min,Math.min(max,(Number.isSafeInteger(value)?value:0)+delta));setDraft(String(next));onChange(next)}
  return <label className={`integer-field ${className}`}><span className="integer-label">{label}</span><input disabled={disabled} aria-label={ariaLabel} type="text" inputMode="numeric" value={draft} onChange={e=>{const text=e.target.value;setDraft(text);if(/^[-+]?\d+$/.test(text)){const next=Number(text);if(Number.isSafeInteger(next) && next>=min && next<=max) onChange(next)}}} onBlur={()=>apply(draft)} onKeyDown={e=>{if(e.key==='Enter') e.currentTarget.blur();if(e.key==='Escape') setDraft(String(value ?? 0));if(e.key==='ArrowUp' || e.key==='ArrowDown'){e.preventDefault();step(e.key==='ArrowUp'?1:-1)}}}/><span className="integer-buttons"><button type="button" aria-label={`${ariaLabel} erhöhen`} disabled={disabled || value>=max} onClick={()=>step(1)}>▴</button><button type="button" aria-label={`${ariaLabel} verringern`} disabled={disabled || value<=min} onClick={()=>step(-1)}>▾</button></span></label>
}
