import assert from 'node:assert/strict'
import { readFileSync, existsSync, readdirSync } from 'node:fs'
import test from 'node:test'

const root = new URL('../../docs/wiki/', import.meta.url)
const read = name => readFileSync(new URL(name, root), 'utf8')
const contract = JSON.parse(read('reference/graph-models-2026-11-08.json'))

for (const [category, file] of [['copycat', 'Knoten-Formgraph.md'], ['building', 'Knoten-Gebaeudegraph.md']]) {
  test(`${category} reference covers every current node, port and inspector field`, () => {
    const text = read(file)
    for (const [id, node] of Object.entries(contract[category].nodes)) {
      const section = text.split(`\n## ${id}\n`)[1]?.split('\n## ')[0]
      assert.ok(section, `${file}: missing ${id}`)
      assert.match(section, /Verfügbarkeit/)
      for (const field of Object.keys(node.fields ?? {})) assert.ok(section.includes('`' + field + '`'), `${id}: missing field ${field}`)
      for (const port of node.ports ?? []) assert.ok(section.includes('`' + port.key + '`'), `${id}: missing port ${port.key}`)
    }
  })
}

test('semantic inspector references and segment operators have explicit documentation', () => {
  const text = read('Knoten-Eigenschaften.md')
  for (const port of contract.building.semanticPorts) assert.ok(text.includes('`' + port.key + '`'), port.key)
  const planned = read('Geplante-Segmentoperatoren.md')
  assert.match(planned, /Status: implementiert/)
  for (const id of ['SEGMENT_UNION', 'SEGMENT_INTERSECTION', 'SEGMENT_DIFFERENCE', 'SEGMENT_SYMMETRIC_DIFFERENCE']) {
    assert.ok(planned.includes(`## ${id}`))
    assert.ok(contract.building.nodes[id])
  }
})

test('all local wiki links and images resolve in the published repository source', () => {
  for (const file of readdirSync(root).filter(name => name.endsWith('.md'))) {
    for (const [, target] of read(file).matchAll(/\]\(([^\s)]+)\)/g)) {
      if (/^(https?:|mailto:|#)/.test(target)) continue
      assert.ok(existsSync(new URL(target.split('#')[0], root)), `${file}: ${target}`)
    }
  }
})
