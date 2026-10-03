import { Link } from 'react-router-dom'
import { AppShell } from '../layouts/AppShell'
import { PageHeader } from '../components/ui'
import { roleDescriptions, roleLabels } from '../lib/rbac'
import { isMockMode } from '../services/api'
import type { Role } from '../data/types'

const endpoints: [string, string, string][] = [
  ['POST', '/auth/login', 'Email + password sign-in; returns a session or an MFA challenge'],
  ['POST', '/auth/mfa/verify', 'Completes sign-in with a 6-digit code'],
  ['GET', '/oauth2/authorization/{google|microsoft}', 'Starts the OAuth2 / OIDC single sign-on flow'],
  ['GET', '/documents', 'Documents visible to the caller'],
  ['POST', '/documents', 'Create a document from a template'],
  ['PATCH', '/documents/{id}', 'Save a new draft version'],
  ['POST', '/documents/{id}/send', 'Send for signature (queues emails via RabbitMQ)'],
  ['POST', '/documents/{id}/otp', 'Send a signing code by SMS/email'],
  ['POST', '/documents/{id}/signatures', 'Apply a signature; seals and anchors when complete'],
  ['GET', '/documents/{id}/evidence', 'Court-ready evidence package'],
  ['GET', '/archive?q=', 'Search the WORM archive'],
  ['GET', '/audit-logs', 'Audit log entries with blockchain hashes'],
  ['GET', '/members', 'Workspace members and roles'],
  ['GET', '/notifications', 'In-app notifications for the caller'],
]

export default function HelpPage() {
  return (
    <AppShell breadcrumb="Help center">
      <main className="mx-auto flex max-w-[860px] flex-col gap-10 px-4 py-8 sm:px-8">
        <PageHeader title="Help center" description="How Edocs works, how your data is protected, and how to get support." />

        <section aria-labelledby="start-title" className="flex flex-col gap-3">
          <h2 id="start-title" className="text-xl font-semibold tracking-tight">Getting started</h2>
          <ol className="flex list-decimal flex-col gap-2 pl-5 text-ink-2">
            <li>Create a document from <Link to="/templates" className="text-ink underline underline-offset-4">a template</Link> and add the parties in signing order.</li>
            <li>Draft and redline it in the editor with your team, then save.</li>
            <li>Send it for signature. Each signer gets an email with a link to the signing portal.</li>
            <li>Signers verify their identity, confirm a one-time code and sign.</li>
            <li>When the last party signs, the document is hashed, anchored on-chain and moved to the WORM archive.</li>
          </ol>
          <h3 className="mt-4 font-semibold">What each role can do</h3>
          <dl className="grid gap-3 sm:grid-cols-2">
            {(Object.keys(roleLabels) as Role[]).map((r) => (
              <div key={r} className="rounded-md border border-line bg-paper p-4">
                <dt className="font-medium">{roleLabels[r]}</dt>
                <dd className="mt-1 text-sm text-ink-2">{roleDescriptions[r]}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section id="security" aria-labelledby="security-title" className="flex scroll-mt-24 flex-col gap-3">
          <h2 id="security-title" className="text-xl font-semibold tracking-tight">Security framework</h2>
          <ul className="flex list-disc flex-col gap-2 pl-5 text-ink-2">
            <li>AES-256-GCM encryption at rest and TLS 1.3 in transit; field-level encryption for personal data through KMS.</li>
            <li>Zero-trust networking: every request is authenticated and authorized; mTLS between services.</li>
            <li>Single sign-on through OAuth2 / OpenID Connect, with optional two-factor sign-in.</li>
            <li>Role-based access control on every route and API call.</li>
            <li>Point-in-time recovery and cross-region replication (RPO under 1 minute, RTO under 15 minutes).</li>
          </ul>
        </section>

        <section id="compliance" aria-labelledby="compliance-title" className="flex scroll-mt-24 flex-col gap-3">
          <h2 id="compliance-title" className="text-xl font-semibold tracking-tight">Compliance: eIDAS, ESIGN, GDPR, SOC 2</h2>
          <ul className="flex list-disc flex-col gap-2 pl-5 text-ink-2">
            <li><strong className="text-ink">eIDAS (EU):</strong> simple, advanced and qualified electronic signatures, with qualified timestamps for long-term validation.</li>
            <li><strong className="text-ink">ESIGN Act & UETA (USA):</strong> intent to sign, consent to electronic records and association of signature with record are captured on every signature.</li>
            <li><strong className="text-ink">GDPR:</strong> EU data residency, retention limits and erasure through cryptographic shredding.</li>
            <li><strong className="text-ink">SOC 2 Type II & ISO 27001:</strong> audited annually; see live control status on the <Link to="/compliance" className="text-ink underline underline-offset-4">compliance page</Link>.</li>
          </ul>
        </section>

        <section id="api" aria-labelledby="api-title" className="flex scroll-mt-24 flex-col gap-3">
          <h2 id="api-title" className="text-xl font-semibold tracking-tight">API documentation</h2>
          <p className="text-ink-2">
            REST API, authenticated with a bearer token from sign-in. {isMockMode && 'This build is running on mock data; the endpoints below are what the frontend will call once VITE_API_BASE_URL is set.'}
          </p>
          <div className="overflow-x-auto rounded-md border border-line bg-paper">
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="bg-well text-xs text-ink-2">
                <tr><th scope="col" className="px-4 py-2.5">Method</th><th scope="col" className="px-4 py-2.5">Path</th><th scope="col" className="px-4 py-2.5">Purpose</th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {endpoints.map(([m, p, d]) => (
                  <tr key={m + p}>
                    <td className="hash px-4 py-2.5 font-medium">{m}</td>
                    <td className="hash px-4 py-2.5">{p}</td>
                    <td className="px-4 py-2.5 text-ink-2">{d}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section id="support" aria-labelledby="support-title" className="flex scroll-mt-24 flex-col gap-3 rounded-md border border-line bg-paper p-6">
          <h2 id="support-title" className="text-xl font-semibold tracking-tight">Contact support</h2>
          <p className="text-ink-2">Include the document ID or audit tracking ID so we can find it quickly.</p>
          <a href="mailto:support@edocs.example?subject=Edocs%20support%20request" className="btn btn-primary self-start">Email support</a>
        </section>
      </main>
    </AppShell>
  )
}
