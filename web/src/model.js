import { applyDiagram, compileForm, graphModels, inspectGraph, prepareForm } from './generated/engine.mjs'

export const model = graphModels()
export const defaults = [{ id: 'body', name: 'Body', role: 'surface', priority: 0 }]
export const formGroups = document => document.groups ?? defaults
export const position = value => Math.max(model.positionMin, Math.min(model.positionMax, Math.round(value)))
export const inspect = (mode, document) => inspectGraph(mode === 'building', document)

export function replaceNodes(document, nodes) {
  return { ...document, graph: { ...document.graph, nodes } }
}

export function readGraph(raw, mode) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Eine Graphdatei muss ein JSON-Objekt sein.')
  const bare = Array.isArray(raw.nodes)
  if (!bare && !raw.graph && !Array.isArray(raw.parts) && !raw.wings && !raw.patterns && !raw.palette) {
    throw new Error('Die Datei enthält weder einen Copycat-Graphen noch eine Form.')
  }
  const isBuilding = bare ? raw.nodes.some(node => node.kind === 'BUILDING')
    : raw.graph ? raw.graph.nodes?.some(node => node.kind === 'BUILDING')
      : !raw.parts && (raw.wings || raw.patterns || raw.palette)
  if (Boolean(isBuilding) !== (mode === 'building')) throw new Error('Die Datei gehört zum anderen Editor.')
  let document = bare ? { name: 'Importierter Graph', schema: isBuilding ? 2 : 3, graph: raw } : raw
  if (!document.graph) {
    if (isBuilding) throw new Error('Diese Datei enthält einen älteren Gebäudeplan ohne Gebäudegraph.')
    document = { ...document, graph: { schema: 2, nodes: prepareForm(document) } }
  }
  inspect(mode, document)
  return document
}

export function writeGraph(mode, document) {
  inspect(mode, document)
  if (mode === 'form') return { ...document, schema: 3, parts: compileForm(document) }
  return { ...document, schema: 2 }
}

export function connections(entries) {
  const known = new Set(entries.map(({ node }) => node.id))
  return entries.flatMap(({ node, ports }) => ports
    .filter(port => known.has(port.source))
    .map(port => ({ source: port.source, target: node.id, port: port.id })))
}

export function protectedNode(node) {
  return node.id === 'empty_wall' || ['OUTPUT', 'BUILDING'].includes(node.kind)
}

export function applyWires(mode, document, entries, wires, removed = []) {
  const previous = new Map(entries.map(({ node }) => [node.id, node]))
  const originals = new Map(document.graph.nodes.map(node => [node.id, node]))
  const layout = entries.map(({ node }) => node).filter(node => !removed.includes(node.id))
  const next = applyDiagram(mode === 'building', document, layout, wires)
  return replaceNodes(document, next.map(node => {
    const before = previous.get(node.id)
    const original = originals.get(node.id)
    const fields = mode === 'building' ? ['inputs', 'offsets', 'walls', 'floor', 'ceiling', 'corner', 'levels', 'roof', 'rotation', 'mirroring', 'dimensions', 'layout', 'geometry', 'regionCopies', 'assembly'] : ['inputs']
    const changed = fields.filter(key => JSON.stringify(before[key]) !== JSON.stringify(node[key]))
    return { ...original, ...Object.fromEntries(changed.map(key => [key, node[key]])) }
  }))
}

export function validateLibrary(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data) || !('forms' in data || 'modules' in data)) {
    throw new Error('Erwartet wird eine Copycat-Bibliothek mit forms und/oder modules.')
  }
  for (const key of ['forms', 'modules']) {
    if (key in data && (!data[key] || typeof data[key] !== 'object' || Array.isArray(data[key]))) {
      throw new Error(`Der Bibliothekseintrag ${key} muss ein Objekt sein.`)
    }
  }
  return data
}
