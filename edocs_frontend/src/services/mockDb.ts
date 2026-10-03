import * as seed from '../data/mock'
import type {
  AppNotification,
  AuditLogEntry,
  ComplianceControl,
  DocumentRecord,
  RoutingRule,
  Template,
  User,
  WorkspaceSettings,
} from '../data/types'

/**
 * In-browser stand-in for the backend. State persists to localStorage so the
 * demo survives reloads, and every write notifies subscribers so open screens
 * refresh. Delete this module once the real API is live.
 */
export interface DbState {
  users: User[]
  documents: DocumentRecord[]
  templates: Template[]
  auditLogs: AuditLogEntry[]
  notifications: AppNotification[]
  complianceControls: ComplianceControl[]
  routingRules: RoutingRule[]
  settings: WorkspaceSettings
  /** Pending one-time passwords keyed by document id. */
  otps: Record<string, string>
}

const STORAGE_KEY = 'edocs.mockdb.v1'

function seedState(): DbState {
  return structuredClone({
    users: seed.seedUsers,
    documents: seed.seedDocuments,
    templates: seed.seedTemplates,
    auditLogs: seed.seedAuditLogs,
    notifications: seed.seedNotifications,
    complianceControls: seed.seedComplianceControls,
    routingRules: seed.seedRoutingRules,
    settings: seed.seedSettings,
    otps: {},
  })
}

function storage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

function load(): DbState {
  try {
    const raw = storage()?.getItem(STORAGE_KEY)
    if (raw) return { ...seedState(), ...(JSON.parse(raw) as Partial<DbState>) }
  } catch {
    // Corrupt or blocked storage: fall back to the seed.
  }
  return seedState()
}

let state: DbState = load()
const listeners = new Set<() => void>()

export const db = {
  get: (): DbState => state,
  update(mutate: (draft: DbState) => void) {
    const next = structuredClone(state)
    mutate(next)
    state = next
    try {
      storage()?.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // Storage full or blocked; keep the in-memory copy.
    }
    listeners.forEach((l) => l())
  },
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  reset() {
    state = seedState()
    storage()?.removeItem(STORAGE_KEY)
    listeners.forEach((l) => l())
  },
}

export function uid(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

export function fakeTxHash() {
  const hex = () => Math.floor(Math.random() * 0xffff).toString(16).padStart(4, '0')
  return `0x${hex()}…${hex()}`
}

export function displayTimestamp(d = new Date()) {
  const time = d.toISOString().slice(11, 16)
  return `${d.toDateString() === new Date().toDateString() ? 'Today' : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}, ${time} UTC`
}
