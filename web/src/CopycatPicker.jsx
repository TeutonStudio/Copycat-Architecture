import { useEffect, useId, useMemo, useRef, useState } from 'react'
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

function ShapeCard({ option, selected, previews, onSelect }) {
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
    onClick={() => onSelect(option.id)}>
    <span className="copycat-thumbnail" aria-hidden="true">
      {thumbnail?.image ? <img src={thumbnail.image} alt="" draggable="false" />
        : <span className={thumbnail?.error ? 'error' : ''} title={thumbnail?.error}>
          {thumbnail?.error ? 'Vorschau nicht verfügbar' : 'Vorschau …'}
        </span>}
      {selected && <span className="copycat-check">✓</span>}
    </span>
    <strong>{option.name}</strong>
    <small>{option.kind === 'library' ? option.reference : sources[option.kind]}</small>
  </button>
}

export default function CopycatPicker({ mode, savedForms, library, initialId, initialCategory = 'basic', onChoose, onClose }) {
  const { catalog } = useWorkspace()
  const dialog = useRef(null)
  const id = useId()
  const [category, setCategory] = useState(initialCategory)
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(48)
  const [chosenId, setChosenId] = useState(initialId || null)
  const [previews, setPreviews] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [applying, setApplying] = useState(false)
  const options = useMemo(() => listCopycatOptions({ savedForms, library }), [savedForms, library])
  const chosen = options.find(option => option.id === chosenId)
  const words = query.toLocaleLowerCase('de').trim().split(/\s+/).filter(Boolean)
  const filtered = options.filter(option => option.category === category && words.every(word =>
    `${option.name} ${option.source || ''} ${option.reference || ''} ${sources[option.kind]}`.toLocaleLowerCase('de').includes(word)))
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

  const switchCategory = next => {
    setCategory(next); setQuery(''); setLimit(48); setChosenId(null); setError('')
  }
  const choose = async () => {
    if (!chosen || !ready || applying) return
    setApplying(true); setError('')
    try { await onChoose(chosen); onClose() }
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
          <label className="copycat-search">Formen suchen<input type="search" autoFocus autoComplete="off"
            placeholder={category === 'basic' ? 'z. B. Treppe, Stufe oder Panel' : 'Name oder Bibliotheksreferenz'}
            value={query} onChange={event => { setQuery(event.target.value); setLimit(48); setChosenId(null); setError('') }} /></label>
          <div className="copycat-results" id={`${id}-forms`} role="tabpanel" aria-labelledby={`${id}-tab-${category}`}>
            <p className="copycat-result-count" role="status">{filtered.length} {filtered.length === 1 ? 'Form' : 'Formen'}
              {category === 'saved' && ' · Persönliche Auswahl und geladene Formbibliothek'}</p>
            {filtered.length ? <>
              <div className="copycat-grid">{filtered.slice(0, limit).map(option => <ShapeCard key={option.id}
                option={option} selected={option.id === chosenId} previews={previews} onSelect={setChosenId} />)}</div>
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
              {chosen.reference && <p>{chosen.reference}</p>}</div>}
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
      <footer className="copycat-dialog-footer">
        <div className="copycat-selection-status" role="status">{error ? <span className="error">{error}</span>
          : chosen ? <><strong>{chosen.name}</strong><span> zur Übernahme ausgewählt</span></> : 'Noch keine Form ausgewählt'}</div>
        <div className="button-row"><button type="button" disabled={applying} onClick={onClose}>Abbrechen</button>
          <button type="button" className="primary" disabled={!ready || applying} onClick={choose}>{applying ? 'Wird übernommen …' : 'Auswahl übernehmen'}</button></div>
      </footer>
    </div>
  </dialog>
}
