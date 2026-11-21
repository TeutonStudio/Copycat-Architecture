import { IconSelect } from './IconSelect.jsx'
import { DeleteDialog } from './FileDialog.jsx'
import { groupVersions } from './version-groups.js'
import { dependencies, deleteVersion, mergeVersions, namedWorkspace } from './documents.js'
import { Modal } from './Modal.jsx'
import { BlockIcon } from './BlockIcon.jsx'
import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { createMaterials, FAITHFUL, importRepository, materialValue, parseMaterial, stateOptions, defaultProperties } from './materials.js'
import { migrateWorkspace, emptyWorkspace, resourceCache, saveWorkspace, stored, validateWorkspace } from './storage.js'
import { readGraph } from './model.js'

const Context = createContext(null)
export const useWorkspace = () => useContext(Context)
export function downloadBackup(data, name = 'graphstudio-sicherung.json') {
  const url = URL.createObjectURL(new Blob([JSON.stringify(data)], { type: 'application/json' }))
  const a = document.createElement('a'); a.href = url; a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export function Workspace({ children }) {
  const [data, setData] = useState(null), [error, setError] = useState(''), [saved, setSaved] = useState(false), [canSave, setCanSave] = useState(true)
  const [panel, setPanel] = useState(false), [tab, setTab] = useState('materials')
  const writeQueue = useRef(Promise.resolve())
  const currentData = useRef(null)
  const revision = useRef(0)
  const storedRevision = useRef(0)
  const lastSavedData = useRef(null)
  useEffect(() => { stored('workspace', 'current').then(value => {
    const workspace = value ? migrateWorkspace(validateWorkspace(value)) : emptyWorkspace()
    storedRevision.current = workspace._revision || 0
    for (const entry of Object.values(workspace.drafts)) readGraph(entry.document, entry.mode)
    lastSavedData.current = workspace
    currentData.current=workspace;setData(workspace)
  })
    .catch(error => { setError(error.message); setCanSave(false); currentData.current=emptyWorkspace();setData(currentData.current) }) }, [])
  useEffect(() => {
    if (!data || !canSave) return
    if (data === lastSavedData.current) { setSaved(true); return }
    const current = ++revision.current
    setSaved(false)
    // Transactions are serialized so a slower old save can never replace a newer one.
    writeQueue.current = writeQueue.current.catch(() => {}).then(async () => { storedRevision.current = await saveWorkspace(data, storedRevision.current); lastSavedData.current = data })
    writeQueue.current.then(() => { if (current === revision.current) { setSaved(true); setError('') } }).catch(error => setError(error.message))
  }, [data, canSave])
  const catalog = useMemo(() => createMaterials(data?.mods, resourceCache), [data?.mods])
  const namedData = useMemo(() => data && namedWorkspace(data), [data])
  const update = callback => {const next=callback(currentData.current);currentData.current=next;setData(next);return next}
  if (!data) return <p className="workspace-status">Gespeicherte Bibliotheken werden geladen …</p>
  return <Context.Provider value={{ data:namedData, update, catalog, openPanel: target => { setTab(target); setPanel(true) } }}>
    <div inert={panel ? true : undefined}>{children}
    <div className="workspace-status" role="status">{error || (saved ? 'Im Browser gespeichert' : 'Wird im Browser gespeichert …')}
      {error && <button onClick={() => downloadBackup(data)}>Sicherung exportieren</button>}</div>
    <div className="texture-credit">Minecraft-Texturen: <a href="https://faithfulpack.net" target="_blank" rel="noreferrer">Faithful 64× Java · Faithful Resource Pack</a> · <a href="https://faithfulpack.net/license" target="_blank" rel="noreferrer">Lizenz</a></div></div>
    {panel && <LibraryPanel tab={tab} setTab={setTab} onClose={() => setPanel(false)} />}
  </Context.Provider>
}

export function MaterialField({ label, value, onChange, blockOnly = false }) {
  const [open, setOpen] = useState(false)
  return <div className="material-field"><label>{label}<div className="material-current">
    {value && <BlockIcon id={value} />}<input aria-label={label} value={value || ''} onChange={event => onChange(event.target.value)} />
    <button type="button" aria-label={`Material für ${label} wählen`} onClick={() => setOpen(true)}>Wählen</button>
  </div></label>{open && <Modal className="material-selection-dialog" title={`Material wählen · ${label}`} onClose={() => setOpen(false)}>
    {blockOnly && <p className="hint">Die Palette übernimmt die Block-ID. Blockzustände dienen hier zur Vorschau.</p>}
    <MaterialPicker value={value} onChoose={next => { onChange(blockOnly ? parseMaterial(next).block : next); setOpen(false) }} onCancel={() => setOpen(false)} />
  </Modal>}</div>
}
function MaterialPicker({ value, onChoose, onCancel }) {
  const { catalog } = useWorkspace()
  const [query, setQuery] = useState(''), [limit, setLimit] = useState(60), [chosen, setChosen] = useState(null)
  const [properties, setProperties] = useState({}), [options, setOptions] = useState({}), [preview, setPreview] = useState(null), [error, setError] = useState(''), [loading,setLoading] = useState(false), [stateReady,setStateReady] = useState(false)
  const generation = useRef(0)
  const ids = catalog.ids.filter(id => id.includes(query.toLowerCase().trim()))
  const choose = async (id, requested) => {
    const request = ++generation.current
    setChosen(id); setError(''); setPreview(null); setProperties({}); setOptions({}); setLoading(true);setStateReady(false)
    try {
      const state = await catalog.state(id)
      if (generation.current !== request) return
      setOptions(stateOptions(state)); setProperties({ ...defaultProperties(state), ...requested });setStateReady(true)
    } catch (cause) { if (generation.current === request) { setError(cause.message); setLoading(false) } }
  }
  useEffect(() => {
    if(value) { try { const {block,properties}=parseMaterial(value); choose(block,properties); const index=catalog.ids.indexOf(block); if(index>=60) setQuery(block) } catch(cause) { setError(cause.message) } }
    return () => { generation.current++ }
  }, [catalog])
  useEffect(() => {
    if (!chosen || !stateReady || !Object.keys(options).every(key => Object.hasOwn(properties,key))) return
    let live = true; setError('');setPreview(null);setLoading(true)
    catalog.resolve(materialValue(chosen, properties)).then(data => { if (live) { setPreview(data);setLoading(false) } }).catch(cause => { if (live) { setError(cause.message);setLoading(false) } })
    return () => { live = false }
  }, [catalog, chosen, properties, options,stateReady])
  return <div className="material-picker">
    <label>Blöcke suchen<input data-autofocus type="search" placeholder="minecraft:stone oder modid:block" value={query} onChange={event => { setQuery(event.target.value); setLimit(60) }} /></label>
    <small>{ids.length} Blöcke · {value || 'Noch kein Material gewählt'}</small>
    <div className="material-list">{ids.slice(0, limit).map(id => <button key={id} type="button" aria-pressed={chosen===id} className={chosen === id ? 'chosen' : ''} onClick={() => choose(id)}><BlockIcon id={id}/><span><strong>{id.split(':').at(-1).replaceAll('_',' ')}</strong><code>{id}</code></span></button>)}</div>
    {!ids.length && <p role="status">Keine passenden Blöcke.</p>}
    {ids.length > limit && <button type="button" onClick={() => setLimit(limit + 100)}>Weitere Blöcke</button>}
    {chosen && <div className="material-detail"><div className="material-detail-identity"><BlockIcon id={materialValue(chosen,properties)}/><div><strong>{chosen}</strong><small>{catalog.owner(chosen)}</small></div></div>
      {Object.entries(options).map(([key, values]) => <label key={key}>{key}<IconSelect domain="state" context={{property:key}} aria-label={`Blockzustand ${key}`} value={properties[key] || ''} onChange={event => setProperties({ ...properties, [key]: event.target.value })}>{values.map(option => <option key={option}>{option}</option>)}</IconSelect></label>)}
      {loading && <p role="status">Blockvorschau wird geladen …</p>}
      {preview?.missing.length > 0 && <p className="error">Fehlende Texturen: {preview.missing.join(', ')}</p>}
      {preview?.warning && <p>{preview.warning}</p>}
    </div>}
    {error && <p className="error" role="alert">{error}</p>}
    {onChoose && <footer><button type="button" onClick={onCancel}>Abbrechen</button><button className="primary" type="button" disabled={loading || !preview || Boolean(error)} onClick={() => onChoose(materialValue(chosen, properties))}>Dieses Material verwenden</button></footer>}
  </div>
}
function LibraryPanel({ tab, setTab, onClose }) {
  const { data, update, catalog } = useWorkspace()
  const [url, setUrl] = useState(''), [ref, setRef] = useState(''), [root, setRoot] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [deleteEntry,setDeleteEntry]=useState(null)
  const input = useRef(null)
  const importMod = async (source = null) => {
    setBusy(true); setError('')
    try { const mod = await importRepository(source?.url || url, source?.ref || ref, source?.root || root)
      if (!data.mods.some(item => item.repo === mod.repo) && data.mods.length >= 100) throw new Error('Höchstens 100 Mod-Repositories verlinken.')
      update(data => ({ ...data, mods: [...data.mods.filter(item => item.repo !== mod.repo), mod] })); if (!source) setUrl('')
    } catch (error) { setError(error.message) } finally { setBusy(false) }
  }
  const restore = async event => {
    const file = event.target.files?.[0]; event.target.value = ''; if (!file) return
    try {
      if (file.size > 30_000_000) throw new Error('Die Sicherung ist größer als 30 MB.')
      const restored = validateWorkspace(JSON.parse(await file.text()))
      for (const entry of [...restored.graphs, ...Object.values(restored.drafts)]) readGraph(entry.document, entry.mode)
      // Merge named entries; current unsaved drafts stay intact.
      const recovered = [...new Map(Object.values(restored.drafts).map(entry => [JSON.stringify(entry),entry])).values()].map(entry => ({ ...entry, id: crypto.randomUUID(), name: `Wiederhergestellt · ${entry.document.name || entry.mode}` }))
      const graphs=mergeVersions(data.graphs,[...restored.graphs,...recovered]), nodeGroups=mergeVersions(data.nodeGroups,restored.nodeGroups)
      update(data => ({ ...data, graphs, nodeGroups,
        documentNames: {...restored.documentNames, ...data.documentNames},
        mods: [...new Map([...data.mods, ...restored.mods].map(item => [item.repo, item])).values()] }))
      setError('Sicherung zusammengeführt. Benannte Graphen können im Editor geöffnet werden.')
    } catch (error) { setError(error.message) }
  }
  const linkedIds = new Set(['minecraft', 'neoforge', 'forge', 'fabricloader', 'java', ...data.mods.flatMap(mod => [...mod.ids, ...Object.keys(mod.blocks).map(id => id.split(':')[0])])])
  return <section className="library-overlay" role="dialog" aria-modal="true" aria-label="Meine Bibliotheken" onKeyDown={event => {
    if (event.key === 'Escape') onClose()
    if (event.key === 'Tab') {
      const focusable = [...event.currentTarget.querySelectorAll('button:not(:disabled),input:not([hidden]),select,a[href]')]
      const first = focusable[0], last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    }
  }}>
    <div className="library-window"><div className="panel-heading"><h2>Meine Bibliotheken</h2><button onClick={onClose} autoFocus>Schließen</button></div>
      <nav className="library-tabs" aria-label="Bibliothek auswählen">{[['materials', 'Materialien'], ['mods', 'Verlinkte Mods'], ['saved', 'Gespeicherte Inhalte']].map(([id, name]) => <button key={id} aria-pressed={tab === id} onClick={() => setTab(id)}>{name}</button>)}</nav>
      <div className="library-content">
        {tab === 'materials' && <><p>{catalog.ids.length} Blöcke. Texturen für Minecraft 1.21.1: <a href={FAITHFUL} target="_blank" rel="noreferrer">Faithful 64× Java</a> von <a href="https://faithfulpack.net" target="_blank" rel="noreferrer">Faithful Resource Pack</a> · <a href="https://faithfulpack.net/license" target="_blank" rel="noreferrer">Lizenz</a>.</p>
          <p className="hint">Materialien im Inspektor einer Copycat-Gruppe oder Gebäuderolle zuweisen. Ob ein Block als Copycat-Material zulässig ist, prüft das Spiel.</p><MaterialPicker /></>}
        {tab === 'mods' && <><p>Öffentliches GitHub-Repository verlinken. Blockstates, Modell-Eltern, Texturen und Mod-Metadaten werden gelesen. Mod-Code wird nicht ausgeführt.</p>
          <form onSubmit={event => { event.preventDefault(); importMod() }}><label>Mod-Repository<input type="url" required placeholder="https://github.com/Autor/Mod" value={url} onChange={event => setUrl(event.target.value)} /></label>
            <label>Zweig, Tag oder Commit<input placeholder="Standardzweig" value={ref} onChange={event => setRef(event.target.value)} /></label>
            <label>Ressourcenordner (optional)<input placeholder="z. B. common/src/main/resources" value={root} onChange={event => setRoot(event.target.value)} /></label>
            <button className="primary" disabled={busy}>{busy ? 'Repository wird gelesen …' : 'Mod verlinken'}</button></form>
          {data.mods.map(mod => <article className="saved-entry" key={mod.repo}><strong><a href={mod.url} target="_blank" rel="noreferrer">{mod.repo}</a></strong><small>{mod.ref} · {mod.commit.slice(0, 8)} · {Object.keys(mod.blocks).length} Blöcke · Lizenz: {mod.license}</small>
            {mod.dependencies.map((dependency, index) => <p key={index} className={dependency.required && !linkedIds.has(dependency.id) ? 'error' : 'hint'}>{dependency.id} {dependency.version} · {dependency.required ? 'erforderlich' : 'optional'}{!linkedIds.has(dependency.id) && dependency.required ? ' · Repository noch verlinken' : ''}</p>)}
            <div className="button-row"><button disabled={busy} onClick={() => importMod(mod)}>Aktualisieren</button><button onClick={() => update(data => ({ ...data, mods: data.mods.filter(item => item.repo !== mod.repo) }))}>Verknüpfung entfernen</button></div>
          </article>)}<p className="hint">Eigene Java-Modell-Loader und erst beim Bauen erzeugte Dateien sind ohne veröffentlichte Ressourcen nicht darstellbar. Fehlende Abhängigkeiten als weiteres Repository verlinken.</p></>}
        {tab === 'saved' && <><p>{data.graphs.length} gespeicherte Graphen · {data.nodeGroups.length} Knotengruppen. Öffnen und Einfügen erfolgt über die Bibliothek des Graphen.</p>
          {groupVersions([...data.graphs.map(item=>({...item,collection:'graphs',type:'Graphkarte'})),...data.nodeGroups.map(item=>({...item,collection:'nodeGroups',type:'Knotengruppe'}))]).map(group=><article className="saved-entry" key={group.id}><strong>{group.name}</strong><small>{group.type} · {group.versions.length} Versionen</small><button className="danger" onClick={()=>setDeleteEntry(group)}>Versionen löschen …</button></article>)}
          {deleteEntry && <DeleteDialog group={deleteEntry} initial={deleteEntry.latest.id} onClose={()=>setDeleteEntry(null)} onDeleted={()=>setDeleteEntry(null)}/>}
          <p>Die Daten bleiben in diesem Browser und auf dieser Website. Das Löschen der Websitedaten entfernt auch die Bibliothek.</p>
          <div className="button-row"><button onClick={() => downloadBackup(data)}>Sicherung exportieren</button><button onClick={() => input.current.click()}>Sicherung importieren</button></div>
          <input ref={input} type="file" accept=".json,application/json" hidden onChange={restore} /></>}
        {error && <p role="status" className="error">{error}</p>}
      </div>
    </div>
  </section>
}
