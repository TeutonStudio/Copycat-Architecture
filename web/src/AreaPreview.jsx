import { IconSelect } from './IconSelect.jsx'
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { areaGraph } from './generated/engine.mjs'
import { IntegerField } from './IntegerField.jsx'
import { areaKey, areaModePatch, areaModes, fitArea, paintArea, rasterLine, rasterRectangle, areaTools } from './area-grid.js'

export function AreaControls({node, model, edit, readOnly=false}) {
  return <div className="area-controls"><label>Flächenform<IconSelect domain="AreaMode" aria-label="Flächenform" value={node.areaMode || 'DRAW'} disabled={readOnly} onChange={e=>edit(areaModePatch(node,e.target.value,model?.points || node.coordinates || []))}>{areaModes.map(([value,label])=><option key={value} value={value}>{label}</option>)}</IconSelect></label>
    {node.areaMode && node.areaMode!=='DRAW' && <div className="coordinate-grid" style={{'--coordinate-count':node.areaMode==='SQUARE'?1:2}}>
      <div>{node.dimensions?.length ? <p className="hint">{node.areaMode==='SQUARE'?'Seitenlänge':'Breite X'}: {model?.width ?? '…'} · Anzahl gebunden</p> : <IntegerField label={node.areaMode==='SQUARE'?'Seitenlänge':'Breite X'} value={node.length} min={1} max={128} onChange={length=>edit({length})}/>}</div>
      {node.areaMode==='RECTANGLE' && <div>{node.dimensions?.height ? <p className="hint">Länge Z: {model?.length ?? '…'} · Anzahl gebunden</p> : <IntegerField label="Länge Z" value={node.height} min={1} max={128} onChange={height=>edit({height})}/>}</div>}
    </div>}
  </div>
}

export function AreaPreview({node, document, edit, readOnly, previewMode, onMode, collapsed, onToggle}) {
  const svg=useRef(null),drag=useRef(null),id=useId().replaceAll(':','')
  const [tool,setTool]=useState('PAINT'),[drawError,setDrawError]=useState(null),[draft,setDraft]=useState(null),[viewport,setViewport]=useState(null),[focus,setFocus]=useState({x:0,z:0})
  const model=useMemo(()=>{try{return areaGraph(document,node.id)}catch(error){return {points:[],error:error.message || String(error)}}},[document,node.id])
  useEffect(()=>{setViewport(null);setFocus({x:0,z:0});setTool('PAINT');setDrawError(null)},[node.id])
  useEffect(()=>{setDraft(null);drag.current=null},[document])
  const points=draft ?? model.points,bounds=viewport ?? fitArea(model.points)
  const selected=new Set(points.map(p=>areaKey(p.x,p.z))),draw=!node.areaMode || node.areaMode==='DRAW'
  const point=event=>{const p=svg.current.createSVGPoint();p.x=event.clientX;p.y=event.clientY;const v=p.matrixTransform(svg.current.getScreenCTM().inverse());return {x:Math.floor(v.x),z:Math.floor(v.y)}}
  const finish=event=>{const state=drag.current;drag.current=null;if(state?.points && !state.error) {edit({coordinates:state.points,areaMode:'DRAW',dimensions:null});setDraft(null)};setDraft(null);if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId)}
  const cancel=()=>{drag.current=null;setDraft(null)}
  const chooseTool=value=>{cancel();setTool(value);setDrawError(null);if(!draw)edit({...areaModePatch(node,'DRAW',model.points),coordinates:model.points})}
  const updateStroke=(state,p)=>{
    try {
      const next=state.tool==='PAINT'?paintArea(state.points,rasterLine(state.last,p),state.add):paintArea(state.base,rasterRectangle(state.start,p,state.tool==='SQUARE'),state.add)
      state.points=next;state.last=p;state.error=false;setDraft(next);setDrawError(null)
    } catch(error) {state.error=true;setDrawError(error.message)}
  }
  const zoom=factor=>setViewport(([x,z,w,h]=bounds)=>{const size=Math.max(4,Math.min(260,w*factor));return [x+(w-size)/2,z+(h-size)/2,size,size]})
  return <section className="preview-panel area-panel" aria-label="2D-Flächenvorschau"><div className="panel-heading"><span>2D-Fläche · X/Z</span><div className="button-row"><div className="preview-target-switch" role="group" aria-label="Vorschauziel">{[['graph','Graph'],['selected','Selektiert']].map(([value,label])=><button key={value} aria-pressed={previewMode===value} onClick={()=>onMode(value)}>{label}</button>)}</div><button aria-expanded={!collapsed} aria-label={collapsed?'Flächenvorschau ausklappen':'Flächenvorschau einklappen'} onClick={onToggle}>{collapsed?'▸':'▾'}</button></div></div>
    {!collapsed && <div className="area-preview-body"><fieldset disabled={readOnly} className="area-options"><AreaControls {...{node,model,edit,readOnly}}/></fieldset>
      <fieldset className="area-drawing-tools" disabled={readOnly}><legend>Bedienung</legend><label>Zeichenwerkzeug<IconSelect domain="areaTool" aria-label="Zeichenwerkzeug" value={draw?tool:''} onChange={event=>chooseTool(event.target.value)}>{!draw && <option value="" disabled iconMeta={{placeholder:true}}>Werkzeug wählen …</option>}{areaTools.map(([value,label])=><option key={value} value={value}>{label}</option>)}</IconSelect></label><p className="hint">{!draw?'Die Anzahlwerte bestimmen die Fläche. Ein Zeichenwerkzeug übernimmt die aktuelle Fläche ins freie Raster.':tool==='PAINT'?'Zellen anklicken oder übermalen. Auf einer belegten Zelle beginnen, um zu löschen.':`${tool==='SQUARE'?'Quadrat':'Rechteck'} mit gedrückter Maustaste ziehen. Loslassen übernimmt die Vorschau. Alt entfernt den gezogenen Bereich.`} 1 Zelle = 1 Block.</p></fieldset>
      <details className="area-help"><summary>Tastatur und Navigation</summary><p className="hint">Pfeiltasten bewegen die Markierung, Leertaste schaltet eine Zelle um. Escape verwirft den aktuellen Strich. Mittlere Maustaste verschiebt das Raster.</p></details>
      <svg ref={svg} className="area-grid" role="application" aria-label="Flächenraster" tabIndex={0} viewBox={bounds.join(' ')} onContextMenu={e=>e.preventDefault()}
        onPointerDown={e=>{if(e.button!==0 && e.button!==1)return;e.preventDefault();svg.current.focus();const p=point(e);setFocus(p);if(e.button===1){drag.current={pan:true,x:e.clientX,z:e.clientY,bounds};svg.current.setPointerCapture(e.pointerId);return};if(!draw || readOnly)return;setDrawError(null);const state={tool,add:!e.altKey && (tool!=='PAINT' || !selected.has(areaKey(p.x,p.z))),start:p,last:p,base:points,points};drag.current=state;updateStroke(state,p);svg.current.setPointerCapture(e.pointerId)}}
        onPointerMove={e=>{const state=drag.current;if(!state)return;if(state.pan){const rect=svg.current.getBoundingClientRect();const scale=state.bounds[2]/Math.min(rect.width,rect.height);setViewport([state.bounds[0]-(e.clientX-state.x)*scale,state.bounds[1]-(e.clientY-state.z)*scale,...state.bounds.slice(2)]);return};const p=point(e);updateStroke(state,p);setFocus(p)}} onPointerUp={finish} onPointerCancel={cancel} onLostPointerCapture={cancel}
        onKeyDown={e=>{if(e.ctrlKey || e.metaKey)return;e.stopPropagation();if(e.key==='Escape'){e.preventDefault();cancel();return};const d={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]}[e.key];if(d){e.preventDefault();const p={x:Math.max(-128,Math.min(127,focus.x+d[0])),z:Math.max(-128,Math.min(127,focus.z+d[1]))};setFocus(p);if(p.x<bounds[0] || p.x>=bounds[0]+bounds[2] || p.z<bounds[1] || p.z>=bounds[1]+bounds[3])setViewport([p.x-bounds[2]/2,p.z-bounds[3]/2,bounds[2],bounds[3]])};if((e.key===' ' || e.key==='Enter') && draw && !readOnly){e.preventDefault();if(!e.repeat)edit({coordinates:paintArea(model.points,[focus],!selected.has(areaKey(focus.x,focus.z))),areaMode:'DRAW',dimensions:null})}}}>
        <defs><pattern id={id} width="1" height="1" patternUnits="userSpaceOnUse"><path d="M1,0H0V1" fill="none" stroke="#607983" strokeWidth="0.025"/></pattern></defs>
        <rect x={bounds[0]} y={bounds[1]} width={bounds[2]} height={bounds[3]} fill="#14232d"/>
        <path d={points.map(p=>`M${p.x},${p.z}h1v1h-1Z`).join('')} fill="#4baba3"/>
        <rect x={bounds[0]} y={bounds[1]} width={bounds[2]} height={bounds[3]} fill={`url(#${id})`}/>
        <path d={`M0,${bounds[1]}v${bounds[3]}M${bounds[0]},0h${bounds[2]}`} stroke="#d69b62" strokeWidth="0.05" fill="none"/>
        <rect x={focus.x} y={focus.z} width="1" height="1" fill="none" stroke="#ffe4a3" strokeWidth="0.08"/>
      </svg>
      <div className="area-toolbar"><button onClick={()=>zoom(.75)}>＋</button><button onClick={()=>zoom(4/3)}>−</button><button onClick={()=>setViewport(fitArea(points))}>Einpassen</button><span>X {focus.x} · Z {focus.z} · {points.length} Zellen</span>{draw && <button disabled={readOnly || !points.length} onClick={()=>edit({coordinates:[],areaMode:'DRAW',dimensions:null})}>Leeren</button>}</div>
      {drawError && <p role="alert">{drawError}</p>}
      {model.error && <p role="alert">{model.error}</p>}
      {!model.points.length && <p role="status">Die Fläche ist leer. Wähle Rasterzellen aus.</p>}
    </div>}
  </section>
}
