export function acceptsValue(port,entry) {
 const actual=entry?.outputType
 return actual!=null && actual!=='VALUE' && (port?.types || []).some(expected=>actual===expected || actual==='COUNT'&&expected==='INT' || actual==='ROOF'&&expected==='WALL')
}
