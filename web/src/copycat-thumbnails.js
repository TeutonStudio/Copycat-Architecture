import * as THREE from 'three'
import { exactGeometry } from './preview-geometry.js'

const colors = {
  body: '#8ba4a2', cap: '#d29763', 'minecraft:stone_bricks': '#979999',
  'minecraft:deepslate_tiles': '#434e59', 'minecraft:oak_planks': '#bd9163',
  'minecraft:glass': '#a6dceb', 'minecraft:stone': '#a1a6ad',
  'minecraft:spruce_planks': '#846247', 'minecraft:bricks': '#aa6557',
}
function color(value) {
  const id = value.split('[')[0]
  if (colors[id]) return colors[id]
  let hash = 0
  for (const char of id) hash = Math.imul(hash, 31) + char.charCodeAt(0) | 0
  return new THREE.Color().setHSL((hash >>> 0) % 360 / 360, 0.32, 0.59)
}

// Use the same exact polygons as the main viewer. Roofs and clipped forms can
// have faces without any raster boxes; both representations may share a material.
export function createCopycatThumbnailMeshes(plan, boxGeometry) {
  const draws = [], batches = new Map(), matrix = new THREE.Matrix4()
  for (const mesh of plan.meshes) {
    const list = batches.get(mesh.material) || []
    list.push(mesh)
    batches.set(mesh.material, list)
  }
  for (const [material, meshes] of batches) {
    const count = meshes.reduce((sum, mesh) => sum + mesh.boxes.length * mesh.positions.length, 0)
    const exact = meshes.filter(mesh => mesh.faces?.length && mesh.positions.length)
    if (!count && !exact.length) continue
    const surface = new THREE.MeshLambertMaterial({ color: color(material) })
    if (count) {
      const draw = new THREE.InstancedMesh(boxGeometry, surface, count)
      let index = 0
      for (const mesh of meshes) for (const [px, py, pz] of mesh.positions) {
        for (const [x, y, z, maxX, maxY, maxZ] of mesh.boxes) {
          matrix.makeScale((maxX - x) / 16, (maxY - y) / 16, (maxZ - z) / 16)
          matrix.setPosition(px + (x + maxX) / 32, py + (y + maxY) / 32, pz + (z + maxZ) / 32)
          draw.setMatrixAt(index++, matrix)
        }
      }
      draw.instanceMatrix.needsUpdate = true
      draw.computeBoundingSphere()
      draws.push(draw)
    }
    for (const mesh of exact) {
      const geometry = exactGeometry(mesh.faces)
      geometry.clearGroups() // Thumbnail surfaces use one untextured material.
      const draw = new THREE.InstancedMesh(geometry, surface, mesh.positions.length)
      mesh.positions.forEach(([x, y, z], index) => draw.setMatrixAt(index, matrix.makeTranslation(x, y, z)))
      draw.instanceMatrix.needsUpdate = true
      draw.computeBoundingSphere()
      draws.push(draw)
    }
  }
  return draws
}

// Cards contain images. All images are rendered, on demand, through this one
// canvas; neither textures nor an animation loop are needed for thumbnails.
export function createCopycatThumbnailRenderer() {
  let renderer
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true, powerPreference: 'low-power' })
  } catch {
    throw new Error('Miniatur-Vorschauen benötigen WebGL 2, das in diesem Browser nicht verfügbar ist.')
  }
  renderer.setPixelRatio(1)
  renderer.setSize(160, 160, false)
  const scene = new THREE.Scene()
  scene.background = new THREE.Color('#17232c')
  scene.add(new THREE.HemisphereLight('#e8f5ff', '#525764', 2.35))
  const sun = new THREE.DirectionalLight('#fff2d8', 2.1)
  sun.position.set(4, 7, 3)
  scene.add(sun)
  const camera = new THREE.OrthographicCamera(-0.96, 0.96, 0.96, -0.96, 0.01, 100)
  camera.position.set(3, 2.6, 3.5)
  camera.lookAt(0.5, 0.5, 0.5)
  const geometry = new THREE.BoxGeometry()
  const frameGeometry = new THREE.EdgesGeometry(geometry)
  const frameMaterial = new THREE.LineBasicMaterial({ color: '#90a5b3', transparent: true, opacity: 0.25 })
  const frame = new THREE.LineSegments(frameGeometry, frameMaterial)
  frame.position.set(0.5, 0.5, 0.5)
  scene.add(frame)
  const group = new THREE.Group()
  scene.add(group)
  let disposed = false
  const clear = () => {
    const materials = new Set(), geometries = new Set()
    for (const mesh of group.children) {
      materials.add(mesh.material)
      if (mesh.geometry !== geometry) geometries.add(mesh.geometry)
      mesh.dispose()
    }
    for (const material of materials) material.dispose()
    for (const geometry of geometries) geometry.dispose()
    group.clear()
  }

  return {
    render(plan) {
      if (disposed) throw new Error('Die Miniatur-Vorschau wurde bereits geschlossen.')
      clear()
      try {
        for (const mesh of createCopycatThumbnailMeshes(plan, geometry)) group.add(mesh)
        if (renderer.getContext().isContextLost()) throw new Error('Der WebGL-Kontext der Miniatur-Vorschau wurde unterbrochen.')
        renderer.render(scene, camera)
        return renderer.domElement.toDataURL('image/png')
      } finally {
        clear()
      }
    },
    dispose() {
      if (disposed) return
      disposed = true
      clear()
      geometry.dispose()
      frameGeometry.dispose()
      frameMaterial.dispose()
      renderer.dispose()
      renderer.forceContextLoss()
    },
  }
}
