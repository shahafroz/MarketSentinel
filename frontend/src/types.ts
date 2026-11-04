/** An exact decimal sent by the API as a string ("12.3400"), so it is never parsed into a float. */
export type Decimal = Intl.StringNumericLiteral

export type Role = 'ANALYST' | 'SUPERVISOR' | 'AUDITOR' | 'ADMIN'

export type Pattern =
  | 'WASH_TRADING'
  | 'SPOOFING'
  | 'MARKING_THE_CLOSE'
  | 'PUMP_AND_DUMP'
  | 'FRONT_RUNNING'
  | 'INSIDER_BEFORE_NEWS'

export type AlertStatus = 'NEW' | 'IN_REVIEW' | 'CLOSED'
export type CaseStatus = 'OPEN' | 'ESCALATED' | 'CLOSED_FALSE_POSITIVE' | 'CLOSED_REPORTED'

export interface User {
  id: string
  email: string
  role: Role
  tenantId: string
  firmName: string
  plan: string
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: User
}

export interface AlertSummary {
  id: number
  pattern: Pattern
  patternLabel: string
  symbol: string
  accounts: string[]
  score: number
  title: string
  status: AlertStatus
  tradeDate: string
  createdAt: string
}

export interface AlertDetail {
  alert: AlertSummary
  reason: string
  evidence: Record<string, unknown>
  windowStart: string
  windowEnd: string
  caseId: number | null
}

export interface Bar {
  time: string
  open: Decimal
  high: Decimal
  low: Decimal
  close: Decimal
  volume: number
}

export interface TradePoint {
  tradeRef: string
  time: string
  price: Decimal
  quantity: number
  buyer: string
  seller: string
}

export interface OrderPoint {
  orderRef: string
  account: string
  side: 'BUY' | 'SELL'
  quantity: number
  price: Decimal
  placedAt: string
  cancelledAt: string | null
  status: string
}

export interface Edge {
  from: string
  to: string
  trades: number
  shares: number
}

export interface MarketContext {
  symbol: string
  date: string
  bars: Bar[]
  flaggedTrades: TradePoint[]
  flaggedOrders: OrderPoint[]
  nodes: string[]
  edges: Edge[]
}

export interface Stats {
  open: number
  inReview: number
  closed: number
  byPattern: { pattern: Pattern; label: string; count: number }[]
  riskiestAccounts: { account: string; openAlerts: number; riskScore: number }[]
  tradesStored: number
  daily: DailyActivity[]
}

export interface DailyActivity {
  day: string
  trades: number
  tradedValue: Decimal
  alerts: Partial<Record<Pattern, number>>
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface CaseView {
  id: number
  status: CaseStatus
  assignee: string | null
  aiDraft: string | null
  aiModel: string | null
  approvedSummary: string | null
  approvedBy: string | null
  closedReason: string | null
  createdAt: string
  updatedAt: string
  alert: AlertSummary
  notes: { id: number; author: string; body: string; createdAt: string }[]
  timeline: { at: string; actor: string; action: string; details: string }[]
}

export interface SimulationResult {
  date: string
  accounts: number
  orders: number
  trades: number
  injected: { pattern: string; symbol: string; accounts: string }[]
  detection: { date: string; findings: number; newAlerts: number }
}

export interface InsiderRow {
  ticker: string
  owner: string
  title: string
  date: string
  code: string
  shares: Decimal
  price: Decimal | null
  acquired: boolean
  accession: string
}

export interface EventRow {
  ticker: string
  filedOn: string
  items: string
  priceSensitive: boolean
  accession: string
}

export interface EdgarImportResult {
  tickers: {
    ticker: string
    company: string | null
    form4Filings: number
    insiderTransactions: number
    eightKs: number
    error: string | null
  }[]
  screen: { findings: number; newAlerts: number }
}

export interface AuditRow {
  id: number
  at: string
  actor: string
  action: string
  entityType: string
  entityId: string
  details: string
}

export interface BillingView {
  plan: string
  tradesThisMonth: number
  monthlyLimit: number
  stripeEnabled: boolean
  hasSubscription: boolean
}
