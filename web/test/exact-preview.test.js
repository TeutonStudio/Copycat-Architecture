import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import * as THREE from 'three'
import { newNode, previewGraph } from '../src/generated/engine.mjs'
import { exactGeometry, textureFace } from '../src/preview-geometry.js'

const fixture = () => JSON.parse(readFileSync(new URL('./fixtures/crossed-roof-xor.json', import.meta.url)))
const operation = (doc, kind) => doc.graph.nodes.find(node => node.kind === kind)
const preview = (doc, kind) => previewGraph(false, doc, {}, operation(doc, kind).id)

function geometryVolume(geometry) {
  const points = geometry.getAttribute('position')
  let volume = 0
  for (let i = 0; i < points.count; i += 3) {
    const a = new THREE.Vector3().fromBufferAttribute(points, i)
    const b = new THREE.Vector3().fromBufferAttribute(points, i + 1)
    const c = new THREE.Vector3().fromBufferAttribute(points, i + 2)
    volume += a.dot(b.cross(c)) / 6
  }
  return volume * 4096
}
function volume(plan) {
  return plan.meshes.reduce((total, mesh) => {
    const geometry = exactGeometry(mesh.faces)
    const result = geometryVolume(geometry) * mesh.positions.length
    geometry.dispose()
    return total + result
  }, 0)
}
function rayHits(plan, y = 7, z = 8) {
  const ray = new THREE.Raycaster(new THREE.Vector3(-1, y / 16, z / 16), new THREE.Vector3(1, 0, 0))
  return plan.meshes.some(mesh => {
    const geometry = exactGeometry(mesh.faces)
    const material = new THREE.MeshBasicMaterial()
    const draw = new THREE.Mesh(geometry, Array(6).fill(material))
    draw.updateMatrixWorld()
    const hits = ray.intersectObject(draw).length
    geometry.dispose(); material.dispose()
    return hits > 0
  })
}

test('crossed roofs remove their intersection in the rendered XOR and union-minus-intersection', () => {
  const doc = fixture()
  const union = preview(doc, 'UNION'), xor = preview(doc, 'SYMMETRIC_DIFFERENCE')
  const constructed = preview(doc, 'DIFFERENCE'), intersection = preview(doc, 'INTERSECTION')
  // The conservative collision grid cannot distinguish these three shapes.
  assert.equal(union.count, 1376); assert.equal(xor.count, 1376); assert.equal(constructed.count, 1376)
  for (const plan of [union, xor, constructed, intersection]) {
    assert.ok(plan.meshes.every(mesh => mesh.faces.length > 0 && mesh.boxes.length === 0))
  }
  assert.ok(Math.abs(volume(union) - 928) < 0.001)
  assert.ok(Math.abs(volume(intersection) - 32) < 0.001)
  assert.ok(Math.abs(volume(xor) - 896) < 0.001)
  assert.ok(Math.abs(volume(constructed) - 896) < 0.001)
  assert.ok(rayHits(union)); assert.ok(rayHits(intersection))
  assert.equal(rayHits(xor), false); assert.equal(rayHits(constructed), false)
  assert.ok(rayHits(xor, 6, 7)); assert.ok(rayHits(constructed, 6, 7))
  assert.deepEqual(xor.meshes, constructed.meshes)
})

test('exact preview preserves material groups and output filters', () => {
  const doc = fixture()
  doc.groups.push({ id: 'second', name: 'Second', role: 'surface', priority: 1 })
  const xor = operation(doc, 'SYMMETRIC_DIFFERENCE')
  xor.inputGroups = ['body', 'second']
  const plan = preview(doc, 'SYMMETRIC_DIFFERENCE')
  assert.deepEqual(new Set(plan.meshes.map(mesh => mesh.material)), new Set(['body', 'second']))
  assert.ok(Math.abs(volume(plan) - 896) < 0.001)
  const output = operation(doc, 'OUTPUT')
  output.inputs = [xor.id]; output.previewGroup = 'second'
  const filtered = preview(doc, 'OUTPUT')
  assert.deepEqual(filtered.meshes.map(mesh => mesh.material), ['second'])
  assert.ok(filtered.meshes[0].faces.length > 0)
})

test('exact source meshes and repeated building placements use the same clipped surfaces', () => {
  const form = fixture()
  operation(form, 'OUTPUT').inputs = [operation(form, 'SYMMETRIC_DIFFERENCE').id]
  const doc = { schema: 2, name: 'Exact building', palette: { wall: 'minecraft:stone' }, graph: { schema: 1, nodes: [
    { id: 'empty_wall', kind: 'SEGMENT', block: 'minecraft:air', length: 1, height: 1 },
    { ...newNode(true, 'COPYCAT', 'source', 0, 0), form: 'cross@1' },
    { ...newNode(true, 'SEGMENT', 'wall', 0, 0), inputs: ['source'], length: 3, height: 1 },
    newNode(true, 'BUILDING', 'building', 0, 0),
  ] } }
  const plan = previewGraph(true, doc, { forms: { 'cross@1': form }, modules: {} }, 'wall')
  assert.equal(plan.count, 3)
  assert.ok(Math.abs(volume(plan) - 3 * 896) < 0.001)
  assert.ok(plan.meshes.every(mesh => mesh.material === 'minecraft:stone'))
})

test('empty exact XOR and ordinary voxel shapes retain their preview contract', () => {
  const doc = fixture(), xor = operation(doc, 'SYMMETRIC_DIFFERENCE')
  xor.inputs = [xor.inputs[0], xor.inputs[0]]
  assert.deepEqual(preview(doc, 'SYMMETRIC_DIFFERENCE').meshes, [])
  const plain = { schema: 3, name: 'Slab', graph: { schema: 2, nodes: [
    { ...newNode(false, 'INPUT', 'slab', 0, 0), part: { source: 'copycats:copycat_slab' } },
    { ...newNode(false, 'OUTPUT', 'result', 0, 0), inputs: ['slab'] },
  ] } }
  const plan = previewGraph(false, plain, {}, '')
  assert.equal(plan.count, 2048)
  assert.deepEqual(plan.meshes[0].boxes.map(box => Array.from(box)), [[0, 0, 0, 16, 8, 16]])
  assert.equal(plan.meshes[0].faces, undefined)
})

test('polygon triangulation corrects winding and binds normals UVs and six texture directions', () => {
  assert.deepEqual([[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]].map(textureFace), [0,1,2,3,4,5])
  assert.equal(textureFace([1, 3, -2]), 2)
  const faces = [{ normal: [-1, 0, 0], vertices: [[0,0,0],[0,16,0],[0,16,16],[0,0,16]] }]
  const geometry = exactGeometry(faces)
  assert.equal(geometry.getAttribute('position').count, 6)
  assert.deepEqual(geometry.groups, [{ start: 0, count: 6, materialIndex: 1 }])
  for (const attribute of ['position', 'normal', 'uv', 'blockCenter', 'blockSize'])
    assert.ok([...geometry.getAttribute(attribute).array].every(Number.isFinite))
  const ray = new THREE.Raycaster(new THREE.Vector3(-1, 0.5, 0.5), new THREE.Vector3(1, 0, 0))
  const material = new THREE.MeshBasicMaterial()
  assert.ok(ray.intersectObject(new THREE.Mesh(geometry, Array(6).fill(material))).length > 0)
  geometry.dispose(); material.dispose()
})
