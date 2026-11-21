import { parseMaterial } from './materials.js'
import { validateReferences } from './documents.js'
import { remapBuildingReferences } from './generated/engine.mjs'
import { applyWires, connections, formGroups, inspect, model, protectedNode, replaceNodes } from './model.js'

export function saveNodeGroup(mode, document, ids, name, materials = {}, library = null) {
  const entries = inspect(mode, document)
  const selected = new Set(entries.filter(({ node }) => ids.includes(node.id) && !protectedNode(node,document)).map(({ node }) => node.id))
  if (!selected.size) throw new Error('Mindestens einen bearbeitbaren Knoten auswählen. Ergebnis und Standard-Leerwand gehören nicht zur Knotengruppe.')
  const used = new Set([formGroups(document)[0].id, ...entries.filter(({ node }) => selected.has(node.id)).flatMap(({ node }) => [
    node.part?.group, ...(node.savedCopycat?.parts || []).map(part => part.group),
    node.materialGroup, node.previewGroup, ...(node.inputGroups || []),
  ])])
  const sourceGroup=document.graph.nodeGroups?.find(group=>group.nodes.length===selected.size && group.nodes.every(id=>selected.has(id)))
  const groupNodes=sourceGroup ? sourceGroup.nodes.map(id=>document.graph.nodes.find(node=>node.id===id)) : document.graph.nodes.filter(node=>selected.has(node.id))
  return { nodeGroups:structuredClone((document.graph.nodeGroups || []).filter(group=>group.nodes.every(id=>selected.has(id)))), graphInterface:sourceGroup?.graphInterface?structuredClone(sourceGroup.graphInterface):undefined,id: crypto.randomUUID(), name: name.trim() || 'Knotengruppe', mode, palette:mode==='building'?structuredClone(document.palette):undefined, materials: structuredClone(materials), library: mode === 'building' ? structuredClone(library) : null,
    nodes: structuredClone(groupNodes),
    wires: connections(entries).filter(wire => selected.has(wire.source) && selected.has(wire.target)),
    groups: mode === 'form' ? structuredClone(formGroups(document).filter(group => used.has(group.id))) : [] }
}
export function insertNodeGroup(document, template, idFactory = () => `node_${crypto.randomUUID().slice(0, 13)}`, placement = null) {
  const mode = template.mode
  const ids = new Map(template.nodes.map(node => [node.id, idFactory()]))
  const groups = structuredClone(formGroups(document))
  const groupIds = new Map()
  if (mode === 'form') for (const source of template.groups) {
    const existing = groups.find(group => group.id === source.id && group.name === source.name && group.role === source.role && group.priority === source.priority)
    if (existing) { groupIds.set(source.id, existing.id); continue }
    if (groups.length >= 8) throw new Error('Die Knotengruppe benötigt mehr als acht Materialgruppen. Zuerst Materialgruppen im Zielgraphen reduzieren.')
    const id = !groups.some(group => group.id === source.id) ? source.id : Array.from({ length: 100 }, (_, i) => `group${i + 1}`).find(id => groups.every(group => group.id !== id))
    const priority = groups.every(group => group.priority !== source.priority) ? source.priority : Array.from({ length: 65 }, (_, i) => i - 32).find(priority => groups.every(group => group.priority !== priority))
    groups.push({ ...source, id, priority }); groupIds.set(source.id, id)
  }
  if (mode === 'form') {
    const templateOrder = [...template.groups].sort((a, b) => a.priority - b.priority).map(group => groupIds.get(group.id))
    const byId = new Map(groups.map(group => [group.id, group]))
    if (templateOrder.some((id, index) => index && byId.get(id).priority <= byId.get(templateOrder[index - 1]).priority)) {
      // Preserve both the existing graph's foreground order and the template's
      // foreground order, even when their absolute priority values collide.
      const edges = new Map(groups.map(group => [group.id, new Set()]))
      for (const sequence of [[...formGroups(document)].sort((a, b) => a.priority - b.priority).map(group => group.id), templateOrder]) {
        sequence.slice(1).forEach((id, index) => edges.get(sequence[index]).add(id))
      }
      const pending = new Set(groups.map(group => group.id)), order = []
      while (pending.size) {
        const id = [...pending].find(id => ![...pending].some(other => edges.get(other).has(id)))
        if (!id) throw new Error('Die Materialprioritäten der Knotengruppe widersprechen sich.')
        order.push(id); pending.delete(id)
      }
      order.forEach((id, index) => { byId.get(id).priority = index - 32 })
    }
  }
  const left = Math.min(...template.nodes.map(node => node.x ?? 0)), top = Math.min(...template.nodes.map(node => node.y ?? 0))
  const right = Math.max(...document.graph.nodes.map(node => node.x ?? 0))
  const nodes = template.nodes.map(original => {
    const node = mode === 'building' ? { ...structuredClone(original), ...remapBuildingReferences(original, Object.fromEntries(ids)) } : structuredClone(original)
    node.id = ids.get(original.id)
    const x = (placement?.x ?? right + 200) + (node.x ?? 0) - left, y = (placement?.y ?? 80) + (node.y ?? 0) - top
    if (placement && (x < model.positionMin || x > model.positionMax || y < model.positionMin || y > model.positionMax)) throw new Error('Die Referenz passt an dieser Knotenposition nicht in den Graphbereich.')
    node.x = placement ? x : Math.min(4096, x); node.y = placement ? y : Math.min(4096, y)
    if (mode === 'form') {
      node.inputs = (node.inputs || []).map(() => '')
      if (node.part) node.part.group = groupIds.get(node.part.group || template.groups[0]?.id) || groups[0].id
      if (node.savedCopycat) node.savedCopycat.parts = node.savedCopycat.parts.map(part => {
        const group = groupIds.get(part.group || 'body')
        if (!group) throw new Error('Der gespeicherten Copycat-Quelle fehlt eine Materialgruppe in der Knotengruppe.')
        return { ...part, group }
      })
      for (const key of ['materialGroup', 'previewGroup']) if (node[key]) node[key] = groupIds.get(node[key]) || ''
      if (node.inputGroups) node.inputGroups = node.inputGroups.map(id => groupIds.get(id) || '')
      if (node.materialMode === 'GROUPED' && !node.materialGroup) node.materialGroup = groupIds.get(template.groups[0]?.id) || groups[0].id
    } else {
      node.inputs = (node.inputs || []).map(() => '')
      for (const key of ['floor', 'ceiling', 'corner', 'roof', 'rotation', 'mirroring', 'positionValue', 'transformationValue']) if (key in node) node[key] = ''
      node.walls = Object.fromEntries(Object.keys(node.walls || {}).map(key => [key, 'empty_wall']))
      node.levels = {}
    }
    return node
  })
  const result = replaceNodes(mode === 'form' ? { ...document, groups } : document, [...document.graph.nodes, ...nodes])
  const entries = inspect(mode, result)
  const wires = [...connections(inspect(mode, document)), ...template.wires.map(wire => ({ ...wire, source: ids.get(wire.source), target: ids.get(wire.target) }))]
  return { nodeIds:[...ids.values()], document: applyWires(mode, result, entries, wires), materials: Object.fromEntries(Object.entries(template.materials || {}).filter(([id]) => groupIds.has(id)).map(([id, material]) => [groupIds.get(id), material])) }
}

export function mergeLibraries(current, incoming) {
  if (!incoming) return current
  const result = { ...incoming, ...current }
  for (const type of ['forms', 'modules', 'graphs']) {
    if(type==='graphs'&&!current.graphs&&!incoming.graphs)continue
    for (const [id, entry] of Object.entries(incoming[type] || {})) if (current[type]?.[id] && JSON.stringify(current[type][id]) !== JSON.stringify(entry)) {
      throw new Error(`Bibliothekskonflikt: ${id} ist im Ziel mit anderem Inhalt vorhanden. Eine andere Version verwenden.`)
    }
    result[type] = { ...current[type], ...incoming[type] }
  }
  return result
}

/** Validate a private library draft through the same insertion/type/reference path. */
export function validateNodeGroup(template) {
  if(!template || !['form','building'].includes(template.mode) || typeof template.name !== 'string' || !template.name.trim() || template.name.length>64 || !['nodes','wires','groups'].every(key=>Array.isArray(template[key])) || (template.schema !== undefined && template.schema !== 1)) throw new Error('Ungültiges Knotengruppenschema oder unvollständige Knotengruppe.')
  if(!template.nodes.length || template.nodes.some(protectedNode)) throw new Error('Die Knotengruppe benötigt bearbeitbare Knoten ohne Ergebnis- oder Pflichtknoten.')
  if(template.nodes.some(node=>typeof node.id !== 'string' || !/^[a-zA-Z0-9_-]{1,36}$/.test(node.id))) throw new Error('Gruppenknoten benötigen gültige Kennungen.')
  if(template.materials != null && (Array.isArray(template.materials) || typeof template.materials !== 'object' || Object.values(template.materials).some(value=>typeof value !== 'string'))) throw new Error('Materialien müssen eine Zuordnung von Kennungen zu Materialtexten sein.')
  for(const value of Object.values(template.materials || {})) if(value.trim()) parseMaterial(value.trim())
  const ids=new Set(template.nodes.map(node=>node.id))
  if(ids.size!==template.nodes.length || template.wires.some(wire=>!ids.has(wire.source) || !ids.has(wire.target))) throw new Error('Eine Verbindung verweist auf einen fehlenden Gruppenknoten.')
  if(template.mode === 'form') {
    const groups=new Set(template.groups.map(group=>group.id))
    const refs=template.nodes.flatMap(node=>[node.part?.group,...(node.savedCopycat?.parts || []).map(part=>part.group),node.materialGroup,node.previewGroup,...(node.inputGroups || [])]).filter(Boolean)
    if(!groups.size || refs.some(id=>!groups.has(id))) throw new Error('Die Knotengruppe verweist auf eine fehlende Materialgruppe.')
  }
  const base=template.mode === 'form' ? {schema:3,name:'Validation',graph:{schema:2,nodes:[{id:'result',kind:'OUTPUT',inputs:['']}]}} : {schema:2,name:'Validation',graph:{schema:1,nodes:[{id:'empty_wall',kind:'SEGMENT',block:'minecraft:air',length:1},{id:'result',kind:'BUILDING'}]}}
  let index=0
  const inserted=insertNodeGroup(base,template,()=>`validation_${index++}`)
  if(template.graphInterface) inspect(template.mode,{...inserted.document,graph:{...inserted.document.graph,nodeGroups:[{id:"validation_group",name:template.name,nodes:inserted.nodeIds,graphInterface:template.graphInterface,inputConnections:{}}]}})
  validateReferences(inserted.document,template.library || {})
  return template
}
