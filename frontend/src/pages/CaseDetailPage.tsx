import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { ErrorBox, Score, Status } from '../components/ui'
import { humanize, nyDateTime } from '../format'
import type { CaseStatus, CaseView } from '../types'

export default function CaseDetailPage() {
  const { id } = useParams()
  const { can } = useAuth()
  const qc = useQueryClient()
  const kase = useQuery({ queryKey: ['case', id], queryFn: () => api<CaseView>(`/cases/${id}`) })
  const [note, setNote] = useState('')
  // null = the supervisor has not edited the draft yet, so the box shows the latest AI draft.
  const [editedSummary, setEditedSummary] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [outcome, setOutcome] = useState<CaseStatus>('CLOSED_FALSE_POSITIVE')
  const [assignee, setAssignee] = useState('')

  const action = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => api<CaseView>(`/cases/${id}/${path}`, { method: 'POST', body }),
    onSuccess: (c, vars) => {
      if (vars.path === 'ai-draft') setEditedSummary(null)
      qc.setQueryData(['case', id], c)
      qc.invalidateQueries({ queryKey: ['cases'] })
      qc.invalidateQueries({ queryKey: ['alerts'] })
      qc.invalidateQueries({ queryKey: ['stats'] })
    },
  })

  if (kase.error) return <ErrorBox error={kase.error} />
  if (!kase.data) return <p className="muted">Loading…</p>
  const c = kase.data
  const closed = c.status === 'CLOSED_FALSE_POSITIVE' || c.status === 'CLOSED_REPORTED'
  const worker = can('ANALYST', 'SUPERVISOR', 'ADMIN') && !closed
  const lead = can('SUPERVISOR', 'ADMIN') && !closed
  const summary = editedSummary ?? c.aiDraft ?? ''

  return (
    <>
      <div className="page-head">
        <div>
          <div className="muted small"><Link to="/cases">Cases</Link> / #{c.id}</div>
          <h1>Case #{c.id}: {c.alert.patternLabel} in {c.alert.symbol}</h1>
          <div className="row">
            <Status value={c.status} />
            <span className="muted">Assigned to {c.assignee ?? 'nobody'}</span>
            <Link to={`/alerts/${c.alert.id}`}>View alert #{c.alert.id} and its charts →</Link>
          </div>
        </div>
        {worker && c.status === 'OPEN' && (
          <button onClick={() => action.mutate({ path: 'escalate' })} disabled={action.isPending}>Escalate to supervisor</button>
        )}
      </div>
      <ErrorBox error={action.error} />

      <div className="grid grid-main">
        <div className="stack">
          <div className="card">
            <div className="row spread"><h2 style={{ margin: 0 }}>{c.alert.title}</h2><Score value={c.alert.score} /></div>
            <p className="muted small">Accounts: <span className="mono">{c.alert.accounts.join(', ')}</span> · trade date {c.alert.tradeDate}</p>
          </div>

          <div className="card stack">
            <div className="row spread">
              <h2 style={{ margin: 0 }}>Investigation summary</h2>
              {worker && (
                <button onClick={() => action.mutate({ path: 'ai-draft' })} disabled={action.isPending}>
                  {action.isPending && action.variables?.path === 'ai-draft' ? 'Drafting…' : c.aiDraft ? 'Redraft with AI' : 'Draft with AI'}
                </button>
              )}
            </div>
            {c.approvedSummary ? (
              <>
                <div className="success small">Approved by {c.approvedBy}</div>
                <pre className="draft">{c.approvedSummary}</pre>
              </>
            ) : c.aiDraft ? (
              <>
                <div className="muted small">Draft by {c.aiModel}. A supervisor must review and approve it; edits are allowed and audited.</div>
                {lead ? (
                  <>
                    <textarea value={summary} onChange={(e) => setEditedSummary(e.target.value)} style={{ minHeight: 220 }} />
                    <div><button className="primary" onClick={() => action.mutate({ path: 'approve-summary', body: { text: summary } })} disabled={action.isPending || !summary.trim()}>Approve summary</button></div>
                  </>
                ) : (
                  <pre className="draft">{c.aiDraft}</pre>
                )}
              </>
            ) : (
              <p className="muted" style={{ margin: 0 }}>No summary yet. The AI drafts one from the alert's evidence and the notes below; a human always approves it.</p>
            )}
          </div>

          <div className="card stack">
            <h2 style={{ margin: 0 }}>Notes</h2>
            {c.notes.length === 0 && <p className="muted" style={{ margin: 0 }}>No notes yet.</p>}
            {c.notes.map((n) => (
              <div key={n.id} style={{ borderBottom: '1px solid var(--border)', paddingBottom: 8 }}>
                <div className="muted small">{n.author} · {nyDateTime(n.createdAt)}</div>
                <div style={{ whiteSpace: 'pre-wrap' }}>{n.body}</div>
              </div>
            ))}
            {worker && (
              <>
                <textarea placeholder="What did you check? What did you find?" value={note} onChange={(e) => setNote(e.target.value)} />
                <div>
                  <button onClick={() => action.mutate({ path: 'notes', body: { body: note } }, { onSuccess: () => setNote('') })} disabled={!note.trim() || action.isPending}>Add note</button>
                </div>
              </>
            )}
          </div>
        </div>

        <div className="stack">
          {worker && (
            <div className="card stack">
              <h2 style={{ margin: 0 }}>Close the case</h2>
              <label>
                Outcome
                <select value={outcome} onChange={(e) => setOutcome(e.target.value as CaseStatus)}>
                  <option value="CLOSED_FALSE_POSITIVE">False positive</option>
                  {can('SUPERVISOR', 'ADMIN') && <option value="CLOSED_REPORTED">Suspicious: reported</option>}
                </select>
              </label>
              <label>
                Reason
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Why this outcome?" />
              </label>
              <button className="primary" onClick={() => action.mutate({ path: 'close', body: { outcome, reason } })} disabled={!reason.trim() || action.isPending}>Close case</button>
              {!can('SUPERVISOR', 'ADMIN') && <p className="muted small" style={{ margin: 0 }}>Only a supervisor can close a case as reported. Escalate it instead.</p>}
            </div>
          )}
          {lead && (
            <div className="card stack">
              <h2 style={{ margin: 0 }}>Reassign</h2>
              <input placeholder="analyst@firm.com" value={assignee} onChange={(e) => setAssignee(e.target.value)} />
              <button onClick={() => action.mutate({ path: 'assign', body: { assignee } })} disabled={!assignee.trim() || action.isPending}>Assign</button>
            </div>
          )}
          {closed && (
            <div className="card">
              <h2>Outcome</h2>
              <Status value={c.status} />
              <p>{c.closedReason}</p>
            </div>
          )}
          <div className="card">
            <h2>Audit trail</h2>
            <ul className="timeline">
              {c.timeline.map((t, i) => (
                <li key={i}>
                  <strong>{humanize(t.action.toLowerCase())}</strong>
                  <div className="muted small">{t.actor} · {nyDateTime(t.at)}</div>
                  {t.details && <div className="small">{t.details}</div>}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </>
  )
}
