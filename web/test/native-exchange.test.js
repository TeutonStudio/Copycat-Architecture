import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { graphModels, normalizeDocument, previewGraph, editNativeGroup, newNode, wireModels } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, readGraph, replaceNodes, writeGraph } from '../src/model.js'

const directory = new URL('../../build/graphstudio-exchange/', import.meta.url)
const corpus = JSON.parse(readFileSync(new URL('native.json', directory)))
const exports = []
const catalog = JSON.parse(readFileSync(new URL('../../src/main/resources/assets/copycat_architect/graph-models.json', import.meta.url)))

test('browser advertises exactly the mod node types, schemas and DTO fields', () => {
  const runtime = graphModels()
  assert.deepEqual(new Set(runtime.form.map(node => node.kind)), new Set(Object.keys(catalog.copycat.nodes)))
  assert.deepEqual(new Set(runtime.building), new Set(Object.keys(catalog.building.nodes)))
  assert.deepEqual(runtime.copycatSchemas, catalog.copycat.graphSchemas)
  assert.deepEqual(runtime.buildingSchemas, catalog.building.graphSchemas)
  assert.equal(Object.keys(wireModels()).length - 1, 52)
  assert.equal(wireModels().enums.CopycatMirrorPlane.length, 13)
})

test('every native node factory survives full browser DTO decoding without dropping fields', () => {
  for (const building of [false, true]) {
    const nodes = building ? corpus.buildingNodes : corpus.formNodes
    const normalized = normalizeDocument(building, { schema: building ? 2 : 4, graph: { schema: building ? 4 : 3, nodes } })
    assert.deepEqual(normalized.graph.nodes, nodes)
  }
})

for (const item of corpus.cases) test(`JVM → browser → JVM: ${item.name}`, () => {
  const mode = item.building ? 'building' : 'form'
  const loaded = readGraph(structuredClone(item.document), mode)
  const preview = previewGraph(item.building, loaded, item.library, '')
  assert.equal(preview.count, item.count)
  assert.equal(preview.raw, item.raw)
  const saved = writeGraph(mode, loaded)
  assert.deepEqual(saved, item.document, 'All native configuration, group and exact geometry fields remain intact')
  const entries = inspect(mode, loaded)
  assert.deepEqual(applyWires(mode, loaded, entries, connections(entries)), loaded)
  exports.push({ name: item.name, building: item.building, document: saved })
})

test('named group input rewiring updates every bound member socket and removal prunes the source', () => {
  let document = structuredClone(corpus.cases.find(item => item.name === 'named-group').document)
  document.graph.nodes.push(newNode(false, 'INPUT', 'replacement', 20, 20))
  let entries = inspect('form', document)
  let wires = connections(entries).filter(wire => wire.target !== 'cut' || wire.port !== '0')
  wires.push({ source: 'replacement', target: 'cut', port: '0' })
  document = applyWires('form', document, entries, wires)
  assert.deepEqual(document.graph.nodes.find(node => node.id === 'cut').inputs, ['replacement', 'replacement'])
  assert.equal(document.graph.nodeGroups[0].inputConnections.in, 'replacement')
  entries = inspect('form', document)
  document = applyWires('form', document, entries, connections(entries).filter(wire => wire.source !== 'replacement'), ['replacement'])
  assert.deepEqual(document.graph.nodes.find(node => node.id === 'cut').inputs, ['', ''])
  assert.deepEqual(document.graph.nodeGroups[0].inputConnections, {})
  inspect('form', document)
})

test('native group instances share configuration and retain their own positions', () => {
  const original = structuredClone(corpus.cases.find(item => item.name === 'named-group').document)
  let document = { ...original, graph: editNativeGroup(false, original, 'instantiate', 'group', '', []) }
  const instance = document.graph.nodeGroups.find(group => group.id !== 'group')
  const member = document.graph.nodes.find(node => node.id === instance.nodes[0])
  document = replaceNodes(document, document.graph.nodes.map(node => node.id === 'cut' ? { ...node, materialMode: 'GROUPED', materialGroup: 'body' } : node))
  const changed = document.graph.nodes.find(node => node.id === member.id)
  assert.equal(changed.materialMode, 'GROUPED')
  assert.equal(changed.materialGroup, 'body')
  assert.equal(changed.x, member.x)
  inspect('form', document)
})

test('future schemas and unknown node kinds still fail without changing the imported data', () => {
  for (const [mode, original] of [['form', corpus.cases[0].document], ['building', corpus.cases.find(item => item.building).document]]) {
    const bad = structuredClone(original); bad.graph.schema = 999
    assert.throws(() => readGraph(bad, mode))
    bad.graph.schema = original.graph.schema; bad.graph.nodes[0].kind = 'FUTURE_KIND'
    assert.throws(() => readGraph(bad, mode))
  }
})

test.after(() => {
  assert.equal(exports.length, corpus.cases.length, 'Every native fixture must return from the browser')
  mkdirSync(directory, { recursive: true })
  writeFileSync(new URL('browser.json', directory), JSON.stringify({ cases: exports }) + '\n')
})

test('removing one member synchronizes the member list across native group instances', () => {
  let document = structuredClone(corpus.cases.find(item => item.name === 'named-group').document)
  document.graph.nodeGroups = []
  document.graph.nodes.push({ ...newNode(false, 'MIRROR', 'inner', 100, 40), inputs: ['source'] })
  document.graph.nodes.find(node => node.id === 'cut').inputs = ['inner', 'source']
  document.graph = editNativeGroup(false, document, 'create', '', 'Zwei Knoten', ['inner', 'cut'])
  const definition = document.graph.nodeGroups[0].id
  document.graph = editNativeGroup(false, document, 'instantiate', definition, '', [])
  const entries = inspect('form', document)
  document = applyWires('form', document, entries, connections(entries).filter(wire => wire.source !== 'inner' && wire.target !== 'inner'), ['inner'])
  assert.equal(document.graph.nodes.filter(node => node.kind === 'MIRROR').length, 0)
  assert.ok(document.graph.nodeGroups.every(group => group.nodes.length === 1))
  inspect('form', document)
})
