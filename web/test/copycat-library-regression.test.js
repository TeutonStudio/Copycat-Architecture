import assert from 'node:assert/strict'
import test from 'node:test'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { loadBuildingCopycat, loadFormCopycatWithMaterials } from '../src/copycat-library.js'
import { readGraph, writeGraph } from '../src/model.js'
import { emptyWorkspace, validateWorkspace } from '../src/storage.js'
import { insertNodeGroup, saveNodeGroup } from '../src/node-groups.js'

const emptyLibrary = () => ({ forms: {}, modules: {} })
const building = () => ({
  schema: 2, name: 'Saved-source regression', palette: { wall: 'minecraft:stone' },
  graph: { schema: 1, nodes: [
    { id: 'empty_wall', kind: 'SEGMENT', block: 'minecraft:air', length: 1, height: 1 },
    newNode(true, 'BUILDING', 'building', 0, 0),
  ] },
})
const savedEntry = materials => ({
  id: 'saved-slab', name: 'Saved slab', mode: 'form', materials,
  document: readGraph({ schema: 3, name: 'Saved slab', parts: [{ source: 'copycats:copycat_slab' }] }, 'form'),
})
const snapshot = value => JSON.parse(JSON.stringify(value))

test('a cleared saved-form material falls back to the building palette', () => {
  const loaded = loadBuildingCopycat(building(), emptyLibrary(), savedEntry({ body: '' }))
  const source = loaded.document.graph.nodes.find(node => node.id === loaded.nodeId)
  assert.deepEqual(source.surfaces, {})
  const preview = previewGraph(true, loaded.document, loaded.library, loaded.nodeId)
  assert.deepEqual([...new Set(preview.meshes.map(mesh => mesh.material))], ['minecraft:stone'])
})

test('saved Copycat snapshots survive workspace serialization and later source edits', () => {
  const entry = savedEntry({ body: 'minecraft:oak_log[axis=x]' })
  const loaded = loadBuildingCopycat(building(), emptyLibrary(), entry)
  const expected = previewGraph(true, loaded.document, loaded.library, loaded.nodeId)
  const workspace = validateWorkspace(snapshot({
    ...emptyWorkspace(),
    drafts: { building: { mode: 'building', document: loaded.document, library: loaded.library, materials: {} } },
    graphs: [entry],
  }))
  workspace.graphs[0].document = readGraph({ schema: 3, name: 'Changed', parts: [{ source: 'copycats:copycat_block' }] }, 'form')
  workspace.graphs[0].materials.body = 'minecraft:gold_block'
  const restored = workspace.drafts.building
  const document = readGraph(snapshot(writeGraph('building', restored.document)), 'building')
  assert.deepEqual(previewGraph(true, document, restored.library, loaded.nodeId), expected)
  assert.deepEqual([...new Set(expected.meshes.map(mesh => mesh.material))], ['minecraft:oak_log'])
})

test('an invalid imported shape leaves both the destination graph and its library intact', () => {
  const initial = loadBuildingCopycat(building(), emptyLibrary(), savedEntry({ body: 'minecraft:stone' }))
  const before = snapshot(initial)
  const invalid = { ...savedEntry({}), document: { schema: 3, name: 'Invalid', parts: [{ source: 'missing:shape' }] } }
  assert.throws(() => loadBuildingCopycat(initial.document, initial.library, invalid, initial.nodeId), /Unsupported base shape/)
  assert.deepEqual(snapshot(initial), before)
})

test('loading a saved form remaps its material assignments and retains unrelated target materials', () => {
  const retained = newNode(false, 'INPUT', 'retained', 0, 0)
  retained.part.group = 'existing'
  const target = {
    schema: 3, name: 'Material target',
    groups: [
      { id: 'body', name: 'Body', role: 'surface', priority: 0 },
      { id: 'existing', name: 'Existing', role: 'surface', priority: 1 },
    ],
    graph: { schema: 2, nodes: [
      retained, newNode(false, 'SAVED', 'saved', 0, 0),
      { ...newNode(false, 'UNION', 'union', 0, 0), inputs: ['retained', 'saved'] },
      { ...newNode(false, 'OUTPUT', 'result', 0, 0), inputs: ['union'] },
    ] },
  }
  const loaded = loadFormCopycatWithMaterials(target, savedEntry({ body: 'minecraft:oak_log[axis=x]' }), 'saved', {
    body: 'minecraft:dirt', existing: 'minecraft:gold_block', stale: 'minecraft:glass',
  })
  const source = loaded.document.graph.nodes.find(node => node.id === 'saved')
  const group = source.savedCopycat.parts[0].group
  assert.notEqual(group, 'body')
  assert.equal(loaded.nodeId, 'saved')
  assert.deepEqual(loaded.materials, { existing: 'minecraft:gold_block', [group]: 'minecraft:oak_log[axis=x]' })
  const replacement = loadFormCopycatWithMaterials(loaded.document, savedEntry({ body: 'minecraft:copper_block' }), 'saved', loaded.materials)
  const replacementGroup = replacement.document.graph.nodes.find(node => node.id === 'saved').savedCopycat.parts[0].group
  assert.notEqual(replacementGroup, group)
  assert.deepEqual(replacement.materials, { existing: 'minecraft:gold_block', [replacementGroup]: 'minecraft:copper_block' })
})

test('a reusable SAVED node keeps its snapshot groups and remaps conflicting destination groups', () => {
  const target = readGraph({ schema: 3, name: 'Target', parts: [{ source: 'copycats:copycat_slice' }] }, 'form')
  target.graph.nodes.push(newNode(false, 'SAVED', 'saved', 0, 0))
  const loaded = loadFormCopycatWithMaterials(target, savedEntry({ body: 'minecraft:oak_log[axis=x]' }), 'saved', { body: 'minecraft:stone' })
  const snapshotGroupId = loaded.document.graph.nodes.find(node => node.id === 'saved').savedCopycat.parts[0].group
  const snapshotGroup = loaded.document.groups.find(group => group.id === snapshotGroupId)
  const template = saveNodeGroup('form', loaded.document, ['saved'], 'Reusable saved source', loaded.materials)
  assert.ok(template.groups.some(group => group.id === snapshotGroupId))
  const destination = readGraph({
    schema: 3, name: 'Destination', groups: [{ ...snapshotGroup, name: 'Unrelated destination group' }],
    parts: [{ source: 'copycats:copycat_byte', group: snapshotGroupId }],
  }, 'form')
  const inserted = insertNodeGroup(destination, template, () => 'reused')
  const source = inserted.document.graph.nodes.find(node => node.id === 'reused')
  const mappedGroup = source.savedCopycat.parts[0].group
  assert.notEqual(mappedGroup, snapshotGroupId)
  assert.equal(inserted.materials[mappedGroup], 'minecraft:oak_log[axis=x]')
  assert.deepEqual(previewGraph(false, inserted.document, {}, 'reused').meshes.map(mesh => mesh.material), [mappedGroup])
})
