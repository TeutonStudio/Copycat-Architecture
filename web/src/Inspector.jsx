import { useState } from 'react'
import { sourceMounting, mountSource } from './generated/engine.mjs'
import { formGroups, model } from './model.js'
import { MaterialField } from './Workspace.jsx'

export const names = {
  INPUT: 'Grundform', UNION: 'Vereinigung', INTERSECTION: 'Schnitt', DIFFERENCE: 'Differenz',
  SYMMETRIC_DIFFERENCE: 'Symmetrische Differenz', ROTATE: 'Copycat rotieren', ROTATION: 'Rotation', OUTPUT: 'Ergebnis',
  SAVED: 'Gespeicherter Copycat', MIRROR: 'Copycat spiegeln',
  COPYCAT_MIRROR: 'Copycat spiegeln', COPYCAT_ROTATE: 'Copycat rotieren',
  SEGMENT_UNION: 'Segment-Vereinigung', SEGMENT_INTERSECTION: 'Segment-Schnitt', SEGMENT_DIFFERENCE: 'Segment-Differenz', SEGMENT_SYMMETRIC_DIFFERENCE: 'Symmetrische Segment-Differenz',
  COPYCAT: 'Copycat-Referenz', SEGMENT: 'Wandsegment', PATTERN: 'Wandmuster', FLOOR: 'Boden', CEILING: 'Decke',
  ROOM: 'Raum', STOREY: 'Geschoss', ROOF: 'Dach', BUILDING: 'Gebäude'
}

export function portName(port, node) {
  const [kind, key] = port.id.split(':')
  if (node.kind === 'SEGMENT_DIFFERENCE' && kind === 'in') return key === '0' ? 'A · Grundsegment' : 'B · Abzug'
  if (kind === 'in') return `${(node.kind === 'PATTERN' || node.kind.startsWith('SEGMENT_')) ? 'Segment' : node.kind === 'STOREY' ? 'Raum' : 'Copycat'} ${Number(key) + 1}`
  if (kind === 'wall') return `Wand ${port.id.slice(5)}${port.length ? ` · ${port.length} B` : ''}`
  if (kind === 'level') return Number(key) === 0 ? 'Erdgeschoss' : `Geschoss ${Number(key) > 0 ? '+' : ''}${key}`
  if (kind === 'floor') return 'Boden'
  if (kind === 'ceiling') return 'Decke'
  if (kind === 'corner') return 'Ecke'
  if (kind === 'roof') return 'Dach'
  if (node.kind === 'ROTATE' && kind === '1') return 'Rotation'
  if (node.kind === 'DIFFERENCE') return kind === '0' ? 'A' : 'B'
  return `Eingang ${Number(kind) + 1}`
}

export function Field({ label, value, onChange, options, type = 'text', min, max, list, maxLength, step = 1 }) {
  const [draft, setDraft] = useState(null)
  if (options) return <label>{label}<select aria-label={label} value={value} onChange={e => onChange(typeof value === 'number' ? Number(e.target.value) : e.target.value)}>
    {options.map(option => {
      const [id, text] = Array.isArray(option) ? option : [option, option]
      return <option key={id} value={id} disabled={Array.isArray(option) && !!option[2]}>{text}</option>
    })}
  </select></label>
  if (type === 'checkbox') return <label className="check"><input aria-label={label} type="checkbox" checked={value} onChange={e => onChange(e.target.checked)} />{label}</label>
  return <label>{label}<input aria-label={label} value={draft ?? value} type={type} min={min} max={max} step={step} list={list} maxLength={maxLength}
    onChange={e => setDraft(e.target.value)}
    onBlur={() => {
      if (draft !== null) {
        setDraft(null)
        const next = type === 'number' ? Number(draft) : draft
        if (type === 'number' && (!draft.trim() || !Number.isInteger(next) || next < min || next > max)) return
        if (next !== value) onChange(next)
      }
    }}
    onKeyDown={e => {
      if (e.key === 'Enter') e.currentTarget.blur()
      if (e.key === 'Escape') setDraft(null)
    }} /></label>
}

const shapeNames = { full: 'Vollblock', slab: 'Stufe', stairs: 'Treppe', panel: 'Panel', slice: 'Gerade Leiste', vertical_slice: 'Vertikale Leiste', corner_slice: 'Eckscheibe', layer: 'Schicht', beam: 'Balken', byte: 'Byte', step: 'Feste Leiste', vertical_step: 'Feste vertikale Leiste', half_panel: 'Halbes Panel', flat_pane: 'Mittige Scheibe', byte_panel: 'Byte-Panel', half_layer: 'Halbe Schicht', vertical_half_layer: 'Vertikale halbe Schicht', stacked_half_layer: 'Gestapelte halbe Schicht' }
const directions = [[2, 'Nord (−Z)'], [3, 'Ost (+X)'], [0, 'Süd (+Z)'], [1, 'West (−X)']]
const angles = [[0, '0°'], [1, '90°'], [2, '180°'], [3, '270°']]
const mountings = [['up', 'Oben (+Y)'], ['down', 'Unten (−Y)'], ['east', 'Seitlich +X'], ['west', 'Seitlich −X'], ['south', 'Seitlich +Z'], ['north', 'Seitlich −Z']]
const axes = [['X', 'X · Ost/West'], ['Y', 'Y · Höhe'], ['Z', 'Z · Süd/Nord'], ['NORMAL', 'Lokale Normale']]
const groupOptions = (groups, empty) => [['', empty], ...groups.map(group => [group.id, group.name])]
const materialModes = [['SEPARATED', 'Separiert'], ['GROUPED', 'Gruppiert']]
const viewFilters = [['all', 'Alles'], ['walls', 'Wände'], ['floor', 'Boden'], ['ceiling', 'Decke'], ['roof', 'Dach']]
const typeNames = [['flat', 'Flachdach'], ['gable', 'Satteldach']]

function NumberGrid({ entries, keys, ranges, update, prefix, scale = 1, unit = '' }) {
  return entries.map((entry, index) => <div key={index} className="number-grid">
    {keys.map(key => <Field key={key} label={`${prefix} ${index + 1} · ${key.toUpperCase()}${unit ? ` (${unit})` : ''}`} value={entry[key] * scale} type="number"
      step={scale} min={ranges[key][0] * scale} max={ranges[key][1] * scale} onChange={value => { if (Number.isInteger(value / scale)) update(index, { ...entry, [key]: value / scale }) }} />)}
  </div>)
}

export function NodeFields({ mode, node, document, edit, library, savedForms = [], onLoadCopycat }) {
  const field = (key, label, options, type, min, max) =>
    <Field key={key} label={label} value={node[key]} onChange={value => edit({ [key]: value })} {...{ options, type, min, max }} />
  const number = (key, label, min, max) => field(key, label, undefined, 'number', min, max)
  const groups = formGroups(document)
  const savedPicker = <Field label="Gespeicherten Copycat laden" value="" options={[["", "Copycat auswählen …", true], ...savedForms.map(entry => [entry.id, entry.name])]} onChange={onLoadCopycat} />
  if (mode === 'form') {
    const part = node.part
    const partField = (key, label, options, type, min, max) =>
      <Field key={key} label={label} value={part[key]} onChange={value => edit({ part: { ...part, [key]: value } })}
        {...{ options, type, min, max }} />
    const adapter = model.shapes.find(shape => shape.id === part.source)
    const groupFields = ['UNION', 'SYMMETRIC_DIFFERENCE'].includes(node.kind)
    return <>
      {node.kind === 'SAVED' && <>{savedPicker}<p className="hint">{node.savedCopycat?.name || 'Copycat auswählen'}</p></>}
      {node.kind === 'MIRROR' && <Field label="Spiegelebene" value={node.mirror?.plane || 'X'} options={['X', 'Y', 'Z']} onChange={plane => edit({ mirror: { plane } })} />}
      {node.kind === 'INPUT' && <>
        <Field label="Copycat-Grundform" value={part.source} options={model.shapes.map(shape => [shape.id, shapeNames[shape.name] ?? shape.name])}
          onChange={source => edit({ part: { ...part, source, size: 1, tilt: 0, discCut: 0, offset: { x: 0, y: 0, z: 0 } } })} />
        {partField('size', 'Größe', undefined, 'number', 1, adapter.max)}
        {partField('turn', 'Drehung um Y', directions)}
        {partField('spin', 'Drehung um die lokale Normale', angles)}
        <Field label="Neigung" value={sourceMounting(part)} options={mountings.map(([id, label]) => [id, label, !adapter.tiltable && !['up', 'down'].includes(id)])} onChange={normal => edit({ part: { ...part, ...mountSource(part, normal) } })} />
        {part.source === 'copycats:copycat_flat_pane' && partField('discCut', 'Scheibenanteil', undefined, 'number', 0, 2)}
        <p className="hint">{!adapter.tiltable && 'Diese native Quelle erlaubt nur Oben/Unten. '}Neigung richtet die lokale Normale aus. Drehung um Y dreht die Form einschließlich ihrer Neigung.</p>
        {partField('group', 'Materialgruppe', groups.map(group => [group.id, group.name]))}
        <NumberGrid entries={[part.offset]} keys={['x', 'y', 'z']} ranges={{ x: [-15, 15], y: [-15, 15], z: [-15, 15] }}
          prefix="Versatz" unit="hB" update={(_, offset) => edit({ part: { ...part, offset } })} />
      </>}
      {['ROTATE', 'ROTATION'].includes(node.kind) && <>
        {field('rotationAxis', 'Achse', axes)}
        {field('quarterTurns', 'Drehwinkel', angles)}
      </>}
      {groupFields && field('materialMode', 'Materialmodus', materialModes)}
      {node.materialMode === 'GROUPED' && field('materialGroup', 'Ergebnisgruppe', groupOptions(groups, 'Erste Gruppe'))}
      {groupFields && node.materialMode === 'SEPARATED' && node.inputs.map((_, index) =>
        <Field key={index} label={`Gruppe Eingang ${index + 1}`} options={groupOptions(groups, 'Vom Eingang übernehmen')}
          value={node.inputGroups[index] ?? ''} onChange={group => edit({
            inputGroups: node.inputs.map((_, i) => i === index ? group : node.inputGroups[i] ?? '')
          })} />)}
      {node.kind === 'OUTPUT' && field('previewGroup', 'Vorschaugruppe', groupOptions(groups, 'Alle'))}
      {node.kind === 'DIFFERENCE' && <button type="button" onClick={() => edit({ inputs: [...node.inputs].reverse() })}>A / B tauschen</button>}
    </>
  }

  if (node.id === 'empty_wall') return <p className="hint">Standardquelle für eine freie Wandöffnung.</p>
  const enclosure = node.kind === 'ROOM' || (node.kind === 'STOREY' && node.storeyMode === 'walls')
  const references = key => Object.keys(library[key] ?? {})
  const ref = (key, label, list) => <Field label={label} value={node[key]} list={list} onChange={value => edit({ [key]: value })} />
  const updateRect = (index, rect) => edit({ rectangles: node.rectangles.map((item, i) => i === index ? rect : item) })
  return <>
    <datalist id="library-forms">{references('forms').map(id => <option key={id} value={id} />)}</datalist>
    <datalist id="library-modules">{references('modules').map(id => <option key={id} value={id} />)}</datalist>
    {field('name', 'Knotenname')}
    {node.kind === 'COPYCAT' && savedPicker}
    {node.kind === 'COPYCAT_MIRROR' && <Field label="Spiegelebene" value={node.mirror?.plane || 'X'} options={['X', 'Y', 'Z']} onChange={plane => edit({ mirror: { plane } })} />}
    {node.kind === 'COPYCAT_ROTATE' && <>{field('rotationAxis', 'Drehachse', ['X', 'Y', 'Z'])}{number('quarterTurns', 'Vierteldrehungen', 0, 3)}</>}
    {node.kind === 'SEGMENT_DIFFERENCE' && <button onClick={() => edit({ inputs: [...node.inputs].reverse() })}>A / B tauschen</button>}
    {field('role', 'Materialrolle')}
    {field('materialMode', 'Materialmodus', materialModes)}
    {node.kind === 'STOREY' && field('storeyMode', 'Zusammensetzung', [['rooms', 'Aus Räumen'], ['walls', 'Aus Wänden']])}
    {['COPYCAT', 'SEGMENT', 'FLOOR', 'CEILING', 'ROOF'].includes(node.kind) && ref('form', 'Form aus Bibliothek', 'library-forms')}
    {node.kind === 'SEGMENT' && <>
      {ref('module', 'Modul aus Bibliothek', 'library-modules')}
      {field('block', 'Vollblock-ID')}
      {number('length', 'Länge', 1, 128)}
    </>}
    {(enclosure || ['SEGMENT', 'PATTERN'].includes(node.kind)) && number('height', 'Höhe', 1, 16)}
    {['FLOOR', 'CEILING', 'ROOF'].includes(node.kind) && number('thickness', 'Dicke', 1, 4)}
    {['FLOOR', 'CEILING'].includes(node.kind) && field('occupied', 'Raumhöhe belegen', undefined, 'checkbox')}
    {node.kind === 'FLOOR' && field('finish', 'Bodenbelag', [['', 'Kein Belag'], ...model.carpets])}
    {node.kind === 'ROOF' && <>
      {field('roofType', 'Dachform', typeNames)}
      {field('ridge', 'Firstrichtung', [[0, 'Nord–Süd (Z)'], [1, 'Ost–West (X)']])}
      {number('overhang', 'Überhang', 0, 2)}
    </>}
    {['ROOM', 'STOREY', 'BUILDING'].includes(node.kind) && field('preview', 'Vorschaufilter', viewFilters)}
    {enclosure && <details open><summary>Grundriss</summary>
      <NumberGrid entries={node.rectangles} keys={['x', 'z', 'width', 'length']}
        ranges={{ x: [-32, 32], z: [-32, 32], width: [1, 32], length: [1, 32] }} prefix="Fläche" update={updateRect} />
      <div className="button-row">
        <button type="button" disabled={node.rectangles.length >= 16} onClick={() => edit({
          rectangles: [...node.rectangles, { x: 3, z: 0, width: 3, length: 3 }]
        })}>+ Fläche</button>
        <button type="button" disabled={node.rectangles.length <= 1}
          onClick={() => edit({ rectangles: node.rectangles.slice(0, -1) })}>Letzte entfernen</button>
      </div>
    </details>}
    {node.kind === 'STOREY' && !enclosure && <details><summary>Raumversatz</summary>
      <NumberGrid entries={node.inputs.map((_, i) => node.offsets[i] ?? { x: 0, y: 0, z: 0 })}
        keys={['x', 'z']} ranges={{ x: [-64, 64], z: [-64, 64] }} prefix="Raumversatz" scale={16} unit="hB · Raster 16" update={(index, offset) =>
          edit({ offsets: node.inputs.map((_, i) => i === index ? offset : node.offsets[i] ?? { x: 0, y: 0, z: 0 }) })} />
    </details>}
  </>
}

export function DocumentFields({ mode, document, edit, library, materials, setMaterials }) {
  const groups = formGroups(document)
  const usedGroups = new Set(document.graph.nodes.flatMap(node => [
    node.kind === 'INPUT' ? node.part?.group : null, node.materialGroup, node.previewGroup, ...(node.inputGroups ?? [])
  ]))
  function updateGroup(id, patch) {
    edit({ ...document, groups: groups.map(group => group.id === id ? { ...group, ...patch } : group) })
  }
  function addGroup() {
    const id = Array.from({ length: 9 }, (_, i) => `group${i + 1}`).find(id => groups.every(group => group.id !== id))
    const priority = Array.from({ length: 65 }, (_, i) => i - 32).find(value => groups.every(group => group.priority !== value) && value > 0)
    edit({ ...document, groups: [...groups, { id, name: id, role: 'surface', priority }] })
  }
  const palette = document.palette ?? {}
  const [role, setRole] = useState('')
  return <details className="document-fields"><summary>{mode === 'form' ? 'Dokument und Materialgruppen' : 'Dokument und Materialpalette'}</summary>
    <Field label="Dokumentname" value={document.name ?? 'Entwurf'} maxLength={64}
      onChange={name => edit({ ...document, name })} />
    {mode === 'form' ? <>
      {groups.map(group => <fieldset key={group.id}><legend>{group.id}</legend>
        <Field label="Name" value={group.name} maxLength={32} onChange={name => updateGroup(group.id, { name })} />
        <Field label="Materialrolle" value={group.role} onChange={role => updateGroup(group.id, { role })} />
        <MaterialField label={`Material ${group.name}`} value={materials[group.id] || ''} onChange={value => setMaterials(previous => ({ ...previous, [group.id]: value }))} />
        <Field label="Vordergrund" value={group.priority} type="number" min={-32} max={32}
          onChange={priority => updateGroup(group.id, { priority })} />
        <button type="button" disabled={groups.length <= 1 || usedGroups.has(group.id)}
          onClick={() => edit({ ...document, groups: groups.filter(item => item.id !== group.id) })}>Gruppe entfernen</button>
      </fieldset>)}
      <button type="button" disabled={groups.length >= 8} onClick={addGroup}>+ Materialgruppe</button>
    </> : <>
      {Object.entries(palette).map(([id, material]) => <div key={id} className="palette-row">
        <MaterialField label={id} value={material} blockOnly onChange={value => edit({ ...document, palette: { ...palette, [id]: value } })} />
        <button type="button" aria-label={`Rolle ${id} entfernen`} onClick={() => edit({
          ...document, palette: Object.fromEntries(Object.entries(palette).filter(([key]) => key !== id))
        })}>×</button>
      </div>)}
      <label>Neue Materialrolle<input value={role} onChange={event => setRole(event.target.value)} /></label>
      <button type="button" disabled={!/^[a-zA-Z0-9_-]{1,32}$/.test(role) || Object.hasOwn(palette, role) || Object.keys(palette).length >= 16}
        onClick={() => { edit({ ...document, palette: { ...palette, [role]: 'minecraft:stone' } }); setRole('') }}>+ Rolle</button>
      <p className="hint">{Object.keys(library.forms ?? {}).length} Formen · {Object.keys(library.modules ?? {}).length} Module geladen</p>
    </>}
  </details>
}
