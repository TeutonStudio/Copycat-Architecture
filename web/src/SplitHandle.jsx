import { useRef } from 'react'

export function SplitHandle({ axis, value, onChange, label }) {
  const dragging = useRef(false)
  const update = event => {
    const rect = event.currentTarget.parentElement.getBoundingClientRect()
    const fraction = axis === 'x' ? (event.clientX - rect.left) / rect.width : (event.clientY - rect.top) / rect.height
    onChange(Math.max(20, Math.min(80, fraction * 100)))
  }
  return <div className={`split-handle split-${axis}`} role="separator" tabIndex={0}
    aria-label={label} aria-orientation={axis === 'x' ? 'vertical' : 'horizontal'}
    aria-valuemin={20} aria-valuemax={80} aria-valuenow={Math.round(value)}
    onPointerDown={event => { dragging.current = true; event.currentTarget.setPointerCapture(event.pointerId); update(event) }}
    onPointerMove={event => { if (dragging.current) update(event) }}
    onPointerUp={event => { dragging.current = false; event.currentTarget.releasePointerCapture(event.pointerId) }}
    onPointerCancel={() => { dragging.current = false }}
    onDoubleClick={() => onChange(axis === 'x' ? 68 : 45)}
    onKeyDown={event => {
      const previous = axis === 'x' ? 'ArrowLeft' : 'ArrowUp'
      const next = axis === 'x' ? 'ArrowRight' : 'ArrowDown'
      if (![previous, next, 'Home', 'End'].includes(event.key)) return
      event.preventDefault()
      onChange(event.key === 'Home' ? 20 : event.key === 'End' ? 80 : Math.max(20, Math.min(80, value + (event.key === next ? 2 : -2))))
    }}><span /></div>
}
