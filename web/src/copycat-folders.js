// Names are picker paths; source identities and exact versions remain unchanged.
export const pathOf = option => option.name.split('/').map(s => s.trim()).filter(Boolean)
export const versionOf = option => Number(option.entry?.version || option.reference?.match(/@(\d+)$/)?.[1] || option.document?.version || 1)
export function copycatGroups(options) {
  const groups = new Map()
  for (const option of options) {
    const path = pathOf(option), key = path.join('/')
    const group = groups.get(key) || { key, path, name: path.at(-1) || option.name, versions: [] }
    group.versions.push(option); groups.set(key, group)
  }
  return [...groups.values()].map(group => ({ ...group, versions: group.versions.sort((a,b) => versionOf(b)-versionOf(a) || a.id.localeCompare(b.id)) })).sort((a,b) => a.name.localeCompare(b.name,'de'))
}
export function folderRows(groups, path) {
  return Array.from({length:path.length+1}, (_,depth) => {
    const parent = path.slice(0,depth)
    const names = [...new Set(groups.filter(g => g.path.length > depth+1 && parent.every((name,i) => g.path[i] === name)).map(g => g.path[depth]))].sort((a,b) => a.localeCompare(b,'de'))
    return { parent, names, active:path[depth], depth }
  }).filter(row => row.names.length || row.depth === 0)
}
export function visibleGroups(groups,path,query) {
  const needle = query.trim().toLocaleLowerCase('de')
  if (needle) return groups.filter(g => g.path.join('/').toLocaleLowerCase('de').includes(needle) || g.versions.some(v => `${v.id} ${v.reference || ''}`.toLocaleLowerCase('de').includes(needle)))
  return groups.filter(g => g.path.length === path.length+1 && path.every((name,i) => g.path[i] === name))
}
