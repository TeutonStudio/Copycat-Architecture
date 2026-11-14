import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, readGraph, replaceNodes, writeGraph } from '../src/model.js'

const example = JSON.parse(readFileSync(new URL('../../examples/building-rotation-segment.json', import.meta.url)))
const library = JSON.parse(readFileSync(new URL('../../examples/library.json', import.meta.url)))
const change = (doc, id, patch) => replaceNodes(doc, doc.graph.nodes.map(n => n.id === id ? { ...n, ...patch } : n))
const preview = doc => previewGraph(true, doc, library, 'column')

test('rotation segment has typed integer and Copycat inputs and a segment output', () => {
  const entries = inspect('building', example)
  const entry = entries.find(e => e.node.id === 'column')
  assert.equal(entry.outputType, 'WALL')
  assert.deepEqual(entry.ports.map(p => [p.id, p.types]), [['length', ['INT']], ['in:0', ['COPYCAT']]])
  assert.equal(newNode(true, 'INT', 'n', 0, 0).countValue, 4)
  assert.equal(previewGraph(true, example, library, 'count').value, 'Anzahl: 6')
  assert.equal(newNode(true, 'ROTATION_SEGMENT', 'n', 0, 0).inputs.length, 1)
  const wires = connections(entries)
  assert.deepEqual(applyWires('building', example, entries, wires), example)
  assert.throws(() => applyWires('building', example, entries, [...wires.filter(w => w.port !== 'length'), { source: 'copycat', target: 'column', port: 'length' }]), /Incompatible/)
  const disconnected = applyWires('building', example, entries, wires.filter(w => w.port !== 'length'))
  assert.equal(preview(disconnected).count, 4)
  const removed = applyWires('building', example, entries, wires.filter(w => w.source !== 'count'), ['count'])
  assert.equal(preview(removed).count, 4)
  assert.deepEqual(preview(readGraph(writeGraph('building', example), 'building')), preview(example))
})

test('integer length changes column height and rejects out-of-range lengths', () => {
  for (const countValue of [1, 8, 128]) assert.equal(preview(change(example, 'count', { countValue })).count, countValue)
  for (const countValue of [0, -1, 129]) assert.throws(() => preview(change(example, 'count', { countValue })), /between 1 and 128/)
  const plan = preview(example)
  assert.equal(plan.count, 6)
  assert.equal(previewGraph(true, example, library, '').count, 6)
  const positions = plan.meshes.flatMap(m => m.positions)
  assert.equal(Math.max(...positions.map(p => p[1])), 5)
  assert.ok(positions.every(p => p[0] === 0 && p[2] === 0))
  const transformed = change(example, 'copycat', { quarterTurns: 1 })
  assert.equal(preview(transformed).raw, plan.raw)
  assert.deepEqual(preview(transformed).materials, plan.materials)
})

test('full symmetric Copycat costs one block per layer and can feed a segment operation', () => {
  const full = change(example, 'copycat', { form: 'full@1' })
  const plan = preview(full)
  const one = previewGraph(true, full, library, 'copycat')
  assert.equal(plan.raw, one.raw * 6)
  const combined = replaceNodes(full, [...full.graph.nodes, { ...newNode(true, 'SEGMENT_UNION', 'union', 0, 0), inputs: ['column', 'column'] }])
  assert.equal(previewGraph(true, combined, library, 'union').raw, plan.raw)
  const rotated = replaceNodes(full, [...full.graph.nodes, { ...newNode(true, 'SEGMENT_ROTATE', 'horizontal', 0, 0), inputs: ['column'], rotationAxis: 'Z', quarterTurns: 1 }])
  assert.equal(previewGraph(true, rotated, library, 'horizontal').count, 6)
})
