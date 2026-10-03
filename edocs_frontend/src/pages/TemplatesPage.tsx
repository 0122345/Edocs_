import { useMemo, useState, type FormEvent } from 'react'
import { FileText, Plus, Search } from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { Badge, ErrorNote, Loading, PageHeader } from '../components/ui'
import { Guarded, Modal } from '../components/overlay'
import { NewDocumentModal } from '../components/modals'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAction } from '../state/toast'
import { sanitizeDocumentHtml } from '../lib/sanitize'
import type { Template } from '../data/types'

export default function TemplatesPage() {
  const { data, error } = useResource(api.getTemplates)
  const run = useAction()
  const [query, setQuery] = useState('')
  const [useTemplate, setUseTemplate] = useState<string | undefined>()
  const [newDocOpen, setNewDocOpen] = useState(false)
  const [preview, setPreview] = useState<Template | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [category, setCategory] = useState('')

  const items = useMemo(
    () => (data ?? []).filter((t) => `${t.name} ${t.category}`.toLowerCase().includes(query.trim().toLowerCase())),
    [data, query],
  )

  async function createTemplate(e: FormEvent) {
    e.preventDefault()
    const t = await run(() => api.createTemplate({ name, category }), (x) => `Template “${x.name}” created.`)
    if (t) {
      setCreateOpen(false)
      setName('')
      setCategory('')
    }
  }

  return (
    <AppShell breadcrumb="Templates">
      <main className="mx-auto flex max-w-[1240px] flex-col gap-6 px-4 py-8 sm:px-8">
        <PageHeader
          title="Templates"
          description="Pre-approved contract templates. Start a document from one to inherit its clauses and fields."
          actions={<Guarded permission="template:manage" className="btn btn-primary" onClick={() => setCreateOpen(true)}><Plus className="size-4" /> New template</Guarded>}
        />
        <label className="relative max-w-sm">
          <span className="sr-only">Search templates</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
          <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search templates" className="field pl-9" />
        </label>
        {error && <ErrorNote message={error} />}
        {!data ? <div className="card p-6"><Loading /></div> : items.length === 0 ? (
          <div className="card flex flex-col items-center gap-3 p-10 text-center">
            <p className="text-ink-2">No templates match “{query}”.</p>
            <button type="button" className="btn btn-ghost" onClick={() => setQuery('')}>Clear search</button>
          </div>
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((t) => (
              <li key={t.id} className="card flex flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <FileText className="size-5 text-ink-2" strokeWidth={1.75} />
                  <Badge>{t.category}</Badge>
                </div>
                <h2 className="mt-4 font-semibold">{t.name}</h2>
                <p className="mt-1 text-xs text-muted">Used {t.uses.toLocaleString()} times. Updated {t.updated}.</p>
                {t.variables.length > 0 && <p className="mt-2 text-xs text-ink-2">Fields: {t.variables.join(', ')}</p>}
                <div className="mt-auto flex gap-2 pt-5">
                  <Guarded permission="document:create" className="btn btn-primary flex-1 py-1.5" onClick={() => { setUseTemplate(t.id); setNewDocOpen(true) }}>Use template</Guarded>
                  <button type="button" className="btn btn-ghost py-1.5" onClick={() => setPreview(t)}>Preview</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>

      <NewDocumentModal open={newDocOpen} onClose={() => setNewDocOpen(false)} templateId={useTemplate} />

      <Modal open={!!preview} onClose={() => setPreview(null)} size="lg" title={preview?.name ?? ''} description={preview ? `${preview.category} template` : undefined}>
        {preview && <div className="doc-body flex flex-col gap-3 text-ink-2" dangerouslySetInnerHTML={{ __html: sanitizeDocumentHtml(preview.content) }} />}
      </Modal>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="New template"
        description="Creates a blank template. Open a document from it to write the clauses."
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setCreateOpen(false)}>Cancel</button>
            <button type="submit" form="tpl-form" className="btn btn-primary">Create template</button>
          </>
        }
      >
        <form id="tpl-form" onSubmit={createTemplate} className="flex flex-col gap-4">
          <label className="text-sm">Name<input required value={name} onChange={(e) => setName(e.target.value)} className="field mt-1.5" placeholder="e.g. Consulting agreement" /></label>
          <label className="text-sm">Category<input value={category} onChange={(e) => setCategory(e.target.value)} className="field mt-1.5" placeholder="e.g. Commercial" list="tpl-categories" /></label>
          <datalist id="tpl-categories">
            {[...new Set((data ?? []).map((t) => t.category))].map((c) => <option key={c} value={c} />)}
          </datalist>
        </form>
      </Modal>
    </AppShell>
  )
}
