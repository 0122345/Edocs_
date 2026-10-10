import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  CircleHelp,
  FileText,
  Globe,
  Info,
  Link2,
  Lock,
  Maximize,
  Minus,
  Plus,
  Search,
  ShieldCheck,
  Upload,
  X,
} from 'lucide-react'
import { SignaturePad } from '../components/SignaturePad'
import { Badge, ErrorNote, Loading } from '../components/ui'
import { Menu, MenuItem } from '../components/overlay'
import { UserMenu } from '../layouts/AppShell'
import { api, isMockMode } from '../services/api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../state/auth'
import { useToast } from '../state/toast'
import { sanitizeDocumentHtml } from '../lib/sanitize'
import { cn } from '../lib/cn'

type Mode = 'draw' | 'type' | 'certificate'
type Lang = 'en' | 'fr'

const strings = {
  en: {
    portal: 'Execution portal',
    title: 'Sign document',
    intro: 'Confirm your identity, then apply your legally binding signature.',
    method: 'Signature method',
    draw: 'Draw',
    type: 'Type',
    cert: 'Certificate',
    drawHint: 'Draw your signature',
    clear: 'Clear',
    signInside: 'Sign inside the box',
    typeLabel: 'Type your full legal name',
    otp: 'Two-factor code',
    otpPlaceholder: '6-digit code from SMS or email',
    send: 'Send code',
    resend: 'Resend code',
    consentLegal: 'I agree that my electronic signature is the legal equivalent of my handwritten signature on this agreement (ESIGN Act and UETA).',
    consentStorage: 'I consent to secure storage of the cryptographic audit log and identity verification metadata.',
    execute: 'Sign and seal document',
    signing: 'Signing…',
    toContinue: 'To continue,',
  },
  fr: {
    portal: 'Portail de signature',
    title: 'Signer le document',
    intro: 'Confirmez votre identité, puis apposez votre signature juridiquement contraignante.',
    method: 'Méthode de signature',
    draw: 'Dessiner',
    type: 'Saisir',
    cert: 'Certificat',
    drawHint: 'Dessinez votre signature',
    clear: 'Effacer',
    signInside: 'Signez dans le cadre',
    typeLabel: 'Saisissez votre nom légal complet',
    otp: 'Code à deux facteurs',
    otpPlaceholder: 'Code à 6 chiffres reçu par SMS ou e-mail',
    send: 'Envoyer le code',
    resend: 'Renvoyer le code',
    consentLegal: 'J’accepte que ma signature électronique ait la même valeur juridique que ma signature manuscrite (ESIGN Act et UETA).',
    consentStorage: 'J’accepte le stockage sécurisé du journal d’audit cryptographique et des métadonnées de vérification d’identité.',
    execute: 'Signer et sceller le document',
    signing: 'Signature…',
    toContinue: 'Pour continuer,',
  },
} satisfies Record<Lang, Record<string, string>>

/** Wraps case-insensitive matches of `query` in <mark>. Returns the match count. */
function highlight(root: HTMLElement, query: string): number {
  const q = query.trim().toLowerCase()
  if (!q) return 0
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const nodes: Text[] = []
  while (walker.nextNode()) nodes.push(walker.currentNode as Text)
  let count = 0
  for (const node of nodes) {
    const text = node.data
    const lower = text.toLowerCase()
    let idx = lower.indexOf(q)
    if (idx === -1) continue
    const frag = document.createDocumentFragment()
    let last = 0
    while (idx !== -1) {
      frag.append(text.slice(last, idx))
      const mark = document.createElement('mark')
      mark.className = 'rounded-sm bg-amber-soft text-ink ring-1 ring-amber/40'
      mark.textContent = text.slice(idx, idx + q.length)
      frag.append(mark)
      count++
      last = idx + q.length
      idx = lower.indexOf(q, last)
    }
    frag.append(text.slice(last))
    node.replaceWith(frag)
  }
  root.querySelector('mark')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  return count
}

export default function SignPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const toast = useToast()
  const { data: doc, error: loadError } = useResource(() => api.getDocument(id), [id])
  const viewerRef = useRef<HTMLDivElement>(null)
  const [lang, setLang] = useState<Lang>('en')
  const t = strings[lang]
  const [page, setPage] = useState(1)
  const [zoom, setZoom] = useState(100)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [matches, setMatches] = useState(0)
  const [infoOpen, setInfoOpen] = useState(false)
  const [mode, setMode] = useState<Mode>('draw')
  const [drawn, setDrawn] = useState<string | null>(null)
  const [clearSignal, setClearSignal] = useState(0)
  const [typed, setTyped] = useState('')
  const [certificate, setCertificate] = useState<File | null>(null)
  const [otp, setOtp] = useState('')
  const [otpCooldown, setOtpCooldown] = useState(0)
  const [otpSentTo, setOtpSentTo] = useState<string | null>(null)
  const [agreeLegal, setAgreeLegal] = useState(false)
  const [agreeStorage, setAgreeStorage] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<{ txId: string; complete: boolean } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const onDrawn = useCallback((d: string | null) => setDrawn(d), [])
  const totalPages = doc?.pages ?? 1

  // Render sanitized document HTML, then apply search highlighting.
  useLayoutEffect(() => {
    const el = viewerRef.current
    if (!el || !doc || page !== 1) return
    el.innerHTML = sanitizeDocumentHtml(doc.content)
    setMatches(highlight(el, query))
  }, [doc, query, page])

  useEffect(() => {
    if (otpCooldown <= 0) return
    const timer = setTimeout(() => setOtpCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [otpCooldown])

  const alreadySigned = !!doc && doc.parties.some((p) => p.email === user?.email && p.signedAt)
  const closed = !!doc && (doc.status === 'signed' || doc.status === 'archived')
  const notOpen = !!doc && (doc.status === 'draft' || doc.status === 'in_review')

  const signature = mode === 'draw' ? drawn : mode === 'type' ? typed.trim() || null : certificate?.name ?? null
  const missing = [
    !signature && (mode === 'draw' ? 'draw your signature' : mode === 'type' ? 'type your full name' : 'upload your certificate'),
    !/^\d{6}$/.test(otp) && (otpSentTo ? 'enter the 6-digit code' : 'send yourself a code and enter it'),
    !(agreeLegal && agreeStorage) && 'accept both statements',
  ].filter(Boolean) as string[]

  async function sendCode() {
    try {
      const r = await api.sendOtp(id)
      setOtpSentTo(r.sentTo)
      setOtpCooldown(30)
      toast(isMockMode ? 'Code sent. In demo mode it appears in your notifications (bell icon).' : `Code sent to ${r.sentTo}.`)
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    }
  }

  async function execute() {
    if (missing.length || !signature) return
    setSubmitting(true)
    setError(null)
    try {
      const r = await api.executeSignature(id, { mode, otp, signature })
      setResult({ txId: r.txId, complete: r.document.status === 'signed' })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <header className="sticky top-0 z-20 border-b border-line bg-paper">
        <div className="flex h-16 items-center gap-4 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-1.5 text-lg font-bold tracking-tight">
            <FileText className="size-5" /> edocs
          </Link>
          <span className="hidden h-6 w-px bg-line sm:block" />
          <p className="hidden min-w-0 truncate text-sm text-ink-2 sm:block">
            Document: <Link to={`/workflow/${id}`} className="hash text-ink hover:underline">{doc?.title ?? id}</Link>
          </p>
          <div className="ml-auto flex items-center gap-2">
            <span className="hidden md:block">
              <Badge tone="seal" className="py-1"><ShieldCheck className="size-3.5" /> AES-256 encrypted, eIDAS QES compliant</Badge>
            </span>
            <Menu align="right" label={`Language: ${lang === 'en' ? 'English' : 'Français'}`} triggerClassName="btn btn-ghost px-2.5 py-1.5" trigger={<><Globe className="size-4" /> {lang.toUpperCase()}</>}>
              {(close) => (
                <>
                  <MenuItem onSelect={() => { setLang('en'); close() }}>English</MenuItem>
                  <MenuItem onSelect={() => { setLang('fr'); close() }}>Français</MenuItem>
                </>
              )}
            </Menu>
            <UserMenu />
          </div>
        </div>
      </header>

      <div className="grid flex-1 lg:grid-cols-[minmax(0,1fr)_480px]">
        {/* Viewer */}
        <section aria-label="Document preview" className="flex min-w-0 flex-col">
          <div className="flex items-center justify-between gap-2 border-b border-line bg-paper px-4 py-2.5 text-sm">
            <div className="flex items-center gap-1">
              <button type="button" className="rounded p-1 hover:bg-well disabled:opacity-40" disabled={page === 1} onClick={() => setPage(page - 1)} aria-label="Previous page"><ChevronLeft className="size-4" /></button>
              <span className="tabular-nums">Page {page} of {totalPages}</span>
              <button type="button" className="rounded p-1 hover:bg-well disabled:opacity-40" disabled={page >= totalPages} onClick={() => setPage(page + 1)} aria-label="Next page"><ChevronRight className="size-4" /></button>
            </div>
            <div className="flex items-center gap-1">
              <div className="flex items-center rounded-md bg-well">
                <button type="button" className="p-1.5 disabled:opacity-40" disabled={zoom <= 50} onClick={() => setZoom(zoom - 10)} aria-label="Zoom out"><Minus className="size-3.5" /></button>
                <span className="hash w-12 text-center text-xs">{zoom}%</span>
                <button type="button" className="p-1.5 disabled:opacity-40" disabled={zoom >= 200} onClick={() => setZoom(zoom + 10)} aria-label="Zoom in"><Plus className="size-3.5" /></button>
              </div>
              <button type="button" aria-pressed={searchOpen} className={cn('rounded p-1.5 hover:bg-well', searchOpen && 'bg-well')} aria-label="Search document" onClick={() => { setSearchOpen((v) => !v); setQuery(''); setPage(1) }}>
                <Search className="size-4" />
              </button>
              <button type="button" className="rounded p-1.5 hover:bg-well" aria-label="Full screen" onClick={() => (document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen?.())}>
                <Maximize className="size-4" />
              </button>
            </div>
          </div>
          {searchOpen && (
            <div className="flex items-center gap-2 border-b border-line bg-paper px-4 py-2">
              <label className="sr-only" htmlFor="doc-search">Find in document</label>
              <input id="doc-search" autoFocus value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Find in document" className="field max-w-xs py-1.5" />
              <span role="status" className="text-xs text-muted">{query.trim() ? `${matches} match${matches === 1 ? '' : 'es'}` : ''}</span>
              <button type="button" className="ml-auto rounded p-1 text-muted hover:bg-well" aria-label="Close search" onClick={() => { setSearchOpen(false); setQuery('') }}><X className="size-4" /></button>
            </div>
          )}
          <div className="flex-1 overflow-auto p-4 sm:p-10">
            {loadError && <ErrorNote message={loadError} />}
            <article
              className="card mx-auto max-w-[680px] origin-top px-6 py-10 shadow-sm sm:px-12"
              style={{ transform: `scale(${zoom / 100})`, marginBottom: `${(zoom - 100) * 6}px` }}
            >
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line pb-6">
                <div>
                  <p className="hash text-xs text-muted">{doc?.category ?? ''} agreement</p>
                  <h1 className="mt-1 text-2xl font-semibold tracking-tight">{doc?.title ?? ''}</h1>
                </div>
                {doc?.sha256 ? <Badge tone="seal">Verified hash</Badge> : <Badge tone="amber">Hash sealed on completion</Badge>}
              </div>
              {!doc && <div className="mt-8"><Loading /></div>}
              {page === 1 ? (
                <div ref={viewerRef} className="doc-body hide-redlines mt-8 flex flex-col gap-4 leading-relaxed text-ink-2" />
              ) : (
                <p className="mt-8 text-ink-2">Page {page} holds the schedules and annexes. They're part of the sealed PDF; use the arrows to return to page 1.</p>
              )}
            </article>
          </div>
        </section>

        {/* Execution panel */}
        <aside aria-labelledby="sign-title" className="flex flex-col border-l border-line bg-paper">
          {result ? (
            <div className="flex flex-1 flex-col items-start gap-4 p-6 sm:p-8">
              <span className="flex size-12 items-center justify-center rounded-full bg-seal-soft text-seal"><CircleCheck className="size-7" /></span>
              <h2 id="sign-title" className="text-2xl font-semibold tracking-tight">Signature applied</h2>
              <p className="text-ink-2">
                {result.complete
                  ? 'Every party has signed. The document hash is anchored on-chain and each party gets the sealed copy by email.'
                  : 'Your signature is recorded. The remaining signers have been notified; the document seals when the last one signs.'}
              </p>
              <dl className="w-full rounded-md border border-line bg-canvas p-4 text-sm">
                <dt className="text-xs text-muted">Transaction ID</dt>
                <dd className="hash mt-1">{result.txId}</dd>
              </dl>
              <div className="flex flex-wrap gap-2">
                <Link to={`/workflow/${id}`} className="btn btn-primary">View signing workflow</Link>
                <Link to="/documents" className="btn btn-ghost">Back to documents</Link>
              </div>
            </div>
          ) : alreadySigned || closed || notOpen ? (
            <div className="flex flex-1 flex-col items-start gap-4 p-6 sm:p-8">
              <h2 id="sign-title" className="text-2xl font-semibold tracking-tight">
                {alreadySigned ? 'You already signed this document' : closed ? 'This document is fully signed' : 'This document isn’t ready to sign'}
              </h2>
              <p className="text-ink-2">
                {notOpen ? 'It is still being drafted or reviewed. You’ll get an email when it is sent for signature.' : 'There’s nothing left for you to do here. You can follow progress on the workflow page.'}
              </p>
              <Link to={`/workflow/${id}`} className="btn btn-primary">View signing workflow</Link>
            </div>
          ) : (
            <>
              <div className="flex flex-col gap-7 p-6 sm:p-8">
                <div>
                  <p className="text-sm text-muted">{t.portal}</p>
                  <h2 id="sign-title" className="mt-1 text-2xl font-semibold tracking-tight">{t.title}</h2>
                  <p className="mt-2 text-ink-2">{t.intro}</p>
                </div>

                <div className="relative flex items-center gap-4 rounded-md border border-line bg-canvas p-4">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-seal-soft text-seal"><ShieldCheck className="size-6" /></span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">SwissID / eIDAS liveness verified</p>
                    <p className="text-sm text-seal">Confidence 99.8%, ID <span className="hash">#CH-883921</span></p>
                  </div>
                  <button type="button" aria-expanded={infoOpen} aria-label="About identity verification" className="rounded p-1 text-muted hover:bg-well hover:text-ink" onClick={() => setInfoOpen((v) => !v)}>
                    <Info className="size-4" />
                  </button>
                  {infoOpen && (
                    <p role="note" className="absolute top-full right-0 left-0 z-10 mt-2 rounded-md border border-line bg-paper p-3 text-xs text-ink-2 shadow-lg">
                      A qualified trust service provider confirmed your identity with a biometric liveness check. That's what makes this a qualified electronic signature (QES) under eIDAS, legally equal to a handwritten one in the EU.
                    </p>
                  )}
                </div>

                <fieldset>
                  <legend className="text-sm font-medium">{t.method}</legend>
                  <div role="tablist" className="mt-2 grid grid-cols-3 rounded-md bg-well p-1 text-sm">
                    {([['draw', t.draw], ['type', t.type], ['certificate', t.cert]] as const).map(([m, label]) => (
                      <button key={m} type="button" role="tab" aria-selected={mode === m} onClick={() => setMode(m)} className={cn('rounded px-2 py-2 transition-colors', mode === m ? 'bg-paper font-medium shadow-sm' : 'text-ink-2 hover:text-ink')}>
                        {label}
                      </button>
                    ))}
                  </div>

                  <div className="mt-5" role="tabpanel">
                    {mode === 'draw' && (
                      <>
                        <div className="flex items-baseline justify-between text-sm">
                          <span>{t.drawHint}</span>
                          <button type="button" className="font-medium underline underline-offset-4" onClick={() => setClearSignal((n) => n + 1)}>{t.clear}</button>
                        </div>
                        <div className="relative mt-2 rounded-md border-2 border-dashed border-line-strong bg-canvas">
                          <SignaturePad onChange={onDrawn} clearSignal={clearSignal} />
                          {!drawn && <span className="pointer-events-none absolute right-3 bottom-2 text-xs text-muted">{t.signInside}</span>}
                        </div>
                      </>
                    )}
                    {mode === 'type' && (
                      <label className="block text-sm">
                        {t.typeLabel}
                        <input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={user?.name} className="field mt-2" autoComplete="name" />
                        <span className="mt-3 flex h-24 items-center justify-center rounded-md border-2 border-dashed border-line-strong bg-canvas font-[Caveat,cursive] text-4xl text-ink" aria-hidden="true">
                          {typed || <span className="text-muted/50">{user?.name}</span>}
                        </span>
                      </label>
                    )}
                    {mode === 'certificate' && (
                      <label className="flex cursor-pointer flex-col items-center gap-2 rounded-md border-2 border-dashed border-line-strong bg-canvas px-4 py-8 text-center text-sm hover:border-ink">
                        <Upload className="size-6 text-ink-2" />
                        {certificate ? (
                          <span className="font-medium">{certificate.name}</span>
                        ) : (
                          <>
                            <span className="font-medium">Choose a qualified certificate</span>
                            <span className="text-xs text-muted">.p12, .pfx or .cer issued by a trust service provider</span>
                          </>
                        )}
                        <input type="file" accept=".p12,.pfx,.cer,.crt" className="sr-only" onChange={(e) => setCertificate(e.target.files?.[0] ?? null)} />
                      </label>
                    )}
                  </div>
                </fieldset>

                <div>
                  <label htmlFor="otp" className="text-sm font-medium">{t.otp}</label>
                  <div className="mt-2 flex gap-2">
                    <input id="otp" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} placeholder={t.otpPlaceholder} className="field hash tracking-widest" />
                    <button type="button" className="btn btn-ghost shrink-0" disabled={otpCooldown > 0} onClick={sendCode}>
                      {otpCooldown > 0 ? `${otpCooldown}s` : otpSentTo ? t.resend : t.send}
                    </button>
                  </div>
                  {otpSentTo && <p role="status" className="mt-2 text-xs text-seal">Code sent to {otpSentTo}. It expires in 10 minutes.</p>}
                </div>

                <div className="flex flex-col gap-3 text-sm text-ink-2">
                  <label className="flex gap-3">
                    <input type="checkbox" checked={agreeLegal} onChange={(e) => setAgreeLegal(e.target.checked)} className="mt-0.5 size-4 shrink-0 accent-ink" />
                    {t.consentLegal}
                  </label>
                  <label className="flex gap-3">
                    <input type="checkbox" checked={agreeStorage} onChange={(e) => setAgreeStorage(e.target.checked)} className="mt-0.5 size-4 shrink-0 accent-ink" />
                    {t.consentStorage}
                  </label>
                </div>
              </div>

              <div className="mt-auto border-t border-line bg-canvas p-6 sm:px-8">
                {error && <p role="alert" className="mb-3 text-sm text-alert">{error}</p>}
                <button type="button" className="btn btn-primary w-full py-3.5" disabled={missing.length > 0 || submitting} onClick={execute}>
                  <Lock className="size-4" /> {submitting ? t.signing : t.execute}
                </button>
                <p className="mt-3 text-center text-xs text-muted">
                  {missing.length > 0 ? (
                    <>{t.toContinue} {missing.join(', ')}.</>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 text-seal"><Link2 className="size-3.5" /> Ready to anchor on Polygon node #4092</span>
                  )}
                </p>
              </div>
            </>
          )}
        </aside>
      </div>

      <footer className="border-t border-line bg-paper px-4 py-4 text-xs text-muted sm:px-6">
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <p>© 2026 Edocs Technologies Inc. Audit trail tracking ID <span className="hash text-ink">TRK-{id.slice(0, 4).toUpperCase()}-XQ71</span></p>
          <nav aria-label="Footer" className="flex flex-wrap gap-x-5 gap-y-1">
            <Link to="/help#security" className="hover:text-ink">Security statement</Link>
            <Link to="/help#compliance" className="hover:text-ink">Compliance policy</Link>
            <Link to="/help" className="inline-flex items-center gap-1 hover:text-ink"><CircleHelp className="size-3.5" /> Support</Link>
          </nav>
        </div>
      </footer>
    </div>
  )
}
