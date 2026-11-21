import test from 'node:test'
import assert from 'node:assert/strict'
import { appendVersion, documentName, identityOf, namedWorkspace, renameDocument, mergeVersions } from '../src/documents.js'
import { emptyWorkspace, validateWorkspace, migrateWorkspace } from '../src/storage.js'
import { categoryOfNode, describeCatalogNode, filterCatalog, nodeCategories } from '../src/node-catalog.js'
import { model } from '../src/model.js'

test('renaming is persistent identity metadata shared by every version and the draft, without changing saved content',()=>{
  let data=emptyWorkspace()
  data.graphs=appendVersion(data.graphs,{mode:'building',name:'Erster Name',document:{name:'Erster Name',graph:{nodes:[{id:'a'}]}}},'shared')
  data.graphs=appendVersion(data.graphs,{mode:'building',name:'Zweiter Name',document:{name:'Zweiter Name',graph:{nodes:[{id:'b'}]}}},'shared')
  data.graphs=appendVersion(data.graphs,{mode:'building',name:'Andere Karte',document:{graph:{nodes:[]}}},'other')
  data.drafts.graph={mode:'building',fileIdentity:'shared',document:{name:'Alter Entwurfsname',graph:{nodes:[]}}}
  const before=structuredClone(data), renamed=renameDocument(data,'shared','  Gemeinsamer Name  ')
  assert.deepEqual(data,before)
  assert.deepEqual(renamed.graphs,before.graphs)
  const reloaded=migrateWorkspace(validateWorkspace(JSON.parse(JSON.stringify(renamed))))
  assert.equal(documentName(reloaded,'shared','Alter Entwurfsname'),'Gemeinsamer Name')
  assert.deepEqual(namedWorkspace(reloaded).graphs.filter(e=>identityOf(e)==='shared').map(e=>e.name),['Gemeinsamer Name','Gemeinsamer Name'])
  assert.equal(namedWorkspace(reloaded).graphs.at(-1).name,'Andere Karte')
  assert.deepEqual(mergeVersions(renamed.graphs,namedWorkspace(reloaded).graphs),renamed.graphs)
  assert.equal(appendVersion(reloaded.graphs,{name:documentName(reloaded,'shared')},'shared').at(-1).version,3)
})
test('legacy version names resolve to the latest identity name until explicitly renamed',()=>{
  const data={...emptyWorkspace(),graphs:[{id:'one',mode:'building',document:{graph:{nodes:[]}},documentId:'legacy',name:'Alt',version:1},{id:'two',mode:'building',document:{graph:{nodes:[]}},documentId:'legacy',name:'Neu',version:2}]}
  assert.equal(documentName(data,'legacy','Entwurf'),'Neu')
  assert.equal(documentName(data,null,'Entwurf'),'Entwurf')
  assert.equal(documentName(renameDocument(data,'legacy','Umbenannt'),'legacy'),'Umbenannt')
  for(const name of ['','   ','x'.repeat(65)]) {
    assert.throws(()=>renameDocument(data,'legacy',name),/Name/)
  }
  assert.throws(()=>renameDocument(data,'legacy','a\nb'),/Name/)
  assert.throws(()=>validateWorkspace({...data,documentNames:[]}),/Graphnamen/)
})
test('every insertable engine node has a category and a concrete explanation; searches include purpose',()=>{
  const kinds=[...new Set([...model.building,...model.form.map(n=>n.kind)])].filter(kind=>!['OUTPUT','POSITION_Y','POSITION_Z'].includes(kind))
  for(const kind of kinds) {
    assert.ok(nodeCategories.some(([id])=>id===categoryOfNode(kind)),kind)
    assert.doesNotMatch(describeCatalogNode(kind),/Einen Knoten hinzufügen und seine Eigenschaften/,kind)
  }
  assert.ok(filterCatalog(kinds,'all','A minus B',{}).includes('COPYCAT_DIFFERENCE'))
  assert.ok(filterCatalog(kinds,'all','vierteldrehung',{}).includes('ROTATION'))
  assert.deepEqual(filterCatalog(kinds,'sources','',{}).sort(),['COPYCAT','INPUT','SAVED'])
  assert.deepEqual(filterCatalog(kinds,'landscape','qwertz',{}),[])
})
