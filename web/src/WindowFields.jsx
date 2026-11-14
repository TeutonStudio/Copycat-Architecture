import { useState } from 'react'

/** Wall coordinates come from the same native registry that validates compilation. */
export function WindowFields({ node, document, walls = [], change }) {
  const [selected, select] = useState(0)
  const items = node.windows || []
  const definitions = document.graph.nodes.filter(n => n.kind === 'WINDOW')
  const active = Math.min(selected, Math.max(0, items.length - 1))
  const patch = (index, next) => change(items.map((item, i) => i === index ? { ...item, ...next } : item))
  const def = item => definitions.find(n => n.id === item.source)
  const wall = item => walls.find(w => w.roomIndex === item.roomIndex && w.edge === item.edge)
  const all = walls.flatMap(w => w.cells)
  const lowX = Math.min(0, ...all.map(p => p.x)) - 2, lowZ = Math.min(0, ...all.map(p => p.z)) - 2
  const size = Math.max(6, Math.max(...all.map(p => p.x), 0) - lowX + 2, Math.max(...all.map(p => p.z), 0) - lowZ + 2)
  const place = (event, w) => {
    if (!items[active] || !w) return
    const svg = event.currentTarget.ownerSVGElement || event.currentTarget
    const cursor = svg.createSVGPoint(); cursor.x = event.clientX; cursor.y = event.clientY
    const local = cursor.matrixTransform(svg.getScreenCTM().inverse())
    const width = def(items[active])?.length || 1
    let best = 0, distance = Infinity
    w.cells.slice(0, Math.max(1, w.cells.length - width + 1)).forEach((p, i) => {
      const d = (p.x + .5 - local.x) ** 2 + (p.z + .5 - local.y) ** 2
      if (d < distance) { distance = d; best = i }
    })
    patch(active, { roomIndex: w.roomIndex, edge: w.edge, offset: best })
  }
  const number = (title, key, item, index, min, max) => <label>{title}<input aria-label={`${title} ${index + 1}`} type="number" min={min} max={max} step="1" value={item[key]} onChange={event => {
    const value = Number(event.target.value)
    if (event.target.value !== '' && Number.isInteger(value) && value >= min && value <= max) patch(index, { [key]: value })
  }} /></label>
  return <fieldset className="window-fields"><legend>Fenster am Stockwerk</legend>
    <p className="hint">Fensterdefinition verbinden, Zielwand anklicken und das Fenster im Grundriss ziehen. Ausrichtung und Durchbruch folgen der Wand. Änderungen mit „Übernehmen“ bestätigen.</p>
    <label>Aktives Fenster<select aria-label="Aktives Fenster" value={active} onChange={event => select(Number(event.target.value))}>
      {items.map((item, i) => <option key={item.id} value={i}>Fenster {i + 1} · {def(item)?.name || item.source || 'Quelle offen'}</option>)}
    </select></label>
    {all.length > 0 && <svg className="arrangement-grid window-plan" viewBox={`${lowX} ${lowZ} ${size} ${size}`} role="application" aria-label="Fenstergrundriss" tabIndex={0}
      onKeyDown={event => { event.stopPropagation(); const i = items[active]; if (!i || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return; event.preventDefault(); patch(active, { offset: Math.max(0, Math.min((wall(i)?.cells.length || 1) - (def(i)?.length || 1), i.offset + (event.key === 'ArrowRight' ? 1 : -1))) }) }}>
      {walls.map(w => <g key={`${w.roomIndex}:${w.edge}`} onPointerDown={event => place(event, w)}>
        {w.cells.map((p, i) => <rect key={i} x={p.x} y={p.z} width="1" height="1" fill="#526a78" stroke="#98aeb9" strokeWidth=".04"><title>{w.roomIndex + 1} · {w.edge}</title></rect>)}
      </g>)}
      {items.map((item, i) => {
        const w = wall(item), width = def(item)?.length || 1
        return <g key={item.id} onPointerDown={event => { select(i); event.currentTarget.setPointerCapture(event.pointerId) }}
          onPointerMove={event => { if (event.buttons === 1 && event.currentTarget.hasPointerCapture(event.pointerId) && i === active) place(event, w) }} onPointerUp={event => { if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId) }}>
          {w?.cells.slice(item.offset, item.offset + width).map((p, x) => <rect key={x} x={p.x + .1} y={p.z + .1} width=".8" height=".8" fill={i === active ? '#e4b967' : '#7ab997'}><title>Fenster {i + 1}</title></rect>)}
        </g>
      })}
    </svg>}
    {items.map((item, i) => <fieldset key={item.id}><legend>Fenster {i + 1}</legend>
      <label>Fensterdefinition<select aria-label={`Fensterdefinition ${i + 1}`} value={item.source} onChange={event => patch(i, { source: event.target.value })}>
        <option value="">Nicht verbunden</option>{definitions.map(n => <option key={n.id} value={n.id}>{n.name || n.id}</option>)}
      </select></label>
      <label>Zielwand<select aria-label={`Zielwand ${i + 1}`} value={`${item.roomIndex}:${item.edge}`} onChange={event => { const w = walls.find(w => `${w.roomIndex}:${w.edge}` === event.target.value); if (w) patch(i, { roomIndex: w.roomIndex, edge: w.edge, offset: 0 }) }}>
        <option value="">Zielwand wählen</option>{walls.map(w => <option key={`${w.roomIndex}:${w.edge}`} value={`${w.roomIndex}:${w.edge}`}>Raum {w.roomIndex + 1} · {w.edge} · {['Nord', 'Ost', 'Süd', 'West'][w.direction]} · {w.cells.length} Blöcke</option>)}
      </select></label>
      {number('Position entlang der Wand', 'offset', item, i, 0, 128)}
      {number('Einbauhöhe', 'sill', item, i, 0, 128)}
      {number('Tiefenversatz', 'depthOffset', item, i, -128, 128)}
      <label className="check"><input type="checkbox" aria-label={`Automatische Wandöffnung ${i + 1}`} checked={item.autoOpening} onChange={event => patch(i, { autoOpening: event.target.checked })} />Innenwand und Fassade automatisch öffnen</label>
      <button type="button" onClick={() => { change(items.filter((_, index) => index !== i)); select(0) }}>Fenster {i + 1} entfernen</button>
    </fieldset>)}
    <button type="button" disabled={items.length >= 64} onClick={() => { const w = walls[0]; change([...items, { id: crypto.randomUUID(), source: definitions[0]?.id || '', roomIndex: w?.roomIndex || 0, edge: w?.edge || '', offset: 0, sill: 1, depthOffset: 0, autoOpening: true }]); select(items.length) }}>Fensterinstanz hinzufügen</button>
  </fieldset>
}
