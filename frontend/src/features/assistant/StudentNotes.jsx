import { useEffect, useState } from 'react'
import { CheckCircle2, NotebookPen, RotateCcw, Trash2 } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { fmtDateTime } from '../../lib/format'
import { EmptyState, Spinner } from '../../components/ui'
import { NOTE_KINDS, dayLabel } from './labels'

/** Private follow-up notes on one student - visible to the teacher and their assistants, never to the student or parent. */
export default function StudentNotes({ studentId }) {
  const [notes, setNotes] = useState(null)
  const [form, setForm] = useState({ kind: 'GENERAL', body: '', followUpOn: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = () => api.get('/assistant/notes', { params: { studentId } }).then((r) => setNotes(r.data))

  useEffect(() => {
    setNotes(null)
    load().catch((e) => { setError(apiErrorMessage(e, 'تعذّر تحميل الملاحظات')); setNotes([]) })
  }, [studentId])

  const run = async (fn) => {
    setBusy(true); setError('')
    try { await fn(); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر تنفيذ الطلب')) }
    finally { setBusy(false) }
  }
  const add = (e) => {
    e.preventDefault()
    run(async () => {
      await api.post('/assistant/notes', { studentId, kind: form.kind, body: form.body, followUpOn: form.followUpOn || null })
      setForm({ kind: 'GENERAL', body: '', followUpOn: '' })
    })
  }
  const setStatus = (n, status) => run(() => api.put(`/assistant/notes/${n.id}/status`, { status }))
  const remove = (n) => window.confirm('حذف هذه الملاحظة؟') && run(() => api.delete(`/assistant/notes/${n.id}`))

  return (
    <div className="card p-6">
      <div className="mb-1 flex items-center gap-2">
        <NotebookPen size={18} className="text-brand-600" />
        <h3 className="text-base font-extrabold text-ink-800">متابعة الطالب</h3>
      </div>
      <p className="mb-4 text-xs text-ink-400">ملاحظات خاصة بالمدرس والمساعد فقط — لا تظهر للطالب ولا لولي الأمر.</p>

      <form onSubmit={add} className="grid gap-3 rounded-2xl bg-ink-50 p-4 sm:grid-cols-[180px_1fr_170px_auto] sm:items-end">
        <label className="block text-xs font-bold">النوع
          <select className="input mt-2" value={form.kind} onChange={(e) => setForm((f) => ({ ...f, kind: e.target.value }))}>
            {Object.entries(NOTE_KINDS).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </label>
        <label className="block text-xs font-bold">الملاحظة
          <input required maxLength={2000} className="input mt-2" placeholder="مثال: كلّمت ولي الأمر واتفقنا على..." value={form.body} onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))} />
        </label>
        <label className="block text-xs font-bold">تذكير بالمتابعة (اختياري)
          <input type="date" className="input mt-2" value={form.followUpOn} onChange={(e) => setForm((f) => ({ ...f, followUpOn: e.target.value }))} />
        </label>
        <button disabled={busy || !form.body.trim()} className="btn-primary">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'إضافة'}</button>
      </form>
      {error && <p role="alert" className="mt-3 text-sm text-rose-600">{error}</p>}

      {!notes ? <div className="flex justify-center py-8"><Spinner className="h-6 w-6" /></div>
        : notes.length === 0 ? <EmptyState icon={NotebookPen} title="لا توجد ملاحظات بعد" />
        : (
          <ul className="mt-4 divide-y divide-ink-100">
            {notes.map((n) => (
              <li key={n.id} className="flex flex-wrap items-start gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="chip bg-brand-50 text-brand-700">{NOTE_KINDS[n.kind] || n.kind}</span>
                    {n.status === 'RESOLVED' && <span className="chip bg-emerald-50 text-emerald-700">تمت المتابعة</span>}
                    {n.due && <span className="chip bg-amber-50 text-amber-700">متابعة مستحقة</span>}
                    {n.followUpOn && n.status === 'OPEN' && !n.due && <span className="chip bg-ink-100 text-ink-600">متابعة {dayLabel(n.followUpOn)}</span>}
                  </p>
                  <p className={`mt-1.5 text-sm leading-7 ${n.status === 'RESOLVED' ? 'text-ink-400' : 'text-ink-700'}`}>{n.body}</p>
                  <p className="text-xs text-ink-400">{n.authorName} · {fmtDateTime(n.createdAt)}</p>
                </div>
                <div className="flex gap-1.5">
                  {n.status === 'OPEN'
                    ? <button disabled={busy} onClick={() => setStatus(n, 'RESOLVED')} className="btn-soft text-xs"><CheckCircle2 size={15} /> تمت</button>
                    : <button disabled={busy} onClick={() => setStatus(n, 'OPEN')} className="btn-ghost text-xs"><RotateCcw size={15} /> إعادة فتح</button>}
                  <button disabled={busy} onClick={() => remove(n)} aria-label="حذف الملاحظة" className="btn-ghost text-xs text-rose-600"><Trash2 size={15} /></button>
                </div>
              </li>
            ))}
          </ul>
        )}
    </div>
  )
}
