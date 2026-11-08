import { insertSavedCopycat, newNode } from './generated/engine.mjs'
import { inspect, readGraph, replaceNodes, writeGraph } from './model.js'

// Persist the selected version inside the building's reference library. Later
// edits/deletion of a personal entry cannot alter a previously placed source.
export function loadBuildingCopycat(document, library, entry, nodeId) {
  const source = writeGraph('form', readGraph(entry.document, 'form'))
  const id = `browser_${entry.id.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 36)}`
  const forms = { ...library.forms }
  let version = 1
  let form
  do { form = { ...source, id, version, name: entry.name || source.name }; version++ }
  while (forms[`${id}@${form.version}`] && JSON.stringify(forms[`${id}@${form.version}`]) !== JSON.stringify(form))
  const key = `${id}@${form.version}`
  forms[key] = structuredClone(form)
  const surfaces = Object.fromEntries(Object.entries(entry.materials || {}).filter(([group]) => form.groups.some(item => item.id === group)))
  let nodes = document.graph.nodes
  if (!nodeId) {
    nodeId = `copycat_${crypto.randomUUID().slice(0, 13)}`
    nodes = [...nodes, newNode(true, 'COPYCAT', nodeId, 40, 100)]
  }
  if (!nodes.some(node => node.id === nodeId && node.kind === 'COPYCAT')) throw new Error('Einen Copycat-Quellknoten auswählen.')
  const next = replaceNodes(document, nodes.map(node => node.id === nodeId ? { ...node, name: form.name, form: key, surfaces } : node))
  inspect('building', next)
  return { document: next, library: { ...library, forms }, nodeId }
}

export function loadFormCopycat(document, entry, nodeId) {
  const source = writeGraph('form', readGraph(entry.document, 'form'))
  const result = insertSavedCopycat(document, source, nodeId)
  const next = replaceNodes({ ...document, groups: result.groups }, result.nodes)
  inspect('form', next)
  return next
}
