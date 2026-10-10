import type {
  ActivityItem,
  AppNotification,
  AuditLogEntry,
  Bottleneck,
  ComplianceControl,
  DocumentRecord,
  Kpi,
  RiskBand,
  RoutingRule,
  Template,
  User,
  WorkspaceSettings,
} from './types'

// ---------------------------------------------------------------------------
// Seed data for the in-browser mock backend (src/services/mockDb.ts).
// Demo accounts all use the password below; the MFA code is fixed in mock mode.
// ---------------------------------------------------------------------------
export const DEMO_PASSWORD = 'Demo@2026'
export const DEMO_MFA_CODE = '246810'

export const organization = { name: 'Acme Corporation', tier: 'Enterprise Tier' }

export const seedUsers: User[] = [
  { id: 'u-admin', name: 'Jordan Davis', email: 'j.davis@acme.corp', role: 'admin', initials: 'JD', mfaEnabled: true, kycStatus: 'verified', active: true, joinedAt: '2025-01-12' },
  { id: 'u-legal', name: 'Sarah Jenkins', email: 's.jenkins@acme.corp', role: 'legal', initials: 'SJ', mfaEnabled: false, kycStatus: 'verified', active: true, joinedAt: '2025-03-02' },
  { id: 'u-signer', name: 'Marcus Vance', email: 'm.vance@partnercorp.io', role: 'signer', initials: 'MV', mfaEnabled: false, kycStatus: 'verified', active: true, joinedAt: '2026-06-18' },
  { id: 'u-auditor', name: 'Elena Rostova', email: 'e.rostova@acme.corp', role: 'auditor', initials: 'ER', mfaEnabled: false, kycStatus: 'pending', active: true, joinedAt: '2025-09-30' },
]

export const kpis: Kpi[] = [
  { id: 'velocity', label: 'Contract velocity', value: '1.8', unit: 'days', delta: '−0.4 days vs last month', deltaTone: 'seal', footnote: 'Average time from send to signature' },
  { id: 'pending', label: 'Pending signatures', value: '42', delta: '12 need urgent review', deltaTone: 'amber', footnote: 'Waiting on a counterparty' },
  { id: 'compliance', label: 'Compliance posture', value: '99.8%', delta: 'eIDAS & SOC 2 verified', deltaTone: 'seal', footnote: 'No audit discrepancies found' },
  { id: 'volume', label: 'Monthly volume', value: '1,248', delta: '+14.2% year over year', deltaTone: 'seal', footnote: 'Contracts signed digitally' },
]

export const riskBands: Record<'weekly' | 'monthly', RiskBand[]> = {
  monthly: [
    { level: 'low', label: 'Low risk (standard MSA / NDA)', percent: 78, docs: 974 },
    { level: 'moderate', label: 'Moderate risk (custom indemnity clauses)', percent: 18, docs: 225 },
    { level: 'high', label: 'High risk (needs legal review)', percent: 4, docs: 49 },
  ],
  weekly: [
    { level: 'low', label: 'Low risk (standard MSA / NDA)', percent: 71, docs: 213 },
    { level: 'moderate', label: 'Moderate risk (custom indemnity clauses)', percent: 22, docs: 66 },
    { level: 'high', label: 'High risk (needs legal review)', percent: 7, docs: 21 },
  ],
}

export const bottlenecks: Bottleneck[] = [
  { stage: 'Legal counsel review', avgDays: 3.4, note: 'Slowest on enterprise procurement agreements.' },
  { stage: 'KYC identity verification', avgDays: 1.9, note: 'Biometric liveness checks pending for external signatories.' },
]

export const seedRoutingRules: RoutingRule[] = [
  { id: 'r1', label: 'Fast-track low-risk NDAs', description: 'Skip legal review when the AI risk score is low and the template is unmodified.', enabled: false },
  { id: 'r2', label: 'Escalate stalled legal reviews', description: 'Notify the legal lead when a review waits more than 2 days.', enabled: true },
  { id: 'r3', label: 'Run KYC in parallel', description: 'Start identity checks for external signers while legal review is still open.', enabled: false },
  { id: 'r4', label: 'Route high-value contracts to the CFO', description: 'Add a CFO approval step when contract value exceeds $250,000.', enabled: true },
]

const MSA_CONTENT = `
<h2>1. Executive summary</h2>
<p>Edocs is an enterprise platform that replaces paper-based contract management with a secure, legally binding digital process. It covers the full e-contract lifecycle: drafting and negotiation, e-signature, automated approval workflows and long-term compliant archiving.</p>
<h2>2. Problem statement: the paper problem</h2>
<p>Contract management remains one of the last strongholds of paper. Organizations face linked problems across operational efficiency, cost and regulatory friction.</p>
<ul>
<li><strong>Operational inefficiency:</strong> printing, scanning, couriering and manual filing add 3–5 days to average turnaround.</li>
<li><strong>Cost burden:</strong> organizations spend $20–$50 per paper contract on printing, shipping, storage and labor.</li>
<li><strong>Risk of loss and tampering:</strong> physical documents are exposed to fire, flooding, misplacement and unauthorized alteration.</li>
</ul>
<h2>3. Solution overview and key features</h2>
<p>Edocs is a modular, API-first platform that can be deployed as cloud SaaS, private cloud or on-premises. <del>The platform mandates strict single-tenant architecture without multi-region failover.</del> <ins>Multi-region active-active high availability with instant cryptographic verification is supported on every enterprise tier.</ins></p>
<h2>4. Security and compliance framework</h2>
<p>Regulatory compliance includes the eIDAS Regulation (EU), supporting simple, advanced and qualified electronic signatures. The ESIGN Act and UETA (USA) guarantee intent to sign, consent to electronic records and association of the signature with the record.</p>
`.trim()

const NDA_CONTENT = `
<h2>1. Purpose</h2>
<p>The parties wish to exchange confidential information to evaluate a potential business relationship (the “Purpose”).</p>
<h2>2. Confidential information</h2>
<p>“Confidential Information” means any non-public information disclosed by either party, in any form, that is marked confidential or would reasonably be understood to be confidential.</p>
<h2>3. Obligations</h2>
<p>The receiving party shall use Confidential Information only for the Purpose and protect it with at least reasonable care.</p>
<h2>4. Term</h2>
<p>This Agreement lasts {{term_years}} years from the Effective Date.</p>
`.trim()

const DPA_CONTENT = `
<h2>1. Subject matter</h2>
<p>This addendum governs the processing of personal data by {{processor_name}} on behalf of {{controller_name}}.</p>
<h2>2. Data residency</h2>
<p>All personal data is stored in the EU (Frankfurt) region unless the controller agrees otherwise in writing.</p>
<h2>3. Sub-processors</h2>
<p>The processor shall not engage sub-processors without prior written authorization from the controller.</p>
`.trim()

const SLA_CONTENT = `
<h2>1. Service availability</h2>
<p>The Provider guarantees {{uptime}} monthly uptime, measured at the API gateway.</p>
<h2>2. Service credits</h2>
<p>If availability drops below the guaranteed level, the Client receives service credits as set out in Schedule C.</p>
`.trim()

const BLANK_CONTENT = '<h2>1. Parties</h2><p>Describe the parties to this agreement.</p><h2>2. Terms</h2><p>Write the terms here.</p>'

export const seedTemplates: Template[] = [
  { id: 'tp1', name: 'Master services agreement', category: 'Commercial', uses: 412, updated: 'Sept 28, 2026', content: MSA_CONTENT, variables: ['client_name', 'effective_date'] },
  { id: 'tp2', name: 'Mutual NDA', category: 'Confidentiality', uses: 1033, updated: 'Sept 12, 2026', content: NDA_CONTENT, variables: ['term_years'] },
  { id: 'tp3', name: 'Data processing addendum (GDPR)', category: 'Privacy', uses: 288, updated: 'Aug 30, 2026', content: DPA_CONTENT, variables: ['processor_name', 'controller_name'] },
  { id: 'tp4', name: 'Enterprise SLA', category: 'Commercial', uses: 167, updated: 'Aug 2, 2026', content: SLA_CONTENT, variables: ['uptime'] },
  { id: 'tp5', name: 'Reseller addendum', category: 'Partnerships', uses: 74, updated: 'Jul 19, 2026', content: BLANK_CONTENT, variables: [] },
  { id: 'tp6', name: 'Employment offer letter', category: 'HR', uses: 539, updated: 'Jul 3, 2026', content: BLANK_CONTENT, variables: ['candidate_name', 'start_date', 'salary'] },
]

export const BLANK_TEMPLATE_CONTENT = BLANK_CONTENT

export const seedDocuments: DocumentRecord[] = [
  {
    id: 'q3-master-agreement',
    title: 'Q3 Master Services Agreement',
    category: 'Commercial',
    templateId: 'tp1',
    format: 'pdf',
    status: 'out_for_signature',
    version: 3,
    content: MSA_CONTENT,
    parties: [
      { id: 'p1', name: 'Jordan Davis', email: 'j.davis@acme.corp', role: 'signer', order: 1, signedAt: '2026-09-14T08:30:00Z' },
      { id: 'p2', name: 'Marcus Vance', email: 'm.vance@partnercorp.io', role: 'signer', order: 2 },
    ],
    value: 480000,
    effectiveDate: '2026-10-24',
    expiryDate: '2029-10-23',
    ownerId: 'u-legal',
    createdAt: '2026-09-01T09:00:00Z',
    updatedAt: '2026-10-02T14:25:00Z',
    legalHold: true,
    holdUntil: 'Oct 14, 2031',
    workflowStep: 4,
    comments: [
      { id: 'cm1', author: 'Sarah Jenkins', initials: 'SJ', ago: '10m ago', body: 'We need to make sure the indemnity cap lines up with the standard SaaS SLAs in section 3.' },
      { id: 'cm2', author: 'Marcus Vance', initials: 'MV', ago: '1h ago', body: 'Agreed on the updated high-availability clause. Looks solid.' },
    ],
    versions: [
      { id: 'v12', label: 'Version 1.2', when: 'Today, 14:25', by: 'S. Jenkins', note: 'Added indemnification cap', current: true },
      { id: 'v11', label: 'Version 1.1', when: 'Yesterday, 09:12', by: 'M. Vance', note: 'Accepted HA clause redline' },
      { id: 'v10', label: 'Version 1.0 (initial draft)', when: 'Sept 1, 2026', by: 'E. Rostova', note: 'Created from MSA template' },
    ],
    sizeKb: 4300,
    pages: 34,
  },
  {
    id: 'vendor-nda-2026',
    title: 'Vendor NDA 2026',
    category: 'Confidentiality',
    templateId: 'tp2',
    format: 'pdf',
    status: 'signed',
    version: 1,
    content: NDA_CONTENT.replace('{{term_years}}', '3'),
    parties: [
      { id: 'p1', name: 'Jordan Davis', email: 'j.davis@acme.corp', role: 'signer', order: 1, signedAt: '2026-09-20T10:00:00Z' },
      { id: 'p2', name: 'CFO, PartnerCorp', email: 'cfo@partnercorp.io', role: 'signer', order: 2, signedAt: '2026-09-21T16:40:00Z' },
    ],
    ownerId: 'u-legal',
    createdAt: '2026-09-18T09:00:00Z',
    updatedAt: '2026-09-21T16:40:00Z',
    legalHold: false,
    workflowStep: 5,
    comments: [],
    versions: [{ id: 'v3', label: 'Version 1.0', when: 'Sept 18, 2026', by: 'S. Jenkins', current: true }],
    sha256: '9b74c9897bac770ffc029102a200c5de2f0c1a3b7e4d2a90b1c8e6f4d5a3b2c1',
    txId: '0x99cc…114f',
    sizeKb: 820,
    pages: 6,
  },
  {
    id: 'enterprise-sla-v2',
    title: 'Enterprise SLA v2',
    category: 'Commercial',
    templateId: 'tp4',
    format: 'docx',
    status: 'in_review',
    version: 2,
    content: SLA_CONTENT.replace('{{uptime}}', '99.95%'),
    parties: [{ id: 'p1', name: 'Legal, Globex', email: 'legal@globex.org', role: 'approver', order: 1 }],
    value: 120000,
    ownerId: 'u-legal',
    createdAt: '2026-09-25T09:00:00Z',
    updatedAt: '2026-10-02T12:05:00Z',
    legalHold: false,
    workflowStep: 2,
    comments: [],
    versions: [{ id: 'v2', label: 'Version 1.1', when: 'Today, 12:05', by: 'Legal, Globex', note: 'Clause redline', current: true }],
    sizeKb: 210,
    pages: 9,
  },
  {
    id: 'dpa-initech',
    title: 'Data Processing Addendum – Initech',
    category: 'Privacy',
    templateId: 'tp3',
    format: 'docx',
    status: 'draft',
    version: 1,
    content: DPA_CONTENT.replace('{{processor_name}}', 'Acme Corporation').replace('{{controller_name}}', 'Initech'),
    parties: [{ id: 'p1', name: 'Ops, Initech', email: 'ops@initech.example', role: 'signer', order: 1 }],
    ownerId: 'u-admin',
    createdAt: '2026-10-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
    legalHold: false,
    workflowStep: 1,
    comments: [],
    versions: [{ id: 'v1', label: 'Version 1.0 (initial draft)', when: 'Yesterday', by: 'J. Davis', current: true }],
    sizeKb: 95,
    pages: 4,
  },
  {
    id: 'supplier-terms-2025',
    title: 'Supplier Terms 2025',
    category: 'Commercial',
    format: 'pdf',
    status: 'archived',
    version: 1,
    content: '<h2>1. Supply</h2><p>The Supplier shall deliver goods according to purchase orders issued under these terms.</p>',
    parties: [{ id: 'p1', name: 'Global Logistics Partner', email: 'contracts@glp.example', role: 'signer', order: 1, signedAt: '2025-02-10T11:00:00Z' }],
    ownerId: 'u-admin',
    createdAt: '2025-01-20T09:00:00Z',
    updatedAt: '2025-02-10T11:00:00Z',
    legalHold: true,
    holdUntil: 'Feb 10, 2032',
    workflowStep: 5,
    comments: [],
    versions: [{ id: 'v5', label: 'Version 1.0', when: 'Feb 10, 2025', by: 'J. Davis', current: true }],
    sha256: '4c1f0a92d7be6e3015a8f7d2c9b4e61a0f3d8c27b5e914a6d0c3f7b2e8a1d596',
    txId: '0x61f4…90ee',
    sizeKb: 1600,
    pages: 18,
  },
]

export const seedAuditLogs: AuditLogEntry[] = [
  { id: 'a1', kind: 'signature', event: 'E-signature executed', document: 'Q3-Master-Agreement.pdf', documentId: 'q3-master-agreement', actor: 'j.davis@acme.corp', origin: '192.168.1.45', timestamp: 'Today, 14:22 UTC', at: '2026-10-02T14:22:00Z', txHash: '0x7f8c…3b9a', status: 'verified' },
  { id: 'a2', kind: 'redline', event: 'Clause redline modified', document: 'Enterprise-SLA-v2.docx', documentId: 'enterprise-sla-v2', actor: 'legal@globex.org', origin: '10.0.4.112', timestamp: 'Today, 12:05 UTC', at: '2026-10-02T12:05:00Z', txHash: '0x34a1…9e22', status: 'verified' },
  { id: 'a3', kind: 'view', event: 'Document viewed', document: 'Vendor-NDA-2026.pdf', documentId: 'vendor-nda-2026', actor: 'cfo@partnercorp.io', origin: '172.16.8.2', timestamp: 'Yesterday, 19:40 UTC', at: '2026-10-01T19:40:00Z', txHash: '0x99cc…114f', status: 'verified' },
  { id: 'a4', kind: 'key-rotation', event: 'Encryption key rotated', document: 'System-Vault-Master', actor: 'system-daemon', origin: 'Internal KMS', timestamp: 'Sept 24, 04:00 UTC', at: '2026-09-24T04:00:00Z', txHash: '0x11be…88a1', status: 'verified' },
  { id: 'a5', kind: 'signature', event: 'E-signature executed', document: 'Vendor-NDA-2026.pdf', documentId: 'vendor-nda-2026', actor: 'cfo@partnercorp.io', origin: '172.16.8.9', timestamp: 'Sept 21, 16:40 UTC', at: '2026-09-21T16:40:00Z', txHash: '0x5d02…a7c3', status: 'verified' },
  { id: 'a6', kind: 'anchor', event: 'Merkle root anchored', document: 'Q3-Master-Agreement.pdf', documentId: 'q3-master-agreement', actor: 'anchor-service', origin: 'Ethereum Mainnet', timestamp: 'Sept 14, 08:42 UTC', at: '2026-09-14T08:42:19Z', txHash: '0x9f8b…2c1a', status: 'verified' },
  { id: 'a7', kind: 'legal-hold', event: 'Legal hold applied', document: 'Q3-Master-Agreement.pdf', documentId: 'q3-master-agreement', actor: 'j.sterling@acme.corp', origin: '192.168.1.12', timestamp: 'Sept 14, 08:41 UTC', at: '2026-09-14T08:41:00Z', txHash: '0x0c71…de40', status: 'verified' },
  { id: 'a8', kind: 'signature', event: 'Multi-party QES signature executed', document: 'Q3-Master-Agreement.pdf', documentId: 'q3-master-agreement', actor: 'j.davis@acme.corp', origin: 'Qualified TSP', timestamp: 'Sept 14, 08:35 UTC', at: '2026-09-14T08:35:12Z', txHash: '0xab43…7f10', status: 'verified' },
  { id: 'a9', kind: 'view', event: 'Document viewed', document: 'Enterprise-SLA-v2.docx', documentId: 'enterprise-sla-v2', actor: 'auditor@kpmg.example', origin: '81.20.4.19', timestamp: 'Sept 10, 15:02 UTC', at: '2026-09-10T15:02:00Z', txHash: '0xe210…44b8', status: 'verified' },
  { id: 'a10', kind: 'signature', event: 'E-signature rejected', document: 'Supplier-Terms-2026.pdf', actor: 'ops@initech.example', origin: '10.2.0.31', timestamp: 'Sept 9, 09:58 UTC', at: '2026-09-09T09:58:00Z', txHash: '0x3e9a…c5d7', status: 'failed' },
  { id: 'a11', kind: 'legal-hold', event: 'Legal hold applied', document: 'Supplier-Terms-2025.pdf', documentId: 'supplier-terms-2025', actor: 'j.davis@acme.corp', origin: '192.168.1.45', timestamp: 'Sept 2, 10:00 UTC', at: '2026-09-02T10:00:00Z', txHash: '0x7aa0…1b62', status: 'pending' },
  { id: 'a12', kind: 'anchor', event: 'Merkle root anchored', document: 'Batch-2026-08-31', actor: 'anchor-service', origin: 'Ethereum Mainnet', timestamp: 'Aug 31, 08:40 UTC', at: '2026-08-31T08:40:00Z', txHash: '0x61f4…90ee', status: 'verified' },
]

export const auditLogBaseline = 1236 // historical entries not shipped to the browser

export const seedNotifications: AppNotification[] = [
  { id: 'n1', type: 'signature_request', channel: 'email', title: 'Signature requested', body: 'Marcus Vance was asked to sign Q3 Master Services Agreement.', link: '/workflow/q3-master-agreement', createdAt: '2026-10-02T14:25:00Z', read: false },
  { id: 'n2', type: 'system', channel: 'in_app', title: 'Legal review is stalling', body: 'Enterprise SLA v2 has waited 3 days for legal review.', link: '/editor/enterprise-sla-v2', createdAt: '2026-10-02T09:00:00Z', read: false },
  { id: 'n3', type: 'signed', channel: 'email', title: 'Vendor NDA 2026 fully signed', body: 'All parties signed. The sealed copy is in the archive.', link: '/archive?doc=vendor-nda-2026', createdAt: '2026-09-21T16:41:00Z', read: true },
]

export const seedComplianceControls: ComplianceControl[] = [
  { id: 'cc1', name: 'eIDAS qualified signatures', framework: 'eIDAS', status: 'pass', checked: '2 hours ago' },
  { id: 'cc2', name: 'ESIGN Act & UETA consent capture', framework: 'ESIGN / UETA', status: 'pass', checked: '2 hours ago' },
  { id: 'cc3', name: 'Encryption at rest (AES-256-GCM)', framework: 'SOC 2 CC6.1', status: 'pass', checked: 'Today, 06:00' },
  { id: 'cc4', name: 'GDPR data residency (Frankfurt)', framework: 'GDPR Art. 44', status: 'pass', checked: 'Today, 06:00' },
  { id: 'cc5', name: 'Retention schedule on archived contracts', framework: 'ISO 27001 A.5.33', status: 'warn', checked: 'Yesterday' },
  { id: 'cc6', name: 'Access review for privileged roles', framework: 'SOC 2 CC6.2', status: 'warn', checked: '3 days ago' },
]

export const seedSettings: WorkspaceSettings = {
  orgName: organization.name,
  adminEmail: 'j.davis@acme.corp',
  signatureLevel: 'qes',
  retentionYears: 7,
  require2fa: true,
}

export const activityFeed: ActivityItem[] = [
  { id: 'f1', icon: 'identity', text: 'Dr. Aris Thorne verified their identity through the SwissID eIDAS gateway.', meta: '2 mins ago · IP 194.154.22.10' },
  { id: 'f2', icon: 'lock', text: 'Document hash generated and anchored to Ethereum L2.', meta: '14 mins ago · Tx #88412' },
  { id: 'f3', icon: 'edit', text: 'Sarah Jenkins approved clause 14.2 (liability cap).', meta: '1 hour ago · Frankfurt node' },
]
