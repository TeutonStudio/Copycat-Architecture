import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describeNode, editNativeGroup, editGroupInterface, inspectorContext, newNode } from '../src/generated/engine.mjs'
import { contract, fieldType, inspectorFields } from '../src/inspector-model.js'
import { editNode, inspect, readGraph } from '../src/model.js'

const corpus = JSON.parse(readFileSync(new URL('../../build/graphstudio-exchange/native.json', import.meta.url)))

for (const [mode, category, nodes] of [['form', 'copycat', corpus.formNodes], ['building', 'building', corpus.buildingNodes]]) {
  test(`${mode}: every native inspector setting is rendered with the native field type and constraints`, () => {
    for (const node of nodes) {
      const native = contract[category].nodes[node.kind].fields || {}
      const fields = inspectorFields(mode, node)
      for (const [path, settings] of Object.entries(native)) {
        assert.ok(fieldType(mode, path), `${node.kind}/${path}: native field has no browser editor`)
        if (settings.when && Object.entries(settings.when).some(([key, value]) => key.split('.').reduce((v, k) => v?.[k], node) !== value)) continue
        const rendered = fields.find(field => field.path === path || path.startsWith(field.path + '.'))
        assert.ok(rendered, `${node.kind}/${path}: missing inspector setting`)
        if (rendered.path === path) for (const key of ['min', 'max', 'choices']) assert.deepEqual(rendered[key], settings[key])
      }
    }
  })
}

test('all native building factory variants expose identical input and output types in the browser', () => {
  assert.equal(corpus.buildingSpecs.length, Object.keys(contract.building.nodes).length * 2)
  for (const fixture of corpus.buildingSpecs) {
    const actual = describeNode(true, fixture.document, fixture.id)
    assert.equal(actual.outputType, fixture.outputType, fixture.id)
    assert.deepEqual(actual.ports, fixture.ports, fixture.id)
  }
})

test('inspector reference choices use the actual native types, including roof and special-room fields', () => {
  const { document } = corpus.cases.find(item => item.name === 'special-rooms')
  const bay = inspectorContext(document, 'bay')
  assert.ok(bay.references['bayWindow.wall'].every(node => ['SEGMENT', 'PATTERN', 'SEGMENT_MIRROR', 'SEGMENT_ROTATE', 'SEGMENT_UNION', 'SEGMENT_INTERSECTION', 'SEGMENT_DIFFERENCE', 'SEGMENT_SYMMETRIC_DIFFERENCE'].includes(node.kind)))
  assert.ok(bay.references['bayWindow.floor'].some(node => node.kind === 'FLOOR'))
  assert.ok(!bay.references['bayWindow.floor'].some(node => node.kind === 'CEILING'))
  assert.equal(inspectorContext(document, 'roof_level').slopes.length, 11)
})

test('inspector edits preserve shared geometry sources and update all their dependent room outlines', () => {
  const item = corpus.cases.find(item => item.name === 'explicit-projection')
  let document = structuredClone(item.document)
  const geometry = document.graph.nodes.find(node => node.kind === 'ROOM_GEOMETRY')
  assert.ok(geometry)
  const context = inspectorContext(document, document.graph.nodes.find(node => node.kind === 'ROOM').id)
  assert.equal(context.derived.height, geometry.id)
  const original = document.graph.nodes.find(node => node.id === geometry.id)
  document = editNode('building', document, { ...original, height: original.height + 1 })
  inspect('building', document)
  assert.equal(inspectorContext(document, document.graph.nodes.find(node => node.kind === 'ROOM').id).resolved.height, original.height + 1)
})

test('native named terminal adapter initializes, renames, adds and removes interface ports', () => {
  let document = structuredClone(corpus.cases.find(item => item.name === 'named-group').document)
  document.graph.nodeGroups = []
  document.graph = editNativeGroup(false, document, 'create', '', 'Gruppe', ['cut'])
  const id = document.graph.nodeGroups[0].id
  document.graph = editNativeGroup(false, document, 'initialize', id, '', [])
  let face = structuredClone(document.graph.nodeGroups[0].graphInterface)
  assert.equal(face.inputs.length, 1)
  assert.equal(face.inputs[0].targets.length, 2)
  face.inputs[0].name = 'Neue Quelle'
  face.outputs[0].name = 'Fertige Form'
  document.graph = editGroupInterface(false, document, id, face)
  assert.equal(document.graph.nodeGroups[0].graphInterface.inputs[0].name, 'Neue Quelle')
  assert.equal(document.graph.nodeGroups[0].graphInterface.outputs[0].name, 'Fertige Form')
  readGraph(document, 'form')
  face = structuredClone(face); face.inputs = []
  document.graph = editGroupInterface(false, document, id, face)
  assert.deepEqual(document.graph.nodes.find(node => node.id === 'cut').inputs, ['', ''])
  assert.deepEqual(document.graph.nodeGroups[0].inputConnections, {})
  readGraph(document, 'form')
})
