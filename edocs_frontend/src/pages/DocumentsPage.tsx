import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { FilePlus, MoreHorizontal, Search } from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { Badge, ErrorNote, Loading, PageHeader } from '../components/ui'
import { Guarded, Menu, MenuItem } from '../components/overlay'
import { ConfirmModal, NewDocumentModal } from '../components/modals'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../state/auth'
import { useAction } from '../state/toast'
import { cn } from '../lib/cn'
import type { DocStatus, DocumentRecord } from '../data/types'

export const statusLabel: Record<DocStatus, string> = {
  draft: 'Draft',
  in_review: 'In review',
  out_for_signature: 'Out for signature',
  signed: 'Signed',
  archived: 'Archived',
}
export const statusTone: Record<DocStatus, 'neutral' | 'info' | 'amber' | 'seal'> = {
  draft: 'neutral',
  in_review: 'info',
  out_for_signature: 'amber',
  signed: 'seal',
  archived: 'neutral',
}

const filters: (DocStatus | 'all')[] = ['all', 'draft', 'in_review', 'out_for_signature', 'signed', 'archived']

export default function DocumentsPage() {
  const { data, error } = useResource(api.listDocuments)
  const { user, can } = useAuth()
  const navigate = useNavigate()
  const run = useAction()
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') as DocStatus | null) ?? 'all'
  const [query, setQuery] = useState('')
  const [newOpen, setNewOpen] = useState(false)
  const [toDelete, setToDelete] = useState<DocumentRecord | null>(null)

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (data ?? []).filter(
      (d) => (status === 'all' || d.status === status) && (!q || `${d.title} ${d.category} ${d.parties.map((p) => p.name + p.email).join(' ')}`.toLowerCase().includes(q)),
    )
  }, [data, status, query])

  const awaitingMe = (d: DocumentRecord) => d.status === 'out_for_signature' && d.parties.some((p) => p.email === user?.email && p.role === 'signer' && !p.signedAt)

  return (
    <AppShell breadcrumb="Documents">
      <main className="mx-auto flex max-w-[1240px] flex-col gap-6 px-4 py-8 sm:px-8">
        <PageHeader
          title="Documents"
          description={user?.role === 'signer' ? 'Documents you are a party to.' : 'Every contract in the workspace, from first draft to sealed archive.'}
          actions={
            <Guarded permission="document:create" className="btn btn-primary" onClick={() => setNewOpen(true)}>
              <FilePlus className="size-4" /> New document
            </Guarded>
          }
        />

        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div role="tablist" aria-label="Filter by status" className="flex gap-1 overflow-x-auto">
            {filters.map((f) => (
              <button
                key={f}
                type="button"
                role="tab"
                aria-selected={status === f}
                onClick={() => setParams(f === 'all' ? {} : { status: f })}
                className={cn('shrink-0 rounded-md px-3 py-1.5 text-sm', status === f ? 'bg-ink text-white' : 'text-ink-2 hover:bg-well')}
              >
                {f === 'all' ? 'All' : statusLabel[f]}
                {data && <span className="ml-1.5 opacity-60">{f === 'all' ? data.length : data.filter((d) => d.status === f).length}</span>}
              </button>
            ))}
          </div>
          <label className="relative md:w-72">
            <span className="sr-only">Search documents</span>
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
            <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search by title, type or party" className="field pl-9" />
          </label>
        </div>

        {error && <ErrorNote message={error} />}
        <section className="card overflow-hidden">
          {!data ? (
            <div className="p-6"><Loading /></div>
          ) : rows.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
              <p className="text-ink-2">{data.length === 0 ? 'No documents yet.' : 'No documents match these filters.'}</p>
              {data.length === 0 && can('document:create') ? (
                <button type="button" className="btn btn-primary" onClick={() => setNewOpen(true)}>Create your first document</button>
              ) : (
                <button type="button" className="btn btn-ghost" onClick={() => { setQuery(''); setParams({}) }}>Clear filters</button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="border-b border-line bg-well text-xs font-semibold text-ink-2">
                  <tr>
                    <th scope="col" className="px-6 py-3">Document</th>
                    <th scope="col" className="px-4 py-3">Status</th>
                    <th scope="col" className="px-4 py-3">Parties</th>
                    <th scope="col" className="px-4 py-3">Value</th>
                    <th scope="col" className="px-4 py-3">Updated</th>
                    <th scope="col" className="px-6 py-3"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {rows.map((d) => {
                    const signed = d.parties.filter((p) => p.signedAt).length
                    const signers = d.parties.filter((p) => p.role === 'signer').length
                    return (
                      <tr key={d.id} className="hover:bg-canvas">
                        <td className="px-6 py-4">
                          <Link to={can('document:edit') ? `/editor/${d.id}` : `/workflow/${d.id}`} className="font-medium underline-offset-4 hover:underline">{d.title}</Link>
                          <p className="text-xs text-muted">{d.category}, version 1.{d.version - 1}</p>
                        </td>
                        <td className="px-4 py-4">
                          <Badge tone={statusTone[d.status]}>{statusLabel[d.status]}</Badge>
                          {awaitingMe(d) && <p className="mt-1 text-xs font-medium text-amber">Waiting for your signature</p>}
                        </td>
                        <td className="px-4 py-4 text-ink-2">{signers ? `${signed} of ${signers} signed` : `${d.parties.length} part${d.parties.length === 1 ? 'y' : 'ies'}`}</td>
                        <td className="px-4 py-4 tabular-nums text-ink-2">{d.value ? `$${d.value.toLocaleString()}` : '—'}</td>
                        <td className="px-4 py-4 whitespace-nowrap text-ink-2">{new Date(d.updatedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            {awaitingMe(d) && can('document:sign') && (
                              <Link to={`/sign/${d.id}`} className="btn btn-seal px-3 py-1.5 text-xs">Sign</Link>
                            )}
                            <Menu align="right" label={`Actions for ${d.title}`} triggerClassName="rounded p-1.5 text-ink-2 hover:bg-well" trigger={<MoreHorizontal className="size-4" />}>
                              {(close) => (
                                <>
                                  <MenuItem disabled={!can('document:edit')} onSelect={() => { close(); navigate(`/editor/${d.id}`) }}>Open in editor</MenuItem>
                                  <MenuItem onSelect={() => { close(); navigate(`/workflow/${d.id}`) }}>View signing workflow</MenuItem>
                                  <MenuItem disabled={!can('audit:read')} onSelect={() => { close(); navigate(`/archive?doc=${d.id}`) }}>View audit trail</MenuItem>
                                  <MenuItem
                                    disabled={!can('document:share') || !['draft', 'in_review'].includes(d.status)}
                                    onSelect={async () => { close(); await run(() => api.sendForSignature(d.id), 'Sent for signature. Signers get an email now.') }}
                                  >
                                    Send for signature
                                  </MenuItem>
                                  <MenuItem tone="alert" disabled={!can('document:delete') || d.status !== 'draft'} title={d.status !== 'draft' ? 'Only drafts can be deleted' : undefined} onSelect={() => { close(); setToDelete(d) }}>
                                    Delete draft
                                  </MenuItem>
                                </>
                              )}
                            </Menu>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>

      <NewDocumentModal open={newOpen} onClose={() => setNewOpen(false)} />
      <ConfirmModal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        danger
        title="Delete this draft?"
        body={`“${toDelete?.title}” will be removed. Drafts haven't been sent, so no one else loses access. The deletion is recorded in the audit log.`}
        confirmLabel="Delete draft"
        onConfirm={async () => {
          const d = toDelete
          setToDelete(null)
          if (d) await run(() => api.deleteDocument(d.id), 'Draft deleted.')
        }}
      />
    </AppShell>
  )
}
