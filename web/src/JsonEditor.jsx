import { useEffect, useRef } from 'react'
import { basicSetup, EditorView } from 'codemirror'
import { json } from '@codemirror/lang-json'
import { oneDark } from '@codemirror/theme-one-dark'

export function JsonEditor({value,onChange,readOnly=false}) {
  const host=useRef(null), current=useRef(onChange)
  current.current=onChange
  useEffect(() => {
    const editor=new EditorView({doc:value,parent:host.current,extensions:[basicSetup,json(),oneDark,
      EditorView.editable.of(!readOnly),EditorView.contentAttributes.of({'aria-label':'JSON-Arbeitskopie','data-autofocus':'true',spellcheck:'false'}),
      EditorView.updateListener.of(update => {if(update.docChanged) current.current(update.state.doc.toString())})]})
    editor.focus()
    return () => editor.destroy()
  },[])
  return <div ref={host} className="json-code-editor" />
}
