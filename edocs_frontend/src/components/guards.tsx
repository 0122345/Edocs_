import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { ShieldOff } from 'lucide-react'
import { useAuth } from '../state/auth'
import { AppShell } from '../layouts/AppShell'
import { deniedReason, roleLabels, type Permission } from '../lib/rbac'

/** Sends signed-out visitors to /login and brings them back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  return <>{children}</>
}

/** Blocks a route for roles without the permission, and says why. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { user, can } = useAuth()
  if (can(permission)) return <>{children}</>
  return (
    <AppShell breadcrumb="Access denied">
      <main className="mx-auto flex max-w-xl flex-col items-start gap-4 px-4 py-24 sm:px-8">
        <ShieldOff className="size-8 text-alert" strokeWidth={1.5} />
        <h1 className="text-3xl font-semibold tracking-tight">You don't have access to this page</h1>
        <p className="text-ink-2">
          You're signed in as {user ? roleLabels[user.role].toLowerCase() : 'a guest'}. {deniedReason(permission)} Ask an administrator to change your role if you need it.
        </p>
        <Link to="/" className="btn btn-primary">Go to dashboard</Link>
      </main>
    </AppShell>
  )
}
