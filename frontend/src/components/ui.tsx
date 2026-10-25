import { humanize, scoreClass, statusLabel } from '../format'

export function Score({ value }: { value: number }) {
  return <span className={`score ${scoreClass(value)}`}>{value}</span>
}

export function Status({ value }: { value: string }) {
  const accent = value === 'NEW' || value === 'OPEN' || value === 'ESCALATED'
  return <span className={`badge ${accent ? 'accent' : ''}`}>{statusLabel[value] ?? value}</span>
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  return <div className="error">{message}</div>
}

/** Renders the evidence JSON as a readable list; nested rows and long id lists are shown compactly. */
export function Evidence({ evidence }: { evidence: Record<string, unknown> }) {
  return (
    <dl className="kv">
      {Object.entries(evidence).map(([key, value]) => {
        const wide = Array.isArray(value) && (value.length > 3 || typeof value[0] === 'object')
        return (
          <div key={key} className={wide ? 'wide' : ''}>
            <dt>{humanize(key)}</dt>
            <dd className={wide ? 'block' : ''}>{render(key, value)}</dd>
          </div>
        )
      })}
    </dl>
  )
}

const isIdList = (key: string) => /refs$|accessions$/i.test(key)

function render(key: string, value: unknown): React.ReactNode {
  if (Array.isArray(value)) {
    if (isIdList(key)) {
      return (
        <details>
          <summary>{value.length} ids</summary>
          <div className="mono small">{value.join(', ')}</div>
        </details>
      )
    }
    if (value.length > 0 && typeof value[0] === 'object') {
      return (
        <table>
          <tbody>
            {value.map((row, i) => (
              <tr key={i}>
                {Object.entries(row as Record<string, unknown>).map(([k, v]) => (
                  <td key={k} className="small">
                    <span className="muted">{humanize(k)}:</span> {String(v)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )
    }
    return <span className={key.toLowerCase().includes('ref') || key.toLowerCase().includes('accession') ? 'mono' : ''}>{value.join(', ')}</span>
  }
  if (typeof value === 'number' && /share|rise|move/i.test(key) && Math.abs(value) <= 1 && !/shares|Shares$/.test(key)) {
    return `${(value * 100).toFixed(1)} %`
  }
  if (typeof value === 'number') return value.toLocaleString('en-US')
  return String(value)
}
