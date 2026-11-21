import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import * as THREE from 'three'
import { previewGraph } from '../src/generated/engine.mjs'
import { listCopycatOptions, previewCopycatOption } from '../src/copycat-options.js'
import { createCopycatThumbnailMeshes } from '../src/copycat-thumbnails.js'

function renderContent(plan) {
  const box = new THREE.BoxGeometry()
  const meshes = createCopycatThumbnailMeshes(plan, box)
  meshes.forEach(mesh => mesh.updateMatrixWorld(true))
  return { meshes, box, dispose() {
    for (const geometry of new Set([box, ...meshes.map(mesh => mesh.geometry)])) geometry.dispose()
    for (const material of new Set(meshes.map(mesh => mesh.material))) material.dispose()
    meshes.forEach(mesh => mesh.dispose())
  } }
}
function rayHits(meshes, y = 7, z = 8) {
  const ray = new THREE.Raycaster(new THREE.Vector3(-1, y / 16, z / 16), new THREE.Vector3(1, 0, 0))
  return ray.intersectObjects(meshes).length > 0
}
function exactVolume(meshes) {
  let total = 0
  for (const mesh of meshes) {
    const points = mesh.geometry.getAttribute('position')
    let volume = 0
    for (let i = 0; i < points.count; i += 3) {
      const a = new THREE.Vector3().fromBufferAttribute(points, i)
      const b = new THREE.Vector3().fromBufferAttribute(points, i + 1)
      const c = new THREE.Vector3().fromBufferAttribute(points, i + 2)
      volume += a.dot(b.cross(c)) / 6
    }
    total += volume * 4096 * mesh.count
  }
  return total
}

test('every basic thumbnail contains drawable geometry, including a visible exact roof', () => {
  for (const option of listCopycatOptions().filter(option => option.kind === 'base')) {
    const plan = previewCopycatOption(option)
    const content = renderContent(plan)
    try {
      assert.ok(content.meshes.length > 0, `${option.name}: empty thumbnail`)
      assert.ok(content.meshes.every(mesh => mesh.count > 0 && mesh.geometry.getAttribute('position').count > 0))
      if (option.source === 'copycat_architect:copycat_roof') {
        assert.ok(plan.meshes.every(mesh => mesh.boxes.length === 0 && mesh.faces.length > 0))
        assert.ok(content.meshes.every(mesh => mesh.geometry !== content.box))
        assert.ok(rayHits(content.meshes))
      }
    } finally { content.dispose() }
  }
})

test('clipped exact thumbnails preserve the roof XOR opening rather than drawing collision boxes', () => {
  const document = JSON.parse(readFileSync(new URL('./fixtures/crossed-roof-xor.json', import.meta.url)))
  const xor = document.graph.nodes.find(node => node.kind === 'SYMMETRIC_DIFFERENCE')
  const content = renderContent(previewGraph(false, document, {}, xor.id))
  try {
    assert.ok(Math.abs(exactVolume(content.meshes) - 896) < 0.001)
    assert.equal(rayHits(content.meshes), false)
    assert.ok(rayHits(content.meshes, 6, 7))
  } finally { content.dispose() }
})

test('mixed raster and exact thumbnail surfaces preserve shared materials and instance placement', () => {
  const roof = listCopycatOptions().find(option => option.source === 'copycat_architect:copycat_roof')
  const exact = previewCopycatOption(roof).meshes[0]
  const plan = { meshes: [
    { material: 'body', boxes: [[0, 0, 0, 16, 8, 16]], positions: [[0, 0, 0], [2, 0, 0]] },
    { ...exact, material: 'body', positions: [[0, 1, 0], [2, 1, 0]] },
    { ...exact, material: 'cap', positions: [[0, 2, 0]] },
  ] }
  const before = JSON.stringify(plan), content = renderContent(plan)
  try {
    assert.equal(content.meshes.length, 3)
    const [boxes, roof, cap] = content.meshes
    assert.equal(boxes.geometry, content.box)
    assert.equal(boxes.count, 2)
    assert.equal(roof.count, 2)
    assert.equal(boxes.material, roof.material)
    assert.notEqual(cap.material, roof.material)
    const matrix = new THREE.Matrix4()
    boxes.getMatrixAt(1, matrix)
    assert.deepEqual(new THREE.Vector3().setFromMatrixPosition(matrix).toArray(), [2.5, 0.25, 0.5])
    roof.getMatrixAt(1, matrix)
    assert.deepEqual(new THREE.Vector3().setFromMatrixPosition(matrix).toArray(), [2, 1, 0])
    assert.equal(JSON.stringify(plan), before)
  } finally { content.dispose() }
  const empty = renderContent({ meshes: [] })
  try { assert.deepEqual(empty.meshes, []) } finally { empty.dispose() }
})
