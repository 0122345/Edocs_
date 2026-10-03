// Domain types. They mirror the entities in documentation/edocs_class_diagram_v4.png
// (User, Organization, Membership, Document/Contract, Party, Template, Signature,
// Workflow, AuditLog, Notification) so the backend can return them unchanged.

export type Role = 'admin' | 'legal' | 'signer' | 'auditor'

export interface User {
  id: string
  name: string
  email: string
  role: Role
  initials: string
  mfaEnabled: boolean
  kycStatus: 'verified' | 'pending' | 'none'
  active: boolean
  joinedAt: string
}

export interface Session {
  token: string
  user: User
}

export interface Kpi {
  id: string
  label: string
  value: string
  unit?: string
  delta: string
  deltaTone: 'seal' | 'amber' | 'alert'
  footnote: string
}

export interface RiskBand {
  level: 'low' | 'moderate' | 'high'
  label: string
  percent: number
  docs: number
}

export interface Bottleneck {
  stage: string
  avgDays: number
  note: string
}

export interface RoutingRule {
  id: string
  label: string
  description: string
  enabled: boolean
}

export type AuditEventKind = 'signature' | 'redline' | 'view' | 'key-rotation' | 'anchor' | 'legal-hold' | 'create' | 'share'

export interface AuditLogEntry {
  id: string
  kind: AuditEventKind
  event: string
  document: string
  documentId?: string
  actor: string
  origin: string
  timestamp: string
  at: string
  txHash: string
  status: 'verified' | 'pending' | 'failed'
}

export type DocStatus = 'draft' | 'in_review' | 'out_for_signature' | 'signed' | 'archived'

export interface Party {
  id: string
  name: string
  email: string
  role: 'signer' | 'approver' | 'viewer'
  order: number
  signedAt?: string
}

export interface Comment {
  id: string
  author: string
  initials: string
  ago: string
  body: string
  resolved?: boolean
}

export interface Version {
  id: string
  label: string
  when: string
  by: string
  note?: string
  current?: boolean
}

export interface DocumentRecord {
  id: string
  title: string
  category: string
  templateId?: string
  format: 'pdf' | 'docx'
  status: DocStatus
  version: number
  content: string
  parties: Party[]
  value?: number
  effectiveDate?: string
  expiryDate?: string
  ownerId: string
  createdAt: string
  updatedAt: string
  legalHold: boolean
  holdUntil?: string
  workflowStep: number
  comments: Comment[]
  versions: Version[]
  sha256?: string
  txId?: string
  sizeKb: number
  pages: number
}

export interface Collaborator {
  id: string
  name: string
  role: string
  presence: 'active' | 'viewing' | 'away'
  initials: string
}

export type StepStatus = 'completed' | 'approved' | 'verified' | 'in-progress' | 'waiting'

export interface WorkflowStep {
  id: number
  title: string
  description: string
  status: StepStatus
}

export interface ActivityItem {
  id: string
  icon: 'identity' | 'lock' | 'edit'
  text: string
  meta: string
}

export interface TimelineEntry {
  id: string
  kind: 'anchor' | 'legal-hold' | 'signature'
  title: string
  timestamp: string
  body: string
  hash?: string
}

export interface Template {
  id: string
  name: string
  category: string
  uses: number
  updated: string
  content: string
  variables: string[]
}

export type NotificationChannel = 'email' | 'sms' | 'in_app'

export interface AppNotification {
  id: string
  type: 'signature_request' | 'otp' | 'invite' | 'share' | 'signed' | 'system'
  channel: NotificationChannel
  title: string
  body: string
  link?: string
  createdAt: string
  read: boolean
}

export interface ComplianceControl {
  id: string
  name: string
  framework: string
  status: 'pass' | 'warn'
  checked: string
}

export interface WorkspaceSettings {
  orgName: string
  adminEmail: string
  signatureLevel: 'ses' | 'aes' | 'qes'
  retentionYears: number
  require2fa: boolean
}

export interface NewDocumentInput {
  title: string
  category: string
  templateId?: string
  parties: Omit<Party, 'id' | 'signedAt'>[]
  value?: number
  effectiveDate?: string
  expiryDate?: string
}
