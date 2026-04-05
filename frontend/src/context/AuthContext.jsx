import { createContext, useContext, useState, useCallback } from 'react'
import api from '../api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const u = localStorage.getItem('user')
    if (!u) return null
    const parsed = JSON.parse(u)
    // If cached user has no roles, treat as logged out so they re-authenticate
    if (!parsed.roles || parsed.roles.length === 0) {
      localStorage.clear()
      return null
    }
    return parsed
  })

  const login = useCallback(async (email, password) => {
    const { data } = await api.post('/auth/login', { email, password })
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)

    // Decode JWT payload — pad base64 to avoid atob errors
    let roles = []
    try {
      const base64 = data.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
      const padded = base64 + '='.repeat((4 - base64.length % 4) % 4)
      const payload = JSON.parse(atob(padded))
      roles = payload.roles || payload.authorities || []
    } catch {}

    // Fallback: fetch roles from /auth/me if JWT had none
    if (!roles.length) {
      try {
        const me = await api.get('/auth/me')
        roles = me.data.roles || []
      } catch {}
    }

    const userData = { email, roles }
    localStorage.setItem('user', JSON.stringify(userData))
    setUser(userData)
    return userData
  }, [])

  const logout = useCallback(async () => {
    const refresh = localStorage.getItem('refreshToken')
    if (refresh) {
      try { await api.post('/auth/logout', { refreshToken: refresh }) } catch {}
    }
    localStorage.clear()
    setUser(null)
  }, [])

  const isAdmin = user?.roles?.some(r => r.includes('ADMIN'))
  const isAnalyst = user?.roles?.some(r => r.includes('ANALYST') || r.includes('ADMIN'))

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin, isAnalyst }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
