import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('manarah_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const academy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
  if (academy && !/^\/(academies|auth|users\/me|public)(\/|$)/.test(config.url)) config.headers['X-Academy-Id'] = academy.id
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response && err.response.status === 401 && !err.config.url.includes('/auth/login')) {
      localStorage.removeItem('manarah_token')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
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
