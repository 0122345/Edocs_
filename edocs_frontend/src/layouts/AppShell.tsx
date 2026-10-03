import { useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import {
  Bell,
  CircleHelp,
  FileStack,
  FileText,
  LayoutGrid,
  LogOut,
  Menu as MenuIcon,
  Plus,
  Settings,
  Share2,
  ShieldCheck,
  ShieldHalf,
  UserPlus,
  X,
  Mail,
  MessageSquareText,
  BellRing,
} from 'lucide-react'
import { cn } from '../lib/cn'
import { organization } from '../data/mock'
import { Avatar } from '../components/ui'
import { Menu, MenuItem } from '../components/overlay'
import { InviteMemberModal, NewDocumentModal, ShareModal } from '../components/modals'
import { useAuth } from '../state/auth'
import { useResource } from '../lib/useResource'
import { api } from '../services/api'
import { roleLabels, type Permission } from '../lib/rbac'
import type { AppNotification } from '../data/types'

const sideNav: { to: string; label: string; icon: typeof LayoutGrid; end?: boolean; permission?: Permission }[] = [
  { to: '/', label: 'Dashboard', icon: LayoutGrid, end: true },
  { to: '/documents', label: 'Documents', icon: FileStack },
  { to: '/templates', label: 'Templates', icon: FileText },
  { to: '/archive', label: 'Audit logs', icon: ShieldHalf, permission: 'audit:read' },
  { to: '/compliance', label: 'Compliance', icon: ShieldCheck },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export interface ShellDocument {
  id: string
  title: string
  category: string
}

function Sidebar({ onNavigate, onInvite }: { onNavigate?: () => void; onInvite: () => void }) {
  const { can, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="flex h-full flex-col gap-6 overflow-y-auto px-4 py-6">
      <div className="flex items-center gap-3 px-1">
        <span className="flex size-10 items-center justify-center rounded-md bg-ink font-semibold text-white">E</span>
        <div className="min-w-0 leading-tight">
          <p className="truncate font-semibold">{organization.name}</p>
          <p className="text-xs text-muted">{organization.tier}</p>
        </div>
      </div>

      <nav aria-label="Workspace" className="flex flex-col gap-1">
        {sideNav
          .filter((n) => !n.permission || can(n.permission))
          .map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onNavigate}
              className={({ isActive }) =>
                cn('flex items-center gap-3 rounded-md px-3 py-2.5 text-sm transition-colors', isActive ? 'bg-nav font-medium text-ink' : 'text-ink-2 hover:bg-well hover:text-ink')
              }
            >
              <Icon className="size-[18px]" strokeWidth={1.75} />
              {label}
            </NavLink>
          ))}
      </nav>

      <div className="mt-auto flex flex-col gap-4">
        {can('members:manage') && (
          <button type="button" className="btn btn-primary w-full py-3" onClick={onInvite}>
            <UserPlus className="size-4" /> Invite team
          </button>
        )}
        <div className="flex flex-col gap-1 border-t border-line pt-4 text-sm">
          <Link to="/help" onClick={onNavigate} className="flex items-center gap-3 rounded-md px-3 py-2 text-ink-2 hover:bg-well hover:text-ink">
            <CircleHelp className="size-4" /> Help center
          </Link>
          <button
            type="button"
            onClick={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
            className="flex items-center gap-3 rounded-md px-3 py-2 text-left text-alert hover:bg-alert-soft"
          >
            <LogOut className="size-4" /> Log out
          </button>
        </div>
      </div>
    </div>
  )
}

const channelIcon = { email: Mail, sms: MessageSquareText, in_app: BellRing } as const

function timeAgo(iso: string) {
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (mins < 1) return 'Just now'
  if (mins < 60) return `${mins}m ago`
  if (mins < 60 * 24) return `${Math.round(mins / 60)}h ago`
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function NotificationsMenu() {
  const navigate = useNavigate()
  const { data } = useResource(api.listNotifications)
  const items = data ?? []
  const unread = items.filter((n) => !n.read).length

  function open(n: AppNotification, close: () => void) {
    void api.markNotificationsRead([n.id])
    close()
    if (n.link) navigate(n.link)
  }

  return (
    <Menu
      align="right"
      label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
      triggerClassName="relative hidden rounded p-2 text-ink-2 hover:bg-well md:block"
      trigger={
        <>
          <Bell className="size-5" />
          {unread > 0 && <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-alert" />}
        </>
      }
    >
      {(close) => (
        <div className="w-[min(360px,calc(100vw-2rem))]">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <p className="text-sm font-semibold">Notifications</p>
            <button type="button" disabled={!unread} onClick={() => api.markNotificationsRead()} className="text-xs font-medium text-info hover:underline disabled:text-muted disabled:no-underline">
              Mark all as read
            </button>
          </div>
          <ul className="max-h-96 overflow-y-auto">
            {items.length === 0 && <li className="px-4 py-8 text-center text-sm text-ink-2">You're all caught up.</li>}
            {items.slice(0, 12).map((n) => {
              const Icon = channelIcon[n.channel]
              return (
                <li key={n.id}>
                  <button type="button" role="menuitem" onClick={() => open(n, close)} className={cn('flex w-full gap-3 px-4 py-3 text-left hover:bg-well', !n.read && 'bg-info-soft/40')}>
                    <Icon className="mt-0.5 size-4 shrink-0 text-ink-2" aria-label={n.channel} />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline justify-between gap-2">
                        <span className={cn('text-sm', !n.read && 'font-semibold')}>{n.title}</span>
                        <span className="shrink-0 text-xs text-muted">{timeAgo(n.createdAt)}</span>
                      </span>
                      <span className="mt-0.5 block text-sm text-ink-2">{n.body}</span>
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </Menu>
  )
}

export function UserMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user) return null
  return (
    <Menu align="right" label="Account menu" triggerClassName="rounded-full" trigger={<Avatar initials={user.initials} />}>
      {(close) => (
        <>
          <div className="border-b border-line px-3 py-2.5">
            <p className="text-sm font-medium">{user.name}</p>
            <p className="text-xs text-muted">{user.email}</p>
            <p className="mt-1 text-xs text-info">{roleLabels[user.role]}</p>
          </div>
          <MenuItem onSelect={() => { close(); navigate('/settings') }}><Settings className="size-4" /> Account and settings</MenuItem>
          <MenuItem onSelect={() => { close(); navigate('/help') }}><CircleHelp className="size-4" /> Help center</MenuItem>
          <MenuItem tone="alert" onSelect={async () => { close(); await logout(); navigate('/login', { replace: true }) }}><LogOut className="size-4" /> Log out</MenuItem>
        </>
      )}
    </Menu>
  )
}

export function AppShell({
  children,
  doc,
  breadcrumb,
  docked = true,
}: {
  children: ReactNode
  /** The document this screen is about; drives breadcrumb, Share and doc-scoped tabs. */
  doc?: ShellDocument
  breadcrumb?: string
  /** When false the sidebar only opens as a drawer, giving the page full width. */
  docked?: boolean
}) {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [shareOpen, setShareOpen] = useState(false)
  const [newDocOpen, setNewDocOpen] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const { pathname, hash, search } = useLocation()

  useEffect(() => setDrawerOpen(false), [pathname])

  // Deep links such as /help#api or /settings#members scroll to their section.
  useEffect(() => {
    if (!hash) return
    const t = setTimeout(() => document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50)
    return () => clearTimeout(t)
  }, [pathname, hash])

  useEffect(() => {
    if (!drawerOpen) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setDrawerOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [drawerOpen])

  const { can } = useAuth()
  const onSigningList = pathname === '/documents' && new URLSearchParams(search).get('status') === 'out_for_signature'
  const topNav = [
    { to: '/', label: 'Overview', active: pathname === '/' },
    { to: doc ? `/workflow/${doc.id}` : '/documents?status=out_for_signature', label: 'Signatures', active: pathname.startsWith('/workflow') || onSigningList },
    ...(can('audit:read') ? [{ to: doc ? `/archive?doc=${doc.id}` : '/archive', label: 'Audit trail', active: pathname.startsWith('/archive') }] : []),
    { to: '/settings', label: 'Settings', active: pathname.startsWith('/settings') },
  ]

  const crumbs = doc ? (
    <>
      <Link to="/documents" className="hover:text-ink hover:underline">Documents</Link> / {doc.category} / <span className="text-ink-2">{doc.title}</span>
    </>
  ) : (
    breadcrumb
  )

  return (
    <div className="min-h-dvh">
      {docked && (
        <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-line bg-canvas lg:block">
          <Sidebar onInvite={() => setInviteOpen(true)} />
        </aside>
      )}

      {drawerOpen && (
        <div className="fixed inset-0 z-50" role="dialog" aria-modal="true" aria-label="Navigation">
          <button type="button" aria-label="Close navigation" className="absolute inset-0 bg-ink/30" onClick={() => setDrawerOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 border-r border-line bg-canvas shadow-xl">
            <button type="button" onClick={() => setDrawerOpen(false)} className="absolute top-5 right-3 rounded p-1.5 text-muted hover:bg-well" aria-label="Close navigation">
              <X className="size-5" />
            </button>
            <Sidebar onNavigate={() => setDrawerOpen(false)} onInvite={() => { setDrawerOpen(false); setInviteOpen(true) }} />
          </aside>
        </div>
      )}

      <div className={cn('flex min-h-dvh flex-col', docked && 'lg:pl-64')}>
        <header className="sticky top-0 z-20 border-b border-line bg-paper/95 backdrop-blur">
          <div className="flex h-16 items-center gap-3 px-4 sm:px-6">
            <button type="button" onClick={() => setDrawerOpen(true)} className={cn('rounded p-1.5 text-ink hover:bg-well', docked && 'lg:hidden')} aria-label="Open navigation">
              <MenuIcon className="size-5" />
            </button>
            <div className="flex min-w-0 items-baseline gap-3">
              <Link to="/" className="text-lg font-bold tracking-tight">EDOCS</Link>
              {crumbs && (
                <nav aria-label="Breadcrumb" className={cn('hidden min-w-0 truncate text-sm text-muted md:block', docked && 'xl:hidden 2xl:block')}>
                  {crumbs}
                </nav>
              )}
            </div>

            <nav aria-label="Sections" className="ml-auto hidden items-center gap-1 xl:flex">
              {topNav.map((t) => (
                <Link
                  key={t.label}
                  to={t.to}
                  aria-current={t.active ? 'page' : undefined}
                  className={cn('border-b-2 px-3 py-5 text-sm whitespace-nowrap transition-colors', t.active ? 'border-ink font-medium text-ink' : 'border-transparent text-ink-2 hover:text-ink')}
                >
                  {t.label}
                </Link>
              ))}
            </nav>

            <div className="ml-auto flex items-center gap-2 xl:ml-4">
              <button type="button" className="btn btn-ghost hidden sm:inline-flex" onClick={() => setShareOpen(true)}>
                <Share2 className="size-4" /> Share
              </button>
              {can('document:create') && (
                <button type="button" className="btn btn-primary" onClick={() => setNewDocOpen(true)} aria-label="New document">
                  <Plus className="size-4" /> <span className="hidden sm:inline">New document</span>
                </button>
              )}
              <span className="mx-1 hidden h-6 w-px bg-line md:block" />
              <NotificationsMenu />
              <Link to="/help" className="hidden rounded p-2 text-ink-2 hover:bg-well md:block" aria-label="Help center">
                <CircleHelp className="size-5" />
              </Link>
              <UserMenu />
            </div>
          </div>
          <nav aria-label="Sections" className="flex gap-1 overflow-x-auto border-t border-line px-2 xl:hidden">
            {topNav.map((t) => (
              <Link
                key={t.label}
                to={t.to}
                aria-current={t.active ? 'page' : undefined}
                className={cn('shrink-0 border-b-2 px-3 py-2.5 text-sm', t.active ? 'border-ink font-medium' : 'border-transparent text-ink-2')}
              >
                {t.label}
              </Link>
            ))}
          </nav>
        </header>

        <div className="flex-1">{children}</div>

        <footer className="border-t border-line px-4 py-6 text-sm text-muted sm:px-8">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <p>Edocs enterprise e-contracts and document management, v1.0 (September 2026)</p>
            <nav aria-label="Footer" className="flex flex-wrap gap-x-6 gap-y-2">
              <Link to="/help#security" className="hover:text-ink">Security framework</Link>
              <Link to="/help#compliance" className="hover:text-ink">Compliance: eIDAS / SOC 2</Link>
              <Link to="/help#api" className="hover:text-ink">API documentation</Link>
            </nav>
          </div>
        </footer>
      </div>

      <ShareModal open={shareOpen} onClose={() => setShareOpen(false)} doc={doc} />
      <NewDocumentModal open={newDocOpen} onClose={() => setNewDocOpen(false)} />
      <InviteMemberModal open={inviteOpen} onClose={() => setInviteOpen(false)} />
    </div>
  )
}
