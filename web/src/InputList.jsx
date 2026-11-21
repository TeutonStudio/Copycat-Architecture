import { nodeChoiceMeta } from './choice-icons.js'
import { IconSelect } from './IconSelect.jsx'
import { names, portName } from './Inspector.jsx'
import { inputListEnabled, inputOrder } from './input-list.js'

export function InputList({entry,entries,limits,properties,onConnect,onOrder}) {
  if(!inputListEnabled(properties)) return null
  const {node,ports}=entry
  const count=node.inputs.length
  const list=ports.filter(port=>/^(?:in:)?\d+$/.test(port.id))
  return <section className="input-list" aria-label="Dynamische Eingänge">
    <header><h3>Eingänge</h3><button type="button" aria-label="Eingang hinzufügen" title={`Bis zu ${limits.max} Eingänge`} disabled={count>=limits.max} onClick={()=>onOrder(inputOrder(count,'add'))}>＋</button></header>
    <p className="hint">Kommutativ &amp; Assoziativ · {count} / {limits.max} Eingänge. Beim Verschieben wandern Verbindung und Eingangszuordnung mit.</p>
    <ol>{list.map((port,index)=><li key={port.id} className="input-list-row">
      <div className="input-list-order"><button type="button" aria-label={`Eingang ${index+1} nach oben`} disabled={index===0} onClick={()=>onOrder(inputOrder(count,'move',index,-1))}>↑</button><button type="button" aria-label={`Eingang ${index+1} nach unten`} disabled={index===count-1} onClick={()=>onOrder(inputOrder(count,'move',index,1))}>↓</button></div>
      <label><IconSelect domain="node" choiceMeta={value=>nodeChoiceMeta(entries,value)} aria-label={portName(port,node)} value={port.source || ''} onChange={event=>onConnect(node.id,port.id,event.target.value)}><option value="">Offen</option>{entries.filter(({node:source})=>source.id!==node.id && port.accepts.includes(source.kind)).map(({node:source})=><option key={source.id} value={source.id}>{source.name || `${names[source.kind] || source.kind} · ${source.id}`}</option>)}</IconSelect></label>
      <button type="button" className="input-list-remove" aria-label={`Eingang ${index+1} entfernen`} title="Eingang und Verbindung entfernen" disabled={count<=limits.min} onClick={()=>onOrder(inputOrder(count,'remove',index))}>×</button>
    </li>)}</ol>
  </section>
}
