import { useRef, useState } from 'react'
import { ImagePlus, X } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { Spinner } from './ui'

const MAX_BYTES = 3 * 1024 * 1024

/**
 * Picks a PNG/JPG, uploads it, and hands the resulting public URL to `onChange`. The value stays a
 * plain URL string, so it drops into forms that used to take a typed link.
 */
export default function ImageUpload({ value, onChange, label = 'رفع صورة' }) {
  const input = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const pick = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (!/^image\/(png|jpeg)$/.test(file.type)) { setError('الصورة لازم تكون PNG أو JPG'); return }
    if (file.size > MAX_BYTES) { setError('اختر صورة أصغر من 3 ميجابايت'); return }
    setBusy(true); setError('')
    try {
      const body = new FormData()
      body.append('file', file)
      const { data } = await api.post('/images', body)
      onChange(data.url)
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر رفع الصورة'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-2">
      <div className="flex items-center gap-3">
        {value
          ? <img src={value} alt="" className="h-16 w-24 rounded-xl border border-ink-100 object-cover" />
          : <div className="grid h-16 w-24 place-items-center rounded-xl border border-dashed border-ink-200 text-ink-300"><ImagePlus size={22} /></div>}
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-soft" disabled={busy} onClick={() => input.current?.click()}>
            {busy ? <Spinner className="h-4 w-4" /> : <ImagePlus size={16} />} {value ? 'تغيير الصورة' : label}
          </button>
          {value && !busy && (
            <button type="button" className="btn-ghost" onClick={() => onChange('')}><X size={16} /> إزالة</button>
          )}
        </div>
      </div>
      <input ref={input} type="file" accept="image/png,image/jpeg" className="hidden" onChange={pick} />
      {error && <p role="alert" className="mt-1 text-xs font-semibold text-rose-600">{error}</p>}
    </div>
  )
}
