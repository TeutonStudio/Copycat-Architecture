import { nodeChoiceMeta } from './choice-icons.js'
import { IconSelect } from './IconSelect.jsx'
import {changeOperator} from './operators.js'
import {OperatorFields} from './OperatorChoice.jsx'
import {acceptsValue} from './value-compatibility.js'
import {configureDocument} from './node-controls.js'
import { selectNodes,clickNodes } from './selection.js'
import { groupViews,viewWires,moveViewNodes,resolveViewConnection,insertSavedReference,splitVector } from './reference-groups.js'
import { CopycatTransforms } from './CopycatTransforms.jsx'
import { FileDialog, JsonDialog } from './FileDialog.jsx'
import { appendVersion, identityOf, documentName, renameDocument, validDocumentName } from './documents.js'
import { duplicateNodes, insertFormReference, savedReferences, viewLayout, versionResources } from './studio-actions.js'
import { portStyle, portStyles } from './port-styles.js'
import { lazy, memo, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Background, Controls, MiniMap, ReactFlow, useUpdateNodeInternals } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { editNativeGroup, previewGraph, adaptRectangles, newNode, starterBuilding } from './generated/engine.mjs'
import { promoteNumbers, applyWires, connections, formGroups, inspect, editNode, model, position, protectedNode, readGraph, replaceNodes, validateLibrary, writeGraph } from './model.js'
import { NativeDocumentFields } from './NativeFields.jsx'
import { DocumentFields, Field, names, NodeFields, portName } from './Inspector.jsx'
import './style.css'
import { SplitHandle } from './SplitHandle.jsx'
import { SidePanels } from './SidePanels.jsx'
import { ArrangementPanel } from './ArrangementGrid.jsx'
import { authoringBuildingNode } from './generated/engine.mjs'
import { Workspace, useWorkspace } from './Workspace.jsx'
import { loadBuildingCopycat, loadFormCopycatWithMaterials } from './copycat-library.js'
import { applyCopycatOption } from './copycat-selection.js'
import { insertNodeGroup, mergeLibraries, saveNodeGroup } from './node-groups.js'
import baseLibrary from '../../examples/library.json'
import formExample from '../../examples/nodegraph-form.json'
import formRotation from '../../examples/nodegraph-rotation-xor.json'
import formMaterials from '../../examples/nodegraph-material-groups.json'
import buildingExample from '../../examples/building-graph.json'
import buildingAssembly from '../../examples/building-graph-assembly.json'
import buildingBasements from '../../examples/building-graph-basements.json'
import buildingRotationSegment from '../../examples/building-rotation-segment.json'
import buildingRoofPeak from '../../examples/building-roof-peak.json'
import buildingRoofEave from '../../examples/building-roof-eave.json'

import { cardResult,cardTypeLabel } from './card-types.js'
import {GroupParameters} from './GroupParameters.jsx'
import { newCard, openCard, graphCardEntry, copycatCardEntry } from './cards.js'
import { AreaPreview } from './AreaPreview.jsx'

// Keep the picker in the main bundle: switching dialogs must not depend on a stale deployment chunk.
import CopycatPicker from './CopycatPicker.jsx'
import { SelectionBoundary } from './SelectionBoundary.jsx'
import { NodeDialog } from './NodeDialog.jsx'
import { NodeAppearance } from './NodeAppearance.jsx'
import {operatorFamily,switchOperator} from './operator-families.js'
import { miniMapNodeColor } from './node-catalog.js'
const Preview = lazy(() => import('./Preview.jsx'))
const wiki = 'https://github.com/TeutonStudio/copycat-architect/wiki'
const examples = {
  form: [
    ['Aussparung', formExample], ['Rotation und Differenz', formRotation], ['Materialgruppen', formMaterials]
  ],
  building: [
    ['Aussparung',formExample], ['Rotation und Differenz',formRotation], ['Materialgruppen',formMaterials], ['Gebäude aus Copycats', buildingAssembly], ['Rotationssegment · Säule', buildingRotationSegment], ['Dachspitze bei ungerader Breite', buildingRoofPeak], ['Dachendkante parallel zum First', buildingRoofEave], ['L-förmiges Gebäude', buildingExample], ['Gebäude mit Kellern', buildingBasements]
  ]
}
const query = new URLSearchParams(window.location.search)
const initialMode = query.get('editor') === 'form' || query.get('graph') === 'copycat' ? 'form' : 'building'

function DiagramNode({id,data,selected}) {
  const updateInternals = useUpdateNodeInternals()
  const signature = data.ports.map(port => `${port.id}:${port.source || ''}`).join('|')+';'+(data.outputs || []).map(output=>output.id).join('|')
  useEffect(() => updateInternals(id), [id, signature, updateInternals])
  return <NodeAppearance data={data} selected={selected} />
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
  return { ...state, push, undo, redo, reset:next => setState({current:structuredClone(next),past:[],future:[]}) }
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
  const { data: savedData, update: updateWorkspace } = useWorkspace()
  const draft = savedData.drafts.graph || savedData.drafts[mode]
  const history = useHistory(draft ? openCard(draft,baseLibrary).document : newCard())
  const [fileIdentity,setFileIdentity] = useState(draft?.fileIdentity || null)
  const [draftIdentity,setDraftIdentity] = useState(draft?.fileIdentity || null)
  const [viewVersion,setViewVersion] = useState(null), [versionLayouts,setVersionLayouts] = useState({})
  const [draftLibrary,setLibrary] = useState(draft ? openCard(draft,baseLibrary).library : baseLibrary)
  const [draftMaterials,setMaterials] = useState(draft?.materials ?? {})
  const rawViewedEntry = [...savedData.graphs.map(e=>({...e,collection:"graphs"})),...savedData.nodeGroups.map(e=>({...e,collection:"nodeGroups"}))].find(entry => entry.id === viewVersion)
  const viewedEntry = useMemo(()=>rawViewedEntry?graphCardEntry(rawViewedEntry,baseLibrary):null,[JSON.stringify(rawViewedEntry)])
  const readOnly = Boolean(viewedEntry)
  const document = useMemo(() => viewedEntry ? {...viewLayout(readGraph(viewedEntry.document,mode),versionLayouts[viewedEntry.id]),name:documentName(savedData,fileIdentity,viewedEntry.name)} : {...history.current,name:documentName(savedData,draftIdentity,history.current.name)},[viewedEntry,versionLayouts,history.current,mode,savedData.documentNames,savedData.graphs,savedData.nodeGroups,fileIdentity,draftIdentity])
  const {library,materials} = versionResources(viewedEntry,draftLibrary,draftMaterials,baseLibrary)
  const versions = [...savedData.graphs,...savedData.nodeGroups].filter(entry => identityOf(entry) === fileIdentity).sort((a,b)=>(a.version || 1)-(b.version || 1))
  useEffect(()=>{if(viewVersion && !viewedEntry) {setViewVersion(null);setFileIdentity(draftIdentity)}},[viewVersion,viewedEntry,draftIdentity])
  const [renaming,setRenaming] = useState(false), [nameDraft,setNameDraft] = useState(''), [groupName,setGroupName] = useState('Neue Gruppe')
  // A source change also changes material mappings and library snapshots.
  const historyContext = useRef(new WeakMap())
  const [selectedIds, setSelectedIds] = useState([])
  const [saveName, setSaveName] = useState(null)
  const pickerOrigin=useRef(null)
  const [referenceDialog,setReferenceDialog]=useState(false),[referenceTarget,setReferenceTarget]=useState(null),[groupPreview,setGroupPreview]=useState({})
  const [fileDialog,setFileDialog] = useState(false), [jsonDialog,setJsonDialog] = useState(false)
  const [contextMenu,setContextMenu] = useState(null)
  useEffect(() => {
    updateWorkspace(data => {
      const previous = data.drafts[mode], document = history.current
      if(previous?.document === document && previous?.library === draftLibrary && previous?.materials === draftMaterials && previous?.fileIdentity === draftIdentity) return data
      const entry={mode,document,library:draftLibrary,materials:draftMaterials,fileIdentity:draftIdentity,references:savedReferences(document)}
      return {...data,drafts:{...data.drafts,[mode]:entry,[`${mode}:${draftIdentity || '_new'}`]:entry}}
    })
  }, [mode, history.current, draftLibrary, draftMaterials, draftIdentity])
  const [diagnostics,setDiagnostics] = useState([])
  const [selected, setSelected] = useState(null)
  const [selectedWire, setSelectedWire] = useState(null)
  const [dragging, setDragging] = useState({})
  const [message, setMessage] = useState(null)
  const [flow, setFlow] = useState(null)
  const [nodeDialog, setNodeDialog] = useState(false)
  const [workspace, setWorkspace] = useState(0)
  // React Flow measurements belong to the view, never to the Minecraft document.
  const [nodeMeasurements, setNodeMeasurements] = useState({})
  const [graphShare, setGraphShare] = useState(68)
  const [arrangementCollapsed,setArrangementCollapsed] = useState(false)
  const [arrangementDraft,setArrangementDraft] = useState(null)
  const [previewCollapsed, setPreviewCollapsed] = useState(false)
  const [inspectorCollapsed, setInspectorCollapsed] = useState(false)
  const [previewMode, setPreviewMode] = useState('graph')
  const [copycatPicker, setCopycatPicker] = useState(null)
  const graphInput = useRef(null)
  const libraryInput = useRef(null)
  const copycatInput = useRef(null)
  const canvas = useRef(null)
  const entries = useMemo(() => inspect(mode, document), [mode, document])
  const wires = useMemo(() => connections(entries), [entries])
  const foldedGroups=useMemo(()=>groupViews(document,entries),[document,entries])
  const selectedGroup=foldedGroups.find(group=>group.id===selected)
  const current = entries.find(({ node }) => node.id === (selectedGroup ? groupPreview[selectedGroup.id] || selectedGroup.previewId : selected))
  const resultNode = entries.find(({node})=>node.id===cardResult(document).id)
  const visibleNode = current ?? resultNode
  const kinds = mode === 'form' ? model.form.map(type => type.kind).filter(kind => kind !== 'OUTPUT' && !['POSITION_Y','POSITION_Z'].includes(kind)) : model.building.filter(kind => !['RESULT','POSITION_Y','POSITION_Z'].includes(kind) && !(model.building.includes('SERVICE_SHAFT_SEGMENT') && ['SERVICE_SHAFT','ELEVATOR_SHAFT'].includes(kind)) && !(model.directAreas && ['SEGMENT_LAYOUT','ROOM_GEOMETRY','ROOF_LEVEL','ROOF_LAYOUT'].includes(kind)))
  const edgeId = ({ target, port }) => `${target}/${port}`
  const nodes = useMemo(() => {
    const hidden=new Set(foldedGroups.flatMap(group=>group.nodes))
    const ordinary=entries.filter(data=>!hidden.has(data.node.id)).map(data=>({
      id:data.node.id,type:'architect',data:{...data,mode,onInputOrder:readOnly?undefined:order=>transact(()=>commit(configureDocument(mode,document,data.node.id,'$inputOrder',order))),onControl:readOnly?undefined:(path,value)=>transact(()=>commit(configureDocument(mode,document,data.node.id,path,value))),onOperation:readOnly?undefined:kind=>model.operators?.[data.node.kind]?transact(()=>commit(changeOperator(mode,document,data.node.id,kind))):changeOperation(data.node.id,kind),reference:document.graph.nodes.find(node=>node.id===data.node.id)?.libraryReference,diagnostic:diagnostics.find(item=>item.nodeId===data.node.id)?.message,hasOutput:data.node.id!==resultNode.node.id,groupName:document.graph.nodeGroups?.find(group=>group.nodes.includes(data.node.id))?.name},position:dragging[data.node.id] || {x:data.node.x,y:data.node.y},selected:selectedIds.includes(data.node.id),measured:nodeMeasurements[data.node.id]
    }))
    const grouped=foldedGroups.map(group=>({id:group.id,type:'architect',position:dragging[group.id] || group.position,selected:selectedIds.includes(group.id),measured:nodeMeasurements[group.id],data:{node:{id:group.id,kind:group.presentation==='vectorSplit'?'VECTOR_SPLIT':'REFERENCE',name:group.name},mode,onOperation:group.presentation==='vectorSplit'&&!readOnly?kind=>changeOperation(group.id,kind):undefined,ports:group.ports.map(port=>({...port,name:port.name || portName({...port,id:port.targetPort},entries.find(e=>e.node.id===port.targetNode).node)})),outputs:group.outputs,outputType:group.outputs[0]?.type,hasOutput:group.outputs.length>0,reference:group.libraryReference,diagnostic:diagnostics.find(item=>group.nodes.includes(item.nodeId))?.message}}))
    return [...ordinary,...grouped]
  },[entries,document,foldedGroups,dragging,selectedIds,nodeMeasurements,diagnostics,readOnly,mode])
  const onNodesChange = useCallback(changes => {
    if(changes.some(change=>change.type==='select')) setSelectedIds(previous=>selectNodes(previous,changes))
    const measured = changes.filter(change => change.type === 'dimensions' && change.dimensions)
    if (!measured.length) return
    setNodeMeasurements(previous => {
      const ids = new Set([...entries.map(({node})=>node.id),...foldedGroups.map(group=>group.id)])
      let changed = false
      const next = Object.fromEntries(Object.entries(previous).filter(([id]) => ids.has(id)))
      changed = Object.keys(next).length !== Object.keys(previous).length
      for (const { id, dimensions } of measured) {
        if (!ids.has(id)) continue
        if (next[id]?.width !== dimensions.width || next[id]?.height !== dimensions.height) {
          next[id] = dimensions; changed = true
        }
      }
      return changed ? next : previous
    })
  }, [entries,foldedGroups])
  const edges = useMemo(() => viewWires(wires,foldedGroups).map(wire => ({
    id: edgeId(wire), source: wire.viewSource, sourceHandle: wire.sourceHandle, target: wire.viewTarget, targetHandle: wire.targetHandle,
    style: wire.source === 'empty_wall' ? { ...edgeStyle, opacity: 0.38 } : edgeStyle,
    selected: edgeId(wire) === selectedWire
  })), [wires, foldedGroups, selectedWire])

  const transact = callback => {
    try {
      setMessage(null)
      callback()
    } catch (error) {
      setMessage({ error: true, text: error.message || String(error) })
    }
  }
  const commit = next => {
    if (readOnly) throw new Error('Gespeicherte Version: nur Betrachten. Zum Bearbeiten Ungespeichert wählen.')
    const nextEntries=inspect(mode,next),nextWires=connections(nextEntries)
    const removed=wires.filter(wire=>!nextWires.some(w=>w.source===wire.source&&w.target===wire.target&&w.port===wire.port)&&nextEntries.some(e=>e.node.id===wire.source))
    if(removed.length)setMessage({text:`${removed.length} inkompatible Verbindung${removed.length===1?' wurde':'en wurden'} wegen des Typwechsels getrennt. Rückgängig stellt sie wieder her.`})
    if(next.name !== document.name && draftIdentity) updateWorkspace(data=>renameDocument(data,draftIdentity,next.name))
    if (JSON.stringify(next) !== JSON.stringify(document)) {
      historyContext.current.set(history.current, { library, materials })
      // Reopening the same example must create a distinct history entry.
      history.push(structuredClone(next))
    }
  }
  const travelHistory = direction => {
    if (readOnly) return
    const next = direction === 'undo' ? history.past.at(-1) : history.future[0]
    if (!next) return
    historyContext.current.set(history.current, { library, materials })
    const context = historyContext.current.get(next)
    if (context) { setLibrary(context.library); setMaterials(context.materials) }
    history[direction]();setSelectedIds(ids=>ids.filter(id=>next.graph.nodes.some(node=>node.id === id)));setSelected(id=>next.graph.nodes.some(node=>node.id === id) ? id : null)
  }
  const patchNode = patch => transact(() => {
    const { node } = visibleNode
    let updated = { ...document.graph.nodes.find(item => item.id === node.id), ...patch }
    if(visibleNode.node.kind==='RESULT')updated.kind='RESULT'
    commit(editNode(mode, document, updated))
  })
  function changeOperation(id,kind) {transact(()=>commit(switchOperator(mode,document,id,kind)))}
  const setWire = (target, port, source) => transact(() => {
    const rest = wires.filter(wire => wire.target !== target || wire.port !== port)
    commit(applyWires(mode, document, entries, source ? [...rest, { source, target, port }] : rest))
    setSelectedWire(null)
  })
  const memberIds=(selectedIds.length?selectedIds:selected?[selected]:[]).flatMap(id=>foldedGroups.find(group=>group.id===id)?.nodes || [id])
  const editableIds=[...new Set(memberIds)].filter(id=>id!==resultNode.node.id && id!=='empty_wall' && document.graph.nodes.some(node=>node.id===id))
  const removeSelection = () => {
    if(readOnly) return
    const wire = selectedIds.length < 2 && wires.find(wire => edgeId(wire) === selectedWire)
    if(wire) {const port=foldedGroups.flatMap(group=>group.ports).find(port=>port.targets?.some(target=>target.target===wire.target && target.port===wire.port));if(port) transact(()=>commit(applyWires(mode,document,entries,wires.filter(wire=>!port.targets.some(target=>target.target===wire.target && target.port===wire.port)))));else setWire(wire.target,wire.port,'')}
    else if(editableIds.length) transact(() => {
      const removed=new Set(editableIds)
      commit(applyWires(mode,document,entries,wires.filter(wire=>!removed.has(wire.source) && !removed.has(wire.target)),editableIds))
      setSelected(null);setSelectedIds([]);setSelectedWire(null)
    })
  }
  const open = raw => {
    setPreviewMode('graph')
    setFileIdentity(null);setDraftIdentity(null);setSaveName(null);setFileDialog(false)
    const opened=openCard({document:raw},baseLibrary)
    setViewVersion(null);history.reset(opened.document);setLibrary(opened.library);setMaterials(opened.materials)
    setSelected(null)
    setSelectedWire(null)
    setSelectedIds([])
    setNodeMeasurements({})
    setWorkspace(key => key + 1)
  }
  const add = (choice,screenPoint) => transact(() => {
    const kind=typeof choice==='string'?choice:choice.kind
    if (!kind || readOnly || count >= maxNodes) return
    setNodeDialog(false)
    const { left, top, width, height } = canvas.current.getBoundingClientRect()
    const center = flow?.screenToFlowPosition(screenPoint || { x: left + width * 0.5, y: top + height * 0.4 }) ?? { x: 50, y: 50 }
    pickerOrigin.current=null
    if(mode==='building' && ['COPYCAT','GRAPH_REFERENCE'].includes(kind)) {pickerOrigin.current=center;setReferenceTarget(null);setReferenceDialog(true);return}
    if(kind==='POSITION_X') {const result=splitVector(mode,document,null,{x:position(center.x),y:position(center.y)});commit(result.document);setSelected(result.nodeId);setSelectedIds([result.nodeId]);return}
    const id = `node_${crypto.randomUUID().slice(0, 13)}`
    const created = newNode(mode === 'building', kind, id, position(center.x), position(center.y), true)
    const node = {...(mode==='building'?authoringBuildingNode(created):created),...(typeof choice==='object'?choice:{}),id,x:position(center.x),y:position(center.y)}
    if(kind==='GROUP_INPUT'&&(!node.name||node.name==='Eingang'))node.name=`Eingang ${document.graph.nodes.filter(n=>n.kind==='GROUP_INPUT').length+1}`
    if (kind === 'INPUT') node.part.group = formGroups(document)[0].id
    const target=operatorFamily(mode,kind)?.id==='numbers'?promoteNumbers(mode,document):document
    commit(replaceNodes(target, [...target.graph.nodes, node]))
    setSelected(id)
    if(kind==='AREA') setPreviewMode('selected')
    setSelectedIds([id])
    setSelectedWire(null)
  })
  const onDrag = useCallback((_, node, moved) => setDragging(Object.fromEntries((moved?.length ? moved : [node]).map(item=>[item.id,item.position]))), [])
  const onDragStop = (_, node, moved) => {
    const positions=moveViewNodes(document,foldedGroups,moved?.length?moved:[node])
    setDragging({})
    if(readOnly) {setVersionLayouts(current=>({...current,[viewedEntry.id]:{...current[viewedEntry.id],...positions}}));return}
    transact(() => commit(replaceNodes(document,document.graph.nodes.map(item=>positions[item.id] ? {...item,...positions[item.id]} : item))))
  }
  const compatible = connection => {
    const resolved=resolveViewConnection(connection,foldedGroups)
    const targets=resolved.targets || [{target:resolved.target,port:resolved.port}]
    const source=entries.find(entry=>entry.node.id===resolved.source)
    return targets.length>0 && targets.every(target=>resolved.source!==target.target && acceptsValue(entries.find(entry=>entry.node.id===target.target)?.ports.find(port=>port.id===target.port),source))
  }
  const connectView = connection => transact(()=>{
    const resolved=resolveViewConnection(connection,foldedGroups),targets=resolved.targets || [{target:resolved.target,port:resolved.port}]
    const rest=wires.filter(wire=>!targets.some(target=>target.target===wire.target && target.port===wire.port))
    commit(applyWires(mode,document,entries,[...rest,...targets.map(target=>({...target,source:resolved.source}))]));setSelectedWire(null)
  })
  const importFile = async (event, isLibrary) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    try {
      if (file.size > 16_000_000) throw new Error('Die JSON-Datei ist größer als 16 MB.')
      const raw = JSON.parse(await file.text())
      if (isLibrary) {
        const imported = validateLibrary(raw)
        setLibrary({ forms: { ...baseLibrary.forms, ...imported.forms }, modules: { ...baseLibrary.modules, ...imported.modules },graphs:{...baseLibrary.graphs,...imported.graphs} })
      }
      else open(raw)
      setMessage({ text: `${file.name} geöffnet` })
    } catch (error) {
      setMessage({ text: error.message, error: true })
    }
  }

  const loadCopycat = (entry, nodeId = visibleNode.node.kind === 'COPYCAT' || visibleNode.node.kind === 'SAVED' ? visibleNode.node.id : undefined) => {
    if (mode === 'building') {
      const result = loadBuildingCopycat(document, library, entry, nodeId)
      commit(result.document); setLibrary(result.library); setSelected(result.nodeId)
    } else {
      const result = loadFormCopycatWithMaterials(document, entry, nodeId, materials)
      commit(result.document); setMaterials(result.materials); setSelected(result.nodeId)
    }
  }
  const savedForms = useMemo(() => savedData.graphs.filter(entry => entry.mode === 'form' || cardResult(entry.document)?.resultType==='COPYCAT').flatMap(entry=>{try{return [copycatCardEntry(entry,baseLibrary)]}catch{return []}}), [savedData.graphs])
  const chooseCopycat = (nodeId,basic=false) => {
    if(nodeId) pickerOrigin.current=null
    if(mode==='building' && !basic) {setReferenceTarget(nodeId || null);setReferenceDialog(true);return}
    if (readOnly) return
    setFileDialog(false)
    const node = entries.find(entry => entry.node.id === nodeId)?.node
    const initialId = node?.inlinePart ? `base:${node.inlinePart.source}` : node?.kind === 'INPUT' ? `base:${node.part.source}` : node?.kind === 'COPYCAT' ? `library:${node.form}` : null
    setCopycatPicker({ nodeId, initialId: basic && !node?.inlinePart && node?.kind !== 'INPUT' ? null : initialId, initialCategory: basic ? 'basic' : node && node.kind !== 'INPUT' && !node.inlinePart ? 'saved' : 'basic' })
  }
  const applyCopycat = option => {
    const result = applyCopycatOption({ mode, document, library, materials, nodeId: copycatPicker.nodeId, option })
    locatePickerResult(result)
    const next={...result.document,graph:{...result.document.graph,nodes:result.document.graph.nodes.map(node=>{if(node.id !== result.nodeId) return node;const {libraryReference,...copy}=node;return copy})}}
    commit(next)
    setLibrary(result.library); setMaterials(result.materials); setSelected(result.nodeId); setSelectedIds([result.nodeId])
    setSelectedWire(null); setMessage(null)
  }
  const count = entries.length
  const maxNodes = mode === 'form' ? model.formNodes : model.buildingNodes
  const arrangementNode=mode==='building' && current && !selectedGroup && (['PATTERN','STOREY'].includes(current.node.kind) || ['SEGMENT_UNION','SEGMENT_INTERSECTION','SEGMENT_DIFFERENCE','SEGMENT_SYMMETRIC_DIFFERENCE','SEGMENT_ROTATE','SEGMENT_MIRROR','SEGMENT_TRANSFORM'].includes(current.node.kind)) ? current.node : null
  useEffect(()=>{if(arrangementNode?.kind==='STOREY')setPreviewMode('selected')},[arrangementNode?.id,arrangementNode?.kind])
  const onArrangementPreview=useCallback(patch=>setArrangementDraft(patch?{id:arrangementNode?.id,patch}:null),[arrangementNode?.id])
  const previewDocument=useMemo(()=>{
    if(!arrangementDraft || arrangementDraft.id!==arrangementNode?.id) return document
    try {return editNode(mode,document,{...arrangementNode,...arrangementDraft.patch})} catch {return document}
  },[document,arrangementDraft,arrangementNode,mode])
  const removableWire=selectedIds.length<2 && wires.some(wire=>edgeId(wire)===selectedWire)
  const canRemove=!readOnly && (removableWire || editableIds.length>0)
  const removeLabel=removableWire?'Verbindung trennen':selectedIds.length>1?'Auswahl löschen':selectedGroup?'Referenzknoten löschen':'Knoten löschen'
  const previewSelected = current?.node.id ?? ''
  const areaSource=n=>n?.resultType==='AREA'?(n.kind==='RESULT'?(n.resultMode==='VALUE'?n.resultSource:n.id):n.inputs[0]):n?.kind==='AREA'?n.id:null
  const areaTarget=areaSource(previewMode==='selected'?current?.node:cardResult(document))
  const areaPreviewNode=entries.find(entry=>entry.node.id===areaTarget)?.node
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
  const duplicate = (withConnections=true) => transact(() => {
    if (readOnly || !editableIds.length) return
    const copies=editableIds.map(() => crypto.randomUUID())
    const next=duplicateNodes(mode,document,editableIds,copies,withConnections), views=groupViews(next,inspect(mode,next)), owners=new Map(views.flatMap(group=>group.nodes.map(id=>[id,group.id]))), selection=[...new Set(copies.map(id=>owners.get(id) || id))];commit(next);setSelectedIds(selection);setSelected(selection[0]);setContextMenu(null)
  })
  const makeGroup = () => transact(() => {
    if(readOnly || !editableIds.length) return
    commit({...document,graph:editNativeGroup(mode === 'building',document,'create','',groupName,editableIds)})
  })
  const saveGroup = () => transact(() => {
    if(readOnly || !editableIds.length) return
    const entry=saveNodeGroup(mode,document,editableIds,groupName,materials,library)
    if(entry.nodes.length === 1) {try {entry.preview=previewGraph(mode === 'building',document,library,entry.nodes[0].id,materials);if(mode === 'form') entry.preview.meshes=entry.preview.meshes.map(mesh=>({...mesh,material:materials[mesh.material] || mesh.material}))} catch {}}
    const existing=savedData.nodeGroups.find(item=>item.mode === mode && item.name === entry.name)
    updateWorkspace(data=>({...data,nodeGroups:appendVersion(data.nodeGroups,{...entry,document},existing ? identityOf(existing) : null)}))
    setMessage({text:'Knotengruppe in der Bibliothek gespeichert.'})
  })
  const save = (asNew = false) => transact(() => {
    if(readOnly && !asNew) return
    const name=validDocumentName(saveName?.trim() || document.name || 'Graph')
    if(savedData.graphs.length >= 500) throw new Error('Höchstens 500 Speicherstände. Nicht benötigte Versionen zuerst löschen.')
    const identity=asNew ? null : fileIdentity
    const collection=!asNew && savedData.nodeGroups.some(e=>identityOf(e)===fileIdentity)?"nodeGroups":"graphs"
    let payload=collection==="nodeGroups" && !document.graph.nodes.some(n=>n.kind==='GROUP_INPUT')?{...saveNodeGroup(mode,document,document.graph.nodes.filter(n=>!protectedNode(n,document)).map(n=>n.id),name,materials,library),graphInterface:document.cardInterface}:null
    if(payload) {const result=cardResult(document);payload.graphInterface={inputs:document.cardInterface?.inputs || [],outputs:[{id:'result',name:'Ergebnis',type:result.resultType,member:payload.nodes.findIndex(n=>n.id===result.inputs[0])}]}}
    const next=appendVersion(savedData[collection],{...payload,name,mode,isGroup:document.graph.nodes.some(n=>n.kind==='GROUP_INPUT'),document:writeGraph(mode,{...document,name}),library,materials,references:savedReferences(document)},identity)
    const proposed=next.at(-1)
    const accepted=updateWorkspace(data => renameDocument({...data,[collection]:appendVersion(data[collection],{...proposed},identity || proposed.documentId)},identity || proposed.documentId,name))
    const entry=accepted[collection].at(-1)
    if(readOnly) {history.reset({...document,name});setLibrary(library);setMaterials(materials);setViewVersion(null)}
    else if(history.current.name !== name) history.push({...document,name})
    setSaveName(null)
    setFileIdentity(entry.documentId);setDraftIdentity(entry.documentId);setMessage({text:`${name} · Version ${entry.version} gespeichert.`})
  })
  const openEntry = entry => transact(() => {
    setPreviewMode('graph')
    graphCardEntry(entry,baseLibrary)
    const identity=identityOf(entry)
    setViewVersion(entry.id);setFileIdentity(identity);setSaveName(null);setSelected(null);setSelectedIds([]);setSelectedWire(null);setWorkspace(key=>key+1)
    setFileDialog(false)
  })
  const selectVersion = id => {
    if(!id && fileIdentity !== draftIdentity) {
      const parked=savedData.drafts[`${mode}:${fileIdentity || '_new'}`], baseline=viewedEntry || versions.at(-1)
      if(parked || baseline) {
        history.reset(parked?.document || graphCardEntry(baseline,baseLibrary).document);setLibrary(parked?.library || baseline?.library || baseLibrary);setMaterials(parked?.materials || baseline?.materials || {});setDraftIdentity(fileIdentity)
      }
    }
    setViewVersion(id || null);setSaveName(null);setRenaming(false);setSelectedWire(null);setContextMenu(null)
  }
  const locatePickerResult = result => {
    const origin=pickerOrigin.current
    if(!origin) return
    const next=result.document
    const groups=groupViews(next,inspect(mode,next))
    const moved=moveViewNodes(next,groups,[{id:result.nodeId,position:origin}])
    result.document=replaceNodes(next,next.graph.nodes.map(node=>moved[node.id]?{...node,...moved[node.id]}:node))
    pickerOrigin.current=null
  }
  const insertReference = entry => transact(() => {
    if(readOnly) return
    const result=insertSavedReference({mode,document,library,materials,entry,currentIdentity:fileIdentity,targetId:referenceDialog?referenceTarget:null})
    locatePickerResult(result)
    commit(result.document);setLibrary(result.library);setMaterials(result.materials);setSelected(result.nodeId);setSelectedIds([result.nodeId]);setSelectedWire(null);setFileDialog(false);setReferenceDialog(false);setReferenceTarget(null);setWorkspace(key=>key+1)
  })
  const splitPosition=()=>transact(()=>{const result=splitVector(mode,document,visibleNode.node.id);commit(result.document);setSelected(result.nodeId);setSelectedIds([result.nodeId])})
  const unpackReference=()=>transact(()=>{commit({...document,graph:{...document.graph,nodeGroups:document.graph.nodeGroups.map(group=>group.id===selectedGroup.id?{...group,libraryReference:null,presentation:null}:group)}});setSelected(null);setSelectedIds([])})
  const renameTo = value => {
    const name=validDocumentName(value)
    if(fileIdentity) updateWorkspace(data=>renameDocument(data,fileIdentity,name))
    else history.push({...document,name})
    setSaveName(null);setRenaming(false)
  }
  const rename = () => transact(() => renameTo(nameDraft))
  const keydown = event => {
    if (event.target.closest('input, textarea, select, [role=combobox], [role=listbox], [contenteditable=true]')) return
    if(readOnly) {if(['Delete','Backspace'].includes(event.key) || (event.ctrlKey || event.metaKey) && ['d','z','y'].includes(event.key.toLowerCase())) event.preventDefault();return}
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd') { event.preventDefault(); duplicate(); return }
    if ((event.ctrlKey || event.metaKey) && ['z', 'y'].includes(event.key.toLowerCase())) {
      event.preventDefault()
      if (event.shiftKey || event.key.toLowerCase() === 'y') travelHistory('redo')
      else travelHistory('undo')
    }
    if (['Delete', 'Backspace'].includes(event.key) && (selected || selectedWire || selectedIds.length)) {
      event.preventDefault()
      removeSelection()
    }
  }
  useEffect(() => {
    if(!active) return
    const handler=event=>{if(window.document.querySelector('dialog[open],.library-overlay,[role="dialog"]')) return;keydown(event)}
    window.addEventListener('keydown',handler)
    return ()=>window.removeEventListener('keydown',handler)
  },[active,keydown])
  return <div className="studio" hidden={!active}>
    {nodeDialog && <NodeDialog mode={mode} onPlace={(kind,point)=>{
      const rect=canvas.current.getBoundingClientRect()
      if(point.x<rect.left || point.x>rect.right || point.y<rect.top || point.y>rect.bottom || readOnly || count>=maxNodes) return false
      add(kind,point);return true
    }} properties={model.nodeProperties[mode]} kinds={kinds} count={count} maxNodes={maxNodes} onAdd={add} onClose={()=>setNodeDialog(false)} />}
    {referenceDialog && <FileDialog mode={mode} library={library} referenceOnly currentIdentity={fileIdentity} readOnly={readOnly} onChoose={insertReference} onReference={insertReference} onClose={()=>{pickerOrigin.current=null;setReferenceDialog(false)}} actions={!foldedGroups.some(group=>group.id===referenceTarget) && <button onClick={()=>transact(()=>{chooseCopycat(referenceTarget,true);setReferenceDialog(false)})}>Grundform wählen …</button>} />}
    {fileDialog && <FileDialog mode={mode} library={library} currentIdentity={fileIdentity} readOnly={readOnly} onChoose={openEntry} onReference={insertReference} onClose={() => setFileDialog(false)} actions={<>    <div className="toolbar">
      <label className="example-select"><span>BEISPIELE</span><IconSelect domain="examples" aria-label="Beispiel öffnen" value="" onChange={e => transact(() => open(examples[mode][Number(e.target.value)][1]))}>
        <option value="" disabled iconMeta={{placeholder:true}}>Beispiel öffnen</option>
        {examples[mode].map(([name], i) => <option key={name} value={i}>{name}</option>)}
      </IconSelect></label>
      <button type="button" onClick={() => transact(() => open(mode === 'building' ? newCard() :
        { schema: 3, name: 'Neue Form', parts: [{ source: 'copycats:copycat_slice' }] }))}>Neu</button>


      {(current?.node.kind==='GRAPH_REFERENCE'||current?.node.kind==='RESULT'&&current.node.producerKind==='GRAPH_REFERENCE') && <button onClick={()=>{setReferenceTarget(current.node.id);setReferenceDialog(true)}}>Referenzversion wählen …</button>}
      <button type="button" disabled={readOnly} onClick={() => chooseCopycat(['INPUT', 'SAVED', 'COPYCAT'].includes(current?.node.kind) ? current.node.id : undefined)}>Copycat auswählen</button>
      {mode === 'building' && <>
        <button type="button" disabled={readOnly} onClick={() => copycatInput.current.click()}>Copycat laden</button>

        <button type="button" disabled={readOnly} onClick={() => libraryInput.current.click()}>Bibliothek öffnen</button>

      </>}
      <button type="button" onClick={() => graphInput.current.click()}>Import …</button><button type="button" className="primary" onClick={exportFile}>Export …</button><button type="button" disabled={readOnly} onClick={() => setJsonDialog(true)}>JSON bearbeiten</button>
      <button type="button" onClick={copyJson}>Minecraft-JSON kopieren</button>
      <span className="toolbar-spacer" />

    </div>

    <div className="personal-library-bar">
      <label>Graphname<input aria-label="Name für Bibliothek" maxLength={64} value={saveName ?? document.name ?? ''} onChange={event => setSaveName(event.target.value)} onBlur={event=>{if(event.target.value !== document.name) transact(()=>renameTo(event.target.value))}} /></label>
      <button disabled={readOnly} onClick={() => save()}>Speichern</button><button onClick={() => save(true)}>Speichern unter …</button>

    </div>

</>} />}
    {jsonDialog && <JsonDialog {...{mode,document,library}} onApply={next => commit(next)} onClose={() => setJsonDialog(false)} />}
    {contextMenu && <div className="context-menu" role="menu" style={{left:contextMenu.x,top:contextMenu.y}}><button role="menuitem" disabled={readOnly || !editableIds.length} onClick={()=>duplicate(true)}>Mit Verbindungen duplizieren · Strg+D</button><button role="menuitem" disabled={readOnly || !editableIds.length} onClick={()=>duplicate(false)}>Ohne Verbindungen duplizieren</button><button role="menuitem" onClick={() => setContextMenu(null)}>Schließen</button></div>}
<input hidden ref={graphInput} type="file" accept=".json,application/json" onChange={event => importFile(event, false)} /><input hidden ref={copycatInput} type="file" accept=".json,application/json" onChange={async event => {
          const file = event.target.files?.[0]; event.target.value = ''; if (!file) return
          try {
            if (file.size > 4_000_000) throw new Error('Die Copycat-Datei ist größer als 4 MB.')
            const source = readGraph(JSON.parse(await file.text()), 'form')
            transact(() => loadCopycat({ id: crypto.randomUUID(), name: source.name, document: source }))
          } catch(error) { setMessage({ text: error.message, error: true }) }
        }} /><input hidden ref={libraryInput} type="file" accept=".json,application/json" onChange={event => importFile(event, true)} />
    <div className="workbench" style={{ '--graph-share': `${graphShare}fr`, '--side-share': `${100 - graphShare}fr` }}>
      <section className="canvas-panel" aria-label="Graph bearbeiten">
        <div className="panel-heading graph-heading">
          <div className="graph-identity">
            {renaming ? <form className="graph-rename" onSubmit={event=>{event.preventDefault();rename()}}><input autoFocus aria-label="Graphname" maxLength={64} value={nameDraft} onBlur={rename} onChange={event=>setNameDraft(event.target.value)} onKeyDown={event=>{if(event.key === 'Escape') setRenaming(false)}}/><button aria-label="Name übernehmen">✓</button></form> : <strong className="graph-name" title={viewedEntry?.name || document.name}>{viewedEntry?.name || document.name || 'Entwurf'}</strong>}
            <button type="button" aria-label="Graphnamen ändern" title="Graphnamen ändern" onClick={()=>{setNameDraft(document.name || '');setRenaming(true)}}>✎</button>
            <IconSelect domain="version" aria-label="Graphversion" value={viewedEntry?.id || ''} onChange={event=>selectVersion(event.target.value)}><option value="">Ungespeichert</option>{versions.map(entry=><option key={entry.id} value={entry.id} iconMeta={{version:entry.version || 1}}>v{entry.version || 1}</option>)}</IconSelect>
            {!readOnly && <button type="button" className="save-version" onClick={()=>save(false)}>Als v{1+Math.max(0,...versions.map(entry=>entry.version || 1))} speichern</button>}
            <button type="button" onClick={()=>setFileDialog(true)}>Bibliothek</button>
            <small>{document.graph.nodes.some(n=>n.kind==='GROUP_INPUT')?'Gruppe · ':''}{count} / {maxNodes}{readOnly ? ' · Betrachten' : ''}</small>
          </div>
          <div className="button-row graph-actions" role="group" aria-label="Graphaktionen">
            <button type="button" aria-label="Rückgängig" title="Rückgängig · Strg+Z" disabled={readOnly || !history.past.length} onClick={()=>travelHistory('undo')}>↶</button>
            <button type="button" aria-label="Wiederholen" title="Wiederholen · Strg+Y" disabled={readOnly || !history.future.length} onClick={()=>travelHistory('redo')}>↷</button>
            <button type="button" className="danger" disabled={!canRemove} onClick={removeSelection} title={`${removeLabel} · Entf`}>{removeLabel}</button>
            <button type="button" disabled={readOnly || count >= maxNodes} onClick={()=>setNodeDialog(true)}>+ Knoten</button>
          </div>
        </div>
        <div className="canvas" ref={canvas}>
          {active && <ReactFlow key={workspace} nodes={nodes} onNodesChange={onNodesChange} edges={edges} nodeTypes={nodeTypes} onInit={setFlow}
            fitView fitViewOptions={{ padding: 0.15, maxZoom: 1.1 }} minZoom={0.13} maxZoom={2}
            selectNodesOnDrag={false} onNodeDragStart={(_,node)=>{setSelected(node.id);if(!selectedIds.includes(node.id))setSelectedIds([node.id])}} onNodeDrag={onDrag} onNodeDragStop={onDragStop} deleteKeyCode={null} nodesConnectable={!readOnly} edgesReconnectable={false}
            onNodeContextMenu={(event,node) => {event.preventDefault();if (!selectedIds.includes(node.id)) {setSelectedIds([node.id]);setSelected(node.id)}setContextMenu({x:Math.min(event.clientX,window.innerWidth-220),y:Math.min(event.clientY,window.innerHeight-90)})}}
            onNodeClick={(event, node) => { const ids=clickNodes(selectedIds,node.id,event.shiftKey || event.ctrlKey || event.metaKey);setSelected(ids.includes(node.id)?node.id:ids.at(-1) || null); if(entries.find(entry=>entry.node.id===node.id)?.node.kind==='AREA') setPreviewMode('selected'); setSelectedWire(null); setSelectedIds(ids); }}
            onEdgeClick={(_, edge) => { setSelectedWire(edge.id); setSelected(edge.target); setSelectedIds([]) }}
            onPaneClick={() => { setContextMenu(null);setSelected(null); setSelectedWire(null); setSelectedIds([]) }}
            isValidConnection={compatible} onConnect={connectView}
            selectionKeyCode="Shift" multiSelectionKeyCode={["Shift","Control","Meta"]} onlyRenderVisibleElements
            colorMode="dark" aria-label="React-Flow-Graph">
            <Background gap={22} size={1} color="#46606d" />
            <Controls showInteractive={false} />
            <MiniMap pannable zoomable style={{width:120,height:78}} nodeColor={miniMapNodeColor} maskColor="#18232c88" />
          </ReactFlow>}
        </div>
        <details className="port-legend"><summary>Anschlüsse · Legende</summary><ul>{Object.entries(portStyles).map(([key,style]) => <li key={key}><span style={{color:style.color}}>{style.symbol}</span>{style.name}</li>)}</ul><small>X / Y / Z kennzeichnen zusätzlich die Achsen. Die Typregeln bestimmen die Verbindung.</small></details>
        <div className={`canvas-footer ${message?.error ? 'error' : ''}`} role="status">
          {message?.text ?? (readOnly ? 'Gespeicherte Version · Betrachten · Knotenpositionen ändern nur die Ansicht' : 'Knoten ziehen · Anschlüsse verbinden · Entf löscht die Auswahl')}
        </div>
      </section>
      <SplitHandle axis="x" value={graphShare} onChange={setGraphShare} label="Breite von Graph und Seitenleiste" />
      <SidePanels showArrangement={Boolean(arrangementNode)} collapsed={{preview:previewCollapsed,arrangement:arrangementCollapsed,inspector:inspectorCollapsed}}>
        {active && mode==='building' && areaPreviewNode ? <AreaPreview node={areaPreviewNode} document={document} readOnly={readOnly} edit={patch=>transact(()=>commit(editNode(mode,document,{...document.graph.nodes.find(node=>node.id===areaPreviewNode.id),...patch})))} previewMode={previewMode} onMode={setPreviewMode} collapsed={previewCollapsed} onToggle={()=>setPreviewCollapsed(value=>!value)}/> : active && <Suspense fallback={<section className="preview-panel"><div className="preview-meta">3D-Vorschau wird geladen …</div></section>}>
          <Preview onDiagnostics={setDiagnostics} mode={mode} document={previewDocument} library={library} selected={previewSelected} materials={materials}
            previewMode={previewMode} onMode={setPreviewMode} collapsed={previewCollapsed} onToggle={() => setPreviewCollapsed(value => !value)} />
        </Suspense>}
        {arrangementNode && <ArrangementPanel key={arrangementNode.id} node={arrangementNode} document={document} library={library} readOnly={readOnly} edit={patch=>transact(()=>commit(editNode(mode,document,{...arrangementNode,...patch})))} onPreview={onArrangementPreview} collapsed={arrangementCollapsed} onToggle={()=>setArrangementCollapsed(value=>!value)}/>}
        <section className={`inspector${inspectorCollapsed ? ' is-collapsed' : ''}`} aria-label="Knoteninspektor">
          <div className="panel-heading"><span>Inspektor</span><div className="button-row"><small>{current ? 'AUSWAHL' : 'ERGEBNIS'}</small>
            <button type="button" aria-expanded={!inspectorCollapsed} aria-controls={`${mode}-inspector`} aria-label={inspectorCollapsed ? 'Inspektor ausklappen' : 'Inspektor einklappen'} onClick={() => setInspectorCollapsed(value => !value)}>{inspectorCollapsed ? '▸' : '▾'}</button>
          </div></div>
          <div id={`${mode}-inspector`} className="inspector-body" hidden={inspectorCollapsed} key={visibleNode.node.id}>
            {selectedIds.length > 1 ? <section className="collection-data" aria-label="Sammlungsdaten"><h2>Sammlungsdaten</h2><p>{selectedIds.length} Knoten ausgewählt · {editableIds.length} bearbeitbar</p><fieldset disabled={readOnly || !editableIds.length} className="inspector-controls"><div className="collection-actions">

              <button onClick={()=>duplicate(false)}>Duplizieren ohne Verbindungen</button>
              <button onClick={()=>duplicate(true)}>Duplizieren mit Verbindungen</button>
              <label>Gruppenname<input value={groupName} maxLength={64} onChange={event=>setGroupName(event.target.value)}/></label>
              <button onClick={makeGroup}>Zu eigener Gruppe machen</button><button onClick={saveGroup}>Knotengruppe speichern</button>
            </div></fieldset></section> : <>
            {readOnly && <p className="hint">Gespeicherte Version · nur Betrachten. Positionen sind Ansichtsänderungen.</p>}
            {document.graph.nodes.find(node=>node.id === visibleNode.node.id)?.libraryReference && <p className="hint">Referenz: {document.graph.nodes.find(node=>node.id === visibleNode.node.id).libraryReference.name} · v{document.graph.nodes.find(node=>node.id === visibleNode.node.id).libraryReference.version}</p>}
            <fieldset className="inspector-controls" disabled={readOnly}>
            <GroupParameters document={document} edit={commit} disabled={readOnly}/>
            {selectedGroup ? <><h2>{selectedGroup.name}</h2>{selectedGroup.libraryReference && <p>Referenz · v{selectedGroup.libraryReference.version}</p>}<p className="hint">{selectedGroup.nodes.length} gespeicherte Knoten · {selectedGroup.outputs.length} typisierte Ausgänge</p>{selectedGroup.outputs.length>1 && <label>Vorschauausgang<IconSelect domain="output" aria-label="Vorschauausgang" value={current?.node.id || ''} onChange={e=>setGroupPreview(value=>({...value,[selectedGroup.id]:e.target.value}))}>{selectedGroup.outputs.map(output=><option key={output.id} value={output.id}>{output.name} · {output.type}</option>)}</IconSelect></label>}{selectedGroup.ports.map(port=><Field key={port.id} label={port.name || portName({...port,id:port.targetPort},entries.find(e=>e.node.id===port.targetNode).node)} value={port.source} options={[[ '', 'Offen' ],...entries.filter(entry=>!selectedGroup.nodes.includes(entry.node.id) && acceptsValue(port,entry)).map(entry=>[entry.node.id,entry.node.name || names[entry.node.kind]])]} onChange={source=>{if(source) connectView({source,target:selectedGroup.id,targetHandle:port.id});else transact(()=>{const targets=port.targets || [{target:port.targetNode,port:port.targetPort}];commit(applyWires(mode,document,entries,wires.filter(wire=>!targets.some(target=>target.target===wire.target && target.port===wire.port))))})}}/>)}{selectedGroup.libraryReference && <button onClick={()=>{setReferenceTarget(selectedGroup.id);setReferenceDialog(true)}}>Referenz auswählen …</button>}<button onClick={unpackReference}>Als unabhängige Knoten auspacken</button></> : <>
            <a className="reference-link" href={`${wiki}/Knoten-${mode === 'form' ? 'Formgraph' : 'Gebaeudegraph'}#${visibleNode.node.kind.toLowerCase()}`}>Knotenreferenz ↗</a>
            <h2>{visibleNode.node.name || operatorFamily(mode,visibleNode.node.kind)?.label || names[visibleNode.node.kind]}</h2>

            <div className="node-id">{visibleNode.node.id} · Ausgang: {visibleNode.outputType || 'Ergebnis'}</div>
            {visibleNode.ports.length > 0 && <details open><summary>Verbindungen</summary>
              {visibleNode.ports.map(port => <Field key={port.id} label={portName(port, visibleNode.node)}
                value={port.source} options={[[ '', 'Offen' ], ...entries.filter(entry =>
                  acceptsValue(port,entry) && entry.node.id !== visibleNode.node.id)
                  .map(({ node }) => [node.id, node.name || `${names[node.kind]} · ${node.id}`])]}
                onChange={source => setWire(visibleNode.node.id, port.id, source)} />)}
            </details>}
            {model.operators?.[visibleNode.node.kind==='RESULT'?visibleNode.node.producerKind:visibleNode.node.kind] && <OperatorFields node={visibleNode.node.kind==='RESULT'?{...visibleNode.node,kind:visibleNode.node.producerKind}:visibleNode.node} edit={patchNode} roles={Object.keys(document.palette || {})}/>}
            <NodeFields {...{ mode, node: visibleNode.node, document, edit: patchNode, library }}
              onInspectNode={id => { setSelected(id); setSelectedIds([id]); setSelectedWire(null) }}
              onChooseCopycat={() => chooseCopycat(visibleNode.node.id)}
              onImportCopycat={() => copycatInput.current.click()} />

            {visibleNode && !visibleNode.node.resultType && (visibleNode.outputType === 'shape' || visibleNode.outputType?.startsWith('COPYCAT') || ['ROTATION','POSITION'].includes(visibleNode.node.kind)) && <CopycatTransforms node={visibleNode.node} edit={patchNode} primary />}
            {!visibleNode.node.resultType && visibleNode.outputType?.toLowerCase()==='position' && <button onClick={splitPosition}>In X/Y/Z zerlegen</button>}
            <NativeDocumentFields {...{ mode, document }} selection={memberIds} edit={next => transact(() => commit(next))} />
            <DocumentFields {...{ mode, document, library, materials, setMaterials }} edit={next => transact(() => commit(next))} />
            </>}</fieldset></>}
          </div>
        </section>
      </SidePanels>
    </div>
    {copycatPicker && <SelectionBoundary onClose={()=>{pickerOrigin.current=null;setCopycatPicker(null)}}>
      <CopycatPicker {...copycatPicker} mode={mode} savedForms={savedForms} library={library}
        onChoose={applyCopycat} onClose={()=>{pickerOrigin.current=null;setCopycatPicker(null)}} />
    </SelectionBoundary>}
  </div>
}

function App() {
  const { openPanel } = useWorkspace()
  const mode = 'building'
  useEffect(() => {
    const url = new URL(window.location.href)
    url.searchParams.delete('graph')
    url.searchParams.set('editor', 'graph')
    window.history.replaceState(null, '', url)
  }, [mode])
  return <>
    <header className="page-header">
      <div className="brand-mark" aria-hidden="true">⬡</div>
      <div className="brand"><a href="../">TEUTONSTUDIO · Graphstudio</a><span>COPYCAT ARCHITECT · {model.release}{import.meta.env.VITE_STUDIO_CHANNEL === 'internal' && ' · Entwicklung'}</span><h1>Graph</h1></div>
<nav aria-label="Graph"><span>Graph</span></nav>
      <button type="button" onClick={() => openPanel('materials')}>Meine Bibliotheken</button>
      <a href={wiki}>Zum Wiki ↗</a>
    </header>
    <main>
      <Studio mode="building" active />
    </main>
  </>
}

createRoot(document.getElementById('root')).render(<Workspace><App /></Workspace>)
