import vanilla from './data/minecraft-materials.json' with { type: 'json' }

export const FAITHFUL = 'https://github.com/Faithful-Resource-Pack/Faithful-64x-Java/tree/1.21.1'
const resourcePattern = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/
export function resourceId(value) {
  const id = value.includes(':') ? value : `minecraft:${value}`
  if (!resourcePattern.test(id) || id.includes('..')) throw new Error(`Ungültige Ressourcen-ID: ${value}`)
  return id
}
export function parseMaterial(value) {
  const match = /^([^\[\]]+)(?:\[([^\]]*)\])?$/.exec(value)
  if (!match) throw new Error('Ungültiger Blockzustand.')
  const properties = {}
  for (const pair of (match[2] || '').split(',').filter(Boolean)) {
    const parts = pair.split('=')
    if (parts.length !== 2 || !parts.every(part => /^[a-z0-9_]+$/.test(part))) throw new Error('Ungültige Zustandseigenschaft.')
    properties[parts[0]] = parts[1]
  }
  return { block: resourceId(match[1]), properties }
}
export function materialValue(block, properties = {}) {
  const entries = Object.entries(properties).sort(([a], [b]) => a.localeCompare(b))
  return resourceId(block) + (entries.length ? `[${entries.map(([key, value]) => `${key}=${value}`).join(',')}]` : '')
}
export function stateOptions(state) {
  const options = {}
  const add = (key, value) => { if (!['OR', 'AND'].includes(key)) for (const item of String(value).split('|')) (options[key] ??= new Set()).add(item) }
  for (const key of Object.keys(state?.variants || {})) for (const pair of key.split(',').filter(Boolean)) { const [key, value] = pair.split('='); add(key, value) }
  const walk = condition => { for (const [key, value] of Object.entries(condition || {})) Array.isArray(value) ? value.forEach(walk) : add(key, value) }
  for (const part of state?.multipart || []) walk(part.when)
  return Object.fromEntries(Object.entries(options).map(([key, values]) => [key, [...values]]))
}
export function defaultProperties(state) {
  const preferred = ['false', 'y', 'north', 'bottom', 'lower', 'single', '0']
  return Object.fromEntries(Object.entries(stateOptions(state)).map(([key, values]) => [key, preferred.find(value => values.includes(value)) ?? values[0]]))
}
export function matchesState(condition, properties) {
  if (!condition) return true
  return Object.entries(condition).every(([key, value]) => key === 'OR' ? value.some(item => matchesState(item, properties))
    : key === 'AND' ? value.every(item => matchesState(item, properties)) : String(value).split('|').includes(properties[key]))
}
export function stateModels(state, properties) {
  const first = value => Array.isArray(value) ? value[0] : value
  if (state.variants) {
    const entry = Object.entries(state.variants).find(([key]) => matchesState(Object.fromEntries(key.split(',').filter(Boolean).map(pair => pair.split('='))), properties))
    return entry ? [first(entry[1])] : []
  }
  return (state.multipart || []).filter(part => matchesState(part.when, properties)).map(part => first(part.apply))
}
export function rotateFaces(faces, variant) {
  const normals = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]
  const rotated = Array(6).fill(null)
  normals.forEach((normal, face) => {
    let [x, y, z] = normal
    for (let q = 0; q < ((variant.x || 0) % 360 + 360) % 360 / 90; q++) [y, z] = [z, -y]
    for (let q = 0; q < ((variant.y || 0) % 360 + 360) % 360 / 90; q++) [x, z] = [-z, x]
    rotated[normals.findIndex(normal => normal[0] === x && normal[1] === y && normal[2] === z)] = faces[face]
  })
  return rotated
}
export function parseRepository(value) {
  const url = new URL(value)
  const match = /^\/([\w.-]+)\/([\w.-]+?)(?:\.git)?\/?$/.exec(url.pathname)
  if (url.protocol !== 'https:' || url.hostname !== 'github.com' || !match || url.username || url.password || url.search || url.hash) throw new Error('Bitte die GitHub-Adresse des Repositories angeben, z. B. https://github.com/Autor/Mod. Den Zweig separat eintragen.')
  return `${match[1]}/${match[2]}`
}
const rawUrl = (repo, commit, path) => `https://raw.githubusercontent.com/${repo}/${commit}/${path.split('/').map(encodeURIComponent).join('/')}`
async function request(url, json = true) {
  const response = await fetch(url, { credentials: 'omit', signal: AbortSignal.timeout(25000) })
  if (!response.ok) {
    if (response.status === 403 || response.status === 429) throw new Error('GitHub-Limit erreicht. Bitte später erneut versuchen; bereits gespeicherte Ressourcen bleiben verfügbar.')
    throw new Error(`Ressource nicht erreichbar (${response.status}): ${url}`)
  }
  const text = await response.text()
  if (text.length > 12_000_000) throw new Error('Die Ressource ist zu groß.')
  return json ? JSON.parse(text) : text
}
export function parseManifest(path, text) {
  if (path.endsWith('fabric.mod.json')) {
    const data = JSON.parse(text)
    return { ids: [data.id].filter(Boolean), dependencies: Object.keys(data.depends || {}).map(id => ({ id, required: true, version: String(data.depends[id]) })) }
  }
  // Mod metadata is read as data; Gradle scripts, loaders and Java code are never executed.
  const ids = [...text.matchAll(/^\s*modId\s*=\s*["']([^"']+)["']/gm)].map(match => match[1])
  const sections = text.split(/\[\[dependencies\.[^\]]+\]\]/).slice(1)
  const dependencies = sections.map(section => {
    const body = section.split(/\n\s*\[/)[0]
    const id = /modId\s*=\s*["']([^"']+)["']/.exec(body)?.[1]
    const required = !/mandatory\s*=\s*false|type\s*=\s*["']optional["']/.test(body)
    return { id, required, version: /versionRange\s*=\s*["']([^"']*)["']/.exec(body)?.[1] || '' }
  }).filter(item => item.id)
  return { ids: ids.filter(id => !dependencies.some(item => item.id === id)), dependencies }
}
export async function importRepository(url, ref = '', root = '') {
  const repo = parseRepository(url)
  const meta = await request(`https://api.github.com/repos/${repo}`)
  const branch = ref.trim() || meta.default_branch
  const commit = (await request(`https://api.github.com/repos/${repo}/commits/${encodeURIComponent(branch)}`)).sha
  const tree = await request(`https://api.github.com/repos/${repo}/git/trees/${commit}?recursive=1`)
  if (tree.truncated) throw new Error('GitHub liefert diesen Ressourcenbaum unvollständig. Bitte ein kleineres Ressourcen-Repository verlinken.')
  const files = tree.tree.filter(item => item.type === 'blob' && item.mode !== '120000' && (!root || item.path.startsWith(root.replace(/\/$/, '') + '/')))
  const blocks = {}, models = {}, textures = {}
  for (const file of files) {
    const match = /(?:^|\/)assets\/([a-z0-9_.-]+)\/(blockstates|models|textures)\/(.+)\.(json|png)$/.exec(file.path)
    if (!match || (match[2] === 'textures' ? match[4] !== 'png' : match[4] !== 'json')) continue
    const id = resourceId(`${match[1]}:${match[3]}`)
    const target = match[2] === 'blockstates' ? blocks : match[2] === 'models' ? models : textures
    target[id] ??= rawUrl(repo, commit, file.path)
  }
  if (!Object.keys(blocks).length && !Object.keys(models).length && !Object.keys(textures).length) throw new Error('Keine assets/<modid>/blockstates, models oder textures gefunden. Generierte Ressourcen müssen im Repository vorhanden sein.')
  const manifests = files.filter(file => /(?:^|\/)(fabric\.mod\.json|(?:neo)?forge\.mods\.toml|mods\.toml)$/.test(file.path)).slice(0, 16)
  const metadata = await Promise.all(manifests.map(async file => parseManifest(file.path, await request(rawUrl(repo, commit, file.path), false))))
  return { repo, ref: branch, commit, root, url: `https://github.com/${repo}`, license: meta.license?.name || 'Im Repository prüfen',
    ids: [...new Set(metadata.flatMap(data => data.ids))], dependencies: metadata.flatMap(data => data.dependencies), blocks, models, textures }
}

export function createMaterials(mods = [], cache = { get: async () => null, put: async () => {} }) {
  const blocks = { ...vanilla.blocks }, models = { ...vanilla.models }, textures = { ...vanilla.textures }
  const owners = {}
  for (const mod of mods) {
    Object.assign(blocks, mod.blocks); Object.assign(models, mod.models); Object.assign(textures, mod.textures)
    for (const id of Object.keys(mod.blocks)) owners[id] = mod.repo
  }
  const memory = new Map()
  const load = async value => {
    if (typeof value !== 'string') return value
    if (!memory.has(value)) memory.set(value, (async () => {
      const saved = await cache.get(value).catch(() => null)
      if (saved) return saved
      const result = await request(value)
      await cache.put(value, result).catch(() => {})
      return result
    })().catch(error => { memory.delete(value); throw error }))
    return memory.get(value)
  }
  const model = async (id, seen = new Set()) => {
    id = resourceId(id)
    if (seen.has(id) || seen.size > 32) throw new Error(`Zyklische Modell-Abhängigkeit: ${id}`)
    if (!models[id]) throw new Error(`Modell fehlt: ${id}. Das zugehörige Mod-Repository verlinken.`)
    const data = await load(models[id])
    const next = new Set(seen).add(id)
    const parent = data.parent && !data.parent.startsWith('builtin/') && !data.parent.includes(':builtin/') ? await model(data.parent, next) : {}
    return { ...parent, ...data, textures: { ...parent.textures, ...data.textures } }
  }
  const state = async id => {
    if (!blocks[id]) throw new Error(`Block fehlt in der Bibliothek: ${id}`)
    return load(blocks[id])
  }
  const resolve = async value => {
    const { block, properties: requested } = parseMaterial(value)
    const data = await state(block)
    const properties = { ...defaultProperties(data), ...requested }
    const options = stateOptions(data)
    for (const [key, value] of Object.entries(properties)) if (!options[key]?.includes(value)) throw new Error(`Unbekannter Blockzustand: ${key}=${value}`)
    const variants = stateModels(data, properties)
    if (!variants.length) throw new Error('Dieser Blockzustand besitzt kein passendes Modell.')
    const resolved = await Promise.all(variants.map(variant => model(variant.model)))
    const missing = new Set()
    const faceTexture = (data, key) => {
      const seen = new Set()
      while (key?.startsWith('#')) {
        if (seen.has(key)) throw new Error('Zyklischer Texturverweis.')
        seen.add(key); key = data.textures?.[key.slice(1)]
      }
      if (!key) return null
      const id = resourceId(key)
      if (!textures[id]) missing.add(id)
      return textures[id] || null
    }
    const variantsFaces = resolved.map((data, index) => rotateFaces(['east', 'west', 'up', 'down', 'south', 'north'].map(face => {
        const key = data.elements?.flatMap(element => element.faces?.[face] ? [element.faces[face].texture] : [])[0]
          || (data.textures?.[face] && `#${face}`) || (data.textures?.all && '#all') || (data.textures?.side && '#side') || '#particle'
        return faceTexture(data, key)
    }), variants[index]))
    const faces = Array.from({ length: 6 }, (_, face) => variantsFaces.map(faces => faces[face]).find(Boolean) || null)
    return { block, properties, faces, missing: [...missing], models: variants, multipart: variants.length > 1,
      warning: resolved.some(data => data.loader) ? 'Eigenen Modell-Loader kann der Browser nicht ausführen.' : !faces.some(Boolean) ? 'Dieser Zustand besitzt keine darstellbare Blocktextur (z. B. Entity- oder Spezialmodell).' : '' }
  }
  return { ids: Object.keys(blocks).sort(), owner: id => owners[id] || 'Minecraft 1.21.1 · Faithful 64×', state, resolve }
}
