const DB = 'copycat-architect-graphstudio'
let connection
export function database() {
  if (!connection) connection = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1)
    request.onupgradeneeded = () => { request.result.createObjectStore('workspace'); request.result.createObjectStore('resources') }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(new Error('Der Browser erlaubt keinen dauerhaften Speicher. Sicherung exportieren, bevor die Seite geschlossen wird.'))
    request.onblocked = () => reject(new Error('Ein anderer Graphstudio-Tab blockiert den Speicher. Bitte diesen Tab schließen.'))
  }).catch(error => { connection = null; throw error })
  return connection
}
export async function stored(store, key, value) {
  const db = await database()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, value === undefined ? 'readonly' : 'readwrite')
    const request = value === undefined ? tx.objectStore(store).get(key) : tx.objectStore(store).put(value, key)
    tx.oncomplete = () => resolve(request.result)
    tx.onerror = tx.onabort = () => reject(new Error('Browserspeicher ist nicht verfügbar oder voll. Bitte eine Sicherung exportieren.'))
  })
}
export const resourceCache = { get: key => stored('resources', key), put: (key, value) => stored('resources', key, value) }
export async function saveWorkspace(data, expectedRevision) {
  const db = await database()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('workspace', 'readwrite'), store = tx.objectStore('workspace')
    let conflict = false
    const request = store.get('current')
    request.onsuccess = () => {
      if ((request.result?._revision || 0) !== expectedRevision) { conflict = true; tx.abort(); return }
      store.put({ ...data, _revision: expectedRevision + 1 }, 'current')
    }
    tx.oncomplete = () => resolve(expectedRevision + 1)
    tx.onerror = tx.onabort = () => reject(new Error(conflict
      ? 'Ein anderer Graphstudio-Tab hat die Bibliothek geändert. Eigene Arbeit als Sicherung exportieren und diese Seite neu laden; der andere Stand wird nicht überschrieben.'
      : 'Speichern fehlgeschlagen. Bitte eine Sicherung exportieren; der letzte gespeicherte Stand bleibt erhalten.'))
  })
}
export const emptyWorkspace = () => ({ schema: 1, drafts: {}, graphs: [], nodeGroups: [], mods: [], versionedGraphs: {}, versionedDrafts: {} })
export function validateWorkspace(value) {
  if (!value || value.schema !== 1 || !value.drafts || typeof value.drafts !== 'object' || Array.isArray(value.drafts) ||
    !['graphs', 'nodeGroups', 'mods'].every(key => Array.isArray(value[key]))) throw new Error('Keine gültige Graphstudio-Sicherung (Schema 1).')
  if (value.graphs.length > 500 || value.nodeGroups.length > 500 || value.mods.length > 100) throw new Error('Die Sicherung enthält zu viele Einträge.')
  const validMaterials = materials => materials === undefined || (materials && !Array.isArray(materials) && typeof materials === 'object' && Object.values(materials).every(value => typeof value === 'string'))
  const validDoc = entry => entry && ['form', 'building'].includes(entry.mode) && entry.document?.graph && Array.isArray(entry.document.graph.nodes) && validMaterials(entry.materials)
  if (value.graphs.some(entry => !entry.id || !validDoc(entry)) || Object.values(value.drafts).some(entry => !validDoc(entry)) ||
    value.nodeGroups.some(entry => !entry.id || !['form', 'building'].includes(entry.mode) || !Array.isArray(entry.nodes) || !Array.isArray(entry.wires) || !Array.isArray(entry.groups) || !validMaterials(entry.materials)) ||
    value.mods.some(entry => !/^[\w.-]+\/[\w.-]+$/.test(entry.repo) || !/^[a-f0-9]{40}$/.test(entry.commit) || !entry.blocks || !entry.models || !entry.textures || !Array.isArray(entry.ids) || !Array.isArray(entry.dependencies))) throw new Error('Die Sicherung enthält unvollständige Einträge.')
  for (const mod of value.mods) for (const values of [mod.blocks, mod.models, mod.textures]) for (const url of Object.values(values)) {
    if (typeof url !== 'string' || !url.startsWith(`https://raw.githubusercontent.com/${mod.repo}/${mod.commit}/`)) throw new Error('Die Sicherung enthält ungültige Ressourcen-Adressen.')
  }
  for (const field of ['versionedGraphs', 'versionedDrafts']) {
    value[field] ??= {}
    if (typeof value[field] !== 'object' || Array.isArray(value[field]) || Object.keys(value[field]).length > 500) throw new Error('Ungültige Untergraph-Bibliothek.')
    for (const [id, entry] of Object.entries(value[field])) {
      if (!entry || entry.schema !== 1 || !['FORM', 'BUILDING'].includes(entry.mode) || !Number.isInteger(entry.version) || entry.version < 0 ||
        (field === 'versionedGraphs' ? entry.version < 1 || id !== `${entry.graphId}@${entry.version}` : id !== entry.graphId) ||
        !Array.isArray(entry.references) || !Array.isArray(entry.connections) || !Array.isArray(entry.ports)) throw new Error('Unvollständiger Untergraph in der Sicherung.')
    }
  }
  return value
}
