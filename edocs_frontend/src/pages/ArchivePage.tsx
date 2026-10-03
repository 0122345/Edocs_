import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CircleCheck, Code, Copy, Download, Fingerprint, Gavel, Lock, LockOpen, Search, FileSignature, ShieldCheck } from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { Badge, ErrorNote, Loading, PageHeader } from '../components/ui'
import { Guarded } from '../components/overlay'
import { ConfirmModal } from '../components/modals'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAction, useToast } from '../state/toast'
import { downloadJson } from '../lib/download'
import { cn } from '../lib/cn'
import { statusLabel, statusTone } from './DocumentsPage'
import type { DocumentRecord, TimelineEntry } from '../data/types'

const timelineIcon: Record<TimelineEntry['kind'], { icon: typeof Lock; className: string }> = {
  anchor: { icon: CircleCheck, className: 'bg-seal text-white' },
  'legal-hold': { icon: Gavel, className: 'bg-ink text-white' },
  signature: { icon: FileSignature, className: 'bg-ink-2 text-white' },
}

type Query = { text: string; format: 'all' | 'pdf' | 'docx'; hold: 'any' | 'active' | 'released'; sha: boolean; anchored: boolean; qes: boolean }
const defaultQuery: Query = { text: '', format: 'all', hold: 'any', sha: false, anchored: false, qes: false }

export default function ArchivePage() {
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('doc')
  const toast = useToast()
  const run = useAction()
  const [form, setForm] = useState<Query>(defaultQuery)
  const [query, setQuery] = useState<Query>(defaultQuery)
  const [ranAt, setRanAt] = useState<string | null>(null)
  const [verified, setVerified] = useState<{ objects: number } | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [copied, setCopied] = useState(false)
  const [proofOpen, setProofOpen] = useState(false)
  const [holdConfirm, setHoldConfirm] = useState(false)

  const results = useResource(
    () =>
      api
        .searchArchive({ text: query.text, format: query.format, hold: query.hold, sealedOnly: query.sha })
        .then((docs) => docs.filter((d) => (!query.anchored || !!d.txId) && (!query.qes || d.status === 'signed' || d.status === 'archived'))),
    [query],
  )
  const metrics = useResource(api.getArchiveMetrics)

  // Fall back to the first result when no document is selected.
  const activeId = selectedId ?? results.data?.[0]?.id ?? null
  const record = useResource(() => (activeId ? api.getArchiveRecord(activeId) : Promise.resolve(null)), [activeId])
  const doc = record.data?.document

  useEffect(() => setProofOpen(false), [activeId])

  function submit(e: FormEvent) {
    e.preventDefault()
    setQuery(form)
    setRanAt(new Date().toLocaleTimeString())
  }

  async function verifyChain() {
    setVerifying(true)
    const r = await run(() => api.verifyHashChain())
    setVerifying(false)
    if (r) setVerified(r)
  }

  async function exportEvidence() {
    if (!activeId) return
    const pkg = await run(() => api.getEvidencePackage(activeId), 'Evidence package downloaded.')
    if (pkg) downloadJson(`evidence-${activeId}.json`, pkg)
  }

  async function copyHash(hash: string) {
    try {
      await navigator.clipboard.writeText(hash)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      toast('Your browser blocked clipboard access. Select the hash and copy it manually.', 'error')
    }
  }

  return (
    <AppShell doc={doc ? { id: doc.id, title: doc.title, category: doc.category } : undefined} breadcrumb="Audit logs">
      <main className="mx-auto flex max-w-[1240px] flex-col gap-8 px-4 py-8 sm:px-8">
        <PageHeader
          title={
            <span className="flex flex-wrap items-center gap-3">
              WORM archive & audit log
              <Badge tone="seal" className="text-xs"><ShieldCheck className="size-3.5" /> Immutable storage</Badge>
            </span>
          }
          description="Audit trail anchored to Ethereum Mainnet, with live legal holds and tamper-evident hash verification."
          actions={
            <>
              <Guarded permission="audit:export" className="btn btn-ghost" onClick={exportEvidence} disabled={!activeId}>
                <Download className="size-4" /> Export evidence package
              </Guarded>
              <button type="button" className="btn btn-primary" onClick={verifyChain} disabled={verifying}>
                <Fingerprint className="size-4" /> {verifying ? 'Verifying…' : 'Verify hash chain'}
              </button>
            </>
          }
        />
        {verified && (
          <p role="status" className="-mt-4 flex items-center gap-2 rounded-md bg-seal-soft px-4 py-2.5 text-sm text-seal">
            <CircleCheck className="size-4" /> All {verified.objects.toLocaleString()} objects match their anchored hashes. No tampering detected.
          </p>
        )}

        <div className="grid gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
          {/* Filters + results */}
          <div className="flex flex-col gap-6">
            <form className="card flex flex-col gap-5 p-5" aria-labelledby="filter-title" onSubmit={submit}>
              <div className="flex items-baseline justify-between gap-2">
                <h2 id="filter-title" className="text-lg font-semibold tracking-tight">Search & filter</h2>
                <span className="text-xs text-muted">50+ formats</span>
              </div>
              <label className="text-sm">
                Query expression
                <span className="relative mt-1.5 block">
                  <Code className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
                  <input value={form.text} onChange={(e) => setForm({ ...form, text: e.target.value })} placeholder="status:signed AND nda" className="field hash pl-9" spellCheck={false} />
                </span>
                <span className="mt-1 block text-xs text-muted">Words are matched against title, type, status and full text.</span>
              </label>
              <div className="grid grid-cols-2 gap-3">
                <label className="text-sm">
                  Format
                  <select value={form.format} onChange={(e) => setForm({ ...form, format: e.target.value as Query['format'] })} className="field mt-1.5">
                    <option value="all">All formats</option>
                    <option value="pdf">PDF</option>
                    <option value="docx">DOCX</option>
                  </select>
                </label>
                <label className="text-sm">
                  Legal hold
                  <select value={form.hold} onChange={(e) => setForm({ ...form, hold: e.target.value as Query['hold'] })} className="field mt-1.5">
                    <option value="any">Any</option>
                    <option value="active">Active only</option>
                    <option value="released">None</option>
                  </select>
                </label>
              </div>
              <fieldset className="text-sm">
                <legend className="mb-2">Cryptographic verification</legend>
                {([
                  ['sha', 'SHA-256 checksum sealed'],
                  ['anchored', 'Anchored on Ethereum'],
                  ['qes', 'QES signature complete (eIDAS)'],
                ] as const).map(([k, label]) => (
                  <label key={k} className="flex items-start gap-3 py-1.5">
                    <input type="checkbox" checked={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.checked })} className="mt-0.5 size-4 shrink-0 accent-ink" />
                    {label}
                  </label>
                ))}
              </fieldset>
              <div className="flex gap-2">
                <button type="submit" className="btn btn-primary flex-1 py-2.5"><Search className="size-4" /> Run query</button>
                <button type="button" className="btn btn-ghost" onClick={() => { setForm(defaultQuery); setQuery(defaultQuery); setRanAt(null) }}>Reset</button>
              </div>
            </form>

            <section className="card p-5" aria-labelledby="results-title">
              <h2 id="results-title" className="text-sm font-semibold">
                Results{results.data && <span className="font-normal text-muted"> ({results.data.length}){ranAt && `, ran at ${ranAt}`}</span>}
              </h2>
              {results.error && <ErrorNote message={results.error} />}
              {!results.data ? <Loading /> : results.data.length === 0 ? (
                <p className="mt-3 text-sm text-ink-2">Nothing matches. Remove a filter or try fewer words.</p>
              ) : (
                <ul className="mt-3 flex flex-col gap-1">
                  {results.data.map((d: DocumentRecord) => (
                    <li key={d.id}>
                      <button
                        type="button"
                        aria-current={d.id === activeId}
                        onClick={() => setParams({ doc: d.id })}
                        className={cn('flex w-full flex-col items-start rounded-md px-3 py-2 text-left text-sm', d.id === activeId ? 'bg-nav' : 'hover:bg-well')}
                      >
                        <span className="font-medium">{d.title}</span>
                        <span className="text-xs text-muted">{statusLabel[d.status]}{d.legalHold ? ', legal hold' : ''}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card p-5" aria-labelledby="metrics-title">
              <h2 id="metrics-title" className="text-sm font-semibold">Archive integrity</h2>
              {metrics.data ? (
                <dl className="mt-3 divide-y divide-line text-sm">
                  {metrics.data.map((m) => (
                    <div key={m.label} className="flex items-center justify-between gap-3 py-3">
                      <dt className="text-ink-2">{m.label}</dt>
                      <dd className={cn('hash font-medium', m.tone === 'seal' && 'text-seal')}>{m.value}</dd>
                    </div>
                  ))}
                </dl>
              ) : <Loading />}
            </section>
          </div>

          {/* Record */}
          <div className="flex min-w-0 flex-col gap-6">
            {record.error && <ErrorNote message={record.error} />}
            <section className="card p-6" aria-labelledby="doc-title">
              {!activeId && results.data ? (
                <p className="text-ink-2">Run a query and pick a result to inspect its archive record.</p>
              ) : !doc ? <Loading /> : (
                <>
                  <div className="flex flex-wrap items-start gap-2">
                    {doc.legalHold ? <Badge tone="amber">Legal hold until {doc.holdUntil}</Badge> : <Badge>No legal hold</Badge>}
                    <Badge tone={statusTone[doc.status]}>{statusLabel[doc.status]}</Badge>
                    <div className="ml-auto flex gap-2 text-xs">
                      <span className="rounded border border-line bg-canvas px-2 py-1">Size <span className="font-medium">{(doc.sizeKb / 1024).toFixed(1)} MB</span></span>
                      <span className="rounded border border-line bg-canvas px-2 py-1">Pages <span className="font-medium">{doc.pages}</span></span>
                    </div>
                  </div>
                  <h2 id="doc-title" className="mt-3 text-xl font-semibold tracking-tight break-all sm:text-2xl">{doc.title}.{doc.format}</h2>
                  {doc.sha256 ? (
                    <div className="mt-3 flex items-start gap-2">
                      <p className="hash min-w-0 text-ink-2 break-all"><span className="text-muted">SHA-256 </span>{doc.sha256}</p>
                      <button type="button" onClick={() => copyHash(doc.sha256!)} className="shrink-0 rounded p-1 text-muted hover:bg-well hover:text-ink" aria-label="Copy SHA-256 hash">
                        {copied ? <CircleCheck className="size-4 text-seal" /> : <Copy className="size-4" />}
                      </button>
                    </div>
                  ) : (
                    <p className="mt-3 text-sm text-ink-2">Not sealed yet. The hash is computed and anchored when the last party signs.</p>
                  )}

                  <div className="mt-6 flex flex-col items-center rounded-md border border-line bg-canvas px-6 py-10 text-center">
                    {doc.sha256 ? <Lock className="size-8" strokeWidth={1.75} /> : <LockOpen className="size-8 text-muted" strokeWidth={1.75} />}
                    <h3 className="mt-3 text-xl font-semibold tracking-tight">{doc.sha256 ? 'Write once, read many protection' : 'Protection starts after signing'}</h3>
                    <p className="mt-2 max-w-[52ch] text-sm text-ink-2">
                      {doc.sha256
                        ? doc.legalHold
                          ? `This document is locked in immutable cold storage. It can't be altered or deleted until the legal hold expires on ${doc.holdUntil}.`
                          : 'This document is locked in immutable storage for the workspace retention period.'
                        : 'While a document is being drafted or signed it lives in normal storage. It moves to WORM storage when sealed.'}
                    </p>
                    <div className="mt-5 flex flex-wrap justify-center gap-2">
                      <button type="button" className="btn btn-primary" aria-expanded={proofOpen} disabled={!doc.sha256} onClick={() => setProofOpen((v) => !v)}>
                        {proofOpen ? 'Hide cryptographic proof' : 'Inspect cryptographic proof'}
                      </button>
                      <Guarded permission="settings:manage" className="btn btn-ghost" onClick={() => setHoldConfirm(true)}>
                        <Gavel className="size-4" /> {doc.legalHold ? 'Release legal hold' : 'Apply legal hold'}
                      </Guarded>
                    </div>
                    {proofOpen && doc.sha256 && (
                      <pre className="hash mt-5 w-full overflow-x-auto rounded-md border border-line bg-paper p-4 text-left text-xs text-ink-2">
{JSON.stringify(
  {
    object: `${doc.title}.${doc.format}`,
    sha256: doc.sha256,
    anchor: { chain: 'ethereum-mainnet', tx: doc.txId },
    retention: { mode: 'compliance', legal_hold: doc.legalHold, until: doc.holdUntil ?? null },
    signers: doc.parties.filter((p) => p.signedAt).map((p) => ({ email: p.email, signed_at: p.signedAt })),
  },
  null,
  2,
)}
                      </pre>
                    )}
                  </div>
                  <p className="mt-4 text-sm">
                    <Link to={`/workflow/${doc.id}`} className="font-medium underline-offset-4 hover:underline">Open signing workflow</Link>
                  </p>
                </>
              )}
            </section>

            <section className="card p-6" aria-labelledby="timeline-title">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h2 id="timeline-title" className="text-lg font-semibold tracking-tight sm:text-xl">Audit timeline & blockchain anchors</h2>
                {doc?.txId && <span className="hash text-xs text-muted">TxID {doc.txId}</span>}
              </div>
              {!record.data ? (activeId ? <Loading /> : null) : record.data.timeline.length === 0 ? (
                <p className="mt-4 text-sm text-ink-2">No audit events for this document yet.</p>
              ) : (
                <ol className="relative mt-6 flex flex-col gap-5">
                  <span className="absolute top-3 bottom-3 left-[11px] w-px bg-line" aria-hidden="true" />
                  {record.data.timeline.map((t) => {
                    const { icon: Icon, className } = timelineIcon[t.kind]
                    return (
                      <li key={t.id} className="relative flex gap-4">
                        <span className={cn('relative z-10 mt-4 flex size-6 shrink-0 items-center justify-center rounded-full ring-4 ring-paper', className)}>
                          <Icon className="size-3.5" aria-hidden="true" />
                        </span>
                        <div className="min-w-0 flex-1 rounded-md border border-line bg-canvas p-4">
                          <div className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between">
                            <h3 className="text-sm font-semibold">{t.title}</h3>
                            <time className="hash shrink-0 text-xs text-muted">{t.timestamp}</time>
                          </div>
                          <p className="mt-1.5 text-sm break-words text-ink-2">{t.body}</p>
                        </div>
                      </li>
                    )
                  })}
                </ol>
              )}
            </section>
          </div>
        </div>
      </main>

      <ConfirmModal
        open={holdConfirm}
        onClose={() => setHoldConfirm(false)}
        danger={doc?.legalHold}
        title={doc?.legalHold ? 'Release the legal hold?' : 'Apply a legal hold?'}
        body={
          doc?.legalHold
            ? 'Releasing the hold lets the retention policy delete this document when its period ends. Only do this when litigation or investigation is closed.'
            : 'A legal hold blocks deletion of this document for five years, overriding the retention policy.'
        }
        confirmLabel={doc?.legalHold ? 'Release hold' : 'Apply hold'}
        onConfirm={async () => {
          setHoldConfirm(false)
          if (doc) await run(() => api.setLegalHold(doc.id, !doc.legalHold), doc.legalHold ? 'Legal hold released.' : 'Legal hold applied.')
        }}
      />
    </AppShell>
  )
}
