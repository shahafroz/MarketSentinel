import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { AlertsByDayChart, TradedValueChart } from '../components/ActivityCharts'
import { ErrorBox, Score } from '../components/ui'
import { num } from '../format'
import type { SimulationResult, Stats } from '../types'

export default function DashboardPage() {
  const { can } = useAuth()
  const qc = useQueryClient()
  const stats = useQuery({ queryKey: ['stats'], queryFn: () => api<Stats>('/alerts/stats') })
  const [date, setDate] = useState('')
  const simulate = useMutation({
    mutationFn: () => api<SimulationResult>(`/simulator/run${date ? `?date=${date}` : ''}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries(),
  })

  const s = stats.data
  const maxPattern = Math.max(1, ...(s?.byPattern.map((p) => p.count) ?? [1]))

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Surveillance overview</h1>
          <div className="muted">Open alerts, what they are about, and which accounts carry the most risk.</div>
        </div>
      </div>
      <ErrorBox error={stats.error} />
      {s && (
        <div className="stack">
          <div className="grid grid-4">
            <div className="card"><div className="kpi-label">New alerts</div><div className="kpi-value">{num(s.open)}</div></div>
            <div className="card"><div className="kpi-label">In review</div><div className="kpi-value">{num(s.inReview)}</div></div>
            <div className="card"><div className="kpi-label">Closed</div><div className="kpi-value">{num(s.closed)}</div></div>
            <div className="card"><div className="kpi-label">Trades monitored</div><div className="kpi-value">{num(s.tradesStored)}</div></div>
          </div>

          {can('ANALYST', 'SUPERVISOR', 'ADMIN') && (
            <div className="card stack">
              <div>
                <h2 style={{ marginBottom: 4 }}>Simulate a trading day</h2>
                <div className="muted">
                  Generates about 2,400 trades across 20 made-up tickers, with five manipulation schemes hidden inside, then runs every detector.
                </div>
              </div>
              <div className="row">
                <label style={{ flexDirection: 'row', alignItems: 'center' }}>
                  Date
                  <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                </label>
                <span className="muted small">Leave empty for the latest weekday not simulated yet.</span>
                <button className="primary" onClick={() => simulate.mutate()} disabled={simulate.isPending}>
                  {simulate.isPending ? 'Simulating…' : 'Run market day'}
                </button>
              </div>
              <ErrorBox error={simulate.error} />
              {simulate.data && (
                <div className="success">
                  {simulate.data.date}: stored {num(simulate.data.trades)} trades and {num(simulate.data.orders)} orders;
                  detection raised <strong>{simulate.data.detection.newAlerts}</strong> new alert(s).{' '}
                  <Link to="/alerts">Open the alert queue →</Link>
                  <div className="small" style={{ marginTop: 6 }}>
                    Hidden in the data: {simulate.data.injected.map((i) => `${i.pattern.replace(/_/g, ' ').toLowerCase()} (${i.symbol})`).join(', ')}.
                  </div>
                </div>
              )}
            </div>
          )}

          {s.daily.length > 0 && (
            <div className="grid grid-2">
              <div className="card">
                <h2>Traded value per day</h2>
                <TradedValueChart daily={s.daily} />
              </div>
              <div className="card">
                <h2>Alerts per day</h2>
                <AlertsByDayChart daily={s.daily} patterns={s.byPattern} />
              </div>
            </div>
          )}

          <div className="grid grid-2">
            <div className="card">
              <h2>Alerts by pattern</h2>
              <div className="stack" style={{ gap: 10 }}>
                {s.byPattern.map((p) => (
                  <Link key={p.pattern} to={`/alerts?pattern=${p.pattern}`} style={{ color: 'inherit' }}>
                    <div className="row spread small"><span>{p.label}</span><strong>{p.count}</strong></div>
                    <div className="bar-track"><div className="bar-fill" style={{ width: `${(100 * p.count) / maxPattern}%` }} /></div>
                  </Link>
                ))}
              </div>
            </div>
            <div className="card">
              <h2>Riskiest accounts</h2>
              {s.riskiestAccounts.length === 0 ? (
                <p className="muted">No open alerts. Simulate a trading day or send trades through the ingest API.</p>
              ) : (
                <table>
                  <thead><tr><th>Account</th><th className="num">Open alerts</th><th className="num">Risk</th></tr></thead>
                  <tbody>
                    {s.riskiestAccounts.map((a) => (
                      <tr key={a.account}><td className="mono">{a.account}</td><td className="num">{a.openAlerts}</td><td className="num"><Score value={a.riskScore} /></td></tr>
                    ))}
                  </tbody>
                </table>
              )}
              <p className="muted small" style={{ marginBottom: 0 }}>Risk = sum of the account's open alert scores, capped at 100.</p>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
