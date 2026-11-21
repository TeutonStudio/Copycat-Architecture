import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

test('mirroring controls show one plane grid per list entry and no legacy grid below it', async () => {
  const server = await createServer({
    configFile: false,
    root: new URL('..', import.meta.url).pathname,
    esbuild: { jsx: 'automatic' },
    optimizeDeps: { noDiscovery: true, include: [] },
    server: { middlewareMode: true, hmr: false, ws: false },
  })
  try {
    const { TransformValues } = await server.ssrLoadModule('/src/TransformValues.jsx')
    const { CopycatTransforms } = await server.ssrLoadModule('/src/CopycatTransforms.jsx')
    for (const mirrors of [[], [{ plane: 'XY' }], [{ plane: 'X' }, { plane: 'BODY_PPP' }]]) {
      const props = { node: { kind: 'MIRRORING', mirrors }, edit() {}, primary: true }
      const markup = renderToStaticMarkup(createElement('div', null,
        createElement(TransformValues, props), createElement(CopycatTransforms, props)))
      assert.equal((markup.match(/role="radiogroup"/g) || []).length, mirrors.length)
      assert.equal((markup.match(/role="radio"/g) || []).length, 13 * mirrors.length)
      assert.doesNotMatch(markup, /Operator \/ Parameterwert/)
    }
    const markup = renderToStaticMarkup(createElement(CopycatTransforms, {
      node: { kind: 'COPYCAT', copycatTransforms: [{ kind: 'MIRROR', plane: 'XY' }] }, edit() {},
    }))
    assert.equal((markup.match(/role="radio"/g) || []).length, 13)
  } finally {
    await server.close()
  }
})
