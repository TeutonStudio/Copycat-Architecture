import test from 'node:test'
import assert from 'node:assert/strict'
import { createVersionedGraph, extractVersionedGraph, resolveVersionedGraph, exportGraphBundle, validateGraphBundle, switchGraphVersion, compareGraphVersions, previewGraph, inspectVersionedGraph } from '../src/generated/engine.mjs'
import { readFileSync } from 'node:fs'
const fixture = name => JSON.parse(readFileSync(new URL(`../../examples/${name}.json`, import.meta.url)))
const source = { schema: 3, name: 'Ports', graph: { schema: 2, nodes: [
  { id: 'full', kind: 'INPUT', inputs: [], part: { source: 'copycats:copycat_block' } },
  { id: 'slab', kind: 'INPUT', inputs: [], part: { source: 'copycats:copycat_slab' } },
  { id: 'difference', kind: 'DIFFERENCE', inputs: ['full', 'slab'] },
  { id: 'union', kind: 'UNION', inputs: ['full', 'difference'] },
  { id: 'result', kind: 'OUTPUT', inputs: ['union'] },
] } }
const library = fixture('library')
test('extracted instances retain both named outputs and original material geometry', () => {
  const d = createVersionedGraph(false, source, 'root')
  const { parent, definition } = extractVersionedGraph(d, ['full', 'difference'], 'piece', 'Piece', 'instance')
  const definitions = { 'piece@1': definition }
  const entry = inspectVersionedGraph(parent).find(n => n.id === 'instance')
  assert.equal(entry.inputs.length, 1); assert.equal(entry.outputs.length, 2)
  assert.deepEqual(parent.shape.nodes.map(n => n.id), ['slab', 'union', 'result'])
  assert.deepEqual(previewGraph(false, resolveVersionedGraph(parent, definitions), library, ''), previewGraph(false, source, library, ''))
  const bundle = exportGraphBundle(parent, definitions, library)
  validateGraphBundle(JSON.parse(JSON.stringify(bundle)))
  assert.deepEqual(Object.keys(bundle.graphs), ['piece@1'])
  assert.throws(() => validateGraphBundle({ ...bundle, graphs: {} }), /Missing graph/)
})
test('explicit updates report changed ports and preserve incompatible edges', () => {
  const { parent, definition } = extractVersionedGraph(createVersionedGraph(false, source, 'root'), ['full', 'difference'], 'piece', 'Piece', 'instance')
  const next = { ...definition, version: 2, ports: definition.ports.filter(p => p.id !== 'out1'), outputBindings: definition.outputBindings.filter(p => p.port !== 'out1') }
  assert.equal(compareGraphVersions(definition, next)[0].incompatible, true)
  const switched = switchGraphVersion(parent, 'instance', next)
  assert.deepEqual(switched.connections, parent.connections)
  assert.throws(() => resolveVersionedGraph(switched, { 'piece@2': next }), /Missing output/)
  assert.deepEqual(switchGraphVersion(switched, 'instance', definition), parent)
})
test('native building package preserves pinned forms and original preview through browser reconstruction', () => {
  const b = fixture('building-graph')
  const d = createVersionedGraph(true, b, 'building')
  const bundle = exportGraphBundle(d, {}, library)
  validateGraphBundle(bundle)
  assert.deepEqual(previewGraph(true, resolveVersionedGraph(bundle.root, bundle.graphs), library, ''), previewGraph(true, b, library, ''))
})

test('executable print definitions preserve their reference package across browser import and export', async () => {
  const { readGraph, writeGraph } = await import('../src/model.js')
  const { compileForm } = await import('../src/generated/engine.mjs')
  const { parent, definition } = extractVersionedGraph(createVersionedGraph(false, source, 'root'), ['full', 'difference'], 'piece', 'Piece', 'instance')
  const bundle = exportGraphBundle(parent, { 'piece@1': definition }, library)
  const native = { schema: 3, id: 'print', version: 1, name: 'Printable', graph: null, parts: [], groups: bundle.root.materialGroups, versionedGraph: bundle }
  const opened = readGraph(native, 'form')
  const expected = previewGraph(false, source, library, '')
  assert.deepEqual(previewGraph(false, opened, library, ''), expected)
  const exported = writeGraph('form', opened)
  assert.equal(exported.graph, null)
  assert.deepEqual(exported.versionedGraph, bundle)
  assert.deepEqual(exported.parts, compileForm(source))
  assert.deepEqual(previewGraph(false, readGraph(exported, 'form'), library, ''), expected)
})

test('executable building definitions use the package resources without an installed source library', async () => {
  const { readGraph, writeGraph } = await import('../src/model.js')
  const b = fixture('building-graph')
  const d = createVersionedGraph(true, b, 'building')
  const bundle = exportGraphBundle(d, {}, library)
  const native = { ...b, graph: null, versionedGraph: bundle }
  const exported = writeGraph('building', readGraph(native, 'building'))
  assert.equal(exported.graph, null)
  assert.deepEqual(exported.versionedGraph, bundle)
  assert.deepEqual(previewGraph(true, readGraph(exported, 'building'), { forms: {}, modules: {} }, ''), previewGraph(true, b, library, ''))
})


test('opening a recolored executable building retains its local palette without mutating its package', () => {
  const b = fixture('building-graph')
  const root = createVersionedGraph(true, b, 'colored')
  const bundle = exportGraphBundle(root, {}, library)
  const palette = Object.fromEntries(Object.keys(b.palette).map(key => [key, 'minecraft:oak_planks']))
  const reopened = createVersionedGraph(true, { ...b, versionedGraph: bundle, palette }, 'ignored')
  assert.deepEqual(reopened.palette, palette)
  assert.notDeepEqual(bundle.root.palette, palette)
})
