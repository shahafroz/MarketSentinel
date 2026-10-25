import { lazy, Suspense } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import LoginPage from './pages/LoginPage'

// Pages load on demand, so the chart library is only downloaded when an alert is opened.
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const AlertsPage = lazy(() => import('./pages/AlertsPage'))
const AlertDetailPage = lazy(() => import('./pages/AlertDetailPage'))
const CasesPage = lazy(() => import('./pages/CasesPage'))
const CaseDetailPage = lazy(() => import('./pages/CaseDetailPage'))
const EdgarPage = lazy(() => import('./pages/EdgarPage'))
const AuditPage = lazy(() => import('./pages/AuditPage'))
const AdminPage = lazy(() => import('./pages/AdminPage'))
const BillingPage = lazy(() => import('./pages/BillingPage'))

export default function App() {
  const { user, loading, logout, can } = useAuth()

  if (loading) return <div className="login-page muted">Loading…</div>
  if (!user) return <LoginPage />

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          MarketSentinel
        </div>
        <nav className="nav">
          <NavLink to="/" end>Overview</NavLink>
          <NavLink to="/alerts">Alerts</NavLink>
          <NavLink to="/cases">Cases</NavLink>
          <NavLink to="/edgar">SEC filings</NavLink>
          {can('AUDITOR', 'SUPERVISOR', 'ADMIN') && <NavLink to="/audit">Audit log</NavLink>}
          {can('ADMIN') && <NavLink to="/admin">Team &amp; API keys</NavLink>}
          <NavLink to="/billing">Plan &amp; billing</NavLink>
        </nav>
        <div className="sidebar-foot">
          <div style={{ fontWeight: 600 }}>{user.firmName}</div>
          <div className="muted">{user.email}</div>
          <div className="row" style={{ marginTop: 8 }}>
            <span className="badge accent">{user.role}</span>
            <button onClick={logout} style={{ padding: '4px 10px' }}>Sign out</button>
          </div>
        </div>
      </aside>
      <main className="main">
        <Suspense fallback={<p className="muted">Loading…</p>}>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/alerts/:id" element={<AlertDetailPage />} />
          <Route path="/cases" element={<CasesPage />} />
          <Route path="/cases/:id" element={<CaseDetailPage />} />
          <Route path="/edgar" element={<EdgarPage />} />
          <Route path="/audit" element={<AuditPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/billing" element={<BillingPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
      </main>
    </div>
  )
}
