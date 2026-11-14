import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { arrangementGraph, previewGraph } from './generated/engine.mjs'
import { arrangementPath, fitArrangement, moveArrangement } from './arrangement.js'

const colors = ['#55b8d0', '#d69b62', '#90be78', '#ba8dcc', '#da7980', '#799ee0']

export function ArrangementGrid({ node, document, library, edit }) {
  const copycats = node.kind === 'SEGMENT'
  const pattern = node.kind === 'PATTERN'
  const segments = copycats || pattern || node.kind === 'SEGMENT_UNION'
  const [plane, setPlane] = useState(segments ? 'Z' : 'Y')
  const [patch, setPatch] = useState(null)
  const [selected, setSelected] = useState(0)
  const [viewport, setViewport] = useState(null)
  const [error, setError] = useState('')
  const svg = useRef(null)
  const drag = useRef(null)
  const patternId = useId().replaceAll(':', '')
  useEffect(() => { setPatch(null); setError(''); drag.current = null }, [document, library])
  const nextDocument = changes => ({ ...document, graph: { ...document.graph,
    nodes: document.graph.nodes.map(item => item.id === node.id ? { ...item, ...changes } : item) } })
  const read = changes => {
    try { return { model: arrangementGraph(nextDocument(changes), library, node.id, plane) } }
    catch (cause) { return { error: cause.message || String(cause) } }
  }
  const base = useMemo(() => read({}), [document, library, node.id, plane])
  const current = useMemo(() => patch ? read(patch) : base, [base, patch])
  const items = current.model?.items ?? []
  const bounds = viewport ?? fitArrangement(base.model?.items ?? [])
  const active = Math.min(selected, Math.max(0, items.length - 1))
  const transforms = current.model?.transforms ?? []
  const offsets = patch?.offsets ?? (copycats ? (base.model?.transforms ?? []).map(t => t.offset) : node.offsets ?? [])
  const offset = pattern ? transforms[active]?.offset : offsets[active] ?? { x: 0, y: 0, z: 0 }
  const stage = (index, value, rotation = {}) => {
    if (pattern) setPatch({ patternAxis: 'transform', transforms: transforms.map((item, i) =>
      i === index ? { ...item, ...rotation, offset: value } : item) })
    else setPatch({ ...(copycats ? { copycatArrangement: true } : {}), offsets: node.inputs.map((_, i) => i === index ? value : offsets[i] ?? { x: 0, y: 0, z: 0 }) })
    setError('')
  }
  const point = event => {
    const p = svg.current.createSVGPoint()
    p.x = event.clientX; p.y = event.clientY
    return p.matrixTransform(svg.current.getScreenCTM().inverse())
  }
  const cancelDrag = () => {
    if (drag.current) { setPatch(drag.current.patch); setViewport(drag.current.viewport); drag.current = null }
  }
  const zoom = factor => setViewport(([x, y, width, height] = bounds) => {
    const size = Math.max(4, Math.min(512, width * factor))
    return [x + (width - size) / 2, y + (height - size) / 2, size, size]
  })
  const apply = () => {
    try {
      previewGraph(true, nextDocument(patch), library, node.id)
      edit(patch); setPatch(null); setError('')
    } catch (cause) { setError(cause.message || String(cause)) }
  }
  return <details className="arrangement-editor" open>
    <summary>2D-Raster · {copycats ? 'Copycats anordnen' : segments ? 'Segmente anordnen' : 'Räume anordnen'}</summary>
    {base.error ? <p role="status">Alle Eingänge verbinden und gültige Quellen verwenden: {base.error}</p> : <>
      <div className="arrangement-tools">
        {segments && <label>Ansicht<select aria-label="Rasteransicht" value={plane} onChange={event => {
          cancelDrag(); setPlane(event.target.value); setViewport(null)
        }}><option value="Z">Vorne · X/Y</option><option value="Y">Oben · X/Z</option><option value="X">Seite · Z/Y</option></select></label>}
        <label>Eingang<select aria-label="Anordnungseingang" value={active} onChange={event => setSelected(Number(event.target.value))}>
          {items.map((item, i) => <option key={i} value={i}>{i + 1}. {item.label}</option>)}
        </select></label>
      </div>
      <p className="hint">1 Rasterzelle = 1 Block = 16 hB. Ziehen verschiebt den gewählten Eingang; Pfeiltasten bewegen ihn um einen Block. Mittlere Maustaste verschiebt die Ansicht. Escape verwirft eine laufende Bewegung.</p>
      {node.kind === 'SEGMENT_UNION' && <p className="hint">Negative Versätze verschieben den gemeinsamen lokalen Ursprung; die Lage der Segmente zueinander bleibt erhalten.</p>}
      <svg ref={svg} className="arrangement-grid" viewBox={bounds.join(' ')} tabIndex={0}
        role="application" aria-label="2D-Anordnungsraster" onContextMenu={event => event.preventDefault()}
        onPointerDown={event => {
          if (event.button !== 1) return
          event.preventDefault(); svg.current.focus(); svg.current.setPointerCapture(event.pointerId)
          drag.current = { pan: true, bounds, clientX: event.clientX, clientY: event.clientY, patch, viewport }
        }} onPointerMove={event => {
          const state = drag.current
          if (!state) return
          const position = point(event)
          if (state.pan) {
            // Convert screen movement using the original viewBox, not the panned one.
            const rect = svg.current.getBoundingClientRect()
            const scale = state.bounds[2] / Math.min(rect.width, rect.height)
            setViewport([state.bounds[0] - (event.clientX - state.clientX) * scale,
              state.bounds[1] - (event.clientY - state.clientY) * scale, ...state.bounds.slice(2)])
            return
          }
          stage(state.index, moveArrangement(state.offset, plane,
            Math.round(position.x - state.start.x), Math.round(position.y - state.start.y), pattern || copycats ? 128 : 64))
        }} onPointerUp={event => {
          drag.current = null
          if (svg.current.hasPointerCapture(event.pointerId)) svg.current.releasePointerCapture(event.pointerId)
        }} onPointerCancel={cancelDrag} onLostPointerCapture={cancelDrag}
        onKeyDown={event => {
          // The graph shortcuts must not delete or move nodes while editing a raster.
          event.stopPropagation()
          if (event.key === 'Escape') { event.preventDefault(); cancelDrag(); return }
          const delta = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key]
          if (delta && offset) { event.preventDefault(); stage(active, moveArrangement(offset, plane, ...delta, pattern || copycats ? 128 : 64)) }
        }}>
        <defs><pattern id={patternId} width="1" height="1" patternUnits="userSpaceOnUse">
          <path d="M1,0H0V1" fill="none" stroke="#647984" strokeWidth="0.025" />
        </pattern></defs>
        <rect x={bounds[0]} y={bounds[1]} width={bounds[2]} height={bounds[3]} fill={`url(#${patternId})`}
          onPointerDown={event => {
            if (event.button === 1) {
              event.preventDefault(); svg.current.focus(); svg.current.setPointerCapture(event.pointerId)
              drag.current = { pan: true, bounds, clientX: event.clientX, clientY: event.clientY, patch, viewport }
              event.stopPropagation()
            }
          }} />
        {[...items.keys()].filter(i => i !== active).concat(items.length ? [active] : []).map(i => <path key={i}
          d={arrangementPath(items[i].cells)} fill={colors[i % colors.length]} fillOpacity={i === active ? 0.8 : 0.45}
          stroke={i === active ? '#ffe4a3' : '#172c37'} strokeWidth={i === active ? 0.12 : 0.04}
          onPointerDown={event => {
            if (event.button !== 0) return
            event.preventDefault(); event.stopPropagation(); svg.current.focus(); setSelected(i)
            svg.current.setPointerCapture(event.pointerId)
            drag.current = { index: i, start: point(event), offset: pattern ? transforms[i].offset : offsets[i] ?? { x: 0, y: 0, z: 0 }, patch, viewport }
          }} />)}
      </svg>
      <div className="button-row"><button type="button" onClick={() => zoom(0.75)}>Vergrößern</button>
        <button type="button" onClick={() => zoom(4 / 3)}>Verkleinern</button>
        <button type="button" onClick={() => setViewport(fitArrangement(items))}>Einpassen</button></div>
      {offset && <div className="arrangement-tools">
        {(segments ? ['x', 'y', 'z'] : ['x', 'z']).map(axis => <label key={axis}>{axis.toUpperCase()} · Blöcke
          <input aria-label={`Anordnung ${axis.toUpperCase()}`} type="number" min={pattern || copycats ? -128 : -64} max={pattern || copycats ? 128 : 64}
            value={offset[axis]} onChange={event => {
              const value = event.target.valueAsNumber
              if (Number.isInteger(value) && Math.abs(value) <= (pattern || copycats ? 128 : 64)) stage(active, { ...offset, [axis]: value })
            }} /></label>)}
        {pattern && <><label>Drehachse<select aria-label="Anordnung Drehachse" value={transforms[active]?.axis} onChange={event => stage(active, offset, { axis: event.target.value })}>
          {['X', 'Y', 'Z'].map(axis => <option key={axis}>{axis}</option>)}</select></label>
          <label>Drehwinkel<select aria-label="Anordnung Drehwinkel" value={transforms[active]?.quarterTurns} onChange={event => stage(active, offset, { quarterTurns: Number(event.target.value) })}>
            {[0, 1, 2, 3].map(q => <option key={q} value={q}>{q * 90}°</option>)}</select></label></>}
      </div>}
      <div className="button-row"><button type="button" disabled={!patch || !!current.error} onClick={apply}>Anordnung übernehmen</button>
        <button type="button" disabled={!patch} onClick={() => { setPatch(null); setError('') }}>Verwerfen</button></div>
      {(error || current.error) && <p role="alert">{error || current.error}</p>}
    </>}
  </details>
}
