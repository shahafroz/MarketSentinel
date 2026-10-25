import { useMutation, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { ErrorBox } from '../components/ui'
import { num } from '../format'
import type { BillingView } from '../types'

const PLANS = [
  { id: 'FREE', name: 'Free', price: '$0', limit: '10,000 trades / month', blurb: 'Try every detector on a small book.' },
  { id: 'PRO', name: 'Pro', price: '$499 / month', limit: '1,000,000 trades / month', blurb: 'For small broker-dealers in production.' },
  { id: 'ENTERPRISE', name: 'Enterprise', price: 'Custom', limit: 'Unlimited trades', blurb: 'High volume, custom thresholds.' },
]

export default function BillingPage() {
  const { can } = useAuth()
  const [params] = useSearchParams()
  const billing = useQuery({ queryKey: ['billing'], queryFn: () => api<BillingView>('/billing') })
  const checkout = useMutation({
    mutationFn: (plan: string) => api<{ url: string }>('/billing/checkout', { method: 'POST', body: { plan } }),
    onSuccess: (r) => window.location.assign(r.url),
  })
  const portal = useMutation({
    mutationFn: () => api<{ url: string }>('/billing/portal', { method: 'POST' }),
    onSuccess: (r) => window.location.assign(r.url),
  })
  const b = billing.data
  const pct = b && b.monthlyLimit > 0 ? Math.min(100, (100 * b.tradesThisMonth) / b.monthlyLimit) : 0

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Plan &amp; billing</h1>
          <div className="muted">Plans are limited by the number of trades your systems send each month. Simulated data is free.</div>
        </div>
      </div>
      {params.get('status') === 'success' && <div className="success" style={{ marginBottom: 12 }}>Payment received. Your plan updates as soon as Stripe confirms it.</div>}
      <ErrorBox error={billing.error ?? checkout.error ?? portal.error} />
      {b && (
        <div className="stack">
          <div className="card stack">
            <div className="row spread">
              <div><div className="kpi-label">Current plan</div><div className="kpi-value">{b.plan}</div></div>
              {b.hasSubscription && can('ADMIN') && <button onClick={() => portal.mutate()} disabled={portal.isPending}>Manage subscription</button>}
            </div>
            <div className="small">{num(b.tradesThisMonth)} {b.monthlyLimit > 0 ? `of ${num(b.monthlyLimit)}` : ''} trades this month</div>
            {b.monthlyLimit > 0 && <div className="bar-track"><div className="bar-fill" style={{ width: `${pct}%` }} /></div>}
            {!b.stripeEnabled && <p className="muted small" style={{ margin: 0 }}>Stripe is not configured on this server, so upgrades are disabled. Set the Stripe test-mode keys to enable checkout.</p>}
          </div>
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            {PLANS.map((p) => (
              <div key={p.id} className="card stack" style={p.id === b.plan ? { borderColor: 'var(--accent)' } : undefined}>
                <div className="row spread"><h2 style={{ margin: 0 }}>{p.name}</h2>{p.id === b.plan && <span className="badge accent">Current</span>}</div>
                <div className="kpi-value" style={{ fontSize: 22 }}>{p.price}</div>
                <div className="small">{p.limit}</div>
                <div className="muted small">{p.blurb}</div>
                {p.id !== 'FREE' && p.id !== b.plan && can('ADMIN') && (
                  <button className="primary" disabled={!b.stripeEnabled || checkout.isPending} onClick={() => checkout.mutate(p.id)}>Upgrade with Stripe</button>
                )}
              </div>
            ))}
          </div>
          <p className="muted small">Payments run in Stripe test mode: use card 4242 4242 4242 4242. Your plan only changes when Stripe's signed webhook confirms the payment.</p>
        </div>
      )}
    </>
  )
}
