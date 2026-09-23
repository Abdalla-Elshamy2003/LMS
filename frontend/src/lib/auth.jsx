import { createContext, useContext, useEffect, useState } from 'react'
import api from './api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('manarah_token')
    if (!token) { setLoading(false); return }
    api.get('/auth/me')
      .then((res) => setUser(res.data))
      .catch(() => localStorage.removeItem('manarah_token'))
      .finally(() => setLoading(false))
  }, [])

  const login = async (email, password) => {
    sessionStorage.removeItem('manarah_academy')
    const res = await api.post('/auth/login', { email, password })
    localStorage.setItem('manarah_token', res.data.accessToken)
    setUser(res.data.user)
    return res.data.user
  }

  // Used right after self-registration: the register endpoint already hands back a valid
  // access token, so we skip a second round-trip to /auth/login and just fetch the profile.
  const loginWithToken = async (accessToken) => {
    localStorage.setItem('manarah_token', accessToken)
    const res = await api.get('/auth/me')
    setUser(res.data)
    return res.data
  }

  // A student with several teachers has a seat in each teacher's space. Moving between them swaps the session
  // (the server also re-issues the cookie videos are fetched with) and reloads, so nothing from the previous
  // teacher lingers in memory.
  const adoptSession = (accessToken, target = '/app') => {
    localStorage.setItem('manarah_token', accessToken)
    sessionStorage.removeItem('manarah_academy')
    location.href = target
  }
  const switchTeacher = async (userId, target) => {
    const { data } = await api.post('/me/teachers/switch', { userId })
    adoptSession(data.accessToken, target)
  }
  const joinTeacher = async (slug, courseId, target) => {
    const { data } = await api.post('/me/teachers/join', { slug, courseId })
    adoptSession(data.accessToken, target)
  }

  const logout = async () => {
    try { await api.post('/auth/logout') } catch { /* local state is still cleared */ }
    sessionStorage.removeItem('manarah_academy')
    Object.keys(sessionStorage).filter(key => key.startsWith('manarah-homework-')).forEach(key => sessionStorage.removeItem(key))
    localStorage.removeItem('manarah_token')
    setUser(null)
    location.href = '/login'
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, loginWithToken, logout, switchTeacher, joinTeacher }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
