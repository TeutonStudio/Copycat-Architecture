import * as THREE from 'three'

// Same face order as Minecraft textures and Three's box materials.
export function textureFace(normal) {
  const axis = normal.map(Math.abs).reduce((best, value, i, values) => value > values[best] ? i : best, 0)
  return axis * 2 + (normal[axis] < 0 ? 1 : 0)
}

/** Preserve exact clipped polygons; collision boxes can fill subvoxel cutouts. */
export function exactGeometry(faces) {
  const positions = [], normals = [], uvs = []
  const geometry = new THREE.BufferGeometry()
  for (const face of faces) {
    const normal = new THREE.Vector3(...face.normal).normalize()
    const material = textureFace(face.normal)
    const points = face.vertices.map(vertex => new THREE.Vector3(...vertex).multiplyScalar(1 / 16))
    const start = positions.length / 3
    for (let i = 1; i + 1 < points.length; i++) {
      const triangle = [points[0], points[i], points[i + 1]]
      const cross = new THREE.Vector3().subVectors(triangle[1], triangle[0]).cross(new THREE.Vector3().subVectors(triangle[2], triangle[0]))
      if (cross.lengthSq() === 0) continue
      if (cross.dot(normal) < 0) [triangle[1], triangle[2]] = [triangle[2], triangle[1]]
      for (const p of triangle) {
        positions.push(p.x, p.y, p.z)
        normals.push(normal.x, normal.y, normal.z)
        uvs.push(...[
          [1 - p.z, p.y], [p.z, p.y], [p.x, 1 - p.z],
          [p.x, p.z], [p.x, p.y], [1 - p.x, p.y],
        ][material])
      }
    }
    const count = positions.length / 3 - start
    if (count) geometry.addGroup(start, count, material)
  }
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3))
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
  // The shared block texture shader expects source-local coordinates.
  geometry.setAttribute('blockCenter', new THREE.Float32BufferAttribute(new Float32Array(positions.length), 3))
  geometry.setAttribute('blockSize', new THREE.Float32BufferAttribute(new Float32Array(positions.length).fill(1), 3))
  geometry.computeBoundingBox()
  geometry.computeBoundingSphere()
  return geometry
}
