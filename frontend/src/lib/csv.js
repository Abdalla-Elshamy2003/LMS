/** Builds a UTF-8 CSV (with BOM so Excel opens Arabic correctly) and triggers a browser download. */
export function downloadCsv(filename, headers, rows) {
  const escape = (v) => {
    const s = v == null ? '' : String(v)
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const lines = [headers.map(escape).join(','), ...rows.map((r) => r.map(escape).join(','))]
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Human "time left / late by" phrase for a deadline (Arabic). */
export function timeLeft(iso, now = Date.now()) {
  if (!iso) return null
  const diff = Date.parse(iso) - now
  const abs = Math.abs(diff)
  const unit = abs < 3600e3 ? [Math.max(1, Math.round(abs / 60e3)), 'دقيقة'] : abs < 86400e3 ? [Math.round(abs / 3600e3), 'ساعة'] : [Math.round(abs / 86400e3), 'يوم']
  return { late: diff < 0, text: `${unit[0]} ${unit[1]}`, ms: diff }
}

export function formatDuration(seconds) {
  if (seconds == null) return '—'
  const m = Math.floor(seconds / 60), s = Math.floor(seconds % 60)
  return m >= 60 ? `${Math.floor(m / 60)} س ${m % 60} د` : `${m} د ${String(s).padStart(2, '0')} ث`
}

/** datetime-local <input> value for an ISO instant, in the user's zone. */
export function toLocalInput(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
