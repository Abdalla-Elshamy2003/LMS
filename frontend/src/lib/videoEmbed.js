/** YouTube/Vimeo links need their embed form; anything else is played as a direct file (returns null). */
export function embedUrl(raw) {
  try {
    const u = new URL(raw)
    const host = u.hostname.replace(/^www\./, '')
    if (host === 'youtu.be') return `https://www.youtube-nocookie.com/embed/${u.pathname.slice(1)}`
    if (host.endsWith('youtube.com')) {
      const id = u.searchParams.get('v') || u.pathname.split('/embed/')[1]
      return id ? `https://www.youtube-nocookie.com/embed/${id}` : null
    }
    if (host.endsWith('vimeo.com')) return `https://player.vimeo.com/video/${u.pathname.split('/').filter(Boolean).pop()}`
    return null
  } catch { return null }
}
