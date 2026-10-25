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
export const num = (n: number) => n.toLocaleString('en-US')
export const usd = (n: number) => n.toLocaleString('en-US', { style: 'currency', currency: 'USD' })

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
