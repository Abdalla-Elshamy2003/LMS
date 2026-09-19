import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, XCircle, Lightbulb, Plus, Trash2, Search, Clock, ListChecks, PlayCircle } from 'lucide-react'
import api, { fileUrl } from '../../lib/api'
import { Modal, PageLoader } from '../../components/ui'
import { apiErrorMessage } from '../../lib/apiError'

const withOptions = t => ['MCQ', 'TRUE_FALSE', 'MULTI_SELECT'].includes(t)

export const clock = s => {
  if (s == null) return 'نهاية الدرس'
  const m = Math.floor(s / 60), r = Math.floor(s % 60)
  return `${m}:${String(r).padStart(2, '0')}`
}

/** Parses "3:20" or "200" into seconds; returns null for an empty value (= end-of-lesson test). */
const parseClock = v => {
  const t = (v || '').trim()
  if (!t) return null
  if (!t.includes(':')) return Math.max(0, Math.floor(Number(t) || 0))
  const [m, s] = t.split(':')
  return Math.max(0, (Number(m) || 0) * 60 + (Number(s) || 0))
}

/**
 * Student side. Watches playback and interrupts it at each checkpoint: the video pauses, the question
 * is asked, and playback only resumes once it is answered. Checkpoints with no timestamp run as a
 * short end-of-lesson test after the video finishes.
 */
export function LessonQuizOverlay({ lessonId, videoRef, endedAt }) {
  const [checkpoints, setCheckpoints] = useState([])
  const [active, setActive] = useState(null)
  const [queue, setQueue] = useState([])
  const [picked, setPicked] = useState([])
  const [text, setText] = useState('')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const shown = useRef(new Set())

  useEffect(() => {
    shown.current = new Set()
    setActive(null); setQueue([]); setResult(null)
    if (!lessonId) { setCheckpoints([]); return }
    api.get(`/courses/lessons/${lessonId}/quiz`).then(r => setCheckpoints(r.data)).catch(() => setCheckpoints([]))
  }, [lessonId])

  // Timed checkpoints: poll the element rather than relying on onTimeUpdate alone, so a seek past a
  // checkpoint still triggers it.
  useEffect(() => {
    if (!checkpoints.length) return
    const timed = checkpoints.filter(c => c.atSeconds != null)
    if (!timed.length) return
    const tick = setInterval(() => {
      const v = videoRef?.current
      if (!v || v.paused || active) return
      const due = timed.find(c => !shown.current.has(c.id) && v.currentTime >= c.atSeconds)
      if (due) { shown.current.add(due.id); v.pause(); openOne(due) }
    }, 500)
    return () => clearInterval(tick)
  }, [checkpoints, active, videoRef])

  // End-of-lesson test.
  useEffect(() => {
    if (!endedAt || active) return
    const final = checkpoints.filter(c => c.atSeconds == null && !shown.current.has(c.id))
    if (!final.length) return
    final.forEach(c => shown.current.add(c.id))
    openOne(final[0])
    setQueue(final.slice(1))
  }, [endedAt, checkpoints])

  const openOne = c => { setActive(c); setPicked([]); setText(''); setResult(null) }

  const submit = async () => {
    setBusy(true)
    try {
      const r = await api.post(`/courses/checkpoints/${active.id}/answer`,
        { selectedOptions: withOptions(active.type) ? picked : null, answerText: withOptions(active.type) ? null : text })
      setResult(r.data)
    } catch (e) { setResult({ correct: false, correctAnswer: apiErrorMessage(e, 'تعذّر إرسال الإجابة') }) }
    finally { setBusy(false) }
  }

  const next = () => {
    if (queue.length) { const [head, ...rest] = queue; setQueue(rest); openOne(head); return }
    setActive(null); setResult(null)
    videoRef?.current?.play?.().catch(() => {})
  }

  if (!active) return null
  const canSubmit = withOptions(active.type) ? picked.length > 0 : text.trim().length > 0
  return <Modal open onClose={() => {}} title={active.atSeconds == null ? 'اختبار نهاية الدرس' : `سؤال عند ${clock(active.atSeconds)}`}>
    <div className="space-y-4">
      <p className="whitespace-pre-wrap text-base font-bold leading-8">{active.stem}</p>
      {active.imageKey && <img src={fileUrl(active.imageKey)} alt="صورة السؤال" className="max-h-72 w-auto max-w-full rounded-xl border border-ink-200" />}

      {withOptions(active.type) ? <div className="space-y-2">{active.options.map(o => {
        const on = picked.includes(o.id)
        return <button key={o.id} type="button" disabled={!!result}
          onClick={() => setPicked(active.type === 'MULTI_SELECT' ? (on ? picked.filter(x => x !== o.id) : [...picked, o.id]) : [o.id])}
          className={`flex w-full items-center gap-3 rounded-xl border p-3 text-right ${on ? 'border-brand-400 bg-brand-50' : 'border-ink-200 hover:bg-ink-50'}`}>
          <span className={`grid h-5 w-5 shrink-0 place-items-center rounded-full border ${on ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-300'}`}>{on && <CheckCircle2 size={13} />}</span>
          <span className="text-sm leading-7">{o.text}</span>
        </button>
      })}</div>
        : <input dir="auto" className="input" value={text} disabled={!!result} onChange={e => setText(e.target.value)} placeholder="اكتب إجابتك" />}

      {result && <div className={`rounded-xl p-3 text-sm leading-7 ${result.correct ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>
        <p className="flex items-center gap-2 font-bold">{result.correct ? <><CheckCircle2 size={17} /> إجابة صحيحة</> : <><XCircle size={17} /> إجابة غير صحيحة</>}</p>
        {!result.correct && result.correctAnswer && <p className="mt-1">الإجابة الصحيحة: {result.correctAnswer}</p>}
        {result.explanation && <p className="mt-1 flex gap-2"><Lightbulb size={16} className="mt-1 shrink-0" />{result.explanation}</p>}
      </div>}

      <div className="flex justify-end gap-2">
        {result ? <button className="btn-primary" onClick={next}>{queue.length ? 'السؤال التالي' : <><PlayCircle size={16} /> متابعة الدرس</>}</button>
          : <button className="btn-primary" disabled={!canSubmit || busy} onClick={submit}>{busy ? 'جارٍ الإرسال…' : 'تأكيد الإجابة'}</button>}
      </div>
    </div>
  </Modal>
}

/** Teacher side: pin bank questions to moments in the video, or to the end-of-lesson test. */
export function LessonQuizManager({ lessonId, videoRef }) {
  const [rows, setRows] = useState(null)
  const [picker, setPicker] = useState(false)
  const [notice, setNotice] = useState('')

  const load = () => api.get(`/courses/lessons/${lessonId}/checkpoints`).then(r => setRows(r.data)).catch(() => setRows([]))
  useEffect(() => { if (lessonId) load() }, [lessonId])

  const remove = async id => {
    try { await api.delete(`/courses/checkpoints/${id}`); load() }
    catch (e) { setNotice(apiErrorMessage(e, 'تعذّر الحذف')) }
  }

  if (!lessonId) return null
  return <div className="card p-5">
    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
      <h3 className="inline-flex items-center gap-2 font-black"><ListChecks size={18} className="text-brand-600" /> أسئلة أثناء الفيديو</h3>
      <button className="btn-primary" onClick={() => setPicker(true)}><Plus size={16} /> أضف سؤالاً</button>
    </div>
    <p className="mb-4 text-xs leading-6 text-ink-500">الفيديو يقف عند التوقيت المحدد ويسأل الطالب، ولا يكمل إلا بعد ما يجاوب. الأسئلة بدون توقيت بتتجمّع في اختبار قصير آخر الدرس.</p>
    {notice && <p role="status" className="mb-3 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{notice}</p>}
    {!rows ? <PageLoader /> : rows.length === 0
      ? <p className="rounded-xl border border-dashed border-ink-200 p-6 text-center text-sm text-ink-400">لا توجد أسئلة على هذا الدرس بعد.</p>
      : <div className="space-y-2">{rows.map(c => <div key={c.id} className="flex items-start gap-3 rounded-xl border border-ink-100 p-3">
        <span className="chip shrink-0 bg-brand-50 text-brand-700"><Clock size={13} /> {clock(c.atSeconds)}</span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold leading-7">{c.stem}</p>
          <p className="mt-0.5 text-[11px] text-ink-400">{c.answered} إجابة · {c.correctCount} صحيحة</p>
        </div>
        <button aria-label="حذف" onClick={() => remove(c.id)} className="rounded-lg p-2 text-ink-400 hover:bg-rose-50 hover:text-rose-600"><Trash2 size={15} /></button>
      </div>)}</div>}
    {picker && <CheckpointPicker lessonId={lessonId} videoRef={videoRef} onClose={() => setPicker(false)}
      onSaved={() => { setPicker(false); load() }} />}
  </div>
}

function CheckpointPicker({ lessonId, videoRef, onClose, onSaved }) {
  const [q, setQ] = useState(''), [data, setData] = useState(null)
  const [selected, setSelected] = useState(null)
  const [at, setAt] = useState('')
  const [saving, setSaving] = useState(false), [err, setErr] = useState('')

  useEffect(() => {
    const t = setTimeout(() => api.get('/exams/questions', { params: { q, page: 0, size: 10 } })
      .then(r => setData(r.data)).catch(() => setData({ content: [] })), 250)
    return () => clearTimeout(t)
  }, [q])

  const save = async () => {
    if (!selected) { setErr('اختر سؤالاً أولاً'); return }
    setSaving(true); setErr('')
    try {
      await api.post(`/courses/lessons/${lessonId}/checkpoints`, { questionId: selected.id, atSeconds: parseClock(at) })
      onSaved()
    } catch (e) { setErr(apiErrorMessage(e, 'تعذّر إضافة السؤال')) } finally { setSaving(false) }
  }

  return <Modal open onClose={onClose} title="أضف سؤالاً على الفيديو" wide>
    <div className="space-y-4">
      <label className="relative block"><Search size={15} className="absolute right-3 top-3 text-ink-400" />
        <input className="input pr-9" value={q} onChange={e => setQ(e.target.value)} placeholder="ابحث في بنك الأسئلة..." /></label>
      <div className="max-h-64 space-y-2 overflow-y-auto">
        {!data ? <PageLoader /> : data.content.length === 0
          ? <p className="p-4 text-center text-xs text-ink-400">لا توجد أسئلة — أضف أسئلة للبنك من صفحة الامتحانات أولاً.</p>
          : data.content.map(qn => <button key={qn.id} type="button" onClick={() => setSelected(qn)}
            className={`flex w-full items-center gap-2 rounded-xl border p-2.5 text-right ${selected?.id === qn.id ? 'border-brand-400 bg-brand-50' : 'border-ink-100 hover:bg-ink-50'}`}>
            <span className="min-w-0 flex-1"><span className="block truncate text-sm font-semibold">{qn.stem}</span>
              <span className="text-[11px] text-ink-400">{qn.subject || '—'} · {qn.points} درجة</span></span>
            {selected?.id === qn.id && <CheckCircle2 size={16} className="shrink-0 text-brand-600" />}
          </button>)}
      </div>
      <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
        <div>
          <label className="label">توقيت السؤال (د:ث) — اتركه فارغاً ليكون ضمن اختبار نهاية الدرس</label>
          <input className="input" value={at} onChange={e => setAt(e.target.value)} placeholder="مثال: 3:20" />
        </div>
        <button type="button" className="btn-soft self-end"
          onClick={() => { const v = videoRef?.current; if (v) setAt(clock(Math.floor(v.currentTime))) }}>
          <Clock size={15} /> التوقيت الحالي
        </button>
      </div>
      {err && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{err}</p>}
      <div className="flex justify-end gap-2">
        <button className="btn-ghost" onClick={onClose}>إلغاء</button>
        <button className="btn-primary" disabled={saving} onClick={save}>{saving ? 'جارٍ الحفظ…' : 'إضافة'}</button>
      </div>
    </div>
  </Modal>
}
