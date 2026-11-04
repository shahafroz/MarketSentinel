import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceArea,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { num, nyTime } from '../format'
import type { MarketContext } from '../types'

// Prices are converted to numbers here only to place points on the chart; tables show the exact strings.
/** Minute closes (line) and volume (bars) for the day, the alert window shaded, flagged trades as red dots. */
export default function PriceChart({ market, windowStart, windowEnd }: { market: MarketContext; windowStart: string; windowEnd: string }) {
  const data = market.bars.map((b) => ({ t: new Date(b.time).getTime(), close: Number(b.close), volume: b.volume }))
  const flagged = market.flaggedTrades.map((t) => ({ t: new Date(t.time).getTime(), price: Number(t.price), qty: t.quantity, who: `${t.seller} → ${t.buyer}` }))
  if (data.length === 0) return <p className="muted">No trades stored for {market.symbol} on {market.date}.</p>

  const prices = [...data.map((d) => d.close), ...flagged.map((f) => f.price)]
  const lo = Math.min(...prices)
  const hi = Math.max(...prices)
  const pad = (hi - lo) * 0.08 || hi * 0.01
  const x1 = new Date(windowStart).getTime()
  const x2 = Math.max(new Date(windowEnd).getTime(), x1 + 60_000)

  return (
    <div style={{ width: '100%', height: 320 }}>
      <ResponsiveContainer>
        <ComposedChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 4 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="t"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(v: number) => nyTime(v).slice(0, 5)}
            stroke="var(--muted)"
            fontSize={12}
          />
          <YAxis yAxisId="price" domain={[lo - pad, hi + pad]} tickFormatter={(v: number) => v.toFixed(2)} stroke="var(--muted)" fontSize={12} width={60} />
          <YAxis yAxisId="vol" orientation="right" hide domain={[0, (dataMax: number) => dataMax * 4]} />
          <ReferenceArea yAxisId="price" x1={x1} x2={x2} fill="var(--chart-band)" stroke="var(--chart-flag)" strokeOpacity={0.35} />
          <Bar yAxisId="vol" dataKey="volume" fill="var(--chart-bar)" isAnimationActive={false} />
          <Line yAxisId="price" dataKey="close" stroke="var(--chart-line)" strokeWidth={1.75} dot={false} isAnimationActive={false} />
          <Scatter yAxisId="price" data={flagged} dataKey="price" fill="var(--chart-flag)" isAnimationActive={false} />
          <Tooltip
            contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8 }}
            labelFormatter={(v) => `${nyTime(Number(v))} ET`}
            formatter={(value, name) => {
              const n = Number(value)
              if (name === 'volume') return [num(n), 'Volume']
              if (name === 'close') return [n.toFixed(2), 'Close']
              return [n.toFixed(2), 'Flagged trade']
            }}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}
