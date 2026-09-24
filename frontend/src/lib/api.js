import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

/**
 * Client for deliberately public endpoints (QR verification, ...). It never sends credentials and never
 * redirects to /login on 401, so a visitor holding a stale session token can still use public pages.
 */
export const publicApi = axios.create({ baseURL: '/api/public' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('manarah_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const academy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
  // Head-office screens (control center, packages) and a student's own endpoints never run inside a teacher's space.
  if (academy && !/^\/(academies|auth|users\/me|public|admin|bundles|me)(\/|$)/.test(config.url)) config.headers['X-Academy-Id'] = academy.id
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response && err.response.status === 401 && !err.config.url.includes('/auth/login')) {
      localStorage.removeItem('manarah_token')
      // Only the signed-in app is bounced to login on an expired session. The startup /auth/me check is
      // skipped: the auth provider and route guards already handle it, and forcing a redirect there sent
      // anyone holding a stale token - e.g. a parent scanning a student's QR - to /login from public pages.
      const isSessionProbe = err.config.url.includes('/auth/me')
      if (!isSessionProbe && location.pathname.startsWith('/app')) location.href = '/login'
    }
    return Promise.reject(err)
  },
)

/**
 * Builds a same-origin file URL. Authentication is supplied by the server-issued HttpOnly,
 * SameSite=Strict file-session cookie; the bearer token is intentionally never put in the URL.
 */
export function fileUrl(key) {
  if (!key) return ''
  const academy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
  return `/api/files/${key}${academy ? `?academy=${encodeURIComponent(academy.id)}` : ''}`
}

export default api
