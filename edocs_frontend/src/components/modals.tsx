import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, Copy, Plus, Trash2 } from 'lucide-react'
import { Modal } from './overlay'
import { api } from '../services/api'
import { useAction, useToast } from '../state/toast'
import { useAuth } from '../state/auth'
import { useResource } from '../lib/useResource'
import { roleDescriptions, roleLabels } from '../lib/rbac'
import type { NewDocumentInput, Party, Role, RoutingRule, Version } from '../data/types'

// ---------------------------------------------------------------------------
// Share
// ---------------------------------------------------------------------------
export function ShareModal({ open, onClose, doc }: { open: boolean; onClose: () => void; doc?: { id: string; title: string } }) {
  const { can } = useAuth()
  const toast = useToast()
  const run = useAction()
  const [email, setEmail] = useState('')
  const [access, setAccess] = useState<'view' | 'comment' | 'sign'>('view')
  const [copied, setCopied] = useState(false)
  const link = window.location.href

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      toast('Your browser blocked clipboard access. Copy the link from the field instead.', 'error')
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!doc) return
    const ok = await run(() => api.shareDocument(doc.id, email.trim(), access), `Shared with ${email.trim()}. They'll get an email invitation.`)
    if (ok !== undefined) {
      setEmail('')
      onClose()
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={doc ? `Share “${doc.title}”` : 'Share this page'} description="People need an Edocs account in your workspace to open the link.">
      <label className="text-sm font-medium" htmlFor="share-link">Link</label>
      <div className="mt-1.5 flex gap-2">
        <input id="share-link" readOnly value={link} className="field hash" onFocus={(e) => e.target.select()} />
        <button type="button" className="btn btn-ghost shrink-0" onClick={copy}>
          {copied ? <Check className="size-4 text-seal" /> : <Copy className="size-4" />} {copied ? 'Copied' : 'Copy link'}
        </button>
      </div>

      {doc && (
        <form onSubmit={submit} className="mt-6 border-t border-line pt-5">
          <p className="text-sm font-medium">Invite by email</p>
          {can('document:share') ? (
            <>
              <div className="mt-2 flex flex-col gap-2 sm:flex-row">
                <label className="sr-only" htmlFor="share-email">Email address</label>
                <input id="share-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.com" className="field" />
                <label className="sr-only" htmlFor="share-access">Access</label>
                <select id="share-access" value={access} onChange={(e) => setAccess(e.target.value as typeof access)} className="field sm:w-36">
                  <option value="view">Can view</option>
                  <option value="comment">Can comment</option>
                  <option value="sign">Can sign</option>
                </select>
              </div>
              <div className="mt-4 flex justify-end">
                <button type="submit" className="btn btn-primary">Share</button>
              </div>
            </>
          ) : (
            <p className="mt-2 text-sm text-ink-2">Your role can copy the link but can't invite people. Ask legal counsel or an administrator.</p>
          )}
        </form>
      )}
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Invite member
// ---------------------------------------------------------------------------
export function InviteMemberModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const run = useAction()
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [role, setRole] = useState<Role>('signer')

  async function submit(e: FormEvent) {
    e.preventDefault()
    const user = await run(() => api.inviteMember(email, name, role), (u) => `Invitation sent to ${u.email}.`)
    if (user) {
      setEmail('')
      setName('')
      onClose()
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Invite to workspace"
      description="Invited people get an email with a sign-in link. Their role decides what they can see and do."
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" form="invite-form" className="btn btn-primary">Send invitation</button>
        </>
      }
    >
      <form id="invite-form" onSubmit={submit} className="flex flex-col gap-4">
        <label className="text-sm">Email address<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="field mt-1.5" placeholder="name@company.com" /></label>
        <label className="text-sm">Full name<input value={name} onChange={(e) => setName(e.target.value)} className="field mt-1.5" placeholder="Optional" /></label>
        <fieldset>
          <legend className="text-sm">Role</legend>
          <div className="mt-2 flex flex-col gap-2">
            {(Object.keys(roleLabels) as Role[]).map((r) => (
              <label key={r} className="flex cursor-pointer gap-3 rounded-md border border-line p-3 text-sm has-[:checked]:border-ink has-[:checked]:bg-canvas">
                <input type="radio" name="role" value={r} checked={role === r} onChange={() => setRole(r)} className="mt-0.5 accent-ink" />
                <span>
                  <span className="font-medium">{roleLabels[r]}</span>
                  <span className="block text-ink-2">{roleDescriptions[r]}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// New document (Contract + Parties from the class diagram)
// ---------------------------------------------------------------------------
type PartyDraft = Omit<Party, 'id' | 'signedAt'>

export function NewDocumentModal({ open, onClose, templateId }: { open: boolean; onClose: () => void; templateId?: string }) {
  const navigate = useNavigate()
  const run = useAction()
  const { user } = useAuth()
  const templates = useResource(api.getTemplates)
  const [title, setTitle] = useState('')
  const [tpl, setTpl] = useState(templateId ?? '')
  const [value, setValue] = useState('')
  const [effectiveDate, setEffectiveDate] = useState('')
  const [expiryDate, setExpiryDate] = useState('')
  const [parties, setParties] = useState<PartyDraft[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setTpl(templateId ?? '')
    setTitle('')
    setError(null)
    setParties([{ name: user?.name ?? '', email: user?.email ?? '', role: 'signer', order: 1 }, { name: '', email: '', role: 'signer', order: 2 }])
  }, [open, templateId, user])

  const template = templates.data?.find((t) => t.id === tpl)

  function updateParty(i: number, patch: Partial<PartyDraft>) {
    setParties((ps) => ps.map((p, j) => (j === i ? { ...p, ...patch } : p)))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (expiryDate && effectiveDate && expiryDate < effectiveDate) {
      setError('The expiry date must be after the effective date.')
      return
    }
    const filled = parties.filter((p) => p.email.trim())
    const input: NewDocumentInput = {
      title: title.trim() || template?.name || 'Untitled agreement',
      category: template?.category ?? 'General',
      templateId: tpl || undefined,
      parties: filled.map((p, i) => ({ ...p, order: i + 1 })),
      value: value ? Number(value) : undefined,
      effectiveDate: effectiveDate || undefined,
      expiryDate: expiryDate || undefined,
    }
    const doc = await run(() => api.createDocument(input), (d) => `“${d.title}” created as a draft.`)
    if (doc) {
      onClose()
      navigate(`/editor/${doc.id}`)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title="New document"
      description="Start from a template or a blank page. You can change everything later in the editor."
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" form="new-doc-form" className="btn btn-primary">Create and open editor</button>
        </>
      }
    >
      <form id="new-doc-form" onSubmit={submit} className="flex flex-col gap-4">
        {error && <p role="alert" className="rounded-md bg-alert-soft px-3 py-2 text-sm text-alert">{error}</p>}
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="text-sm">Template
            <select value={tpl} onChange={(e) => setTpl(e.target.value)} className="field mt-1.5">
              <option value="">Blank document</option>
              {templates.data?.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </label>
          <label className="text-sm">Title
            <input value={title} onChange={(e) => setTitle(e.target.value)} className="field mt-1.5" placeholder={template?.name ?? 'Untitled agreement'} />
          </label>
          <label className="text-sm">Contract value (USD)
            <input type="number" min="0" step="1000" value={value} onChange={(e) => setValue(e.target.value)} className="field mt-1.5" placeholder="Optional" />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="text-sm">Effective<input type="date" value={effectiveDate} onChange={(e) => setEffectiveDate(e.target.value)} className="field mt-1.5" /></label>
            <label className="text-sm">Expires<input type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} className="field mt-1.5" /></label>
          </div>
        </div>
        {template && template.variables.length > 0 && (
          <p className="text-xs text-muted">This template has fields to fill in the editor: {template.variables.map((v) => `{{${v}}}`).join(', ')}</p>
        )}

        <fieldset className="border-t border-line pt-4">
          <legend className="sr-only">Parties</legend>
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium">Parties, in signing order</p>
            <button type="button" className="btn btn-ghost px-2.5 py-1 text-xs" onClick={() => setParties((ps) => [...ps, { name: '', email: '', role: 'signer', order: ps.length + 1 }])}>
              <Plus className="size-3.5" /> Add party
            </button>
          </div>
          <ol className="mt-3 flex flex-col gap-2">
            {parties.map((p, i) => (
              <li key={i} className="flex flex-wrap items-center gap-2 rounded-md border border-line p-2 sm:flex-nowrap sm:border-0 sm:p-0">
                <span className="hash w-5 text-xs text-muted">{i + 1}</span>
                <input aria-label={`Party ${i + 1} name`} value={p.name} onChange={(e) => updateParty(i, { name: e.target.value })} className="field min-w-32 flex-1" placeholder="Name" />
                <input aria-label={`Party ${i + 1} email`} type="email" value={p.email} onChange={(e) => updateParty(i, { email: e.target.value })} className="field min-w-40 flex-1" placeholder="Email" />
                <select aria-label={`Party ${i + 1} role`} value={p.role} onChange={(e) => updateParty(i, { role: e.target.value as PartyDraft['role'] })} className="field w-32">
                  <option value="signer">Signer</option>
                  <option value="approver">Approver</option>
                  <option value="viewer">Viewer</option>
                </select>
                <button type="button" aria-label={`Remove party ${i + 1}`} onClick={() => setParties((ps) => ps.filter((_, j) => j !== i))} className="rounded p-1.5 text-muted hover:bg-well hover:text-alert">
                  <Trash2 className="size-4" />
                </button>
              </li>
            ))}
          </ol>
        </fieldset>
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Routing rules (dashboard → "Optimize routing rules")
// ---------------------------------------------------------------------------
export function RoutingRulesModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const run = useAction()
  const { data } = useResource(api.getRoutingRules, [open])
  const [rules, setRules] = useState<RoutingRule[]>([])

  useEffect(() => {
    if (open && data) setRules(data)
  }, [open, data])

  async function save() {
    const saved = await run(() => api.saveRoutingRules(rules), (r) => `Routing rules saved. ${r.filter((x) => x.enabled).length} active.`)
    if (saved) onClose()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Optimize routing rules"
      description="Rules apply to documents sent after you save. They target the bottlenecks on your dashboard."
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={save}>Save rules</button>
        </>
      }
    >
      <ul className="flex flex-col gap-3">
        {rules.map((r) => (
          <li key={r.id}>
            <label className="flex cursor-pointer items-start gap-3 rounded-md border border-line p-3">
              <input type="checkbox" checked={r.enabled} onChange={(e) => setRules((rs) => rs.map((x) => (x.id === r.id ? { ...x, enabled: e.target.checked } : x)))} className="mt-1 size-4 accent-ink" />
              <span>
                <span className="text-sm font-medium">{r.label}</span>
                <span className="block text-sm text-ink-2">{r.description}</span>
              </span>
            </label>
          </li>
        ))}
      </ul>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Version changelog
// ---------------------------------------------------------------------------
export function ChangelogModal({ open, onClose, versions }: { open: boolean; onClose: () => void; versions: Version[] }) {
  return (
    <Modal open={open} onClose={onClose} title="Full changelog" description="Every saved version of this document, newest first.">
      <ol className="flex flex-col divide-y divide-line">
        {versions.map((v) => (
          <li key={v.id} className="py-3">
            <p className="text-sm font-medium">{v.label}{v.current && <span className="font-normal text-muted"> (current)</span>}</p>
            <p className="text-xs text-muted">{v.when} by {v.by}</p>
            {v.note && <p className="mt-1 text-sm text-ink-2">{v.note}</p>}
          </li>
        ))}
      </ol>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Confirm
// ---------------------------------------------------------------------------
export function ConfirmModal({ open, onClose, onConfirm, title, body, confirmLabel, danger }: { open: boolean; onClose: () => void; onConfirm: () => void; title: string; body: string; confirmLabel: string; danger?: boolean }) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="button" className={danger ? 'btn bg-alert text-white hover:bg-alert/90' : 'btn btn-primary'} onClick={onConfirm}>{confirmLabel}</button>
        </>
      }
    >
      <p className="text-sm text-ink-2">{body}</p>
    </Modal>
  )
}
