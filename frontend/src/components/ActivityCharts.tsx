import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { num, usd, usdCompact } from '../format'
import type { DailyActivity, Pattern } from '../types'

const tooltipStyle = { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 13 }
const axis = { stroke: 'var(--muted)', fontSize: 12, tickLine: false }
const shortDay = (iso: string) => new Date(`${iso}T12:00:00Z`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })

/** Total value traded (price × quantity) per market day. The tooltip shows the exact amount from the API. */
export function TradedValueChart({ daily }: { daily: DailyActivity[] }) {
  // Number() only positions the bars; the tooltip formats the exact decimal string.
  const data = daily.map((d) => ({ ...d, value: Number(d.tradedValue) }))
  return (
    <div style={{ width: '100%', height: 240 }}>
      <ResponsiveContainer>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 4 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="day" tickFormatter={shortDay} {...axis} />
          <YAxis tickFormatter={usdCompact} {...axis} width={64} />
          <Tooltip
            cursor={{ fill: 'var(--surface-2)' }}
            contentStyle={tooltipStyle}
            labelFormatter={(d) => shortDay(String(d))}
            formatter={(_v, _n, item) => {
              const d = item.payload as DailyActivity
              return [`${usd(d.tradedValue)} across ${num(d.trades)} trades`, 'Traded value']
            }}
          />
          <Bar dataKey="value" fill="var(--chart-line)" radius={[4, 4, 0, 0]} maxBarSize={36} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

/** Alerts raised per market day, stacked by pattern. Colors are fixed per pattern, so they never shift between days. */
export function AlertsByDayChart({ daily, patterns }: { daily: DailyActivity[]; patterns: { pattern: Pattern; label: string }[] }) {
  const data = daily.map((d) => ({ day: d.day, ...d.alerts }))
  const color = (i: number) => `var(--pattern-${i + 1})`
  return (
    <>
      <div style={{ width: '100%', height: 240 }}>
        <ResponsiveContainer>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 4 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="day" tickFormatter={shortDay} {...axis} />
            <YAxis allowDecimals={false} {...axis} width={32} />
            <Tooltip cursor={{ fill: 'var(--surface-2)' }} contentStyle={tooltipStyle} labelFormatter={(d) => shortDay(String(d))} />
            {patterns.map((p, i) => (
              <Bar
                key={p.pattern}
                dataKey={p.pattern}
                name={p.label}
                stackId="alerts"
                fill={color(i)}
                stroke="var(--surface)"
                strokeWidth={2}
                maxBarSize={36}
                isAnimationActive={false}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="legend">
        {patterns.map((p, i) => (
          <span key={p.pattern}><i style={{ background: color(i) }} />{p.label}</span>
        ))}
      </div>
    </>
  )
}
