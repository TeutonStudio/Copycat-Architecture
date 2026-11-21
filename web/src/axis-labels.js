export const axisNames={X:'erste horizontale Achse',Y:'vertikale Achse',Z:'zweite horizontale Achse'}
export const axisLabel=axis=>`${axis} · ${axisNames[axis]}${axis==='X'?' · Ost/West':axis==='Z'?' · Süd/Nord':''}`
export const directionLabel=(axis,positive)=>`${positive?'+':'−'}${axis} · ${axisNames[axis]} ${axis==='Y'?(positive?'oben':'unten'):(positive?'rechts':'links')}${axis==='X'?` · ${positive?'Ost':'West'}`:axis==='Z'?` · ${positive?'Süd':'Nord'}`:''}`
export function regionLabel(key) {
 const parts=/^region:x([01ib])_y([01ib])_z([01ib])$/.exec(key)
 if(!parts)return key
 const directions=parts.slice(1).flatMap((state,i)=>state==='i'?[]:state==='b'?[`${'XYZ'[i]} · beide Gegenseiten`]:[directionLabel('XYZ'[i],state==='1')])
 const boundaries=parts.slice(1).filter(state=>state==='0'||state==='1').length
 return `${boundaries===3?'Ecke':boundaries===2?'Kante':boundaries===1?'Fläche':'Inneres'}${directions.length?' · '+directions.join(' / '):''}`
}
