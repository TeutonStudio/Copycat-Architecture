// Pure navigation functions, shared by the combobox and its behavioral tests.
export function enabledIndex(options,start,direction) {
  for(let index=start;index>=0 && index<options.length;index+=direction) if(!options[index].disabled) return index
  return -1
}
export function nextIndex(options,current,delta) {
  const found=enabledIndex(options,current+delta,Math.sign(delta))
  return found<0?current:found
}
export function searchIndex(options,query,start=0) {
  const text=query.toLocaleLowerCase('de').normalize('NFKD')
  for(let offset=0;offset<options.length;offset++) {
    const index=(start+offset)%options.length
    if(!options[index].disabled && options[index].label.toLocaleLowerCase('de').normalize('NFKD').startsWith(text)) return index
  }
  return -1
}
export function popupPosition(anchor,viewport,optionCount) {
  const gap=4,margin=8,width=Math.min(Math.max(anchor.width,240),viewport.width-2*margin)
  const below=viewport.height-anchor.bottom-margin-gap,above=anchor.top-margin-gap
  const up=below<Math.min(240,optionCount*36+8) && above>below
  const maxHeight=Math.max(36,Math.min(320,up?above:below))
  return {left:Math.max(margin,Math.min(anchor.left,viewport.width-width-margin)),width,maxHeight,
    ...(up?{bottom:viewport.height-anchor.top+gap}:{top:anchor.bottom+gap})}
}
