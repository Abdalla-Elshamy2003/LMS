import { useState } from 'react'
import { Plus, Trash2, CheckCircle2, ImagePlus, X } from 'lucide-react'
import api, { fileUrl } from '../../lib/api'
import { Modal } from '../../components/ui'
import { apiErrorMessage } from '../../lib/apiError'

export const TYPES = [['MCQ', 'اختيار من متعدد'], ['TRUE_FALSE', 'صح / خطأ'], ['MULTI_SELECT', 'اختيار متعدد الإجابات'], ['FILL_BLANK', 'أكمل الفراغ'], ['SHORT_ANSWER', 'إجابة قصيرة'], ['NUMERIC', 'رقمي'], ['ESSAY', 'مقالي (تصحيح يدوي)']]
const withOptions = t => ['MCQ', 'TRUE_FALSE', 'MULTI_SELECT'].includes(t)

/** Create or edit one bank question with every supported type, answer key and student-facing explanation. */
export default function QuestionEditor({ question, onClose, onSaved }) {
  const [form, setForm] = useState(() => question ? { ...question, options: question.options?.map(o => ({ text: o.text, correct: !!o.correct })) || [], explanation: question.explanation || '', correctAnswer: question.correctAnswer || '', subject: question.subject || '', chapter: question.chapter || '', tags: question.tags || '', imageKey: question.imageKey || '' }
    : { type: 'MCQ', difficulty: 'MEDIUM', subject: '', chapter: '', stem: '', points: 1, correctAnswer: '', explanation: '', tags: '', imageKey: '', options: [{ text: '', correct: true }, { text: '', correct: false }, { text: '', correct: false }, { text: '', correct: false }] })
  const [saving, setSaving] = useState(false), [err, setErr] = useState('')
  const [uploading, setUploading] = useState(false)
  const uploadImage = async file => {
    if (!file) return
    setUploading(true); setErr('')
    try {
      const fd = new FormData(); fd.append('file', file); fd.append('folder', 'exams')
      const r = await api.post('/files/upload', fd)
      setForm(f => ({ ...f, imageKey: r.data.fileKey }))
    } catch (e) { setErr(apiErrorMessage(e, 'تعذّر رفع الصورة')) } finally { setUploading(false) }
  }
  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }))
  const setType = t => setForm(f => ({ ...f, type: t, options: t === 'TRUE_FALSE' ? [{ text: 'صح', correct: true }, { text: 'خطأ', correct: false }] : withOptions(t) ? (f.options.length >= 2 && f.type !== 'TRUE_FALSE' ? f.options : [{ text: '', correct: true }, { text: '', correct: false }, { text: '', correct: false }, { text: '', correct: false }]) : [] }))
  const option = (i, patch) => setForm(f => ({ ...f, options: f.options.map((o, idx) => idx === i ? { ...o, ...patch } : (patch.correct && f.type !== 'MULTI_SELECT' ? { ...o, correct: false } : o)) }))
  const save = async () => {
    if (!form.stem.trim()) { setErr('اكتب نص السؤال'); return }
    if (withOptions(form.type) && (form.options.some(o => !o.text.trim()) || !form.options.some(o => o.correct))) { setErr('أكمل نصوص الاختيارات وحدّد الإجابة الصحيحة'); return }
    if (!withOptions(form.type) && form.type !== 'ESSAY' && !form.correctAnswer.trim()) { setErr('اكتب الإجابة النموذجية'); return }
    setSaving(true); setErr('')
    const body = { type: form.type, difficulty: form.difficulty, subject: form.subject || null, chapter: form.chapter || null, stem: form.stem.trim(), points: Number(form.points) || 1, correctAnswer: withOptions(form.type) ? null : form.correctAnswer, explanation: form.explanation || null, tags: form.tags || null, imageKey: form.imageKey || null, options: withOptions(form.type) ? form.options.map((o, i) => ({ text: o.text.trim(), correct: o.correct, position: i })) : null }
    try { const r = question ? await api.put(`/exams/questions/${question.id}`, body) : await api.post('/exams/questions', body); onSaved(r.data) }
    catch (e) { setErr(apiErrorMessage(e, 'تعذّر حفظ السؤال')) } finally { setSaving(false) }
  }
  return <Modal open onClose={onClose} title={question ? 'تعديل سؤال' : 'سؤال جديد في البنك'} wide>
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="sm:col-span-2"><label className="label">نوع السؤال</label><div className="flex flex-wrap gap-1.5">{TYPES.map(([k, v]) => <button key={k} type="button" onClick={() => setType(k)} className={`chip border ${form.type === k ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>{v}</button>)}</div></div>
        <div><label className="label">الصعوبة</label><select className="input" value={form.difficulty} onChange={set('difficulty')}><option value="EASY">سهل</option><option value="MEDIUM">متوسط</option><option value="HARD">صعب</option></select></div>
      </div>
      <div><label className="label">نص السؤال</label><textarea dir="auto" className="input text-base leading-8" rows={3} value={form.stem} onChange={set('stem')} placeholder="اكتب السؤال كما سيراه الطالب" /></div>
      <div>
        <label className="label">صورة السؤال (اختياري) · رسم بياني، شكل هندسي، قطعة مصورة</label>
        {form.imageKey ? <div className="relative inline-block">
          <img src={fileUrl(form.imageKey)} alt="صورة السؤال" className="max-h-56 rounded-xl border border-ink-200" />
          <button type="button" aria-label="إزالة الصورة" onClick={() => setForm(f => ({ ...f, imageKey: '' }))}
            className="absolute left-2 top-2 rounded-lg bg-white/90 p-1.5 text-ink-500 shadow hover:text-rose-600"><X size={15} /></button>
        </div> : <label className="flex cursor-pointer items-center justify-center gap-2 rounded-xl border border-dashed border-ink-300 p-5 text-sm text-ink-500 hover:bg-ink-50">
          <ImagePlus size={18} /> {uploading ? 'جارٍ رفع الصورة…' : 'اختر صورة (PNG / JPG / WEBP)'}
          <input type="file" className="hidden" accept=".png,.jpg,.jpeg,.webp" disabled={uploading}
            onChange={e => uploadImage(e.target.files?.[0])} />
        </label>}
      </div>
      {withOptions(form.type) ? <div><label className="label">الاختيارات · {form.type === 'MULTI_SELECT' ? 'علّم كل الإجابات الصحيحة' : 'علّم الإجابة الصحيحة'}</label>
        <div className="space-y-2">{form.options.map((o, i) => <div key={i} className={`flex items-center gap-2 rounded-xl border p-2 ${o.correct ? 'border-emerald-300 bg-emerald-50' : 'border-ink-200'}`}>
          <input type={form.type === 'MULTI_SELECT' ? 'checkbox' : 'radio'} name="correct" checked={o.correct} onChange={e => option(i, { correct: form.type === 'MULTI_SELECT' ? e.target.checked : true })} aria-label={`الاختيار ${i + 1} صحيح`} />
          <input dir="auto" className="input py-1.5" value={o.text} onChange={e => option(i, { text: e.target.value })} placeholder={`الاختيار ${i + 1}`} disabled={form.type === 'TRUE_FALSE'} />
          {o.correct && <CheckCircle2 size={17} className="shrink-0 text-emerald-600" />}
          {form.type !== 'TRUE_FALSE' && <button type="button" aria-label="حذف الاختيار" disabled={form.options.length <= 2} onClick={() => setForm(f => ({ ...f, options: f.options.filter((_, idx) => idx !== i) }))} className="text-ink-300 hover:text-rose-500 disabled:opacity-30"><Trash2 size={16} /></button>}
        </div>)}</div>
        {form.type !== 'TRUE_FALSE' && form.options.length < 10 && <button type="button" className="btn-ghost mt-2" onClick={() => setForm(f => ({ ...f, options: [...f.options, { text: '', correct: false }] }))}><Plus size={15} /> اختيار</button>}
      </div> : form.type !== 'ESSAY' && <div><label className="label">الإجابة النموذجية {form.type !== 'NUMERIC' && <span className="font-normal text-ink-400">· افصل البدائل المقبولة بعلامة |</span>}</label><input dir="auto" className="input" value={form.correctAnswer} onChange={set('correctAnswer')} placeholder={form.type === 'NUMERIC' ? '12.5' : 'مثال: القاهرة|Cairo'} /><p className="mt-1 text-xs text-ink-400">التصحيح يتجاهل حالة الأحرف والمسافات الزائدة والتشكيل والأرقام العربية/الهندية.</p></div>}
      <div className="grid gap-3 sm:grid-cols-4">
        <div><label className="label">الدرجة</label><input type="number" min="0.5" step="0.5" className="input" value={form.points} onChange={set('points')} /></div>
        <div><label className="label">المادة</label><input className="input" value={form.subject} onChange={set('subject')} placeholder="رياضيات" /></div>
        <div><label className="label">الفصل / الوحدة</label><input className="input" value={form.chapter} onChange={set('chapter')} placeholder="الوحدة 3" /></div>
        <div><label className="label">وسوم</label><input className="input" value={form.tags} onChange={set('tags')} placeholder="تفاضل، نهائي" /></div>
      </div>
      <div><label className="label">شرح الإجابة (يظهر للطالب في المراجعة)</label><textarea dir="auto" className="input" rows={2} value={form.explanation} onChange={set('explanation')} placeholder="لماذا هذه هي الإجابة الصحيحة؟" /></div>
      {question?.usedInExams > 0 && <p className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">هذا السؤال مستخدم في {question.usedInExams} امتحان. التعديل مرفوض إذا كان أحدها قد بدأ فيه طلاب.</p>}
      {err && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{err}</p>}
      <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving} className="btn-primary">{saving ? 'جارٍ الحفظ...' : question ? 'حفظ التعديل' : 'إضافة للبنك'}</button></div>
    </div>
  </Modal>
}
