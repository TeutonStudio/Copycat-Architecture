import { model, inspect, replaceNodes } from './model.js'
import { authoringBuildingNode,newNode } from './generated/engine.mjs'
import { semanticType } from './port-styles.js'
export const families=model.operators
export const symbols={VALUE:'≔',UNION:'∪',INTERSECTION:'∩',DIFFERENCE:'∖',SYMMETRIC_DIFFERENCE:'△',INVERT:'∁',ROTATE:'↻',MIRROR:'↔',TRANSLATE:'↦',ADD:'+',SUBTRACT:'−',MULTIPLY:'×',SCALE:'×',DIVIDE:'÷',REMAINDER:'mod',MIN:'min',MAX:'max',ABS:'| |',EQUAL:'=',NOT_EQUAL:'≠',LESS:'<',LESS_EQUAL:'≤',GREATER:'>',GREATER_EQUAL:'≥',DIVISIBLE:'∣',AND:'∧',OR:'∨',XOR:'⊻',NOT:'¬',IMPLIES:'⇒',EQUIVALENT:'⇔'}
export function operatorIdentity(node) {
  if(families[node.kind]) return {family:node.kind,operation:node.operation || families[node.kind].default}
  const k=node.kind
  if(['UNION','INTERSECTION','DIFFERENCE','SYMMETRIC_DIFFERENCE','ROTATE','MIRROR','TRANSLATE'].includes(k)) return {family:'COPYCAT_OPERATOR',operation:k}
  if(k==='COPYCAT_POSITION') return {family:'COPYCAT_OPERATOR',operation:'TRANSLATE'}
  if(/^(COPYCAT|SEGMENT)_(UNION|INTERSECTION|DIFFERENCE|SYMMETRIC_DIFFERENCE|ROTATE|MIRROR)$/.test(k)) return {family:k.startsWith('COPYCAT')?'COPYCAT_OPERATOR':'SEGMENT_OPERATOR',operation:k.replace(/^(COPYCAT|SEGMENT)_/,'')}
  if(k==='INT' || /^INT_/.test(k)) return {family:'NUMBER_OPERATOR',operation:k==='INT'?'VALUE':k.slice(4)}
  if(k==='POSITION' || /^POSITION_(ADD|SUBTRACT|MULTIPLY|DIVIDE|MIN|MAX)$/.test(k)) return {family:'POSITION_OPERATOR',operation:k==='POSITION'?'VALUE':k.slice(9)}
  return null
}
export function caption(node) { const o=operatorIdentity(node);return o?`${symbols[o.operation]} ${families[o.family].operations[o.operation].label}`:null }
export function draftNode(mode,kind) {const node=newNode(mode==='building',kind,'preview',0,0,true);return mode==='building'?authoringBuildingNode(node):node}
export function signature(node) {const o=operatorIdentity(node);return o?families[o.family].operations[o.operation]:null}
export function switchDraft(mode,node,operation,entries=[]) {
  const {family}=operatorIdentity(node),spec=families[family].operations[operation]
  const before=entries.find(e=>e.node.id===node.id)
  const byId=new Map(entries.map(e=>[e.node.id,e]))
  const old=(before?.ports || []).filter(p=>p.source).map(p=>({...p,type:semanticType(byId.get(p.source)?.outputType)}))
  const output=families[family].output
  const geometry=semanticType(output)
  const count=spec.min===spec.max?spec.min:Math.max(spec.min,Math.min(spec.max,old.filter(p=>p.type===geometry).length))
  const moved=new Map(),inputs=Array.from({length:count},(_,i)=>{
    const type=semanticType(spec.types[Math.min(i,spec.types.length-1)]==='geometry'?output:spec.types[Math.min(i,spec.types.length-1)])
    const index=old.findIndex(p=>p.type===type)
    if(index<0)return ''
    const [p]=old.splice(index,1);moved.set(p.id,mode==='building'?`in:${i}`:`${i}`);return p.source
  })
  const next={...draftNode(mode,family),...node,kind:family,operation,inputs,numberValue:node.numberValue ?? node.countValue ?? 0,translation:node.translation || {x:0,y:0,z:0}}
  for(const key of ['countValue','rotation','mirroring','positionValue','rotations','mirrors','mirrorPositionsOnly'])delete next[key]
  if(mode==='building' && family==='SEGMENT_OPERATOR') {
    const values=node.operandOffsets ?? (node.kind==='SEGMENT_UNION'?node.offsets:[]) ?? []
    next.operandOffsets=['UNION','INTERSECTION','DIFFERENCE','SYMMETRIC_DIFFERENCE'].includes(operation)
      ? inputs.map((_,i)=>values[Number([...moved].find(([,target])=>target===`in:${i}`)?.[0].replace('in:',''))] ?? {x:0,y:0,z:0}) : null
    next.offsets=[]
  }
  // Family switches preserve node identity, placement, names and material settings.
  if(mode==='form')next.inputGroups=inputs.map((_,i)=>node.inputGroups?.[Number([...moved].find(([,v])=>v===`${i}`)?.[0])] || '')
  return {node:next,moved}
}
export function changeOperator(mode,document,id,operation) {
  const entries=inspect(mode,document),original=document.graph.nodes.find(n=>n.id===id)
  const {node,moved}=switchDraft(mode,original,operation,entries)
  const groups=document.graph.nodeGroups?.map(g=>{
    const member=g.nodes.indexOf(id)
    if(member<0 || !g.graphInterface)return g
    return {...g,graphInterface:{...g.graphInterface,inputs:g.graphInterface.inputs.map(p=>({...p,targets:p.targets.flatMap(t=>t.member!==member?[t]:moved.has(t.port)?[{...t,port:moved.get(t.port)}]:[])}))}}
  })
  const candidate={...document,graph:{...document.graph,...(groups?{nodeGroups:groups}:{}),schema:Math.max(document.graph.schema,mode==='building'?9:6)}}
  return replaceNodes(candidate,document.graph.nodes.map(n=>n.id===id?node:n))
}
export function catalogKinds(kinds) {
 const seen=new Set()
 return kinds.flatMap(kind=>{const o=operatorIdentity({kind}),key=o?.family || kind;if(seen.has(key))return [];seen.add(key);return [key]})
}
export function matchesRule(entry,rule) {
 if(rule.children) {const matches=rule.children.map(r=>matchesRule(entry,r));return rule.op==='OR'?matches.some(Boolean):matches.every(Boolean)}
 const wanted=rule.type
 const exact=type=>type==='shape'?'COPYCAT':type==='position'?'POSITION':type==='rotation'?'ROTATION':type==='mirroring'?'MIRRORING':type
 return (rule.direction==='output'?[entry.outputType]:entry.ports.flatMap(p=>p.types || [])).some(type=>wanted.startsWith('type:')?exact(type)===wanted.slice(5):semanticType(type)===wanted)
}
export function filterCatalog(entries,{query='',rule=null,sort='name',descending=false},label) {
 const q=query.trim().toLocaleLowerCase('de')
 return entries.filter(e=>(!q || `${label(e.node)} ${e.description || ''} ${signature(e.node)?.description || ''} ${Object.values(families[operatorIdentity(e.node)?.family]?.operations || {}).map(o=>o.label+' '+o.description).join(' ')}`.toLocaleLowerCase('de').includes(q)) && (!rule || matchesRule(e,rule)))
 .sort((a,b)=>{let delta=0;if(sort==='inputs')delta=a.ports.length-b.ports.length;else if(sort==='output')delta=(a.outputType || '').localeCompare(b.outputType || '','de');else if(sort==='family')delta=(operatorIdentity(a.node)?.family || a.node.kind).localeCompare(operatorIdentity(b.node)?.family || b.node.kind,'de');return (delta || label(a.node).localeCompare(label(b.node),'de'))*(descending?-1:1)})
}
export function insertionPosition(screen,grip,flow) {const point=flow.screenToFlowPosition(screen);return {x:Math.round(point.x-grip.x),y:Math.round(point.y-grip.y)}}
