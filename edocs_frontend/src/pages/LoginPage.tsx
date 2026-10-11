import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { FileText, KeyRound, LoaderCircle, MailCheck, ShieldCheck } from 'lucide-react'
import { useAuth } from '../state/auth'
import { api, isMockMode, passwordProblem, type LoginResult } from '../services/api'
import { DEMO_MFA_CODE, DEMO_PASSWORD, seedUsers } from '../data/mock'
import { roleLabels } from '../lib/rbac'

function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path fill="#4285F4" d="M22.5 12.3c0-.8-.1-1.5-.2-2.2H12v4.2h5.9a5 5 0 0 1-2.2 3.3v2.7h3.6c2-1.9 3.2-4.7 3.2-8z" />
      <path fill="#34A853" d="M12 23c3 0 5.5-1 7.3-2.7l-3.6-2.7c-1 .7-2.2 1.1-3.7 1.1-2.9 0-5.3-1.9-6.2-4.5H2.1v2.8A11 11 0 0 0 12 23z" />
      <path fill="#FBBC05" d="M5.8 14.2a6.6 6.6 0 0 1 0-4.3V7.1H2.1a11 11 0 0 0 0 9.9l3.7-2.8z" />
      <path fill="#EA4335" d="M12 5.4c1.6 0 3.1.6 4.2 1.7l3.2-3.2A11 11 0 0 0 2.1 7.1l3.7 2.8C6.7 7.3 9.1 5.4 12 5.4z" />
    </svg>
  )
}

function MicrosoftMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path fill="#F25022" d="M2 2h9.5v9.5H2z" />
      <path fill="#7FBA00" d="M12.5 2H22v9.5h-9.5z" />
      <path fill="#00A4EF" d="M2 12.5h9.5V22H2z" />
      <path fill="#FFB900" d="M12.5 12.5H22V22h-9.5z" />
    </svg>
  )
}

type Mode = 'signin' | 'signup' | 'forgot' | 'reset'

const alertBox = 'rounded-md bg-alert-soft px-3 py-2 text-sm text-alert'

export default function LoginPage() {
  const { user, login, signup, verifyMfa, loginWithProvider } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next') || '/'
  const [mode, setMode] = useState<Mode>(params.get('mode') === 'signup' ? 'signup' : 'signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [orgName, setOrgName] = useState('')
  const [name, setName] = useState('')
  const [challenge, setChallenge] = useState<{ challengeId: string; email: string; kind: 'mfa' | 'verify' } | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(params.get('error'))
  const [notice, setNotice] = useState<string | null>(null)

  if (user) return <Navigate to={next} replace />

  function switchTo(m: Mode) {
    setMode(m)
    setError(null)
    setNotice(null)
    setCode('')
    setPassword('')
  }

  async function attempt(key: string, fn: () => Promise<void>) {
    setBusy(key)
    setError(null)
    try {
      await fn()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(null)
    }
  }

  function handle(r: LoginResult) {
    if (r.kind === 'session') navigate(next, { replace: true })
    else setChallenge({ challengeId: r.challengeId, email: r.email, kind: r.kind })
  }

  function submitPassword(e: FormEvent) {
    e.preventDefault()
    void attempt('password', async () => handle(await login(email, password)))
  }

  function submitSignup(e: FormEvent) {
    e.preventDefault()
    const problem = passwordProblem(password, email)
    if (problem) return setError(problem)
    void attempt('signup', async () => handle(await signup({ orgName, name, email, password })))
  }

  function submitForgot(e: FormEvent) {
    e.preventDefault()
    void attempt('forgot', async () => {
      await api.requestPasswordReset(email)
      setMode('reset')
      setNotice(`If ${email} has an Edocs account, a reset code is on its way. Enter it below with your new password.`)
    })
  }

  function submitReset(e: FormEvent) {
    e.preventDefault()
    const problem = passwordProblem(password, email)
    if (problem) return setError(problem)
    void attempt('reset', async () => {
      await api.resetPassword(email, code, password)
      switchTo('signin')
      setNotice('Your password was changed. Sign in with the new one.')
    })
  }

  function submitCode(e: FormEvent) {
    e.preventDefault()
    if (!challenge) return
    void attempt('code', async () => {
      await verifyMfa(challenge.challengeId, code)
      navigate(next, { replace: true })
    })
  }

  const passwordHint = <span className="mt-1 block text-xs text-muted">At least 10 characters, with letters and numbers.</span>

  return (
    <div className="grid min-h-dvh bg-canvas lg:grid-cols-[1fr_minmax(480px,560px)]">
      <section className="hidden flex-col justify-between bg-ink p-12 text-white lg:flex">
        <p className="flex items-center gap-2 text-lg font-bold tracking-tight"><FileText className="size-5" /> EDOCS</p>
        <div className="max-w-[46ch]">
          <h1 className="text-4xl leading-tight font-semibold tracking-tight">Contracts drafted, signed and sealed without paper.</h1>
          <p className="mt-4 text-white/70">Qualified e-signatures under eIDAS, ESIGN and UETA, with every action anchored to an immutable audit trail.</p>
        </div>
        <p className="flex items-center gap-2 text-sm text-white/60"><ShieldCheck className="size-4" /> AES-256 encryption · SOC 2 Type II · ISO 27001</p>
      </section>

      <main className="flex flex-col justify-center px-4 py-12 sm:px-12">
        <div className="mx-auto w-full max-w-sm">
          <p className="mb-8 flex items-center gap-2 text-lg font-bold tracking-tight lg:hidden"><FileText className="size-5" /> EDOCS</p>

          {challenge ? (
            <form onSubmit={submitCode} className="flex flex-col gap-4">
              {challenge.kind === 'verify' ? <MailCheck className="size-7" strokeWidth={1.5} /> : <KeyRound className="size-7" strokeWidth={1.5} />}
              <h2 className="text-2xl font-semibold tracking-tight">{challenge.kind === 'verify' ? 'Confirm your email' : 'Enter your verification code'}</h2>
              <p className="text-sm text-ink-2">
                {challenge.kind === 'verify'
                  ? `We emailed a 6-digit code to ${challenge.email}. Enter it to confirm the address and open your workspace.`
                  : `Your account (${challenge.email}) uses two-factor authentication. Enter the 6-digit code we emailed you.`}
              </p>
              {error && <p role="alert" className={alertBox}>{error}</p>}
              <label className="text-sm">Code
                <input inputMode="numeric" autoComplete="one-time-code" maxLength={6} required autoFocus value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} className="field hash mt-1.5 tracking-[0.3em]" />
              </label>
              <button type="submit" className="btn btn-primary w-full py-2.5" disabled={code.length !== 6 || !!busy}>
                {busy === 'code' && <LoaderCircle className="size-4 animate-spin" />} {challenge.kind === 'verify' ? 'Confirm and continue' : 'Verify and sign in'}
              </button>
              <button type="button" className="text-sm text-ink-2 underline underline-offset-4" onClick={() => { setChallenge(null); switchTo('signin') }}>
                Use a different account
              </button>
            </form>
          ) : mode === 'signup' ? (
            <>
              <h2 className="text-2xl font-semibold tracking-tight">Create your workspace</h2>
              <p className="mt-2 text-sm text-ink-2">You become its administrator, then invite your team and give each person a role.</p>
              <form onSubmit={submitSignup} className="mt-8 flex flex-col gap-4">
                {error && <p role="alert" className={alertBox}>{error}</p>}
                <label className="text-sm">Organization name<input required maxLength={160} autoComplete="organization" value={orgName} onChange={(e) => setOrgName(e.target.value)} className="field mt-1.5" /></label>
                <label className="text-sm">Your full name<input required maxLength={120} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} className="field mt-1.5" /></label>
                <label className="text-sm">Work email<input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} className="field mt-1.5" /></label>
                <label className="text-sm">Password<input type="password" required minLength={10} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} className="field mt-1.5" />{passwordHint}</label>
                <button type="submit" className="btn btn-primary w-full py-2.5" disabled={!!busy}>
                  {busy === 'signup' && <LoaderCircle className="size-4 animate-spin" />} Create workspace
                </button>
              </form>
              <p className="mt-6 text-sm text-ink-2">Already have an account? <button type="button" className="font-medium text-ink underline underline-offset-4" onClick={() => switchTo('signin')}>Sign in</button></p>
              <p className="mt-2 text-xs text-muted">Joining an existing team? Ask its administrator to invite you; the invitation email has your sign-in details.</p>
            </>
          ) : mode === 'forgot' || mode === 'reset' ? (
            <>
              <h2 className="text-2xl font-semibold tracking-tight">Reset your password</h2>
              <p className="mt-2 text-sm text-ink-2">{mode === 'forgot' ? 'Enter your account email and we will send you a 6-digit reset code.' : 'Enter the code from the email and choose a new password.'}</p>
              <form onSubmit={mode === 'forgot' ? submitForgot : submitReset} className="mt-8 flex flex-col gap-4">
                {notice && <p role="status" className="rounded-md bg-seal-soft px-3 py-2 text-sm text-seal">{notice}</p>}
                {error && <p role="alert" className={alertBox}>{error}</p>}
                <label className="text-sm">Work email<input type="email" required autoComplete="username" readOnly={mode === 'reset'} value={email} onChange={(e) => setEmail(e.target.value)} className="field mt-1.5" /></label>
                {mode === 'reset' && (
                  <>
                    <label className="text-sm">Reset code
                      <input inputMode="numeric" autoComplete="one-time-code" maxLength={6} required value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} className="field hash mt-1.5 tracking-[0.3em]" />
                    </label>
                    <label className="text-sm">New password<input type="password" required minLength={10} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} className="field mt-1.5" />{passwordHint}</label>
                  </>
                )}
                <button type="submit" className="btn btn-primary w-full py-2.5" disabled={!!busy || (mode === 'reset' && code.length !== 6)}>
                  {(busy === 'forgot' || busy === 'reset') && <LoaderCircle className="size-4 animate-spin" />} {mode === 'forgot' ? 'Send reset code' : 'Set new password'}
                </button>
              </form>
              <p className="mt-6 text-sm text-ink-2"><button type="button" className="font-medium text-ink underline underline-offset-4" onClick={() => switchTo('signin')}>Back to sign in</button></p>
            </>
          ) : (
            <>
              <h2 className="text-2xl font-semibold tracking-tight">Sign in to your workspace</h2>
              <p className="mt-2 text-sm text-ink-2">Use your company single sign-on, or your Edocs email and password.</p>

              <div className="mt-8 flex flex-col gap-2">
                <button type="button" className="btn btn-ghost w-full py-2.5" disabled={!!busy} onClick={() => attempt('google', () => loginWithProvider('google').then(() => navigate(next, { replace: true })))}>
                  {busy === 'google' ? <LoaderCircle className="size-4 animate-spin" /> : <GoogleMark />} Continue with Google
                </button>
                <button type="button" className="btn btn-ghost w-full py-2.5" disabled={!!busy} onClick={() => attempt('microsoft', () => loginWithProvider('microsoft').then(() => navigate(next, { replace: true })))}>
                  {busy === 'microsoft' ? <LoaderCircle className="size-4 animate-spin" /> : <MicrosoftMark />} Continue with Microsoft
                </button>
              </div>

              <div className="my-6 flex items-center gap-3 text-xs text-muted">
                <span className="h-px flex-1 bg-line" /> or <span className="h-px flex-1 bg-line" />
              </div>

              <form onSubmit={submitPassword} className="flex flex-col gap-4">
                {notice && <p role="status" className="rounded-md bg-seal-soft px-3 py-2 text-sm text-seal">{notice}</p>}
                {error && <p role="alert" className={alertBox}>{error}</p>}
                <label className="text-sm">Work email<input type="email" required autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} className="field mt-1.5" /></label>
                <div className="text-sm">
                  <div className="flex items-baseline justify-between">
                    <label htmlFor="signin-password">Password</label>
                    <button type="button" className="text-xs text-ink-2 underline underline-offset-4" onClick={() => switchTo('forgot')}>Forgot password?</button>
                  </div>
                  <input id="signin-password" type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} className="field mt-1.5" />
                </div>
                <button type="submit" className="btn btn-primary w-full py-2.5" disabled={!!busy}>
                  {busy === 'password' && <LoaderCircle className="size-4 animate-spin" />} Sign in
                </button>
              </form>
              <p className="mt-6 text-sm text-ink-2">New to Edocs? <button type="button" className="font-medium text-ink underline underline-offset-4" onClick={() => switchTo('signup')}>Create a workspace</button></p>
            </>
          )}

          {isMockMode && !challenge && mode === 'signin' && (
            <aside className="mt-10 rounded-md border border-dashed border-line-strong p-4 text-sm" aria-label="Demo accounts">
              <p className="font-medium">Demo accounts (mock mode)</p>
              <p className="mt-1 text-ink-2">Pick a role to fill the form. Single sign-on signs you in as the administrator.</p>
              <ul className="mt-3 flex flex-col gap-1">
                {seedUsers.map((u) => (
                  <li key={u.id}>
                    <button type="button" className="flex w-full items-baseline justify-between gap-2 rounded px-2 py-1.5 text-left hover:bg-well" onClick={() => { setEmail(u.email); setPassword(DEMO_PASSWORD) }}>
                      <span>{roleLabels[u.role]}</span>
                      <span className="hash truncate text-xs text-muted">{u.email}</span>
                    </button>
                  </li>
                ))}
              </ul>
              <p className="mt-2 text-xs text-muted">The administrator account has two-factor sign-in on; its code (and every confirmation or reset code in mock mode) is <span className="hash">{DEMO_MFA_CODE}</span>.</p>
            </aside>
          )}
        </div>
      </main>
    </div>
  )
}
