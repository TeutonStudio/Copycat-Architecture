import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { newNode, previewGraph, starterBuilding, mountSource, sourceMounting } from '../src/generated/engine.mjs'
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


test('selected Copycat branch is independent of the final output connection', () => {
  const original = fixture('nodegraph-form')
  const source = original.graph.nodes.find(node => node.kind === 'INPUT')
  const graph = replaceNodes(original, original.graph.nodes.map(node => node.kind === 'OUTPUT' ? { ...node, inputs: [''] } : node))
  const selected = previewGraph(false, graph, library, source.id)
  assert.ok(selected.count > 0)
  assert.throws(() => previewGraph(false, graph, library, ''), /Connect every input/)
})

test('all building node kinds show their own result, including empty wall and contextual plates', () => {
  const kinds = new Set()
  for (const name of buildings) {
    const graph = fixture(name)
    for (const node of graph.graph.nodes) {
      const result = previewGraph(true, graph, library, node.id)
      kinds.add(node.kind)
      assert.ok(Array.isArray(result.meshes), `${name}/${node.id}`)
      if (node.id === 'empty_wall') assert.equal(result.count, 0)
      if (node.kind === 'COPYCAT') assert.equal(result.count, 1)
    }
  }
  assert.equal(kinds.size, 9)
})

test('selected missing Copycat reference reports its error instead of showing the final building', () => {
  const original = fixture('building-graph-assembly')
  const node = { ...newNode(true, 'COPYCAT', 'missing', 0, 0), form: 'does-not-exist@1' }
  const graph = replaceNodes(original, [...original.graph.nodes, node])
  assert.throws(() => previewGraph(true, graph, library, node.id), /Missing form/)
})

test('a rotation node previews its typed value rather than unrelated geometry', () => {
  const graph = fixture('nodegraph-rotation-xor')
  const node = graph.graph.nodes.find(node => node.kind === 'ROTATION')
  const result = previewGraph(false, graph, library, node.id)
  assert.equal(result.count, 0)
  assert.equal(result.meshes.length, 0)
  assert.match(result.value, /90°/)
})

test('six world-axis mountings preserve source shape volume, offset and JSON compatibility', () => {
  for (const source of ['copycats:copycat_slab', 'create:copycat_panel', 'copycats:copycat_beam']) {
    const part = { ...newNode(false, 'INPUT', 'source', 0, 0).part, source, offset: { x: 1, y: 0, z: -1 } }
    for (const normal of ['up', 'down', 'east', 'west', 'south', 'north']) {
      const mounted = mountSource(part, normal)
      assert.equal(sourceMounting(mounted), normal)
      assert.deepEqual(mounted.offset, part.offset)
      assert.equal(mounted.source, source)
      assert.equal(mounted.cutMask, '')
      const document = { schema: 3, name: 'Mounting', graph: { schema: 2, nodes: [
        { ...newNode(false, 'INPUT', 'source', 0, 0), part: { ...mounted, offset: { x: 0, y: 0, z: 0 } } },
        { ...newNode(false, 'OUTPUT', 'out', 0, 0), inputs: ['source'] }
      ] } }
      assert.ok(writeGraph('form', document).parts.length > 0)
      const expected = { 'copycats:copycat_slab': 2048, 'create:copycat_panel': 768, 'copycats:copycat_beam': 1024 }[source]
      assert.equal(previewGraph(false, document, library, 'source').count, expected)
    }
  }
})


test('composed stairs support six mountings without losing their source or volume', () => {
  const part = { ...newNode(false, 'INPUT', 'source', 0, 0).part, source: 'copycats:copycat_stairs' }
  for (const normal of ['up', 'down', 'east', 'west', 'south', 'north']) {
    const mounted = mountSource(part, normal)
    assert.equal(sourceMounting(mounted), normal)
    assert.equal(mounted.source, part.source)
    const document = { schema: 3, name: 'Stairs', graph: { schema: 2, nodes: [
      { ...newNode(false, 'INPUT', 'source', 0, 0), part: mounted },
      { ...newNode(false, 'OUTPUT', 'out', 0, 0), inputs: ['source'] }
    ] } }
    assert.equal(previewGraph(false, document, library, '').count, 3072)
    assert.deepEqual(writeGraph('form', document).graph, document.graph)
  }
})


test('a selected plate needs only its own branch and its contextual footprint', () => {
  const original = fixture('building-graph')
  const room = original.graph.nodes.find(node => node.kind === 'ROOM')
  const graph = replaceNodes(original, original.graph.nodes.map(node => node.id === room.id ? { ...node, ceiling: '' } : node))
  assert.ok(previewGraph(true, graph, library, room.floor).count > 0)
  assert.throws(() => previewGraph(true, graph, library, ''), /missing input/)
})


test('selected preview and file import retain document metadata validation', () => {
  for (const [mode, name] of [['form', 'nodegraph-form'], ['building', 'building-graph']]) {
    const document = { ...fixture(name), schema: 999 }
    assert.throws(() => readGraph(document, mode), /Invalid .* schema/)
    assert.throws(() => previewGraph(mode === 'building', document, library, document.graph.nodes[0].id), /Invalid .* schema/)
  }
})

test('empty intermediate geometry is inspectable but the empty final form cannot be exported', () => {
  const original = fixture('nodegraph-form')
  const difference = original.graph.nodes.find(node => node.kind === 'DIFFERENCE')
  const graph = replaceNodes(original, original.graph.nodes.map(node => node.id === difference.id ? { ...node, inputs: [node.inputs[0], node.inputs[0]] } : node))
  assert.equal(previewGraph(false, graph, library, difference.id).count, 0)
  assert.throws(() => previewGraph(false, graph, library, ''), /Result is empty/)
  assert.throws(() => writeGraph('form', graph), /Result is empty/)
})

test('native-only nodes, schemas and geometry are rejected before browser import or export', () => {
  const original = fixture('nodegraph-form')
  const input = original.graph.nodes.find(node => node.kind === 'INPUT')
  for (const kind of ['UNSUPPORTED_NODE']) {
    const document = replaceNodes(original, original.graph.nodes.map(node => node.id === input.id ? { ...node, kind } : node))
    assert.throws(() => readGraph(document, 'form'), /native editor/)
  }
  for (const field of ['exact', 'exactClip', 'roof']) {
    const document = replaceNodes(original, original.graph.nodes.map(node => node.id === input.id ? { ...node, part: { ...node.part, [field]: {} } } : node))
    assert.throws(() => writeGraph('form', document), /native editor/)
  }
  const groups = { ...original, graph: { ...original.graph, nodeGroups: [{ id: 'group' }] } }
  assert.throws(() => readGraph(groups, 'form'), /existing editable nodes/)
  const validGroup = { ...original, graph: { ...original.graph, nodeGroups: [{ id: 'group', name: 'Legacy', nodes: [input.id], definitionId: '' }] } }
  assert.deepEqual(writeGraph('form', readGraph(validGroup, 'form')).graph.nodeGroups, validGroup.graph.nodeGroups)
  const graph = fixture('building-graph')
  for (const change of [{ roomConfig: {mode:"SEGMENTS"} }, { roofConfig: {mode:"FLAT"} }]) {
    const document = replaceNodes(graph, graph.graph.nodes.map((node, index) => index === 0 ? { ...node, ...change } : node))
    assert.throws(() => writeGraph('building', document), /native editor/)
  }
  assert.throws(() => readGraph({ ...graph, graph: { ...graph.graph, schema: 55 } }, 'building'), /schema/)
  assert.equal(newNode(false, 'MIRROR', 'mirror', 0, 0).kind, 'MIRROR')
  assert.throws(() => newNode(true, 'ROOM_LAYOUT', 'layout', 0, 0), /native editor/)
})

test('plate previews use the connected footprint while independent room branches stay incomplete', () => {
  const original = fixture('building-graph')
  const room = original.graph.nodes.find(node => node.kind === 'ROOM')
  const full = previewGraph(true, original, library, room.floor)
  const altered = replaceNodes(original, original.graph.nodes.map(node => node.id === room.id
    ? { ...node, rectangles: node.rectangles.map(rect => ({ ...rect, width: rect.width + 2 })), ceiling: '' } : node))
  assert.ok(previewGraph(true, altered, library, room.floor).count > full.count)
})
