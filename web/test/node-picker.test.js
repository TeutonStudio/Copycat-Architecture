import test from 'node:test'
import assert from 'node:assert/strict'
import {portStyle} from '../src/port-styles.js'
import {model} from '../src/model.js'
import {pickerEntries,selectPickerEntries,groupPickerEntries} from '../src/node-picker.js'
const kinds=mode=>(mode==='building'?model.building:model.form.map(entry=>entry.kind)).filter(kind=>!['OUTPUT','POSITION_Y','POSITION_Z'].includes(kind))

test('both pickers group operator families and create native previews without changing catalog metadata',()=>{
 const original=JSON.stringify(model)
 for(const mode of ['form','building']) {
  const entries=pickerEntries(kinds(mode),mode,{},model.nodeProperties[mode])
  assert.ok(entries.length<kinds(mode).length)
  assert.equal(entries.filter(entry=>entry.family==='copycat').length,1)
  assert.equal(entries.filter(entry=>entry.family==='segments').length,mode==='building'?1:0)
  for(const entry of entries) {
   assert.equal(entry.preview.node.kind,entry.kind)
   assert.ok(Array.isArray(entry.preview.ports),entry.kind)
   assert.ok(entry.output,entry.kind)
   assert.deepEqual(portStyle(entry.output),portStyle(entry.preview.outputType),entry.kind)
  }
 }
 assert.equal(JSON.stringify(model),original)
})

test('combined filters search descriptions and narrow by category, output and the single property',()=>{
 const entries=pickerEntries(kinds('building'),'building',{},model.nodeProperties.building)
 const selected=selectPickerEntries(entries,{category:'copycat',capability:'yes',output:'shape'})
 assert.ok(selected.some(entry=>entry.kind==='COPYCAT_UNION'))
 assert.ok(!selected.some(entry=>entry.kind==='COPYCAT_DIFFERENCE'))
 assert.ok(selected.every(entry=>entry.variable && entry.category==='copycat' && entry.output==='shape'))
 assert.ok(selectPickerEntries(entries,{query:'Öffnungen'}).some(entry=>entry.kind==='ELEVATOR_SHAFT'))
 assert.deepEqual(selectPickerEntries(entries,{query:'never a node kind'}),[])
})

test('numeric input sorting and each grouping preserve every filtered entry exactly once',()=>{
 const entries=pickerEntries(kinds('building'),'building',{},model.nodeProperties.building)
 const sorted=selectPickerEntries(entries,{sort:'inputs',direction:'desc'})
 for(let i=1;i<sorted.length;i++) assert.ok(sorted[i-1].inputs>=sorted[i].inputs)
 for(const group of ['none','category','output','property']) {
  const groups=groupPickerEntries(sorted,group)
  assert.deepEqual(groups.flatMap(([,entries])=>entries).map(entry=>entry.kind).sort(),entries.map(entry=>entry.kind).sort())
 }
})

test('Transformieren is selected within each operator family and is not a separate catalog card',{skip:!model.building.includes('TRANSFORMATION')},()=>{
 for(const mode of ['form','building']) {
  const defaults=pickerEntries(kinds(mode),mode,{},model.nodeProperties[mode])
  assert.ok(!defaults.some(entry=>['COPYCAT_TRANSFORM','SEGMENT_TRANSFORM'].includes(entry.kind)))
  assert.equal(selectPickerEntries(defaults,{query:'transformieren'}).filter(entry=>entry.family).length,mode==='building'?2:1)
  const selected=pickerEntries(kinds(mode),mode,{},model.nodeProperties[mode],{copycat:'COPYCAT_TRANSFORM',segments:'SEGMENT_TRANSFORM'})
  assert.equal(selected.filter(entry=>entry.kind==='COPYCAT_TRANSFORM').length,1)
  assert.equal(selected.find(entry=>entry.family==='copycat').preview.ports.length,2)
  assert.equal(selected.length,defaults.length)
 }
})
