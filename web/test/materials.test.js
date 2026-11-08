import test from 'node:test'
import assert from 'node:assert/strict'
import { createMaterials, defaultProperties, importRepository, matchesState, materialValue, parseManifest, parseMaterial, parseRepository, stateModels } from '../src/materials.js'
import { emptyWorkspace, validateWorkspace } from '../src/storage.js'

test('Minecraft catalog follows parent models and selects Faithful faces', async () => {
  const catalog = createMaterials()
  assert.ok(catalog.ids.length > 1000)
  const stone = await catalog.resolve('minecraft:stone')
  assert.equal(stone.faces.length, 6)
  assert.ok(stone.faces.every(url => url?.includes('/Faithful-64x-Java/') && url.endsWith('/stone.png')))
  assert.deepEqual(stone.missing, [])
  const log = await catalog.resolve('minecraft:oak_log[axis=y]')
  assert.ok(log.faces[0].endsWith('/oak_log.png'))
  assert.ok(log.faces[2].endsWith('/oak_log_top.png'))
  const horizontal = await catalog.resolve('minecraft:oak_log[axis=x]')
  assert.ok(horizontal.faces[0].endsWith('/oak_log_top.png'))
  assert.ok(horizontal.faces[2].endsWith('/oak_log.png'))
  await assert.rejects(catalog.resolve('minecraft:oak_log[axis=q]'), /Unbekannter Blockzustand/)
})

test('state matching supports multipart OR/AND and deterministic weighted variants', () => {
  assert.ok(matchesState({ OR: [{ north: 'true' }, { AND: [{ axis: 'x|y' }, { waterlogged: 'false' }] }] }, { axis: 'y', waterlogged: 'false' }))
  assert.equal(matchesState({ axis: 'x' }, { axis: 'z' }), false)
  assert.deepEqual(stateModels({ variants: { '': [{ model: 'a', weight: 1 }, { model: 'b' }] } }, {}), [{ model: 'a', weight: 1 }])
  assert.deepEqual(defaultProperties({ variants: { 'axis=x': {}, 'axis=y': {}, 'axis=z': {} } }), { axis: 'y' })
  assert.equal(materialValue('minecraft:stone', {}), 'minecraft:stone')
  assert.deepEqual(parseMaterial('mod:block[axis=x,lit=false]'), { block: 'mod:block', properties: { axis: 'x', lit: 'false' } })
  assert.throws(() => parseMaterial('mod:../private'), /Ungültige/)
})

test('mod resources resolve dependencies across linked repositories and expose missing resources', async () => {
  const mods = [{ repo: 'owner/mod', blocks: { 'mod:block': { variants: { '': { model: 'mod:block/child' } } } },
    models: { 'mod:block/child': { parent: 'dep:block/base', textures: { top: 'mod:block/top', side: 'dep:block/side' } } }, textures: { 'mod:block/top': 'https://example.test/top.png' } },
  { repo: 'owner/dep', blocks: {}, models: { 'dep:block/base': { parent: 'minecraft:block/cube', textures: { up: '#top', down: '#side', east: '#side', west: '#side', north: '#side', south: '#side' } } }, textures: { 'dep:block/side': 'https://example.test/side.png' } }]
  const result = await createMaterials(mods).resolve('mod:block')
  assert.equal(result.faces[2], 'https://example.test/top.png')
  assert.equal(result.faces[0], 'https://example.test/side.png')
  await assert.rejects(createMaterials(mods.slice(0, 1)).resolve('mod:block'), /Modell fehlt: dep:block\/base/)
  const missing = await createMaterials([{ ...mods[0], models: { 'mod:block/child': { parent: 'minecraft:block/cube_all', textures: { all: 'dep:block/lost' } } } }]).resolve('mod:block')
  assert.deepEqual(missing.missing, ['dep:block/lost'])
})

test('cyclic parent and texture references stop instead of hanging', async () => {
  const block = { blocks: { 'mod:a': { variants: { '': { model: 'mod:block/a' } } } }, textures: {}, models: { 'mod:block/a': { parent: 'mod:block/b' }, 'mod:block/b': { parent: 'mod:block/a' } } }
  await assert.rejects(createMaterials([block]).resolve('mod:a'), /Zyklische Modell/)
  block.models = { 'mod:block/a': { parent: 'minecraft:block/cube_all', textures: { all: '#cycle', cycle: '#all' } } }
  await assert.rejects(createMaterials([block]).resolve('mod:a'), /Zyklischer Texturverweis/)
})

test('Fabric and Forge/NeoForge metadata retain dependency versions and optionality', () => {
  assert.deepEqual(parseManifest('fabric.mod.json', JSON.stringify({ id: 'mod', depends: { minecraft: '~1.21.1', dep: '>=1' } })).dependencies[1], { id: 'dep', required: true, version: '>=1' })
  const result = parseManifest('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="mod"\n[[dependencies.mod]]\nmodId="dep"\ntype="required"\nversionRange="[1,2)"\n[[dependencies.mod]]\nmodId="extra"\nmandatory=false\n')
  assert.deepEqual(result.ids, ['mod'])
  assert.deepEqual(result.dependencies, [{ id: 'dep', required: true, version: '[1,2)' }, { id: 'extra', required: false, version: '' }])
})

test('repository import pins all resources to a commit and discovers generated assets', async () => {
  const oldFetch = globalThis.fetch
  const commit = 'a'.repeat(40)
  const paths = ['common/src/generated/resources/assets/mod/blockstates/stone.json', 'common/src/main/resources/assets/mod/models/block/stone.json', 'common/src/main/resources/assets/mod/textures/block/stone.png', 'common/src/main/resources/fabric.mod.json']
  const calls = []
  globalThis.fetch = async url => {
    calls.push(url)
    const body = url.endsWith('/repos/author/mod') ? { default_branch: '1.21/main', license: { name: 'MIT' } }
      : url.includes('/commits/') ? { sha: commit } : url.includes('/git/trees/') ? { tree: paths.map(path => ({ type: 'blob', mode: '100644', path })) }
        : { id: 'mod', depends: { minecraft: '1.21.1' } }
    return { ok: true, text: async () => JSON.stringify(body) }
  }
  try {
    const result = await importRepository('https://github.com/author/mod.git')
    assert.equal(result.repo, 'author/mod')
    assert.equal(result.commit, commit)
    assert.ok(result.blocks['mod:stone'].includes(`/${commit}/common/src/generated/`))
    assert.ok(calls.some(url => url.includes('1.21%2Fmain')))
    assert.deepEqual(result.ids, ['mod'])
  } finally { globalThis.fetch = oldFetch }
  assert.throws(() => parseRepository('https://github.com.evil.test/author/mod'), /GitHub/)
  assert.throws(() => parseRepository('https://github.com/author/mod/tree/main'), /Zweig separat/)
})

test('backups reject malformed and foreign resource links without discarding existing data', () => {
  const workspace = emptyWorkspace()
  assert.equal(validateWorkspace(workspace), workspace)
  assert.throws(() => validateWorkspace({ ...workspace, schema: 9 }), /Schema 1/)
  const mod = { repo: 'author/mod', commit: 'a'.repeat(40), blocks: { 'mod:a': 'https://evil.test/private' }, models: {}, textures: {}, ids: [], dependencies: [] }
  assert.throws(() => validateWorkspace({ ...workspace, mods: [mod] }), /Ressourcen-Adressen/)
})
