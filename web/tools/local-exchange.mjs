import {readFileSync,writeFileSync} from 'node:fs'
import {join} from 'node:path'
import assert from 'node:assert/strict'
import {previewGraph} from '../src/generated/engine.mjs'
import {readGraph,writeGraph} from '../src/model.js'
const directory=process.argv[2]
const corpus=JSON.parse(readFileSync(join(directory,'native.json'),'utf8')).cases
const local=JSON.parse(readFileSync(join(directory,'local.json'),'utf8')).cases
assert.ok(local.some(item=>item.name==='common-transformed-building'))
for(const item of local) {
 const original=corpus.find(value=>value.name===item.name),mode=item.building?'building':'form'
 const document=readGraph(item.document,mode),plan=previewGraph(item.building,document,original.library,'')
 assert.equal(plan.count,original.count,item.name);assert.equal(plan.raw,original.raw,item.name)
 item.document=writeGraph(mode,document)
}
writeFileSync(join(directory,'local-browser.json'),JSON.stringify({cases:local})+'\n')
console.log(`Local JVM → browser: ${local.length} common cases passed`)
