import {identityOf,dependencies} from './documents.js'
export function groupVersions(entries) {
 const groups=new Map()
 for(const entry of entries) {
  const key=`${entry.collection}:${identityOf(entry)}`
  if(!groups.has(key))groups.set(key,{id:key,documentId:identityOf(entry),collection:entry.collection,versions:[]})
  groups.get(key).versions.push(entry)
 }
 return [...groups.values()].map(group=>{const versions=group.versions.sort((a,b)=>(b.version || 1)-(a.version || 1));return {...group,name:versions[0].name,type:versions[0].type,versions,latest:versions[0]}})
}
export function deletionState(workspace,entries) {
 return entries.map(entry=>({entry,references:dependencies(workspace,entry)}))
}
export function deleteVersions(workspace,collection,ids) {
 if(!ids.length)throw new Error('Eine Version auswählen.')
 const selected=workspace[collection].filter(entry=>ids.includes(entry.id))
 if(selected.length!==new Set(ids).size)throw new Error('Die Auswahl ist nicht mehr aktuell. Bibliothek erneut öffnen.')
 // Validate the complete transaction against current storage, including other tabs/drafts.
 const locked=deletionState(workspace,selected).find(item=>item.references.length)
 if(locked)throw new Error(`v${locked.entry.version || 1} wird noch verwendet: ${locked.references.join(', ')}`)
 return {...workspace,[collection]:workspace[collection].filter(entry=>!ids.includes(entry.id))}
}
