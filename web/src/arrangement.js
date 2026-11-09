export function fitArrangement(items) {
  const cells = items.flatMap(item => item.cells)
  if (!cells.length) return [-2, -2, 12, 12]
  let left = Infinity, top = Infinity, right = -Infinity, bottom = -Infinity
  for (const [x, y] of cells) {
    left = Math.min(left, x); top = Math.min(top, y)
    right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1)
  }
  const size = Math.max(8, right - left + 4, bottom - top + 4)
  return [(left + right - size) / 2, (top + bottom - size) / 2, size, size]
}

export function moveArrangement(offset, plane, horizontal, vertical, limit = 128) {
  const delta = plane === 'X' ? [0, -vertical, horizontal]
    : plane === 'Y' ? [horizontal, 0, vertical] : [horizontal, -vertical, 0]
  return Object.fromEntries(['x', 'y', 'z'].map((key, i) => [key,
    Math.max(-limit, Math.min(limit, (offset[key] ?? 0) + delta[i]))]))
}

// Merge adjacent occupied cells into row spans, keeping sparse outlines sparse.
export function arrangementPath(cells) {
  const rows = new Map()
  for (const [x, y] of cells) {
    if (!rows.has(y)) rows.set(y, new Set())
    rows.get(y).add(x)
  }
  let path = ''
  for (const [y, columns] of rows) {
    const sorted = [...columns].sort((a, b) => a - b)
    for (let i = 0; i < sorted.length;) {
      const start = sorted[i++]
      let end = start + 1
      while (sorted[i] === end) { i++; end++ }
      path += `M${start},${y}h${end - start}v1h${start - end}z`
    }
  }
  return path
}
