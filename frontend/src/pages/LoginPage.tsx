import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth'
import { ErrorBox } from '../components/ui'

const DEMO_PASSWORD = 'Sentinel#2026'
const DEMO_USERS = [
  { role: 'Analyst', email: 'analyst@demo.marketsentinel.dev', hint: 'works alerts and cases' },
  { role: 'Supervisor', email: 'supervisor@demo.marketsentinel.dev', hint: 'approves and reports' },
  { role: 'Auditor', email: 'auditor@demo.marketsentinel.dev', hint: 'read-only, audit log' },
  { role: 'Admin', email: 'admin@demo.marketsentinel.dev', hint: 'team, API keys, billing' },
]

export default function LoginPage() {
  const { login, signup } = useAuth()
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [firm, setFirm] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    run(() => (mode === 'login' ? login(email, password) : signup(firm, email, password)))
  }

  return (
    <div className="login-page">
      <div className="login-card stack">
        <div className="brand" style={{ padding: 0, fontSize: 20 }}>
          <img src="/favicon.svg" alt="" style={{ width: 32, height: 32 }} />
          MarketSentinel
        </div>
        <p className="muted" style={{ margin: 0 }}>
          Trade surveillance for US broker-dealers. Detects wash trading, spoofing, marking the close, pump and dump,
          front-running and insider buying before news.
        </p>
        <form className="card stack" onSubmit={submit}>
          <h2 style={{ margin: 0 }}>{mode === 'login' ? 'Sign in' : 'Create your firm'}</h2>
          {mode === 'signup' && (
            <label>
              Firm name
              <input value={firm} onChange={(e) => setFirm(e.target.value)} required maxLength={120} />
            </label>
          )}
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={mode === 'signup' ? 10 : 1} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
          </label>
          <ErrorBox error={error} />
          <button className="primary" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create firm (Free plan)'}</button>
          <button type="button" onClick={() => setMode(mode === 'login' ? 'signup' : 'login')} style={{ border: 'none' }}>
            {mode === 'login' ? 'New firm? Create an account' : 'Have an account? Sign in'}
          </button>
        </form>
        {mode === 'login' && (
          <div className="card stack">
            <h3 style={{ margin: 0 }}>Try the demo firm</h3>
            <div className="demo-users">
              {DEMO_USERS.map((u) => (
                <button key={u.email} disabled={busy} onClick={() => run(() => login(u.email, DEMO_PASSWORD))} style={{ textAlign: 'left' }}>
                  {u.role}
                  <div className="muted small" style={{ fontWeight: 400 }}>{u.hint}</div>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
