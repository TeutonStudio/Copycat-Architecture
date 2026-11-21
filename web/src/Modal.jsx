import { useEffect, useId, useRef } from 'react'
export function Modal({ title, children, onClose, className = '' }) {
  const ref = useRef(null), id = useId()
  useEffect(() => {
    const opener = document.activeElement, el = ref.current
    const overflow = document.body.style.overflow
    el.showModal(); el.querySelector('[data-autofocus]')?.focus({preventScroll:true}); document.body.style.overflow = 'hidden'
    return () => { el.close(); document.body.style.overflow = overflow; if (opener?.isConnected) opener.focus({ preventScroll: true }) }
  }, [])
  return <dialog ref={ref} className={`studio-dialog ${className}`} aria-labelledby={id} onKeyDown={e => e.stopPropagation()} onCancel={e => { e.preventDefault(); onClose() }}>
    <header><h2 id={id}>{title}</h2><button aria-label="Schließen" onClick={onClose}>×</button></header>{children}
  </dialog>
}
