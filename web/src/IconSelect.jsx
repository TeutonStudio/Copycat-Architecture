import { Children, Fragment, isValidElement, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ChoiceIcon } from './ChoiceIcon.jsx'
import { decorateChoices } from './choice-icons.js'
import { enabledIndex, nextIndex, popupPosition, searchIndex } from './select-model.js'

const textOf=children=>Children.toArray(children).map(child=>isValidElement(child)?textOf(child.props.children):String(child)).join('')
function childOptions(children) {
  return Children.toArray(children).flatMap(child=>{
    if(!isValidElement(child)) return []
    if(child.type===Fragment) return childOptions(child.props.children)
    if(child.type!=='option') throw new Error('IconSelect children must be options')
    const label=textOf(child.props.children)
    return [{value:child.props.value ?? label,label,disabled:Boolean(child.props.disabled),...child.props.iconMeta}]
  })
}
const stop=event=>event.stopPropagation()

/** Typed options/onValueChange for new callers; option children/onChange retain existing select conversions. */
export function IconSelect({domain,context={},options,children,value,onChange,onValueChange,disabled=false,className='',stripSymbols=false,choiceMeta,...attributes}) {
  const id=useId(),trigger=useRef(null),popup=useRef(null),search=useRef({text:'',time:0}),opened=useRef(false)
  const [open,setOpen]=useState(false),[active,setActive]=useState(-1),[position,setPosition]=useState(null),[host,setHost]=useState(null)
  const raw=(options || childOptions(children)).map(option=>({...option,...choiceMeta?.(option.value),label:stripSymbols?option.label.replace(/^[∪∩−△≔∖∁↻↔↦+×÷=≠<≤>≥∣∧∨⊻¬⇒⇔]\s+/u,''):option.label}))
  const choices=decorateChoices(domain,raw,context)
  const selected=choices.findIndex(option=>String(option.value)===String(value ?? ''))
  const current=choices[selected] || choices[0]
  const signature=JSON.stringify(choices.map(option=>[option.value,option.disabled]))
  const disabledNow=()=>disabled || trigger.current?.matches(':disabled')
  const close=()=>{opened.current=false;setOpen(false)}
  const commit=index=>{
    if(!opened.current) return
    opened.current=false
    const option=choices[index]
    if(option && !option.disabled && !disabledNow() && String(option.value)!==String(value ?? '')) {
      onValueChange?.(option.value)
      onChange?.({target:{value:String(option.value)},currentTarget:{value:String(option.value)}})
    }
    setOpen(false)
  }
  const show=(index=selected)=>{
    if(disabledNow() || !choices.some(option=>!option.disabled)) return
    setHost(trigger.current.closest('dialog,[role="dialog"]') || document.body)
    setActive(index>=0 && choices[index] && !choices[index].disabled?index:enabledIndex(choices,0,1))
    search.current={text:'',time:0};opened.current=true;setOpen(true)
  }
  const visibleOptions=useRef(choices);visibleOptions.current=choices
  useEffect(()=>{close()},[disabled,value])
  useEffect(()=>{if(open && (active>=choices.length || choices[active]?.disabled)) setActive(enabledIndex(choices,0,1))},[signature,open,active])
  useLayoutEffect(()=>{
    if(!open) return
    const update=()=>{
      if(!trigger.current?.isConnected || disabledNow()) {close();return}
      setPosition(popupPosition(trigger.current.getBoundingClientRect(),{width:document.documentElement.clientWidth,height:window.innerHeight},visibleOptions.current.length))
    }
    update()
    const resize=new ResizeObserver(update);resize.observe(trigger.current)
    window.addEventListener('resize',update);window.addEventListener('scroll',update,true)
    const mutation=new MutationObserver(update)
    for(let parent=trigger.current.parentElement;parent;parent=parent.parentElement) if(parent.tagName==='FIELDSET') mutation.observe(parent,{attributes:true,attributeFilter:['disabled']})
    return ()=>{resize.disconnect();mutation.disconnect();window.removeEventListener('resize',update);window.removeEventListener('scroll',update,true)}
  },[open,host,signature])
  useLayoutEffect(()=>{if(open && active>=0) popup.current?.querySelector(`[data-choice-index="${active}"]`)?.scrollIntoView({block:'nearest'})},[open,active])
  useEffect(()=>{
    if(!open) return
    const outside=event=>{if(!trigger.current?.contains(event.target) && !popup.current?.contains(event.target)) close()}
    document.addEventListener('pointerdown',outside,true)
    return ()=>document.removeEventListener('pointerdown',outside,true)
  },[open])
  const keyboard=event=>{
    event.stopPropagation()
    if(disabledNow()) return
    const key=event.key
    if(key==='Escape' && open) {event.preventDefault();close();return}
    if(key==='Tab') {if(open) commit(active);return}
    if(key==='Enter' || key===' ' && !(search.current.text && Date.now()-search.current.time<700)) {event.preventDefault();if(open) commit(active);else show();return}
    if(['ArrowDown','ArrowUp','Home','End','PageDown','PageUp'].includes(key)) {
      event.preventDefault()
      if(!open) {show(key==='End'?enabledIndex(choices,choices.length-1,-1):key==='Home'?enabledIndex(choices,0,1):selected);return}
      if(event.altKey && key==='ArrowUp') {commit(active);return}
      if(key==='Home') setActive(enabledIndex(choices,0,1))
      else if(key==='End') setActive(enabledIndex(choices,choices.length-1,-1))
      else if(key.startsWith('Page')) {
        const direction=key==='PageDown'?1:-1
        let index=active;for(let n=0;n<10;n++) index=nextIndex(choices,index,direction);setActive(index)
      } else setActive(nextIndex(choices,active,key==='ArrowDown'?1:-1))
      return
    }
    if(key.length===1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault()
      const now=Date.now(),previous=now-search.current.time<700?search.current.text:''
      const query=previous===key?key:previous+key
      const index=searchIndex(choices,query,query.length===1?Math.max(0,active+1):0)
      if(!open) show(index)
      else if(index>=0) setActive(index)
      search.current={text:query,time:now}
    }
  }
  // A real button also inherits disabled fieldsets and participates in existing modal focus traps.
  return <>
    <button {...attributes} type="button" ref={trigger} id={attributes.id || `${id}-trigger`} role="combobox"
      className={`icon-select nodrag nopan nowheel ${className}`} disabled={disabled} title={attributes.title || current?.label}
      aria-haspopup="listbox" aria-controls={`${id}-list`} aria-expanded={open}
      aria-activedescendant={open && active>=0?`${id}-option-${active}`:undefined}
      data-choice-domain={domain} onPointerDown={stop} onDoubleClick={stop} onWheel={stop}
      onKeyDown={keyboard} onClick={event=>{event.stopPropagation();if(open)close();else show()}}
      onBlur={event=>{if(open && !popup.current?.contains(event.relatedTarget)) commit(active)}}>
      {current && <><ChoiceIcon icon={current.icon}/><span className="icon-select-value">{current.label}</span></>}
      <svg className="icon-select-chevron" viewBox="0 0 16 16" aria-hidden="true"><path d="m4 6 4 4 4-4"/></svg>
    </button>
    {open && host && position && createPortal(<div ref={popup} id={`${id}-list`} role="listbox"
      aria-label={attributes['aria-label']} aria-labelledby={attributes['aria-labelledby'] || (!attributes['aria-label']?trigger.current?.id:undefined)}
      className="icon-select-popup nodrag nopan nowheel" style={position} onPointerDown={event=>{event.stopPropagation();event.preventDefault()}}
      onClick={stop} onDoubleClick={stop} onKeyDown={stop} onWheel={stop} onContextMenu={event=>{event.preventDefault();event.stopPropagation()}}>
      {choices.map((option,index)=><div key={String(option.value)} id={`${id}-option-${index}`} role="option"
        aria-selected={selected===index} aria-disabled={option.disabled || undefined} data-choice-index={index} data-choice-value={String(option.value)}
        className={`icon-select-option ${active===index?'is-active':''}`}
        onPointerMove={event=>{if(event.pointerType!=='touch' && !option.disabled) setActive(index)}} onClick={event=>{event.stopPropagation();if(!option.disabled)commit(index)}}>
        <ChoiceIcon icon={option.icon}/><span className="icon-select-label">{option.label}</span>{selected===index && <svg className="icon-select-check" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="m4 12 5 5L20 5"/></svg>}
      </div>)}
    </div>,host)}
  </>
}
