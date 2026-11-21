import { IconSelect } from './IconSelect.jsx'
import {axisLabel} from './axis-labels.js'
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { arrangementGraph, previewGraph } from './generated/engine.mjs'
import { arrangementPath, fitArrangement, moveArrangement } from './arrangement.js'
import { editNode, model as editorModel } from './model.js'

const colors = ['#55b8d0', '#d69b62', '#90be78', '#ba8dcc', '#da7980', '#799ee0']

export function ArrangementGrid({ node, document, library, edit, standalone=false, readOnly=false, onPreview }) {
  const pattern = node.kind === 'PATTERN'
  const booleans = ['SEGMENT_UNION','SEGMENT_INTERSECTION','SEGMENT_DIFFERENCE','SEGMENT_SYMMETRIC_DIFFERENCE'].includes(node.kind)
  const segments = pattern || node.kind.startsWith('SEGMENT_')
  const [plane, setPlane] = useState(segments ? 'Z' : 'Y')
  const [patch, setPatch] = useState(null)
  const [selected, setSelected] = useState(0)
  const [viewport, setViewport] = useState(null)
  const [error, setError] = useState('')
  const svg = useRef(null)
  const drag = useRef(null)
  const patternId = useId().replaceAll(':', '')
  useEffect(() => { setPatch(null); setError(''); drag.current = null }, [document, library])
  useEffect(() => { onPreview?.(patch); return () => onPreview?.(null) }, [patch,onPreview])
  const nextDocument = changes => changes ? editNode('building',document,{...node,...changes}) : document
  const read = changes => {
    try { return { model: arrangementGraph(nextDocument(changes), library, node.id, plane) } }
    catch (cause) { return { error: cause.message || String(cause) } }
  }
  const base = useMemo(() => read(null), [document, library, node.id, plane])
  const current = useMemo(() => patch ? read(patch) : base, [base, patch])
  const items = current.model?.items ?? []
  const conflicts = current.model?.conflicts ?? []
  const conflictMessage = conflict => `${conflict.kind === 'INTERIORS' ? 'Innenräume überlappen' : 'Wand ragt in fremden Innenraum'}: ${conflict.first + 1}. ${conflict.firstLabel} / ${conflict.second + 1}. ${conflict.secondLabel}. ${conflict.cells.map(([x,z]) => `X ${x}, Z ${z}`).join('; ')}`
  const valid = !current.error && !current.model?.pending && conflicts.length === 0
  const bounds = viewport ?? fitArrangement(base.model?.items ?? [])
  const active = Math.min(selected, Math.max(0, items.length - 1))
  const transforms = current.model?.transforms ?? []
  const offsets = booleans && editorModel.directAreas ? patch?.operandOffsets ?? node.operandOffsets ?? current.model?.items.map(item=>item.offset || {x:0,y:0,z:0}) ?? [] : patch?.offsets ?? node.offsets ?? []
  const editable = !readOnly && current.model?.editable!==false
  const offset = pattern ? transforms[active]?.offset : offsets[active] ?? { x: 0, y: 0, z: 0 }
  const stage = (index, value, rotation = {}) => {
    if(!editable) return
    if (pattern) setPatch({ patternAxis: 'transform', transforms: transforms.map((item, i) =>
      i === index ? { ...item, ...rotation, offset: value } : item) })
    else setPatch({ [booleans && editorModel.directAreas ? 'operandOffsets' : 'offsets']: node.inputs.map((_, i) => i === index ? value : offsets[i] ?? { x: 0, y: 0, z: 0 }) })
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
      if (!valid) throw new Error(conflicts.map(conflictMessage).join("\n") || current.model?.pending || current.error)
      previewGraph(true, nextDocument(patch), library, node.id)
      edit(patch); setPatch(null); setError('')
    } catch (cause) { setError(cause.message || String(cause)) }
  }
  const Container=standalone?'div':'details'
  return <Container className={`arrangement-editor${standalone?' is-standalone':''}`} {...(!standalone?{open:true}:{})}>
    {!standalone && <summary>2D-Raster · {segments ? 'Segmente anordnen' : 'Räume anordnen'}</summary>}
    {base.error ? <p role="status">Alle Eingänge verbinden und gültige Quellen verwenden: {base.error}</p> : <>
      <div className="arrangement-tools">
        {segments && <label>Ansicht<IconSelect domain="view" aria-label="Rasteransicht" value={plane} onChange={event => {
          cancelDrag(); setPlane(event.target.value); setViewport(null)
        }}><option value="Z">Vorne · X/Y</option><option value="Y">Oben · X/Z</option><option value="X">Seite · Z/Y</option></IconSelect></label>}
        <label>Eingang<IconSelect domain="input" aria-label="Anordnungseingang" value={active} onChange={event => setSelected(Number(event.target.value))}>
          {items.map((item, i) => <option key={i} value={i}>{node.kind==='SEGMENT_DIFFERENCE' ? ['A','B'][i] : i + 1}. {item.label}</option>)}
        </IconSelect></label>
      </div>
      <p className="hint">Gemeinsamer Ursprung: X 0 · Y 0 · Z 0. 1 Rasterzelle = 1 Block = 16 hB. Ziehen verschiebt den gewählten Eingang; Pfeiltasten bewegen ihn um einen Block. Mittlere Maustaste verschiebt die Ansicht. Escape verwirft eine laufende Bewegung.</p>
      {node.kind === 'SEGMENT_UNION' && <p className="hint">Negative Versätze verschieben den gemeinsamen lokalen Ursprung; die Lage der Segmente zueinander bleibt erhalten.</p>}
      {!segments && <div role="status" aria-live="polite" className={`arrangement-status ${!valid ? 'invalid' : 'valid'}`}>
        {conflicts.length ? `Anordnung unzulässig · ${conflicts.length} Konflikte (rot / !)` : !valid ? 'Anordnung noch nicht prüfbar' : 'Anordnung zulässig · gemeinsame Wandbereiche erlaubt'}
        {current.model?.pending && <p>{current.model.pending}</p>}
        {conflicts.map((conflict,i) => <p key={i}>{conflictMessage(conflict)}</p>)}
      </div>}
      <ul className="arrangement-legend">{items.map((item,i)=><li key={i}><span style={{background:colors[i%colors.length]}}/>{node.kind==='SEGMENT_DIFFERENCE'?['A','B'][i]:i+1}. {item.label}{item.error && <small>{item.error}</small>}</li>)}</ul>
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
            Math.round(position.x - state.start.x), Math.round(position.y - state.start.y), pattern ? 128 : 64))
        }} onPointerUp={event => {
          drag.current = null
          if (svg.current.hasPointerCapture(event.pointerId)) svg.current.releasePointerCapture(event.pointerId)
        }} onPointerCancel={cancelDrag} onLostPointerCapture={cancelDrag}
        onKeyDown={event => {
          // The graph shortcuts must not delete or move nodes while editing a raster.
          event.stopPropagation()
          if (event.key === 'Escape') { event.preventDefault(); cancelDrag(); return }
          const delta = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key]
          if (delta && offset) { event.preventDefault(); stage(active, moveArrangement(offset, plane, ...delta, pattern ? 128 : 64)) }
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
        <path d={`M0,${bounds[1]}v${bounds[3]}M${bounds[0]},0h${bounds[2]}`} stroke="#e4bb78" strokeWidth="0.06" fill="none" pointerEvents="none"/>
        {!segments && items.map((item,i) => <path key={`wall-${i}`} d={arrangementPath(item.walls || [])} fill="#7e8990" fillOpacity="0.65"><title>Wandbereich · {i+1}. {item.label}</title></path>)}
        {[...items.keys()].filter(i => i !== active).concat(items.length ? [active] : []).map(i => <path key={i}
          d={arrangementPath(items[i].cells)} fill={colors[i % colors.length]} fillOpacity={i === active ? 0.8 : 0.45}
          stroke={i === active ? '#ffe4a3' : '#172c37'} strokeWidth={i === active ? 0.12 : 0.04}
          onPointerDown={event => {
            if (event.button !== 0 || !editable) return
            event.preventDefault(); event.stopPropagation(); svg.current.focus(); setSelected(i)
            svg.current.setPointerCapture(event.pointerId)
            drag.current = { index: i, start: point(event), offset: pattern ? transforms[i].offset : offsets[i] ?? { x: 0, y: 0, z: 0 }, patch, viewport }
          }} />)}
        {!segments && conflicts.map((conflict,i) => <g key={`conflict-${i}`} pointerEvents="none">
          <title>{conflictMessage(conflict)}</title><path d={arrangementPath(conflict.cells)} fill="#d44e4e" fillOpacity="0.9" stroke="#fff1e7" strokeWidth="0.08" />
          {conflict.cells.map(([x,z]) => <text key={`${x}:${z}`} x={x+0.5} y={z+0.75} textAnchor="middle" fill="white" fontSize="0.75">!</text>)}
        </g>)}
      </svg>
      <div className="button-row arrangement-zoom"><button type="button" aria-label="Vergrößern" title="Vergrößern" onClick={() => zoom(0.75)}>＋</button>
        <button type="button" aria-label="Verkleinern" title="Verkleinern" onClick={() => zoom(4 / 3)}>−</button>
        <button type="button" aria-label="Raster einpassen" title="Einpassen" onClick={() => setViewport(fitArrangement(items))}>⤢</button></div>
      {offset && <div className="arrangement-tools arrangement-position"><fieldset disabled={!editable} className="arrangement-coordinates">
        {(segments ? ['x', 'y', 'z'] : ['x', 'z']).map(axis => <label key={axis}>{axisLabel(axis.toUpperCase())} · Blöcke
          <input aria-label={`Anordnung ${axis.toUpperCase()}`} type="number" min={pattern ? -128 : -64} max={pattern ? 128 : 64}
            value={offset[axis]} onChange={event => {
              const value = event.target.valueAsNumber
              if (Number.isInteger(value) && Math.abs(value) <= (pattern ? 128 : 64)) stage(active, { ...offset, [axis]: value })
            }} /></label>)}
        {pattern && <><label>Drehachse<IconSelect domain="axis" aria-label="Anordnung Drehachse" value={transforms[active]?.axis} onChange={event => stage(active, offset, { axis: event.target.value })}>
          {['X', 'Y', 'Z'].map(axis => <option key={axis} value={axis}>{axisLabel(axis)}</option>)}</IconSelect></label>
          <label>Drehwinkel<IconSelect domain="angle" aria-label="Anordnung Drehwinkel" value={transforms[active]?.quarterTurns} onChange={event => stage(active, offset, { quarterTurns: Number(event.target.value) })}>
            {[0, 1, 2, 3].map(q => <option key={q} value={q}>{q * 90}°</option>)}</IconSelect></label></>}
      </fieldset></div>}
      {editable && <div className="button-row arrangement-apply"><button type="button" disabled={!patch || !valid} onClick={apply}>Anordnung übernehmen</button>
        <button type="button" disabled={!patch} onClick={() => { setPatch(null); setError('') }}>Verwerfen</button></div>}
      {(error || current.error) && <p role="alert">{error || current.error}</p>}
    </>}
  </Container>
}

export function ArrangementPanel({collapsed,onToggle,...props}) {
  const storey=props.node.kind==='STOREY'
  return <section className="arrangement-panel preview-panel" aria-label={storey?'2D-Raumanordnung':'2D-Segmentanordnung'}><div className="panel-heading"><span>2D-Anordnung · {storey?'Räume':'Segmente'}</span><button type="button" aria-expanded={!collapsed} aria-label={collapsed?'2D-Anordnung ausklappen':'2D-Anordnung einklappen'} onClick={onToggle}>{collapsed?'▸':'▾'}</button></div>
    {!collapsed && (storey&&props.node.storeyMode!=='rooms'?<p className="hint">Für die relative Raumanordnung am Geschossknoten den Aufbau „Räume“ wählen und Räume anschließen.</p>:<ArrangementGrid {...props} standalone/>)}
  </section>
}
