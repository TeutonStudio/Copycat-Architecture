import { IconSelect } from './IconSelect.jsx'
import { useMemo, useRef, useState } from 'react'
import { Modal } from './Modal.jsx'
import { names } from './Inspector.jsx'
import { NodeAppearance } from './NodeAppearance.jsx'
import { nodeCategories, nodeCategoryColors } from './node-catalog.js'
import { pickerEntries, selectPickerEntries, groupPickerEntries } from './node-picker.js'
import { portStyle } from './port-styles.js'
import {configureNode} from './node-controls.js'
const labels = {...names, POSITION_X:'Position zerlegen', POSITION:'Position zusammenführen (hB)'}
const capabilityLabel='Kommutativ & Assoziativ'
const capabilityHint='Variable Eingangsanzahl; Eingänge lassen sich frei umsortieren und gruppieren.'

export function NodeDialog({mode='building',kinds,count,maxNodes,onAdd,onPlace,onClose,properties={}}) {
  const [query,setQuery]=useState(''),[category,setCategory]=useState('all'),[output,setOutput]=useState('all'),[capability,setCapability]=useState('all')
  const [sort,setSort]=useState('name'),[direction,setDirection]=useState('asc'),[group,setGroup]=useState('none'),[selected,setSelected]=useState(null)
  const [operations,setOperations]=useState({}),[drafts,setDrafts]=useState({})
  const [drag,setDrag]=useState(null),pointer=useRef(null)
  const entries=useMemo(()=>pickerEntries(kinds,mode,labels,properties,operations,drafts),[kinds,mode,properties,operations,drafts])
  const filtered=selectPickerEntries(entries,{query,category,output,capability,sort,direction})
  const grouped=groupPickerEntries(filtered,group)
  const selectedEntry=filtered.find(entry=>entry.kind===selected)
  const outputTypes=[...new Set(entries.map(entry=>entry.output))].sort((a,b)=>portStyle(a).name.localeCompare(portStyle(b).name,'de'))
  const categoryLabel=id=>nodeCategories.find(([key])=>key===id)?.[1] || id
  const groupLabel=key=>group==='category' ? categoryLabel(key) : group==='output' ? `Ausgang: ${portStyle(key).name}` : group==='property' ? `${capabilityLabel}: ${key==='unknown'?'Unbekannt':key==='yes'?'Ja':'Nein'}` : 'Alle Knoten'
  const startPointer=(event,kind)=>{
    if(!onPlace || count>=maxNodes || event.button!==0 || !event.target.closest('.node-catalog-select')) return
    pointer.current={id:event.pointerId,kind,x:event.clientX,y:event.clientY,moving:false}
  }
  const movePointer=event=>{
    const origin=pointer.current
    if(!origin || origin.id!==event.pointerId) return
    if(!origin.moving && Math.hypot(event.clientX-origin.x,event.clientY-origin.y)<6) return
    event.currentTarget.setPointerCapture(event.pointerId)
    origin.moving=true;setSelected(origin.kind);setDrag({kind:origin.kind,x:event.clientX,y:event.clientY})
  }
  const endPointer=event=>{
    const origin=pointer.current;pointer.current=null
    if(!origin?.moving) return
    event.preventDefault();setDrag(null)
    onPlace(entries.find(e=>e.kind===origin.kind).preview.node,{x:event.clientX,y:event.clientY})
  }
  return <Modal title="Knoten hinzufügen" className={`node-dialog${drag?' is-dragging':''}`} onClose={onClose}>
    <div className="node-dialog-content">
      <p className="hint">Operator wählen, Anschlüsse und Erklärung prüfen. Ein Klick wählt nur die Vorschau. Ziehe den Knoten an die gewünschte Stelle auf der Karte.</p>
      <div className="node-picker-toolbar">
        <label>Suchen<input type="search" data-autofocus value={query} placeholder="Name, Operator oder Aufgabe …" onChange={event=>setQuery(event.target.value)}/></label>
        <label>Sortieren nach<IconSelect domain="sort" aria-label="Sortieren nach" value={sort} onChange={event=>setSort(event.target.value)}><option value="name">Name</option><option value="category">Kategorie</option><option value="output">Ausgang</option><option value="inputs">Eingangsanzahl</option></IconSelect></label>
        <label>Reihenfolge<IconSelect domain="direction" aria-label="Reihenfolge" value={direction} onChange={event=>setDirection(event.target.value)}><option value="asc">Aufsteigend</option><option value="desc">Absteigend</option></IconSelect></label>
        <label>Gruppieren nach<IconSelect domain="grouping" aria-label="Gruppieren nach" value={group} onChange={event=>setGroup(event.target.value)}><option value="none">Keine Gruppierung</option><option value="category">Kategorie</option><option value="output">Ausgang</option><option value="property">{capabilityLabel}</option></IconSelect></label>
      </div>
      <details className="node-picker-filters"><summary>Filtern</summary><div>
        <label>Kategorie<IconSelect domain="category" aria-label="Kategorie" value={category} onChange={event=>setCategory(event.target.value)}><option value="all">Alle Kategorien</option>{nodeCategories.filter(([id])=>entries.some(entry=>entry.category===id)).map(([id,label])=><option key={id} value={id}>{label}</option>)}</IconSelect></label>
        <label>Ausgang<IconSelect domain="type" aria-label="Ausgang" value={output} onChange={event=>setOutput(event.target.value)}><option value="all">Alle Ausgänge</option>{outputTypes.map(type=><option key={type} value={type}>{portStyle(type).symbol} {portStyle(type).name}</option>)}</IconSelect></label>
        <label title={capabilityHint}>{capabilityLabel}<IconSelect domain="capability" aria-label={capabilityLabel} value={capability} onChange={event=>setCapability(event.target.value)}><option value="all">Alle Knoten</option><option value="yes">Ja · variable Eingänge, frei umsortierbar</option><option value="no">Nein</option></IconSelect></label>
      </div><p className="hint">{capabilityLabel}: {capabilityHint}</p></details>
      <p className="hint" role="status">{filtered.length} passende Knoten · {count} / {maxNodes} auf der Karte</p>
      {grouped.map(([key,list])=><details className="node-picker-group" key={`${group}:${key}`} open><summary>{group!=='none' && group==='category' && <span aria-hidden="true" className="category-color" style={{backgroundColor:nodeCategoryColors[key]}}/>}{groupLabel(key)} · {list.length}</summary>
        <div className="node-catalog">{list.map(entry=><article className={`node-catalog-card${selected===entry.kind?' is-selected':''}`} key={entry.kind} onClick={()=>setSelected(entry.kind)} onPointerDown={event=>startPointer(event,entry.kind)} onPointerMove={movePointer} onPointerUp={endPointer} onPointerCancel={()=>{pointer.current=null;setDrag(null)}}>
          <div role="button" tabIndex={0} className="node-catalog-select" aria-label={`${entry.label} auswählen`} aria-pressed={selected===entry.kind} onKeyDown={event=>{if(event.target===event.currentTarget && ['Enter',' '].includes(event.key)){event.preventDefault();setSelected(entry.kind)}}}><NodeAppearance preview data={{...entry.preview,onInputOrder:order=>setDrafts(current=>({...current,[entry.kind]:configureNode(entry.preview.node,'$inputOrder',order)})),onControl:(path,value)=>setDrafts(current=>({...current,[entry.kind]:configureNode(entry.preview.node,path,value)})),onOperation:entry.family?kind=>{setOperations(current=>({...current,[entry.family]:kind}));setSelected(kind)}:undefined}}/></div>
          <p>{entry.description}</p>
          <small>{portStyle(entry.output).symbol} Ausgang: {portStyle(entry.output).name} · {entry.inputs} {entry.inputs===1?'Eingang':'Eingänge'}</small>
          <dl className="node-algebra" title={capabilityHint}><div><dt>{capabilityLabel}:</dt><dd>{entry.available ? entry.variable?'Ja':'Nein':'—'}</dd></div></dl>
        </article>)}</div>
      </details>)}
      {!filtered.length && <p>Keine passenden Knoten. Ändere die Suche oder die Filter.</p>}
    </div>
    <footer><button type="button" onClick={onClose}>Schließen</button><button type="button" disabled={!selectedEntry || count>=maxNodes} onClick={()=>onAdd(selectedEntry.preview.node)}>Ausgewählten Knoten in Kartenmitte einfügen</button></footer>
    {drag && <div aria-hidden="true" className="node-drag-preview" style={{left:drag.x+12,top:drag.y+12}}><NodeAppearance preview data={entries.find(entry=>entry.kind===drag.kind).preview}/></div>}
  </Modal>
}
