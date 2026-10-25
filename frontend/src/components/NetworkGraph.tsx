import { num } from '../format'
import type { Edge } from '../types'

/**
 * Accounts as dots on a circle, trades as arrows from seller to buyer (thicker = more shares).
 * Flagged accounts are red. A wash-trading ring shows up as a closed loop between red dots.
 */
export default function NetworkGraph({ nodes, edges, flagged }: { nodes: string[]; edges: Edge[]; flagged: string[] }) {
  if (edges.length === 0) return <p className="muted">No trades between accounts to show.</p>
  const size = 420
  const c = size / 2
  const r = size / 2 - 56
  const flaggedSet = new Set(flagged)
  // Flagged accounts first, so they sit together at the top of the circle.
  const ordered = [...nodes].sort((a, b) => Number(flaggedSet.has(b)) - Number(flaggedSet.has(a)) || a.localeCompare(b))
  const pos = new Map(
    ordered.map((n, i) => {
      const angle = -Math.PI / 2 + (2 * Math.PI * i) / ordered.length
      return [n, { x: c + r * Math.cos(angle), y: c + r * Math.sin(angle) }]
    }),
  )
  const maxShares = Math.max(...edges.map((e) => e.shares))

  return (
    <svg viewBox={`0 0 ${size} ${size}`} style={{ width: '100%', maxWidth: size, height: 'auto' }} role="img" aria-label="Trading network between accounts">
      <defs>
        {/* userSpaceOnUse keeps arrowheads the same size on thick and thin lines. */}
        <marker id="net-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" markerUnits="userSpaceOnUse" orient="auto-start-reverse">
          <path d="M0 0L10 5L0 10z" fill="var(--muted)" />
        </marker>
        <marker id="net-arrow-flag" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" markerUnits="userSpaceOnUse" orient="auto-start-reverse">
          <path d="M0 0L10 5L0 10z" fill="var(--chart-flag)" />
        </marker>
      </defs>
      {edges.map((e) => {
        const a = pos.get(e.from)
        const b = pos.get(e.to)
        if (!a || !b) return null
        const both = flaggedSet.has(e.from) && flaggedSet.has(e.to)
        // Stop the arrow short of the target dot.
        const dx = b.x - a.x
        const dy = b.y - a.y
        const len = Math.hypot(dx, dy) || 1
        const end = { x: b.x - (dx / len) * 12, y: b.y - (dy / len) * 12 }
        return (
          <line
            key={`${e.from}-${e.to}`}
            x1={a.x}
            y1={a.y}
            x2={end.x}
            y2={end.y}
            stroke={both ? 'var(--chart-flag)' : 'var(--muted)'}
            strokeOpacity={both ? 0.9 : 0.45}
            strokeWidth={1 + 4 * (e.shares / maxShares)}
            markerEnd={both ? 'url(#net-arrow-flag)' : 'url(#net-arrow)'}
          >
            <title>{`${e.from} sold ${num(e.shares)} shares to ${e.to} in ${e.trades} trade(s)`}</title>
          </line>
        )
      })}
      {ordered.map((n) => {
        const p = pos.get(n)!
        const red = flaggedSet.has(n)
        const anchor = p.x < c - 10 ? 'end' : p.x > c + 10 ? 'start' : 'middle'
        const lx = p.x + (anchor === 'end' ? -12 : anchor === 'start' ? 12 : 0)
        const ly = p.y + (p.y < c ? -12 : 20)
        return (
          <g key={n}>
            <circle cx={p.x} cy={p.y} r={red ? 8 : 6} fill={red ? 'var(--chart-flag)' : 'var(--surface)'} stroke={red ? 'var(--chart-flag)' : 'var(--muted)'} strokeWidth={1.5} />
            <text x={lx} y={ly} textAnchor={anchor} fontSize={11.5} fill={red ? 'var(--text)' : 'var(--muted)'} fontWeight={red ? 600 : 400}>
              {n}
            </text>
          </g>
        )
      })}
    </svg>
  )
}
