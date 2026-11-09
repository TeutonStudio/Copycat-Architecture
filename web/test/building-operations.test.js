import assert from 'node:assert/strict'
import test from 'node:test'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, model, readGraph, writeGraph } from '../src/model.js'

const operations = ['ROTATE', 'MIRROR', 'UNION', 'INTERSECTION', 'DIFFERENCE', 'SYMMETRIC_DIFFERENCE']
const library = { forms: {
  'full@1': { schema: 3, name: 'Full', parts: [{ source: 'copycats:copycat_block' }] },
  'slab@1': { schema: 3, name: 'Slab', parts: [{ source: 'copycats:copycat_slab' }] },
  'byte@1': { schema: 3, name: 'Byte', parts: [{ source: 'copycats:copycat_byte' }] },
}, modules: {} }
const node = (kind, id, values = {}) => ({ ...newNode(true, kind, id, 0, 0), ...values })
const document = nodes => ({ schema: 2, name: 'Building operators', palette: { wall: 'minecraft:stone' },
  graph: { schema: 1, nodes: [
    node('SEGMENT', 'empty_wall', { block: 'minecraft:air', length: 1, height: 1 }),
    ...nodes, node('BUILDING', 'building'),
  ] } })
const preview = (doc, selected) => previewGraph(true, doc, library, selected)
const roundTrip = doc => readGraph(JSON.parse(JSON.stringify(writeGraph('building', doc))), 'building')
function voxels(plan) {
  const result = new Set()
  for (const mesh of plan.meshes) for (const [px, py, pz] of mesh.positions) {
    for (const [x, y, z, X, Y, Z] of mesh.boxes) {
      for (let i = x; i < X; i++) for (let j = y; j < Y; j++) for (let k = z; k < Z; k++) {
        result.add(`${16 * px + i},${16 * py + j},${16 * pz + k}`)
      }
    }
  }
  return result
}
const volume = (doc, selected) => voxels(preview(doc, selected)).size
function binary(family, operation, inputs = ['a', 'b']) {
  return document([
    node(family, 'a', { form: 'full@1', length: 1, height: 1 }),
    node(family, 'b', { form: 'slab@1', length: 1, height: 1 }),
    node(`${family}_${operation}`, 'op', { inputs }),
  ])
}

test('all twelve requested building operators can be created with the correct socket count', () => {
  for (const family of ['COPYCAT', 'SEGMENT']) for (const operation of operations) {
    const kind = `${family}_${operation}`
    assert.ok(model.building.includes(kind), `${kind} missing from browser node picker`)
    const operator = node(kind, 'op')
    const unary = ['ROTATE', 'MIRROR'].includes(operation)
    assert.equal(operator.inputs.length, unary ? 1 : 2)
    const limits = model.buildingInputs[kind]
    assert.deepEqual(limits, unary ? undefined : { min: 2, max: operation === 'DIFFERENCE' ? 2 : 16 })
    inspect('building', document([operator]))
  }
})

for (const family of ['COPYCAT', 'SEGMENT']) {
  for (const [operation, expected] of [['UNION', 4096], ['INTERSECTION', 2048], ['DIFFERENCE', 2048], ['SYMMETRIC_DIFFERENCE', 2048]]) {
    test(`${family} ${operation} computes partial geometry and survives JSON/wire round trips`, () => {
      const doc = binary(family, operation)
      assert.equal(volume(doc, 'op'), expected)
      assert.deepEqual(preview(roundTrip(doc), 'op'), preview(doc, 'op'))
      assert.deepEqual(applyWires('building', doc, inspect('building', doc), connections(inspect('building', doc))), doc)
      if (family === 'COPYCAT') {
        doc.graph.nodes.push(node('SEGMENT', 'wall', { inputs: ['op'], length: 2, height: 1 }))
        assert.equal(volume(doc, 'wall'), expected * 2, 'Copycat output must feed a building segment')
      }
    })
  }

  test(`${family} difference is ordered and empty boolean results remain composable`, () => {
    const reverse = binary(family, 'DIFFERENCE', ['b', 'a'])
    assert.equal(volume(reverse, 'op'), 0)
    reverse.graph.nodes.push(
      node(`${family}_ROTATE`, 'rotate', { inputs: ['op'], rotationAxis: 'X', quarterTurns: 1 }),
      node(`${family}_MIRROR`, 'mirror', { inputs: ['rotate'], rotationAxis: 'X', mirror: family === 'COPYCAT' ? { plane: 'X' } : null }),
      node(`${family}_UNION`, 'combined', { inputs: ['mirror', 'b'] }),
    )
    assert.equal(volume(reverse, 'mirror'), 0)
    assert.equal(volume(reverse, 'combined'), 2048)
    assert.equal(volume(binary(family, 'SYMMETRIC_DIFFERENCE', ['a', 'a']), 'op'), 0)
    assert.equal(volume(binary(family, 'SYMMETRIC_DIFFERENCE', ['a', 'a', 'a']), 'op'), 4096)
  })

  test(`${family} transformations preserve shape over complete turns and double reflections`, () => {
    for (const axis of ['X', 'Y', 'Z']) {
      const doc = document([node(family, 'source', { form: 'byte@1', length: 3, height: 2 })])
      let previous = 'source'
      for (let i = 1; i <= 4; i++) {
        doc.graph.nodes.push(node(`${family}_ROTATE`, `turn${i}`, { inputs: [previous], rotationAxis: axis, quarterTurns: 1 }))
        previous = `turn${i}`
      }
      const original = voxels(preview(doc, 'source'))
      assert.notDeepEqual(voxels(preview(doc, 'turn1')), original, `${axis} must actually rotate geometry`)
      assert.deepEqual(voxels(preview(doc, 'turn4')), original)
      doc.graph.nodes.push(
        node(`${family}_MIRROR`, 'once', { inputs: ['source'], rotationAxis: axis, mirror: family === 'COPYCAT' ? { plane: axis } : null }),
        node(`${family}_MIRROR`, 'twice', { inputs: ['once'], rotationAxis: axis, mirror: family === 'COPYCAT' ? { plane: axis } : null }),
      )
      assert.notDeepEqual(voxels(preview(doc, 'once')), original, `${axis} must actually reflect geometry`)
      assert.deepEqual(voxels(preview(doc, 'twice')), original)
      assert.deepEqual(voxels(preview(roundTrip(doc), 'once')), voxels(preview(doc, 'once')))
    }
  })

  test(`${family} boolean sockets reject the wrong family, cycles and invalid arities`, () => {
    const doc = binary(family, 'UNION')
    const wrong = family === 'COPYCAT' ? 'SEGMENT' : 'COPYCAT'
    doc.graph.nodes.push(node(wrong, 'wrong'))
    const entries = inspect('building', doc)
    assert.throws(() => applyWires('building', doc, entries, [{ source: 'wrong', target: 'op', port: 'in:0' }]), /Incompatible/)
    assert.throws(() => applyWires('building', doc, entries, [{ source: 'op', target: 'op', port: 'in:0' }]), /Cycle/)
    assert.throws(() => inspect('building', binary(family, 'DIFFERENCE', ['a', 'b', 'a'])))
    assert.throws(() => inspect('building', binary(family, 'UNION', ['a'])))
  })
}

test('segment position-only mirror preserves local Copycats and the setting across edits', () => {
  const doc = document([
    node('SEGMENT', 'source', { form: 'byte@1', length: 3, height: 2 }),
    node('SEGMENT_MIRROR', 'positions', { inputs: ['source'], rotationAxis: 'X', mirrorPositionsOnly: true }),
    node('SEGMENT_MIRROR', 'whole', { inputs: ['source'], rotationAxis: 'X', mirrorPositionsOnly: false }),
  ])
  assert.deepEqual(voxels(preview(doc, 'positions')), voxels(preview(doc, 'source')))
  assert.notDeepEqual(voxels(preview(doc, 'whole')), voxels(preview(doc, 'positions')))
  const restored = roundTrip(doc)
  assert.equal(restored.graph.nodes.find(n => n.id === 'positions').mirrorPositionsOnly, true)
  assert.deepEqual(applyWires('building', restored, inspect('building', restored), connections(inspect('building', restored))), restored)
})
