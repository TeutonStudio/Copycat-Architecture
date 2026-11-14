import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { arrangementGraph, newNode, previewGraph } from '../src/generated/engine.mjs'
import { applyWires, connections, inspect, readGraph, writeGraph } from '../src/model.js'
import { arrangementPath, fitArrangement, moveArrangement } from '../src/arrangement.js'

const library = JSON.parse(readFileSync(new URL('../../examples/library.json', import.meta.url)))
const document = () => ({ schema: 2, palette: { wall: 'minecraft:stone' }, graph: { schema: 1, nodes: [
  { ...newNode(true, 'SEGMENT', 'empty_wall', 0, 0), block: 'minecraft:air', length: 1, height: 1 },
  newNode(true, 'BUILDING', 'building', 0, 0),
  { ...newNode(true, 'SEGMENT', 'a', 0, 0), module: '', form: 'full@1', length: 3, height: 2 },
  { ...newNode(true, 'SEGMENT', 'b', 0, 0), module: '', form: 'full@1', length: 1, height: 2 }
] } })

for (const axis of ['X', 'Y', 'Z']) test(`segment ${axis} rotation survives wiring/export and inverse rotation`, () => {
  const d = document()
  d.graph.nodes.push({ ...newNode(true, 'SEGMENT_ROTATE', 'rotated', 0, 0), inputs: ['a'], rotationAxis: axis, quarterTurns: 1 },
    { ...newNode(true, 'SEGMENT_ROTATE', 'inverse', 0, 0), inputs: ['rotated'], rotationAxis: axis, quarterTurns: 3 })
  const round = readGraph(JSON.parse(JSON.stringify(writeGraph('building', d))), 'building')
  assert.deepEqual(previewGraph(true, round, library, 'inverse'), previewGraph(true, d, library, 'a'))
  assert.deepEqual(applyWires('building', d, inspect('building', d), connections(inspect('building', d))), d)
  assert.equal(inspect('building', d).find(({ node }) => node.id === 'rotated').ports.length, 2)
})

test('segment mirror preserves positions-only mode and mirrors asymmetric source geometry', () => {
  const d = document()
  const lib = { forms: { ...library.forms, 'half@1': { schema: 3, parts: [{ source: 'copycats:copycat_slab' }] } } }
  d.graph.nodes.find(n => n.id === 'a').form = 'half@1'
  d.graph.nodes.push({ ...newNode(true, 'SEGMENT_MIRROR', 'mirror', 0, 0), inputs: ['a'], rotationAxis: 'Y', mirrorPositionsOnly: true })
  const positionsOnly = previewGraph(true, d, lib, 'mirror')
  d.graph.nodes.at(-1).mirrorPositionsOnly = false
  const fullMirror = previewGraph(true, d, lib, 'mirror')
  assert.notDeepEqual(fullMirror.meshes.map(m => m.boxes), positionsOnly.meshes.map(m => m.boxes))
  const round = readGraph(JSON.parse(JSON.stringify(writeGraph('building', d))), 'building')
  assert.equal(round.graph.nodes.at(-1).mirrorPositionsOnly, false)
  assert.deepEqual(previewGraph(true, round, lib, 'mirror'), fullMirror)
})

test('segment arrangement inherits sequential placement and exports all three coordinates and rotations', () => {
  const d = document()
  const pattern = { ...newNode(true, 'PATTERN', 'pattern', 0, 0), inputs: ['a', 'b'] }
  d.graph.nodes.push(pattern)
  const initial = arrangementGraph(d, library, 'pattern', 'Z')
  assert.deepEqual(initial.transforms.map(t => t.offset), [{ x: 0, y: 0, z: 0 }, { x: 3, y: 0, z: 0 }])
  assert.ok(initial.items[0].cells.some(([x, y]) => x === 2 && y === -2))
  pattern.patternAxis = 'transform'
  pattern.transforms = initial.transforms.map((t, i) => ({ ...t, offset: { x: -4 + i * 3, y: 2, z: 5 }, axis: 'X', quarterTurns: 1 }))
  const round = readGraph(JSON.parse(JSON.stringify(writeGraph('building', d))), 'building')
  assert.deepEqual(round.graph.nodes.at(-1).transforms, pattern.transforms)
  for (const plane of ['X', 'Y', 'Z']) assert.deepEqual(arrangementGraph(round, library, 'pattern', plane), arrangementGraph(d, library, 'pattern', plane))
  assert.ok(previewGraph(true, round, library, 'pattern').count > 0)
  assert.deepEqual(applyWires('building', round, inspect('building', round), connections(inspect('building', round))), round)
  pattern.transforms[0].offset.x = 129
  assert.throws(() => inspect('building', d), /Invalid segment transformation/)
})

test('room grid moves the chosen input instance, including repeated sources, without mutating the room', () => {
  const d = JSON.parse(readFileSync(new URL('../../examples/building-graph.json', import.meta.url)))
  const storey = d.graph.nodes.find(n => n.kind === 'STOREY')
  storey.inputs = [storey.inputs[0], storey.inputs[0]]
  storey.offsets = [{ x: 0, y: 0, z: 0 }, { x: 8, y: 0, z: -3 }]
  const result = arrangementGraph(d, library, storey.id, 'Y')
  assert.equal(result.items.length, 2)
  const shift = result.items[0].cells.map(([x, z]) => [x + 8, z - 3])
  assert.deepEqual(result.items[1].cells, shift)
})

test('2D dragging keeps the hidden coordinate and respects native bounds', () => {
  const offset = { x: 3, y: 7, z: 9 }
  assert.deepEqual(moveArrangement(offset, 'Z', 2, -3), { x: 5, y: 10, z: 9 })
  assert.deepEqual(moveArrangement(offset, 'X', 2, -3), { x: 3, y: 10, z: 11 })
  assert.deepEqual(moveArrangement(offset, 'Y', 2, -3), { x: 5, y: 7, z: 6 })
  assert.deepEqual(moveArrangement(offset, 'Y', 999, -999, 64), { x: 64, y: 7, z: -64 })
  assert.deepEqual(offset, { x: 3, y: 7, z: 9 })
  assert.equal(arrangementPath([[0, 0], [1, 0], [4, 0]]), 'M0,0h2v1h-2zM4,0h1v1h-1z')
  assert.deepEqual(fitArrangement([{ cells: [[0, 0], [3, 1]] }]), [-2, -3, 8, 8])
})

test('wall segment grid arranges connected Copycats and retains depth, materials and legacy repetition', () => {
  const d = document()
  d.graph.nodes.push({...newNode(true, 'COPYCAT', 'stone', 0, 0), block:'minecraft:stone'},
    {...newNode(true, 'COPYCAT', 'glass', 0, 0), block:'minecraft:glass'})
  const wall = d.graph.nodes.find(n => n.id === 'a')
  wall.inputs = ['stone','glass']; wall.length=4; wall.height=3; wall.depth=2
  assert.equal(previewGraph(true,d,library,'a').count,24)
  const initial=arrangementGraph(d,library,'a','Z')
  assert.deepEqual(initial.transforms.map(t=>t.offset),[{x:0,y:0,z:0},{x:1,y:0,z:0}])
  wall.copycatArrangement=true; wall.offsets=[{x:1,y:0,z:0},{x:3,y:2,z:1}]
  assert.deepEqual(arrangementGraph(d,library,'a','Y').items.map(i=>i.cells),[[[1,0]],[[3,1]]])
  const arranged=previewGraph(true,d,library,'a')
  assert.equal(arranged.count,2)
  const round=readGraph(JSON.parse(JSON.stringify(writeGraph('building',d))),'building')
  assert.deepEqual(previewGraph(true,round,library,'a'),arranged)
  assert.equal(round.graph.nodes.find(n=>n.id==='a').depth,2)
  wall.offsets[1].x=4
  assert.throws(()=>previewGraph(true,d,library,'a'),/segment dimensions/)
})
