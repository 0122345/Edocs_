import * as seed from '../data/mock'
import type {
  ActivityItem,
  AppNotification,
  AuditEventKind,
  AuditLogEntry,
  Comment,
  ComplianceControl,
  DocumentRecord,
  NewDocumentInput,
  Role,
  RoutingRule,
  Session,
  Template,
  TimelineEntry,
  User,
  WorkflowStep,
  WorkspaceSettings,
} from '../data/types'
import { can, type Permission } from '../lib/rbac'
import { db, displayTimestamp, fakeTxHash, uid } from './mockDb'

/**
 * The single data-access layer for the UI.
 *
 * - VITE_API_BASE_URL unset → every call runs against the in-browser mock DB.
 * - VITE_API_BASE_URL set   → every call becomes the REST request written next
 *   to it. Those paths are the contract the backend should implement.
 */
const BASE_URL = (import.meta.env?.VITE_API_BASE_URL as string | undefined) || ''
export const isMockMode = !BASE_URL

const SESSION_KEY = 'edocs.session'
const LATENCY_MS = import.meta.env?.MODE === 'test' ? 0 : 150

export class ApiError extends Error {
  constructor(message: string, readonly status = 400) {
    super(message)
  }
}

// --------------------------------------------------------------------------
// Session helpers
// --------------------------------------------------------------------------
function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

function writeSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else localStorage.removeItem(SESSION_KEY)
  } catch {
    // Storage blocked: session lives only for this page load.
  }
}

let currentSession: Session | null = readSession()

function me(): User {
  if (!currentSession) throw new ApiError('Your session has ended. Sign in again.', 401)
  // Re-read the user so role changes made by an admin apply immediately.
  return db.get().users.find((u) => u.id === currentSession!.user.id) ?? currentSession.user
}

function requirePermission(permission: Permission) {
  if (!can(me().role, permission)) throw new ApiError('Your role does not allow this action.', 403)
}

// --------------------------------------------------------------------------
// Transport
// --------------------------------------------------------------------------
async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    credentials: 'include',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(currentSession ? { Authorization: `Bearer ${currentSession.token}` } : {}),
      ...init?.headers,
    },
  })
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null
    throw new ApiError(body?.message ?? `${init?.method ?? 'GET'} ${path} failed with ${res.status}`, res.status)
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}

async function call<T>(remote: () => Promise<T>, mock: () => T | Promise<T>): Promise<T> {
  if (!isMockMode) return remote()
  if (LATENCY_MS) await new Promise((r) => setTimeout(r, LATENCY_MS))
  return mock()
}

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) })

// --------------------------------------------------------------------------
// Mock-side helpers
// --------------------------------------------------------------------------
function findDoc(id: string): DocumentRecord {
  const doc = db.get().documents.find((d) => d.id === id)
  if (!doc) throw new ApiError('That document no longer exists.', 404)
  return doc
}

function fileName(doc: DocumentRecord) {
  return `${doc.title.replace(/[^\w]+/g, '-').replace(/^-|-$/g, '')}.${doc.format}`
}

function audit(kind: AuditEventKind, event: string, doc?: DocumentRecord, status: AuditLogEntry['status'] = 'verified'): AuditLogEntry {
  const now = new Date()
  return {
    id: uid('a'),
    kind,
    event,
    document: doc ? fileName(doc) : 'Workspace',
    documentId: doc?.id,
    actor: currentSession ? me().email : 'anonymous',
    origin: 'Web app',
    timestamp: displayTimestamp(now),
    at: now.toISOString(),
    txHash: fakeTxHash(),
    status,
  }
}

function notify(n: Omit<AppNotification, 'id' | 'createdAt' | 'read'>): AppNotification {
  return { ...n, id: uid('n'), createdAt: new Date().toISOString(), read: false }
}

export async function sha256Hex(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('') || '?'
}

const stepMeta = [
  { title: 'Draft', description: 'Contract generated and clauses assembled.', done: 'completed' },
  { title: 'Legal review', description: 'Internal compliance check and redlining.', done: 'approved' },
  { title: 'Counterparty', description: 'External negotiation and preliminary sign-off.', done: 'verified' },
  { title: 'QES / eIDAS', description: 'Qualified electronic signature with biometric ID check.', done: 'verified' },
] as const

export function workflowSteps(doc: DocumentRecord): WorkflowStep[] {
  return stepMeta.map((s, i) => {
    const n = i + 1
    return {
      id: n,
      title: s.title,
      description: s.description,
      status: n < doc.workflowStep ? s.done : n === doc.workflowStep ? 'in-progress' : 'waiting',
    }
  })
}

// --------------------------------------------------------------------------
// API
// --------------------------------------------------------------------------
export type LoginResult = { kind: 'session'; session: Session } | { kind: 'mfa'; challengeId: string; email: string }

export const api = {
  /** Re-render hook for screens: fires after every mock write. A real backend would push via WebSocket/SSE. */
  subscribe: (listener: () => void) => db.subscribe(listener),

  // ---------------- Auth (OAuth2 / OIDC + MFA) ----------------
  session: () => currentSession,

  login: (email: string, password: string) =>
    call<LoginResult>(
      () => http('/auth/login', json('POST', { email, password })),
      () => {
        const user = db.get().users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase())
        if (!user || password !== seed.DEMO_PASSWORD) throw new ApiError('Email or password is incorrect.', 401)
        if (!user.active) throw new ApiError('This account is deactivated. Ask an administrator to reactivate it.', 403)
        if (user.mfaEnabled) return { kind: 'mfa', challengeId: user.id, email: user.email }
        return { kind: 'session', session: api._start(user) }
      },
    ).then((r) => {
      if (r.kind === 'session') api._persist(r.session)
      return r
    }),

  verifyMfa: (challengeId: string, code: string) =>
    call<Session>(
      () => http('/auth/mfa/verify', json('POST', { challengeId, code })),
      () => {
        const user = db.get().users.find((u) => u.id === challengeId)
        if (!user || code !== seed.DEMO_MFA_CODE) throw new ApiError('That code is incorrect. Check your authenticator app and try again.', 401)
        return api._start(user)
      },
    ).then((s) => {
      api._persist(s)
      return s
    }),

  /** OAuth2 authorization-code flow. In mock mode the provider "returns" the admin account. */
  loginWithProvider: (provider: 'google' | 'microsoft') => {
    if (!isMockMode) {
      window.location.assign(`${BASE_URL}/oauth2/authorization/${provider}`)
      return new Promise<Session>(() => {})
    }
    return call(
      () => Promise.reject(),
      () => api._start(db.get().users[0]!),
    ).then((s) => {
      api._persist(s)
      return s
    })
  },

  /** Finishes OAuth2 sign-in: the backend redirects to /login#token=<jwt>; trade it for the user profile. */
  completeOAuthRedirect: async (): Promise<Session | null> => {
    const match = /[#&]token=([^&]+)/.exec(window.location.hash)
    if (!match || isMockMode) return null
    const token = decodeURIComponent(match[1]!)
    window.history.replaceState(null, '', window.location.pathname + window.location.search)
    const res = await fetch(`${BASE_URL}/auth/me`, { headers: { Authorization: `Bearer ${token}` } })
    if (!res.ok) throw new ApiError('Single sign-on failed. Try again.', res.status)
    const session: Session = { token, user: (await res.json()) as User }
    api._persist(session)
    return session
  },

  logout: () =>
    call<void>(
      () => http('/auth/logout', json('POST')),
      () => undefined,
    ).finally(() => api._persist(null)),

  _start(user: User): Session {
    return { token: `mock.${user.id}.${Date.now()}`, user }
  },
  _persist(session: Session | null) {
    currentSession = session
    writeSession(session)
  },

  // ---------------- Dashboard & audit ----------------
  getDashboard: () =>
    call(
      () => http<{ kpis: typeof seed.kpis; riskBands: typeof seed.riskBands; bottlenecks: typeof seed.bottlenecks }>('/dashboard'),
      () => {
        const docs = db.get().documents
        const pending = docs.filter((d) => d.status === 'out_for_signature').length
        const kpis = seed.kpis.map((k) => (k.id === 'pending' ? { ...k, value: String(40 + pending) } : k))
        return { kpis, riskBands: seed.riskBands, bottlenecks: seed.bottlenecks }
      },
    ),

  getAuditLogs: () =>
    call(
      () => http<{ items: AuditLogEntry[]; total: number }>('/audit-logs'),
      () => {
        const items = [...db.get().auditLogs].sort((a, b) => b.at.localeCompare(a.at))
        return { items, total: seed.auditLogBaseline + items.length }
      },
    ),

  verifyHashChain: () =>
    call(
      () => http<{ objects: number; intact: boolean }>('/audit-logs/verify', json('POST')),
      () => {
        requirePermission('audit:read')
        const objects = 1_429_000 + db.get().documents.length * 16 + db.get().auditLogs.length
        db.update((s) => s.auditLogs.push(audit('anchor', 'Hash chain verified')))
        return { objects, intact: true }
      },
    ),

  getRoutingRules: () => call(() => http<RoutingRule[]>('/workflow/routing-rules'), () => db.get().routingRules),

  saveRoutingRules: (rules: RoutingRule[]) =>
    call(
      () => http<RoutingRule[]>('/workflow/routing-rules', json('PUT', rules)),
      () => {
        requirePermission('workflow:manage')
        db.update((s) => {
          s.routingRules = rules
          s.auditLogs.push(audit('redline', `Routing rules updated (${rules.filter((r) => r.enabled).length} active)`))
        })
        return rules
      },
    ),

  // ---------------- Documents ----------------
  listDocuments: () =>
    call(
      () => http<DocumentRecord[]>('/documents'),
      () => {
        const user = me()
        const docs = [...db.get().documents].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
        // Signers only see documents they are a party to.
        return user.role === 'signer' ? docs.filter((d) => d.parties.some((p) => p.email === user.email)) : docs
      },
    ),

  getDocument: (id: string) => call(() => http<DocumentRecord>(`/documents/${id}`), () => findDoc(id)),

  createDocument: (input: NewDocumentInput) =>
    call(
      () => http<DocumentRecord>('/documents', json('POST', input)),
      () => {
        requirePermission('document:create')
        const template = db.get().templates.find((t) => t.id === input.templateId)
        const now = new Date().toISOString()
        const user = me()
        const base = input.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'document'
        const doc: DocumentRecord = {
          id: `${base}-${Math.random().toString(36).slice(2, 6)}`,
          title: input.title.trim(),
          category: input.category,
          templateId: template?.id,
          format: 'pdf',
          status: 'draft',
          version: 1,
          content: template?.content ?? seed.BLANK_TEMPLATE_CONTENT,
          parties: input.parties.map((p) => ({ ...p, id: uid('p') })),
          value: input.value,
          effectiveDate: input.effectiveDate,
          expiryDate: input.expiryDate,
          ownerId: user.id,
          createdAt: now,
          updatedAt: now,
          legalHold: false,
          workflowStep: 1,
          comments: [],
          versions: [{ id: uid('v'), label: 'Version 1.0 (initial draft)', when: 'Just now', by: user.name, note: template ? `Created from ${template.name}` : 'Blank document', current: true }],
          sizeKb: 40,
          pages: 1,
        }
        db.update((s) => {
          s.documents.push(doc)
          const t = s.templates.find((x) => x.id === template?.id)
          if (t) t.uses += 1
          s.auditLogs.push(audit('create', 'Document created', doc))
        })
        return doc
      },
    ),

  saveDocument: (id: string, content: string, note?: string) =>
    call(
      () => http<DocumentRecord>(`/documents/${id}`, json('PATCH', { content, note })),
      () => {
        requirePermission('document:edit')
        const doc = findDoc(id)
        if (doc.status === 'signed' || doc.status === 'archived') throw new ApiError('Signed documents are sealed and cannot be edited.', 409)
        const user = me()
        let saved!: DocumentRecord
        db.update((s) => {
          const d = s.documents.find((x) => x.id === id)!
          d.content = content
          d.version += 1
          d.updatedAt = new Date().toISOString()
          d.versions.forEach((v) => (v.current = false))
          d.versions.unshift({ id: uid('v'), label: `Version 1.${d.version - 1}`, when: 'Just now', by: user.name, note: note ?? 'Saved draft', current: true })
          s.auditLogs.push(audit('redline', 'Draft saved', d))
          saved = structuredClone(d)
        })
        return saved
      },
    ),

  deleteDocument: (id: string) =>
    call<void>(
      () => http(`/documents/${id}`, json('DELETE')),
      () => {
        requirePermission('document:delete')
        const doc = findDoc(id)
        if (doc.status !== 'draft') throw new ApiError('Only drafts can be deleted. Signed and in-flight documents are retained for audit.', 409)
        db.update((s) => {
          s.documents = s.documents.filter((d) => d.id !== id)
          s.auditLogs.push(audit('create', 'Draft deleted', doc))
        })
      },
    ),

  addComment: (id: string, body: string) =>
    call(
      () => http<Comment>(`/documents/${id}/comments`, json('POST', { body })),
      () => {
        const user = me()
        const comment: Comment = { id: uid('cm'), author: user.name, initials: user.initials, ago: 'Just now', body }
        db.update((s) => s.documents.find((d) => d.id === id)!.comments.push(comment))
        return comment
      },
    ),

  resolveComment: (id: string, commentId: string) =>
    call<void>(
      () => http(`/documents/${id}/comments/${commentId}/resolve`, json('POST')),
      () => {
        db.update((s) => {
          const c = s.documents.find((d) => d.id === id)?.comments.find((x) => x.id === commentId)
          if (c) c.resolved = true
        })
      },
    ),

  shareDocument: (id: string, email: string, access: 'view' | 'comment' | 'sign') =>
    call<void>(
      () => http(`/documents/${id}/shares`, json('POST', { email, access })),
      () => {
        requirePermission('document:share')
        const doc = findDoc(id)
        db.update((s) => {
          s.notifications.unshift(notify({ type: 'share', channel: 'email', title: `Shared with ${email}`, body: `${email} can now ${access} “${doc.title}”. An email invitation is queued.`, link: `/editor/${id}` }))
          s.auditLogs.push(audit('share', `Shared with ${email} (${access})`, doc))
        })
      },
    ),

  sendForSignature: (id: string) =>
    call(
      () => http<DocumentRecord>(`/documents/${id}/send`, json('POST')),
      () => {
        requirePermission('document:share')
        const doc = findDoc(id)
        const signers = doc.parties.filter((p) => p.role === 'signer')
        if (signers.length === 0) throw new ApiError('Add at least one signer before sending.', 422)
        if (doc.status === 'signed' || doc.status === 'archived') throw new ApiError('This document is already signed.', 409)
        let sent!: DocumentRecord
        db.update((s) => {
          const d = s.documents.find((x) => x.id === id)!
          d.status = 'out_for_signature'
          d.workflowStep = Math.max(d.workflowStep, 3)
          d.updatedAt = new Date().toISOString()
          for (const p of signers.filter((p) => !p.signedAt)) {
            s.notifications.unshift(notify({ type: 'signature_request', channel: 'email', title: 'Signature requested', body: `${p.name} (${p.email}) was asked to sign “${d.title}”.`, link: `/workflow/${id}` }))
          }
          s.auditLogs.push(audit('signature', `Sent for signature to ${signers.length} signer${signers.length > 1 ? 's' : ''}`, d, 'pending'))
          sent = structuredClone(d)
        })
        return sent
      },
    ),

  sendReminder: (id: string, partyId: string) =>
    call<void>(
      () => http(`/documents/${id}/parties/${partyId}/remind`, json('POST')),
      () => {
        requirePermission('document:share')
        const doc = findDoc(id)
        const party = doc.parties.find((p) => p.id === partyId)
        if (!party || party.signedAt) throw new ApiError('This party has already signed.', 409)
        db.update((s) => {
          s.notifications.unshift(notify({ type: 'signature_request', channel: 'email', title: 'Reminder sent', body: `${party.name} (${party.email}) was reminded to sign “${doc.title}”.`, link: `/workflow/${id}` }))
          s.auditLogs.push(audit('share', `Signing reminder sent to ${party.email}`, doc))
        })
      },
    ),

  // ---------------- Workflow ----------------
  getWorkflow: (id: string) =>
    call(
      () => http<{ document: DocumentRecord; steps: WorkflowStep[]; activity: ActivityItem[] }>(`/documents/${id}/workflow`),
      () => {
        const doc = findDoc(id)
        const recent: ActivityItem[] = db
          .get()
          .auditLogs.filter((a) => a.documentId === id)
          .sort((a, b) => b.at.localeCompare(a.at))
          .slice(0, 3)
          .map((a) => ({ id: a.id, icon: a.kind === 'signature' ? 'identity' : a.kind === 'anchor' || a.kind === 'legal-hold' ? 'lock' : 'edit', text: `${a.event} by ${a.actor}.`, meta: `${a.timestamp} · ${a.origin}` }))
        return { document: doc, steps: workflowSteps(doc), activity: recent.length ? recent : seed.activityFeed }
      },
    ),

  getEvidencePackage: (id: string) =>
    call(
      () => http<Record<string, unknown>>(`/documents/${id}/evidence`),
      () => {
        requirePermission('audit:read')
        const doc = findDoc(id)
        return {
          generatedAt: new Date().toISOString(),
          generatedBy: me().email,
          document: { id: doc.id, title: doc.title, version: doc.version, status: doc.status, sha256: doc.sha256 ?? null, anchorTx: doc.txId ?? null, legalHold: doc.legalHold },
          parties: doc.parties,
          workflow: workflowSteps(doc),
          auditTrail: db.get().auditLogs.filter((a) => a.documentId === id),
        }
      },
    ),

  // ---------------- Signing ----------------
  sendOtp: (id: string) =>
    call(
      () => http<{ sentTo: string }>(`/documents/${id}/otp`, json('POST')),
      () => {
        requirePermission('document:sign')
        const code = String(Math.floor(100000 + Math.random() * 900000))
        const user = me()
        db.update((s) => {
          s.otps[id] = code
          // In production this goes out via RabbitMQ → SMS/email worker; the mock delivers it in-app.
          s.notifications.unshift(notify({ type: 'otp', channel: 'sms', title: 'Your signing code', body: `Your Edocs signing code is ${code}. It expires in 10 minutes.` }))
        })
        return { sentTo: user.email }
      },
    ),

  executeSignature: (id: string, payload: { mode: string; otp: string; signature: string }) =>
    call(
      () => http<{ txId: string; document: DocumentRecord }>(`/documents/${id}/signatures`, json('POST', payload)),
      async () => {
        requirePermission('document:sign')
        const doc = findDoc(id)
        if (doc.status === 'signed' || doc.status === 'archived') throw new ApiError('This document is already fully signed.', 409)
        if (!db.get().otps[id] || db.get().otps[id] !== payload.otp) throw new ApiError('That code is wrong or has expired. Send a new code and try again.', 422)
        const user = me()
        const sha = await sha256Hex(doc.content + payload.signature)
        const txId = fakeTxHash()
        let updated!: DocumentRecord
        db.update((s) => {
          const d = s.documents.find((x) => x.id === id)!
          const party = d.parties.find((p) => p.email === user.email && !p.signedAt) ?? d.parties.find((p) => p.role === 'signer' && !p.signedAt)
          if (party) party.signedAt = new Date().toISOString()
          delete s.otps[id]
          const allSigned = d.parties.filter((p) => p.role === 'signer').every((p) => p.signedAt)
          d.updatedAt = new Date().toISOString()
          s.auditLogs.push(audit('signature', `E-signature executed (${payload.mode})`, d))
          if (allSigned) {
            d.status = 'signed'
            d.workflowStep = 5
            d.sha256 = sha
            d.txId = txId
            s.auditLogs.push({ ...audit('anchor', 'Merkle root anchored', d), actor: 'anchor-service', origin: 'Ethereum Mainnet', txHash: txId })
            s.notifications.unshift(notify({ type: 'signed', channel: 'email', title: `${d.title} fully signed`, body: 'All parties signed. The sealed copy is in the archive.', link: `/archive?doc=${id}` }))
          } else {
            d.status = 'out_for_signature'
            d.workflowStep = 4
          }
          updated = structuredClone(d)
        })
        return { txId, document: updated }
      },
    ),

  // ---------------- Archive ----------------
  searchArchive: (query: { text: string; format: 'all' | 'pdf' | 'docx'; hold: 'any' | 'active' | 'released'; sealedOnly: boolean }) =>
    call(
      () => http<DocumentRecord[]>(`/archive?${new URLSearchParams({ q: query.text, format: query.format, hold: query.hold, sealed: String(query.sealedOnly) })}`),
      () => {
        requirePermission('audit:read')
        const terms = query.text.toLowerCase().split(/\s+(?:and\s+)?/).filter(Boolean)
        const sealedFirst = [...db.get().documents].sort((a, b) => Number(!!b.sha256) - Number(!!a.sha256) || b.updatedAt.localeCompare(a.updatedAt))
        return sealedFirst.filter((d) => {
          const hay = `${d.id} ${d.title} ${d.category} ${d.status} ${d.content}`.toLowerCase()
          return (
            terms.every((t) => hay.includes(t.replace(/^\w+:/, ''))) &&
            (query.format === 'all' || d.format === query.format) &&
            (query.hold === 'any' || (query.hold === 'active') === d.legalHold) &&
            (!query.sealedOnly || !!d.sha256)
          )
        })
      },
    ),

  getArchiveRecord: (id: string) =>
    call(
      () => http<{ document: DocumentRecord; timeline: TimelineEntry[] }>(`/archive/${id}`),
      () => {
        requirePermission('audit:read')
        const doc = findDoc(id)
        const timeline: TimelineEntry[] = db
          .get()
          .auditLogs.filter((a) => a.documentId === id)
          .sort((a, b) => b.at.localeCompare(a.at))
          .map((a) => ({
            id: a.id,
            kind: a.kind === 'anchor' ? 'anchor' : a.kind === 'legal-hold' ? 'legal-hold' : 'signature',
            title: a.event,
            timestamp: a.at.replace('T', ' ').slice(0, 19) + ' UTC',
            body: `${a.actor} from ${a.origin}. Tx ${a.txHash}.`,
          }))
        return { document: doc, timeline }
      },
    ),

  getArchiveMetrics: () =>
    call(
      () => http<{ label: string; value: string; tone?: 'seal' }[]>('/archive/metrics'),
      () => {
        const sealed = db.get().documents.filter((d) => d.sha256).length
        return [
          { label: 'Total WORM objects', value: (1_429_077 + sealed).toLocaleString() },
          { label: 'Blockchain anchor lag', value: '12.4 s avg', tone: 'seal' as const },
          { label: 'Tamper events detected', value: '0', tone: 'seal' as const },
        ]
      },
    ),

  setLegalHold: (id: string, on: boolean) =>
    call<void>(
      () => http(`/archive/${id}/legal-hold`, json('PUT', { on })),
      () => {
        requirePermission('settings:manage')
        db.update((s) => {
          const d = s.documents.find((x) => x.id === id)!
          d.legalHold = on
          d.holdUntil = on ? new Date(Date.now() + 5 * 365 * 864e5).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : undefined
          s.auditLogs.push(audit('legal-hold', on ? 'Legal hold applied' : 'Legal hold released', d))
        })
      },
    ),

  // ---------------- Templates ----------------
  getTemplates: () => call(() => http<Template[]>('/templates'), () => db.get().templates),

  createTemplate: (input: { name: string; category: string }) =>
    call(
      () => http<Template>('/templates', json('POST', input)),
      () => {
        requirePermission('template:manage')
        const t: Template = { id: uid('tp'), name: input.name.trim(), category: input.category.trim() || 'General', uses: 0, updated: 'Just now', content: seed.BLANK_TEMPLATE_CONTENT, variables: [] }
        db.update((s) => s.templates.unshift(t))
        return t
      },
    ),

  // ---------------- Compliance ----------------
  getComplianceControls: () => call(() => http<ComplianceControl[]>('/compliance/controls'), () => db.get().complianceControls),

  runComplianceChecks: () =>
    call(
      () => http<ComplianceControl[]>('/compliance/checks', json('POST')),
      () => {
        requirePermission('compliance:run')
        db.update((s) => {
          s.complianceControls = s.complianceControls.map((c) => ({ ...c, checked: 'Just now' }))
          s.auditLogs.push(audit('view', 'Compliance checks run'))
        })
        return db.get().complianceControls
      },
    ),

  // ---------------- Workspace, members, notifications ----------------
  getSettings: () => call(() => http<WorkspaceSettings>('/settings'), () => db.get().settings),

  updateSettings: (settings: WorkspaceSettings) =>
    call(
      () => http<WorkspaceSettings>('/settings', json('PUT', settings)),
      () => {
        requirePermission('settings:manage')
        db.update((s) => {
          s.settings = settings
          s.auditLogs.push(audit('key-rotation', 'Workspace settings updated'))
        })
        return settings
      },
    ),

  listMembers: () => call(() => http<User[]>('/members'), () => db.get().users),

  inviteMember: (email: string, name: string, role: Role) =>
    call(
      () => http<User>('/members', json('POST', { email, name, role })),
      () => {
        requirePermission('members:manage')
        if (db.get().users.some((u) => u.email.toLowerCase() === email.toLowerCase())) throw new ApiError(`${email} is already a member.`, 409)
        const user: User = { id: uid('u'), name: name.trim() || email.split('@')[0]!, email: email.trim(), role, initials: initials(name || email), mfaEnabled: false, kycStatus: 'none', active: true, joinedAt: new Date().toISOString().slice(0, 10) }
        db.update((s) => {
          s.users.push(user)
          s.notifications.unshift(notify({ type: 'invite', channel: 'email', title: 'Invitation sent', body: `${user.email} was invited as ${role}. The invitation email is queued.`, link: '/settings' }))
          s.auditLogs.push(audit('share', `Member invited: ${user.email} (${role})`))
        })
        return user
      },
    ),

  updateMember: (id: string, patch: Partial<Pick<User, 'role' | 'active' | 'mfaEnabled'>>) =>
    call(
      () => http<User>(`/members/${id}`, json('PATCH', patch)),
      () => {
        const self = me()
        const editingSelfMfa = id === self.id && Object.keys(patch).every((k) => k === 'mfaEnabled')
        if (!editingSelfMfa) requirePermission('members:manage')
        if (id === self.id && (patch.role && patch.role !== 'admin' || patch.active === false)) throw new ApiError('You can’t remove your own administrator access.', 409)
        let user!: User
        db.update((s) => {
          const u = s.users.find((x) => x.id === id)!
          Object.assign(u, patch)
          s.auditLogs.push(audit('key-rotation', `Member updated: ${u.email}`))
          user = structuredClone(u)
        })
        return user
      },
    ),

  listNotifications: () =>
    call(
      () => http<AppNotification[]>('/notifications'),
      () => [...db.get().notifications].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    ),

  markNotificationsRead: (ids?: string[]) =>
    call<void>(
      () => http('/notifications/read', json('POST', { ids })),
      () => {
        db.update((s) => s.notifications.forEach((n) => (!ids || ids.includes(n.id)) && (n.read = true)))
      },
    ),

  /** Mock-only: restore the seed data. */
  resetDemoData: () => {
    db.reset()
  },
}
