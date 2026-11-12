import { lazy, Suspense, useMemo, useRef, useState } from 'react'
import { Background, Controls, Handle, Position, ReactFlow } from '@xyflow/react'
import { checkGraphBundleConflicts, compareGraphVersions, exportGraphBundle, extractVersionedGraph, inspectVersionedGraph, nextGraphVersion, resolveVersionedGraph, switchGraphVersion, validateGraphBundle, validateVersionedGraph } from './generated/engine.mjs'
const Preview = lazy(() => import('./Preview.jsx'))
const key = d => `${d.graphId}@${d.version}`
const clone = value => structuredClone(value)
const fresh = prefix => `${prefix}_${crypto.randomUUID().slice(0, 12)}`
const nativeNodes = d => (d.shape || d.building).nodes
const bodyKey = d => d.mode === 'FORM' ? 'shape' : 'building'
function PortNode({ data, selected }) {
  return <article className={`diagram-node ${selected ? 'selected' : ''}`} style={{ minHeight: 65 + Math.max(data.inputs.length, data.outputs.length) * 24 }}>
    <strong>{data.title}</strong>
    {data.inputs.map((p, i) => <div key={p.id} style={{ position: 'absolute', top: 38 + i * 24, left: 12 }}><Handle type="target" id={p.id} position={Position.Left} /><small>{p.name}</small></div>)}
    {data.outputs.map((p, i) => <div key={p.id} style={{ position: 'absolute', top: 38 + i * 24, right: 12 }}><small>{p.name}</small><Handle type="source" id={p.id} position={Position.Right} /></div>)}
  </article>
}
const nodeTypes = { versioned: PortNode }
function download(value, name) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export default function VersionedGraphs({ initial, initialSelection = [], definitions = {}, library, materials, onSave, onClose, onApply }) {
  const [state, setState] = useState({ document: clone(initial), definitions: clone(definitions), library: clone(library) })
  const [past, setPast] = useState([]), [future, setFuture] = useState([])
  const [selection, setSelection] = useState(initialSelection), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const [pending, setPending] = useState(null), [parents, setParents] = useState([])
  const [positions, setPositions] = useState({})
  const [previewMode, setPreviewMode] = useState('graph'), input = useRef()
  const d = state.document, mode = d.mode === 'FORM' ? 'form' : 'building'
  const entries = useMemo(() => { try { return inspectVersionedGraph(d) } catch { return [] } }, [d])
  const resolved = useMemo(() => { try { return { value: resolveVersionedGraph(d, state.definitions) } } catch (e) { return { error: e.message } } }, [d, state.definitions])
  const selected = selection.length === 1 ? selection[0] : ''
  const reference = d.references.find(r => r.id === selected)
  const native = nativeNodes(d).find(n => n.id === selected)
  function act(action) { try { setError(''); action() } catch (e) { setError(e.message) } }
  function commit(next, validate = true) {
    inspectVersionedGraph(next.document)
    let valid = true; try { validateVersionedGraph(d) } catch { valid = false }
    if (validate && valid) validateVersionedGraph(next.document)
    setPast(p => [...p.slice(-99), state]); setFuture([]); setState(next)
  }
  function edit(next, validate = true) { commit({ ...state, document: { ...next, version: 0 } }, validate) }
  function travel(undo) {
    const from = undo ? past : future
    if (!from.length) return
    const next = from.at(-1)
    if (undo) { setPast(from.slice(0, -1)); setFuture(f => [...f, state]) } else { setFuture(from.slice(0, -1)); setPast(p => [...p, state]) }
    setState(next); setError('')
  }
  function patchNode(patch) { edit({ ...d, [bodyKey(d)]: { ...d[bodyKey(d)], nodes: nativeNodes(d).map(n => n.id === selected ? { ...n, ...patch } : n) } }) }
  function publish() {
    const published = nextGraphVersion(d, state.definitions)
    exportGraphBundle(published, state.definitions, state.library)
    const next = { ...state, document: published, definitions: { ...state.definitions, [key(published)]: clone(published) } }
    commit(next); onSave(next); setNotice(`Version ${key(published)} veröffentlicht. Bestehende Referenzen bleiben unverändert.`)
  }
  function openDefinition(definition) {
    setParents(p => [...p, d]); setState(s => ({ ...s, document: clone(definition) })); setPast([]); setFuture([]); setSelection([]); setError('')
  }
  const available = Object.values(state.definitions).filter(g => g.mode === d.mode)
  const versions = reference ? available.filter(g => g.graphId === reference.reference.graphId) : []
  return <section className="versioned-dialog" role="dialog" aria-modal="true" aria-label="Versionierte Untergraphen">
    <header><h2>Versionierte Untergraphen</h2><button onClick={onClose}>Schließen</button></header>
    <div className="toolbar">
      <input aria-label="Graphname" maxLength={64} value={d.name} onChange={e => act(() => edit({ ...d, name: e.target.value }, false))} />
      <span>{d.graphId} · {d.version ? `Version ${d.version}` : 'Entwurf'}</span>
      <button disabled={!past.length} onClick={() => travel(true)}>Rückgängig</button><button disabled={!future.length} onClick={() => travel(false)}>Wiederholen</button>
      <button onClick={() => act(() => { onSave(state); setNotice('Entwurf und Definitionen gespeichert.') })}>Speichern</button>
      <button onClick={() => act(publish)}>Neue Version veröffentlichen</button>
      {onApply && <button onClick={() => act(() => onApply(exportGraphBundle(d, state.definitions, state.library)))}>In Entwurf übernehmen</button>}
      <button onClick={() => act(() => download(exportGraphBundle(d, state.definitions, state.library), `${d.name}.graph.json`))}>Paket exportieren</button>
      <button onClick={() => input.current.click()}>Paket importieren</button>
      <input ref={input} type="file" hidden accept="application/json,.json" onChange={e => { const file = e.target.files[0]; e.target.value = ''; if (!file) return
        if (file.size > 16000000) { setError('Das Paket überschreitet 16 MB.'); return }
        file.text().then(text => act(() => { const bundle = JSON.parse(text); checkGraphBundleConflicts(bundle, state.definitions, state.library)
          const merged = { ...state.definitions }
          for (const [id, graph] of Object.entries(bundle.graphs)) { merged[id] = graph }
          if (bundle.root.version) { const id = key(bundle.root); merged[id] = bundle.root }
          const nextLibrary = clone(state.library)
          for (const type of ['forms', 'modules']) { nextLibrary[type] ||= {}; for (const [id, value] of Object.entries(bundle[type])) {
            nextLibrary[type][id] = value
          } }
          commit({ document: bundle.root, definitions: merged, library: nextLibrary }); setSelection([])
        })).catch(e => setError(e.message))
      }} />
      {parents.length > 0 && <button onClick={() => { setState(s => ({ ...s, document: parents.at(-1) })); setParents(p => p.slice(0, -1)); setPast([]); setFuture([]); setSelection([]) }}>Zum Elterngraphen</button>}
    </div>
    <div className="personal-library-bar">
      <button disabled={!selection.length} onClick={() => act(() => {
        const extracted = extractVersionedGraph(d, selection, fresh('graph'), d.name.slice(0, 58) + ' Teil', fresh('ref'))
        commit({ ...state, document: { ...extracted.parent, version: 0 }, definitions: { ...state.definitions, [key(extracted.definition)]: extracted.definition } }); setSelection([])
      })}>Auswahl als Untergraph veröffentlichen</button>
      <select aria-label="Untergraphreferenz einfügen" value="" onChange={e => act(() => {
        const definition = state.definitions[e.target.value], id = fresh('ref')
        edit({ ...d, references: [...d.references, { id, reference: { graphId: definition.graphId, version: definition.version }, ports: clone(definition.ports), x: 200, y: 200 }] }); setSelection([id])
      })}><option value="" disabled>Referenz einfügen</option>{available.map(g => <option key={key(g)} value={key(g)}>{g.name} @{g.version}</option>)}</select>
      <select aria-label="Graphdefinition öffnen" value="" onChange={e => act(() => openDefinition(state.definitions[e.target.value]))}><option value="" disabled>Definition öffnen</option>{available.map(g => <option key={key(g)} value={key(g)}>{g.name} @{g.version}</option>)}</select>
    </div>
    <div className="versioned-workspace">
      <div className="versioned-canvas"><ReactFlow key={d.graphId} nodeTypes={nodeTypes} nodes={entries.map(entry => ({ id: entry.id, type: 'versioned', data: { ...entry, title: d.references.some(r => r.id === entry.id) ? (() => { const r = d.references.find(r => r.id === entry.id); return `${state.definitions[`${r.reference.graphId}@${r.reference.version}`]?.name || r.reference.graphId} @${r.reference.version}` })() : entry.title }, position: positions[entry.id] || { x: entry.x, y: entry.y }, selected: selection.includes(entry.id) }))}
        edges={d.connections.map((c, i) => ({ id: `wire${i}`, source: c.source, target: c.target, sourceHandle: c.sourcePort || 'out', targetHandle: c.port }))}
        onNodesChange={changes => {
          const selectedChanges = changes.filter(c => c.type === 'select')
          if (selectedChanges.length) setSelection(previous => { const next = new Set(previous); selectedChanges.forEach(c => c.selected ? next.add(c.id) : next.delete(c.id)); return [...next] })
          const moved = changes.filter(c => c.type === 'position' && c.position)
          if (moved.length) setPositions(previous => ({ ...previous, ...Object.fromEntries(moved.map(c => [c.id, c.position])) }))
        }} selectionOnDrag multiSelectionKeyCode="Shift" fitView deleteKeyCode={null}
        onConnect={c => act(() => edit({ ...d, connections: [...d.connections.filter(w => w.target !== c.target || w.port !== c.targetHandle), { source: c.source, sourcePort: c.sourceHandle, target: c.target, port: c.targetHandle }] }))}
        onEdgeDoubleClick={(_, edge) => act(() => edit({ ...d, connections: d.connections.filter((_, i) => `wire${i}` !== edge.id) }))}
        onNodeDragStop={(_, node) => act(() => { const p = { x: Math.round(node.position.x), y: Math.round(node.position.y) }
          setPositions({}); edit({ ...d, [bodyKey(d)]: { ...d[bodyKey(d)], nodes: nativeNodes(d).map(n => n.id === node.id ? { ...n, ...p } : n) }, references: d.references.map(r => r.id === node.id ? { ...r, ...p } : r) })
        })}><Background /><Controls /></ReactFlow></div>
      <aside>
        {reference && <><h3>Feste Referenz</h3><p>{reference.reference.graphId} @{reference.reference.version}</p>
          <button onClick={() => act(() => { const definition = state.definitions[`${reference.reference.graphId}@${reference.reference.version}`]; if (!definition) throw new Error('Die referenzierte Version fehlt. Paket mit dieser Version importieren.'); openDefinition(definition) })}>Definition dieser Version öffnen</button>
          <select aria-label="Referenzversion wechseln" value="" onChange={e => act(() => { const next = state.definitions[e.target.value], before = { ...next, ports: reference.ports }; setPending({ next, changes: compareGraphVersions(before, next) }) })}>
            <option value="" disabled>Andere Version wählen</option>{versions.map(g => <option key={key(g)} value={key(g)}>{g.name} @{g.version}</option>)}</select></>}
        {native && <><h3>{native.kind}</h3>
          {native.kind === 'INPUT' && <label>Copycat-Block<input value={native.part.source} onChange={e => act(() => patchNode({ part: { ...native.part, source: e.target.value } }))} /></label>}
          {native.kind === 'INT' && <label>Anzahl<input type="number" step="1" value={native.countValue ?? 0} onChange={e => act(() => patchNode({ countValue: Number(e.target.value) }))} /></label>}
          {['SEGMENT', 'SEGMENT_LAYOUT'].includes(native.kind) && ['length', 'height', 'depth'].map(field => <label key={field}>{field}<input type="number" min="1" max="128" value={native[field]} onChange={e => act(() => patchNode({ [field]: Number(e.target.value) }))} /></label>)}
        </>}
        <h3>Benannte Schnittstelle</h3>
        {d.ports.map(p => <label key={p.id}>{p.direction === 'INPUT' ? 'Eingang' : 'Ausgang'} · {p.id} · {p.type}<input maxLength={64} value={p.name} onChange={e => act(() => edit({ ...d, ports: d.ports.map(port => port.id === p.id ? { ...port, name: e.target.value } : port) }, false))} /></label>)}
        <p className="hint">Verbindungen durch Doppelklick trennen. Vorhandene lokale Gruppen bleiben erhalten; die Auswahl wird nur auf ausdrücklichen Wunsch ausgelagert.</p>
        {(error || resolved.error) && <p role="alert" className="error">{error || resolved.error}</p>}{notice && <p role="status">{notice}</p>}
      </aside>
      <div className="versioned-preview">{resolved.value && <Suspense fallback={<p>Vorschau wird geladen …</p>}><Preview mode={mode} document={resolved.value} library={state.library} materials={materials} selected={resolved.value.nodeOutputs?.[selected]?.[reference?.ports.find(p => p.direction === 'OUTPUT')?.id || 'out'] || ''} previewMode={previewMode} onMode={setPreviewMode} /></Suspense>}</div>
    </div>
    {pending && <div className="versioned-confirm" role="dialog" aria-label="Portvergleich"><h3>Auf Version {pending.next.version} wechseln?</h3>
      {pending.changes.length ? pending.changes.map(c => <p key={c.id} className={c.incompatible ? 'error' : ''}>{c.id}: {c.before?.type || 'neu'} → {c.after?.type || 'entfernt'}{c.incompatible ? ' · Verbindung muss repariert werden' : ''}</p>) : <p>Die Schnittstelle ist unverändert.</p>}
      <p>Vorhandene Verbindungen bleiben erhalten.</p><button onClick={() => act(() => { edit(switchGraphVersion(d, selected, pending.next), false); setPending(null) })}>Version übernehmen</button><button onClick={() => setPending(null)}>Abbrechen</button></div>}
  </section>
}
