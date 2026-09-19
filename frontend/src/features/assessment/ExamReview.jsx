import { useEffect, useState } from 'react'
import { ArrowRight, CheckCircle2, XCircle, Clock } from 'lucide-react'
import api from '../../lib/api'
import { PageLoader } from '../../components/ui'
import { apiErrorMessage } from '../../lib/apiError'

/** Teacher view of one attempt: every answer with auto-grade result, plus manual grading for essays. */
export default function ExamReview({ row, onBack }) {
  const [answers, setAnswers] = useState(null), [draft, setDraft] = useState({}), [error, setError] = useState(''), [busy, setBusy] = useState(false), [saved, setSaved] = useState(null)
  const load = () => api.get(`/exams/attempts/${row.studentExamId}/answers`).then(r => setAnswers(r.data)).catch(e => setError(apiErrorMessage(e, 'تعذّر تحميل الإجابات')))
  useEffect(() => { load() }, [row.studentExamId])
  const save = async a => {
    const value = draft[a.questionId] || { points: a.awardedPoints, feedback: a.feedback || '' }
    if (value.points === '' || !Number.isFinite(Number(value.points)) || Number(value.points) < 0 || Number(value.points) > a.points) { setError(`الدرجة من صفر إلى ${a.points}`); return }
    setBusy(true); setError('')
    try { await api.post(`/exams/attempts/${row.studentExamId}/grade`, { questionId: a.questionId, points: Number(value.points), feedback: value.feedback }); await load(); setSaved(a.questionId); setTimeout(() => setSaved(null), 2000) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ التصحيح')) } finally { setBusy(false) }
  }
  const pending = (answers || []).filter(a => a.correct === null).length
  return <div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-2"><button onClick={onBack} disabled={busy} className="btn-ghost"><ArrowRight size={16} /> العودة للنتائج</button><div className="text-left"><h3 className="font-black">{row.studentName}</h3><p className="text-xs text-ink-500">{row.score} / {row.maxScore} · {pending ? `${pending} سؤال بانتظار تصحيحك` : 'كل الأسئلة مصحَّحة'}</p></div></div>
    {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    {!answers ? <PageLoader /> : answers.map((a, i) => {
      const v = draft[a.questionId] || { points: a.awardedPoints, feedback: a.feedback || '' }
      const change = patch => setDraft(d => ({ ...d, [a.questionId]: { ...v, ...patch } }))
      return <div key={a.questionId} className={`rounded-2xl border p-4 ${a.correct === null ? 'border-amber-300 bg-amber-50/30' : a.correct ? 'border-emerald-200' : 'border-rose-200'}`}>
        <div className="flex items-start justify-between gap-2"><p className="font-bold leading-7">{i + 1}. {a.stem}</p><span className="inline-flex shrink-0 items-center gap-1 text-xs font-bold">{a.correct === null ? <Clock size={14} className="text-amber-600" /> : a.correct ? <CheckCircle2 size={14} className="text-emerald-600" /> : <XCircle size={14} className="text-rose-600" />}{a.awardedPoints} / {a.points}</span></div>
        <p className="my-3 whitespace-pre-wrap rounded-xl bg-white p-3 text-sm leading-7 shadow-inner">{a.answerText || a.selectedOptions.join('، ') || <span className="text-ink-400">بدون إجابة</span>}</p>
        {a.type === 'ESSAY' && <div className="grid gap-3 sm:grid-cols-[120px_minmax(0,1fr)_auto]"><label className="block text-xs font-bold">الدرجة من {a.points}<input className="input mt-1" type="number" min="0" max={a.points} step="0.5" value={v.points} onChange={e => change({ points: e.target.value })} /></label><label className="block text-xs font-bold">ملاحظة للطالب<textarea dir="auto" rows={1} className="input mt-1" value={v.feedback} onChange={e => change({ feedback: e.target.value })} placeholder="ما الذي أصاب فيه وما الذي ينقصه؟" /></label><button disabled={busy} onClick={() => save(a)} className={`btn-primary self-end ${saved === a.questionId ? 'bg-emerald-600' : ''}`}>{saved === a.questionId ? 'تم الحفظ' : 'حفظ'}</button></div>}
        {a.type !== 'ESSAY' && a.feedback && <p className="text-xs text-ink-500">ملاحظة: {a.feedback}</p>}
      </div>
    })}
  </div>
}
