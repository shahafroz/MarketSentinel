import { num, nyTime, price } from '../format'
import type { OrderPoint } from '../types'

/** Each flagged order as a row: when it was placed, how long it lived, and whether it was cancelled. */
export default function OrderTimeline({ orders }: { orders: OrderPoint[] }) {
  if (orders.length === 0) return <p className="muted">No large or cancelled orders from the flagged accounts.</p>
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Placed (ET)</th>
            <th>Account</th>
            <th>Side</th>
            <th className="num">Shares</th>
            <th className="num">Limit</th>
            <th>Outcome</th>
            <th className="num">Lived</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((o) => {
            const lived = o.cancelledAt ? (new Date(o.cancelledAt).getTime() - new Date(o.placedAt).getTime()) / 1000 : null
            return (
              <tr key={o.orderRef}>
                <td className="mono">{nyTime(o.placedAt)}</td>
                <td>{o.account}</td>
                <td>{o.side}</td>
                <td className="num">{num(o.quantity)}</td>
                <td className="num">{price(o.price)}</td>
                <td>{o.status === 'CANCELLED' ? <span className="score score-high">Cancelled</span> : <span className="badge">{o.status}</span>}</td>
                <td className="num">{lived === null ? '–' : lived < 120 ? `${lived.toFixed(0)} s` : `${(lived / 60).toFixed(0)} min`}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
