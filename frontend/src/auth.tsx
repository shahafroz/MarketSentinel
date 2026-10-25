import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, getToken, setToken } from './api'
import type { LoginResponse, Role, User } from './types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (firmName: string, email: string, password: string) => Promise<void>
  logout: () => void
  can: (...roles: Role[]) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(() => getToken() !== null)

  useEffect(() => {
    if (!getToken()) return
    api<User>('/auth/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onLogout = () => setUser(null)
    window.addEventListener('ms:logout', onLogout)
    return () => window.removeEventListener('ms:logout', onLogout)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<LoginResponse>('/auth/login', { method: 'POST', body: { email, password } })
    setToken(res.token)
    setUser(res.user)
  }, [])

  const signup = useCallback(async (firmName: string, email: string, password: string) => {
    const res = await api<LoginResponse>('/auth/signup', { method: 'POST', body: { firmName, email, password } })
    setToken(res.token)
    setUser(res.user)
  }, [])

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  // UI hint only: the server checks every role again on every request.
  const can = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user])

  return <AuthContext.Provider value={{ user, loading, login, signup, logout, can }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
