import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { ErrorBox, Score, Status } from '../components/ui'
import { nyDateTime } from '../format'
import type { CaseView } from '../types'

export default function CasesPage() {
  const navigate = useNavigate()
  const cases = useQuery({ queryKey: ['cases'], queryFn: () => api<CaseView[]>('/cases') })
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Cases</h1>
          <div className="muted">Investigations opened from alerts, most recently changed first.</div>
        </div>
      </div>
      <ErrorBox error={cases.error} />
      <div className="card table-wrap" style={{ padding: 0 }}>
        <table>
          <thead><tr><th>Case</th><th className="num">Risk</th><th>Alert</th><th>Assignee</th><th>Status</th><th>Updated</th></tr></thead>
          <tbody>
            {cases.data?.map((c) => (
              <tr key={c.id} className="clickable" onClick={() => navigate(`/cases/${c.id}`)}>
                <td className="mono">#{c.id}</td>
                <td className="num"><Score value={c.alert.score} /></td>
                <td>{c.alert.title}</td>
                <td className="small">{c.assignee ?? '–'}</td>
                <td><Status value={c.status} /></td>
                <td className="small">{nyDateTime(c.updatedAt)}</td>
              </tr>
            ))}
            {cases.data?.length === 0 && (
              <tr><td colSpan={6} className="muted" style={{ padding: 24, textAlign: 'center' }}>No cases yet. Open an alert and click "Start investigation".</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}
