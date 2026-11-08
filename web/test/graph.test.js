import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { newNode, previewGraph, starterBuilding } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, readGraph, replaceNodes, writeGraph } from '../src/model.js'

const fixture = name => JSON.parse(readFileSync(new URL(`../../examples/${name}.json`, import.meta.url)))
const library = fixture('library')
const forms = ['nodegraph-form', 'nodegraph-rotation-xor', 'nodegraph-material-groups']
const buildings = ['building-graph', 'building-graph-assembly', 'building-graph-basements']

for (const [mode, fixtures] of [['form', forms], ['building', buildings]]) {
  for (const file of fixtures) {
    test(`${file} opens, previews and round-trips`, () => {
      const document = fixture(file)
      const loaded = readGraph(document, mode)
      const plan = previewGraph(mode === 'building', loaded, library, '')
      assert.ok(plan.count > 0)
      assert.ok(plan.meshes.length > 0)
      const exported = writeGraph(mode, loaded)
      assert.deepEqual(exported.graph, document.graph)
      assert.deepEqual(previewGraph(mode === 'building', exported, library, ''), plan)
      const entries = inspect(mode, loaded)
      const unchanged = applyWires(mode, loaded, entries, connections(entries))
      assert.deepEqual(unchanged, loaded)
    })
  }
}

test('new starter plans a building with the supplied example library', () => {
  assert.ok(previewGraph(true, starterBuilding(), library, '').count > 0)
})

test('removing a form source clears only its connections and keeps custom data', () => {
  const source = newNode(false, 'INPUT', 'source', 20, 10)
  const output = { ...newNode(false, 'OUTPUT', 'out', 40, 30), inputs: ['source'], extra: { keep: true } }
  const document = { name: 'Form', schema: 3, graph: { schema: 2, nodes: [source, output] } }
  const edited = applyWires('form', document, inspect('form', document), [], ['source'])
  assert.deepEqual(edited.graph.nodes, [{ ...output, inputs: [''] }])
  assert.throws(() => writeGraph('form', edited), /Connect every input/)
})

test('the shared rules reject cycles and incompatible form ports', () => {
  const rotation = newNode(false, 'ROTATION', 'rot', 0, 0)
  const union = newNode(false, 'UNION', 'union', 0, 0)
  const output = newNode(false, 'OUTPUT', 'out', 0, 0)
  const document = { name: 'Form', schema: 3, graph: { schema: 2, nodes: [rotation, union, output] } }
  const entries = inspect('form', document)
  assert.throws(() => applyWires('form', document, entries, [{ source: 'rot', target: 'out', port: '0' }]), /Incompatible socket/)
  assert.throws(() => applyWires('form', document, entries, [{ source: 'union', target: 'union', port: '0' }]), /Cycle/)
})

test('building wires write native levels and roof fields and compact basements', () => {
  const original = fixture('building-graph-basements')
  const document = replaceNodes(original, original.graph.nodes.map(node => node.kind === 'BUILDING'
    ? { ...node, levels: { ...node.levels, '-2': node.levels['-1'] } } : node))
  const entries = inspect('building', document)
  const root = entries.find(({ node }) => node.kind === 'BUILDING').node
  const remaining = connections(entries).filter(wire => wire.target !== root.id || !['roof', 'level:-1'].includes(wire.port))
  const edited = applyWires('building', document, entries, remaining)
  const result = edited.graph.nodes.find(node => node.id === root.id)
  assert.equal(result.roof, '')
  assert.equal(result.levels['-1'], root.levels['-2'])
  assert.ok(!('-2' in result.levels))
})

test('unsupported graph schemas fail instead of silently changing the file', () => {
  const document = fixture('nodegraph-form')
  const altered = { ...replaceNodes(document, document.graph.nodes), graph: { ...document.graph, schema: 55 } }
  assert.throws(() => readGraph(altered, 'form'), /schema/)
})
