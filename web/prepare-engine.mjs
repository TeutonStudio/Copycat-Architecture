import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'

// One contract, one release: the mod ships this catalog and the web compiler
// embeds it alongside the same domain sources. Fingerprints are public evidence.
const root = new URL('../', import.meta.url)
const catalogue = readFileSync(new URL('src/main/resources/assets/copycat_architect/graph-models.json', root), 'utf8')
const model = JSON.parse(catalogue)
const release = JSON.parse(readFileSync(new URL('package.json', import.meta.url))).version
const hash = text => createHash('sha256').update(text).digest('hex')
const domain = new URL('src/main/kotlin/de/teutonstudio/copycatarchitect/domain/', root)
const sourceHash = hash(readdirSync(domain).filter(name => name.endsWith('.kt')).sort().map(name => name + '\n' + readFileSync(new URL(name, domain), 'utf8')).join('\n') + ['GraphDiagramModels', 'KilaGroupProjection', 'GroupGraphInterfaces'].map(name => name + '\n' + readFileSync(new URL(`src/main/kotlin/de/teutonstudio/copycatarchitect/client/${name}.kt`, root), 'utf8')).join('\n'))
const literal = JSON.stringify(catalogue).replaceAll('$', '\\$')
const output = new URL('src/generated/Catalog.kt', import.meta.url)
const source = `package de.teutonstudio.copycatarchitect.domain\ninternal val graphCatalogJson = ${literal}\ninternal const val graphRelease = "${release}"\ninternal const val graphCatalogHash = "${hash(catalogue)}"\n`
if (!existsSync(output) || readFileSync(output, 'utf8') !== source) writeFileSync(output, source)
const manifest = {
  release, catalogSha256: hash(catalogue), domainSha256: sourceHash,
  copycat: { nodes: Object.keys(model.copycat.nodes), graphSchemas: model.copycat.graphSchemas, documentSchemas: model.copycat.documentSchemas },
  building: { nodes: Object.keys(model.building.nodes), graphSchemas: model.building.graphSchemas, documentSchemas: model.building.documentSchemas },
  features: ['projection', 'nodeGroups', 'namedGroupInputs', 'sharedGroupDefinitions', 'exactGeometry', 'roofTemplates', 'specialRooms'],
}
writeFileSync(new URL('public/compatibility.json', import.meta.url), JSON.stringify(manifest, null, 2) + '\n')
