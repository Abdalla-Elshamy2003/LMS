import { fmtMoney } from '../../lib/format'

/** Days as the backend stores them (0 = Sunday … 6 = Saturday), listed from Saturday like the Egyptian week. */
export const DAYS = [
  { value: 6, label: 'السبت' }, { value: 0, label: 'الأحد' }, { value: 1, label: 'الاثنين' }, { value: 2, label: 'الثلاثاء' },
  { value: 3, label: 'الأربعاء' }, { value: 4, label: 'الخميس' }, { value: 5, label: 'الجمعة' },
]

/** The link inside a student's QR: it opens their card's page, and a signed-in center that opens it records attendance. */
export const cardUrl = (token) => `${window.location.origin}/c/${token}`

/** Today in Cairo as yyyy-MM-dd — the center's "today", wherever the browser is. */
export const cairoToday = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Cairo' }).format(new Date())

export const shiftDay = (iso, days) => {
  const d = new Date(`${iso}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export function fmtDay(iso, withWeekday = true) {
  if (!iso) return ''
  return new Date(`${iso}T12:00:00Z`).toLocaleDateString('ar-EG', {
    timeZone: 'UTC', day: 'numeric', month: 'long', ...(withWeekday ? { weekday: 'long' } : {}),
  })
}

export function fmtClock(instant) {
  if (!instant) return ''
  return new Date(instant).toLocaleTimeString('ar-EG', { hour: 'numeric', minute: '2-digit', timeZone: 'Africa/Cairo' })
}

export const money = (n) => fmtMoney(Number(n || 0))

/** "٢٠ ج" without the currency word, for tight table cells. */
export const num = (n) => Number(n || 0).toLocaleString('ar-EG')

/** A WhatsApp link that opens a chat with the number and the text filled in — nothing is sent until the user presses send. */
export function whatsappLink(phone, text) {
  const digits = String(phone || '').replace(/\D/g, '')
  if (!digits) return null
  const intl = digits.startsWith('20') ? digits : digits.startsWith('0') ? `2${digits}` : `20${digits}`
  return `https://wa.me/${intl}?text=${encodeURIComponent(text)}`
}

/** A short, pleasant beep for the scanning desk — success high, problem low. Silent where audio is blocked. */
export function beep(ok = true) {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    const ctx = beep.ctx || (beep.ctx = new Ctx())
    const osc = ctx.createOscillator(), gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = ok ? 1046 : 220
    gain.gain.setValueAtTime(0.0001, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + 0.01)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + (ok ? 0.18 : 0.4))
    osc.connect(gain).connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + (ok ? 0.2 : 0.42))
  } catch { /* no sound is fine */ }
}

/** A stable hue family per id, so each book cover keeps its colours (brand blues, teals and the warm amber). */
const COVERS = [
  ['#0c4a6e', '#0284c7'], ['#0b2e47', '#0e7490'], ['#075985', '#38bdf8'], ['#134e4a', '#14b8a6'],
  ['#7c2d12', '#ea7a2f'], ['#0f172a', '#0369a1'], ['#155e75', '#22d3ee'], ['#78350f', '#fbbf24'],
]
export const coverOf = (id) => COVERS[Math.abs(Number(id) || 0) % COVERS.length]
