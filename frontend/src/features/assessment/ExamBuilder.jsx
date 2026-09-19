import { useEffect, useState } from 'react'
import { Wand2, Database, FileUp, Plus, CheckCircle2, Trash2, ArrowUp, ArrowDown, Search, ShieldCheck, CalendarClock, Eye, Info } from 'lucide-react'
import api, { fileUrl } from '../../lib/api'
import { Modal, PageLoader, Spinner } from '../../components/ui'
import { toLocalInput } from '../../lib/csv'
import { TYPES } from './QuestionEditor'
import { apiErrorMessage } from '../../lib/apiError'

const DIFF = { EASY: 'سهل', MEDIUM: 'متوسط', HARD: 'صعب' }
const STEPS = [['details', 'التفاصيل'], ['content', 'الأسئلة'], ['settings', 'الحماية والمواعيد'], ['review', 'مراجعة ونشر']]
const iso = v => v ? new Date(v).toISOString() : null

/**
 * Four-step exam wizard (details → content → security & schedule & review policy → publish).
 * Works for a new exam or an existing one (`existing` = ExamDetail); the server freezes attempt-affecting
 * settings once students started, and the UI mirrors that.
 */
export default function ExamBuilder({ courses, existing, onClose, onSaved }) {
  const [step, setStep] = useState('details')
  const [mode, setMode] = useState(existing ? (existing.summary.pdfKey ? 'pdf' : 'manual') : 'auto')
  const [form, setForm] = useState(() => existing ? { title: existing.summary.title, courseId: String(existing.summary.courseId || ''), description: existing.description || '', durationMinutes: existing.summary.durationMinutes, passPercent: existing.summary.passPercent, shuffleQuestions: existing.shuffleQuestions, shuffleOptions: existing.shuffleOptions, fullscreen: existing.fullscreen, disableCopy: existing.disableCopy, detectTabSwitch: existing.detectTabSwitch, startAt: toLocalInput(existing.startAt), endAt: toLocalInput(existing.endAt), showResults: existing.showResults || 'AFTER_SUBMIT', showCorrectAnswers: existing.showCorrectAnswers !== false, subject: '', easy: 3, medium: 4, hard: 2 }
    : { title: '', courseId: courses.length === 1 ? String(courses[0].id) : '', description: '', durationMinutes: 45, passPercent: 50, shuffleQuestions: true, shuffleOptions: true, fullscreen: false, disableCopy: true, detectTabSwitch: true, startAt: '', endAt: '', showResults: 'AFTER_SUBMIT', showCorrectAnswers: true, subject: '', easy: 3, medium: 4, hard: 2 })
  const [selected, setSelected] = useState(() => existing ? existing.questions.map((q, i) => ({ ...q, pointsOverride: existing.pointsOverrides?.[i] ?? null })) : [])
  const [pdfFile, setPdfFile] = useState(null), [uploadedPdf, setUploadedPdf] = useState(existing?.summary.pdfKey || null)
  const [examId, setExamId] = useState(existing?.summary.id || null), [detail, setDetail] = useState(existing || null)
  const [saving, setSaving] = useState(false), [err, setErr] = useState('')
  const frozen = !!existing && existing.summary.attempts > 0
  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }))
  const toggle = k => setForm(f => ({ ...f, [k]: !f[k] }))
  const total = selected.reduce((n, q) => n + (q.pointsOverride ?? q.points), 0)

  const validateDetails = () => {
    if (!form.courseId || !form.title.trim()) return 'اختر الكورس واكتب عنواناً للامتحان'
    if (Number(form.durationMinutes) < 1 || Number(form.durationMinutes) > 600) return 'المدة من 1 إلى 600 دقيقة'
    if (Number(form.passPercent) < 0 || Number(form.passPercent) > 100) return 'نسبة النجاح من 0 إلى 100'
    return ''
  }
  const validateContent = () => {
    if (mode === 'auto' && Number(form.easy) + Number(form.medium) + Number(form.hard) < 1) return 'اختر سؤالاً واحداً على الأقل للتوليد'
    if (mode === 'manual' && !selected.length) return 'أضف سؤالاً واحداً على الأقل من البنك'
    if (mode === 'pdf' && !pdfFile && !uploadedPdf) return 'اختر ملف الامتحان'
    if (form.startAt && form.endAt && new Date(form.endAt) <= new Date(form.startAt)) return 'موعد الإغلاق يجب أن يأتي بعد الفتح'
    return ''
  }
  const next = async () => {
    setErr('')
    const i = STEPS.findIndex(([k]) => k === step)
    if (step === 'details') { const e = validateDetails(); if (e) { setErr(e); return } }
    if (step === 'content') { const e = validateContent(); if (e) { setErr(e); return } }
    if (step === 'settings') { const e = validateContent(); if (e) { setErr(e); return } await persist(); return }
    setStep(STEPS[i + 1][0])
  }
  const base = () => ({ courseId: Number(form.courseId), title: form.title.trim(), description: form.description || null, durationMinutes: Number(form.durationMinutes), passPercent: Number(form.passPercent), shuffleQuestions: form.shuffleQuestions, shuffleOptions: form.shuffleOptions, detectTabSwitch: form.detectTabSwitch, fullscreen: form.fullscreen, disableCopy: form.disableCopy, startAt: iso(form.startAt), endAt: iso(form.endAt), showResults: form.showResults, showCorrectAnswers: form.showCorrectAnswers })

  /** Creates (or updates) the exam on the server, then shows the authoritative detail for review. */
  const persist = async () => {
    setSaving(true); setErr('')
    try {
      let id = examId
      if (existing) {
        const b = base(); const patch = frozen ? { title: b.title, description: b.description, passPercent: b.passPercent, endAt: b.endAt, showResults: b.showResults, showCorrectAnswers: b.showCorrectAnswers, clearSchedule: !form.startAt && !form.endAt } : { ...b, clearSchedule: !form.startAt && !form.endAt }
        delete patch.courseId
        await api.put(`/exams/${id}`, patch)
        if (!frozen && mode === 'manual') await syncQuestions(id)
      } else if (mode === 'auto') {
        const r = await api.post('/exams/auto-generate', { ...base(), subject: form.subject || null, easy: Number(form.easy), medium: Number(form.medium), hard: Number(form.hard) })
        id = r.data.summary.id
      } else if (mode === 'pdf') {
        let key = uploadedPdf
        if (!key) { const fd = new FormData(); fd.append('file', pdfFile); fd.append('folder', 'exams'); key = (await api.post('/files/upload', fd)).data.fileKey; setUploadedPdf(key) }
        if (!id) { const r = await api.post('/exams', { ...base(), pdfKey: key }); id = r.data.summary.id }
      } else {
        if (!id) { const r = await api.post('/exams', base()); id = r.data.summary.id }
        await syncQuestions(id)
      }
      setExamId(id)
      const { data } = await api.get(`/exams/${id}`); setDetail(data); setStep('review')
    } catch (e) { setErr(apiErrorMessage(e, 'تعذّر حفظ الامتحان')) } finally { setSaving(false) }
  }
  const syncQuestions = async id => {
    const { data } = await api.get(`/exams/${id}`)
    const current = data.questions.map((q, i) => ({ id: q.id, pointsOverride: data.pointsOverrides?.[i] ?? null }))
    for (const c of current) if (!selected.some(s => s.id === c.id)) await api.delete(`/exams/${id}/questions/${c.id}`)
    for (const s of selected) {
      const c = current.find(x => x.id === s.id)
      if (!c) await api.post(`/exams/${id}/questions`, { questionId: s.id, pointsOverride: s.pointsOverride ?? null })
      else if ((c.pointsOverride ?? null) !== (s.pointsOverride ?? null)) await api.patch(`/exams/${id}/questions/${s.id}`, { pointsOverride: s.pointsOverride ?? null })
    }
    await api.put(`/exams/${id}/questions/order`, { questionIds: selected.map(s => s.id) })
  }
  const publish = async () => { setSaving(true); setErr(''); try { await api.post(`/exams/${examId}/publish`); onSaved() } catch (e) { setErr(apiErrorMessage(e, 'تعذّر النشر؛ المسودة محفوظة ويمكنك المحاولة مرة أخرى')) } finally { setSaving(false) } }

  return <Modal open onClose={onClose} title={existing ? `تعديل · ${existing.summary.title}` : 'امتحان جديد'} size="lg">
    <ol className="mb-5 grid grid-cols-4 gap-1 text-center text-[11px] font-bold sm:text-xs">{STEPS.map(([k, v], i) => { const idx = STEPS.findIndex(([s]) => s === step); return <li key={k} className={`rounded-xl py-2 ${i === idx ? 'bg-brand-600 text-white' : i < idx ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-400'}`}>{i + 1}. {v}</li> })}</ol>
    {frozen && <p className="mb-4 flex gap-2 rounded-xl bg-amber-50 p-3 text-xs leading-6 text-amber-800"><Info size={16} className="mt-1 shrink-0" /> بدأ {existing.summary.attempts} طالب هذا الامتحان. يمكنك تعديل العنوان والتعليمات ونسبة النجاح وموعد الإغلاق وسياسة عرض النتائج فقط؛ الأسئلة والمدة وإعدادات الحماية مجمّدة.</p>}

    {step === 'details' && <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <div><label className="label">عنوان الامتحان</label><input dir="auto" className="input" value={form.title} onChange={set('title')} placeholder="امتحان الوحدة الثالثة" /></div>
        <div><label className="label">الكورس</label><select className="input" value={form.courseId} onChange={set('courseId')} disabled={!!existing}><option value="">— اختر —</option>{courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}</select></div>
      </div>
      <div><label className="label">تعليمات للطالب (تظهر قبل البدء)</label><textarea dir="auto" className="input" rows={3} value={form.description} onChange={set('description')} placeholder="مثال: الامتحان 20 سؤالاً، الآلة الحاسبة غير مسموحة، أجب عن كل الأسئلة." /></div>
      <div className="grid grid-cols-2 gap-3">
        <div><label className="label">المدة (دقيقة)</label><input type="number" min="1" max="600" className="input" value={form.durationMinutes} onChange={set('durationMinutes')} disabled={frozen} /></div>
        <div><label className="label">نسبة النجاح (٪)</label><input type="number" min="0" max="100" className="input" value={form.passPercent} onChange={set('passPercent')} /></div>
      </div>
    </div>}

    {step === 'content' && <div className="space-y-4">
      {!existing && <div className="grid grid-cols-3 gap-2">{[['auto', 'توليد من البنك', Wand2, 'اختر مزيج الصعوبة والمنصة تختار عشوائياً'], ['manual', 'اختيار يدوي', Database, 'ابحث في البنك، رتّب الأسئلة، وعدّل درجاتها'], ['pdf', 'ورقة PDF', FileUp, 'ارفع ورقة الامتحان ليحمّلها الطالب']].map(([m, l, Icon, hint]) => <button key={m} type="button" onClick={() => setMode(m)} className={`rounded-2xl border p-3 text-right transition ${mode === m ? 'border-brand-400 bg-brand-50' : 'border-ink-200 hover:bg-ink-50'}`}><Icon size={18} className="text-brand-700" /><b className="mt-2 block text-sm">{l}</b><span className="text-[11px] leading-5 text-ink-500">{hint}</span></button>)}</div>}
      {mode === 'auto' && <div className="rounded-2xl bg-ink-50 p-4"><div className="grid gap-3 sm:grid-cols-4"><div className="sm:col-span-1"><label className="label">المادة (اختياري)</label><input className="input" value={form.subject} onChange={set('subject')} placeholder="كل المواد" /></div>{[['easy', 'سهل'], ['medium', 'متوسط'], ['hard', 'صعب']].map(([k, l]) => <div key={k}><label className="label">{l}</label><input type="number" min="0" className="input" value={form[k]} onChange={set(k)} /></div>)}</div><p className="mt-3 text-xs text-ink-500">إجمالي {Number(form.easy) + Number(form.medium) + Number(form.hard)} سؤال. لو البنك لا يحتوي العدد المطلوب لأي مستوى ستظهر رسالة توضّح المتاح.</p></div>}
      {mode === 'pdf' && <div><label className="label">ملف الامتحان (PDF / صورة / Word)</label><input type="file" className="input" accept=".pdf,.png,.jpg,.jpeg,.webp,.doc,.docx" onChange={e => { setPdfFile(e.target.files[0]); setUploadedPdf(null) }} />{uploadedPdf && !pdfFile && <a className="btn-soft mt-2" href={fileUrl(uploadedPdf)} target="_blank" rel="noreferrer">الملف الحالي</a>}<p className="mt-2 text-xs text-ink-500">الامتحان الورقي يُحمَّل من بطاقة الامتحان ولا يُصحَّح آلياً.</p></div>}
      {mode === 'manual' && <ManualContent selected={selected} setSelected={setSelected} total={total} frozen={frozen} />}
    </div>}

    {step === 'settings' && <div className="space-y-5">
      <section><h4 className="mb-2 inline-flex items-center gap-1.5 text-sm font-black"><ShieldCheck size={16} className="text-brand-600" /> حماية المحاولة</h4>
        <div className="grid gap-2 sm:grid-cols-2">{[['shuffleQuestions', 'ترتيب عشوائي للأسئلة', 'كل طالب يرى ترتيباً مختلفاً'], ['shuffleOptions', 'ترتيب عشوائي للاختيارات', 'لا ينطبق على صح/خطأ'], ['fullscreen', 'إلزام ملء الشاشة', 'الخروج منه يُسجَّل في تقرير النزاهة'], ['disableCopy', 'تعطيل النسخ واللصق', 'داخل قاعة الامتحان فقط'], ['detectTabSwitch', 'رصد مغادرة الصفحة', 'التبديل لنافذة أخرى يُسجَّل بالوقت']].map(([k, l, h]) => <label key={k} className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 ${form[k] ? 'border-brand-300 bg-brand-50' : 'border-ink-200'} ${frozen ? 'opacity-60' : ''}`}><input type="checkbox" className="mt-1" checked={form[k]} onChange={() => toggle(k)} disabled={frozen} /><span><b className="block text-sm">{l}</b><span className="text-[11px] text-ink-500">{h}</span></span></label>)}</div>
        <p className="mt-2 text-xs text-ink-400">هذه الإجراءات تردع وتوثّق، ولا تمنع الغش تقنياً بشكل مطلق؛ تقرير النزاهة مؤشر للمدرس وليس دليلاً قاطعاً.</p></section>
      <section><h4 className="mb-2 inline-flex items-center gap-1.5 text-sm font-black"><CalendarClock size={16} className="text-brand-600" /> نافذة الامتحان (اختياري)</h4>
        <div className="grid gap-3 sm:grid-cols-2"><div><label className="label">يفتح في</label><input type="datetime-local" className="input" value={form.startAt} onChange={set('startAt')} disabled={frozen} /></div><div><label className="label">يغلق في</label><input type="datetime-local" className="input" value={form.endAt} onChange={set('endAt')} /></div></div>
        <p className="mt-2 text-xs text-ink-500">بتوقيت جهازك. بدون مواعيد يكون الامتحان متاحاً فور النشر حتى تغلقه يدوياً. وقت الطالب ينتهي عند الأقرب: مدة الامتحان أو موعد الإغلاق.</p></section>
      <section><h4 className="mb-2 inline-flex items-center gap-1.5 text-sm font-black"><Eye size={16} className="text-brand-600" /> ماذا يرى الطالب بعد التسليم؟</h4>
        <div className="grid gap-2 sm:grid-cols-3">{[['AFTER_SUBMIT', 'فور التسليم', 'الدرجة ومراجعة الإجابات مباشرة'], ['AFTER_CLOSE', 'بعد إغلاق الامتحان', 'يمنع تسريب الأسئلة بين الطلاب'], ['NEVER', 'الدرجة فقط', 'بدون مراجعة الأسئلة']].map(([k, l, h]) => <label key={k} className={`flex cursor-pointer items-start gap-2 rounded-xl border p-3 ${form.showResults === k ? 'border-brand-300 bg-brand-50' : 'border-ink-200'}`}><input type="radio" className="mt-1" name="showResults" checked={form.showResults === k} onChange={() => setForm(f => ({ ...f, showResults: k }))} /><span><b className="block text-sm">{l}</b><span className="text-[11px] text-ink-500">{h}</span></span></label>)}</div>
        <label className={`mt-2 flex items-center gap-2 text-sm ${form.showResults === 'NEVER' ? 'opacity-50' : ''}`}><input type="checkbox" checked={form.showCorrectAnswers} onChange={() => toggle('showCorrectAnswers')} disabled={form.showResults === 'NEVER'} /> إظهار الإجابات الصحيحة والشرح في المراجعة</label></section>
    </div>}

    {step === 'review' && (!detail ? <PageLoader /> : <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2"><span className={`chip ${detail.summary.status === 'PUBLISHED' ? 'bg-emerald-50 text-emerald-700' : 'bg-brand-50 text-brand-700'}`}>{detail.summary.status === 'PUBLISHED' ? 'منشور' : detail.summary.status === 'CLOSED' ? 'مغلق' : 'مسودة محفوظة · لم تُنشر بعد'}</span><span className="chip bg-ink-100 text-ink-600">{detail.summary.durationMinutes} دقيقة</span><span className="chip bg-ink-100 text-ink-600">{detail.summary.questionCount} سؤال</span><span className="chip bg-ink-100 text-ink-600">{detail.summary.totalPoints} درجة</span><span className="chip bg-ink-100 text-ink-600">نجاح {detail.summary.passPercent}٪</span></div>
      <h3 className="text-xl font-black">{detail.summary.title}</h3>
      <div className="flex flex-wrap gap-2 text-xs">{detail.fullscreen && <span className="chip bg-amber-50 text-amber-700">ملء الشاشة</span>}{detail.disableCopy && <span className="chip bg-amber-50 text-amber-700">منع النسخ</span>}{detail.detectTabSwitch && <span className="chip bg-amber-50 text-amber-700">رصد المغادرة</span>}{detail.shuffleQuestions && <span className="chip bg-ink-100 text-ink-600">ترتيب عشوائي</span>}<span className="chip bg-ink-100 text-ink-600">النتائج: {{ AFTER_SUBMIT: 'فور التسليم', AFTER_CLOSE: 'بعد الإغلاق', NEVER: 'الدرجة فقط' }[detail.showResults]}</span>{detail.startAt && <span className="chip bg-sky-50 text-sky-700">يفتح {new Date(detail.startAt).toLocaleString('ar-EG')}</span>}{detail.endAt && <span className="chip bg-sky-50 text-sky-700">يغلق {new Date(detail.endAt).toLocaleString('ar-EG')}</span>}</div>
      {detail.summary.pdfKey && <a className="btn-soft" href={fileUrl(detail.summary.pdfKey)} target="_blank" rel="noreferrer">معاينة ملف الامتحان</a>}
      <div className="max-h-72 space-y-2 overflow-auto">{detail.questions.map((q, i) => <div key={q.id} className="rounded-xl bg-ink-50 p-3 text-sm"><div className="flex justify-between gap-2"><span className="font-bold">{i + 1}. {q.stem}</span><span className="shrink-0 text-xs text-ink-500">{detail.pointsOverrides?.[i] ?? q.points} درجة</span></div><p className="mt-1 text-[11px] text-ink-400">{TYPES.find(([k]) => k === q.type)?.[1]} · {DIFF[q.difficulty]}{q.options?.length ? ` · الصحيح: ${q.options.filter(o => o.correct).map(o => o.text).join('، ')}` : q.correctAnswer ? ` · الإجابة: ${q.correctAnswer}` : ''}</p></div>)}</div>
    </div>)}

    {err && <p role="alert" className="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{err}</p>}
    <div className="mt-5 flex flex-wrap justify-between gap-2">
      <button className="btn-ghost" disabled={saving} onClick={() => { const i = STEPS.findIndex(([k]) => k === step); if (i === 0) onClose(); else setStep(STEPS[i - 1][0]) }}>{step === 'details' ? 'إلغاء' : 'رجوع'}</button>
      {step === 'review' ? <div className="flex gap-2">{detail?.summary.status === 'DRAFT' && <button disabled={saving} onClick={publish} className="btn-primary">{saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <><CheckCircle2 size={16} /> نشر الامتحان الآن</>}</button>}<button disabled={saving} onClick={onSaved} className={detail?.summary.status === 'DRAFT' ? 'btn-ghost' : 'btn-primary'}>{detail?.summary.status === 'DRAFT' ? 'احتفظ به كمسودة' : 'تم'}</button></div>
      : <button className="btn-primary" disabled={saving} onClick={next}>{saving ? 'جارٍ الحفظ...' : step === 'settings' ? (existing ? 'حفظ التعديلات ومراجعة' : 'إنشاء ومراجعة') : 'التالي'}</button>}
    </div>
  </Modal>
}

function ManualContent({ selected, setSelected, total, frozen }) {
  const [q, setQ] = useState(''), [diff, setDiff] = useState(''), [type, setType] = useState(''), [data, setData] = useState(null), [page, setPage] = useState(0)
  const [mine, setMine] = useState(false)
  useEffect(() => { const t = setTimeout(() => api.get('/exams/questions', { params: { q, difficulty: diff, type, mine, page, size: 12 } }).then(r => setData(r.data)).catch(() => setData({ content: [], totalElements: 0, totalPages: 0 })), 250); return () => clearTimeout(t) }, [q, diff, type, mine, page])
  const add = qn => { if (!selected.some(s => s.id === qn.id)) setSelected([...selected, { ...qn, pointsOverride: null }]) }
  const move = (i, d) => { const n = [...selected]; const [x] = n.splice(i, 1); n.splice(i + d, 0, x); setSelected(n) }
  return <div className="grid gap-4 md:grid-cols-2">
    <div className="min-w-0"><p className="mb-2 text-xs font-bold text-ink-500">بنك الأسئلة</p>
      <div className="flex gap-2"><label className="relative flex-1"><Search size={15} className="absolute right-3 top-3 text-ink-400" /><input className="input pr-9" placeholder="ابحث..." value={q} onChange={e => { setQ(e.target.value); setPage(0) }} /></label><select className="input w-28" value={diff} onChange={e => { setDiff(e.target.value); setPage(0) }}><option value="">الصعوبة</option>{Object.entries(DIFF).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
      <div className="mt-2 flex gap-2">
        <select className="input" value={type} onChange={e => { setType(e.target.value); setPage(0) }}><option value="">كل الأنواع</option>{TYPES.map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        <button type="button" onClick={() => { setMine(!mine); setPage(0) }} className={`chip shrink-0 border ${mine ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>أسئلتي</button>
      </div>
      <div className="mt-2 max-h-72 space-y-2 overflow-y-auto">{!data ? <PageLoader /> : data.content.map(qn => { const on = selected.some(s => s.id === qn.id); return <div key={qn.id} className="flex items-center gap-2 rounded-xl border border-ink-100 p-2.5"><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{qn.stem}</p><p className="text-[11px] text-ink-400">{TYPES.find(([k]) => k === qn.type)?.[1]} · {DIFF[qn.difficulty]} · {qn.points} درجة</p></div><button type="button" disabled={on || frozen} onClick={() => add(qn)} aria-label="إضافة" className={`rounded-lg p-1.5 ${on ? 'bg-emerald-50 text-emerald-600' : 'bg-brand-50 text-brand-700'}`}>{on ? <CheckCircle2 size={16} /> : <Plus size={16} />}</button></div> })}{data && !data.content.length && <p className="p-3 text-center text-xs text-ink-400">لا توجد أسئلة مطابقة</p>}</div>
      {data && data.totalPages > 1 && <div className="mt-2 flex items-center justify-between text-xs"><button className="btn-ghost px-3 py-1" disabled={page === 0} onClick={() => setPage(page - 1)}>السابق</button><span>{page + 1} / {data.totalPages} · {data.totalElements} سؤال</span><button className="btn-ghost px-3 py-1" disabled={page >= data.totalPages - 1} onClick={() => setPage(page + 1)}>التالي</button></div>}
    </div>
    <div className="min-w-0"><div className="mb-2 flex items-center justify-between"><p className="text-xs font-bold text-ink-500">أسئلة الامتحان ({selected.length})</p><span className="chip bg-brand-50 text-brand-700">{total} درجة</span></div>
      <div className="max-h-[22rem] space-y-2 overflow-y-auto">{selected.map((s, i) => <div key={s.id} className="rounded-xl border border-brand-100 bg-brand-50/40 p-2.5"><div className="flex items-start gap-2"><span className="grid h-6 w-6 shrink-0 place-items-center rounded-lg bg-brand-600 text-[11px] font-black text-white">{i + 1}</span><p className="min-w-0 flex-1 text-sm font-semibold leading-6">{s.stem}</p></div><div className="mt-2 flex items-center gap-1"><label className="flex items-center gap-1 text-[11px] text-ink-500">الدرجة<input type="number" min="0.5" step="0.5" className="input w-16 px-2 py-1 text-xs" value={s.pointsOverride ?? s.points} disabled={frozen} onChange={e => setSelected(selected.map((x, xi) => xi === i ? { ...x, pointsOverride: Number(e.target.value) === x.points ? null : Number(e.target.value) } : x))} /></label><span className="flex-1" /><button type="button" aria-label="لأعلى" disabled={i === 0 || frozen} onClick={() => move(i, -1)} className="rounded p-1 text-ink-400 hover:bg-white disabled:opacity-30"><ArrowUp size={14} /></button><button type="button" aria-label="لأسفل" disabled={i === selected.length - 1 || frozen} onClick={() => move(i, 1)} className="rounded p-1 text-ink-400 hover:bg-white disabled:opacity-30"><ArrowDown size={14} /></button><button type="button" aria-label="إزالة" disabled={frozen} onClick={() => setSelected(selected.filter((_, xi) => xi !== i))} className="rounded p-1 text-ink-400 hover:text-rose-600"><Trash2 size={14} /></button></div></div>)}{!selected.length && <p className="rounded-xl border border-dashed border-ink-200 p-6 text-center text-xs text-ink-400">أضف أسئلة من البنك</p>}</div>
    </div>
  </div>
}
