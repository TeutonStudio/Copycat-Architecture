import { readdirSync, readFileSync, writeFileSync } from 'node:fs'

// Generate the browser wire codec from the native DTO constructors. A new field
// or enum cannot silently disappear from browser imports/exports after a mod edit.
const directory = new URL('../src/main/kotlin/de/teutonstudio/copycatarchitect/domain/', import.meta.url)
const classes = new Map(), enums = new Set()
function stripComments(source) {
  return source.replace(/"(?:\\.|[^"\\])*"|\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, text => text.startsWith('/') ? ' '.repeat(text.length) : text)
}
function balanced(source, start, end = ')') {
  const stack = [end]; let quoted = false
  for (let i = start + 1; i < source.length; i++) {
    const ch = source[i]
    if (quoted) { if (ch === '\\') i++; else if (ch === '"') quoted = false; continue }
    if (ch === '"') { quoted = true; continue }
    if ('([{'.includes(ch)) stack.push({ '(': ')', '[': ']', '{': '}' }[ch])
    else if (ch === stack.at(-1)) { stack.pop(); if (!stack.length) return i }
  }
  throw new Error('Unbalanced Kotlin constructor')
}
function split(source) {
  const result = []; let depth = 0, start = 0, quoted = false
  for (let i = 0; i < source.length; i++) {
    const ch = source[i]
    if (quoted) { if (ch === '\\') i++; else if (ch === '"') quoted = false; continue }
    if (ch === '"') quoted = true
    else if ('([{<'.includes(ch)) depth++
    else if (')]}>' .includes(ch)) depth--
    else if (ch === ',' && depth === 0) { result.push(source.slice(start, i).trim()); start = i + 1 }
  }
  const last = source.slice(start).trim(); if (last) result.push(last)
  return result
}
for (const name of readdirSync(directory).filter(name => name.endsWith('.kt') && name !== 'GraphModels.kt')) {
  const source = stripComments(readFileSync(new URL(name, directory), 'utf8'))
  for (const match of source.matchAll(/enum\s+class\s+(\w+)/g)) enums.add(match[1])
  for (const match of source.matchAll(/data\s+class\s+(\w+)\s*\(/g)) {
    const start = match.index + match[0].length - 1
    const fields = split(source.slice(start + 1, balanced(source, start))).map(field => {
      const parsed = field.match(/^(?:val|var)\s+(\w+)\s*:\s*([^=]+?)(?:\s*=\s*([\s\S]+))?$/)
      if (!parsed) throw new Error(`Cannot parse ${match[1]} field: ${field}`)
      return { name: parsed[1], type: parsed[2].replace(/\s/g, ''), fallback: parsed[3] }
    })
    classes.set(match[1], fields)
  }
}
const used = new Set(), primitive = new Set(['String', 'Int', 'Long', 'Boolean', 'Double', 'Float'])
function argumentsOf(type) { return split(type.slice(type.indexOf('<') + 1, -1)) }
function visit(type) {
  if (type.endsWith('?')) return visit(type.slice(0, -1))
  if (type.includes('<')) { argumentsOf(type).forEach(visit); return }
  if (primitive.has(type) || enums.has(type) || used.has(type)) return
  if (!classes.has(type)) throw new Error(`Missing wire type ${type}`)
  used.add(type); classes.get(type).forEach(field => visit(field.type))
}
;['Form', 'Building', 'Module', 'NodeGraph', 'BuildingGraph', 'GraphNode', 'BuildingNode'].forEach(visit)
function read(type, value) {
  if (type.endsWith('?')) return `if (${value}==null) null else ${read(type.slice(0, -1), value)}`
  if (/^(List|Set)</.test(type)) return `wireArray(${value}).map { item -> ${read(argumentsOf(type)[0], 'item')} }${type.startsWith('Set') ? '.toSet()' : ''}`
  if (type.startsWith('Map<')) {
    const [key, item] = argumentsOf(type)
    if (!['String', 'Int'].includes(key)) throw new Error(`Unsupported map key ${key}`)
    return `wireKeys(${value}).associate { key -> ${key === 'Int' ? 'key.toInt()' : 'key'} to ${read(item, `${value}[key]`)} }`
  }
  if (primitive.has(type)) return `wire${type}(${value})`
  if (enums.has(type)) return `${type}.valueOf(wireString(${value}))`
  return `decode${type}(${value})`
}
function write(type, value) {
  if (type.endsWith('?')) return `${value}?.let { item -> ${write(type.slice(0, -1), 'item')} }`
  if (/^(List|Set)</.test(type)) return `${value}.map { item -> ${write(argumentsOf(type)[0], 'item')} }.toTypedArray()`
  if (type.startsWith('Map<')) return `json(*${value}.entries.map { entry -> entry.key.toString() to ${write(argumentsOf(type)[1], 'entry.value')} }.toTypedArray())`
  if (type === 'Long') return `${value}.toDouble()`
  if (primitive.has(type)) return value
  if (enums.has(type)) return `${value}.name`
  return `encode${type}(${value})`
}
let output = `// Generated from domain DTOs by generate-codec.mjs. Do not edit.\npackage de.teutonstudio.copycatarchitect.web\nimport de.teutonstudio.copycatarchitect.domain.*\nimport kotlin.js.Json\nimport kotlin.js.json\n\nprivate fun wireString(v:dynamic):String { require(jsTypeOf(v)=="string") { "Expected string" };return v.unsafeCast<String>() }\nprivate fun wireInt(v:dynamic):Int { require(jsTypeOf(v)=="number" && js("Number.isInteger(v) && v >= -2147483648 && v <= 2147483647").unsafeCast<Boolean>()) { "Expected 32-bit integer" };return v.unsafeCast<Int>() }\nprivate fun wireLong(v:dynamic):Long { require(jsTypeOf(v)=="number" && js("Number.isSafeInteger(v)").unsafeCast<Boolean>()) { "Expected safe integer" };return v.unsafeCast<Double>().toLong() }\nprivate fun wireBoolean(v:dynamic):Boolean { require(jsTypeOf(v)=="boolean") { "Expected boolean" };return v.unsafeCast<Boolean>() }\nprivate fun wireArray(v:dynamic):Array<dynamic> { require(js("Array.isArray(v)").unsafeCast<Boolean>() && v.length<=1000000) { "Expected bounded array" };return v.unsafeCast<Array<dynamic>>() }\nprivate fun wireKeys(v:dynamic):Array<String> { require(v!=null && jsTypeOf(v)=="object" && !js("Array.isArray(v)").unsafeCast<Boolean>()) { "Expected object" };return js("Object.keys(v)").unsafeCast<Array<String>>().also { keys -> require(keys.none { it in setOf("__proto__","prototype","constructor") }) { "Invalid property" } } }\n`
for (const name of used) {
  const fields = classes.get(name)
  output += `\ninternal fun decode${name}(v:dynamic):${name} {\n    wireKeys(v)\n`
  // Fail closed on unknown fields; export must never strip future native geometry.

  for (const { name, type, fallback } of fields) output += `    val ${name}:${type} = if(v.${name}==null) ${fallback ?? `error("Missing ${name}")`} else ${read(type, `v.${name}`)}\n`
  output += `    return ${name}(${fields.map(field => `${field.name}=${field.name}`).join(',')})\n}\n`
  output += `internal fun encode${name}(v:${name}):Json=json(${fields.map(field => `"${field.name}" to ${write(field.type, `v.${field.name}`)}`).join(',')})\n`
}
output += '\ninternal fun codecModels():Json=json(\n'
output += [...used].map(name => `"${name}" to json("fields" to json(${classes.get(name).map(field => `"${field.name}" to "${field.type}"`).join(',')}),"default" to ${classes.get(name).every(field => field.fallback !== undefined) ? `encode${name}(${name}())` : 'null'})`).join(',\n')
output += ',\n"enums" to json(' + [...enums].map(name => `"${name}" to ${name}.entries.map { it.name }.toTypedArray()`).join(',') + '))\n'
const target = new URL('src/generated/Codec.kt', import.meta.url)
if (!readFileSafe(target) || readFileSafe(target) !== output) writeFileSync(target, output)
function readFileSafe(path) { try { return readFileSync(path, 'utf8') } catch { return null } }
console.log(`Generated lossless codec for ${used.size} native DTOs`)
