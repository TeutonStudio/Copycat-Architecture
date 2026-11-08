import { existsSync, readFileSync, writeFileSync } from 'node:fs'

// Embed the native catalogue as data; the browser adapter reads its port rules.
const catalogue = readFileSync(new URL('../src/main/resources/assets/copycat_architect/graph-models.json', import.meta.url), 'utf8')
JSON.parse(catalogue)
const literal = JSON.stringify(catalogue).replaceAll('$', '\\$')
const output = new URL('src/generated/Catalog.kt', import.meta.url)
const source = `package de.teutonstudio.copycatarchitect.domain\ninternal val graphCatalogJson = ${literal}\n`
if (!existsSync(output) || readFileSync(output, 'utf8') !== source) writeFileSync(output, source)
