import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { listCopycatOptions, previewCopycatOption, copycatPickerSelection, copycatShapeName } from '../src/copycat-options.js'
import { applyCopycatOption } from '../src/copycat-selection.js'
import { inspectorFields } from '../src/inspector-model.js'
import { editNode, inspect, model, readGraph, writeGraph } from '../src/model.js'

const native = JSON.parse(readFileSync(new URL('../../src/main/resources/assets/copycat_architect/graph-models.json', import.meta.url))).copycat.baseShapes
const options = listCopycatOptions().filter(option => option.kind === 'base')
const roof = options.find(option => option.source === 'copycat_architect:copycat_roof')
const group = { id: 'body', name: 'Body', role: 'surface', priority: 0 }
const documentFor = mode => mode === 'form' ? {
  schema: 3, name: 'Grundformen', groups: [group],
  graph: { schema: 2, nodes: [newNode(false, 'INPUT', 'source', 0, 0), { ...newNode(false, 'OUTPUT', 'result', 200, 0), inputs: ['source'] }] },
} : {
  schema: 2, name: 'Grundformen', palette: { wall: 'minecraft:stone' },
  graph: { schema: 1, nodes: [
    { ...newNode(true, 'SEGMENT', 'empty_wall', 0, 0), block: 'minecraft:air', length: 1, height: 1 },
    newNode(true, 'COPYCAT', 'source', 0, 0), newNode(true, 'BUILDING', 'result', 200, 0),
  ] },
}

test('the browser exposes the full native base-shape catalogue, including all disc variants and roof', () => {
  assert.equal(native.length, 21)
  assert.deepEqual(model.baseShapes.map(({ source, discCut, name }) => ({ source, discCut, labelKey: name })), native.map(({ source, discCut, labelKey }) => ({ source, discCut, labelKey })))
  assert.equal(options.length, native.length)
  assert.equal(new Set(options.map(option => option.id)).size, native.length)
  for (const [index, option] of options.entries()) {
    assert.equal(option.source, native[index].source)
    assert.equal(option.document.parts[0].discCut, native[index].discCut)
    assert.ok(previewCopycatOption(option).count > 0)
  }
  const discs = options.filter(option => option.source === 'copycats:copycat_flat_pane')
  assert.deepEqual(discs.map(option => previewCopycatOption(option).count), [512, 256, 128])
  assert.equal(roof.name, 'Dach')
  assert.ok(roof.document.parts[0].roof)
})

test('explicit basic selection opens Grundformen even when replacing a saved reference', () => {
  const node = { kind: 'COPYCAT', form: 'saved@1' }
  assert.equal(copycatPickerSelection(node).initialCategory, 'saved')
  assert.deepEqual(copycatPickerSelection(node, true), { initialId: null, initialCategory: 'basic' })
  for (const option of options) {
    const part = option.document.parts[0]
    for (const node of [{ kind: 'COPYCAT', inlinePart: part }, { kind: 'INPUT', part }]) {
      assert.deepEqual(copycatPickerSelection(node), { initialId: option.id, initialCategory: 'basic' })
      assert.equal(copycatShapeName(part), option.name)
    }
  }
})

for (const mode of ['form', 'building']) test(`${mode}: selecting every native basic shape preserves its variant through JSON and preview`, () => {
  for (const option of options) {
    const result = applyCopycatOption({ mode, document: documentFor(mode), nodeId: 'source', option })
    const restored = readGraph(JSON.parse(JSON.stringify(writeGraph(mode, result.document))), mode)
    const node = inspect(mode, restored).find(entry => entry.node.id === 'source').node
    const part = mode === 'form' ? node.part : node.inlinePart
    assert.equal(part.source, option.source)
    assert.equal(part.discCut, option.document.parts[0].discCut)
    assert.deepEqual(part.roof, option.document.parts[0].roof)
    assert.ok(previewGraph(mode === 'building', restored, {}, 'source').count > 0)
  }
})

test('inline roof settings edit real geometry and invalidate previously compiled exact geometry', () => {
  let document = applyCopycatOption({ mode: 'building', document: documentFor('building'), nodeId: 'source', option: roof }).document
  let node = inspect('building', document).find(entry => entry.node.id === 'source').node
  assert.ok(inspectorFields('building', node).some(field => field.path === 'inlinePart.roof'))
  assert.ok(!inspectorFields('building', node).some(field => field.path === 'inlinePart.discCut'))
  const initial = previewGraph(true, document, {}, 'source')
  const exact = { cells: [{ planes: [[1, 0, 0, 1], [-1, 0, 0, 0], [0, 1, 0, 1], [0, -1, 0, 0], [0, 0, 1, 1], [0, 0, -1, 0]].map(([x, y, z, d]) => ({ x, y, z, d })) }] }
  node = { ...node, inlinePart: { ...node.inlinePart, exact, exactClip: exact, roof: { ...node.inlinePart.roof, thickness: 4 } } }
  document = editNode('building', document, node)
  node = inspect('building', document).find(entry => entry.node.id === 'source').node
  assert.equal(node.inlinePart.exact, null)
  assert.equal(node.inlinePart.exactClip, null)
  const actual = previewGraph(true, document, {}, 'source')
  assert.notDeepEqual(actual.meshes, initial.meshes)
  const expected = previewCopycatOption({ ...roof, document: { ...roof.document, parts: [{ ...roof.document.parts[0], roof: { ...node.inlinePart.roof, thickness: 4 } }] } })
  assert.deepEqual(actual.meshes.map(mesh => mesh.faces), expected.meshes.map(mesh => mesh.faces))
  const restored = readGraph(JSON.parse(JSON.stringify(writeGraph('building', document))), 'building')
  assert.deepEqual(previewGraph(true, restored, {}, 'source'), actual)
  for (const option of options.filter(option => option.source === 'copycats:copycat_flat_pane')) {
    const document = applyCopycatOption({ mode: 'building', document: documentFor('building'), nodeId: 'source', option }).document
    const node = inspect('building', document).find(entry => entry.node.id === 'source').node
    assert.ok(inspectorFields('building', node).some(field => field.path === 'inlinePart.discCut'))
    assert.ok(!inspectorFields('building', node).some(field => field.path === 'inlinePart.roof'))
  }
})
