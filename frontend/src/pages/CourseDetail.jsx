import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  ArrowRight, PlayCircle, FileText, Presentation, Link as LinkIcon, Layers, Video, Users2,
  Plus, Image as ImageIcon, Music, Download, Trash2, X,
} from 'lucide-react'
import api, { fileUrl } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { fmtMoney } from '../lib/format'
import { apiErrorMessage } from '../lib/apiError'

const MAT = {
  VIDEO: { icon: Video, c: 'bg-rose-50 text-rose-600', label: 'فيديو' },
  PDF: { icon: FileText, c: 'bg-red-50 text-red-600', label: 'PDF' },
  PPT: { icon: Presentation, c: 'bg-orange-50 text-orange-600', label: 'عرض' },
  DOC: { icon: FileText, c: 'bg-sky-50 text-sky-600', label: 'مستند' },
  IMAGE: { icon: ImageIcon, c: 'bg-violet-50 text-violet-600', label: 'صورة' },
  AUDIO: { icon: Music, c: 'bg-emerald-50 text-emerald-600', label: 'صوت' },
  LINK: { icon: LinkIcon, c: 'bg-ink-100 text-ink-600', label: 'رابط' },
}

export function AddModule({ courseId, onClose, onSaved }) {
  const [title, setTitle] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const save = async () => { setSaving(true); setError(''); try { await api.post(`/courses/${courseId}/modules`, { title: title.trim() }); onSaved() } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ الفصل')) } finally { setSaving(false) } }
  return (
    <Modal open onClose={onClose} title="فصل جديد">
      <div className="space-y-4">
        <div><label className="label">عنوان الفصل</label><input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="الفصل الأول" /></div>
        {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving || !title} className="btn-primary">حفظ</button></div>
      </div>
    </Modal>
  )
}

export function AddLesson({ moduleId, onClose, onSaved }) {
  const [form, setForm] = useState({ title: '', durationMin: 60, contentText: '' })
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const [error, setError] = useState('')
  const save = async () => { setSaving(true); setError(''); try { await api.post(`/courses/modules/${moduleId}/lessons`, { ...form, title: form.title.trim(), durationMin: Math.max(0, Number(form.durationMin)) }); onSaved() } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ الدرس')) } finally { setSaving(false) } }
  return (
    <Modal open onClose={onClose} title="درس جديد">
      <div className="space-y-4">
        <div><label className="label">عنوان الدرس</label><input className="input" value={form.title} onChange={set('title')} placeholder="المحاضرة الأولى" /></div>
        <div><label className="label">المدة (دقيقة)</label><input type="number" className="input" value={form.durationMin} onChange={set('durationMin')} /></div>
        <div><label className="label">شرح الدرس</label><textarea className="input" rows={4} value={form.contentText} onChange={set('contentText')} /></div>
        {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving || !form.title} className="btn-primary">حفظ</button></div>
      </div>
    </Modal>
  )
}

export function EditModule({ module, onClose, onSaved }) {
  const [title, setTitle] = useState(module.title)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const save = async () => { setSaving(true); setError(''); try { await api.put(`/courses/modules/${module.id}`, { title: title.trim() }); onSaved() } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ التعديل')) } finally { setSaving(false) } }
  return (
    <Modal open onClose={onClose} title="تعديل الفصل">
      <div className="space-y-4">
        <div><label className="label">عنوان الفصل</label><input className="input" value={title} onChange={(e) => setTitle(e.target.value)} /></div>
        {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving || !title.trim()} className="btn-primary">حفظ</button></div>
      </div>
    </Modal>
  )
}

export function EditLesson({ lesson, onClose, onSaved }) {
  const [form, setForm] = useState({ title: lesson.title, durationMin: lesson.durationMin ?? 0, contentText: lesson.contentText || '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async () => {
    setSaving(true); setError('')
    try {
      // releaseAt is sent back unchanged so editing the text never cancels a scheduled release.
      await api.put(`/courses/lessons/${lesson.id}`, { ...form, title: form.title.trim(), durationMin: Math.max(0, Number(form.durationMin)), releaseAt: lesson.releaseAt || null })
      onSaved()
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ التعديل')) } finally { setSaving(false) }
  }
  return (
    <Modal open onClose={onClose} title="تعديل الدرس">
      <div className="space-y-4">
        <div><label className="label">عنوان الدرس</label><input className="input" value={form.title} onChange={set('title')} /></div>
        <div><label className="label">المدة (دقيقة)</label><input type="number" className="input" value={form.durationMin} onChange={set('durationMin')} /></div>
        <div><label className="label">شرح الدرس</label><textarea className="input" rows={4} value={form.contentText} onChange={set('contentText')} /></div>
        {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving || !form.title.trim()} className="btn-primary">حفظ</button></div>
      </div>
    </Modal>
  )
}

/** Asks before deleting a chapter or lesson; `warning` says what else goes with it. */
export function ConfirmDelete({ title, warning, onClose, onConfirm }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const go = async () => { setBusy(true); setError(''); try { await onConfirm() } catch (e) { setError(apiErrorMessage(e, 'تعذّر الحذف')); setBusy(false) } }
  return (
    <Modal open onClose={onClose} title={title}>
      <div className="space-y-4">
        <p className="text-sm leading-7 text-ink-600">{warning}</p>
        {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={go} disabled={busy} className="btn bg-rose-600 text-white hover:bg-rose-700">{busy ? 'جارٍ الحذف…' : 'نعم، احذف'}</button>
        </div>
      </div>
    </Modal>
  )
}

/** Multi-item material uploader: a "+" adds rows so the teacher can upload several files at once. */
export function AddMaterials({ lessonId, onClose, onSaved }) {
  const empty = () => ({ type: 'VIDEO', title: '', description: '', url: '', file: null })
  const [rows, setRows] = useState([empty()])
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState('')
  const [uploadPercent, setUploadPercent] = useState(0)

  const update = (i, patch) => setRows((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)))
  const remove = (i) => setRows((rs) => rs.filter((_, idx) => idx !== i))

  const save = async () => {
    setSaving(true); setErr('')
    try {
      const pending = rows.filter(r => !r.saved)
      if (!pending.length || pending.some(r => !r.title.trim() || (!r.file && !r.url.trim() && !r.fileKey))) throw new Error('كل مادة محتاجة عنوان وملف أو رابط.')
      if (pending.some(r => r.url && !/^https?:\/\/[^\s]+$/i.test(r.url))) throw new Error('استخدم رابطاً صالحاً يبدأ بـ https:// أو http://')
      for (const r of rows) {
        if (r.saved) continue
        const rowIndex = rows.indexOf(r)
        let fileKey = r.fileKey || null
        if (r.file && !fileKey) {
          const fd = new FormData(); fd.append('file', r.file); fd.append('folder', 'materials')
          const up = await api.post('/files/upload', fd, { onUploadProgress: e => setUploadPercent(Math.round(e.loaded / (e.total || e.loaded) * 100)) })
          fileKey = up.data.fileKey
          update(rowIndex, { fileKey })
        }
        await api.post(`/courses/lessons/${lessonId}/materials`, {
          type: r.type, title: r.title.trim(), description: r.description, url: r.url || null, fileKey, sizeBytes: r.file?.size || null,
        })
        update(rowIndex, { saved: true })
      }
      onSaved()
    } catch (e) { setErr(apiErrorMessage(e, e.message || 'تعذّر الرفع')) } finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title="إضافة مواد (فيديو / ملفات / صور)" wide>
      <div className="space-y-4">
        {rows.map((r, i) => (
          <fieldset disabled={saving || r.saved} key={i} className="rounded-2xl border border-ink-100 p-4 disabled:opacity-70">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-bold text-ink-500">عنصر {i + 1} {r.saved ? '· تم الحفظ بنجاح' : ''}</span>
              {rows.length > 1 && <button onClick={() => remove(i)} className="text-rose-400 hover:text-rose-600"><X size={16} /></button>}
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="label">النوع</label>
                <select className="input" value={r.type} onChange={(e) => update(i, { type: e.target.value })}>
                  {Object.entries(MAT).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                </select>
              </div>
              <div><label className="label">العنوان</label><input className="input" value={r.title} onChange={(e) => update(i, { title: e.target.value })} placeholder="عنوان المادة" /></div>
            </div>
            <div className="mt-3"><label className="label">الوصف</label><input className="input" value={r.description} onChange={(e) => update(i, { description: e.target.value })} placeholder="وصف يظهر للطالب" /></div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <div><label className="label">رفع ملف</label><input type="file" className="input" onChange={(e) => update(i, { file: e.target.files[0] })} /></div>
              <div><label className="label">أو رابط خارجي / فيديو مشفر</label><input className="input" value={r.url} onChange={(e) => update(i, { url: e.target.value })} placeholder="https://..." />{r.type === "VIDEO" && <p className="mt-2 text-xs leading-6 text-ink-500">للفيديو المشفر: ارفع الفيديو إلى VdoCipher ثم أدخل https://player.vdocipher.com/v2/?video= متبوعاً بمعرّف الفيديو. يحتاج تفعيل المزود من الإدارة. الفيديو المحلي له حماية وصول فقط.</p>}</div>
            </div>
          </fieldset>
        ))}
        <button disabled={saving} onClick={() => setRows((rs) => [...rs, empty()])} className="btn-ghost w-full border border-dashed border-ink-300"><Plus size={16} /> إضافة عنصر آخر</button>
        {saving && <div role="status"><p className="mb-2 text-xs text-brand-600">جارٍ رفع وحفظ المواد · {uploadPercent}٪</p><div className="h-2 overflow-hidden rounded-full bg-brand-50"><div className="h-full bg-brand-500 transition-all" style={{ width: `${uploadPercent}%` }} /></div></div>}
        {err && <p className="rounded-2xl bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600">{err}</p>}
        <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving} className="btn-primary">{saving ? 'جارٍ الرفع...' : 'حفظ المواد'}</button></div>
      </div>
    </Modal>
  )
}

