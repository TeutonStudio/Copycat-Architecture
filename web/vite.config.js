import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  base: './',
  esbuild: { jsx: 'automatic' },
  build: { license: { fileName: 'THIRD-PARTY-LICENSES.md' } },
  server: { fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] } },
  worker: { format: 'es' }
})
