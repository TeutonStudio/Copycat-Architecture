import { IconSelect } from './IconSelect.jsx'
import {libraryReferenceEntries,previewReferenceEntry} from './reference-groups.js'
import baseLibrary from '../../examples/library.json'
import {JsonEditor} from './JsonEditor.jsx'
import {referenceDisabled} from './studio-actions.js'
import {useEffect,useMemo,useState} from 'react'
import {Modal} from './Modal.jsx'
import {Viewer} from './Preview.jsx'
import {useWorkspace} from './Workspace.jsx'
import {readGraph} from './model.js'
import {validateNodeGroup} from './node-groups.js'
import {appendVersion,identityOf,validateReferences} from './documents.js'
import {groupVersions,deletionState,deleteVersions} from './version-groups.js'
import {cardResult,cardTypeLabel} from './card-types.js'

export function FileDialog({mode,onChoose,onReference,currentIdentity,readOnly,actions,onClose,referenceOnly=false,referenceTargetId=null,library=baseLibrary}) {
 const {data,update,catalog}=useWorkspace()
 const [query,setQuery]=useState(''),[kind,setKind]=useState('all'),[selected,setSelected]=useState(null),[deleting,setDeleting]=useState(false),[error,setError]=useState(''),[editing,setEditing]=useState(false)
 const entries=useMemo(()=>[...data.graphs.map(entry=>({...entry,collection:'graphs',type:entry.document?.graph ? (entry.document.graph.nodes.some(n=>n.kind==='GROUP_INPUT')?'Gruppe · ':'')+cardTypeLabel(cardResult(entry.document)?.resultType || (entry.mode==='form'?'COPYCAT':'BUILDING')):'Graph'})),...data.nodeGroups.map(entry=>({...entry,collection:'nodeGroups',type:'Knotengruppe'})),...(referenceOnly?libraryReferenceEntries(library):[])],[data,referenceOnly,library])
 const groups=useMemo(()=>groupVersions(entries),[entries]),chosen=entries.find(entry=>entry.id===selected),chosenGroup=groups.find(group=>group.versions.some(v=>v.id===selected))
 const filtered=groups.filter(group=>(kind==='all' || group.collection===kind) && group.name.toLocaleLowerCase('de').includes(query.toLocaleLowerCase('de'))).sort((a,b)=>a.name.localeCompare(b.name,'de'))
 const [plan,setPlan]=useState(null)
 useEffect(()=>{setPlan(null);setError('');if(!chosen)return;const timer=setTimeout(()=>{try{setPlan(previewReferenceEntry(chosen,library))}catch(e){setError(`Vorschau: ${e.message}`)}},0);return ()=>clearTimeout(timer)},[chosen?.id,library])
 const legacyGroup=chosen?.collection==='nodeGroups'&&!chosen.document
 const payload=chosen && (legacyGroup?Object.fromEntries(['name','mode','schema','nodes','wires','groups','materials','library','palette','graphInterface'].filter(key=>chosen[key]!==undefined).map(key=>[key,chosen[key]])):chosen.document)
 const saveDraft=payload=>{const {collection,type,...entry}=chosen;const changed=legacyGroup?{...entry,...payload,preview:undefined,document:undefined}:{...entry,document:payload};const accepted=update(current=>({...current,[collection]:appendVersion(current[collection],changed,identityOf(chosen))}));setEditing(false);setSelected(accepted[collection].at(-1).id)}
 const disabledReference=referenceDisabled(chosen,currentIdentity,readOnly,mode),replacing=referenceTargetId!==null
 return <Modal title={referenceOnly?'Referenz auswählen':'Bibliothek'} className="file-dialog" onClose={onClose}>
  <div className="file-dialog-actions">{actions}</div>
  <div className="file-filters"><label>Suchen<input autoFocus data-autofocus type="search" value={query} onChange={e=>setQuery(e.target.value)}/></label><label>Typ<IconSelect domain="fileType" value={kind} onChange={e=>setKind(e.target.value)}><option value="all">Alle</option><option value="graphs">Graphkarten</option><option value="nodeGroups">Knotengruppen</option>{referenceOnly && <><option value="libraryForms">Bibliotheksformen</option><option value="libraryModules">Bibliothekssegmente</option><option value="libraryGraphs">Graphdefinitionen</option></>}</IconSelect></label></div>
  <div className="file-grid"><div className="file-list">{filtered.map(group=><button key={group.id} aria-pressed={chosenGroup?.id===group.id} onClick={()=>setSelected(group.latest.id)} onDoubleClick={()=>{const entry=group.latest;if(!referenceOnly || !referenceDisabled(entry,currentIdentity,readOnly,mode))(referenceOnly?onReference:onChoose)(entry)}}><strong>{group.name}</strong><small>{group.type} · {group.collection.startsWith('library')?'Bibliothek':'Browser'} · {group.versions.length} {group.versions.length===1?'Version':'Versionen'}</small><small>{group.documentId}</small></button>)}{!filtered.length && <p>Keine passenden Einträge.</p>}</div>
   <div className="file-preview">{chosenGroup && <label className="file-version">Version<IconSelect domain="version" aria-label="Bibliotheksversion" value={selected} onChange={e=>setSelected(e.target.value)}>{chosenGroup.versions.map(entry=><option key={entry.id} value={entry.id} iconMeta={{version:entry.version || 1}}>v{entry.version || 1}{entry.savedAt?` · ${new Date(entry.savedAt).toLocaleString('de-DE')}`:''}</option>)}</IconSelect></label>}{plan?.value && <p className="reference-value" role="status">{plan.value}</p>}<Viewer plan={plan} catalog={catalog} blockFrame={chosen?.mode==='form' || chosen?.document && cardResult(chosen.document)?.resultType==='COPYCAT'}/>{!plan && <p>Vorschau wird geladen oder ist für diesen Eintrag nicht verfügbar.</p>}</div>
  </div>
  <p className={error?'file-status error':'file-status hint'} role="status">{error || disabledReference || (replacing?'Version wählen und für diesen Knoten übernehmen.':'Version wählen, als eigene Karte öffnen oder als Referenzknoten einfügen.')}</p>
  {editing && chosen && <JsonDialog mode={chosen.mode} document={payload} library={chosen.library || baseLibrary} parse={legacyGroup?validateNodeGroup:undefined} saveLabel="Neue Version speichern" onApply={saveDraft} onClose={()=>setEditing(false)}/>}
  {deleting && chosenGroup && <DeleteDialog group={chosenGroup} initial={chosen.id} onClose={()=>setDeleting(false)} onDeleted={()=>{setDeleting(false);setSelected(null)}}/>}
  <footer><button onClick={onClose}>Schließen</button><button disabled={!chosen || !['graphs','nodeGroups'].includes(chosen.collection)} onClick={()=>setEditing(true)}>JSON bearbeiten …</button><button className="danger" disabled={!chosen || !['graphs','nodeGroups'].includes(chosen.collection)} onClick={()=>setDeleting(true)}>Löschen …</button><button disabled={Boolean(disabledReference)} title={disabledReference || (replacing?'Genaue Version für diesen Knoten auswählen':'Genaue Version als typisierten Referenzknoten einfügen')} onClick={()=>onReference(chosen)}>{replacing?'Auswählen':'Als Referenz einfügen'}</button>{!referenceOnly && <button className="primary" disabled={!chosen} onClick={()=>onChoose(chosen)}>Als Karte öffnen</button>}</footer>
 </Modal>
}
export function DeleteDialog({group,initial,onClose,onDeleted}) {
 const {data,update}=useWorkspace(),[choice,setChoice]=useState(initial),[error,setError]=useState('')
 const entries=data[group.collection].filter(e=>identityOf(e)===group.documentId).sort((a,b)=>(b.version || 1)-(a.version || 1)),states=deletionState(data,entries)
 const selected=choice==='all'?states:states.filter(s=>s.entry.id===choice),locked=selected.some(s=>s.references.length)
 return <Modal title={`Löschen · ${group.name}`} className="delete-dialog" onClose={onClose}><p>Wähle eine Version oder den gesamten Eintrag. Verwendete Versionen sind gesperrt.</p><fieldset className="delete-versions"><legend>Versionen und Verwendung</legend>{states.map(({entry,references})=><label key={entry.id} className={references.length?'version-locked':'delete-choice'}><input type="radio" name="delete-version" checked={choice===entry.id} disabled={references.length>0} onChange={()=>setChoice(entry.id)}/><span><strong>v{entry.version || 1} · {references.length?'Gesperrt':'Löschbar'}</strong><small>{references.length?references.join(' · '):'Wird von keinem gespeicherten Graphen oder Entwurf verwendet.'}</small></span></label>)}<label className="delete-choice"><input type="radio" name="delete-version" checked={choice==='all'} disabled={states.some(s=>s.references.length)} onChange={()=>setChoice('all')}/><span>Gesamten Eintrag löschen · {entries.length} Versionen</span></label></fieldset>{error && <p role="alert">{error}</p>}<footer><button onClick={onClose}>Abbrechen</button><button className="danger" disabled={!selected.length || locked} onClick={()=>{try{update(current=>deleteVersions(current,group.collection,selected.map(s=>s.entry.id)));onDeleted()}catch(e){setError(e.message)}}}>{choice==='all'?'Gesamten Eintrag löschen':'Gewählte Version löschen'}</button></footer></Modal>
}
export function JsonDialog({mode,document,library,onApply,onClose,parse,saveLabel='Übernehmen'}) {
 const [text,setText]=useState(()=>JSON.stringify(document,null,2)),[applyError,setError]=useState('')
 let error=applyError,candidate
 try{candidate=parse?parse(JSON.parse(text)):validateReferences(readGraph(JSON.parse(text),mode),library)}catch(e){candidate=null;if(!error)error=e.message}
 return <Modal title="JSON-Arbeitskopie" onClose={onClose}><p>Übernahme prüft Syntax, Schema, Typen und Referenzen. Abbrechen verwirft die Arbeitskopie.</p><JsonEditor value={text} onChange={value=>{setText(value);setError('')}}/><p className={candidate?'hint':'error'} role="status">{candidate?'Arbeitskopie ist gültig.':error}</p><footer><button onClick={onClose}>Abbrechen</button><button className="primary" disabled={!candidate} onClick={()=>{try{onApply(candidate);onClose()}catch(e){setError(e.message)}}}>{saveLabel}</button></footer></Modal>
}
