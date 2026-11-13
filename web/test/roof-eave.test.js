import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { previewGraph, editNativeGroup } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, readGraph, replaceNodes, writeGraph } from '../src/model.js'

const fixture = name => JSON.parse(readFileSync(new URL(`../../examples/${name}.json`, import.meta.url)))
const library = fixture('library')
const roof = doc => doc.graph.nodes.find(node => node.kind === 'ROOF')
const change = (doc, patch) => replaceNodes(doc, doc.graph.nodes.map(node => node.kind === 'ROOF' ? { ...node, ...patch } : node))
const preview = doc => previewGraph(true, doc, library, 'roof')
const document = () => change(fixture('building-roof-peak'), { roofEave: 'peak_copycat' })
const positions = plan => plan.meshes.filter(mesh => mesh.component === 'roof').flatMap(mesh => mesh.positions)

test('optional roof eave accepts Copycat values and roundtrips independently of the peak', () => {
  const doc = readGraph(document(), 'building')
  const entries = inspect('building', doc)
  const port = entries.find(entry => entry.node.id === 'roof').ports.find(port => port.id === 'roofEave')
  assert.equal(port.source, 'peak_copycat')
  assert.deepEqual(port.types, ['COPYCAT'])
  assert.ok(port.accepts.includes('COPYCAT_ROTATE'))
  assert.ok(port.accepts.includes('COPYCAT_MIRROR'))
  assert.equal(roof(readGraph(writeGraph('building', doc), 'building')).roofEave, 'peak_copycat')
  const wires = connections(entries).filter(wire => wire.port !== 'roofEave')
  assert.throws(() => applyWires('building', doc, entries, [...wires, { source: 'wall', target: 'roof', port: 'roofEave' }]), /Incompatible/)
  assert.throws(() => applyWires('building', doc, entries, [...wires, { source: 'roof', target: 'roof', port: 'roofEave' }]), /Incompatible|Cycle/)
  const disconnected = applyWires('building', doc, entries, wires)
  assert.ok(!roof(disconnected).roofEave)
  assert.equal(roof(disconnected).roofPeak, roof(doc).roofPeak)
  assert.deepEqual(preview(disconnected), preview(change(doc, { roofEave: null })))
})

for (const ridge of [0, 1]) test(`roof eaves change only low parallel rows with gable extension, axis ${ridge}`, () => {
  const doc = change(document(), { ridge, overhang: 1 })
  const before = preview(change(doc, { roofEave: null }))
  const after = preview(doc)
  assert.notDeepEqual(after, before)
  assert.deepEqual(new Set(positions(after).map(String)), new Set(positions(before).map(String)))
  const exported = readGraph(writeGraph('building', doc), 'building')
  assert.deepEqual(preview(exported), after)
  const flat = change(doc, { roofType: 'flat' })
  assert.deepEqual(preview(flat), preview(change(flat, { roofEave: null })))
})

test('hidden roof eave survives flat mode and diagram movement but is removed with its source', () => {
  const doc = change(document(), { roofType: 'flat' })
  const entries = inspect('building', doc)
  assert.ok(!entries.find(entry => entry.node.id === 'roof').ports.some(port => port.id === 'roofEave'))
  const moved = applyWires('building', doc, entries.map(entry => ({ ...entry, node: { ...entry.node, x: entry.node.x + 20 } })), connections(entries))
  assert.equal(roof(moved).roofEave, 'peak_copycat')
  assert.equal(inspect('building', change(moved, { roofType: 'gable' })).find(entry => entry.node.id === 'roof').ports.find(port => port.id === 'roofEave').source, 'peak_copycat')
  const deleted = applyWires('building', moved, inspect('building', moved), connections(inspect('building', moved)), ['peak_copycat'])
  assert.ok(!roof(deleted).roofEave)
  assert.throws(() => inspect('building', change(doc, { roofEave: 'wall' })), /Incompatible/)
})

test('named group adapters bind and remap the new eave socket', () => {
  let doc = document()
  doc.graph = editNativeGroup(true, doc, 'create', '', 'Dachgruppe', ['roof', 'peak_copycat'])
  const id = doc.graph.nodeGroups[0].id
  doc.graph = editNativeGroup(true, doc, 'initialize', id, '', [])
  doc.graph = editNativeGroup(true, doc, 'instantiate', id, '', [])
  const copied = doc.graph.nodes.find(node => node.kind === 'ROOF' && node.id !== 'roof')
  assert.ok(copied)
  assert.ok(copied.roofEave && copied.roofEave !== 'peak_copycat')
  assert.equal(copied.roofEave, copied.roofPeak)
  inspect('building', doc)
})
