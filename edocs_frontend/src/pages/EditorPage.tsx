import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Bold,
  CircleCheck,
  History,
  Italic,
  Lock,
  MessageSquare,
  PenLine,
  Redo2,
  Sparkles,
  Underline,
  Undo2,
  FileSignature,
  ChevronDown,
} from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { Avatar, Badge, ErrorNote, Loading } from '../components/ui'
import { Guarded, Menu, MenuItem } from '../components/overlay'
import { ChangelogModal } from '../components/modals'
import { api } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../state/auth'
import { useAction, useToast } from '../state/toast'
import { downloadFile } from '../lib/download'
import { cn } from '../lib/cn'
import { escapeHtml, sanitizeDocumentHtml } from '../lib/sanitize'
import { statusLabel, statusTone } from './DocumentsPage'
import type { Collaborator, Party } from '../data/types'

const presenceLabel = { active: 'editing now', viewing: 'invited', away: 'signed' } as const
const partyRoleLabel: Record<Party['role'], string> = { signer: 'Signer', approver: 'Approver', viewer: 'Viewer' }

const GDPR_CLAUSE =
  '<h2>5. Data retention</h2><p>Personal data processed under this Agreement is retained for no longer than necessary for the purposes set out in Schedule B, and in any case no longer than six (6) years after termination, after which it is erased or anonymised in line with Article 5(1)(e) GDPR.</p>'

const fontSizes: Record<string, string> = { '10pt': '2', '12pt': '3', '14pt': '4', '18pt': '5' }

// execCommand is deprecated but remains the simplest way to format a
// contentEditable region without a rich-text library (see way-forward notes).
function format(command: string, value?: string) {
  document.execCommand(command, false, value)
}

const keepFocus = (e: React.MouseEvent) => e.preventDefault()

export default function EditorPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { user, can } = useAuth()
  const toast = useToast()
  const run = useAction()
  const { data: doc, error } = useResource(() => api.getDocument(id), [id])
  const editorRef = useRef<HTMLDivElement>(null)
  const commentBox = useRef<HTMLTextAreaElement>(null)
  const loadedFor = useRef<string | null>(null)
  const [draft, setDraft] = useState('')
  // Collaborators are the document's parties; the signed-in user is the one actively editing.
  const collaborators: Collaborator[] = (doc?.parties ?? []).map((p) => ({
    id: p.id,
    name: p.name,
    role: partyRoleLabel[p.role],
    presence: p.email === user?.email ? 'active' : p.signedAt ? 'away' : 'viewing',
    initials: p.name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join('') || '?',
  }))
  const [showRedlines, setShowRedlines] = useState(true)
  const [suggestionDismissed, setSuggestionDismissed] = useState(false)
  const [hasRetention, setHasRetention] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState<'save' | 'send' | null>(null)
  const [changelogOpen, setChangelogOpen] = useState(false)
  const [blockStyle, setBlockStyle] = useState('p')

  const readOnly = !doc || !can('document:edit') || doc.status === 'signed' || doc.status === 'archived'

  // Load the document body into the editor once per document, not on every refresh,
  // so background reloads never overwrite unsaved typing.
  useEffect(() => {
    if (!doc || !editorRef.current || loadedFor.current === doc.id) return
    editorRef.current.innerHTML = sanitizeDocumentHtml(doc.content)
    loadedFor.current = doc.id
    setHasRetention(/retention/i.test(doc.content))
    setDirty(false)
  }, [doc])

  // Warn before leaving with unsaved changes.
  useEffect(() => {
    if (!dirty) return
    const onBeforeUnload = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [dirty])

  function onInput() {
    setDirty(true)
    setHasRetention(/retention/i.test(editorRef.current?.innerHTML ?? ''))
  }

  function insertHtml(html: string) {
    const el = editorRef.current
    if (!el || readOnly) return
    const sel = window.getSelection()
    if (!sel || !sel.rangeCount || !el.contains(sel.anchorNode)) {
      el.insertAdjacentHTML('beforeend', html)
    } else {
      el.focus()
      format('insertHTML', html)
    }
    onInput()
  }

  async function save(note?: string) {
    if (!editorRef.current) return false
    setBusy('save')
    const saved = await run(() => api.saveDocument(id, sanitizeDocumentHtml(editorRef.current!.innerHTML), note), (d) => `Draft saved as version ${d.versions[0]?.label.replace('Version ', '')}.`)
    setBusy(null)
    if (saved) setDirty(false)
    return !!saved
  }

  async function sendForSignatures() {
    setBusy('send')
    if (dirty && !(await save('Saved before sending'))) {
      setBusy(null)
      return
    }
    const sent = await run(() => api.sendForSignature(id), 'Sent for signature. Each signer gets an email with a signing link.')
    setBusy(null)
    if (sent) navigate(`/workflow/${id}`)
  }

  function downloadHtml() {
    if (!doc || !editorRef.current) return
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(doc.title)}</title></head><body><h1>${escapeHtml(doc.title)}</h1>${sanitizeDocumentHtml(editorRef.current.innerHTML)}</body></html>`
    downloadFile(`${doc.title.replace(/[^\w]+/g, '-')}.html`, html, 'text/html')
  }

  async function postComment() {
    const body = draft.trim()
    if (!body) {
      commentBox.current?.focus()
      return
    }
    const c = await run(() => api.addComment(id, body))
    if (c) setDraft('')
  }

  if (error) {
    return (
      <AppShell docked={false} breadcrumb="Documents">
        <main className="mx-auto max-w-xl px-4 py-24"><ErrorNote message={error} /></main>
      </AppShell>
    )
  }

  const open = doc?.comments.filter((c) => !c.resolved) ?? []
  const showSuggestion = !!doc && !readOnly && !hasRetention && !suggestionDismissed

  return (
    <AppShell docked={false} doc={doc ? { id: doc.id, title: doc.title, category: doc.category } : undefined} breadcrumb="Documents">
      {/* Formatting toolbar */}
      <div className="sticky top-16 z-10 border-b border-line bg-canvas/95 backdrop-blur">
        <div className="flex items-center gap-2 overflow-x-auto px-4 py-2.5 sm:px-6 [&>*]:shrink-0">
          <div className="flex items-center rounded-md border border-line bg-paper p-0.5 text-sm">
            <Menu label="File menu" triggerClassName="flex items-center gap-1 rounded px-3 py-1 hover:bg-well" trigger={<>File <ChevronDown className="size-3" /></>}>
              {(close) => (
                <>
                  <MenuItem disabled={readOnly || !dirty} onSelect={() => { close(); void save() }}>Save draft</MenuItem>
                  <MenuItem onSelect={() => { close(); downloadHtml() }}>Download as HTML</MenuItem>
                  <MenuItem onSelect={() => { close(); window.print() }}>Print or save as PDF</MenuItem>
                  <MenuItem onSelect={() => { close(); setChangelogOpen(true) }}>Version history</MenuItem>
                </>
              )}
            </Menu>
            <Menu label="Edit menu" triggerClassName="flex items-center gap-1 rounded px-3 py-1 hover:bg-well" trigger={<>Edit <ChevronDown className="size-3" /></>}>
              {(close) => (
                <>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); editorRef.current?.focus(); format('undo'); onInput() }}>Undo</MenuItem>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); editorRef.current?.focus(); format('redo'); onInput() }}>Redo</MenuItem>
                  <MenuItem onSelect={() => { close(); editorRef.current?.focus(); format('selectAll') }}>Select all</MenuItem>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); editorRef.current?.focus(); format('removeFormat'); onInput() }}>Clear formatting</MenuItem>
                </>
              )}
            </Menu>
            <Menu label="Insert menu" triggerClassName="flex items-center gap-1 rounded px-3 py-1 hover:bg-well" trigger={<>Insert <ChevronDown className="size-3" /></>}>
              {(close) => (
                <>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); insertHtml(`<span>${new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</span>`) }}>Today's date</MenuItem>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); insertHtml('<h2>Signatures</h2><p>Signed for and on behalf of the Provider: ____________________ Date: __________</p><p>Signed for and on behalf of the Client: ____________________ Date: __________</p>') }}>Signature block</MenuItem>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); insertHtml(GDPR_CLAUSE) }}>GDPR data retention clause</MenuItem>
                  <MenuItem disabled={readOnly} onSelect={() => { close(); insertHtml('<hr />') }}>Divider</MenuItem>
                </>
              )}
            </Menu>
            <button
              type="button"
              aria-pressed={showRedlines}
              onClick={() => setShowRedlines((v) => !v)}
              title={showRedlines ? 'Hide tracked changes and show the clean text' : 'Show tracked changes'}
              className={cn('flex items-center gap-1.5 rounded px-3 py-1', showRedlines ? 'bg-nav text-ink' : 'hover:bg-well')}
            >
              <PenLine className="size-3.5" /> Redline
            </button>
          </div>
          <span className="h-6 w-px bg-line" />
          <button type="button" disabled={readOnly} onMouseDown={keepFocus} onClick={() => { format('undo'); onInput() }} className="rounded p-1.5 hover:bg-well disabled:opacity-40" aria-label="Undo"><Undo2 className="size-4" /></button>
          <button type="button" disabled={readOnly} onMouseDown={keepFocus} onClick={() => { format('redo'); onInput() }} className="rounded p-1.5 hover:bg-well disabled:opacity-40" aria-label="Redo"><Redo2 className="size-4" /></button>
          <select aria-label="Paragraph style" disabled={readOnly} value={blockStyle} onChange={(e) => { setBlockStyle(e.target.value); editorRef.current?.focus(); format('formatBlock', e.target.value); onInput() }} className="field w-auto py-1">
            <option value="p">Normal text</option>
            <option value="h2">Heading</option>
            <option value="h3">Subheading</option>
            <option value="blockquote">Quote</option>
          </select>
          <select aria-label="Font" disabled={readOnly} defaultValue="IBM Plex Sans" onChange={(e) => { editorRef.current?.focus(); format('fontName', e.target.value); onInput() }} className="field w-auto py-1">
            <option>IBM Plex Sans</option>
            <option value="Georgia">Georgia (serif)</option>
            <option value="IBM Plex Mono">IBM Plex Mono</option>
          </select>
          <select aria-label="Font size" disabled={readOnly} defaultValue="12pt" onChange={(e) => { editorRef.current?.focus(); format('fontSize', fontSizes[e.target.value]); onInput() }} className="field w-auto py-1">
            {Object.keys(fontSizes).map((s) => <option key={s}>{s}</option>)}
          </select>
          <span className="h-6 w-px bg-line" />
          <button type="button" disabled={readOnly} onMouseDown={keepFocus} onClick={() => { format('bold'); onInput() }} className="rounded p-1.5 hover:bg-well disabled:opacity-40" aria-label="Bold"><Bold className="size-4" /></button>
          <button type="button" disabled={readOnly} onMouseDown={keepFocus} onClick={() => { format('italic'); onInput() }} className="rounded p-1.5 hover:bg-well disabled:opacity-40" aria-label="Italic"><Italic className="size-4" /></button>
          <button type="button" disabled={readOnly} onMouseDown={keepFocus} onClick={() => { format('underline'); onInput() }} className="rounded p-1.5 hover:bg-well disabled:opacity-40" aria-label="Underline"><Underline className="size-4" /></button>
          <button
            type="button"
            disabled={readOnly}
            onClick={() => {
              if (hasRetention) toast('No suggestions right now. The clauses match your compliance playbook.')
              else setSuggestionDismissed(false)
            }}
            className="btn ml-auto border border-info/40 bg-info-soft py-1.5 text-info hover:bg-nav"
          >
            <Sparkles className="size-4" /> AI clause suggestion
          </button>
        </div>
      </div>

      <div className="mx-auto grid max-w-[1320px] gap-6 px-4 py-8 sm:px-6 lg:grid-cols-[260px_minmax(0,1fr)] xl:grid-cols-[260px_minmax(0,1fr)_300px]">
        {/* Left rail */}
        <aside className="order-2 flex flex-col gap-6 lg:order-1">
          <section className="card p-4" aria-labelledby="collab-title">
            <div className="flex items-center justify-between">
              <h2 id="collab-title" className="text-sm font-semibold">Active collaborators</h2>
              <span className="size-2 rounded-full bg-seal" title="Live session" />
            </div>
            <ul className="mt-3 flex flex-col gap-1">
              {collaborators.map((c) => (
                <li key={c.id} className={cn('flex items-center gap-3 rounded-md p-2', c.presence === 'active' && 'bg-well')}>
                  <Avatar initials={c.initials} presence={c.presence} />
                  <div className="min-w-0 leading-tight">
                    <p className="truncate text-sm font-medium">{c.name}</p>
                    <p className="text-xs text-muted">{c.role} ({presenceLabel[c.presence]})</p>
                  </div>
                </li>
              ))}
            </ul>
          </section>

          <section className="card p-4" aria-labelledby="version-title">
            <div className="flex items-center justify-between">
              <h2 id="version-title" className="flex items-center gap-2 text-sm font-semibold">
                <History className="size-4" /> Version history
              </h2>
              {doc && <span className="hash rounded bg-well px-1.5 py-0.5 text-xs text-ink-2">v1.{doc.version - 1}</span>}
            </div>
            {doc ? (
              <ol className="relative mt-4 ml-1.5 flex flex-col gap-5 border-l border-line pl-5">
                {doc.versions.slice(0, 3).map((v) => (
                  <li key={v.id} className="relative">
                    <span className={cn('absolute top-1 -left-[26px] size-2.5 rounded-full ring-4 ring-paper', v.current ? 'bg-ink' : 'bg-line-strong')} />
                    <p className="text-sm font-medium">{v.label}{v.current && <span className="font-normal text-muted"> (current)</span>}</p>
                    <p className="text-xs text-muted">{v.when} by {v.by}</p>
                    {v.note && <p className="mt-0.5 text-xs text-seal">{v.note}</p>}
                  </li>
                ))}
              </ol>
            ) : <Loading />}
            <button type="button" className="btn btn-ghost mt-5 w-full" disabled={!doc} onClick={() => setChangelogOpen(true)}>
              View full changelog{doc && doc.versions.length > 3 ? ` (${doc.versions.length})` : ''}
            </button>
          </section>
        </aside>

        {/* Document */}
        <article className="card order-1 px-6 py-10 sm:px-12 lg:order-2">
          <header className="flex flex-wrap items-start justify-between gap-3 border-b border-line pb-6">
            <div>
              <p className="hash text-xs text-muted">{doc?.category ?? 'Loading'} agreement</p>
              <h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-[1.75rem]">{doc?.title ?? ' '}</h1>
            </div>
            {doc && (readOnly ? <Badge tone={statusTone[doc.status]}>{statusLabel[doc.status]}, read only</Badge> : <Badge tone="info">Drafting mode</Badge>)}
          </header>

          {doc && readOnly && (
            <p className="mt-6 flex items-center gap-2 rounded-md bg-well px-3 py-2 text-sm text-ink-2">
              <Lock className="size-4 shrink-0" />
              {can('document:edit') ? 'This document is signed and sealed. Edits would break its signatures, so it is read only.' : 'Your role can read this document but not edit it.'}
            </p>
          )}
          {!doc && <div className="mt-8"><Loading /></div>}

          <div
            ref={editorRef}
            contentEditable={!readOnly}
            suppressContentEditableWarning
            spellCheck
            onInput={onInput}
            aria-label="Agreement text"
            className={cn(
              'doc-body mt-8 flex flex-col gap-4 text-[1.0625rem] leading-[1.7] text-ink-2 focus:outline-none',
              !showRedlines && 'hide-redlines',
            )}
          />

          {showSuggestion && (
            <aside className="mt-10 flex gap-3 rounded-md border border-line bg-canvas p-5" aria-label="AI clause recommendation">
              <Sparkles className="mt-0.5 size-5 shrink-0 text-info" aria-hidden="true" />
              <div>
                <h2 className="font-semibold">Suggested clause: GDPR retention period</h2>
                <p className="mt-1 text-sm text-ink-2">This agreement has no explicit GDPR data retention period. Insert the standard EU compliance clause?</p>
                <div className="mt-4 flex gap-2">
                  <button type="button" className="btn btn-primary py-1.5" onClick={() => { editorRef.current?.insertAdjacentHTML('beforeend', GDPR_CLAUSE); onInput() }}>Insert clause</button>
                  <button type="button" className="btn btn-ghost py-1.5" onClick={() => setSuggestionDismissed(true)}>Dismiss</button>
                </div>
              </div>
            </aside>
          )}
        </article>

        {/* Right rail */}
        <aside className="order-3 flex flex-col gap-6 lg:col-span-2 lg:grid lg:grid-cols-2 xl:col-span-1 xl:flex">
          <section className="card p-4" aria-labelledby="comments-title">
            <h2 id="comments-title" className="flex items-center gap-2 border-b border-line pb-3 text-sm font-semibold">
              <MessageSquare className="size-4" /> Comments & redlines ({open.length})
            </h2>
            <ul className="mt-4 flex flex-col gap-3">
              {open.map((c) => (
                <li key={c.id} className="rounded-md border border-line bg-canvas p-3">
                  <div className="flex items-center gap-2">
                    <Avatar initials={c.initials} size="sm" />
                    <p className="text-sm font-medium">{c.author}</p>
                    <span className="ml-auto text-xs text-muted">{c.ago}</span>
                  </div>
                  <p className="mt-2 text-sm text-ink-2">{c.body}</p>
                  <div className="mt-2 flex justify-end gap-3 text-xs">
                    <button type="button" className="text-ink-2 hover:text-ink" onClick={() => { setDraft(`@${c.author.split(' ')[0]} `); commentBox.current?.focus() }}>Reply</button>
                    <button type="button" className="font-medium text-seal hover:underline" onClick={() => run(() => api.resolveComment(id, c.id), 'Comment resolved.')}>Resolve</button>
                  </div>
                </li>
              ))}
              {doc && open.length === 0 && <li className="py-4 text-center text-sm text-ink-2">No open comments. Add a note below to start a thread.</li>}
            </ul>
            <form
              className="mt-4 border-t border-line pt-4"
              onSubmit={(e) => {
                e.preventDefault()
                void postComment()
              }}
            >
              <label htmlFor="comment" className="sr-only">Comment</label>
              <textarea id="comment" ref={commentBox} rows={3} value={draft} onChange={(e) => setDraft(e.target.value)} placeholder={`Comment as ${user?.name ?? 'you'}`} className="field resize-none" />
              <div className="mt-3 flex justify-end">
                <button type="submit" className="btn btn-primary py-1.5" disabled={!doc}>Post comment</button>
              </div>
            </form>
          </section>

          <section className="card p-4" aria-labelledby="score-title">
            <h2 id="score-title" className="flex items-center gap-2 text-sm font-semibold">
              <CircleCheck className="size-4 text-seal" /> Compliance score
            </h2>
            <div className="mt-3 flex items-center justify-between text-sm">
              <span className="text-ink-2">eIDAS & ESIGN readiness</span>
              <span className="font-semibold text-seal tabular-nums">{hasRetention ? '100%' : '98%'}</span>
            </div>
            <div className="mt-2 h-1.5 rounded-full bg-well">
              <div className="h-full rounded-full bg-seal transition-[width] duration-500" style={{ width: hasRetention ? '100%' : '98%' }} />
            </div>
            <p className="mt-3 text-xs text-muted">{hasRetention ? 'All checks pass. Ready for multi-party signing.' : 'Missing a GDPR retention clause. Everything else is ready for signing.'}</p>
          </section>
        </aside>
      </div>

      {/* Session bar */}
      <div className="sticky bottom-0 z-10 border-t border-line bg-paper/95 backdrop-blur">
        <div className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p className="flex items-center gap-2 text-xs text-ink-2 sm:text-sm">
            <Lock className="size-4 shrink-0 text-seal" />
            <span>End-to-end AES-256 encrypted session{doc?.txId ? <>. Anchor <span className="hash">{doc.txId}</span></> : ''}</span>
          </p>
          <div className="flex items-center gap-2">
            <span role="status" className="text-xs text-muted">{dirty ? 'Unsaved changes' : doc ? 'All changes saved' : ''}</span>
            <button type="button" className="btn btn-ghost" onClick={() => save()} disabled={readOnly || !dirty || !!busy}>
              {busy === 'save' ? 'Saving…' : 'Save draft'}
            </button>
            <Guarded
              permission="document:share"
              className="btn btn-seal"
              onClick={sendForSignatures}
              disabled={!doc || readOnly || doc.status === 'out_for_signature' || !!busy}
              title={doc?.status === 'out_for_signature' ? 'Already out for signature' : undefined}
            >
              <FileSignature className="size-4" /> {busy === 'send' ? 'Sending…' : 'Send for signatures'}
            </Guarded>
          </div>
        </div>
      </div>

      <ChangelogModal open={changelogOpen} onClose={() => setChangelogOpen(false)} versions={doc?.versions ?? []} />
    </AppShell>
  )
}
