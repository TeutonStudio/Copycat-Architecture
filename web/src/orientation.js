export const axisColors = { x: '#ff857f', y: '#a1e889', z: '#8ec5ff' }

// Minecraft world directions, shared by preview labels and segment region names.
export const sides = [
  { id: 'up', label: 'Oben', axis: 'y', sign: 1, direction: [0, 1, 0], up: [0, 0, -1] },
  { id: 'down', label: 'Unten', axis: 'y', sign: -1, direction: [0, -1, 0], up: [0, 0, 1] },
  { id: 'north', label: 'Nord', axis: 'z', sign: -1, direction: [0, 0, -1], up: [0, 1, 0] },
  { id: 'east', label: 'Ost', axis: 'x', sign: 1, direction: [1, 0, 0], up: [0, 1, 0] },
  { id: 'south', label: 'Süd', axis: 'z', sign: 1, direction: [0, 0, 1], up: [0, 1, 0] },
  { id: 'west', label: 'West', axis: 'x', sign: -1, direction: [-1, 0, 0], up: [0, 1, 0] },
]

export function segmentRegionName(key) {
  const match = /^region:x([01ib])_y([01ib])_z([01ib])$/.exec(key)
  if (!match) return key
  const states = { x: match[1], y: match[2], z: match[3] }
  const boundaries = Object.values(states).filter(state => state !== 'i').length
  const category = ['Inneres', 'Fläche', 'Kante', 'Ecke'][boundaries]
  const names = ['y', 'z', 'x'].filter(axis => states[axis] !== 'i').map(axis => {
    const state = states[axis]
    const labels = sign => sides.find(side => side.axis === axis && side.sign === sign).label
    return state === 'b' ? `${labels(-1)}/${labels(1)}` : labels(state === '0' ? -1 : 1)
  })
  return `${category}${names.length ? ` · ${names.join(' · ')}` : ''}`
}
