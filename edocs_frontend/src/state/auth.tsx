import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, isMockMode, type LoginResult } from '../services/api'
import { db } from '../services/mockDb'
import { can, type Permission } from '../lib/rbac'
import type { User } from '../data/types'

interface AuthValue {
  user: User | null
  login: (email: string, password: string) => Promise<LoginResult>
  verifyMfa: (challengeId: string, code: string) => Promise<void>
  loginWithProvider: (provider: 'google' | 'microsoft') => Promise<void>
  logout: () => Promise<void>
  can: (permission: Permission) => boolean
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => api.session()?.user ?? null)

  // Pick up the token handed back by an OAuth2 (Google / Microsoft) redirect.
  useEffect(() => {
    api.completeOAuthRedirect().then((s) => s && setUser(s.user)).catch(() => setUser(null))
  }, [])

  // Keep the signed-in user in sync when an admin changes their role or status, or the backend ends the session.
  useEffect(
    () =>
      api.subscribe(() => {
        if (!isMockMode) {
          if (!api.session()) setUser(null)
          return
        }
        setUser((u) => {
          if (!u) return u
          const fresh = db.get().users.find((x) => x.id === u.id)
          if (!fresh || !fresh.active) return null
          return JSON.stringify(fresh) === JSON.stringify(u) ? u : fresh
        })
      }),
    [],
  )

  const login = useCallback(async (email: string, password: string) => {
    const result = await api.login(email, password)
    if (result.kind === 'session') setUser(result.session.user)
    return result
  }, [])

  const verifyMfa = useCallback(async (challengeId: string, code: string) => {
    setUser((await api.verifyMfa(challengeId, code)).user)
  }, [])

  const loginWithProvider = useCallback(async (provider: 'google' | 'microsoft') => {
    setUser((await api.loginWithProvider(provider)).user)
  }, [])

  const logout = useCallback(async () => {
    await api.logout()
    setUser(null)
  }, [])

  const value = useMemo<AuthValue>(
    () => ({ user, login, verifyMfa, loginWithProvider, logout, can: (p) => can(user?.role, p) }),
    [user, login, verifyMfa, loginWithProvider, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
