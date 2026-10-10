import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CircleCheck, LoaderCircle, TriangleAlert } from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { ErrorNote, Loading, PageHeader } from '../components/ui'
import { Guarded } from '../components/overlay'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAction } from '../state/toast'
import { cn } from '../lib/cn'

/** What to do about a failing control, and where. */
const remediation: Record<string, { text: string; to: string; label: string }> = {
  cc5: { text: 'Some archived contracts have no retention period. Set the workspace default.', to: '/settings', label: 'Open settings' },
  cc6: { text: 'Review who holds administrator and legal roles.', to: '/settings#members', label: 'Review members' },
}

export default function CompliancePage() {
  const { data, error } = useResource(api.getComplianceControls)
  const dashboard = useResource(api.getDashboard)
  const run = useAction()
  const [running, setRunning] = useState(false)
  const passing = data?.filter((c) => c.status === 'pass').length ?? 0

  async function runChecks() {
    setRunning(true)
    await run(() => api.runComplianceChecks(), (r) => `Checks finished. ${r.filter((c) => c.status === 'pass').length} of ${r.length} controls pass.`)
    setRunning(false)
  }

  return (
    <AppShell breadcrumb="Compliance">
      <main className="mx-auto flex max-w-[1240px] flex-col gap-6 px-4 py-8 sm:px-8">
        <PageHeader
          title="Compliance"
          description="Automated checks against eIDAS, ESIGN / UETA, GDPR, SOC 2 and ISO 27001 controls. Checks also run every six hours."
          actions={
            <Guarded permission="compliance:run" className="btn btn-primary" onClick={runChecks} disabled={running}>
              {running && <LoaderCircle className="size-4 animate-spin" />} {running ? 'Running checks…' : 'Run checks now'}
            </Guarded>
          }
        />
        {error && <ErrorNote message={error} />}
        <section className="card overflow-hidden" aria-labelledby="controls-title">
          <div className="flex items-baseline justify-between gap-3 p-6">
            <h2 id="controls-title" className="text-lg font-semibold tracking-tight">Controls</h2>
            {data && <p className="text-sm text-ink-2">{passing} of {data.length} passing</p>}
          </div>
          {!data ? <div className="px-6 pb-6"><Loading /></div> : (
            <ul className={cn('divide-y divide-line border-t border-line', running && 'opacity-60')}>
              {data.map((c) => (
                <li key={c.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-6 py-4">
                  {c.status === 'pass'
                    ? <CircleCheck className="size-5 shrink-0 text-seal" aria-label="Passing" />
                    : <TriangleAlert className="size-5 shrink-0 text-amber" aria-label="Needs attention" />}
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{c.name}</p>
                    <p className="hash text-xs text-muted">{c.framework}</p>
                    {c.status === 'warn' && remediation[c.id] && <p className="mt-1 text-sm text-ink-2">{remediation[c.id]!.text}</p>}
                  </div>
                  <div className="flex items-center gap-3">
                    <p className="text-sm text-ink-2">Checked {c.checked}</p>
                    {c.status === 'warn' && remediation[c.id] && (
                      <Link to={remediation[c.id]!.to} className="btn btn-ghost px-3 py-1.5 text-xs">{remediation[c.id]!.label}</Link>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section id="risk" className="card scroll-mt-24 p-6" aria-labelledby="risk-title">
          <h2 id="risk-title" className="text-lg font-semibold tracking-tight">Full risk assessment</h2>
          <p className="mt-1 text-sm text-ink-2">AI clause analysis of active contracts this month, by risk band.</p>
          <dl className="mt-5 grid gap-3 sm:grid-cols-3">
            {dashboard.data?.riskBands.monthly.map((b) => (
              <div key={b.level} className="rounded-md border border-line bg-canvas p-4">
                <dt className="text-sm text-ink-2">{b.label}</dt>
                <dd className="mt-1 text-2xl font-semibold tabular-nums">{b.docs} <span className="text-sm font-normal text-muted">docs, {b.percent}%</span></dd>
              </div>
            ))}
          </dl>
          <p className="mt-4 text-sm">
            <Link to="/documents?status=in_review" className="font-medium underline-offset-4 hover:underline">Review documents waiting on legal</Link>
          </p>
        </section>
      </main>
    </AppShell>
  )
}
