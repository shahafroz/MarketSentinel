import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api'
import { ErrorBox } from '../components/ui'
import { nyDateTime } from '../format'
import type { Role } from '../types'

interface UserRow { id: string; email: string; role: Role; createdAt: string }
interface KeyRow { id: string; name: string; prefix: string; createdAt: string; revokedAt: string | null }

export default function AdminPage() {
  const qc = useQueryClient()
  const users = useQuery({ queryKey: ['users'], queryFn: () => api<UserRow[]>('/admin/users') })
  const keys = useQuery({ queryKey: ['keys'], queryFn: () => api<KeyRow[]>('/admin/api-keys') })
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('ANALYST')
  const [keyName, setKeyName] = useState('')
  const [newKey, setNewKey] = useState<string | null>(null)

  const addUser = useMutation({
    mutationFn: () => api<UserRow>('/admin/users', { method: 'POST', body: { email, password, role } }),
    onSuccess: () => { setEmail(''); setPassword(''); qc.invalidateQueries({ queryKey: ['users'] }) },
  })
  const addKey = useMutation({
    mutationFn: () => api<{ apiKey: string }>('/admin/api-keys', { method: 'POST', body: { name: keyName } }),
    onSuccess: (k) => { setNewKey(k.apiKey); setKeyName(''); qc.invalidateQueries({ queryKey: ['keys'] }) },
  })
  const revoke = useMutation({
    mutationFn: (id: string) => api(`/admin/api-keys/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['keys'] }),
  })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Team &amp; API keys</h1>
          <div className="muted">Who can sign in, with which role, and which systems may send trades.</div>
        </div>
      </div>
      <div className="grid grid-2">
        <div className="card stack">
          <h2 style={{ margin: 0 }}>Team</h2>
          <ErrorBox error={users.error} />
          <table>
            <thead><tr><th>Email</th><th>Role</th><th>Since</th></tr></thead>
            <tbody>{users.data?.map((u) => <tr key={u.id}><td>{u.email}</td><td><span className="badge accent">{u.role}</span></td><td className="small">{nyDateTime(u.createdAt)}</td></tr>)}</tbody>
          </table>
          <h3 style={{ margin: 0 }}>Add a team member</h3>
          <div className="row">
            <input type="email" placeholder="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            <input type="password" placeholder="password (10+ characters)" value={password} onChange={(e) => setPassword(e.target.value)} />
            <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
              <option value="ANALYST">Analyst</option>
              <option value="SUPERVISOR">Supervisor</option>
              <option value="AUDITOR">Auditor</option>
              <option value="ADMIN">Admin</option>
            </select>
            <button className="primary" onClick={() => addUser.mutate()} disabled={!email || password.length < 10 || addUser.isPending}>Add</button>
          </div>
          <ErrorBox error={addUser.error} />
        </div>

        <div className="card stack">
          <h2 style={{ margin: 0 }}>API keys</h2>
          <p className="muted small" style={{ margin: 0 }}>
            Broker systems send orders and trades to <span className="mono">/api/v1/ingest</span> with the <span className="mono">X-API-Key</span> header.
            Only a hash of each key is stored.
          </p>
          {newKey && (
            <div className="success">
              Copy this key now; it will not be shown again:
              <div className="mono" style={{ wordBreak: 'break-all', marginTop: 6 }}>{newKey}</div>
            </div>
          )}
          <table>
            <thead><tr><th>Name</th><th>Key</th><th>Created</th><th /></tr></thead>
            <tbody>
              {keys.data?.map((k) => (
                <tr key={k.id}>
                  <td>{k.name}</td>
                  <td className="mono">{k.prefix}…</td>
                  <td className="small">{nyDateTime(k.createdAt)}</td>
                  <td>{k.revokedAt ? <span className="badge">Revoked</span> : <button className="danger" onClick={() => revoke.mutate(k.id)}>Revoke</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="row">
            <input placeholder="Key name, e.g. OMS gateway" value={keyName} onChange={(e) => setKeyName(e.target.value)} />
            <button className="primary" onClick={() => addKey.mutate()} disabled={!keyName.trim() || addKey.isPending}>Create key</button>
          </div>
          <ErrorBox error={addKey.error ?? revoke.error} />
        </div>
      </div>
    </>
  )
}
