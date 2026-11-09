import assert from 'node:assert/strict'
import test from 'node:test'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { applyCopycatOption } from '../src/copycat-selection.js'
import { connections, inspect } from '../src/model.js'

const group = (id, priority = 0) => ({ id, name: id, role: 'surface', priority })
const emptyLibrary = () => ({ forms: {}, modules: {} })
const volume = plan => plan.meshes.reduce((sum, mesh) => sum + mesh.positions.length * mesh.boxes.reduce(
  (result, [x, y, z, X, Y, Z]) => result + (X - x) * (Y - y) * (Z - z), 0), 0)
function freeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.freeze(value)
    Object.values(value).forEach(freeze)
  }
  return value
}
function basic(source = 'copycats:copycat_slab') {
  const name = source === 'copycats:copycat_slab' ? 'Stufe' : 'Vollblock'
  const document = { schema: 3, name, parts: [{ source }] }
  const entry = { id: `base_${source}`, name, mode: 'form', document, materials: {} }
  return { id: `base:${source}`, kind: 'base', category: 'basic', source, name, document, materials: {}, entry }
}
function saved(source = 'copycats:copycat_slab') {
  const entry = {
    id: 'my-saved-form', name: 'Meine gespeicherte Form', mode: 'form',
    document: { schema: 3, name: 'Anderer Dokumentname', groups: [group('shape')], parts: [{ source, group: 'shape' }] },
    materials: { shape: 'minecraft:oak_log[axis=x]' },
  }
  return { id: `saved:${entry.id}`, kind: 'saved', category: 'saved', name: entry.name, document: entry.document, materials: entry.materials, entry }
}
function form(nodes = [], groups = [group('body')]) {
  return {
    schema: 3, name: 'Auswahltest', groups,
    graph: { schema: 2, nodes: [...nodes, { ...newNode(false, 'OUTPUT', 'result', 500, 80), inputs: [nodes[0]?.id ?? ''] }] },
  }
}
function building() {
  return {
    schema: 2, name: 'Gebäude', palette: { wall: 'minecraft:stone', frame: 'minecraft:glass' },
    graph: { schema: 1, nodes: [
      { id: 'empty_wall', kind: 'SEGMENT', block: 'minecraft:air', length: 1, height: 1 },
      { ...newNode(true, 'COPYCAT', 'source', 130, 170), form: 'full@1', surfaces: { body: 'minecraft:gold_block' }, role: 'frame', materialMode: 'GROUPED', quarterTurns: 1 },
      { ...newNode(true, 'COPYCAT_ROTATE', 'rotate', 300, 170), inputs: ['source'], quarterTurns: 1 },
      newNode(true, 'BUILDING', 'building', 500, 40),
    ] },
  }
}
function referenceLibrary() {
  return { forms: {
    'full@1': { schema: 3, id: 'full', version: 1, name: 'Ganzer Block', parts: [{ source: 'copycats:copycat_block' }] },
    'slab@2': { schema: 3, id: 'slab', version: 2, name: 'Halbe Form', parts: [{ source: 'copycats:copycat_slab' }] },
  }, modules: {} }
}
function fromLibrary(library, reference) {
  const document = library.forms[reference]
  return { id: `library:${reference}`, kind: 'library', category: 'saved', reference, name: document.name, document, materials: {},
    entry: { id: `library_${reference}`, name: document.name, mode: 'form', document, materials: {} } }
}

test('choosing a basic shape resets geometry and retains the connected source, group and materials', () => {
  const source = newNode(false, 'INPUT', 'source', 130, 170)
  source.part = { ...source.part, source: 'copycats:copycat_slab', group: 'existing', turn: 3, top: true, spin: 1, offset: { x: 0, y: -2, z: 0 } }
  Object.assign(source, { rotationAxis: 'Y', quarterTurns: 1, note: 'Keep this node metadata' })
  const document = freeze(form([source], [group('existing')]))
  const library = freeze(emptyLibrary())
  const materials = freeze({ existing: 'minecraft:gold_block' })
  const before = JSON.stringify({ document, library, materials })
  const result = applyCopycatOption({ mode: 'form', document, library, materials, nodeId: 'source', option: freeze(basic()) })
  const changed = inspect('form', result.document).find(({ node }) => node.id === 'source').node
  assert.equal(changed.kind, 'INPUT')
  assert.equal(changed.part.source, 'copycats:copycat_slab')
  assert.equal(changed.part.group, 'existing')
  assert.deepEqual([changed.part.turn, changed.part.spin, changed.part.tilt, changed.part.top, changed.part.size, changed.quarterTurns], [0, 0, 0, false, 1, 0])
  assert.deepEqual(changed.part.offset, { x: 0, y: 0, z: 0 })
  assert.deepEqual([changed.x, changed.y], [130, 170])
  assert.equal(result.document.graph.nodes.find(node => node.id === 'source').note, source.note)
  assert.deepEqual(connections(inspect('form', result.document)), connections(inspect('form', document)))
  assert.equal(volume(previewGraph(false, result.document, {}, 'source')), 2048)
  assert.equal(result.materials, materials)
  assert.equal(result.library, library)
  assert.equal(JSON.stringify({ document, library, materials }), before)
})

test('a saved selection converts an INPUT in place, uses its displayed name and remaps source materials', () => {
  const source = newNode(false, 'INPUT', 'source', 130, 170)
  source.quarterTurns = 2
  const other = { ...newNode(false, 'INPUT', 'other', 50, 280), note: 'Unchanged other node' }
  const union = { ...newNode(false, 'UNION', 'union', 300, 100), inputs: ['source', 'other'] }
  const document = form([source, other, union])
  document.graph.nodes.at(-1).inputs = ['union']
  freeze(document)
  const result = applyCopycatOption({ mode: 'form', document, library: emptyLibrary(), materials: { body: 'minecraft:stone' }, nodeId: 'source', option: saved() })
  const changed = result.document.graph.nodes.find(node => node.id === 'source')
  const mapped = changed.savedCopycat.parts[0].group
  assert.equal(changed.kind, 'SAVED')
  assert.equal(changed.savedCopycat.name, 'Meine gespeicherte Form')
  assert.equal(changed.quarterTurns, 0)
  assert.notEqual(mapped, 'shape')
  assert.deepEqual([changed.x, changed.y], [130, 170])
  assert.deepEqual(result.materials, { body: 'minecraft:stone', [mapped]: 'minecraft:oak_log[axis=x]' })
  assert.deepEqual(connections(inspect('form', result.document)), connections(inspect('form', document)))
  assert.equal(result.document.graph.nodes.find(node => node.id === 'other'), other)
  assert.equal(volume(previewGraph(false, result.document, {}, 'source')), 2048)
})

test('switching a saved source to a basic shape retires only its unused groups', () => {
  for (const keepCap of [false, true]) {
    const source = { ...newNode(false, 'SAVED', 'source', 20, 30), savedCopycat: { id: 'old', version: 1, name: 'Alte Form', parts: [
      { source: 'copycats:copycat_slab', group: 'base' },
      { source: 'copycats:copycat_layer', group: 'cap', top: true },
    ] } }
    const other = newNode(false, 'INPUT', 'other', 20, 250)
    other.part.group = keepCap ? 'cap' : 'unrelated'
    const document = freeze(form([source, other], [group('base'), group('cap', 1), group('unrelated', 2)]))
    const result = applyCopycatOption({ mode: 'form', document, nodeId: 'source', option: basic(),
      materials: { base: 'minecraft:stone', cap: 'minecraft:glass', unrelated: 'minecraft:oak_planks' } })
    const changed = result.document.graph.nodes.find(node => node.id === 'source')
    assert.equal(changed.kind, 'INPUT')
    assert.equal(changed.savedCopycat, null)
    assert.equal(changed.part.group, 'base')
    assert.equal(result.document.groups.some(group => group.id === 'cap'), keepCap)
    assert.equal(Object.hasOwn(result.materials, 'cap'), keepCap)
    assert.equal(result.materials.unrelated, 'minecraft:oak_planks')
    assert.equal(result.document.graph.nodes.find(node => node.id === 'other'), other)
    assert.equal(volume(previewGraph(false, result.document, {}, 'source')), 2048)
  }
})

test('replacing a SAVED source preserves explicit group settings and clears source rotation', () => {
  const source = { ...newNode(false, 'SAVED', 'source', 70, 80), materialMode: 'GROUPED', materialGroup: 'body', rotationAxis: 'X', quarterTurns: 2 }
  const document = freeze(form([source]))
  const result = applyCopycatOption({ mode: 'form', document, materials: { body: 'minecraft:stone' }, nodeId: 'source', option: saved() })
  const changed = result.document.graph.nodes.find(node => node.id === 'source')
  assert.equal(changed.materialMode, 'GROUPED')
  assert.equal(changed.materialGroup, 'body')
  assert.equal(changed.rotationAxis, 'NORMAL')
  assert.equal(changed.quarterTurns, 0)
  assert.ok(result.document.groups.some(group => group.id === 'body'))
  assert.equal(result.materials.body, 'minecraft:stone')
  assert.equal(changed.savedCopycat.name, 'Meine gespeicherte Form')
  assert.equal(volume(previewGraph(false, result.document, {}, 'source')), 2048)
})

test('a building library selection retains the exact version and downstream transforms', () => {
  const library = freeze(referenceLibrary())
  const document = freeze(building())
  const result = applyCopycatOption({ mode: 'building', document, library, nodeId: 'source', option: fromLibrary(library, 'slab@2') })
  const changed = result.document.graph.nodes.find(node => node.id === 'source')
  assert.equal(changed.form, 'slab@2')
  assert.equal(changed.name, 'Halbe Form')
  assert.equal(changed.role, 'frame')
  assert.equal(changed.materialMode, 'GROUPED')
  assert.equal(changed.quarterTurns, 0)
  assert.deepEqual(changed.surfaces, {})
  assert.deepEqual([changed.x, changed.y], [130, 170])
  assert.equal(result.library, library)
  assert.equal(result.document.graph.nodes.find(node => node.id === 'rotate'), document.graph.nodes.find(node => node.id === 'rotate'))
  assert.equal(volume(previewGraph(true, result.document, library, 'source')), 2048)
  const sameReference = applyCopycatOption({ mode: 'building', document, library, nodeId: 'source', option: fromLibrary(library, 'full@1') })
  assert.deepEqual(sameReference.document.graph.nodes.find(node => node.id === 'source').surfaces, { body: 'minecraft:gold_block' })
})

test('basic and saved building choices create immutable source snapshots', () => {
  const document = freeze(building())
  const library = freeze(referenceLibrary())
  const first = applyCopycatOption({ mode: 'building', document, library, nodeId: 'source', option: saved() })
  const key = first.document.graph.nodes.find(node => node.id === 'source').form
  assert.notEqual(key, 'slab@2')
  assert.equal(first.library.forms[key].name, 'Meine gespeicherte Form')
  assert.deepEqual(first.document.graph.nodes.find(node => node.id === 'source').surfaces, { shape: 'minecraft:oak_log' })
  const second = applyCopycatOption({ mode: 'building', document: first.document, library: first.library, nodeId: 'source', option: saved('copycats:copycat_block') })
  assert.notEqual(second.document.graph.nodes.find(node => node.id === 'source').form, key)
  assert.equal(volume(previewGraph(true, first.document, second.library, 'source')), 2048)
  assert.equal(volume(previewGraph(true, second.document, second.library, 'source')), 4096)
  const third = applyCopycatOption({ mode: 'building', document: second.document, library: second.library, nodeId: 'source', option: basic() })
  assert.deepEqual(third.document.graph.nodes.find(node => node.id === 'source').surfaces, {})
  assert.equal(volume(previewGraph(true, third.document, third.library, 'source')), 2048)
  assert.equal(document.graph.nodes.find(node => node.id === 'source').form, 'full@1')
  assert.equal(Object.keys(library.forms).length, 2)
})

test('selection without a target creates the appropriate source only in the returned document', () => {
  const library = freeze(referenceLibrary())
  for (const option of [basic(), saved(), fromLibrary(library, 'slab@2')]) {
    for (const mode of ['form', 'building']) {
      const document = freeze(mode === 'form' ? form() : building())
      const count = document.graph.nodes.length
      const result = applyCopycatOption({ mode, document, library, option })
      const created = result.document.graph.nodes.find(node => node.id === result.nodeId)
      assert.equal(result.document.graph.nodes.length, count + 1)
      assert.equal(document.graph.nodes.length, count)
      assert.equal(created.kind, mode === 'building' ? 'COPYCAT' : option.kind === 'base' ? 'INPUT' : 'SAVED')
      assert.deepEqual([created.x, created.y], [40, 100])
      assert.equal(volume(previewGraph(mode === 'building', result.document, result.library, result.nodeId)), 2048)
      if (option.kind === 'library' && mode === 'building') assert.equal(created.form, 'slab@2')
    }
  }
})

test('invalid choices and exceeded source limits leave all input state unchanged', () => {
  const document = freeze(form([newNode(false, 'INPUT', 'source', 0, 0)]))
  const library = freeze(referenceLibrary())
  const materials = freeze({ body: 'minecraft:stone' })
  const before = JSON.stringify({ document, library, materials })
  const args = { mode: 'form', document, library, materials, nodeId: 'source' }
  assert.throws(() => applyCopycatOption({ ...args, option: null }), /auswählen/)
  assert.throws(() => applyCopycatOption({ ...args, nodeId: 'result', option: basic() }), /Quellknoten/)
  assert.throws(() => applyCopycatOption({ ...args, nodeId: 'missing', option: basic() }), /Quellknoten/)
  assert.throws(() => applyCopycatOption({ ...args, option: { ...saved(), document: { schema: 3, name: 'Invalid', parts: [{ source: 'missing:shape' }] } } }), /Unsupported base shape/)
  assert.throws(() => applyCopycatOption({ ...args, option: { ...fromLibrary(library, 'slab@2'), reference: 'removed@1' } }), /nicht mehr/)
  assert.throws(() => applyCopycatOption({ ...args, option: { ...saved(), materials: { shape: 'invalid material' } } }), /Material|material|Block|block/)
  assert.equal(JSON.stringify({ document, library, materials }), before)

  const full = freeze(form(Array.from({ length: 32 }, (_, i) => newNode(false, 'INPUT', `source${i}`, 0, i * 20))))
  assert.throws(() => applyCopycatOption({ mode: 'form', document: full, option: basic() }), /32 source/)
  assert.throws(() => applyCopycatOption({ mode: 'form', document: full, option: saved() }), /32 source/)
  const replaced = applyCopycatOption({ mode: 'form', document: full, nodeId: 'source0', option: saved() })
  assert.equal(replaced.document.graph.nodes.filter(node => ['INPUT', 'SAVED'].includes(node.kind)).length, 32)
  assert.equal(full.graph.nodes[0].kind, 'INPUT')
})
