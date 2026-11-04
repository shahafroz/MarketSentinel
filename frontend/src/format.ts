import type { Decimal } from './types'

// All market times are shown in New York time, the US equities market's clock.
const NY = 'America/New_York'

const timeFmt = new Intl.DateTimeFormat('en-US', { timeZone: NY, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
const dateTimeFmt = new Intl.DateTimeFormat('en-US', {
  timeZone: NY,
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export const nyTime = (iso: string | number) => timeFmt.format(new Date(iso))
export const nyDateTime = (iso: string) => `${dateTimeFmt.format(new Date(iso))} ET`
const numFmt = new Intl.NumberFormat('en-US')
const priceFmt = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// Intl formats a numeric string as an exact decimal, so API amounts are shown without passing through a float.
export const num = (n: number | Decimal) => numFmt.format(n)
export const price = (d: Decimal) => priceFmt.format(d)
const usdFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const usdCompactFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 })
export const usd = (d: Decimal) => usdFmt.format(d)
/** Axis ticks only ($1.2M); exact amounts use usd(). */
export const usdCompact = (n: number) => usdCompactFmt.format(n)

export const humanize = (key: string) =>
  key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/^./, (c) => c.toUpperCase())

export function scoreClass(score: number) {
  if (score >= 85) return 'score-high'
  if (score >= 65) return 'score-mid'
  return 'score-low'
}

export const statusLabel: Record<string, string> = {
  NEW: 'New',
  IN_REVIEW: 'In review',
  CLOSED: 'Closed',
  OPEN: 'Open',
  ESCALATED: 'Escalated',
  CLOSED_FALSE_POSITIVE: 'Closed: false positive',
  CLOSED_REPORTED: 'Closed: reported',
}
