import { Link, useParams } from 'react-router-dom'
import {
  BadgeCheck,
  BellRing,
  ChartColumn,
  Check,
  Clock3,
  Download,
  FilePen,
  Fingerprint,
  Gavel,
  Handshake,
  LoaderCircle,
  Lock,
  Pencil,
  Send,
  ShieldAlert,
  ShieldCheck,
  Signature,
  TriangleAlert,
  CircleCheck,
  UserCheck,
} from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { Badge, CardHeader, ErrorNote, Loading, PageHeader } from '../components/ui'
import { Guarded } from '../components/overlay'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../state/auth'
import { useAction } from '../state/toast'
import { downloadJson } from '../lib/download'
import { cn } from '../lib/cn'
import { statusLabel, statusTone } from './DocumentsPage'
import type { ActivityItem, StepStatus, WorkflowStep } from '../data/types'

const stepIcon = [FilePen, Gavel, Handshake, Signature]
const statusText: Record<StepStatus, string> = {
  completed: 'Completed',
  approved: 'Approved',
  verified: 'Verified',
  'in-progress': 'In progress',
  waiting: 'Waiting',
}

function Step({ step }: { step: WorkflowStep }) {
  const Icon = stepIcon[step.id - 1] ?? FilePen
  const active = step.status === 'in-progress'
  const waiting = step.status === 'waiting'
  return (
    <li
      className={cn(
        'relative flex flex-col rounded-md border p-4 pt-5',
        active ? 'border-amber bg-amber-soft/50 ring-1 ring-amber' : 'border-line bg-canvas',
        waiting && 'opacity-60',
      )}
      aria-current={active ? 'step' : undefined}
    >
      <span
        className={cn(
          'absolute -top-3 -left-2 flex size-6 items-center justify-center rounded-full text-xs font-semibold text-white ring-4 ring-paper',
          active ? 'bg-amber' : waiting ? 'bg-line-strong' : 'bg-seal',
        )}
      >
        {step.id}
      </span>
      <div className="flex items-center gap-2">
        <Icon className={cn('size-5', active ? 'text-amber' : waiting ? 'text-muted' : 'text-seal')} strokeWidth={1.75} aria-hidden="true" />
        <h3 className="text-sm font-semibold">{step.title}</h3>
      </div>
      <p className="mt-2 text-sm text-ink-2">{step.description}</p>
      <p
        className={cn('flex items-center justify-between border-t pt-3 text-sm font-medium', active ? 'border-amber/30 text-amber' : waiting ? 'border-line text-muted' : 'border-line text-seal')}
        style={{ marginTop: 'max(1rem, auto)' }}
      >
        {statusText[step.status]}
        {active ? <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : waiting ? <Clock3 className="size-4" aria-hidden="true" /> : <CircleCheck className="size-4" aria-hidden="true" />}
      </p>
    </li>
  )
}

const activityIcon: Record<ActivityItem['icon'], typeof Lock> = { identity: UserCheck, lock: Lock, edit: Pencil }

export default function WorkflowPage() {
  const { id = '' } = useParams()
  const { user, can } = useAuth()
  const run = useAction()
  const { data, error } = useResource(() => api.getWorkflow(id), [id])
  const doc = data?.document

  const myTurn = !!doc && doc.status === 'out_for_signature' && doc.parties.some((p) => p.role === 'signer' && !p.signedAt && (p.email === user?.email || user?.role !== 'signer'))
  const fullySigned = doc?.status === 'signed' || doc?.status === 'archived'

  async function exportEvidence() {
    const pkg = await run(() => api.getEvidencePackage(id), 'Evidence package downloaded.')
    if (pkg) downloadJson(`evidence-${id}.json`, pkg)
  }

  return (
    <AppShell doc={doc ? { id: doc.id, title: doc.title, category: doc.category } : undefined} breadcrumb="Documents">
      <main className="mx-auto flex max-w-[1240px] flex-col gap-6 px-4 py-8 sm:px-8">
        {error && <ErrorNote message={error} />}
        <section className="card p-6">
          {!doc ? <Loading /> : (
            <PageHeader
              divider={false}
              meta={
                <>
                  <Badge tone={statusTone[doc.status]}>
                    {doc.status === 'out_for_signature' ? <><Clock3 className="size-3.5" /> Step {doc.workflowStep}: {doc.workflowStep === 4 ? 'QES eIDAS' : 'counterparty signatures'}</> : statusLabel[doc.status]}
                  </Badge>
                  <span className="hash text-xs text-muted">ID {doc.id.toUpperCase()}</span>
                </>
              }
              title={doc.title}
              description="Multi-party contract signing with biometric identity checks and qualified timestamps."
              actions={
                <>
                  <Guarded permission="audit:read" className="btn btn-ghost" onClick={exportEvidence}>
                    <Download className="size-4" /> Export evidence package
                  </Guarded>
                  {doc.status === 'draft' || doc.status === 'in_review' ? (
                    <Guarded permission="document:share" className="btn btn-primary" onClick={() => run(() => api.sendForSignature(id), 'Sent for signature. Signers get an email now.')}>
                      <Send className="size-4" /> Send for signature
                    </Guarded>
                  ) : fullySigned ? (
                    <span className="btn btn-quiet cursor-default text-seal"><CircleCheck className="size-4" /> Fully signed</span>
                  ) : myTurn && can('document:sign') ? (
                    <Link to={`/sign/${id}`} className="btn btn-seal"><BadgeCheck className="size-4" /> Sign with QES</Link>
                  ) : (
                    <button type="button" className="btn btn-seal" disabled title={can('document:sign') ? 'You have no signature left to give on this document.' : 'Your role cannot sign documents.'}>
                      <BadgeCheck className="size-4" /> Sign with QES
                    </button>
                  )}
                </>
              }
            />
          )}
        </section>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <section className="card p-6" aria-labelledby="flow-title">
            <CardHeader
              title={<span id="flow-title">Signing workflow</span>}
              description="Each stage must clear its compliance gate before the next one starts."
              actions={
                <span className="flex items-center gap-2 text-xs text-ink-2">
                  <span className={cn('size-2 rounded-full', fullySigned ? 'bg-line-strong' : 'bg-seal')} /> {fullySigned ? 'Workflow complete' : 'Engine active'}
                </span>
              }
            />
            {data ? (
              <ol className="mt-8 grid gap-5 sm:grid-cols-2 2xl:grid-cols-4">
                {data.steps.map((s) => <Step key={s.id} step={s} />)}
              </ol>
            ) : <div className="mt-6"><Loading /></div>}

            {doc && (
              <div className="mt-8 border-t border-line pt-6">
                <h3 className="text-sm font-semibold">Parties in signing order</h3>
                <ol className="mt-3 divide-y divide-line rounded-md border border-line">
                  {doc.parties.map((p) => (
                    <li key={p.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 text-sm">
                      <span className="hash w-4 text-xs text-muted">{p.order}</span>
                      <span className="min-w-0 flex-1">
                        <span className="font-medium">{p.name || p.email}</span>
                        <span className="block text-xs text-muted">{p.email}, {p.role}</span>
                      </span>
                      {p.signedAt ? (
                        <Badge tone="seal"><Check className="size-3" /> Signed {new Date(p.signedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</Badge>
                      ) : p.role !== 'signer' ? (
                        <Badge>No signature needed</Badge>
                      ) : doc.status === 'out_for_signature' ? (
                        <Guarded permission="document:share" className="btn btn-ghost px-2.5 py-1 text-xs" onClick={() => run(() => api.sendReminder(id, p.id), `Reminder emailed to ${p.email}.`)}>
                          <BellRing className="size-3.5" /> Send reminder
                        </Guarded>
                      ) : (
                        <Badge tone="neutral">Not sent yet</Badge>
                      )}
                    </li>
                  ))}
                  {doc.parties.length === 0 && <li className="px-4 py-3 text-sm text-ink-2">No parties yet. Add them in the editor before sending.</li>}
                </ol>
              </div>
            )}

            <div className="mt-6 grid gap-3 sm:grid-cols-3">
              <div className="flex items-center gap-3 rounded-md bg-canvas p-4">
                <Fingerprint className="size-6 shrink-0 text-seal" strokeWidth={1.5} />
                <div>
                  <p className="text-xs text-muted">Biometric verification</p>
                  <p className="text-sm font-medium text-seal">Passed (liveness 99.8%)</p>
                </div>
              </div>
              <div className="flex items-center gap-3 rounded-md bg-canvas p-4">
                <ShieldCheck className="size-6 shrink-0 text-ink-2" strokeWidth={1.5} />
                <div>
                  <p className="text-xs text-muted">Blockchain anchor</p>
                  <p className="hash text-sm font-medium">{doc?.txId ?? 'After last signature'}</p>
                </div>
              </div>
              <div className="flex items-center gap-3 rounded-md bg-canvas p-4">
                <Clock3 className={cn('size-6 shrink-0', fullySigned ? 'text-seal' : 'text-amber')} strokeWidth={1.5} />
                <div>
                  <p className="text-xs text-muted">Timestamp authority</p>
                  <p className={cn('text-sm font-medium', fullySigned ? 'text-seal' : 'text-amber')}>{fullySigned ? 'QES token issued' : 'Waiting for QES token'}</p>
                </div>
              </div>
            </div>
          </section>

          <section className="card flex flex-col p-6" aria-labelledby="feed-title">
            <div className="flex items-baseline justify-between">
              <h2 id="feed-title" className="text-lg font-semibold tracking-tight sm:text-xl">Activity feed</h2>
              <span className="text-xs text-muted">Live ledger</span>
            </div>
            {data ? (
              <ul className="mt-5 flex flex-col gap-5">
                {data.activity.map((a) => {
                  const Icon = activityIcon[a.icon]
                  return (
                    <li key={a.id} className="flex gap-3">
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-well text-ink-2">
                        <Icon className="size-4" aria-hidden="true" />
                      </span>
                      <div className="min-w-0">
                        <p className="text-sm font-medium break-words">{a.text}</p>
                        <p className="hash mt-1 text-xs text-muted">{a.meta}</p>
                      </div>
                    </li>
                  )
                })}
              </ul>
            ) : <Loading />}
            {can('audit:read') && (
              <div className="border-t border-line pt-4" style={{ marginTop: 'max(1.5rem, auto)' }}>
                <Link to={`/archive?doc=${id}`} className="btn btn-ghost w-full">View full audit ledger</Link>
              </div>
            )}
          </section>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="card p-6" aria-labelledby="ai-title">
            <CardHeader
              title={<span id="ai-title" className="flex items-center gap-2"><ChartColumn className="size-5" /> AI document intelligence</span>}
              description="Named entity recognition and risk scoring against your compliance playbook."
              actions={<Badge tone="seal">Playbook matched</Badge>}
            />
            <ul className="mt-6 flex flex-col gap-3">
              <li className="flex items-center gap-3 rounded-md bg-canvas px-4 py-3.5">
                <ShieldCheck className="size-5 shrink-0 text-seal" />
                <span className="flex-1 text-sm font-medium">Jurisdiction compliance (EU / Swiss)</span>
                <span className="text-sm font-medium text-seal">98.4% match</span>
              </li>
              <li className="flex items-center gap-3 rounded-md bg-canvas px-4 py-3.5">
                <TriangleAlert className="size-5 shrink-0 text-amber" />
                <span className="flex-1 text-sm font-medium">Indemnification clause variance</span>
                <span className="text-sm font-medium text-amber">Medium risk</span>
              </li>
              <li className="flex items-center gap-3 rounded-md bg-canvas px-4 py-3.5">
                <Check className="size-5 shrink-0 text-seal" />
                <span className="flex-1 text-sm font-medium">GDPR data residency (Frankfurt)</span>
                <span className="text-sm font-medium text-seal">Enforced</span>
              </li>
            </ul>
          </section>

          <section className="card p-6" aria-labelledby="sec-title">
            <CardHeader
              title={<span id="sec-title" className="flex items-center gap-2"><ShieldAlert className="size-5" /> Security & compliance framework</span>}
              description="Zero-trust architecture with end-to-end encryption and court-admissible non-repudiation."
              actions={<span className="text-xs text-muted">ISO 27001 / SOC 2</span>}
            />
            <dl className="mt-6 grid gap-3 sm:grid-cols-2">
              {[
                ['Encryption at rest', 'AES-256-GCM + KMS'],
                ['Signature standard', 'eIDAS QES / AATL'],
                ['Data residency', 'EU multi-region'],
                ['Audit integrity', 'WORM compliant'],
              ].map(([k, v]) => (
                <div key={k} className="rounded-md border border-line bg-canvas p-4">
                  <dt className="text-xs text-muted">{k}</dt>
                  <dd className="mt-1 font-semibold">{v}</dd>
                </div>
              ))}
            </dl>
          </section>
        </div>
      </main>
    </AppShell>
  )
}
