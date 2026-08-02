import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, clearToken, getToken, setToken, UNAUTHORIZED_EVENT } from './api/client'
import type { UserDto } from './types'

interface AuthContextValue {
  user: UserDto | null
  loading: boolean
  error: string | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const logout = useCallback(() => {
    clearToken()
    setUser(null)
  }, [])

  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    api
      .me()
      .then(setUser)
      .catch(() => clearToken())
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout)
  }, [logout])

  const login = useCallback(async (email: string, password: string) => {
    setError(null)
    try {
      const auth = await api.login(email, password)
      setToken(auth.token)
      setUser(auth.user)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not log in.')
      throw e
    }
  }, [])

  const register = useCallback(async (email: string, password: string) => {
    setError(null)
    try {
      const auth = await api.register(email, password)
      setToken(auth.token)
      setUser(auth.user)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create an account.')
      throw e
    }
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, error, login, register, logout }}>{children}</AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
