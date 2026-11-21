import {configureGraphProgram} from './generated/engine.mjs'
import {names} from './node-names.js'
import {cardTypeLabel} from './card-types.js'
import {model,editNode,editInputs,promoteNumbers} from './model.js'
import {reorderDraft} from './input-list.js'
import {axisLabel} from './axis-labels.js'
export const orders=['SRP','SPR','RSP','RPS','PRS','PSR']
const orderNames={S:'Spiegeln',R:'Rotieren',P:'Positionieren'}
export const get=(n,path)=>path.split('.').reduce((v,key)=>v?.[key],n)
const set=(n,path,value)=>{const [key,...rest]=path.split('.');return {...n,[key]:rest.length?set(n[key]||{},rest.join('.'),value):value}}
export function nodeControls(mode,node) {
 const domains={transformationOrder:'order',areaMode:'AreaMode',roofInputMode:'RoofInputMode',dynamicAxes:'axes',booleanValue:'boolean',operation:'operation',storeyMode:'storeyMode','roomConfig.mode':'RoomMode',producerKind:'node',resultMode:'GraphResultMode',producerOutput:'axis'}
 const result=[],pick=(path,label,value,choices)=>result.push({path,label,value,choices,domain:domains[path] || 'type'}),number=(path,label,value,min,max)=>result.push({path,label,value,min,max})
 if(node.resultType) {
  pick('resultType','Ausgabetyp',node.resultType,model.cardTypes.filter(t=>t!=='VALUE').map(t=>[t,cardTypeLabel(t)]))
  pick('resultMode','Erzeugungsmodus',node.kind==='RESULT'?node.resultMode||'CONSTRUCT':'VALUE',[['CONSTRUCT','Direkt erzeugen'],['VALUE','Wert übernehmen']])
  if(node.kind==='RESULT'&&node.resultMode!=='VALUE') {
   pick('producerKind','Erzeugungsart',node.producerKind||'COPYCAT',(model.resultConstructors[node.resultType]||[]).map(k=>[k,names[k]||k]))
   if(/^POSITION_[XYZ]$/.test(node.producerKind))pick('producerOutput','Ergebnisausgang',node.producerOutput||'X',['X','Y','Z'])
   const effective={...node,kind:node.producerKind,resultType:null}
   result.push(...nodeControls(mode,effective))
  }
  return result
 }
 if(node.kind==='GROUP_INPUT')pick('valueType','Datentyp',node.valueType||'COUNT',model.cardTypes.map(t=>[t,cardTypeLabel(t)]))
 if(['INT','COUNT'].includes(node.kind)) {pick('$numberType','Zahlentyp',node.kind,[['COUNT','Anzahl'],['INT','Ganzzahl']]);number('countValue','Wert',node.countValue??(node.kind==='INT'&&mode==='building'?4:0),node.kind==='COUNT'?0:-2147483648,2147483647)}
 if(node.kind==='TRANSFORMATION')pick('transformationOrder','Reihenfolge',node.transformationOrder||'SRP',orders.map(v=>[v,`${v} · ${[...v].map(l=>orderNames[l]).join(' → ')}`]))

 if(node.kind==='AREA') {
  pick('areaMode','Flächenmodus',node.areaMode||'DRAW',[['SQUARE','Quadrat'],['RECTANGLE','Rechteck'],['DRAW','Zeichnen']])
  if(node.areaMode==='SQUARE'||node.areaMode==='RECTANGLE')number('length',node.areaMode==='SQUARE'?'Seitenlänge':axisLabel('X'),node.length,1,128)
  if(node.areaMode==='RECTANGLE')number('height',axisLabel('Z'),node.height,1,128)
 }
 if(node.kind==='ROOF' && model.cardTypes.includes('ROOF_TEMPLATE'))pick('roofInputMode','Dachaufbau',node.roofInputMode||'LEGACY',[...(node.roofInputMode?[]:[['LEGACY','Bisheriger Dachaufbau']]),['TEMPLATE','Fläche, Dachvorlage'],['SEGMENTS','Fläche, Segmente …']])
 if(node.kind==='SEGMENT' && node.id!=='empty_wall') {
  const axes=(node.dynamicAxes||[]).join('')
  pick('dynamicAxes','Dynamische Achsen',axes,[['','Feste Maße'],...['X','Y','Z','XY','XZ','YZ','XYZ'].map(v=>[v,[...v].join(' / ')])])
 }
 if(node.kind==='ROOM')pick('roomConfig.mode','Raumaufbau',node.roomConfig?.mode||'FOOTPRINT',[['FOOTPRINT','Fläche und Wände'],['SEGMENTS','Segmente']])
 if(node.kind==='STOREY')pick('storeyMode','Geschossaufbau',node.storeyMode||'rooms',[['rooms','Räume'],['walls','Wände']])
 if(node.kind==='BOOLEAN')pick('booleanValue','Booleanwert',node.booleanValue===true?'true':'false',[['false','Falsch'],['true','Wahr']])
 if(node.kind==='BOOLEAN_LOGIC')pick('operation','Operation',node.operation||'AND',[['AND','UND'],['OR','ODER'],['XOR','XOR'],['NOT','NICHT']])
 if(node.kind==='INT_COMPARE')pick('operation','Vergleich',node.operation||'EQUAL',[['EQUAL','='],['NOT_EQUAL','≠'],['LESS','<'],['LESS_EQUAL','≤'],['GREATER','>'],['GREATER_EQUAL','≥'],['DIVISIBLE','Teilbar · A ÷ B ∈ ℤ']])
 const operation=model.operators?.[node.kind]?.operations[node.operation || model.operators[node.kind].default]
 const limits=operation || (mode==='building'?model.buildingInputs[node.kind]:model.form.find(n=>n.kind===node.kind))
 if(limits&&limits.max>limits.min && !node.resultType && !['SEGMENT','ROOF','BOOLEAN_LOGIC','POSITION'].includes(node.kind))number('$inputCount','Eingänge',node.inputs.length,limits.min,limits.max)
 return result
}
export function configureNode(node,path,value) {
 if(path==='$inputOrder')return reorderDraft(node,value)
 if(path==='$numberType')return {...node,kind:value,countValue:node.countValue??0}
 if(path==='$inputCount')return {...node,inputs:Array.from({length:Number(value)},(_,i)=>node.inputs[i]||'')}
 if(path==='roofInputMode'&&value==='LEGACY')value=null
 if(path==='dynamicAxes')value=[...value]
 if(path==='booleanValue')value=value==='true'
 let next=set(node,path,value)
 if(path==='operation'&&node.kind==='BOOLEAN_LOGIC')next.inputs=Array.from({length:value==='NOT'?1:2},(_,i)=>node.inputs[i]||'')
 return next
}
export function configureDocument(mode,document,id,path,value) {
 if(path==='$numberType')document=promoteNumbers(mode,document)
 let node=document.graph.nodes.find(n=>n.id===id)
 if((['resultMode','resultType','producerKind','producerOutput','valueType'].includes(path)||path==='operation'&&node.kind==='RESULT'&&model.operators[node.producerKind])) {
  if(node.kind!=='RESULT'&&node.resultType)document={...document,graph:{...document.graph,nodes:document.graph.nodes.map(n=>n.id===id?{...n,kind:'RESULT',resultMode:'VALUE',resultSource:n.inputs[0]||'',producerKind:'COPYCAT',inputs:[],levels:{},roof:''}:n)}}
  return {...document,graph:configureGraphProgram(document,id,path,String(value))}
 }
 if(path==='$inputOrder')return editInputs(mode,document,id,value)
 if(path==='$inputCount') {let next=document;while(next.graph.nodes.find(n=>n.id===id).inputs.length!==Number(value)) {const count=next.graph.nodes.find(n=>n.id===id).inputs.length;next=editInputs(mode,next,id,Array.from({length:count+(count<Number(value)?1:-1)},(_,i)=>i<count?i:-1))};return next}
 const next=configureNode(node.kind==='RESULT'?{...node,kind:node.producerKind}:node,path,value)
 return editNode(mode,document,node.kind==='RESULT'?{...next,kind:'RESULT',producerKind:next.kind}:next)
}
