import { previewGraph } from './generated/engine.mjs'

self.onmessage = ({ data: { id, mode, document, library, selected } }) => {
  try {
    self.postMessage({ id, plan: previewGraph(mode === 'building', document, library, selected) })
  } catch (error) {
    self.postMessage({ id, error: error.message || String(error) })
  }
}
