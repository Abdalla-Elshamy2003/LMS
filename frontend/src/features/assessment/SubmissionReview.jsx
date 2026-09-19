import { useEffect, useMemo, useRef, useState } from 'react'
import { FileCheck, Search, Paperclip, ArrowLeft, RotateCcw, Download, Keyboard, Clock, AlertTriangle, Plus, Trash2, BookmarkPlus, ExternalLink, FileText, Image as ImageIcon, ChevronDown } from 'lucide-react'
import api, { fileUrl } from '../../lib/api'
import { Modal, PageLoader, EmptyState } from '../../components/ui'
import { downloadCsv } from '../../lib/csv'
import { apiErrorMessage } from '../../lib/apiError'

const labels = { SUBMITTED: 'بانتظار التصحيح', LATE: 'تسليم متأخر', GRADED: 'تم التصحيح', MISSING: 'لم يسلّم', RETURNED: 'أُعيد للتعديل' }
const tone = { SUBMITTED: 'bg-sky-50 text-sky-700', LATE: 'bg-amber-50 text-amber-700', GRADED: 'bg-emerald-50 text-emerald-700', MISSING: 'bg-rose-50 text-rose-700', RETURNED: 'bg-violet-50 text-violet-700' }
const isImage = n => /\.(png|jpe?g|webp)$/i.test(n || ''), isPdf = n => /\.pdf$/i.test(n || '')
const lateDays = s => s.lateSeconds ? Math.ceil(s.lateSeconds / 86400) : 0
const lateText = s => !s.lateSeconds ? '' : s.lateSeconds < 3600 ? `متأخر ${Math.max(1, Math.round(s.lateSeconds / 60))} دقيقة` : s.lateSeconds < 86400 ? `متأخر ${Math.round(s.lateSeconds / 3600)} ساعة` : `متأخر ${lateDays(s)} يوم`

/**
 * The grading studio (SpeedGrader-style): student queue on one side, the submission previewed inline in the
 * middle, and a grading panel with rubric levels, late-penalty preview, and the teacher's own comment bank.
 * J / K move between students, Ctrl+Enter saves and advances.
 */
export default function SubmissionReview({ assignment, onClose }) {
  const [rows, setRows] = useState(null), [selected, setSelected] = useState(null), [confirmClose, setConfirmClose] = useState(false)
  const [query, setQuery] = useState(''), [filter, setFilter] = useState('waiting'), [drafts, setDrafts] = useState({}), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const [bank, setBank] = useState([]), [bankOpen, setBankOpen] = useState(true), [preview, setPreview] = useState(0)
  const feedbackRef = useRef(null)
  const rubric = assignment.rubric || null
  const load = async () => { try { const { data } = await api.get(`/homework/assignments/${assignment.id}/submissions`); setRows(data); const waiting = data.find(s => ['SUBMITTED', 'LATE'].includes(s.status)); if (!waiting) setFilter(f => f === 'waiting' ? 'all' : f); setSelected(id => id ?? waiting?.id ?? data.find(s => s.status === 'GRADED')?.id ?? data[0]?.id) } catch (e) { setError(apiErrorMessage(e, 'تعذّر تحميل التسليمات')) } }
  useEffect(() => { load(); api.get('/homework/comments').then(r => setBank(r.data)).catch(() => {}) }, [assignment.id])
  const filtered = useMemo(() => (rows || []).filter(s => s.studentName.includes(query) && (filter === 'all' || (filter === 'waiting' ? ['SUBMITTED', 'LATE'].includes(s.status) : s.status === filter))), [rows, query, filter])
  const current = (rows || []).find(s => s.id === selected)
  const blank = c => ({ score: c.rawScore ?? c.score ?? '', feedback: c.feedback || '', rubric: c.rubricScores || {}, waive: c.status === 'GRADED' && c.penaltyPercent === 0 && lateDays(c) > 0 })
  const value = current ? drafts[current.id] || blank(current) : {}
  const change = patch => setDrafts(d => ({ ...d, [current.id]: { ...value, ...patch } }))
  const dirty = Object.keys(drafts).length > 0
  useEffect(() => { setPreview(0) }, [selected])

  const rubricTotal = rubric ? rubric.reduce((n, c) => n + (Number(value.rubric?.[c.id]) || 0), 0) : null
  const raw = rubric ? rubricTotal : Number(value.score)
  const days = current ? lateDays(current) : 0
  const penalty = current && !value.waive && assignment.latePenaltyPercent > 0 ? Math.min(100, assignment.latePenaltyPercent * days) : 0
  const finalScore = Number.isFinite(raw) ? Math.round((raw - raw * penalty / 100) * 10) / 10 : null

  const mutate = async (action, next = false) => {
    if (!current || busy) return
    if (action === 'grade') {
      if (rubric) { const missing = rubric.find(c => value.rubric?.[c.id] === undefined || value.rubric?.[c.id] === ''); if (missing) { setError(`حدّد درجة المعيار «${missing.title}»`); return } }
      else if (value.score === '' || !Number.isFinite(Number(value.score)) || Number(value.score) < 0 || Number(value.score) > assignment.maxScore) { setError(`الدرجة من صفر إلى ${assignment.maxScore}`); return }
    }
    if (action === 'return' && !value.feedback.trim()) { setError('اكتب التعديلات المطلوبة أولاً'); return }
    setBusy(true); setError(''); setNotice('')
    try {
      const body = action === 'grade' ? { score: rubric ? null : Number(value.score), rubricScores: rubric ? Object.fromEntries(rubric.map(c => [c.id, Number(value.rubric[c.id])])) : null, feedback: value.feedback, waivePenalty: value.waive } : { feedback: value.feedback }
      const { data } = await api.post(`/homework/submissions/${current.id}/${action}`, body)
      setRows(rs => rs.map(r => r.id === data.id ? data : r)); setDrafts(d => { const n = { ...d }; delete n[current.id]; return n })
      setNotice(action === 'grade' ? `تم اعتماد ${data.score} / ${assignment.maxScore}${data.penaltyPercent ? ` بعد خصم ${data.penaltyPercent}٪ للتأخير` : ''}` : 'أُعيد الواجب للطالب مع ملاحظتك')
      if (next) { const idx = filtered.findIndex(s => s.id === current.id); const after = [...filtered.slice(idx + 1), ...filtered.slice(0, idx)].find(s => ['SUBMITTED', 'LATE'].includes(s.status)); if (after) setSelected(after.id) }
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ؛ التعديلات ما زالت موجودة')) } finally { setBusy(false) }
  }
  const missing = async () => { setBusy(true); setError(''); try { const { data } = await api.post(`/homework/assignments/${assignment.id}/mark-missing`); await load(); setNotice(`تم رصد ${data} طالب لم يسلّم من كل طلاب الكورس المؤهلين`) } catch (e) { setError(apiErrorMessage(e, 'تعذّر رصد غير المسلّمين')) } finally { setBusy(false) } }
  const insert = async c => { change({ feedback: `${value.feedback}${value.feedback ? '\n' : ''}${c.text}` }); api.post(`/homework/comments/${c.id}/use`).then(r => setBank(b => b.map(x => x.id === c.id ? r.data : x))).catch(() => {}) }
  const saveToBank = async () => { const text = value.feedback.trim(); if (!text) return; try { const { data } = await api.post('/homework/comments', { text }); setBank(b => [data, ...b]); setNotice('حُفظ التعليق في بنكك') } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ التعليق')) } }
  const exportCsv = () => downloadCsv(`${assignment.title}-grades.csv`, ['الطالب', 'الحالة', 'وقت التسليم', 'التأخير', 'الدرجة قبل الخصم', 'الخصم ٪', 'الدرجة', 'من', 'الملاحظة'], (rows || []).map(s => [s.studentName, labels[s.status], s.submittedAt ? new Date(s.submittedAt).toLocaleString('ar-EG') : '', lateText(s), s.rawScore ?? '', s.penaltyPercent || 0, s.score ?? '', assignment.maxScore, s.feedback || '']))

  useEffect(() => {
    const key = e => {
      if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); mutate('grade', true); return }
      if (['TEXTAREA', 'INPUT', 'SELECT'].includes(e.target.tagName)) return
      if (e.key.toLowerCase() === 'j' || e.key.toLowerCase() === 'k') { const i = filtered.findIndex(s => s.id === selected); const n = filtered[i + (e.key.toLowerCase() === 'j' ? 1 : -1)]; if (n) setSelected(n.id) }
    }
    window.addEventListener('keydown', key); return () => window.removeEventListener('keydown', key)
  })

  const files = current ? (current.files?.length ? current.files : current.fileKey ? [{ fileKey: current.fileKey, name: current.fileKey.split('/').pop() }] : []) : []
  const previewFile = files[preview]
  return <Modal open size="xl" title={`استوديو التصحيح · ${assignment.title}`} onClose={() => { if (!busy) dirty ? setConfirmClose(true) : onClose() }}>
    <div className="space-y-3">
      {confirmClose && <div className="rounded-xl bg-amber-50 p-4"><p className="text-sm">فيه تصحيح لسه ما اتحفظش. إغلاق الاستوديو هيفقد التعديلات دي.</p><div className="mt-3 flex gap-2"><button onClick={() => setConfirmClose(false)} className="btn-primary">أكمل التصحيح</button><button onClick={onClose} className="btn-ghost">إغلاق بدون حفظ</button></div></div>}
      <div className="flex flex-wrap items-center gap-2 text-xs"><span className="chip bg-sky-50 text-sky-700">{(rows || []).filter(s => ['SUBMITTED', 'LATE'].includes(s.status)).length} بانتظار التصحيح</span><span className="chip bg-emerald-50 text-emerald-700">{(rows || []).filter(s => s.status === 'GRADED').length} مُصحَّح</span><span className="chip bg-violet-50 text-violet-700">{(rows || []).filter(s => s.status === 'RETURNED').length} للتعديل</span><span className="chip bg-rose-50 text-rose-700">{(rows || []).filter(s => s.status === 'MISSING').length} غائب</span><span className="chip bg-ink-100 text-ink-500">{assignment.enrolled} مسجّل</span><span className="flex-1" /><span className="hidden items-center gap-1 text-ink-400 md:inline-flex"><Keyboard size={13} /> J/K للتنقل · Ctrl+Enter حفظ والتالي</span><button onClick={exportCsv} className="btn-ghost px-3 py-1.5" disabled={!rows?.length}><Download size={14} /> CSV</button></div>
      {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error} {!rows && <button onClick={load} className="btn-ghost">إعادة المحاولة</button>}</p>}
      {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{notice}</p>}
      {!rows ? (!error && <PageLoader />) : <div className="grid gap-4 lg:grid-cols-[230px_minmax(0,1fr)_320px]">
        <aside className="space-y-2"><label className="relative block"><Search size={15} className="absolute right-3 top-3 text-ink-400" /><input aria-label="ابحث باسم الطالب" className="input pr-9" placeholder="ابحث عن طالب" value={query} onChange={e => setQuery(e.target.value)} /></label>
          <select aria-label="حالة التسليم" className="input" value={filter} onChange={e => setFilter(e.target.value)}><option value="waiting">بانتظار التصحيح</option><option value="all">كل التسليمات</option>{Object.entries(labels).filter(([k]) => !['SUBMITTED', 'LATE'].includes(k)).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          <div className="max-h-40 space-y-1.5 overflow-auto lg:max-h-[56vh]">{filtered.map(s => <button disabled={busy} key={s.id} onClick={() => { setSelected(s.id); setError(''); setNotice('') }} className={`w-full rounded-xl border p-2.5 text-right transition ${s.id === selected ? 'border-brand-300 bg-brand-50' : 'border-ink-100 hover:bg-ink-50'}`}><span className="flex items-center justify-between gap-2"><b className="truncate text-sm">{s.studentName}</b>{s.score != null && <span className="shrink-0 text-xs font-black text-ink-600">{s.score}</span>}</span><span className={`mt-1 inline-block rounded-md px-1.5 py-0.5 text-[10px] font-bold ${tone[s.status]}`}>{labels[s.status]}</span>{drafts[s.id] && <span className="mr-1 text-[10px] text-amber-600">• غير محفوظ</span>}</button>)}{!filtered.length && <p className="p-3 text-center text-xs text-ink-400">لا نتائج</p>}</div>
        </aside>
        {current ? <section className="min-w-0 space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-2 rounded-2xl bg-ink-50 p-4"><div><h3 className="font-black">{current.studentName}</h3><p className="mt-1 text-xs text-ink-500">{current.submittedAt ? <><Clock size={11} className="inline" /> سلّم {new Date(current.submittedAt).toLocaleString('ar-EG')}</> : 'لا يوجد تسليم'}{current.resubmissions > 0 && ` · أعاد التسليم ${current.resubmissions} مرة`}</p>{current.lateSeconds > 0 && <p className="mt-1 inline-flex items-center gap-1 text-xs font-bold text-amber-700"><AlertTriangle size={12} /> {lateText(current)}{assignment.latePenaltyPercent > 0 && ` · الخصم المقترح ${Math.min(100, assignment.latePenaltyPercent * days)}٪`}</p>}</div><span className={`chip ${tone[current.status]}`}>{labels[current.status]}</span></div>
          {current.text && <div className="max-h-56 overflow-auto whitespace-pre-wrap break-words rounded-2xl border border-ink-200 bg-white p-4 text-sm leading-8">{current.text}</div>}
          {files.length > 0 && <div className="rounded-2xl border border-ink-200 bg-white"><div className="flex flex-wrap items-center gap-1 border-b border-ink-100 p-2">{files.map((f, i) => <button key={f.fileKey} onClick={() => setPreview(i)} className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-bold ${i === preview ? 'bg-brand-600 text-white' : 'bg-ink-100 text-ink-600'}`}>{isImage(f.name) ? <ImageIcon size={12} /> : <FileText size={12} />} {f.name?.length > 22 ? f.name.slice(0, 20) + '…' : f.name}</button>)}<span className="flex-1" />{previewFile && <a className="btn-ghost px-2 py-1 text-xs" href={fileUrl(previewFile.fileKey)} target="_blank" rel="noreferrer"><ExternalLink size={12} /> فتح</a>}</div>
            {previewFile && (isImage(previewFile.name) ? <div className="max-h-[52vh] overflow-auto bg-ink-900/90 p-2 text-center"><img src={fileUrl(previewFile.fileKey)} alt={previewFile.name} className="mx-auto max-h-[50vh] rounded" /></div> : isPdf(previewFile.name) ? <iframe title={previewFile.name} src={fileUrl(previewFile.fileKey)} className="h-[52vh] w-full" /> : <p className="p-6 text-center text-sm text-ink-500"><Paperclip size={16} className="inline" /> لا تتوفر معاينة لهذا النوع؛ افتحه في تبويب جديد.</p>)}</div>}
          {!current.text && !files.length && <p className="rounded-2xl border border-dashed border-ink-200 p-8 text-center text-sm text-ink-400">{current.status === 'MISSING' ? 'رُصد الطالب كغائب' : 'لا يوجد محتوى في هذا التسليم'}</p>}
        </section> : <EmptyState icon={FileCheck} title="لا توجد تسليمات بعد" />}
        {current && !['MISSING', 'RETURNED'].includes(current.status) && <aside className="space-y-3 rounded-2xl border border-ink-200 bg-white p-4">
          {rubric ? <div className="space-y-2"><p className="text-xs font-bold text-ink-500">معايير التصحيح</p>{rubric.map(c => <div key={c.id} className="rounded-xl bg-ink-50 p-2.5"><div className="flex items-center justify-between gap-2"><b className="text-sm">{c.title}</b><input type="number" min="0" max={c.maxPoints} step="0.5" disabled={busy} aria-label={`درجة ${c.title}`} className="input w-20 px-2 py-1 text-xs" value={value.rubric?.[c.id] ?? ''} onChange={e => change({ rubric: { ...value.rubric, [c.id]: e.target.value } })} /></div>{c.description && <p className="mt-1 text-[11px] text-ink-500">{c.description}</p>}{c.levels?.length > 0 && <div className="mt-2 flex flex-wrap gap-1">{c.levels.map(l => <button key={l.label} type="button" disabled={busy} onClick={() => change({ rubric: { ...value.rubric, [c.id]: l.points } })} className={`rounded-lg px-2 py-1 text-[11px] font-bold ${Number(value.rubric?.[c.id]) === l.points ? 'bg-brand-600 text-white' : 'bg-white text-ink-600 ring-1 ring-ink-200'}`}>{l.label} · {l.points}</button>)}</div>}<p className="mt-1 text-left text-[10px] text-ink-400">من {c.maxPoints}</p></div>)}<p className="text-left text-sm font-black">المجموع {rubricTotal} / {assignment.maxScore}</p></div>
          : <label className="block text-sm font-bold">الدرجة من {assignment.maxScore}<input className="input mt-2 text-lg font-black" disabled={busy} type="number" min="0" max={assignment.maxScore} step="0.5" value={value.score} onChange={e => change({ score: e.target.value })} /></label>}
          {days > 0 && assignment.latePenaltyPercent > 0 && <div className={`rounded-xl p-3 text-xs ${value.waive ? 'bg-ink-50 text-ink-600' : 'bg-amber-50 text-amber-800'}`}><p className="font-bold">{days} يوم تأخير × {assignment.latePenaltyPercent}٪ = خصم {Math.min(100, assignment.latePenaltyPercent * days)}٪</p>{finalScore != null && <p className="mt-1">الدرجة النهائية بعد الخصم: <b>{finalScore}</b> / {assignment.maxScore}</p>}<label className="mt-2 flex items-center gap-2"><input type="checkbox" checked={!!value.waive} onChange={e => change({ waive: e.target.checked })} /> إعفاء هذا الطالب من الخصم</label></div>}
          <div><label className="block text-sm font-bold">ملاحظات للطالب<textarea ref={feedbackRef} dir="auto" className="input mt-2" rows={4} disabled={busy} value={value.feedback} onChange={e => change({ feedback: e.target.value })} placeholder="ما الذي أتقنه؟ وما الذي يحتاج إلى تحسين؟" /></label>
            <div className="mt-2"><button type="button" onClick={() => setBankOpen(o => !o)} className="inline-flex items-center gap-1 text-xs font-bold text-brand-700"><ChevronDown size={13} className={bankOpen ? '' : '-rotate-90'} /> بنك تعليقاتي ({bank.length})</button><button type="button" disabled={!value.feedback?.trim()} onClick={saveToBank} className="mr-3 inline-flex items-center gap-1 text-xs font-bold text-ink-500 disabled:opacity-40"><BookmarkPlus size={13} /> احفظ الملاحظة الحالية</button>
              {bankOpen && <div className="mt-2 flex max-h-32 flex-wrap gap-1.5 overflow-auto">{bank.map(c => <span key={c.id} className="inline-flex items-center gap-1 rounded-lg border border-ink-200 bg-ink-50 text-xs"><button type="button" disabled={busy} className="px-2 py-1 text-ink-700" onClick={() => insert(c)}>{c.text.length > 40 ? c.text.slice(0, 38) + '…' : c.text}</button><button type="button" aria-label="حذف التعليق" onClick={() => api.delete(`/homework/comments/${c.id}`).then(() => setBank(b => b.filter(x => x.id !== c.id))).catch(() => {})} className="pl-1.5 text-ink-300 hover:text-rose-500"><Trash2 size={11} /></button></span>)}{!bank.length && <p className="text-[11px] text-ink-400">اكتب ملاحظة ثم احفظها لتعيد استخدامها بضغطة واحدة.</p>}</div>}</div></div>
          <div className="flex flex-wrap gap-2 border-t border-ink-100 pt-3"><button disabled={busy} className="btn-primary flex-1" onClick={() => mutate('grade', true)}><FileCheck size={16} /> حفظ والتالي <ArrowLeft size={14} /></button><button disabled={busy} className="btn-soft" onClick={() => mutate('grade')}>حفظ</button>{current.status !== 'GRADED' && <button disabled={busy} className="btn-ghost w-full" onClick={() => mutate('return')}><RotateCcw size={15} /> إعادة للطالب للتعديل</button>}</div>
          {current.status === 'GRADED' && <p className="text-[11px] text-ink-400">اعتُمدت {current.score} / {assignment.maxScore}{current.penaltyPercent ? ` (قبل الخصم ${current.rawScore})` : ''} · {current.gradedAt && new Date(current.gradedAt).toLocaleString('ar-EG')}. يمكنك تعديلها وإعادة الحفظ.</p>}
        </aside>}
        {current && current.status === 'RETURNED' && <aside className="rounded-2xl border border-violet-200 bg-violet-50 p-4 text-sm"><b className="block">أُعيد للطالب للتعديل</b><p className="mt-2 whitespace-pre-wrap text-xs leading-6 text-violet-900">{current.feedback}</p><p className="mt-2 text-[11px] text-violet-700">سيظهر هنا تسليمه الجديد فور وصوله.</p></aside>}
      </div>}
      {assignment.deadline && Date.now() > Date.parse(assignment.deadline) && <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3"><p className="text-xs text-ink-500">يشمل الرصد كل طلاب الكورس المؤهلين الذين لم يسلّموا، بغض النظر عن الفلتر.</p><button disabled={busy} onClick={missing} className="btn-ghost text-rose-700">رصد غير المسلّمين</button></div>}
    </div>
  </Modal>
}
