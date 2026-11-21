// React Flow selection changes are the single source of truth, separate from layout.
export function selectNodes(previous,changes) {
 const next=new Set(previous)
 for(const change of changes) if(change.type==='select') {if(change.selected)next.add(change.id);else next.delete(change.id)}
 const result=[...next]
 return result.length===previous.length && result.every(id=>previous.includes(id))?previous:result
}

export function clickNodes(previous,id,multi=false) {return multi?previous.includes(id)?previous.filter(value=>value!==id):[...previous,id]:[id]}
