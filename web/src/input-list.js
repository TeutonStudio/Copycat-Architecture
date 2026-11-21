import {model} from './model.js'
// This is a capability of the operation, never a user override of its mathematics.
export function inputListEnabled(properties) {
  return properties?.commutativeAssociative === true
}
export function inputOrder(count, action, index, delta) {
  const order=Array.from({length:count},(_,i)=>i)
  if(action==='add') return [...order,-1]
  if(!Number.isInteger(index) || index<0 || index>=count) throw new Error('Ungültiger Eingang.')
  if(action==='remove') return order.filter(i=>i!==index)
  const target=index+delta
  if(action!=='move' || ![-1,1].includes(delta) || target<0 || target>=count) throw new Error('Ungültige Eingangsreihenfolge.')
  ;[order[index],order[target]]=[order[target],order[index]]
  return order
}

const orderedBinary=new Set(['DIFFERENCE','SYMMETRIC_DIFFERENCE','COPYCAT_DIFFERENCE','COPYCAT_SYMMETRIC_DIFFERENCE','SEGMENT_DIFFERENCE','INT_SUBTRACT','INT_DIVIDE','INT_REMAINDER','POSITION_SUBTRACT','POSITION_DIVIDE','INT_COMPARE'])
export const inputsReorderable=(node,properties)=>inputListEnabled(properties)||orderedBinary.has(node.kind)||!!model.operators?.[node.kind]?.operations[node.operation || model.operators[node.kind].default]?.types.every((type,_,types)=>type===types[0])
export function moveInput(count,from,to) {
 if(!Number.isInteger(from)||!Number.isInteger(to)||from<0||to<0||from>=count||to>=count)throw new Error('Ungültige Eingangsreihenfolge.')
 const order=Array.from({length:count},(_,i)=>i);order.splice(to,0,order.splice(from,1)[0]);return order
}
export function reorderDraft(node,order) {
 const next={...node,inputs:order.map(i=>node.inputs[i]||'')}
 for(const [key,fallback]of [['inputGroups',''],['offsets',{x:0,y:0,z:0}],['operandOffsets',{x:0,y:0,z:0}],['transforms',null]])if(node[key]?.length)next[key]=order.map(i=>node[key][i]??fallback)
 return next
}
