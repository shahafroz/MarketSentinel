import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { ErrorBox, Score, Status } from '../components/ui'
import type { AlertSummary, Page } from '../types'

const PATTERNS = [
  ['WASH_TRADING', 'Wash trading'],
  ['SPOOFING', 'Spoofing'],
  ['MARKING_THE_CLOSE', 'Marking the close'],
  ['PUMP_AND_DUMP', 'Pump and dump'],
  ['FRONT_RUNNING', 'Front-running'],
  ['INSIDER_BEFORE_NEWS', 'Insider before news'],
]

export default function AlertsPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const status = params.get('status') ?? ''
  const pattern = params.get('pattern') ?? ''
  const symbol = params.get('symbol') ?? ''
  const page = Number(params.get('page') ?? 0)

  const qs = new URLSearchParams({ page: String(page), size: '25' })
  if (status) qs.set('status', status)
  if (pattern) qs.set('pattern', pattern)
  if (symbol) qs.set('symbol', symbol)
  const alerts = useQuery({ queryKey: ['alerts', qs.toString()], queryFn: () => api<Page<AlertSummary>>(`/alerts?${qs}`) })

  function set(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setParams(next)
  }

  const data = alerts.data
  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Alert queue</h1>
          <div className="muted">Highest risk first. Open an alert to see the evidence and start a case.</div>
        </div>
        <div className="row">
          <select value={status} onChange={(e) => set('status', e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            <option value="NEW">New</option>
            <option value="IN_REVIEW">In review</option>
            <option value="CLOSED">Closed</option>
          </select>
          <select value={pattern} onChange={(e) => set('pattern', e.target.value)} aria-label="Pattern">
            <option value="">All patterns</option>
            {PATTERNS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <input placeholder="Symbol" value={symbol} onChange={(e) => set('symbol', e.target.value.toUpperCase())} style={{ width: 110 }} />
        </div>
      </div>
      <ErrorBox error={alerts.error} />
      <div className="card table-wrap" style={{ padding: 0 }}>
        <table>
          <thead>
            <tr><th className="num">Risk</th><th>Alert</th><th>Pattern</th><th>Symbol</th><th>Accounts</th><th>Trade date</th><th>Status</th></tr>
          </thead>
          <tbody>
            {data?.items.map((a) => (
              <tr key={a.id} className="clickable" onClick={() => navigate(`/alerts/${a.id}`)}>
                <td className="num"><Score value={a.score} /></td>
                <td style={{ maxWidth: 420 }}>{a.title}</td>
                <td><span className="badge">{a.patternLabel}</span></td>
                <td className="mono">{a.symbol}</td>
                <td className="mono small">{a.accounts.join(', ')}</td>
                <td>{a.tradeDate}</td>
                <td><Status value={a.status} /></td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr><td colSpan={7} className="muted" style={{ padding: 24, textAlign: 'center' }}>No alerts match. Run a simulated market day from the overview page.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {data && pages > 1 && (
        <div className="row" style={{ marginTop: 12 }}>
          <button disabled={page === 0} onClick={() => set('page', String(page - 1))}>Previous</button>
          <span className="muted">Page {page + 1} of {pages} · {data.total} alerts</span>
          <button disabled={page + 1 >= pages} onClick={() => { const n = new URLSearchParams(params); n.set('page', String(page + 1)); setParams(n) }}>Next</button>
        </div>
      )}
    </>
  )
}
