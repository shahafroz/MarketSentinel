import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api'
import { ErrorBox } from '../components/ui'
import { nyDateTime } from '../format'
import type { AuditRow } from '../types'

export default function AuditPage() {
  const [page, setPage] = useState(0)
  const audit = useQuery({
    queryKey: ['audit', page],
    queryFn: () => api<{ items: AuditRow[]; page: number; total: number }>(`/audit?page=${page}&size=50`),
  })
  const pages = audit.data ? Math.max(1, Math.ceil(audit.data.total / 50)) : 1
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Audit log</h1>
          <div className="muted">Every action, by whom and when. Append-only: the database itself rejects any change or deletion.</div>
        </div>
      </div>
      <ErrorBox error={audit.error} />
      <div className="card table-wrap" style={{ padding: 0 }}>
        <table>
          <thead><tr><th>When</th><th>Who</th><th>Action</th><th>On</th><th>Details</th></tr></thead>
          <tbody>
            {audit.data?.items.map((e) => (
              <tr key={e.id}>
                <td className="small" style={{ whiteSpace: 'nowrap' }}>{nyDateTime(e.at)}</td>
                <td className="small">{e.actor}</td>
                <td className="mono">{e.action}</td>
                <td className="small">{e.entityType} {e.entityId.length > 12 ? `${e.entityId.slice(0, 8)}…` : e.entityId}</td>
                <td className="small">{e.details}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {pages > 1 && (
        <div className="row" style={{ marginTop: 12 }}>
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
          <span className="muted">Page {page + 1} of {pages}</span>
          <button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Older</button>
        </div>
      )}
    </>
  )
}
