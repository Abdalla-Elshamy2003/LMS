import api from './api'

const PARALLEL = 3
const RETRIES = 3

/**
 * Uploads a lesson video straight to the video store, in parts: the server hands out one URL per part (signed R2
 * URLs, so the file never passes through our server — or our own endpoint while videos stay on the server's disk),
 * three parts go at once, a failed part is retried, and the server then checks the whole file and adds it to the
 * lesson. Returns { materialId, assetId, status }.
 */
export async function uploadVideo({ lessonId, title, file, onProgress, signal }) {
  const { data: up } = await api.post('/videos/uploads', { lessonId, title, fileName: file.name, sizeBytes: file.size })
  const sent = new Array(up.parts).fill(0)
  const report = () => onProgress?.(Math.min(99, Math.round((sent.reduce((a, b) => a + b, 0) / file.size) * 100)))
  const parts = []
  let next = 0
  try {
    const worker = async () => {
      while (next < up.parts) {
        const n = ++next
        const blob = file.slice((n - 1) * up.partSize, Math.min(n * up.partSize, file.size))
        for (let attempt = 1; ; attempt++) {
          try {
            const etag = await putPart(up.urls[n - 1], blob, up.withSession, (loaded) => { sent[n - 1] = loaded; report() }, signal)
            parts.push({ partNumber: n, etag })
            break
          } catch (e) {
            sent[n - 1] = 0; report()
            if (signal?.aborted || attempt >= RETRIES) throw e
            await new Promise((r) => setTimeout(r, 1000 * attempt))
          }
        }
      }
    }
    await Promise.all(Array.from({ length: Math.min(PARALLEL, up.parts) }, worker))
    const { data } = await api.post(`/videos/uploads/${up.assetId}/complete`, { parts })
    onProgress?.(100)
    return data
  } catch (e) {
    api.delete(`/videos/uploads/${up.assetId}`).catch(() => {})
    throw e
  }
}

/** One part, with upload progress. Our own endpoint needs the session; a signed R2 URL must not get it. */
function putPart(url, blob, withSession, onLoaded, signal) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    if (withSession) {
      const token = localStorage.getItem('manarah_token')
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      const academy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
      if (academy) xhr.setRequestHeader('X-Academy-Id', academy.id)
      xhr.setRequestHeader('Content-Type', 'application/octet-stream')
    }
    xhr.upload.onprogress = (e) => onLoaded(e.loaded)
    xhr.onload = () => {
      const etag = xhr.getResponseHeader('ETag')
      if (xhr.status >= 200 && xhr.status < 300 && etag) { onLoaded(blob.size); resolve(etag) }
      else reject(new Error(xhr.status >= 200 && xhr.status < 300 ? 'التخزين ما رجّعش ETag (راجع إعدادات CORS)' : `فشل رفع جزء (${xhr.status})`))
    }
    xhr.onerror = () => reject(new Error('انقطع الاتصال أثناء رفع الفيديو'))
    xhr.onabort = () => reject(new Error('اتلغى الرفع'))
    signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(blob)
  })
}
