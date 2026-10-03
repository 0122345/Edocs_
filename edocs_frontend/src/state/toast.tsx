import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { CircleAlert, CircleCheck, X } from 'lucide-react'
import { cn } from '../lib/cn'

type Tone = 'success' | 'error'
interface Toast {
  id: number
  message: string
  tone: Tone
}

const ToastContext = createContext<((message: string, tone?: Tone) => void) | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const toast = useCallback(
    (message: string, tone: Tone = 'success') => {
      const id = Date.now() + Math.random()
      setToasts((t) => [...t.slice(-2), { id, message, tone }])
      setTimeout(() => dismiss(id), tone === 'error' ? 7000 : 4000)
    },
    [dismiss],
  )

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed right-4 bottom-4 z-[60] flex w-[min(380px,calc(100vw-2rem))] flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.tone === 'error' ? 'alert' : 'status'}
            className={cn(
              'pointer-events-auto flex items-start gap-3 rounded-md border bg-paper px-4 py-3 text-sm shadow-lg',
              t.tone === 'error' ? 'border-alert/40' : 'border-line',
            )}
          >
            {t.tone === 'error' ? <CircleAlert className="mt-0.5 size-4 shrink-0 text-alert" /> : <CircleCheck className="mt-0.5 size-4 shrink-0 text-seal" />}
            <p className="flex-1">{t.message}</p>
            <button type="button" onClick={() => dismiss(t.id)} className="rounded text-muted hover:text-ink" aria-label="Dismiss">
              <X className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}

/** Runs an async action, toasting success or the error message. Returns the result or undefined on failure. */
export function useAction() {
  const toast = useToast()
  return useCallback(
    async <T,>(fn: () => Promise<T>, success?: string | ((r: T) => string)): Promise<T | undefined> => {
      try {
        const r = await fn()
        if (success) toast(typeof success === 'function' ? success(r) : success)
        return r
      } catch (e) {
        toast(e instanceof Error ? e.message : String(e), 'error')
        return undefined
      }
    },
    [toast],
  )
}
