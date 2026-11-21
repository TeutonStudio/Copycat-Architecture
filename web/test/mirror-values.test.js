import test from 'node:test'
import assert from 'node:assert/strict'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { inspect, readGraph, writeGraph, replaceNodes } from '../src/model.js'
import { mirrorNormals } from '../src/mirror-planes.js'

const node = (kind, id, patch = {}) => ({ ...newNode(true, kind, id, 0, 0), ...patch })
const document = plane => { const doc = { schema: 2, palette: { wall: 'minecraft:stone' }, graph: { schema: 5, nodes: [
  node('SEGMENT', 'empty_wall', { block: 'minecraft:air', inputs: [] }),
  node('BUILDING', 'building'),
  node('COPYCAT', 'source', { inlinePart: { source: 'copycats:copycat_byte', size: 1, offset: { x: 4, y: 4, z: 4 } } }),
  node('MIRRORING', 'value', { mirrors: [{ plane }] }),
  node('COPYCAT_MIRROR', 'linked', { inputs: ['source'], mirroring: 'value' }),
  node('COPYCAT_MIRROR', 'inline', { inputs: ['source'], mirror: { plane } }),
] } }; return replaceNodes(doc, doc.graph.nodes) }

for (const plane of Object.keys(mirrorNormals)) {
  test(`mirror list accepts, applies and round-trips picker plane ${plane}`, () => {
    const doc = document(plane)
    assert.ok(inspect('building', doc).find(entry => entry.node.id === 'value'))
    assert.match(previewGraph(true, doc, {}, 'value').value, new RegExp(plane))
    const expected = previewGraph(true, doc, {}, 'inline')
    assert.ok(expected.count > 0)
    assert.deepEqual(previewGraph(true, doc, {}, 'linked'), expected)
    const round = readGraph(JSON.parse(JSON.stringify(writeGraph('building', doc))), 'building')
    assert.equal(round.graph.nodes.find(n => n.id === 'value').mirrors[0].plane, plane)
    assert.deepEqual(previewGraph(true, round, {}, 'linked'), expected)
  })
}

test('mirror lists retain ownership and length validation', () => {
  const doc = document('XY')
  doc.graph.nodes.find(n => n.id === 'value').mirrors = Array.from({ length: 65 }, () => ({ plane: 'X' }))
  assert.throws(() => inspect('building', doc), /Invalid mirror value list/)
  doc.graph.nodes.find(n => n.id === 'value').mirrors = []
  assert.deepEqual(previewGraph(true, doc, {}, 'linked'), previewGraph(true, doc, {}, 'source'))
  doc.graph.nodes.find(n => n.id === 'source').mirrors = [{ plane: 'X' }]
  assert.throws(() => inspect('building', doc), /Invalid mirror value list/)
})
