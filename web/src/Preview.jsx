import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'

const colors = {
  body: '#8ba4a2', cap: '#d29763', 'minecraft:stone_bricks': '#979999',
  'minecraft:deepslate_tiles': '#434e59', 'minecraft:oak_planks': '#bd9163',
  'minecraft:glass': '#a6dceb', 'minecraft:stone': '#a1a6ad',
  'minecraft:spruce_planks': '#846247', 'minecraft:bricks': '#aa6557'
}
function color(id) {
  if (colors[id]) return colors[id]
  let hash = 0
  for (const char of id) hash = Math.imul(hash, 31) + char.charCodeAt(0) | 0
  return new THREE.Color().setHSL((hash >>> 0) % 360 / 360, 0.32, 0.59)
}

function dispose(group) {
  const geometries = new Set()
  const materials = new Set()
  group.traverse(item => {
    if (item.geometry) geometries.add(item.geometry)
    for (const material of Array.isArray(item.material) ? item.material : item.material ? [item.material] : []) materials.add(material)
    if (item.isInstancedMesh) item.dispose()
  })
  for (const geometry of geometries) geometry.dispose()
  for (const material of materials) { material.map?.dispose(); material.dispose() }
}

function referenceFrame(group) {
  const bounds = new THREE.Box3(new THREE.Vector3(), new THREE.Vector3(1, 1, 1))
  const size = bounds.getSize(new THREE.Vector3()).max(new THREE.Vector3(1, 1, 1))
  bounds.max.copy(bounds.min).add(size)
  const geometry = new THREE.CylinderGeometry(1, 1, 1, 8)
  const material = (color, opacity) => new THREE.MeshBasicMaterial({ color, opacity, transparent: true, depthTest: false, depthWrite: false, toneMapped: false })
  const front = material('#f5f6f2', 0.95)
  const back = material('#87919d', 0.42)
  const markFront = material('#ff8b2d', 0.98)
  const markBack = material('#ff8b2d', 0.38)
  const cylinder = (position, direction, length, surface) => {
    const mesh = new THREE.Mesh(geometry, surface)
    mesh.position.copy(position)
    mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction)
    mesh.scale.set(0.004, length, 0.004)
    mesh.renderOrder = 3
    group.add(mesh)
    return mesh
  }
  const edges = []
  for (let axis = 0; axis < 3; axis++) {
    const others = [0, 1, 2].filter(value => value !== axis)
    const direction = new THREE.Vector3().setComponent(axis, 1)
    for (const first of [0, 1]) for (const second of [0, 1]) {
      const start = bounds.min.clone()
      start.setComponent(others[0], first ? bounds.max.getComponent(others[0]) : bounds.min.getComponent(others[0]))
      start.setComponent(others[1], second ? bounds.max.getComponent(others[1]) : bounds.min.getComponent(others[1]))
      const center = start.clone().addScaledVector(direction, size.getComponent(axis) / 2)
      const mesh = cylinder(center, direction, size.getComponent(axis), front)
      const marks = [0.25, 0.5, 0.75].map(fraction => {
        const position = start.clone().addScaledVector(direction, size.getComponent(axis) * fraction)
        return { mesh: cylinder(position, new THREE.Vector3(0, 1, 0), 0.05, markFront), major: fraction === 0.5 }
      })
      edges.push({ mesh, marks, direction, faces: [{ axis: others[0], sign: first ? 1 : -1 }, { axis: others[1], sign: second ? 1 : -1 }] })
    }
  }
  group.userData.reference = { edges, front, back, markFront, markBack }
  return bounds
}

function updateReferenceFrame(group, camera, height) {
  const reference = group.userData.reference
  if (!reference) return
  const unitsPerPixel = point => 2 * camera.position.distanceTo(point) * Math.tan(camera.fov * Math.PI / 360) / Math.max(height, 1)
  const up = new THREE.Vector3(0, 1, 0)
  for (const edge of reference.edges) {
    const visible = edge.faces.some(face => face.sign * (camera.position.getComponent(face.axis) - edge.mesh.position.getComponent(face.axis)) > 0)
    edge.mesh.material = visible ? reference.front : reference.back
    edge.mesh.renderOrder = visible ? 3 : 2
    const radius = unitsPerPixel(edge.mesh.position) * (visible ? 0.9 : 0.55)
    edge.mesh.scale.x = radius
    edge.mesh.scale.z = radius
    for (const mark of edge.marks) {
      const direction = edge.direction.clone().cross(camera.position.clone().sub(mark.mesh.position))
      if (direction.lengthSq() < 0.00001) direction.copy(edge.direction).cross(camera.up)
      if (direction.lengthSq() < 0.00001) direction.set(1, 0, 0)
      direction.normalize()
      const scale = unitsPerPixel(mark.mesh.position)
      const width = scale * (mark.major ? 1.4 : 0.65)
      mark.mesh.scale.set(width, scale * (mark.major ? 17 : 8), width)
      mark.mesh.quaternion.setFromUnitVectors(up, direction)
      mark.mesh.material = visible ? reference.markFront : reference.markBack
      mark.mesh.renderOrder = visible ? 5 : 4
    }
  }
}

function Viewer({ plan, mode }) {
  const container = useRef(null)
  const viewer = useRef(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const element = container.current
    let renderer
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true })
    } catch {
      setError('WebGL 2 steht in diesem Browser nicht zur Verfügung.')
      return
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))
    element.append(renderer.domElement)
    renderer.domElement.setAttribute('aria-label', 'Drehbare 3D-Vorschau')
    const scene = new THREE.Scene()
    scene.background = new THREE.Color('#131c23')
    const camera = new THREE.PerspectiveCamera(42, 1, 0.01, 4096)
    camera.position.set(4, 3, 5)
    const controls = new OrbitControls(camera, renderer.domElement)
    const render = () => {
      updateReferenceFrame(guides, camera, element.clientHeight)
      renderer.render(scene, camera)
    }
    controls.addEventListener('change', render)
    scene.add(new THREE.HemisphereLight('#e8f5ff', '#525764', 2.35))
    const sun = new THREE.DirectionalLight('#fff2d8', 2.1)
    sun.position.set(4, 7, 3)
    scene.add(sun)
    const model = new THREE.Group()
    scene.add(model)
    const guides = new THREE.Group()
    scene.add(guides)
    const resize = new ResizeObserver(() => {
      const { clientWidth: width, clientHeight: height } = element
      if (!width || !height) return
      camera.aspect = width / height
      camera.updateProjectionMatrix()
      renderer.setSize(width, height, false)
      render()
    })
    resize.observe(element)
    viewer.current = { model, guides, camera, controls, render, bounds: null }
    return () => {
      resize.disconnect()
      controls.dispose()
      dispose(model)
      dispose(guides)
      renderer.dispose()
      renderer.domElement.remove()
      viewer.current = null
    }
  }, [])

  const fit = () => {
    const view = viewer.current
    if (!view?.bounds || view.bounds.isEmpty()) return
    const center = view.bounds.getCenter(new THREE.Vector3())
    const radius = Math.max(view.bounds.getSize(new THREE.Vector3()).length() / 2, 0.75)
    const angle = Math.min(Math.tan(view.camera.fov * Math.PI / 360), Math.tan(view.camera.fov * Math.PI / 360) * view.camera.aspect)
    view.controls.target.copy(center)
    view.camera.position.copy(center).addScaledVector(new THREE.Vector3(1, 0.72, 1.1).normalize(), radius / angle * 1.08)
    view.controls.update()
    view.render()
  }

  useEffect(() => {
    const view = viewer.current
    if (!view) return
    dispose(view.model)
    view.model.clear()
    const batches = new Map()
    for (const mesh of plan?.meshes ?? []) {
      const list = batches.get(mesh.material) ?? []
      list.push(mesh)
      batches.set(mesh.material, list)
    }
    const matrix = new THREE.Matrix4()
    const scale = new THREE.Vector3()
    const translation = new THREE.Vector3()
    const bounds = new THREE.Box3()
    const corner = new THREE.Vector3()
    for (const [material, meshes] of batches) {
      const count = meshes.reduce((sum, mesh) => sum + mesh.boxes.length * mesh.positions.length, 0)
      if (!count) continue
      const draw = new THREE.InstancedMesh(new THREE.BoxGeometry(), new THREE.MeshLambertMaterial({ color: color(material) }), count)
      let index = 0
      for (const mesh of meshes) for (const [px, py, pz] of mesh.positions) {
        for (const [x, y, z, maxX, maxY, maxZ] of mesh.boxes) {
          scale.set((maxX - x) / 16, (maxY - y) / 16, (maxZ - z) / 16)
          translation.set(px + (x + maxX) / 32, py + (y + maxY) / 32, pz + (z + maxZ) / 32)
          matrix.makeScale(scale.x, scale.y, scale.z).setPosition(translation)
          draw.setMatrixAt(index++, matrix)
          bounds.expandByPoint(corner.set(px + x / 16, py + y / 16, pz + z / 16))
          bounds.expandByPoint(corner.set(px + maxX / 16, py + maxY / 16, pz + maxZ / 16))
        }
      }
      draw.instanceMatrix.needsUpdate = true
      draw.computeBoundingSphere()
      view.model.add(draw)
    }
    const first = !view.bounds || view.bounds.isEmpty()
    dispose(view.guides)
    view.guides.clear()
    view.bounds = mode === 'form' ? referenceFrame(view.guides) : bounds
    if (first) fit()
    view.render()
  }, [plan, mode])

  return <div className="viewer" ref={container}>
    <button type="button" className="fit3d" onClick={fit}>3D einpassen</button>
    {error && <p className="preview-error" role="status">{error}</p>}
  </div>
}

export default function Preview({ mode, document, library, selected, previewMode, onMode, collapsed, onToggle }) {
  const worker = useRef(null)
  const sequence = useRef(0)
  const running = useRef(false)
  const queued = useRef(null)
  const [result, setResult] = useState({})
  const target = previewMode === 'selected' ? selected : ''
  const missingSelection = previewMode === 'selected' && !selected
  const latest = useRef(document)
  latest.current = document
  useEffect(() => { setResult({}) }, [mode, target, previewMode])
  const content = useMemo(() => JSON.stringify({
    ...document, graph: { ...document.graph, nodes: document.graph.nodes.map(({ x, y, ...node }) => node) }
  }), [document])
  const send = () => {
    if (!worker.current || running.current || !queued.current) return
    running.current = true
    worker.current.postMessage(queued.current)
    queued.current = null
  }
  useEffect(() => {
    const instance = new Worker(new URL('./preview.worker.js', import.meta.url), { type: 'module' })
    worker.current = instance
    instance.onmessage = ({ data }) => {
      running.current = false
      if (data.id === sequence.current) {
        setResult(previous => data.error ? { plan: previous.plan, error: data.error, pending: false } : data)
      }
      send()
    }
    instance.onerror = event => {
      running.current = false
      setResult(previous => ({ ...previous, error: event.message, pending: false }))
    }
    return () => instance.terminate()
  }, [])

  useEffect(() => {
    const id = ++sequence.current
    queued.current = null
    if (missingSelection) {
      setResult({ error: 'Wähle einen Knoten im Graphen aus.', pending: false })
      return
    }
    const timer = setTimeout(() => {
      setResult(previous => ({ ...previous, pending: true, error: '' }))
      queued.current = { id, mode, document: latest.current, library, selected: target }
      send()
    }, 130)
    return () => clearTimeout(timer)
  }, [mode, content, library, target, missingSelection])


  const noun = mode === 'building' ? 'Platzierungen' : 'Rasterzellen'
  return <section className="preview-panel" aria-label="Gebäude- und Formvorschau">
    <div className="panel-heading"><span>3D-Vorschau</span><div className="button-row">
      <select aria-label="Vorschauziel" value={previewMode} onChange={event => onMode(event.target.value)}>
        <option value="graph">Graph</option><option value="selected">Selektiert</option>
      </select>
      <button type="button" aria-expanded={!collapsed} aria-controls={`${mode}-preview`} aria-label={collapsed ? '3D-Vorschau ausklappen' : '3D-Vorschau einklappen'} onClick={onToggle}>{collapsed ? '▸' : '▾'}</button>
    </div></div>
    <div id={`${mode}-preview`} className="preview-body" hidden={collapsed}>
    <Viewer key={`${mode}:${previewMode}:${target}`} plan={result.plan} mode={mode} />
    <div className="preview-meta" role="status">
      {result.error ? <><strong>{result.plan ? 'Letzter gültiger Stand' : 'Vorschau wartet auf einen gültigen Graphen'}</strong><br />{result.error}</>
        : result.pending ? 'Vorschau wird berechnet …'
          : result.plan ? <>{result.plan.count.toLocaleString('de-DE')} {noun} · {result.plan.raw.toLocaleString('de-DE')} Zinkeinheiten</>
            : 'Vorschau wird geladen …'}
    </div>
    {result.plan?.value && <p className="preview-meta">{result.plan.value}</p>}
    {result.plan?.warnings?.map((warning, index) => <p className="preview-meta" key={index}>{warning}</p>)}
    <p className="axis-legend"><span>X</span> Ost/West · <span>Y</span> Höhe · <span>Z</span> Süd/Nord
      {mode === 'form' && <><br />Rahmen: 16 hB · lange Marke: 8 hB · kurze Marken: 4 / 12 hB</>}</p>
    {result.plan?.materials && <details className="materials"><summary>Materialbedarf</summary>
      {Object.entries(result.plan.materials).map(([id, count]) => <div key={id}><code>{id}</code><span>{count}</span></div>)}
    </details>}
    </div>
  </section>
}
