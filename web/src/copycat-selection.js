import { newNode } from './generated/engine.mjs'
import { loadBuildingCopycat, loadFormCopycatWithMaterials } from './copycat-library.js'
import { formGroups, inspect, model, readGraph, replaceNodes, writeGraph } from './model.js'

const sourceKinds = new Set(['empty', 'base', 'saved', 'library'])
const sourceId = () => `copycat_${crypto.randomUUID().slice(0, 13)}`

function selectedSource(option, library) {
  if (!option || !sourceKinds.has(option.kind)) throw new Error('Eine Copycat-Form auswählen.')
  let raw = option.document
  if (option.kind === 'library') {
    if (typeof option.reference !== 'string' || !Object.hasOwn(library.forms ?? {}, option.reference)) {
      throw new Error('Diese Form ist nicht mehr in der Bibliothek vorhanden. Die Auswahl erneut öffnen.')
    }
    raw = library.forms[option.reference]
  }
  const document = readGraph(raw, 'form')
  const name = option.name || option.entry?.name || document.name || 'Copycat'
  return {
    ...option.entry,
    id: option.entry?.id || option.id,
    name,
    document: { ...document, name },
    materials: option.materials ?? option.entry?.materials ?? {},
  }
}

function basicPart(option, entry) {
  if (!model.shapes.some(shape => shape.id === option.source)) throw new Error('Diese Copycat-Grundform wird nicht unterstützt.')
  const nodes = inspect('form', entry.document).map(({ node }) => node)
  const source = nodes.find(node => node.kind === 'INPUT')
  const output = nodes.find(node => node.kind === 'OUTPUT')
  if (nodes.length !== 2 || !source || source.part.source !== option.source || output?.inputs[0] !== source.id) {
    throw new Error('Eine Grundform muss genau eine Copycat-Quelle enthalten.')
  }
  return structuredClone(source.part)
}

function groupReferences(nodes) {
  return new Set(nodes.flatMap(node => [
    node.kind === 'INPUT' ? node.part?.group || 'body' : null,
    ...(node.kind === 'SAVED' ? node.savedCopycat?.parts ?? [] : []).map(part => part.group || 'body'),
    node.materialGroup, node.previewGroup, ...(node.inputGroups ?? []),
  ]).filter(Boolean))
}

// Selection is a transaction: resolve and validate the source before returning
// any state. The dialog can be dismissed without touching the graph or library.
export function applyCopycatOption({ mode, document, library = { forms: {}, modules: {} }, materials = {}, nodeId, option }) {
  if (!['form', 'building'].includes(mode)) throw new Error('Unbekannter Graphtyp.')
  const entries = inspect(mode, document)
  const target = nodeId == null ? null : entries.find(({ node }) => node.id === nodeId)?.node
  if (nodeId != null && (!target || !(mode === 'building' ? target.kind === 'COPYCAT' : ['INPUT', 'SAVED'].includes(target.kind)))) {
    throw new Error('Einen Copycat-Quellknoten auswählen.')
  }
  const entry = selectedSource(option, library)
  const part = option.kind === 'base' ? basicPart(option, entry) : null
  const original = target && document.graph.nodes.find(node => node.id === target.id)

  if (mode === 'building') {
    if (option.kind === 'library') {
      // Validate the current library content, including its compiled result.
      // Keeping the reference avoids creating a duplicate browser snapshot.
      writeGraph('form', entry.document)
      const node = original ?? newNode(true, 'COPYCAT', sourceId(), 40, 100)
      const nextNode = {
        ...node, name: entry.name, form: option.reference, block: '',
        surfaces: node.form === option.reference ? node.surfaces : {},
        rotationAxis: 'Y', quarterTurns: 0,
      }
      const nodes = original ? document.graph.nodes.map(item => item.id === node.id ? nextNode : item)
        : [...document.graph.nodes, nextNode]
      const next = replaceNodes(document, nodes)
      inspect('building', next)
      return { document: next, library, materials, nodeId: node.id }
    }
    if (typeof entry.id !== 'string' || !entry.id) throw new Error('Die ausgewählte Form hat keine gültige Kennung.')
    const prepared = original ? replaceNodes(document, document.graph.nodes.map(node => node.id === original.id
      ? { ...node, block: '', rotationAxis: 'Y', quarterTurns: 0 } : node)) : document
    const result = loadBuildingCopycat(prepared, library, entry, target?.id)
    return { ...result, materials }
  }

  if (part) {
    const groups = formGroups(document)
    const previousGroup = target?.kind === 'INPUT' ? target.part.group : target?.savedCopycat?.parts[0]?.group
    const group = groups.find(group => group.id === previousGroup)?.id ?? groups[0].id
    const node = original ?? newNode(false, 'INPUT', sourceId(), 40, 100)
    const nextNode = { ...node, kind: 'INPUT', part: { ...part, group }, savedCopycat: null, rotationAxis: 'NORMAL', quarterTurns: 0 }
    const nodes = original ? document.graph.nodes.map(item => item.id === node.id ? nextNode : item)
      : [...document.graph.nodes, nextNode]
    let next = replaceNodes(document, nodes)
    let nextMaterials = materials
    if (target?.kind === 'SAVED') {
      const previousGroups = new Set(target.savedCopycat.parts.map(part => part.group))
      const used = groupReferences(nodes)
      const removed = new Set(groups.filter(group => previousGroups.has(group.id) && !used.has(group.id)).map(group => group.id))
      if (removed.size) {
        next = { ...next, groups: groups.filter(group => !removed.has(group.id)) }
        nextMaterials = Object.fromEntries(Object.entries(materials).filter(([group]) => !removed.has(group)))
      }
    }
    inspect('form', next)
    return { document: next, library, materials: nextMaterials, nodeId: node.id }
  }

  let prepared = document
  if (original) {
    // The insertion helper uses the previous snapshot to retire its unused
    // material groups. Include an INPUT's old part when converting it, while
    // retaining groups explicitly referenced by other settings on this node.
    const pinned = new Set([target.materialGroup, target.previewGroup, ...target.inputGroups].filter(Boolean))
    const previousParts = target.kind === 'INPUT' ? [target.part] : target.savedCopycat.parts
    const previousSnapshot = target.savedCopycat ?? { id: '', version: 1, name: 'Copycat' }
    const node = {
      ...original, kind: 'SAVED', rotationAxis: 'NORMAL', quarterTurns: 0,
      savedCopycat: { ...previousSnapshot, parts: previousParts.filter(part => !pinned.has(part.group)) },
    }
    prepared = replaceNodes(document, document.graph.nodes.map(item => item.id === node.id ? node : item))
  }
  const result = loadFormCopycatWithMaterials(prepared, entry, target?.id, materials)
  const originals = new Map(document.graph.nodes.map(node => [node.id, node]))
  const nodes = result.document.graph.nodes.map(node => node.id === result.nodeId
    ? { ...original, ...node, ...(!original ? { x: 40, y: 100 } : {}) }
    : originals.get(node.id) ?? node)
  const next = replaceNodes(result.document, nodes)
  inspect('form', next)
  return { ...result, document: next, library }
}
