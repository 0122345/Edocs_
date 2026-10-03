import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ClipboardClock,
  Eye,
  FilePenLine,
  FileSignature,
  Gauge,
  KeyRound,
  Link2,
  ListFilter,
  Lock,
  Search,
  ShieldCheck,
  TrendingDown,
  TrendingUp,
  TriangleAlert,
  CircleAlert,
  BadgeCheck,
  FileText,
  FilePlus,
  Share2,
} from 'lucide-react'
import { Guarded } from '../components/overlay'
import { RoutingRulesModal } from '../components/modals'
import { useAuth } from '../state/auth'
import { AppShell } from '../layouts/AppShell'
import { Badge, CardHeader, ErrorNote, Loading } from '../components/ui'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { cn } from '../lib/cn'
import type { AuditEventKind, AuditLogEntry, Kpi, RiskBand } from '../data/types'

const kpiIcon = { velocity: Gauge, pending: ClipboardClock, compliance: ShieldCheck, volume: FileText } as const
const deltaIcon = { velocity: TrendingDown, pending: CircleAlert, compliance: BadgeCheck, volume: TrendingUp } as const

function KpiCard({ kpi }: { kpi: Kpi }) {
  const Icon = kpiIcon[kpi.id as keyof typeof kpiIcon] ?? FileText
  const Delta = deltaIcon[kpi.id as keyof typeof deltaIcon] ?? TrendingUp
  return (
    <article className="card flex flex-col p-5">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-sm text-ink-2">{kpi.label}</h3>
        <Icon className="size-[18px] text-muted" strokeWidth={1.75} aria-hidden="true" />
      </div>
      <p className="mt-4 text-[2.25rem] leading-none font-semibold tracking-tight tabular-nums">
        {kpi.value}
        {kpi.unit && <span className="ml-1.5 text-lg font-normal text-ink-2">{kpi.unit}</span>}
      </p>
      <p className={cn('mt-3 flex items-center gap-1.5 text-sm', kpi.deltaTone === 'amber' ? 'text-amber' : kpi.deltaTone === 'alert' ? 'text-alert' : 'text-seal')}>
        <Delta className="size-4" aria-hidden="true" /> {kpi.delta}
      </p>
      <p className="mt-auto pt-4 text-xs text-muted">{kpi.footnote}</p>
    </article>
  )
}

const bandColor: Record<RiskBand['level'], string> = { low: 'bg-seal', moderate: 'bg-amber', high: 'bg-alert' }

function RiskDistribution({ bands }: { bands: Record<'weekly' | 'monthly', RiskBand[]> }) {
  const [period, setPeriod] = useState<'weekly' | 'monthly'>('monthly')
  return (
    <section className="card p-6" aria-labelledby="risk-title">
      <CardHeader
        title={<span id="risk-title">Risk scoring distribution</span>}
        description="AI clause analysis across active enterprise contracts"
        actions={
          <div role="group" aria-label="Period" className="flex rounded-md bg-well p-0.5 text-sm">
            {(['weekly', 'monthly'] as const).map((p) => (
              <button
                key={p}
                type="button"
                aria-pressed={period === p}
                onClick={() => setPeriod(p)}
                className={cn('rounded px-3 py-1 capitalize transition-colors', period === p ? 'bg-ink text-white' : 'text-ink-2 hover:text-ink')}
              >
                {p}
              </button>
            ))}
          </div>
        }
      />
      <ul className="mt-8 flex flex-col gap-6">
        {bands[period].map((b) => (
          <li key={b.level}>
            <div className="flex items-center justify-between gap-3 text-sm">
              <span className="flex items-center gap-2">
                <span className={cn('size-2.5 rounded-full', bandColor[b.level])} aria-hidden="true" />
                {b.label}
              </span>
              <span className="font-medium tabular-nums whitespace-nowrap">
                {b.percent}% <span className="font-normal text-muted">({b.docs} docs)</span>
              </span>
            </div>
            <div className="mt-2 h-2.5 overflow-hidden rounded-full bg-well" role="meter" aria-valuenow={b.percent} aria-valuemin={0} aria-valuemax={100} aria-label={b.label}>
              <div key={period} className={cn('bar-fill h-full rounded-full', bandColor[b.level])} style={{ width: `${b.percent}%` }} />
            </div>
          </li>
        ))}
      </ul>
      <div className="mt-8 flex flex-wrap items-center justify-between gap-2 border-t border-line pt-4 text-xs text-muted">
        <span>Last AI scan finished 14 minutes ago</span>
        <Link to="/compliance#risk" className="font-medium text-ink underline-offset-4 hover:underline">
          View full risk assessment
        </Link>
      </div>
    </section>
  )
}

function Bottlenecks({ items }: { items: { stage: string; avgDays: number; note: string }[] }) {
  const [rulesOpen, setRulesOpen] = useState(false)
  return (
    <section className="card flex flex-col p-6" aria-labelledby="bottleneck-title">
      <div className="flex items-start justify-between gap-3">
        <h2 id="bottleneck-title" className="text-lg font-semibold tracking-tight sm:text-xl">Workflow bottlenecks</h2>
        <TriangleAlert className="size-5 text-ink-2" aria-hidden="true" />
      </div>
      <p className="mt-2 text-sm text-ink-2">Approval stages with the longest average wait.</p>
      <ul className="mt-5 flex flex-col gap-3">
        {items.map((b) => (
          <li key={b.stage} className="rounded-md border border-line bg-canvas p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">{b.stage}</h3>
              <Badge tone={b.avgDays > 3 ? 'alert' : 'amber'}>{b.avgDays} days avg</Badge>
            </div>
            <p className="mt-1.5 text-sm text-ink-2">{b.note}</p>
          </li>
        ))}
      </ul>
      <div className="mt-auto pt-5">
        <Guarded permission="workflow:manage" className="btn btn-quiet w-full" onClick={() => setRulesOpen(true)}>
          Optimize routing rules
        </Guarded>
      </div>
      <RoutingRulesModal open={rulesOpen} onClose={() => setRulesOpen(false)} />
    </section>
  )
}

const eventIcon: Record<AuditEventKind, typeof Eye> = {
  signature: FileSignature,
  redline: FilePenLine,
  view: Eye,
  'key-rotation': KeyRound,
  anchor: Link2,
  'legal-hold': Lock,
  create: FilePlus,
  share: Share2,
}
const eventTint: Record<AuditEventKind, string> = {
  signature: 'text-seal',
  redline: 'text-amber',
  view: 'text-ink-2',
  'key-rotation': 'text-alert',
  anchor: 'text-info',
  'legal-hold': 'text-amber',
  create: 'text-info',
  share: 'text-info',
}
const statusTone = { verified: 'seal', pending: 'amber', failed: 'alert' } as const

const PAGE_SIZE = 4

function AuditLogTable({ items, total }: { items: AuditLogEntry[]; total: number }) {
  const [query, setQuery] = useState('')
  const [onlyIssues, setOnlyIssues] = useState(false)
  const [page, setPage] = useState(1)
  const canReadAudit = useAuth().can('audit:read')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return items.filter(
      (e) =>
        (!onlyIssues || e.status !== 'verified') &&
        (!q || [e.event, e.document, e.actor, e.origin, e.txHash].some((v) => v.toLowerCase().includes(q))),
    )
  }, [items, query, onlyIssues])

  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const current = Math.min(page, pages)
  const rows = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)
  const isFiltered = query.trim() !== '' || onlyIssues

  return (
    <section className="card overflow-hidden" aria-labelledby="logs-title">
      <div className="flex flex-col gap-4 p-6 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 id="logs-title" className="text-lg font-semibold tracking-tight sm:text-xl">Recent audit logs & blockchain proofs</h2>
          <p className="mt-1 text-sm text-ink-2">Cryptographic anchors that prove each document hasn't changed.</p>
        </div>
        <div className="flex gap-2">
          <label className="relative flex-1 md:w-64">
            <span className="sr-only">Search audit logs</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
            <input
              type="search"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(1)
              }}
              placeholder="Search audit logs"
              className="field pl-9"
            />
          </label>
          <button
            type="button"
            aria-pressed={onlyIssues}
            onClick={() => {
              setOnlyIssues((v) => !v)
              setPage(1)
            }}
            className={cn('btn', onlyIssues ? 'btn-primary' : 'btn-ghost')}
            title="Show only pending or failed entries"
          >
            <ListFilter className="size-4" /> {onlyIssues ? 'Issues only' : 'Filter'}
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead className="border-y border-line bg-well text-xs font-semibold text-ink-2">
            <tr>
              <th scope="col" className="px-6 py-3.5">Event</th>
              <th scope="col" className="px-4 py-3.5">Document</th>
              <th scope="col" className="px-4 py-3.5">Actor / IP address</th>
              <th scope="col" className="px-4 py-3.5">Timestamp</th>
              <th scope="col" className="px-4 py-3.5">Blockchain tx hash</th>
              <th scope="col" className="px-6 py-3.5 text-right">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((e) => {
              const Icon = eventIcon[e.kind]
              return (
                <tr key={e.id} className="hover:bg-canvas">
                  <td className="px-6 py-4">
                    <span className="flex items-center gap-2.5">
                      <Icon className={cn('size-4 shrink-0', eventTint[e.kind])} aria-hidden="true" />
                      {e.event}
                    </span>
                  </td>
                  <td className="px-4 py-4 font-medium break-all">
                    {e.documentId && canReadAudit ? (
                      <Link to={`/archive?doc=${e.documentId}`} className="underline-offset-4 hover:underline">{e.document}</Link>
                    ) : (
                      e.document
                    )}
                  </td>
                  <td className="px-4 py-4">
                    <span className="block">{e.actor}</span>
                    <span className="hash text-xs text-muted">{e.origin}</span>
                  </td>
                  <td className="px-4 py-4 whitespace-nowrap text-ink-2">{e.timestamp}</td>
                  <td className="hash px-4 py-4 text-ink-2">{e.txHash}</td>
                  <td className="px-6 py-4 text-right">
                    <Badge tone={statusTone[e.status]} className="capitalize">{e.status}</Badge>
                  </td>
                </tr>
              )
            })}
            {rows.length === 0 && (
              <tr>
                <td colSpan={6} className="px-6 py-12 text-center text-ink-2">
                  No log entries match “{query}”. Try a document name, email address or hash.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col gap-3 border-t border-line bg-canvas px-6 py-4 text-xs text-ink-2 sm:flex-row sm:items-center sm:justify-between">
        <p>
          Showing {rows.length} of {isFiltered ? `${filtered.length} matching` : total.toLocaleString()} log entries
        </p>
        <nav aria-label="Pagination" className="flex items-center gap-1">
          <button type="button" className="btn px-3 py-1.5 text-xs hover:bg-well" disabled={current === 1} onClick={() => setPage(current - 1)}>
            Previous
          </button>
          {Array.from({ length: pages }, (_, i) => i + 1).map((n) => (
            <button
              key={n}
              type="button"
              aria-current={n === current ? 'page' : undefined}
              onClick={() => setPage(n)}
              className={cn('size-8 rounded text-xs font-medium tabular-nums', n === current ? 'bg-ink text-white' : 'hover:bg-well')}
            >
              {n}
            </button>
          ))}
          <button type="button" className="btn px-3 py-1.5 text-xs hover:bg-well" disabled={current === pages} onClick={() => setPage(current + 1)}>
            Next
          </button>
        </nav>
      </div>
    </section>
  )
}

export default function DashboardPage() {
  const dash = useResource(api.getDashboard)
  const logs = useResource(api.getAuditLogs)

  return (
    <AppShell>
      <main className="mx-auto flex max-w-[1240px] flex-col gap-6 px-4 py-8 sm:px-8">
        <h1 className="sr-only">Dashboard</h1>
        {dash.error && <ErrorNote message={dash.error} />}
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {dash.data
            ? dash.data.kpis.map((k) => <KpiCard key={k.id} kpi={k} />)
            : [0, 1, 2, 3].map((i) => (
                <div key={i} className="card p-5">
                  <Loading />
                </div>
              ))}
        </div>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          {dash.data ? (
            <>
              <RiskDistribution bands={dash.data.riskBands} />
              <Bottlenecks items={dash.data.bottlenecks} />
            </>
          ) : (
            <>
              <div className="card p-6"><Loading /></div>
              <div className="card p-6"><Loading /></div>
            </>
          )}
        </div>

        {logs.error && <ErrorNote message={logs.error} />}
        {logs.data ? <AuditLogTable items={logs.data.items} total={logs.data.total} /> : <div className="card p-6"><Loading /></div>}
      </main>
    </AppShell>
  )
}
