import { previewCopycatOption } from './copycat-options.js'

self.onmessage = ({ data: { id, option } }) => {
  try {
    self.postMessage({ id, plan: previewCopycatOption(option) })
  } catch (error) {
    self.postMessage({ id, error: error.message || String(error) })
  }
}
