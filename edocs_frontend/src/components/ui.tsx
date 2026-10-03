import type { ReactNode } from 'react'
import { cn } from '../lib/cn'

type Tone = 'seal' | 'amber' | 'alert' | 'info' | 'neutral'

const toneClasses: Record<Tone, string> = {
  seal: 'bg-seal-soft text-seal',
  amber: 'bg-amber-soft text-amber',
  alert: 'bg-alert-soft text-alert',
  info: 'bg-info-soft text-info',
  neutral: 'bg-well text-ink-2',
}

export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap', toneClasses[tone], className)}>
      {children}
    </span>
  )
}

export function Avatar({ initials, size = 'md', presence }: { initials: string; size?: 'sm' | 'md'; presence?: 'active' | 'viewing' | 'away' }) {
  const dot = presence === 'away' ? 'bg-amber' : presence ? 'bg-seal' : ''
  return (
    <span
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center rounded-full bg-nav font-medium text-info',
        size === 'sm' ? 'size-7 text-[11px]' : 'size-9 text-xs',
      )}
      aria-hidden="true"
    >
      {initials}
      {presence && <span className={cn('absolute -right-0.5 -bottom-0.5 size-2.5 rounded-full ring-2 ring-paper', dot)} />}
    </span>
  )
}

export function CardHeader({ title, description, actions, className }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-wrap items-start justify-between gap-3', className)}>
      <div className="min-w-0">
        <h2 className="text-lg font-semibold tracking-tight text-ink sm:text-xl">{title}</h2>
        {description && <p className="mt-1 max-w-[60ch] text-sm text-ink-2">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  )
}

export function PageHeader({ title, description, actions, meta, divider = true }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; meta?: ReactNode; divider?: boolean }) {
  return (
    <header className={cn("flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between", divider && "border-b border-line pb-6")}>
      <div className="min-w-0">
        {meta && <div className="mb-2 flex flex-wrap items-center gap-2">{meta}</div>}
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
        {description && <p className="mt-2 max-w-[68ch] text-ink-2">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2 lg:shrink-0 lg:flex-nowrap">{actions}</div>}
    </header>
  )
}

export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex flex-col gap-3 py-2">
      <span className="sr-only">{label}</span>
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-4 animate-pulse rounded bg-well" style={{ width: `${90 - i * 18}%` }} />
      ))}
    </div>
  )
}

export function ErrorNote({ message }: { message: string }) {
  return (
    <p role="alert" className="rounded-md border border-alert/30 bg-alert-soft px-3 py-2 text-sm text-alert">
      Couldn't load this section: {message}. Check that the API is running, then reload the page.
    </p>
  )
}
