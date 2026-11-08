import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { saveNodeGroup, insertNodeGroup, mergeLibraries } from '../src/node-groups.js'
import { connections, inspect } from '../src/model.js'
const fixture = name => JSON.parse(readFileSync(new URL(`../../examples/${name}.json`, import.meta.url)))

test('copycat groups remap internal links and materials while dropping external inputs', () => {
  const document = fixture('nodegraph-form')
  const source = document.graph.nodes.find(node => node.kind === 'INPUT')
  const operation = document.graph.nodes.find(node => node.kind === 'DIFFERENCE')
  const template = saveNodeGroup('form', document, [source.id, operation.id], 'Quelle und Differenz', { body: 'minecraft:stone' })
  let next = 0
  const inserted = insertNodeGroup(document, template, () => `copy_${++next}`)
  const entries = inspect('form', inserted.document)
  const links = connections(entries).filter(wire => wire.target.startsWith('copy_'))
  assert.equal(links.length, 1)
  assert.equal(links[0].source, 'copy_1')
  assert.equal(links[0].target, 'copy_2')
  assert.equal(inserted.materials.body, 'minecraft:stone')
  assert.equal(entries.filter(({ node }) => node.kind === 'OUTPUT').length, 1)
})

test('building groups remap copycat inputs without duplicating the protected root', () => {
  const document = fixture('building-graph-assembly')
  const source = document.graph.nodes.find(node => node.kind === 'COPYCAT')
  const wall = document.graph.nodes.find(node => node.kind === 'SEGMENT' && node.id !== 'empty_wall')
  const template = saveNodeGroup('building', document, [source.id, wall.id, 'building', 'empty_wall'], 'Wand')
  assert.equal(template.nodes.length, 2)
  let next = 0
  const inserted = insertNodeGroup(document, template, () => `copy_${++next}`).document
  const links = connections(inspect('building', inserted)).filter(wire => wire.target.startsWith('copy_'))
  assert.ok(links.some(wire => wire.source === 'copy_1' && wire.target === 'copy_2'))
  assert.equal(inserted.graph.nodes.filter(node => node.kind === 'BUILDING').length, 1)
})

test('group insertion resolves conflicting group IDs and priorities', () => {
  const document = fixture('nodegraph-form')
  const source = document.graph.nodes.find(node => node.kind === 'INPUT')
  const template = saveNodeGroup('form', document, [source.id], 'Material', { body: 'minecraft:oak_planks' })
  template.groups[0].name = 'Anderes Material'
  const result = insertNodeGroup(document, template, () => 'new_source')
  const group = result.document.groups.find(group => group.name === 'Anderes Material')
  assert.notEqual(group.id, 'body')
  assert.equal(new Set(result.document.groups.map(group => group.priority)).size, result.document.groups.length)
  assert.equal(result.materials[group.id], 'minecraft:oak_planks')
  assert.equal(result.document.graph.nodes.find(node => node.id === 'new_source').part.group, group.id)
})

test('priority collisions preserve foreground order even when template fields are unsorted', () => {
  const document = fixture('nodegraph-form')
  document.groups = [{ id: 'body', name: 'Existing', role: 'surface', priority: 10 }]
  const source = document.graph.nodes.find(node => node.kind === 'INPUT')
  const template = saveNodeGroup('form', document, [source.id], 'Priority')
  template.groups = [{ id: 'body', name: 'Upper', role: 'surface', priority: 10 }, { id: 'lower', name: 'Lower', role: 'surface', priority: -10 }]
  const result = insertNodeGroup(document, template, () => 'new_source').document
  assert.ok(result.groups.find(group => group.name === 'Lower').priority < result.groups.find(group => group.name === 'Upper').priority)
})

test('building templates carry their library without silently replacing another version', () => {
  const document = fixture('building-graph-assembly'), library = fixture('library')
  const source = document.graph.nodes.find(node => node.kind === 'COPYCAT')
  const template = saveNodeGroup('building', document, [source.id], 'Source', {}, library)
  assert.deepEqual(mergeLibraries({ forms: {}, modules: {} }, template.library), library)
  assert.throws(() => mergeLibraries({ forms: { a: { version: 1 } } }, { forms: { a: { version: 2 } } }), /Bibliothekskonflikt/)
})
