import { previewGraph, transformDiagnostics } from './generated/engine.mjs'
self.onmessage = ({ data: { id, mode, document, library, selected, materials,viewKey } }) => {
  let diagnostics=[]
  try {diagnostics=transformDiagnostics(mode === 'building',document,library)} catch {}
  try {self.postMessage({id,diagnostics,viewKey,plan:previewGraph(mode === 'building',document,library,selected,materials)})}
  catch(error) {self.postMessage({id,diagnostics,viewKey,error:error.message || String(error)})}
}
