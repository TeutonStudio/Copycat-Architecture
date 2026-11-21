import { IconSelect } from './IconSelect.jsx'
import {OperatorChoice} from './OperatorChoice.jsx'
import {inputsReorderable,moveInput} from './input-list.js'
import {model} from './model.js'
import {useState} from 'react'
import { Handle, Position } from '@xyflow/react'
import { names, portName } from './Inspector.jsx'
import { portStyle } from './port-styles.js'
import {operatorFamily,operatorLabel} from './operator-families.js'
import { cardTypeLabel } from './card-types.js'
import {nodeControls} from './node-controls.js'
import {IntegerField} from './IntegerField.jsx'

function Socket({preview,...props}) {
  return preview
    ? <span aria-hidden="true" className={`preview-socket preview-socket-${props.type}`} style={props.style} />
    : <Handle {...props} />
}

// Canvas and picker share the same node rendering; picker sockets are static.
export function NodeAppearance({data:{node,ports=[],groupName,outputType,producedType,hasOutput,diagnostic,reference,dynamicAxes=[],outputs=[],mode='building',onOperation,onControl,onInputOrder},selected=false,preview=false}) {
  const [dropIndex,setDropIndex]=useState(null)
  const recipe=node.kind==='RESULT'&&node.resultMode!=='VALUE'?{...node,kind:node.producerKind,resultType:null}:node
  const canSort=!!onInputOrder && inputsReorderable(recipe,model.nodeProperties[mode]?.[recipe.kind])
  const indexOf=port=>/^(?:in:)?\d+$/.test(port.id)?Number(port.id.replace('in:','')):-1
  const sortable=port=>canSort&&indexOf(port)>=0&&indexOf(port)<node.inputs.length
  const dragType='application/x-graphstudio-input'
  const drop=(event,port)=>{
    if(!sortable(port))return
    const raw=event.dataTransfer.getData(dragType);setDropIndex(null)
    if(!raw)return
    event.preventDefault();event.stopPropagation()
    try {const from=JSON.parse(raw);if(from.node===node.id)onInputOrder(moveInput(node.inputs.length,from.index,indexOf(port)))}catch{}
  }
  const family=node.resultType?null:operatorFamily(mode,node.kind)
  const source = hasOutput ?? !(node.resultType || node.kind==='OUTPUT')
  return <div className={`diagram-node ${selected ? 'selected' : ''} ${diagnostic ? 'invalid' : ''} ${source ? '' : 'result-node'} ${preview ? 'node-preview' : ''}`}>
    <div className="node-heading">
      <span className="node-symbol">{source ? portStyle(outputType).symbol : '◆'}</span>
      <div><strong>{node.resultType ? "Ergebnis" : node.name || family?.label || names[node.kind]}</strong><small>{node.resultType ? cardTypeLabel(node.resultType) : family ? operatorLabel(mode,node.kind) : node.name ? names[node.kind] : node.id}{groupName ? ` · ${groupName}` : ''}</small></div>
      {source && outputs.length<2 && <Socket preview={preview} type="source" position={Position.Right} id={outputs[0]?.id || "out"} style={{background:portStyle(outputType).color}} aria-label={`Ausgang · ${outputType}`} title={`Ausgang · ${outputType}`} />}
    </div>
    {node.kind==='RESULT'&&node.resultMode!=='VALUE' && <div className="node-reference">Erzeugter Typ · {producedType?cardTypeLabel(producedType):'Unbestimmt'}</div>}
    {(family?.id==='numbers'||node.kind==='CONDITIONAL') && <div className="node-reference">Ausgang · {outputType?cardTypeLabel(outputType):'Unbestimmt'}</div>}
    {model.operators?.[recipe.kind] && <div className="node-operation nodrag nopan"><OperatorChoice node={recipe} onChange={node.kind==='RESULT'?value=>onControl?.('operation',value):onOperation} disabled={!onOperation}/></div>}
    {family && <div className="node-operation nodrag nopan" onPointerDown={event=>event.stopPropagation()} onClick={event=>event.stopPropagation()}><IconSelect domain="operation" stripSymbols aria-label={`${family.label} · Operation`} value={node.kind==='VECTOR_SPLIT'?'POSITION_X':node.kind==='INT'?'COUNT':node.kind} disabled={!onOperation} onChange={event=>onOperation(event.target.value)}>{family.operations.map(([kind,label])=><option key={kind} value={kind}>{label}</option>)}</IconSelect></div>}
    {node.kind==='GROUP_INPUT' && <label className="node-operation nodrag nopan">Name<input aria-label="Eingangsname" value={node.name} onChange={e=>onControl?.('name',e.target.value)} disabled={!onControl}/></label>}
    {nodeControls(mode,node).map(control=><div key={control.path} className="node-operation nodrag nopan" onPointerDown={event=>event.stopPropagation()} onClick={event=>event.stopPropagation()}>{control.choices
      ? <label>{control.label}<IconSelect domain={control.domain || 'type'} aria-label={control.label} value={control.value} disabled={!onControl} onChange={event=>onControl(control.path,event.target.value)}>{control.choices.map(choice=>{const [value,label]=Array.isArray(choice)?choice:[choice,choice];return <option value={value} key={value}>{label}</option>})}</IconSelect></label>
      : <IntegerField disabled={!onControl} label={control.label} value={control.value} min={control.min} max={control.max} onChange={value=>onControl?.(control.path,value)}/>}</div>)}
    {outputs.length>1 && <div className="ports-output">{outputs.map(output=><div className="port" key={output.id}><span>{output.name}</span><Socket preview={preview} type="source" position={Position.Right} id={output.id} style={{background:portStyle(output.type).color}} title={`${output.name} · ${output.type}`} aria-label={`Ausgang ${output.name} · ${output.type}`}/></div>)}</div>}
    {dynamicAxes.length>0 && <div className="node-reference">Dynamisch · {dynamicAxes.join(" / ")}</div>}
    {reference && <div className="node-reference" title={`${reference.documentId}@${reference.version}`}>↗ {reference.name} · v{reference.version}</div>}
    {diagnostic && <p className="node-diagnostic" role="alert" title={diagnostic}>⚠ {diagnostic}</p>}
    {ports.length > 0 && <div className="ports">{ports.map(port => <div className={`port ${dropIndex===indexOf(port)?'input-drop-target':''}`} key={port.id} onDragOver={event=>{if(sortable(port)&&event.dataTransfer.types.includes(dragType)){event.preventDefault();event.dataTransfer.dropEffect='move';setDropIndex(indexOf(port))}}} onDragLeave={()=>setDropIndex(null)} onDrop={event=>drop(event,port)}>
      <Socket preview={preview} type="target" position={Position.Left} id={port.id} style={{background:portStyle(port.types?.[0]).color}} title={`${portName(port, node)} · ${(port.types || []).join(" / ")}`} />
      <span>{port.name || portName(port, node)}</span>
      {sortable(port)?<button type="button" draggable className={`port-status port-sort-handle nodrag nopan ${port.source?'connected':''}`} aria-label={`${portName(port,node)} umsortieren`} title="Ziehen zum Umsortieren · ↑/↓ verschiebt mit Verbindung" onPointerDown={event=>event.stopPropagation()} onDragStart={event=>{event.stopPropagation();event.dataTransfer.effectAllowed='move';event.dataTransfer.setData(dragType,JSON.stringify({node:node.id,index:indexOf(port)}))}} onDragEnd={()=>setDropIndex(null)} onKeyDown={event=>{if(['ArrowUp','ArrowDown'].includes(event.key)){event.preventDefault();event.stopPropagation();const to=indexOf(port)+(event.key==='ArrowUp'?-1:1);if(to>=0&&to<node.inputs.length)onInputOrder(moveInput(node.inputs.length,indexOf(port),to))}}}>{port.source?'●':'○'}</button>:<span className={`port-status ${port.source?'connected':''}`}>{port.source?'●':'○'}</span>}
    </div>)}</div>}
  </div>
}
