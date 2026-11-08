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
  group.traverse(item => {
    if (item.isMesh) {
      item.geometry.dispose()
      item.material.dispose()
      item.dispose?.()
    }
  })
}

function Viewer({ plan }) {
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
    const render = () => renderer.render(scene, camera)
    controls.addEventListener('change', render)
    scene.add(new THREE.HemisphereLight('#e8f5ff', '#525764', 2.35))
    const sun = new THREE.DirectionalLight('#fff2d8', 2.1)
    sun.position.set(4, 7, 3)
    scene.add(sun)
    const model = new THREE.Group()
    scene.add(model)
    const resize = new ResizeObserver(() => {
      const { clientWidth: width, clientHeight: height } = element
      if (!width || !height) return
      camera.aspect = width / height
      camera.updateProjectionMatrix()
      renderer.setSize(width, height, false)
      render()
    })
    resize.observe(element)
    viewer.current = { model, camera, controls, render, bounds: null }
    return () => {
      resize.disconnect()
      controls.dispose()
      dispose(model)
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
    view.camera.position.copy(center).addScaledVector(new THREE.Vector3(1, 0.72, 1.1).normalize(), radius / angle * 1.14)
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
    view.bounds = bounds
    if (first) fit()
    view.render()
  }, [plan])

  return <div className="viewer" ref={container}>
    <button type="button" className="fit3d" onClick={fit}>3D einpassen</button>
    {error && <p className="preview-error" role="status">{error}</p>}
  </div>
}

export default function Preview({ mode, document, library, selected }) {
  const worker = useRef(null)
  const sequence = useRef(0)
  const running = useRef(false)
  const queued = useRef(null)
  const [result, setResult] = useState({})
  const latest = useRef(document)
  latest.current = document
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
    const timer = setTimeout(() => {
      setResult(previous => ({ ...previous, pending: true, error: '' }))
      queued.current = { id, mode, document: latest.current, library, selected }
      send()
    }, 130)
    return () => clearTimeout(timer)
  }, [mode, content, library, selected])

  const noun = mode === 'building' ? 'Platzierungen' : 'Rasterzellen'
  return <section className="preview-panel" aria-label="Gebäude- und Formvorschau">
    <div className="panel-heading"><span>3D-Vorschau</span><small>LIVE · KOTLIN</small></div>
    <Viewer plan={result.plan} />
    <div className="preview-meta" role="status">
      {result.error ? <><strong>{result.plan ? 'Letzter gültiger Stand' : 'Vorschau wartet auf einen gültigen Graphen'}</strong><br />{result.error}</>
        : result.pending ? 'Vorschau wird berechnet …'
          : result.plan ? <>{result.plan.count.toLocaleString('de-DE')} {noun} · {result.plan.raw.toLocaleString('de-DE')} Zinkeinheiten</>
            : 'Vorschau wird geladen …'}
    </div>
    {result.plan?.materials && <details className="materials"><summary>Materialbedarf</summary>
      {Object.entries(result.plan.materials).map(([id, count]) => <div key={id}><code>{id}</code><span>{count}</span></div>)}
    </details>}
  </section>
}
