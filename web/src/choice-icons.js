// Presentation metadata only. Never add these descriptors to a graph document.
export const iconPaths = {
 count:['M5 5v14M10 5v14M15 5v14M3 16 18 7M21 5v14'],
 roofTemplate:['M2 2h20v20H2Z','m5 16 7-10 7 10M12 6v12'],
  cube:['M12 2 22 7v10l-10 5L2 17V7Z','m2 7 10 5 10-5M12 12v10'],
  number:['M8 3 6 21M17 3l-2 18M3 9h18M2 15h18'],
  boolean:['M3 3h18v18H3Z','m5 9 2 2 4-4m1 8 5 5m0-5-5 5'],
  position:['M5 19V3m0 16h16M5 19l10-10M2 6l3-3 3 3m10 10 3 3-3 3M11 9h4v4'],
  rotate:['M20 9a8 8 0 1 0 0 7','M20 3v6h-6'],
  mirror:['M12 2v20M3 6l6 3v6l-6 3Zm18 0-6 3v6l6 3Z'],
  translate:['M2 9h7v10H2ZM12 10h10m-4-4 4 4-4 4'],
  transform:['M12 2v6M2 14l4-4 4 4M6 10v12M11 18h11m-4-4 4 4-4 4M14 4a5 5 0 0 1 7 5m-4 0h4V5'],
  add:['M12 4v16M4 12h16'], subtract:['M4 12h16'],
  multiply:['m5 5 14 14M19 5 5 19'], divide:['M4 12h16','M12 4v1m0 14v1'],
  scale:['M7 9v10h10M3 7V3h4M3 3l6 6M17 3h4v4m0-4-6 6M17 21h4v-4m0 4-6-6'],
  remainder:['M3 9h15M10 3v1m0 10v1','M19 18v3m-1-1h3'],
  min:['M4 5v14h16M7 7h3m3 4h3m1 4h3','M5 19h6'],
  max:['M4 5v14h16M7 15h3m3-4h3m1-4h3','M16 5h6'],
  abs:['M4 3v18M20 3v18M8 16l4-8 4 8M10 13h4'],
  equal:['M4 8h16M4 16h16'], notEqual:['M4 8h16M4 16h16M18 3 6 21'],
  less:['m18 4-12 8 12 8'], lessEqual:['m18 3-12 7 12 7M5 21h15'],
  greater:['m6 4 12 8-12 8'], greaterEqual:['m6 3 12 7-12 7M4 21h15'],
  divisible:['M12 3v18M4 7v10M20 7v10'],
  and:['m4 20 8-16 8 16'], or:['M4 4 12 20 20 4'],
  xor:['M4 3 12 18 20 3M4 22h16'], not:['M3 8h17v10'],
  implies:['M3 9h15M3 15h15m-4-12 8 9-8 9'],
  equivalent:['M6 8h12M6 16h12M8 3 2 12l6 9M16 3l6 9-6 9'],
  check:['m4 12 5 5L20 5'], cross:['m5 5 14 14M19 5 5 19'],
  segment:['M2 5h20v14H2ZM2 12h20M8 5v7m8 0v7'],
  area:['M3 3h18v18H3Z'], rectangle:['M2 6h20v12H2Z'],
  draw:['M3 3h12v12H3ZM3 9h12M9 3v12m3-3 9 3-4 2-2 4Z'],
  paint:['m14 3 7 7-9 9-7-7ZM5 12l-3 9 9-3'],
  room:['M2 2h20v20H2ZM6 6h12v12h-5m-7 0V6'],
  roomGeometry:['M2 3h12v7h8v11H2ZM5 6h6v7h8v5H5Z'],
  storey:['m2 6 10-4 10 4-10 4Zm0 6 10 4 10-4M2 18l10 4 10-4'],
  building:['M4 22V2h16v20M8 6h2m4 0h2M8 11h2m4 0h2M8 16h2m4 0h2M10 22v-3h4v3'],
  floor:['M3 3v18h18M3 17h18v4H3'], ceiling:['M3 21V3h18M3 3h18v4H3'],
  roof:['m2 16 10-12 10 12M5 13v8h14v-8'],
  pent:['M2 16 22 5M4 15v6h16V6'], hip:['m2 16 6-11h8l6 11Zm6-11 4 11 4-11'],
  mansard:['m2 19 4-11 6-4 6 4 4 11M6 8l6 4 6-4'],
  crossRoof:['M2 18 7 9l5 5 5-9 5 13M2 18h20'],
  roofLevel:['m2 12 10-9 10 9M3 17h18M3 22h18'],
  roofLayout:['m2 8 5-5 5 5M12 8l5-5 5 5M2 20l5-5 5 5m0 0 5-5 5 5'],
  segmentLayout:['M2 3h20v18H2ZM2 9h20M9 3v18M15 9v12'],
  roomLayout:['M2 2h20v20H2ZM2 12h8V2m0 10h12m-7 0v10'],
  bay:['M3 21V8h4V3h10v5h4v13M7 8v13m10-13v13'],
  dormer:['M2 20 8 4h8l6 16M7 20v-8l5-5 5 5v8M10 20v-5h4v5'],
  stairs:['M3 21v-5h6v-5h6V5h6M3 3h18v18'],
  lift:['M4 2h16v20H4ZM8 12l4-5 4 5m-8 2 4 5 4-5'],
  service:['M3 2h18v20H3ZM8 2v8h5v12M17 2v14h-4'],
  garden:['M2 19h20M6 19V7m0 6-4-4m4 2 4-4M15 19V9m0 6 5-5'],
  path:['M2 21 8 3h8l6 18M12 5v3m0 4v3m0 4v2'],
  material:['M3 3h18v18H3ZM3 15 15 3M3 21 21 3M9 21l12-12M15 21l6-6'],
  structure:['M3 3h6v6H3Zm12 0h6v6h-6ZM9 15h6v6H9ZM6 9v3h12V9m-6 3v3'],
  frame:['M2 2h20v20H2ZM6 6h12v12H6Z'], glass:['M3 3h18v18H3ZM6 14l8-8m-4 12 8-8'],
  gable:['m2 21 10-18 10 18ZM7 21v-9m5-9v18m5-9v9'], trim:['M2 5h20v4H2Zm0 4 4 6h12l4-6M6 15v4h12v-4'],
  separated:['M2 4h7v7H2Zm13 9h7v7h-7M15 4h7v3h-7M2 17h7v3H2'],
  grouped:['M3 3h18v18H3ZM6 6h5v5H6Zm7 7h5v5h-5'],
  horizontal:['M2 7h6v10H2Zm7 0h6v10H9Zm7 0h6v10h-6'],
  vertical:['M7 2h10v6H7Zm0 7h10v6H7Zm0 7h10v6H7'],
  depth:['M3 9V3h12v6M6 12V6h12v6M9 9h12v12H9Z'],
  overlay:['M3 9h18v6H3ZM9 3h6v18H9Z'], adjacent:['M2 2h9v9H2Zm11 0h9v9h-9ZM2 13h9v9H2Zm11 0h9v9h-9Z'],
  open:['M3 4h6v16H3Zm12 0h6v16h-6M9 12h6'],
  empty:['M3 3h18v18H3ZM3 21 21 3'], auto:['M12 2v4m0 12v4M2 12h4m12 0h4M5 5l3 3m8 8 3 3M5 19l3-3m8-8 3-3','M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8'],
  explicit:['M3 3h18v18H3Zm3 9 4 4 8-8'], opening:['M3 21V3h18v18M7 21V7h10v14'],
  decoration:['m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z'],
  straight:['M5 21V3h14M9 7h6M9 11h6M9 15h6M9 19h6'],
  stairL:['M3 21V3h18v8H11v10ZM7 7h10M7 11h4m-4 4h4'],
  stairU:['M2 21V3h20v18h-7V10H9v11ZM5 7h14M5 13h4m6 0h4'],
  height:['M5 2v20M2 5l3-3 3 3M2 19l3 3 3-3M12 5h10M12 19h10'],
  slope:['M2 21h20L2 3ZM3 15a6 6 0 0 1 6 6'],
  input:['M2 12h13m-4-4 4 4-4 4M19 4h3v16h-3'],
  output:['M2 4h3v16H2M9 12h13m-4-4 4 4-4 4'],
  inputs:['M2 5h8m-3-3 3 3-3 3M2 18h8m-3-3 3 3-3 3M15 2h7v20h-7'],
  name:['m2 21 6-18 6 18M4 15h8M17 5h5l-5 14h5'],
  family:['M2 2h8v8H2Zm12 0h8v8h-8ZM6 10v4h12v-4m-6 4v8'],
  category:['M2 7V3h8l3 4h9v14H2Z'], none:['M3 5h18M3 12h18M3 19h18'],
  ascending:['M4 20V4m-3 3 3-3 3 3M11 6h4m-4 6h7m-7 6h11'],
  descending:['M4 4v16m-3-3 3 3 3-3M11 6h11m-11 6h7m-7 6h4'],
  graph:['M2 3h6v6H2Zm14 12h6v6h-6ZM8 6h9v9M5 9v9h11'],
  group:['M3 2H1v20h2M21 2h2v20h-2M6 6h5v5H6Zm7 7h5v5h-5M11 8h5v5'],
  collection:['M2 8h15v14H2ZM6 5h14v14M10 2h13v14'],
  version:['M3 6h13v16H3ZM7 2h13v16M7 11h5m-5 4h5'],
  draft:['M3 2h13l5 5v15H3ZM16 2v6h5M7 17l2-5 5-3 2 3-5 3Z'],
  select:['M3 3h18v18H3ZM7 9l5 6 5-6'],
  first:['M3 3h18v5H3ZM3 12h18M3 18h18'],
  inherit:['M3 3h7v7H3Zm11 11h7v7h-7M6 10v7h8m-3-3 3 3-3 3'],
  boundary:['M3 3h18v18H3ZM7 7v10h10V7Z'],
  wood:['M3 3h18v18H3ZM7 3c-4 8 4 10 0 18M13 3c-4 8 4 10 0 18M18 3c-4 8 4 10 0 18'],
  sand:['M3 3h18v18H3Z','M7 7h.1M15 6h.1M12 11h.1M7 16h.1M17 17h.1'],
  carpet:['m2 15 10-9 10 9-10 7ZM5 18v3m5-1v3m5-3v3m5-6v3'],
  fence:['M4 2h4v20H4Zm12 0h4v20h-4M2 7h20M2 16h20'],
  grass:['M2 13h20v9H2ZM6 13V5m0 4L2 6m4 1 4-5M15 13V6m0 4 5-5'],
  property:['M3 6h18M3 18h18M7 3v6M16 15v6'],
  cutout:['M3 3h18v8H11v10H3Z'],
  basement:['M3 12h18M6 12V2h12v10M6 16v6h12v-6M9 5h2m3 0h1M9 19h6'],
  flatRoof:['M2 9h20M4 9v12h16V9M7 5h10'],
  drawRectangle:['M2 6h18v14H2ZM17 3l4 4-4 4M5 17l-4 4m0-4v4h4'],
  drawSquare:['M4 4h16v16H4ZM17 1l4 4-4 4M7 17l-4 4m0-4v4h4'],
  ridge:['m2 19 10-15 10 15M12 4v15M9 3h6'],
  eave:['m2 16 10-12 10 12M2 16h20M5 20h14'],
  roofEdge:['m2 19 10-15 10 15M2 19l10-15M4 21 14 6'],
  valley:['M2 5 12 19 22 5M12 19V9'],
  roofCorner:['M2 19 9 4l13 15ZM9 4l4 15'],
  roofCap:['m2 19 10-15 10 15M8 9h8v5H8Z'],
  roofPeak:['m2 20 10-16 10 16M9 4h6M12 1v6'],
  column:['M6 3h12v18H6ZM6 8h12M6 16h12M3 12h18m-3-3 3 3-3 3'],
  lBuilding:['M3 2h8v11h10v9H3ZM6 5h2m-2 4h2m-2 4h2m-2 5h2m4 0h2m3 0h2'],
  rotatedDifference:['M2 9h12v5H9v8H2ZM12 3a8 8 0 0 1 9 9m-4 0h4V8'],
}

export const operationIcons = {
  UNION:'union', INTERSECTION:'intersection', DIFFERENCE:'difference', SYMMETRIC_DIFFERENCE:'symmetricDifference',
  INVERT:'invert', ROTATE:'rotate', MIRROR:'mirror', TRANSLATE:'translate', TRANSFORM:'transform', VALUE:'number',
  ADD:'add', SUBTRACT:'subtract', MULTIPLY:'multiply', DIVIDE:'divide', SCALE:'scale', REMAINDER:'remainder',
  MIN:'min', MAX:'max', ABS:'abs', EQUAL:'equal', NOT_EQUAL:'notEqual', LESS:'less', LESS_EQUAL:'lessEqual',
  GREATER:'greater', GREATER_EQUAL:'greaterEqual', DIVISIBLE:'divisible', AND:'and', OR:'or', XOR:'xor',
  NOT:'not', IMPLIES:'implies', EQUIVALENT:'equivalent',
}
export const typeIcons = {
 VALUE:'select',all:'collection',
  COPYCAT:'cube', COUNT:'count', ROOF_TEMPLATE:'roofTemplate', INT:'number', BOOLEAN:'boolean', POSITION:'position', ROTATION:'rotate', MIRRORING:'mirror', TRANSFORMATION:'transform',
  WALL:'segment', AREA:'area', ROOM:'room', STOREY:'storey', BUILDING:'building', FLOOR:'floor', CEILING:'ceiling',
  ROOF:'roof', ROOF_LEVEL:'roofLevel', ROOF_LAYOUT:'roofLayout', SEGMENT_LAYOUT:'segmentLayout', ROOM_LAYOUT:'roomLayout',
  ROOM_GEOMETRY:'roomGeometry', BAY_WINDOW:'bay', DORMER:'dormer', STAIRWELL:'stairs', ELEVATOR_SHAFT:'lift', SERVICE_SHAFT:'service',
  GARDEN:'garden', PATH:'path', material:'material', structure:'structure',
}
const aliases = {shape:'COPYCAT',segment:'WALL',room:'ROOM',storey:'STOREY',roof:'ROOF',position:'POSITION',rotation:'ROTATION',mirroring:'MIRRORING',transformation:'TRANSFORMATION'}
export const fixedChoices = {
  GraphResultMode:{CONSTRUCT:'cube',VALUE:'input'},
  RoofInputMode:{LEGACY:'roof',TEMPLATE:'roofLayout',SEGMENTS:'segmentLayout'},
  axes:{'':'cube',X:'horizontal',Y:'vertical',Z:'depth',XY:'area',XZ:'area',YZ:'area',XYZ:'cube'},
  MaterialMode:{SEPARATED:'separated',GROUPED:'grouped'},
  AreaMode:{SQUARE:'area',RECTANGLE:'rectangle',DRAW:'draw'},
  RoomMode:{FOOTPRINT:'room',SEGMENTS:'segmentLayout'},
  RoofForm:{FLAT:'flatRoof',PENT:'pent',GABLE:'roof',HIP:'hip',MANSARD:'mansard',CROSS:'crossRoof'},
  RoofMode:{HEIGHT:'height',ANGLE:'slope'}, StairLayout:{STRAIGHT:'straight',L:'stairL',U:'stairU'},
  WallPolicy:{AUTO:'auto',EXPLICIT:'explicit',OPEN:'open'},
  RoomContribution:{BOUNDARY:'boundary',OPENING:'opening',STRUCTURE:'structure',DECORATION:'decoration'},
  RotationSegmentMode:{OVERLAY:'overlay',ADJACENT:'adjacent'},
  areaTool:{PAINT:'paint',RECTANGLE:'drawRectangle',SQUARE:'drawSquare'},
  patternAxis:{horizontal:'horizontal',vertical:'vertical',depth:'depth',transform:'transform'},
  storeyMode:{rooms:'roomLayout',walls:'segmentLayout'},
  role:{wall:'segment',frame:'frame',glass:'glass',roof:'roof',gable:'gable',trim:'trim'},
  preview:{all:'building',walls:'segment',floor:'floor',ceiling:'ceiling',roof:'roof'},
  capability:{all:'collection',yes:'check',no:'cross',unknown:'property'},
  category:{all:'collection',sources:'cube',copycat:'cube',segments:'segment',building:'building',layouts:'area',values:'number',landscape:'garden'},
  sort:{category:'category',name:'name',output:'output',inputs:'inputs',family:'family'},
  grouping:{property:'property',none:'none',output:'output',inputs:'inputs',family:'family',category:'category'},
  direction:{asc:'ascending',desc:'descending',false:'ascending',true:'descending'}, filterDirection:{input:'input',output:'output'},
  fileType:{all:'collection',graphs:'graph',nodeGroups:'group',libraryForms:'cube',libraryModules:'segment',libraryGraphs:'graph'},
  roofVariant:{slope:'slope',eave:'eave',edge:'roofEdge',ridge:'ridge',hip:'hip',valley:'valley',corner:'roofCorner',cap:'roofCap'},
  boolean:{true:'check',false:'cross'},
  examples:{0:'cutout',1:'rotatedDifference',2:'material',3:'building',4:'column',5:'roofPeak',6:'eave',7:'lBuilding',8:'basement'},
}
const typeColors={COPYCAT:'#38bdf8',INT:'#a78bfa',BOOLEAN:'#f9a8d4',POSITION:'#fb923c',ROTATION:'#f472b6',MIRRORING:'#2dd4bf',TRANSFORMATION:'#f2c66d',WALL:'#f59e0b',AREA:'#84cc16',ROOM:'#22c55e',STOREY:'#e2e8f0',ROOF:'#c084fc'}
const descriptor=(kind,extra={})=>({kind,...extra})
export const shortDetail=value=>String(value ?? '').normalize('NFKC').split(/[^\p{L}\p{N}]+/u).filter(Boolean).map(s=>s[0]).join('').slice(0,2).toUpperCase() || '·'

export function operationIcon(value,family='') {
  if(/^POSITION_[XYZ]$/.test(value))return descriptor('axis',{value:value.slice(-1)})
  const generator={ROTATION_SEGMENT:'column',SERVICE_SHAFT_SEGMENT:'service',ELEVATOR_SHAFT_SEGMENT:'lift'}[value]
  if(generator)return descriptor(generator)
  const operation=String(value).replace(/^(COPYCAT|SEGMENT|INT|POSITION)_/,'')
  const key=operation==='POSITION'?'TRANSLATE':operation==='TO_COUNT'?'ABS':operation==='COUNT'||operation==='INT'?'VALUE':operation
  const type={COPYCAT_OPERATOR:'COPYCAT',SEGMENT_OPERATOR:'WALL',POSITION_OPERATOR:'POSITION',NUMBER_OPERATOR:'INT',RELATION_OPERATOR:'BOOLEAN',STATEMENT_OPERATOR:'BOOLEAN'}[family]
  const color=typeColors[type]
  if(key==='VALUE') return descriptor(family==='POSITION_OPERATOR'?'position':family==='STATEMENT_OPERATOR'?'boolean':'number',{color})
  return operationIcons[key]?descriptor(operationIcons[key],{color}):null
}
export function typeIcon(value) {
  const key=aliases[value] || value
  return typeIcons[key]?descriptor(typeIcons[key],{color:typeColors[key]}):null
}
export function nodeChoiceMeta(entries,value) {
  const entry=entries.find(entry=>entry.node.id===String(value))
  return entry?{type:entry.outputType,kind:entry.node.kind,operation:entry.node.operation,family:entry.node.kind}:{}
}
function nodeKindIcon(kind='') {
  const exact=typeIcon(kind)
  if(exact) return exact
  if(kind==='GROUP_INPUT')return descriptor('input')
  if(kind==='RESULT')return descriptor('output')
  if(kind==='GRAPH_REFERENCE')return descriptor('graph')
  if(kind==='CONDITIONAL')return descriptor('select')
  if(['INPUT','SAVED','REFERENCE'].includes(kind) || kind.startsWith('COPYCAT_')) return typeIcon('COPYCAT')
  if(kind.startsWith('SEGMENT_') || ['SEGMENT','PATTERN','ROTATION_SEGMENT'].includes(kind)) return typeIcon('WALL')
  if(kind.startsWith('INT_') || kind.startsWith('NUMBER_') || /^POSITION_[XYZ]$/.test(kind)) return typeIcon('INT')
  if(kind.startsWith('POSITION_')) return typeIcon('POSITION')
  if(/^(STATEMENT|RELATION)_/.test(kind)) return typeIcon('BOOLEAN')
  return null
}
export function nativeChoiceDomain(type,path='',context={}) {
  const key=path.split('.').at(-1)
  if(context.references?.[path]) return 'node'
  if(context.group && key==='type') return 'type'
  if(context.group && key==='member') return 'member'
  if(context.group && key==='port') return 'port'
  if(['quarterTurns','turn','spin'].includes(key)) return 'angle'
  if(key==='ridge') return 'ridge'
  if(key==='sourceIndex') return 'input'
  if(['block','groundBlock','fenceBlock','finish'].includes(key)) return 'material'
  if(key==='form') return 'form'
  if(['storey','fromStorey','toStorey'].includes(key)) return 'storey'
  if(key==='variant') return 'roofVariant'
  if(key==='roofType') return 'roofType'
  if(key==='operation')return 'operation'
  if(key==='role') return 'role'
  if(['patternAxis','storeyMode'].includes(key)) return key
  if(key==='source') return 'form'
  return type.replace(/\?$/,'')
}

export function choiceIcon(domain,value,meta={}) {
  const raw=String(value ?? '')
  if(meta.icon) return meta.icon
  if(meta.placeholder) return descriptor('select')
  if(domain==='operation') return operationIcon(value,meta.family)
  if(domain==='type') return typeIcon(raw.replace(/^type:/,''))
  if(domain==='filterLogic') return operationIcon(value)
  if(['RotationAxis','axis'].includes(domain)) return ['X','Y','Z','NORMAL'].includes(raw.toUpperCase())?descriptor('axis',{value:raw.toUpperCase()}):null
  if(domain==='CopycatMirrorPlane') return descriptor('mirror',{plane:raw})
  if(domain==='angle') return [0,1,2,3].includes(Number(value))?descriptor('angle',{value:Number(value)}):null
  if(domain==='order') return ['SRP','SPR','RSP','RPS','PRS','PSR'].includes(raw)?descriptor('order',{value:raw}):null
  if(domain==='view') return ['X','Y','Z'].includes(raw)?descriptor('view',{value:raw}):null
  if(domain==='ridge') return descriptor('axis',{value:Number(value)===0?'Z':'X'})
  if(domain==='slope') {
    let slope
    try{slope=JSON.parse(raw)}catch{slope=null}
    const [p,q]=slope && typeof slope==='object'?[slope.p,slope.q]:raw.split(':').map(Number)
    return p>0 && q>0?descriptor('slope',{ratio:[p,q]}):null
  }
  if(domain==='roofType') return choiceIcon('RoofForm',raw.toUpperCase())
  if(fixedChoices[domain]?.[raw]) return descriptor(fixedChoices[domain][raw])
  if(domain==='version') return descriptor(raw?'version':'draft',raw?{detail:meta.version ?? meta.label?.match(/^v(\d+)/)?.[1] ?? shortDetail(raw)}:{})
  if(domain==='group') {
    if(!raw) return descriptor({inherit:'inherit',first:'first',all:'grouped'}[meta.empty] || 'grouped')
    return descriptor('material',{detail:shortDetail(meta.label || raw)})
  }
  if(domain==='material') {
    if(!raw) return descriptor('empty')
    const kind=/carpet/.test(raw)?'carpet':/fence/.test(raw)?'fence':/planks|wood|log/.test(raw)?'wood':/sand|gravel|dirt/.test(raw)?'sand':/grass|moss/.test(raw)?'grass':'segment'
    return descriptor(kind,{detail:shortDetail(raw.split(':').at(-1)),identity:raw})
  }
  if(domain==='state') {
    const property=meta.property || ''
    if(raw==='true' || raw==='false') return choiceIcon('boolean',raw)
    if(property==='axis') return choiceIcon('axis',raw)
    if(['facing','horizontal_facing'].includes(property)) return ['up','down'].includes(raw)?descriptor('axis',{value:'Y',detail:raw==='up'?'+':'−'}):descriptor('compass',{value:raw})
    if(property==='half') return descriptor(raw==='top' || raw==='upper'?'ceiling':'floor')
    if(property==='rotation' && /^\d+$/.test(raw)) return descriptor('compass',{degrees:Number(raw)*22.5,detail:raw})
    if(['level','power','age','layers'].includes(property) && /^\d+$/.test(raw)) return descriptor('height',{detail:raw})
    return descriptor(/^-?\d+$/.test(raw)?'number':'property',{detail:raw.length<=3?raw:shortDetail(raw)})
  }
  if(['node','form','member','port','input','storey','wallSection','output'].includes(domain)) {
    if(!raw || raw==='-1') return descriptor(domain==='storey'?'auto':domain==='form'?'empty':'open')
    if(domain==='form' && raw==='_solid') return descriptor('segment')
    if(domain==='form' && raw==='_air') return descriptor('open')
    const base=typeIcon(meta.type) || nodeKindIcon(meta.kind) || descriptor({form:'cube',member:'group',port:'input',input:'input',storey:'storey',wallSection:'segment',output:'output'}[domain] || 'graph')
    return {...base,detail:meta.index ?? (domain==='input' && /^\d+$/.test(raw)?Number(raw)+1:shortDetail(meta.label || raw)),identity:raw}
  }
  if(domain==='role') return descriptor('material',{detail:shortDetail(meta.label || raw),identity:raw})
  return null
}

// Normalize a whole list so names with equal initials still have distinct badges.
export function decorateChoices(domain,options,context={}) {
  const choices=options.map(option=>{
    const icon=choiceIcon(domain,option.value,{...context,...option})
    if(!icon) throw new Error(`Missing dropdown icon: ${domain}/${String(option.value)}`)
    return {...option,icon}
  })
  const peers=new Map()
  for(const option of choices) if(option.icon.identity && option.icon.detail) {
    const key=option.icon.kind+':'+option.icon.detail
    peers.set(key,[...(peers.get(key) || []),option])
  }
  for(const group of peers.values()) if(group.length>1) {
    const sorted=[...group].sort((a,b)=>String(a.value).localeCompare(String(b.value),'en'))
    sorted.forEach((option,index)=>{option.icon={...option.icon,detail:`${option.icon.detail}${index+1}`}})
  }
  return choices
}
