import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'

// Drives the real API client against a running backend. Run: EDOCS_LIVE_API=http://localhost:8080/api npm test
const LIVE = process.env.EDOCS_LIVE_API
const MAILPIT = process.env.EDOCS_MAILPIT ?? 'http://localhost:8025'
const PASSWORD = process.env.EDOCS_DEMO_PASSWORD ?? 'Demo@2026'

type Api = (typeof import('../services/api'))['api']
let api: Api

async function mailedCode(to: string): Promise<string> {
  for (let i = 0; i < 30; i++) {
    const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}&limit=5`)
    const { messages } = (await res.json()) as { messages: { ID: string; Snippet: string }[] }
    const hit = messages.map((m) => ({ id: m.ID, code: /\b(\d{6})\b/.exec(m.Snippet)?.[1] })).find((m) => m.code)
    if (hit) {
      await fetch(`${MAILPIT}/api/v1/messages`, { method: 'DELETE', body: JSON.stringify({ IDs: [hit.id] }) })
      return hit.code!
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`No code emailed to ${to}`)
}

async function clearMail(to: string) {
  await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`, { method: 'DELETE' })
}

describe.skipIf(!LIVE)('live backend', () => {
  beforeAll(async () => {
    vi.stubEnv('VITE_API_BASE_URL', LIVE!)
    vi.resetModules()
    api = (await import('../services/api')).api
  })
  afterAll(() => vi.unstubAllEnvs())

  it('rejects a wrong password with the server message', async () => {
    await expect(api.login('s.jenkins@acme.corp', 'wrong-password')).rejects.toMatchObject({ status: 401 })
  })

  it('signs the administrator in with the MFA code delivered by email', async () => {
    await clearMail('j.davis@acme.corp')
    const r = await api.login('j.davis@acme.corp', PASSWORD)
    expect(r.kind).toBe('mfa')
    if (r.kind !== 'mfa') return
    const session = await api.verifyMfa(r.challengeId, await mailedCode('j.davis@acme.corp'))
    expect(session.user.role).toBe('admin')
    expect(api.session()?.token).toBe(session.token)
  })

  let docId = ''
  it('creates, edits and sends a document, notifying subscribers after each write', async () => {
    const changes = vi.fn()
    const off = api.subscribe(changes)
    const [template] = await api.getTemplates()
    const doc = await api.createDocument({
      title: `Live client test ${Date.now()}`,
      category: 'Commercial',
      templateId: template!.id,
      value: 5000,
      effectiveDate: '2026-12-01',
      expiryDate: '2027-12-01',
      parties: [{ name: 'Marcus Vance', email: 'm.vance@partnercorp.io', role: 'signer', order: 1 }],
    })
    docId = doc.id
    expect(doc.status).toBe('draft')
    const saved = await api.saveDocument(doc.id, '<p>Edited from the live client test.</p>', 'live test')
    expect(saved.version).toBe(2)
    expect((await api.sendForSignature(doc.id)).status).toBe('out_for_signature')
    expect(changes).toHaveBeenCalledTimes(3)
    off()
    expect((await api.listDocuments()).some((d) => d.id === doc.id)).toBe(true)
    expect((await api.getDashboard()).kpis).toHaveLength(4)
  })

  it('lets the signer sign with the emailed OTP and seals the document', async () => {
    await api.logout()
    await clearMail('m.vance@partnercorp.io')
    const r = await api.login('m.vance@partnercorp.io', PASSWORD)
    expect(r.kind).toBe('session')
    await api.sendOtp(docId)
    const otp = await mailedCode('m.vance@partnercorp.io')
    await expect(api.deleteDocument(docId)).rejects.toMatchObject({ status: 403 })
    const signature = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
    const { document } = await api.executeSignature(docId, { mode: 'draw', otp, signature })
    expect(document.parties.every((p) => p.role !== 'signer' || p.signedAt)).toBe(true)
  })

  it('ends the session locally once the server revokes the token', async () => {
    const token = api.session()!.token
    await api.logout()
    expect(api.session()).toBeNull()
    api._persist({ token, user: { id: 'x', name: 'x', email: 'x', role: 'signer', initials: 'X', mfaEnabled: false, kycStatus: 'none', active: true, joinedAt: '' } })
    await expect(api.listDocuments()).rejects.toMatchObject({ status: 401 })
    expect(api.session()).toBeNull()
  })
})
