import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import NetworkGraph from '../components/NetworkGraph'
import OrderTimeline from '../components/OrderTimeline'
import PriceChart from '../components/PriceChart'
import { ErrorBox, Evidence, Score, Status } from '../components/ui'
import { num, nyDateTime, nyTime, price } from '../format'
import type { AlertDetail, CaseView, MarketContext } from '../types'

export default function AlertDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { can } = useAuth()
  const detail = useQuery({ queryKey: ['alert', id], queryFn: () => api<AlertDetail>(`/alerts/${id}`) })
  const isInsider = detail.data?.alert.pattern === 'INSIDER_BEFORE_NEWS'
  const market = useQuery({
    queryKey: ['alert-market', id],
    queryFn: () => api<MarketContext>(`/alerts/${id}/market`),
    enabled: !!detail.data && !isInsider,
  })
  const openCase = useMutation({
    mutationFn: () => api<CaseView>(`/alerts/${id}/case`, { method: 'POST' }),
    onSuccess: (c) => {
      qc.invalidateQueries()
      navigate(`/cases/${c.id}`)
    },
  })

  if (detail.error) return <ErrorBox error={detail.error} />
  if (!detail.data) return <p className="muted">Loading…</p>
  const { alert, reason, evidence, windowStart, windowEnd, caseId } = detail.data

  return (
    <>
      <div className="page-head">
        <div>
          <div className="muted small"><Link to="/alerts">Alerts</Link> / #{alert.id}</div>
          <h1>{alert.title}</h1>
          <div className="row">
            <Score value={alert.score} />
            <span className="badge">{alert.patternLabel}</span>
            <Status value={alert.status} />
            <span className="muted">{alert.symbol} · {alert.tradeDate}</span>
          </div>
        </div>
        <div className="row">
          {caseId ? (
            <Link to={`/cases/${caseId}`} className="button">Open case #{caseId}</Link>
          ) : (
            can('ANALYST', 'SUPERVISOR', 'ADMIN') && alert.status !== 'CLOSED' && (
              <button className="primary" onClick={() => openCase.mutate()} disabled={openCase.isPending}>Start investigation</button>
            )
          )}
        </div>
      </div>
      <ErrorBox error={openCase.error} />

      <div className="grid grid-main">
        <div className="stack">
          <div className="card">
            <h2>Why this was flagged</h2>
            <p style={{ marginTop: 0 }}>{reason}</p>
            <div className="muted small">Window: {nyDateTime(windowStart)} to {nyTime(windowEnd)} ET</div>
          </div>
          {!isInsider && (
            <div className="card">
              <h2>{alert.symbol} on {alert.tradeDate}</h2>
              <p className="muted small" style={{ marginTop: -6 }}>Minute closes and volume. Shaded: the alert window. Red dots: trades by the flagged accounts.</p>
              <ErrorBox error={market.error} />
              {market.data && <PriceChart market={market.data} windowStart={windowStart} windowEnd={windowEnd} />}
            </div>
          )}
          {market.data && (alert.pattern === 'SPOOFING' || alert.pattern === 'FRONT_RUNNING') && (
            <div className="card">
              <h2>Orders by the flagged accounts</h2>
              <OrderTimeline orders={market.data.flaggedOrders} />
            </div>
          )}
          {market.data && market.data.flaggedTrades.length > 0 && (
            <div className="card table-wrap">
              <h2>Flagged trades ({market.data.flaggedTrades.length})</h2>
              <table>
                <thead><tr><th>Time (ET)</th><th>Seller</th><th>Buyer</th><th className="num">Shares</th><th className="num">Price</th></tr></thead>
                <tbody>
                  {market.data.flaggedTrades.slice(0, 40).map((t) => (
                    <tr key={t.tradeRef}><td className="mono">{nyTime(t.time)}</td><td className="mono">{t.seller}</td><td className="mono">{t.buyer}</td><td className="num">{num(t.quantity)}</td><td className="num">{price(t.price)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <div className="stack">
          <div className="card">
            <h2>Evidence</h2>
            <Evidence evidence={evidence} />
          </div>
          {market.data && (
            <div className="card">
              <h2>Who traded with whom</h2>
              <NetworkGraph nodes={market.data.nodes} edges={market.data.edges} flagged={alert.accounts} />
              <p className="muted small" style={{ marginBottom: 0 }}>Arrows go from seller to buyer; thicker means more shares. Flagged accounts are red.</p>
            </div>
          )}
          {isInsider && (
            <div className="card">
              <h2>Source</h2>
              <p className="muted" style={{ marginTop: 0 }}>Real filings from SEC EDGAR: Form 4 (insider transactions) and Form 8-K (material events).</p>
              <Link to="/edgar">View imported filings →</Link>
            </div>
          )}
        </div>
      </div>
    </>
  )
}
