import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { ErrorBox } from '../components/ui'
import { num } from '../format'
import type { EdgarImportResult, EventRow, InsiderRow } from '../types'

const CODES: Record<string, string> = {
  P: 'Open-market purchase',
  S: 'Open-market sale',
  A: 'Grant / award',
  M: 'Option exercise',
  F: 'Tax withholding',
  G: 'Gift',
  D: 'Disposition to issuer',
}

export default function EdgarPage() {
  const { can } = useAuth()
  const qc = useQueryClient()
  const [tickers, setTickers] = useState('')
  const [days, setDays] = useState(180)
  const insiders = useQuery({ queryKey: ['edgar-insiders'], queryFn: () => api<InsiderRow[]>('/edgar/insider-transactions') })
  const events = useQuery({ queryKey: ['edgar-events'], queryFn: () => api<EventRow[]>('/edgar/material-events') })
  const run = useMutation({
    mutationFn: () =>
      api<EdgarImportResult>('/edgar/import', {
        method: 'POST',
        body: { tickers: tickers.split(/[\s,]+/).filter(Boolean), days },
      }),
    onSuccess: () => qc.invalidateQueries(),
  })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>SEC filings</h1>
          <div className="muted">
            Real data from SEC EDGAR: Form 4 insider transactions and Form 8-K announcements. MarketSentinel flags insiders who bought
            shares in the open market shortly before a price-sensitive 8-K.
          </div>
        </div>
      </div>

      {can('ANALYST', 'SUPERVISOR', 'ADMIN') && (
        <div className="card stack" style={{ marginBottom: 16 }}>
          <h2 style={{ margin: 0 }}>Import from EDGAR</h2>
          <div className="row">
            <label style={{ flex: 1, minWidth: 220 }}>
              Tickers (up to 10)
              <input value={tickers} onChange={(e) => setTickers(e.target.value.toUpperCase())} placeholder="e.g. NVDA, TSLA, PLTR" />
            </label>
            <label>
              Look back (days)
              <input type="number" min={1} max={730} value={days} onChange={(e) => setDays(Number(e.target.value))} style={{ width: 110 }} />
            </label>
            <button className="primary" style={{ alignSelf: 'flex-end' }} disabled={!tickers.trim() || run.isPending} onClick={() => run.mutate()}>
              {run.isPending ? 'Importing from SEC…' : 'Import and screen'}
            </button>
          </div>
          <p className="muted small" style={{ margin: 0 }}>Large companies' insiders mostly sell; open-market purchases are more common at small and mid-size companies.</p>
          <ErrorBox error={run.error} />
          {run.data && (
            <div className="success small">
              {run.data.tickers.map((t) => (
                <div key={t.ticker}>
                  <strong>{t.ticker}</strong>{' '}
                  {t.error ? <span className="error" style={{ padding: '0 6px' }}>{t.error}</span> : `${t.company}: ${t.insiderTransactions} insider transactions from ${t.form4Filings} Form 4s, ${t.eightKs} new 8-Ks`}
                </div>
              ))}
              <div style={{ marginTop: 6 }}>Screen: {run.data.screen.newAlerts} new alert(s). <Link to="/alerts?pattern=INSIDER_BEFORE_NEWS">View insider alerts →</Link></div>
            </div>
          )}
        </div>
      )}

      <div className="grid grid-main">
        <div className="card table-wrap">
          <h2>Insider transactions ({insiders.data?.length ?? 0})</h2>
          <ErrorBox error={insiders.error} />
          <table>
            <thead><tr><th>Date</th><th>Ticker</th><th>Insider</th><th>Type</th><th className="num">Shares</th><th className="num">Price</th></tr></thead>
            <tbody>
              {insiders.data?.slice(0, 200).map((t) => (
                <tr key={`${t.accession}-${t.date}-${t.shares}-${t.code}`}>
                  <td>{t.date}</td>
                  <td className="mono">{t.ticker}</td>
                  <td>{t.owner}<div className="muted small">{t.title}</div></td>
                  <td>{t.code === 'P' ? <span className="score score-mid">{CODES.P}</span> : <span className="small">{CODES[t.code] ?? t.code}</span>}</td>
                  <td className="num">{num(t.shares)}</td>
                  <td className="num">{t.price ? t.price.toFixed(2) : '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="card table-wrap">
          <h2>8-K filings ({events.data?.length ?? 0})</h2>
          <table>
            <thead><tr><th>Filed</th><th>Ticker</th><th>Items</th></tr></thead>
            <tbody>
              {events.data?.slice(0, 100).map((e) => (
                <tr key={e.accession}>
                  <td>{e.filedOn}</td>
                  <td className="mono">{e.ticker}</td>
                  <td>{e.items || '–'} {e.priceSensitive && <span className="badge accent">price-sensitive</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted small">Price-sensitive items: 1.01 material agreement, 1.02 terminated agreement, 2.01 acquisition, 2.02 results, 5.01 change in control, 5.02 officer changes, 8.01 other events.</p>
        </div>
      </div>
    </>
  )
}
