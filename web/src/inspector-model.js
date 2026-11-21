import { editorContract, wireModels } from './generated/engine.mjs'

export const contract = editorContract()
export const wire = wireModels()
export const inspectorNode = (mode, kind) => contract[mode === 'building' ? 'building' : 'copycat'].nodes[kind]
export function fieldType(mode, path) {
  let type = mode === 'building' ? 'BuildingNode' : 'GraphNode'
  for (const key of path.split('.')) {
    type = wire[type.replace(/\?$/, '')]?.fields[key]
    if (!type) throw new Error(`Missing native inspector type: ${path}`)
  }
  return type
}
export function inspectorFields(mode, node) {
  const fields = { ...inspectorNode(mode, node.kind)?.fields }
  // Inline references edit the same native Part settings as form input nodes.
  if (mode === 'building' && node.kind === 'COPYCAT' && node.inlinePart) {
    for (const path of ['part.source','part.size','part.turn','part.top','part.offset','part.tilt','part.spin','part.roof', 'part.discCut']) {
      const native = contract.copycat.nodes.INPUT.fields[path]
      const settings = path === 'part.discCut' ? { ...native, when: { 'part.source': 'copycats:copycat_flat_pane' } } : native
      fields[path.replace('part.', 'inlinePart.')] = {
        ...settings,
        when: Object.fromEntries(Object.entries(settings.when || {}).map(([key, value]) => [key.replace('part.', 'inlinePart.'), value])),
      }
    }
  }
  return Object.entries(fields).filter(([, settings]) => !settings.when || Object.entries(settings.when).every(([path, value]) => getPath(node, path) === value)).filter(([path]) => !Object.keys(fields).some(parent => path.startsWith(parent + '.')))
    .map(([path, settings]) => ({ path, type: fieldType(mode, path), ...settings }))
}
export function getPath(object, path) { return path.split('.').reduce((value, key) => value?.[key], object) }
export function setPath(object, path, value) {
  const [key, ...rest] = path.split('.')
  return { ...object, [key]: rest.length ? setPath(object?.[key] || {}, rest.join('.'), value) : value }
}
