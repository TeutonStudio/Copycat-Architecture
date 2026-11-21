import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { Modal } from './Modal.jsx'
import { copycatGroups, folderRows, visibleGroups, versionOf, pathOf } from './copycat-folders.js'
import { Viewer } from './Preview.jsx'
import { useWorkspace } from './Workspace.jsx'
import { listCopycatOptions } from './copycat-options.js'
import { createCopycatPreviewClient } from './copycat-preview-client.js'
import { createCopycatThumbnailRenderer } from './copycat-thumbnails.js'

const sources = { empty: 'Grundform', base: 'Grundform', saved: 'Meine gespeicherten Formen', library: 'Formbibliothek' }
const categories = [['basic', 'Grundformen'], ['saved', 'Gespeicherte Formen']]

function createPreviews() {
  const client = createCopycatPreviewClient()
  const images = new Map()
  let renderer, renderError, closed = false
  return {
    request: (option, settings) => client.request(option, settings),
    async thumbnail(option) {
      if (images.has(option)) return images.get(option)
      const plan = await client.request(option)
      if (closed) throw Object.assign(new Error('Die Auswahl wurde geschlossen.'), { name: 'AbortError' })
      if (renderError) throw renderError
      try {
        renderer ??= createCopycatThumbnailRenderer()
        const image = renderer.render(plan)
        images.set(option, image)
        if (images.size > 80) images.delete(images.keys().next().value)
        return image
      } catch (error) {
        renderError = error
        throw error
      }
    },
    dispose() {
      closed = true
      client.dispose()
      renderer?.dispose()
      images.clear()
    },
  }
}

function ShapeCard({ option, selected, previews, onSelect, onActivate, versions = 1 }) {
  const card = useRef(null)
  const [visible, setVisible] = useState(false)
  const [thumbnail, setThumbnail] = useState(null)
  useEffect(() => {
    if (!window.IntersectionObserver) { setVisible(true); return }
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) { setVisible(true); observer.disconnect() }
    }, { rootMargin: '80px' })
    observer.observe(card.current)
    return () => observer.disconnect()
  }, [])
  useEffect(() => {
    if (!visible || !previews) return
    let live = true
    setThumbnail(null)
    previews.thumbnail(option).then(image => { if (live) setThumbnail({ image }) })
      .catch(error => { if (live && error.name !== 'AbortError') setThumbnail({ error: error.message || String(error) }) })
    return () => { live = false }
  }, [option, visible, previews])
  return <button ref={card} type="button" className="copycat-card" aria-pressed={selected}
    aria-label={`${option.name} · ${sources[option.kind]}${option.reference ? ` · ${option.reference}` : ''}`}
    title={`${option.name} · ${option.reference || option.id} · v${versionOf(option)}${thumbnail?.error ? ` · ${thumbnail.error}` : ''}`} onClick={() => onSelect(option.id)} onDoubleClick={() => onActivate(option)}>
    <span className="copycat-thumbnail" aria-hidden="true">
      {thumbnail?.image ? <img src={thumbnail.image} alt="" draggable="false" />
        : <span className={thumbnail?.error ? 'error' : ''} title={thumbnail?.error}>
          {thumbnail?.error ? 'Vorschau nicht verfügbar' : 'Vorschau …'}
        </span>}
      {selected && <span className="copycat-check">✓</span>}
    </span>
    <strong>{pathOf(option).at(-1)}</strong>
    <small>v{versionOf(option)} · {versions} {versions === 1 ? 'Version' : 'Versionen'}</small>
  </button>
}

export default function CopycatPicker({ mode, savedForms, library, initialId, initialCategory = 'basic', onChoose, onClose }) {
  const { catalog } = useWorkspace()
  const dialog = useRef(null)
  const resultsRef = useRef(null)
  const id = useId()
  const [category, setCategory] = useState(initialCategory)
  const [query, setQuery] = useState('')
  const [path, setPath] = useState([])
  const [versionGroup, setVersionGroup] = useState(null)
  const [limit, setLimit] = useState(48)
  const [chosenId, setChosenId] = useState(initialId || null)
  const [previews, setPreviews] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [applying, setApplying] = useState(false)
  const options = useMemo(() => listCopycatOptions({ savedForms, library }), [savedForms, library])
  const chosen = options.find(option => option.id === chosenId)
  const groups = useMemo(() => copycatGroups(options.filter(option => option.category === category)), [options, category])
  const rows = folderRows(groups,path)
  const filtered = category === 'saved' ? visibleGroups(groups,path,query) : groups.filter(group => group.name.toLocaleLowerCase('de').includes(query.trim().toLocaleLowerCase('de')))
  const folder = next => { setPath(next); setQuery(''); setLimit(48) }
  const tileOption = group => group.versions.find(option => option.id === chosenId) || group.versions.find(option => query.trim() && (option.id === query.trim() || option.reference === query.trim())) || group.versions[0]
  const ready = result?.option === chosen && result?.plan
  const previewError = result?.option === chosen && result?.error

  useEffect(() => {
    const element = dialog.current
    const opener = document.activeElement
    const overflow = document.body.style.overflow
    element.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      element.close()
      document.body.style.overflow = overflow
      if (opener?.isConnected) opener.focus({ preventScroll: true })
    }
  }, [])
  useEffect(() => {
    const services = createPreviews()
    setPreviews(services)
    return () => services.dispose()
  }, [])
  useEffect(() => {
    setResult(null)
    setError('')
    if (!chosen || !previews) return
    let live = true
    previews.request(chosen, { priority: true }).then(plan => { if (live) setResult({ option: chosen, plan }) })
      .catch(error => { if (live && error.name !== 'AbortError') setResult({ option: chosen, error: error.message || String(error) }) })
    return () => { live = false }
  }, [chosen, previews])

  useEffect(() => { if(resultsRef.current) resultsRef.current.scrollTop=0 },[category,query,path.join('/')])
  useEffect(() => {
    const element=resultsRef.current
    if(!element) return
    const reveal=() => {
      const card=element.querySelector('.copycat-card[aria-pressed=true]')
      if(!card) return
      const box=card.getBoundingClientRect(),view=element.getBoundingClientRect()
      if(box.top<view.top) element.scrollTop+=box.top-view.top
      else if(box.bottom>view.bottom) element.scrollTop+=box.bottom-view.bottom
    }
    const observer=new ResizeObserver(reveal);observer.observe(element);reveal()
    return () => observer.disconnect()
  },[chosenId])

  const switchCategory = next => {
    setCategory(next); setQuery(''); setLimit(48); setError('')
  }
  const choose = async (option = chosen, exact = false) => {
    if (!option || applying) return
    const group = groups.find(group => group.versions.some(version => version.id === option.id))
    if (!exact && group?.versions.length > 1) { setVersionGroup(group); return }
    setApplying(true); setError('')
    try { await onChoose(option); onClose() }
    catch (error) { setError(error.message || String(error)); setApplying(false) }
  }
  const keydown = event => {
    event.stopPropagation()
    if (event.key !== 'Tab') return
    const controls = [...dialog.current.querySelectorAll('button, input, select, textarea, a[href], [tabindex]')]
      .filter(element => !element.disabled && element.tabIndex >= 0 && element.getClientRects().length
        && getComputedStyle(element).visibility !== 'hidden')
    const target = event.shiftKey ? controls.at(-1) : controls[0]
    if (document.activeElement === (event.shiftKey ? controls[0] : controls.at(-1)) || !dialog.current.contains(document.activeElement)) {
      event.preventDefault()
      target?.focus()
    }
  }

  return <dialog ref={dialog} className="copycat-dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}
    onCancel={event => { event.preventDefault(); if (!applying) onClose() }} onKeyDown={keydown}>
    <div className="copycat-dialog-layout">
      <header className="copycat-dialog-heading">
        <div><span className="copycat-eyebrow">COPYCAT-BIBLIOTHEK</span><h2 id={`${id}-title`}>Copycat auswählen</h2>
          <p id={`${id}-description`}>Form ansehen und mit „Auswahl übernehmen“ in den Graphen einsetzen.</p></div>
        <button type="button" aria-label="Auswahl schließen" disabled={applying} onClick={onClose}>×</button>
      </header>
      <div className="copycat-dialog-body">
        <section className="copycat-catalog" aria-label="Verfügbare Copycats">
          <div className="copycat-tabs" role="tablist" aria-label="Formenquelle">
            {categories.map(([value, label], index) => <button key={value} type="button" role="tab"
              id={`${id}-tab-${value}`} aria-controls={`${id}-forms`} aria-selected={category === value}
              tabIndex={category === value ? 0 : -1} onClick={() => switchCategory(value)}
              onKeyDown={event => {
                if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
                event.preventDefault()
                const target = event.key === 'Home' ? 0 : event.key === 'End' ? categories.length - 1
                  : (index + (event.key === 'ArrowRight' ? 1 : -1) + categories.length) % categories.length
                switchCategory(categories[target][0])
                document.getElementById(`${id}-tab-${categories[target][0]}`)?.focus()
              }}>{label}<span aria-hidden="true">{options.filter(option => option.category === value).length}</span></button>)}
          </div>
          {category === 'saved' && <nav className="copycat-folder-navigation" aria-label="Ordner">{rows.map(row => <div className="folder-strip" role="group" aria-label={`Ordnerebene ${row.depth+1}`} key={row.depth}>
            <button title={row.parent.join('/') || 'Hauptebene'} aria-pressed={path.length === row.depth} onClick={() => folder(row.parent)}>{row.depth === 0 ? 'Hauptebene' : 'Dieser Ordner'}</button>
            {row.names.map(name => <button key={name} title={[...row.parent,name].join('/')} aria-pressed={row.active === name} onClick={() => folder([...row.parent,name])}>{name}</button>)}
          </div>)}</nav>}
          <label className="copycat-search">Formen suchen<input type="search" autoFocus autoComplete="off"
            placeholder={category === 'basic' ? 'z. B. Treppe, Stufe oder Panel' : 'Name oder Bibliotheksreferenz'}
            value={query} onChange={event => { setQuery(event.target.value); setLimit(48); setError('') }} /></label>
          <div ref={resultsRef} className="copycat-results" id={`${id}-forms`} role="tabpanel" aria-labelledby={`${id}-tab-${category}`}>
            <p className="copycat-result-count" role="status">{filtered.length} {filtered.length === 1 ? 'Form' : 'Formen'}
              {category === 'saved' && ' · Persönliche Auswahl und geladene Formbibliothek'}</p>
            {filtered.length ? <>
              <div className="copycat-grid" onKeyDown={event => {
                if (!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End'].includes(event.key) || !event.target.closest('.copycat-card')) return
                const grid=event.currentTarget, cards=[...grid.querySelectorAll('.copycat-card')], index=cards.indexOf(event.target.closest('.copycat-card'))
                const columns=getComputedStyle(grid).gridTemplateColumns.split(' ').length
                const delta={ArrowLeft:-1,ArrowRight:1,ArrowUp:-columns,ArrowDown:columns}[event.key]
                const next=event.key === 'Home' ? 0 : event.key === 'End' ? cards.length-1 : Math.max(0,Math.min(cards.length-1,index+delta))
                event.preventDefault();cards[next]?.focus();cards[next]?.click()
              }}>{filtered.slice(0, limit).map(group => { const option=tileOption(group); return <ShapeCard key={group.key}
                option={option} versions={group.versions.length} selected={group.versions.some(v => v.id === chosenId)} previews={previews} onSelect={setChosenId} onActivate={choose} /> })}</div>
              {filtered.length > limit && <button className="copycat-more" type="button" onClick={() => setLimit(limit + 48)}>Weitere Formen anzeigen</button>}
            </> : <div className="copycat-empty">
              <strong>{query ? 'Keine passende Form gefunden' : 'Noch keine gespeicherten Formen'}</strong>
              <p>{query ? 'Versuche einen anderen Namen oder lösche die Suche.'
                : 'Speichere einen Formgraphen mit „Graph speichern“ oder lade eine Formbibliothek.'}</p>
            </div>}
          </div>
        </section>
        <section className={`copycat-detail${chosen ? '' : ' is-empty'}`} aria-label="Vorschau der Auswahl" aria-busy={Boolean(chosen && !ready && !previewError)}>
          {chosen &&
            <div className="copycat-detail-heading"><span className="copycat-eyebrow">{sources[chosen.kind]}</span><h3>{chosen.name}</h3>
              <p>Version {versionOf(chosen)} · {chosen.reference || chosen.id}</p></div>}
          <div className={`copycat-preview-stage${ready ? '' : ' is-pending'}`}>
            <Viewer plan={ready || null} blockFrame catalog={catalog} />
            {!ready && <div className="copycat-preview-placeholder" role="status">{chosen
              ? previewError ? <p className="error">Diese Form kann nicht geladen werden: {previewError}</p> : <p>3D-Vorschau wird berechnet …</p>
              : <><span className="copycat-placeholder-symbol" aria-hidden="true">◇</span>
                <h3>Eine Form auswählen</h3><p>Klicke auf eine Kachel, um die Form in 3D zu prüfen.</p></>}
            </div>}
          </div>
          {chosen &&
            <div className="copycat-detail-info">
              <p>{ready ? `${ready.count.toLocaleString('de-DE')} belegte Rasterzellen` : 'Vorschau der gewählten Form'} · Rahmen: 16 hB</p>
              <p>Ziehen zum Drehen · Scrollen zum Zoomen</p>
              {chosen.kind === 'base' ? <p>Grundformen werden in Standardausrichtung eingesetzt. {mode === 'form'
                ? 'Größe und Ausrichtung lassen sich danach im Inspektor ändern.'
                : 'Copycat-Operatoren im Graphen drehen oder spiegeln das Ergebnis.'}</p>
                : <p>Die Auswahl übernimmt die gespeicherte Geometrie mit ihren Materialgruppen.</p>}
            </div>}
        </section>
      </div>
      {versionGroup && <Modal title={`Version wählen · ${versionGroup.name}`} onClose={() => setVersionGroup(null)} className="version-dialog"><p>Eine genaue gespeicherte Version auswählen. Abbrechen übernimmt nichts.</p><div className="version-list">{versionGroup.versions.map(option => <button key={option.id} aria-pressed={chosenId === option.id} title={option.reference || option.id} onClick={() => setChosenId(option.id)} onDoubleClick={() => choose(option,true)}>{pathOf(option).at(-1)} · Version {versionOf(option)}<small>{option.reference || option.id}</small></button>)}</div><footer><button onClick={() => setVersionGroup(null)}>Zurück</button><button className="primary" disabled={!ready || !versionGroup.versions.some(v => v.id === chosenId)} onClick={() => choose(chosen,true)}>Version übernehmen</button></footer></Modal>}
      <footer className="copycat-dialog-footer">
        <div className="copycat-selection-status" role="status">{error ? <span className="error">{error}</span>
          : chosen ? <><strong>{chosen.name}</strong><span> zur Übernahme ausgewählt</span></> : 'Noch keine Form ausgewählt'}</div>
        <div className="button-row"><button type="button" disabled={applying} onClick={onClose}>Abbrechen</button>
          <button type="button" className="primary" disabled={!ready || applying} onClick={() => choose()}>{applying ? 'Wird übernommen …' : 'Auswahl übernehmen'}</button></div>
      </footer>
    </div>
  </dialog>
}
