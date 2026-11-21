import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { loadBuildingCopycat } from '../src/copycat-library.js'
import { editNode, readGraph, writeGraph } from '../src/model.js'

const node = (kind, id, patch = {}) => ({ ...newNode(true, kind, id, 0, 0), ...patch })
const building = () => ({ schema: 2, name: 'Reference rotation', palette: { wall: 'minecraft:stone' }, graph: { schema: 5, nodes: [
  node('SEGMENT', 'empty_wall', { block: 'minecraft:air', length: 1, height: 1 }),
  node('BUILDING', 'building'),
] } })
const voxelCut = () => ({ schema: 3, name: 'Split cut slab', groups: [
  { id: 'body', name: 'Body', role: 'surface', priority: 0 },
  { id: 'accent', name: 'Accent', role: 'glass', priority: 1 },
], graph: { schema: 2, nodes: [
  { id: 'slab', kind: 'INPUT', part: { source: 'copycats:copycat_slab' } },
  { id: 'byte', kind: 'INPUT', part: { source: 'copycats:copycat_byte' } },
  { id: 'body', kind: 'DIFFERENCE', inputs: ['slab', 'byte'] },
  { id: 'accent', kind: 'INTERSECTION', inputs: ['slab', 'byte'] },
  { id: 'join', kind: 'UNION', inputs: ['body', 'accent'], inputGroups: ['body', 'accent'] },
  { id: 'result', kind: 'OUTPUT', inputs: ['join'] },
] } })
const exactCut = () => {
  const form = JSON.parse(readFileSync(new URL('./fixtures/crossed-roof-xor.json', import.meta.url)))
  form.graph.nodes.find(n => n.kind === 'OUTPUT').inputs = ['xor']
  return form
}
const order = values => [...values].sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)))
const meshes = plan => order(plan.meshes.map(mesh => ({
  material: mesh.material,
  boxes: order(mesh.boxes),
  faces: order((mesh.faces || []).map(face => ({ normal: face.normal, vertices: order(face.vertices) }))),
  positions: order(mesh.positions),
})))

for (const [name, makeForm] of [['voxel material cuts', voxelCut], ['exact roof XOR cuts', exactCut]]) {
  test(`reference inspector rotation preserves ${name} and survives chained operators and JSON`, () => {
    const entry = { id: 'saved-cut', documentId: 'saved-cut', version: 7, name, mode: 'form', document: readGraph(makeForm(), 'form'),
      materials: { body: 'minecraft:oak_planks', accent: 'minecraft:glass' } }
    const loaded = loadBuildingCopycat(building(), { forms: {}, modules: {} }, entry)
    const source = loaded.document.graph.nodes.find(n => n.id === loaded.nodeId)
    const original = JSON.stringify({ entry, library: loaded.library, document: loaded.document })
    const before = previewGraph(true, loaded.document, loaded.library, source.id)
    for (const axis of ['X', 'Y', 'Z']) for (const quarterTurns of [1, 2, 3]) {
      const steps = [{ kind: 'ROTATE', axis, quarterTurns }]
      const changed = editNode('building', loaded.document, { ...source, copycatTransforms: steps })
      const actual = previewGraph(true, changed, loaded.library, source.id)
      const output = entry.document.graph.nodes.find(n => n.kind === 'OUTPUT')
      const expectedForm = { ...entry.document, graph: { ...entry.document.graph, nodes: [
        ...entry.document.graph.nodes.filter(n => n !== output),
        { id: 'expected_rotation', kind: 'ROTATE', inputs: [output.inputs[0], ''], rotationAxis: axis, quarterTurns },
        { ...output, inputs: ['expected_rotation'] },
      ] } }
      const expected = previewGraph(false, expectedForm, {}, '')
      expected.meshes.forEach(mesh => { mesh.material = entry.materials[mesh.material] })
      assert.deepEqual(meshes(actual), meshes(expected), `${axis}/${quarterTurns}: clipped surfaces`)
      assert.equal(actual.raw, before.raw)
      assert.deepEqual(actual.materials, before.materials)
      const round = readGraph(JSON.parse(JSON.stringify(writeGraph('building', changed))), 'building')
      assert.deepEqual(previewGraph(true, round, loaded.library, source.id), actual)
      const restored = { ...round, graph: { ...round.graph, nodes: [...round.graph.nodes,
        node('COPYCAT_ROTATE', 'inverse', { inputs: [source.id], rotationAxis: axis, quarterTurns: 4 - quarterTurns }),
        node('SEGMENT', 'segment', { inputs: ['inverse'], module: '', length: 1, height: 1 }),
      ] } }
      assert.deepEqual(meshes(previewGraph(true, restored, loaded.library, 'segment')), meshes(before))
      assert.equal(source.form, loaded.document.graph.nodes.find(n => n.id === source.id).form)
      assert.equal(JSON.stringify({ entry, library: loaded.library, document: loaded.document }), original)
    }
  })
}
