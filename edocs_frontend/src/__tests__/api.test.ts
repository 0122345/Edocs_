import { beforeEach, describe, expect, it } from 'vitest'
import { api } from '../services/api'
import { db } from '../services/mockDb'
import { DEMO_MFA_CODE, DEMO_PASSWORD } from '../data/mock'

async function signInAs(email: string) {
  const r = await api.login(email, DEMO_PASSWORD)
  if (r.kind === 'mfa') await api.verifyMfa(r.challengeId, DEMO_MFA_CODE)
}

beforeEach(async () => {
  db.reset()
  await api.logout()
})

describe('auth', () => {
  it('rejects a wrong password', async () => {
    await expect(api.login('s.jenkins@acme.corp', 'nope')).rejects.toThrow('Email or password is incorrect.')
  })

  it('asks the administrator for an MFA code and checks it', async () => {
    const r = await api.login('j.davis@acme.corp', DEMO_PASSWORD)
    expect(r.kind).toBe('mfa')
    if (r.kind !== 'mfa') return
    await expect(api.verifyMfa(r.challengeId, '000000')).rejects.toThrow()
    const session = await api.verifyMfa(r.challengeId, DEMO_MFA_CODE)
    expect(session.user.role).toBe('admin')
  })

  it('blocks deactivated members', async () => {
    await signInAs('j.davis@acme.corp')
    await api.updateMember('u-auditor', { active: false })
    await api.logout()
    await expect(api.login('e.rostova@acme.corp', DEMO_PASSWORD)).rejects.toThrow('deactivated')
  })
})

describe('documents', () => {
  it('creates a draft from a template and records it in the audit log', async () => {
    await signInAs('s.jenkins@acme.corp')
    const doc = await api.createDocument({ title: 'Test NDA', category: 'Confidentiality', templateId: 'tp2', parties: [{ name: 'A', email: 'a@x.io', role: 'signer', order: 1 }] })
    expect(doc.status).toBe('draft')
    expect(doc.content).toContain('Confidential information')
    expect(db.get().auditLogs.some((a) => a.documentId === doc.id && a.event === 'Document created')).toBe(true)
  })

  it('refuses edits from roles without document:edit', async () => {
    await signInAs('m.vance@partnercorp.io')
    await expect(api.saveDocument('dpa-initech', '<p>x</p>')).rejects.toThrow('Your role does not allow this action.')
  })

  it('only lets signers see documents they are party to', async () => {
    await signInAs('m.vance@partnercorp.io')
    const docs = await api.listDocuments()
    expect(docs.map((d) => d.id)).toEqual(['q3-master-agreement'])
  })

  it('only deletes drafts', async () => {
    await signInAs('j.davis@acme.corp')
    await expect(api.deleteDocument('vendor-nda-2026')).rejects.toThrow('Only drafts')
    await api.deleteDocument('dpa-initech')
    expect(db.get().documents.find((d) => d.id === 'dpa-initech')).toBeUndefined()
  })
})

describe('signing', () => {
  it('rejects a wrong one-time code', async () => {
    await signInAs('m.vance@partnercorp.io')
    await api.sendOtp('q3-master-agreement')
    await expect(api.executeSignature('q3-master-agreement', { mode: 'type', otp: '000000', signature: 'Marcus Vance' })).rejects.toThrow('wrong or has expired')
  })

  it('seals and anchors the document when the last signer signs', async () => {
    await signInAs('m.vance@partnercorp.io')
    await api.sendOtp('q3-master-agreement')
    const otp = db.get().otps['q3-master-agreement']!
    const r = await api.executeSignature('q3-master-agreement', { mode: 'type', otp, signature: 'Marcus Vance' })
    expect(r.document.status).toBe('signed')
    expect(r.document.sha256).toMatch(/^[0-9a-f]{64}$/)
    expect(db.get().notifications[0]?.type).toBe('signed')
  })
})

describe('self-service accounts', () => {
  const newcomer = { orgName: 'NewCo', name: 'Nia Founder', email: 'nia@newco.example', password: 'Launch2026rocket' }

  it('signs up an administrator who must confirm the email first', async () => {
    await expect(api.signup({ ...newcomer, password: 'short1' })).rejects.toThrow('at least 10 characters')
    const r = await api.signup(newcomer)
    expect(r.kind).toBe('verify')
    if (r.kind === 'session') return
    expect((await api.login(newcomer.email, newcomer.password)).kind).toBe('verify')
    const session = await api.verifyMfa(r.challengeId, DEMO_MFA_CODE)
    expect(session.user.role).toBe('admin')
    await api.logout()
    expect((await api.login(newcomer.email, newcomer.password)).kind).toBe('session')
    await expect(api.signup(newcomer)).rejects.toThrow('already exists')
  })

  it('changes and resets a password', async () => {
    await signInAs('s.jenkins@acme.corp')
    await expect(api.changePassword('wrong-password', 'Replaced2026pw')).rejects.toThrow('current password is incorrect')
    await api.changePassword(DEMO_PASSWORD, 'Replaced2026pw')
    await api.logout()
    await expect(api.login('s.jenkins@acme.corp', DEMO_PASSWORD)).rejects.toThrow('incorrect')
    await api.requestPasswordReset('s.jenkins@acme.corp')
    await expect(api.resetPassword('s.jenkins@acme.corp', '000000', 'Recovered2026now')).rejects.toThrow('wrong or has expired')
    await api.resetPassword('s.jenkins@acme.corp', DEMO_MFA_CODE, 'Recovered2026now')
    expect((await api.login('s.jenkins@acme.corp', 'Recovered2026now')).kind).toBe('session')
  })
})
