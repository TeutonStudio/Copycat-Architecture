import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import {operatorFamilies} from '../src/operator-families.js'
import {nodeControls} from '../src/node-controls.js'
import {nodePreview} from '../src/node-picker.js'
import { model } from '../src/model.js'
import { wireModels } from '../src/generated/engine.mjs'
import { contract, fieldType } from '../src/inspector-model.js'
import { choiceIcon, decorateChoices, fixedChoices, iconPaths, nativeChoiceDomain, operationIcon, typeIcon } from '../src/choice-icons.js'
import { enabledIndex, nextIndex, popupPosition, searchIndex } from '../src/select-model.js'

const procedural=new Set(['union','intersection','difference','symmetricDifference','invert','axis','angle','order','view','compass'])
const hasDrawing=icon=>assert.ok(icon && (iconPaths[icon.kind]?.length || procedural.has(icon.kind)),JSON.stringify(icon))
test('every catalog operation, exact output type and semantic filter group has an SVG',()=>{
  for(const [family,definition] of Object.entries(model.operators)) for(const operation of Object.keys(definition.operations)) hasDrawing(operationIcon(operation,family))
  for(const operation of ['COPYCAT_TRANSFORM','SEGMENT_TRANSFORM','COPYCAT_POSITION','SEGMENT_POSITION']) hasDrawing(operationIcon(operation))
  for(const type of [...model.cardTypes,...Object.keys(contract.portStyles)]) hasDrawing(typeIcon(type))
  assert.equal(new Set(model.cardTypes.map(type=>typeIcon(type).kind)).size,model.cardTypes.length,'Exact output types must have distinct motifs')
  assert.equal(new Set(['NUMBER_OPERATOR','POSITION_OPERATOR','STATEMENT_OPERATOR'].map(f=>operationIcon('VALUE',f).kind)).size,3)
})
test('all native fixed choices and recursively reachable editable enums are covered',()=>{
  const wire=wireModels(),enums=new Set()
  function visit(type,path,stack=new Set()) {
    type=type.replace(/\?$/,'')
    if(wire.enums[type]) {enums.add(type);for(const value of wire.enums[type])hasDrawing(choiceIcon(nativeChoiceDomain(type,path),value));return}
    if(stack.has(type))return
    stack=new Set([...stack,type])
    if(type.includes('<')) for(const child of type.slice(type.indexOf('<')+1,-1).split(','))visit(child,path,stack)
    else for(const [key,child] of Object.entries(wire[type]?.fields || {})) if(key!=='kind')visit(child,path+'.'+key,stack)
  }
  for(const mode of ['copycat','building']) for(const node of Object.values(contract[mode].nodes)) for(const [path,settings] of Object.entries(node.fields)) {
    const type=fieldType(mode==='copycat'?'form':'building',path),domain=nativeChoiceDomain(type,path)
    for(const value of settings.choices || [])hasDrawing(choiceIcon(domain,value))
    visit(type,path)
  }
  assert.ok(enums.size>=11)
  for(const [domain,values] of Object.entries(fixedChoices)) for(const value of Object.keys(values))hasDrawing(choiceIcon(domain,value))
  for(const value of [0,1,2,3]) hasDrawing(choiceIcon('angle',value))
  for(const value of ['SRP','SPR','RSP','RPS','PRS','PSR'])hasDrawing(choiceIcon('order',value))
  for(const {p,q} of model.roofSlopes)hasDrawing(choiceIcon('slope',`${p}:${q}`))
})
test('dynamic choices retain primitive values and get deterministic distinguishable details',()=>{
  const options=[{value:'id-b',label:'Meine Form',type:'COPYCAT'},{value:'id-a',label:'Meine Form',type:'COPYCAT'}]
  const first=decorateChoices('node',options),reversed=decorateChoices('node',[...options].reverse())
  assert.notEqual(first[0].icon.detail,first[1].icon.detail)
  assert.deepEqual(first.map(o=>[o.value,o.icon]),reversed.reverse().map(o=>[o.value,o.icon]))
  assert.equal(decorateChoices('input',[{value:0,label:'A'},{value:1,label:'B'}])[0].value,0)
  assert.equal(choiceIcon('version','arbitrary-id',{version:137}).detail,137)
  assert.equal(choiceIcon('state','true',{property:'waterlogged'}).kind,'check')
  assert.equal(new Set(['north','east','south','west','up','down'].map(value=>JSON.stringify(choiceIcon('state',value,{property:'facing'})))).size,6)
  hasDrawing(choiceIcon('state','CUSTOM_VALUE',{property:'mod_property'}))
  for(const value of ['minecraft:oak_planks','minecraft:sand','minecraft:stone','minecraft:red_carpet','other:custom_material'])hasDrawing(choiceIcon('material',value))
  assert.throws(()=>decorateChoices('operation',[{value:'NEW_OPERATION',label:'Neu'}]),/Missing dropdown icon/)
})
test('keyboard navigation skips disabled options, preserves bounds and supports German typeahead',()=>{
  const options=[{label:'Platzhalter',disabled:true},{label:'Äquivalenz'},{label:'Dach',disabled:true},{label:'Drehung'},{label:'Decke'}]
  assert.equal(enabledIndex(options,0,1),1)
  assert.equal(nextIndex(options,1,1),3)
  assert.equal(nextIndex(options,4,1),4)
  assert.equal(nextIndex(options,1,-1),1)
  assert.equal(searchIndex(options,'Dr'),3)
  assert.equal(searchIndex(options,'ä'),1)
  assert.equal(searchIndex(options,'D',4),4)
})
test('popup placement stays in the viewport and flips above a low anchor',()=>{
  for(const width of [320,390,820,1440]) {
    const position=popupPosition({left:width-50,top:700,bottom:730,width:100},{width,height:760},100)
    assert.ok(position.left>=8)
    assert.ok(position.left+position.width<=width-8)
    assert.ok(position.bottom>0)
    assert.ok(position.maxHeight<=320)
  }
})
test('all forty original selection sites use the shared icon component',()=>{
  const root=new URL('../src/',import.meta.url)
  const files=readdirSync(root).filter(name=>name.endsWith('.jsx') && name!=='IconSelect.jsx')
  const sources=files.map(name=>readFileSync(new URL(name,root),'utf8'))
  assert.ok(sources.every(source=>!/<select\b/.test(source)))
  assert.ok(sources.some(source=>source.includes('<IconSelect')))
})

test('every authoring family and inline node selector has complete icon metadata',()=>{
 for(const mode of ['form','building']) {
  for(const family of operatorFamilies(mode))for(const [kind]of family.operations)hasDrawing(operationIcon(kind))
  for(const kind of mode==='building'?model.building.filter(k=>k!=='RESULT'):model.form.map(n=>n.kind).filter(k=>k!=='OUTPUT'))for(const c of nodeControls(mode,nodePreview(mode,kind).node))for(const option of c.choices||[])hasDrawing(choiceIcon(c.domain,Array.isArray(option)?option[0]:option))
 }
})
