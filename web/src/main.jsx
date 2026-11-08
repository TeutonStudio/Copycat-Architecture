import { lazy, memo, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Background, Controls, Handle, MiniMap, Position, ReactFlow, useUpdateNodeInternals } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { adaptRectangles, newNode, starterBuilding } from './generated/engine.mjs'
import { applyWires, connections, formGroups, inspect, model, position, protectedNode, readGraph, replaceNodes, validateLibrary, writeGraph } from './model.js'
import { DocumentFields, Field, names, NodeFields, portName } from './Inspector.jsx'
import './style.css'
import baseLibrary from '../../examples/library.json'
import formExample from '../../examples/nodegraph-form.json'
import formRotation from '../../examples/nodegraph-rotation-xor.json'
import formMaterials from '../../examples/nodegraph-material-groups.json'
import buildingExample from '../../examples/building-graph.json'
import buildingAssembly from '../../examples/building-graph-assembly.json'
import buildingBasements from '../../examples/building-graph-basements.json'

const Preview = lazy(() => import('./Preview.jsx'))
const wiki = 'https://github.com/TeutonStudio/copycat-architect/wiki'
const examples = {
  form: [
    ['Aussparung', formExample], ['Rotation und Differenz', formRotation], ['Materialgruppen', formMaterials]
  ],
  building: [
    ['Gebäude aus Copycats', buildingAssembly], ['L-förmiges Gebäude', buildingExample], ['Gebäude mit Kellern', buildingBasements]
  ]
}
const query = new URLSearchParams(window.location.search)
const initialMode = query.get('editor') === 'form' || query.get('graph') === 'copycat' ? 'form' : 'building'

function DiagramNode({ id, data: { node, ports }, selected }) {
  const updateInternals = useUpdateNodeInternals()
  const signature = ports.map(port => port.id).join('|')
  useEffect(() => updateInternals(id), [id, signature, updateInternals])
  const source = !['BUILDING', 'OUTPUT'].includes(node.kind)
  return <div className={`diagram-node ${selected ? 'selected' : ''} ${source ? '' : 'result-node'}`}>
    <div className="node-heading">
      <span className="node-symbol">{source ? '◇' : '◆'}</span>
      <div><strong>{node.name || names[node.kind]}</strong><small>{node.name ? names[node.kind] : node.id}</small></div>
      {source && <Handle type="source" position={Position.Right} id="out" aria-label="Ausgang" />}
    </div>
    {ports.length > 0 && <div className="ports">{ports.map(port => <div className="port" key={port.id}>
      <Handle type="target" position={Position.Left} id={port.id} title={portName(port, node)} />
      <span>{portName(port, node)}</span>
      <span className={`port-status ${port.source ? 'connected' : ''}`}>{port.source ? '●' : '○'}</span>
    </div>)}</div>}
  </div>
}

const nodeTypes = { architect: memo(DiagramNode) }
const edgeStyle = { stroke: '#8daeb1', strokeWidth: 1.7 }

function useHistory(initial) {
  const [state, setState] = useState(() => ({ current: initial, past: [], future: [] }))
  const push = next => setState(({ current, past }) => ({
    current: next, past: [...past.slice(-49), current], future: []
  }))
  const undo = () => setState(state => state.past.length ? {
    current: state.past.at(-1), past: state.past.slice(0, -1), future: [state.current, ...state.future]
  } : state)
  const redo = () => setState(state => state.future.length ? {
    current: state.future[0], past: [...state.past, state.current], future: state.future.slice(1)
  } : state)
  return { ...state, push, undo, redo }
}

function downloadJson(document, mode) {
  const name = (document.name ?? 'copycat').normalize('NFKD').replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-|-$/g, '') || 'copycat'
  const url = URL.createObjectURL(new Blob([JSON.stringify(document) + '\n'], { type: 'application/json' }))
  const link = window.document.createElement('a')
  link.href = url
  link.download = `${name}-${mode}.json`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function Studio({ mode, active }) {
  const history = useHistory(examples[mode][0][1])
  const document = history.current
  const [library, setLibrary] = useState(baseLibrary)
  const [selected, setSelected] = useState(null)
  const [selectedWire, setSelectedWire] = useState(null)
  const [dragging, setDragging] = useState(null)
  const [message, setMessage] = useState(null)
  const [flow, setFlow] = useState(null)
  const [kind, setKind] = useState('')
  const [workspace, setWorkspace] = useState(0)
  const graphInput = useRef(null)
  const libraryInput = useRef(null)
  const canvas = useRef(null)
  const entries = useMemo(() => inspect(mode, document), [mode, document])
  const wires = useMemo(() => connections(entries), [entries])
  const current = entries.find(({ node }) => node.id === selected)
  const resultNode = entries.find(({ node }) => ['OUTPUT', 'BUILDING'].includes(node.kind))
  const visibleNode = current ?? resultNode
  const kinds = mode === 'form' ? model.form.map(type => type.kind).filter(kind => kind !== 'OUTPUT') : model.building.filter(kind => kind !== 'BUILDING')
  const edgeId = ({ target, port }) => `${target}/${port}`
  const nodes = entries.map(data => ({
    id: data.node.id, type: 'architect', data,
    position: dragging?.id === data.node.id ? dragging.position : { x: data.node.x, y: data.node.y },
    selected: data.node.id === selected
  }))
  const edges = useMemo(() => wires.map(wire => ({
    id: edgeId(wire), source: wire.source, sourceHandle: 'out', target: wire.target, targetHandle: wire.port,
    style: wire.source === 'empty_wall' ? { ...edgeStyle, opacity: 0.38 } : edgeStyle,
    selected: edgeId(wire) === selectedWire
  })), [wires, selectedWire])

  const transact = callback => {
    try {
      callback()
      setMessage(null)
    } catch (error) {
      setMessage({ error: true, text: error.message || String(error) })
    }
  }
  const commit = next => {
    inspect(mode, next)
    if (JSON.stringify(next) !== JSON.stringify(document)) history.push(next)
  }
  const patchNode = patch => transact(() => {
    const { node } = visibleNode
    let updated = { ...document.graph.nodes.find(item => item.id === node.id), ...patch }
    if ('rectangles' in patch || patch.storeyMode === 'walls') {
      const adapted = adaptRectangles(node, updated)
      updated = { ...updated, walls: adapted.walls }
    }
    if (patch.storeyMode === 'rooms' && !node.inputs.length) updated.inputs = ['']
    commit(replaceNodes(document, document.graph.nodes.map(item => item.id === node.id ? updated : item)))
  })
  const setWire = (target, port, source) => transact(() => {
    const rest = wires.filter(wire => wire.target !== target || wire.port !== port)
    commit(applyWires(mode, document, entries, source ? [...rest, { source, target, port }] : rest))
    setSelectedWire(null)
  })
  const removeSelection = () => {
    const wire = wires.find(wire => edgeId(wire) === selectedWire)
    if (wire) {
      setWire(wire.target, wire.port, '')
    } else if (current && !protectedNode(current.node)) {
      transact(() => {
        const rest = wires.filter(wire => wire.source !== selected && wire.target !== selected)
        commit(applyWires(mode, document, entries, rest, [selected]))
        setSelected(null)
      })
    }
  }
  const open = raw => {
    commit(readGraph(raw, mode))
    setSelected(null)
    setSelectedWire(null)
    setWorkspace(key => key + 1)
  }
  const add = () => transact(() => {
    if (!kind) return
    const { left, top, width, height } = canvas.current.getBoundingClientRect()
    const center = flow?.screenToFlowPosition({ x: left + width * 0.5, y: top + height * 0.4 }) ?? { x: 50, y: 50 }
    const id = `node_${crypto.randomUUID().slice(0, 13)}`
    const node = newNode(mode === 'building', kind, id, position(center.x), position(center.y))
    if (kind === 'INPUT') node.part.group = formGroups(document)[0].id
    commit(replaceNodes(document, [...document.graph.nodes, node]))
    setSelected(id)
    setSelectedWire(null)
  })
  const onDrag = useCallback((_, node) => setDragging({ id: node.id, position: node.position }), [])
  const onDragStop = (_, node) => {
    setDragging(null)
    transact(() => commit(replaceNodes(document, document.graph.nodes.map(item => item.id === node.id
      ? { ...item, x: position(node.position.x), y: position(node.position.y) } : item))))
  }
  const compatible = ({ source, target, targetHandle }) => {
    const port = entries.find(({ node }) => node.id === target)?.ports.find(port => port.id === targetHandle)
    const sourceKind = entries.find(({ node }) => node.id === source)?.node.kind
    return source !== target && Boolean(port?.accepts.includes(sourceKind))
  }
  const importFile = async (event, isLibrary) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    try {
      if (file.size > 16_000_000) throw new Error('Die JSON-Datei ist größer als 16 MB.')
      const raw = JSON.parse(await file.text())
      if (isLibrary) {
        const imported = validateLibrary(raw)
        setLibrary({ forms: { ...baseLibrary.forms, ...imported.forms }, modules: { ...baseLibrary.modules, ...imported.modules } })
      }
      else open(raw)
      setMessage({ text: `${file.name} geöffnet` })
    } catch (error) {
      setMessage({ text: error.message, error: true })
    }
  }

  const count = entries.length
  const maxNodes = mode === 'form' ? model.formNodes : model.buildingNodes
  const portLimits = mode === 'form' ? model.form.find(type => type.kind === visibleNode.node.kind)
    : visibleNode.node.kind === 'PATTERN' ? { min: 2, max: model.sockets } : null
  const isFormSourceLimit = kind === 'INPUT' && entries.filter(({ node }) => node.kind === 'INPUT').length >= model.formSources
  const previewSelected = current && ['ROOM', 'STOREY', 'BUILDING'].includes(current.node.kind) ? current.node.id : ''
  const exportFile = () => transact(() => downloadJson(writeGraph(mode, document), mode))
  const copyJson = async () => {
    try {
      const json = JSON.stringify(writeGraph(mode, document))
      await navigator.clipboard.writeText(json)
      setMessage({ text: 'Minecraft-JSON kopiert. Im passenden Editor im Spiel importieren.' })
    } catch (error) {
      setMessage({ error: true, text: `JSON konnte nicht kopiert werden: ${error.message || String(error)}. Nutze alternativ JSON exportieren.` })
    }
  }
  const keydown = event => {
    if (event.target.closest('input, textarea, select, [contenteditable=true]')) return
    if ((event.ctrlKey || event.metaKey) && ['z', 'y'].includes(event.key.toLowerCase())) {
      event.preventDefault()
      if (event.shiftKey || event.key.toLowerCase() === 'y') history.redo()
      else history.undo()
    }
    if (['Delete', 'Backspace'].includes(event.key) && (selected || selectedWire)) {
      event.preventDefault()
      removeSelection()
    }
  }
  return <div className="studio" hidden={!active} onKeyDown={keydown}>
    <div className="toolbar">
      <label className="example-select"><span>BEISPIELE</span><select aria-label="Beispiel öffnen" value="" onChange={e => transact(() => open(examples[mode][Number(e.target.value)][1]))}>
        <option value="" disabled>Beispiel öffnen</option>
        {examples[mode].map(([name], i) => <option key={name} value={i}>{name}</option>)}
      </select></label>
      <button type="button" onClick={() => transact(() => open(mode === 'building' ? starterBuilding() :
        { schema: 3, name: 'Neue Form', parts: [{ source: 'copycats:copycat_slice' }] }))}>Neu</button>
      <button type="button" onClick={() => graphInput.current.click()}>JSON öffnen</button>
      <input hidden ref={graphInput} type="file" accept=".json,application/json" onChange={event => importFile(event, false)} />
      {mode === 'building' && <>
        <button type="button" onClick={() => libraryInput.current.click()}>Bibliothek öffnen</button>
        <input hidden ref={libraryInput} type="file" accept=".json,application/json" onChange={event => importFile(event, true)} />
      </>}
      <button type="button" className="primary" onClick={exportFile}>JSON exportieren</button>
      <button type="button" onClick={copyJson}>Minecraft-JSON kopieren</button>
      <span className="toolbar-spacer" />
      <button type="button" aria-label="Rückgängig" title="Rückgängig" disabled={!history.past.length} onClick={history.undo}>↶</button>
      <button type="button" aria-label="Wiederholen" title="Wiederholen" disabled={!history.future.length} onClick={history.redo}>↷</button>
    </div>

    <div className="workbench">
      <section className="canvas-panel" aria-label="Graph bearbeiten">
        <div className="panel-heading graph-heading">
          <div><span>{document.name || 'Entwurf'}</span><small>{count} / {maxNodes} Knoten</small></div>
          <div className="button-row">
            <select aria-label="Knotentyp" value={kind} onChange={event => setKind(event.target.value)}>
              <option value="" disabled>Knotentyp</option>
              {kinds.map(kind => <option key={kind} value={kind}>{names[kind]}</option>)}
            </select>
            <button type="button" disabled={!kind || count >= maxNodes || isFormSourceLimit} onClick={add}>+ Knoten</button>
          </div>
        </div>
        <div className="canvas" ref={canvas}>
          {active && <ReactFlow key={workspace} nodes={nodes} edges={edges} nodeTypes={nodeTypes} onInit={setFlow}
            fitView fitViewOptions={{ padding: 0.15, maxZoom: 1.1 }} minZoom={0.13} maxZoom={2}
            onNodeDrag={onDrag} onNodeDragStop={onDragStop} deleteKeyCode={null} nodesConnectable
            onNodeClick={(_, node) => { setSelected(node.id); setSelectedWire(null) }}
            onEdgeClick={(_, edge) => { setSelectedWire(edge.id); setSelected(edge.target) }}
            onPaneClick={() => { setSelected(null); setSelectedWire(null) }}
            isValidConnection={compatible} onConnect={wire => setWire(wire.target, wire.targetHandle, wire.source)}
            selectionKeyCode={null} multiSelectionKeyCode={null} onlyRenderVisibleElements
            colorMode="dark" aria-label="React-Flow-Graph">
            <Background gap={22} size={1} color="#46606d" />
            <Controls showInteractive={false} />
            <MiniMap pannable zoomable nodeColor="#659a9c" maskColor="#18232c88" />
          </ReactFlow>}
        </div>
        <div className={`canvas-footer ${message?.error ? 'error' : ''}`} role="status">
          {message?.text ?? 'Knoten ziehen · Anschlüsse verbinden · Entf löscht die Auswahl'}
        </div>
      </section>
      <div className="side">
        {active && <Suspense fallback={<section className="preview-panel"><div className="preview-meta">3D-Vorschau wird geladen …</div></section>}>
          <Preview mode={mode} document={document} library={library} selected={previewSelected} />
        </Suspense>}
        <section className="inspector" aria-label="Knoteninspektor">
          <div className="panel-heading"><span>Inspektor</span><small>{current ? 'AUSWAHL' : 'ERGEBNIS'}</small></div>
          <div className="inspector-body" key={visibleNode.node.id}>
            <h2>{visibleNode.node.name || names[visibleNode.node.kind]}</h2>
            <div className="node-id">{visibleNode.node.id}</div>
            {visibleNode.ports.length > 0 && <details open><summary>Verbindungen</summary>
              {visibleNode.ports.map(port => <Field key={port.id} label={portName(port, visibleNode.node)}
                value={port.source} options={[[ '', 'Offen' ], ...entries.filter(({ node }) =>
                  port.accepts.includes(node.kind) && node.id !== visibleNode.node.id)
                  .map(({ node }) => [node.id, node.name || `${names[node.kind]} · ${node.id}`])]}
                onChange={source => setWire(visibleNode.node.id, port.id, source)} />)}
              {portLimits && portLimits.min !== portLimits.max && <div className="button-row">
                <button type="button" disabled={visibleNode.node.inputs.length >= portLimits.max}
                  onClick={() => patchNode({ inputs: [...visibleNode.node.inputs, ''] })}>+ Eingang</button>
                <button type="button" disabled={visibleNode.node.inputs.length <= portLimits.min}
                  onClick={() => patchNode({ inputs: visibleNode.node.inputs.slice(0, -1),
                    ...(mode === 'form' ? { inputGroups: visibleNode.node.inputGroups.slice(0, visibleNode.node.inputs.length - 1) } : {})
                  })}>Letzten Eingang entfernen</button>
              </div>}
            </details>}
            <NodeFields {...{ mode, node: visibleNode.node, document, edit: patchNode, library }} />
            {(selectedWire || current && !protectedNode(current.node)) &&
              <button className="danger" type="button" onClick={removeSelection}>{selectedWire ? 'Verbindung trennen' : 'Knoten löschen'}</button>}
            <DocumentFields {...{ mode, document, library }} edit={next => transact(() => commit(next))} />
          </div>
        </section>
      </div>
    </div>
  </div>
}

function App() {
  const [mode, setMode] = useState(initialMode)
  useEffect(() => {
    const url = new URL(window.location.href)
    url.searchParams.delete('graph')
    url.searchParams.set('editor', mode)
    window.history.replaceState(null, '', url)
  }, [mode])
  return <>
    <header className="page-header">
      <div className="brand-mark" aria-hidden="true">⬡</div>
      <div className="brand"><span>COPYCAT ARCHITECT</span><h1>Graphstudio</h1></div>
      <nav aria-label="Graph auswählen">
        <button type="button" aria-pressed={mode === 'form'} onClick={() => setMode('form')}>Formgraph</button>
        <button type="button" aria-pressed={mode === 'building'} onClick={() => setMode('building')}>Gebäudegraph</button>
      </nav>
      <a href={wiki}>Zum Wiki ↗</a>
    </header>
    <main>
      <Studio mode="form" active={mode === 'form'} />
      <Studio mode="building" active={mode === 'building'} />
    </main>
  </>
}

createRoot(document.getElementById('root')).render(<App />)
