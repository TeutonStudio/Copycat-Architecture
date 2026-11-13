import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { previewGraph, editNativeGroup } from '../src/generated/engine.mjs'
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

const ports = doc => inspect('building', doc).find(entry => entry.node.id === 'roof').ports.map(port => port.id)

test('roof type and ridge axis update ports without discarding dormant connections', () => {
  assert.ok(ports(example).includes('roofPeak'))
  assert.deepEqual(ports(example).filter(key => key.startsWith('wall:')), ['wall:0', 'wall:2'])
  const rotated = change(example, { ridge: 1 })
  assert.deepEqual(ports(rotated).filter(key => key.startsWith('wall:')), ['wall:1', 'wall:3'])
  const flat = change(example, { roofType: 'flat' })
  assert.ok(!ports(flat).includes('roofPeak'))
  assert.deepEqual(ports(flat).filter(key => key.startsWith('wall:')), ['wall:0', 'wall:1', 'wall:2', 'wall:3'])
  assert.equal(roof(readGraph(writeGraph('building', flat), 'building')).roofPeak, 'peak_copycat')
  const entries = inspect('building', flat)
  const moved = applyWires('building', flat, entries.map(entry => ({ ...entry, node: { ...entry.node, x: entry.node.x + 20 } })), connections(entries))
  assert.equal(roof(moved).roofPeak, 'peak_copycat')
  assert.equal(inspect('building', change(moved, { roofType: 'gable' })).find(e => e.node.id === 'roof').ports.find(p => p.id === 'roofPeak').source, 'peak_copycat')
  const deleted = applyWires('building', flat, entries, connections(entries), ['peak_copycat'])
  assert.ok(!roof(deleted).roofPeak)
})

test('hidden gable sources are preserved, typed and removed even after changing the ridge axis', () => {
  const gable = change(example, { walls: { '0': 'gable_wall', '2': 'gable_wall' } })
  const rotated = replaceNodes(change(gable, { ridge: 1 }), gable.graph.nodes.map(node => node.id === 'roof'
    ? { ...node, ridge: 1 } : node.id === 'gable_wall' ? { ...node, inputs: [], module: 'missing@1' } : node))
  const entries = inspect('building', rotated)
  const unchanged = applyWires('building', rotated, entries, connections(entries))
  assert.deepEqual(roof(unchanged).walls, roof(gable).walls)
  assert.ok(preview(unchanged).count > 0)
  const deleted = applyWires('building', rotated, entries, connections(entries).filter(wire => wire.target !== 'gable_wall' && wire.source !== 'gable_wall'), ['gable_wall'])
  assert.deepEqual(roof(deleted).walls, {})
  assert.throws(() => inspect('building', change(rotated, { walls: { '0': 'peak_copycat' } })), /Incompatible/)
  assert.throws(() => inspect('building', change(change(example, { roofType: 'flat' }), { roofPeak: 'wall' })), /Incompatible/)
})

test('named group inputs retain hidden roof connections during diagram edits', () => {
  let grouped = structuredClone(example)
  grouped.graph = editNativeGroup(true, grouped, 'create', '', 'Dachgruppe', ['roof'])
  const id = grouped.graph.nodeGroups[0].id
  grouped.graph = editNativeGroup(true, grouped, 'initialize', id, '', [])
  const flat = change(grouped, { roofType: 'flat' })
  const entries = inspect('building', flat)
  const unchanged = applyWires('building', flat, entries, connections(entries))
  assert.equal(roof(unchanged).roofPeak, 'peak_copycat')
  assert.deepEqual(unchanged.graph.nodeGroups, flat.graph.nodeGroups)
  const deleted = applyWires('building', flat, entries, connections(entries), ['peak_copycat'])
  assert.ok(!roof(deleted).roofPeak)
  inspect('building', deleted)
})
