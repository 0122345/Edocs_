import { useEffect, useState, type FormEvent } from 'react'
import { Check, Minus, UserPlus } from 'lucide-react'
import { AppShell } from '../layouts/AppShell'
import { Avatar, Badge, ErrorNote, Loading, PageHeader } from '../components/ui'
import { Guarded } from '../components/overlay'
import { ConfirmModal, InviteMemberModal } from '../components/modals'
import { api, isMockMode } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../state/auth'
import { useAction } from '../state/toast'
import { permissionsFor, roleDescriptions, roleLabels, type Permission } from '../lib/rbac'
import type { Role, WorkspaceSettings } from '../data/types'

const permissionLabels: Record<Permission, string> = {
  'document:create': 'Create documents',
  'document:edit': 'Edit drafts',
  'document:sign': 'Sign documents',
  'document:share': 'Share and send for signature',
  'document:delete': 'Delete drafts',
  'workflow:manage': 'Manage routing rules',
  'audit:read': 'Read archive and audit logs',
  'audit:export': 'Export evidence packages',
  'compliance:run': 'Run compliance checks',
  'template:manage': 'Manage templates',
  'members:manage': 'Manage members and roles',
  'settings:manage': 'Change workspace settings and legal holds',
}
const roles = Object.keys(roleLabels) as Role[]

export default function SettingsPage() {
  const { user, can } = useAuth()
  const run = useAction()
  const settings = useResource(api.getSettings)
  const members = useResource(api.listMembers)
  const [form, setForm] = useState<WorkspaceSettings | null>(null)
  const [dirty, setDirty] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [resetOpen, setResetOpen] = useState(false)
  const canManage = can('settings:manage')
  const me = members.data?.find((m) => m.id === user?.id)

  useEffect(() => {
    if (settings.data && !dirty) setForm(settings.data)
  }, [settings.data, dirty])

  function patch(p: Partial<WorkspaceSettings>) {
    setForm((f) => (f ? { ...f, ...p } : f))
    setDirty(true)
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    const saved = await run(() => api.updateSettings(form), 'Settings saved.')
    if (saved) setDirty(false)
  }

  return (
    <AppShell breadcrumb="Settings">
      <main className="mx-auto flex max-w-[960px] flex-col gap-6 px-4 py-8 sm:px-8">
        <PageHeader title="Settings" description="Workspace details, signing defaults, your account security, and who can do what." />

        {/* Workspace */}
        <form className="card flex flex-col gap-5 p-6" onSubmit={save} aria-labelledby="ws-title">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="ws-title" className="text-lg font-semibold tracking-tight">Workspace</h2>
            {!canManage && <p className="text-sm text-muted">Read only. Only administrators can change these.</p>}
          </div>
          {settings.error && <ErrorNote message={settings.error} />}
          {!form ? <Loading /> : (
            <fieldset disabled={!canManage} className="grid gap-4 sm:grid-cols-2">
              <label className="text-sm">Organization name<input required className="field mt-1.5" value={form.orgName} onChange={(e) => patch({ orgName: e.target.value })} /></label>
              <label className="text-sm">Admin email<input type="email" required className="field mt-1.5" value={form.adminEmail} onChange={(e) => patch({ adminEmail: e.target.value })} /></label>
              <label className="text-sm">Default signature level
                <select className="field mt-1.5" value={form.signatureLevel} onChange={(e) => patch({ signatureLevel: e.target.value as WorkspaceSettings['signatureLevel'] })}>
                  <option value="ses">Simple (SES)</option>
                  <option value="aes">Advanced (AES)</option>
                  <option value="qes">Qualified (QES)</option>
                </select>
              </label>
              <label className="text-sm">Archive retention
                <select className="field mt-1.5" value={form.retentionYears} onChange={(e) => patch({ retentionYears: Number(e.target.value) })}>
                  {[5, 7, 10].map((y) => <option key={y} value={y}>{y} years</option>)}
                </select>
              </label>
              <label className="flex items-start gap-3 text-sm sm:col-span-2">
                <input type="checkbox" checked={form.require2fa} onChange={(e) => patch({ require2fa: e.target.checked })} className="mt-0.5 size-4 accent-ink" />
                Require a two-factor code for every signature
              </label>
            </fieldset>
          )}
          {canManage && (
            <div className="flex items-center justify-end gap-3 border-t border-line pt-5">
              {dirty && <button type="button" className="btn btn-ghost" onClick={() => { setDirty(false); setForm(settings.data) }}>Discard changes</button>}
              <button type="submit" className="btn btn-primary" disabled={!dirty}>Save settings</button>
            </div>
          )}
        </form>

        {/* My account */}
        <section className="card p-6" aria-labelledby="acct-title">
          <h2 id="acct-title" className="text-lg font-semibold tracking-tight">Your account</h2>
          {me ? (
            <div className="mt-4 flex flex-wrap items-center gap-4">
              <Avatar initials={me.initials} />
              <div className="min-w-0 flex-1">
                <p className="font-medium">{me.name}</p>
                <p className="text-sm text-muted">{me.email}, {roleLabels[me.role].toLowerCase()}, identity {me.kycStatus === 'verified' ? 'verified' : 'not verified'}</p>
              </div>
              <label className="flex items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  checked={me.mfaEnabled}
                  onChange={(e) => run(() => api.updateMember(me.id, { mfaEnabled: e.target.checked }), e.target.checked ? 'Two-factor sign-in turned on.' : 'Two-factor sign-in turned off.')}
                  className="size-4 accent-ink"
                />
                Two-factor sign-in
              </label>
            </div>
          ) : <Loading />}
        </section>

        {/* Members */}
        <section id="members" className="card scroll-mt-24 overflow-hidden" aria-labelledby="members-title">
          <div className="flex flex-wrap items-start justify-between gap-3 p-6">
            <div>
              <h2 id="members-title" className="text-lg font-semibold tracking-tight">Members</h2>
              <p className="mt-1 text-sm text-ink-2">Each member has one role. Deactivated members can't sign in, but their audit history stays.</p>
            </div>
            <Guarded permission="members:manage" className="btn btn-primary" onClick={() => setInviteOpen(true)}><UserPlus className="size-4" /> Invite</Guarded>
          </div>
          {members.error && <div className="px-6 pb-6"><ErrorNote message={members.error} /></div>}
          {!members.data ? <div className="px-6 pb-6"><Loading /></div> : (
            <ul className="divide-y divide-line border-t border-line">
              {members.data.map((m) => (
                <li key={m.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-6 py-3">
                  <Avatar initials={m.initials} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">{m.name}{m.id === user?.id && <span className="font-normal text-muted"> (you)</span>}</p>
                    <p className="text-xs text-muted">{m.email}</p>
                  </div>
                  {!m.active && <Badge tone="alert">Deactivated</Badge>}
                  {can('members:manage') && m.id !== user?.id ? (
                    <>
                      <label className="sr-only" htmlFor={`role-${m.id}`}>Role for {m.name}</label>
                      <select id={`role-${m.id}`} value={m.role} onChange={(e) => run(() => api.updateMember(m.id, { role: e.target.value as Role }), `${m.name} is now ${roleLabels[e.target.value as Role].toLowerCase()}.`)} className="field w-40 py-1.5">
                        {roles.map((r) => <option key={r} value={r}>{roleLabels[r]}</option>)}
                      </select>
                      <button type="button" className="btn btn-ghost px-3 py-1.5 text-xs" onClick={() => run(() => api.updateMember(m.id, { active: !m.active }), m.active ? `${m.name} deactivated.` : `${m.name} reactivated.`)}>
                        {m.active ? 'Deactivate' : 'Reactivate'}
                      </button>
                    </>
                  ) : (
                    <Badge tone="info">{roleLabels[m.role]}</Badge>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* RBAC matrix */}
        <section className="card overflow-hidden" aria-labelledby="roles-title">
          <div className="p-6">
            <h2 id="roles-title" className="text-lg font-semibold tracking-tight">Roles and permissions</h2>
            <p className="mt-1 text-sm text-ink-2">What each role can do. The server enforces the same rules.</p>
          </div>
          <div className="overflow-x-auto border-t border-line">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="bg-well text-xs text-ink-2">
                <tr>
                  <th scope="col" className="px-6 py-3 font-semibold">Permission</th>
                  {roles.map((r) => <th key={r} scope="col" className="px-3 py-3 text-center font-semibold" title={roleDescriptions[r]}>{roleLabels[r]}</th>)}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {(Object.keys(permissionLabels) as Permission[]).map((p) => (
                  <tr key={p}>
                    <th scope="row" className="px-6 py-2.5 font-normal">{permissionLabels[p]}</th>
                    {roles.map((r) => (
                      <td key={r} className="px-3 py-2.5 text-center">
                        {permissionsFor(r).includes(p) ? <Check className="mx-auto size-4 text-seal" aria-label="Allowed" /> : <Minus className="mx-auto size-4 text-line-strong" aria-label="Not allowed" />}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {isMockMode && (
          <section className="card flex flex-wrap items-center justify-between gap-3 border-dashed p-6" aria-labelledby="demo-title">
            <div>
              <h2 id="demo-title" className="font-semibold">Demo data</h2>
              <p className="mt-1 text-sm text-ink-2">The app runs on browser-stored mock data until the backend is connected. Resetting restores the original sample documents.</p>
            </div>
            <button type="button" className="btn btn-ghost" onClick={() => setResetOpen(true)}>Reset demo data</button>
          </section>
        )}
      </main>

      <InviteMemberModal open={inviteOpen} onClose={() => setInviteOpen(false)} />
      <ConfirmModal
        open={resetOpen}
        onClose={() => setResetOpen(false)}
        danger
        title="Reset demo data?"
        body="Documents, members, notifications and audit entries you created in this browser will be replaced with the original samples. You stay signed in."
        confirmLabel="Reset data"
        onConfirm={() => {
          setResetOpen(false)
          api.resetDemoData()
        }}
      />
    </AppShell>
  )
}
