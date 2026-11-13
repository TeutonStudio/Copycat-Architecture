import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { previewGraph } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, readGraph, replaceNodes, writeGraph } from '../src/model.js'

const fixture = name => JSON.parse(readFileSync(new URL(`../../examples/${name}.json`, import.meta.url)))
const library = fixture('library')
const example = fixture('building-roof-peak')
const roof = doc => doc.graph.nodes.find(node => node.kind === 'ROOF')
const change = (doc, patch) => replaceNodes(doc, doc.graph.nodes.map(node => node.kind === 'ROOF' ? { ...node, ...patch } : node))
const preview = doc => previewGraph(true, doc, library, 'roof')

test('optional roof peak is a typed Copycat port and survives JSON roundtrip', () => {
  const loaded = readGraph(example, 'building')
  const entries = inspect('building', loaded)
  const port = entries.find(entry => entry.node.id === 'roof').ports.find(port => port.id === 'roofPeak')
  assert.equal(port.source, 'peak_copycat')
  assert.deepEqual(port.types, ['COPYCAT'])
  assert.ok(port.accepts.includes('COPYCAT_ROTATE'))
  assert.ok(port.accepts.includes('COPYCAT_MIRROR'))
  assert.equal(roof(readGraph(writeGraph('building', loaded), 'building')).roofPeak, 'peak_copycat')
  assert.ok(previewGraph(true, loaded, library, '').count > 0)
  const wires = connections(entries).filter(wire => wire.port !== 'roofPeak')
  assert.throws(() => applyWires('building', loaded, entries, [...wires, { source: 'wall', target: 'roof', port: 'roofPeak' }]), /Incompatible/)
  assert.throws(() => applyWires('building', loaded, entries, [...wires, { source: 'roof', target: 'roof', port: 'roofPeak' }]), /Incompatible|Cycle/)
  const disconnected = applyWires('building', loaded, entries, wires)
  assert.ok(!roof(disconnected).roofPeak)
  assert.deepEqual(preview(disconnected), preview(change(loaded, { roofPeak: null })))
  const removed = applyWires('building', loaded, entries, wires, ['peak_copycat'])
  assert.ok(!roof(removed).roofPeak)
  assert.ok(preview(removed).count > 0)
})

for (const ridge of [0, 1]) {
  test(`only the odd-width central ridge changes, axis ${ridge}`, () => {
    const doc = change(example, { ridge })
    assert.notDeepEqual(preview(doc), preview(change(doc, { roofPeak: null })))
    const flat = change(doc, { roofType: 'flat' })
    assert.deepEqual(preview(flat), preview(change(flat, { roofPeak: null })))
    // The storey's outer shell is two cells wider than its interior rectangle.
    const even = replaceNodes(doc, doc.graph.nodes.map(node => node.kind === 'STOREY'
      ? { ...node, walls: {}, rectangles: [{ width: ridge === 0 ? 4 : 3, length: ridge === 0 ? 3 : 4 }] } : node))
    assert.deepEqual(preview(even), preview(change(even, { roofPeak: null })))
  })
}
