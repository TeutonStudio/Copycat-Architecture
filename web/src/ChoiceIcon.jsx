import { iconPaths } from './choice-icons.js'
import { mirrorPolygon } from './mirror-planes.js'

const setFill={
  union:'M3 5H14V9H21V19H10V15H3Z', intersection:'M10 9h4v6h-4Z',
  difference:'M3 5H14V9H10V15H3Z', symmetricDifference:'M3 5H14V9H21V19H10V15H3ZM10 9h4v6h-4Z',
  invert:'M2 2h20v20H2ZM8 8h8v8H8Z',
}
const axisLines={X:'M5 19h16m-4-4 4 4-4 4',Y:'M5 19V3m-3 3 3-3 3 3',Z:'M5 19 18 6m-5 0h5v5',NORMAL:'M3 17 12 21 21 17 12 13ZM12 16V2m-3 3 3-3 3 3'}
const arrowAngles={north:0,east:90,south:180,west:270,up:0,down:180}

export function ChoiceIcon({icon,className=''}) {
  const {kind,detail,color}=icon
  let drawing
  if(setFill[kind]) drawing=<><path d={setFill[kind]} fill="currentColor" fillOpacity=".55" fillRule="evenodd" stroke="none"/><path d={kind==='invert'?'M2 2h20v20H2ZM8 8h8v8H8Z':'M3 5h11v10H3Zm7 4h11v10H10Z'}/></>
  else if(kind==='axis') drawing=<><path d="M5 3v16h16M5 19 18 6" opacity=".25"/><path d={axisLines[icon.value]}/></>
  else if(kind==='angle') drawing=<><path d="M12 12h9" opacity=".35"/><path d="M12 12h8m-3-3 3 3-3 3" transform={`rotate(${-icon.value*90} 12 12)`}/>{icon.value>0 && <path d={icon.value===1?'M19 12a7 7 0 0 0-7-7':icon.value===2?'M19 12a7 7 0 1 0-14 0':'M19 12a7 7 0 1 0-7 7'} strokeWidth="1.25"/>}{icon.value===0 && <circle cx="12" cy="12" r="3"/>}</>
  else if(kind==='order') drawing=<>{[...icon.value].map((letter,index)=><g key={letter} transform={`translate(${index*8} 7) scale(.32)`}>{iconPaths[{S:'mirror',R:'rotate',P:'translate'}[letter]].map((d,i)=><path d={d} key={i}/>)}</g>)}<path d="M3 20h18m-3-2 3 2-3 2" strokeWidth="1"/></>
  else if(kind==='view') drawing=<><path d="M12 2 22 7v10l-10 5L2 17V7Zm0 10v10M2 7l10 5 10-5"/><path d={{X:'M12 12 22 7v10l-10 5Z',Y:'M12 2 22 7l-10 5L2 7Z',Z:'M2 7l10 5v10L2 17Z'}[icon.value]} fill="currentColor" fillOpacity=".3"/></>
  else if(kind==='slope' && icon.ratio) {
    const [p,q]=icon.ratio,scale=18/Math.max(p,q)
    drawing=<><path d={`M3 21h18M3 21V3M3 21l${q*scale} ${-p*scale}`}/><path d="M5 17a4 4 0 0 1 4 4" opacity=".5"/></>
  } else if(kind==='compass') drawing=<><path d="M12 2v20M2 12h20" opacity=".3"/><path d="M12 3v17M7 8l5-5 5 5" transform={`rotate(${icon.degrees ?? arrowAngles[icon.value] ?? 0} 12 12)`}/></>
  else if(kind==='mirror' && icon.plane) drawing=<g transform="scale(.375 .4)"><path d="M4 26 36 18 60 28 28 38ZM4 26v24l24 8 32-10V28M28 38v20" opacity=".5"/><polygon points={mirrorPolygon(icon.plane).map(p=>p.join(',')).join(' ')} fill="currentColor" fillOpacity=".25"/></g>
  else drawing=(iconPaths[kind] || []).map((d,i)=><path key={i} d={d}/>)
  return <span className={`choice-icon ${detail!=null?'choice-icon-detailed':''} ${className}`} aria-hidden="true" data-icon={kind} style={color?{color}:undefined}>
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" focusable="false">{drawing}</svg>
    {detail!=null && <span className="choice-icon-detail">{String(detail)}</span>}
  </span>
}
