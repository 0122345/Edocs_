import { useEffect, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { cn } from '../lib/cn'
import { deniedReason, type Permission } from '../lib/rbac'
import { useAuth } from '../state/auth'

/** Accessible modal built on <dialog>: focus trap, Escape to close, backdrop click to close. */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = 'md',
}: {
  open: boolean
  onClose: () => void
  title: string
  description?: ReactNode
  children: ReactNode
  footer?: ReactNode
  size?: 'md' | 'lg'
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => e.target === ref.current && onClose()}
      aria-labelledby="modal-title"
      className={cn(
        'm-auto w-[calc(100vw-2rem)] rounded-lg border border-line bg-paper p-0 text-ink shadow-2xl backdrop:bg-ink/40',
        size === 'lg' ? 'max-w-2xl' : 'max-w-lg',
      )}
    >
      {open && (
        <div className="flex max-h-[85dvh] flex-col">
          <header className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
            <div>
              <h2 id="modal-title" className="text-lg font-semibold tracking-tight">{title}</h2>
              {description && <p className="mt-1 text-sm text-ink-2">{description}</p>}
            </div>
            <button type="button" onClick={onClose} className="rounded p-1 text-muted hover:bg-well hover:text-ink" aria-label="Close">
              <X className="size-5" />
            </button>
          </header>
          <div className="overflow-y-auto px-6 py-5">{children}</div>
          {footer && <footer className="flex flex-wrap justify-end gap-2 border-t border-line bg-canvas px-6 py-4">{footer}</footer>}
        </div>
      )}
    </dialog>
  )
}

/** Small dropdown menu anchored to a trigger. Closes on outside click and Escape. */
export function Menu({
  trigger,
  children,
  align = 'left',
  label,
  triggerClassName,
}: {
  trigger: ReactNode
  children: (close: () => void) => ReactNode
  align?: 'left' | 'right'
  label: string
  triggerClassName?: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false)
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button type="button" aria-haspopup="menu" aria-expanded={open} aria-label={label} onClick={() => setOpen((v) => !v)} className={triggerClassName}>
        {trigger}
      </button>
      {open && (
        <div role="menu" className={cn('absolute top-full z-40 mt-1 min-w-48 rounded-md border border-line bg-paper py-1 shadow-lg', align === 'right' ? 'right-0' : 'left-0')}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  )
}

export function MenuItem({ onSelect, children, disabled, tone, title }: { onSelect: () => void; children: ReactNode; disabled?: boolean; tone?: 'alert'; title?: string }) {
  return (
    <button
      type="button"
      role="menuitem"
      disabled={disabled}
      title={title}
      onClick={onSelect}
      className={cn('flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-well disabled:cursor-not-allowed disabled:opacity-45', tone === 'alert' ? 'text-alert' : 'text-ink')}
    >
      {children}
    </button>
  )
}

/** A button that is disabled, with an explanation, when the current role lacks the permission. */
export function Guarded({ permission, children, title, disabled, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { permission: Permission }) {
  const { can } = useAuth()
  const allowed = can(permission)
  return (
    <button type="button" {...rest} disabled={!allowed || disabled} title={allowed ? title : deniedReason(permission)}>
      {children}
    </button>
  )
}
