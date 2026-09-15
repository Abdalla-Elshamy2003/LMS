import { useEffect, useState } from 'react'
import { CheckCircle2, XCircle, Clock, Lightbulb, MessageSquare, ArrowRight } from 'lucide-react'
import api, { fileUrl } from '../../lib/api'
import { PageLoader } from '../../components/ui'

/** A student's own post-exam review (Moodle "review options" style): what they answered, what was right, and why. */
export default function StudentExamReview({ examId, onBack }) {
  const [data, setData] = useState(null), [error, setError] = useState(''), [filter, setFilter] = useState('all')
  useEffect(() => { api.get(`/exams/${examId}/my-review`).then(r => setData(r.data)).catch(e => setError(e.response?.data?.message || 'تعذّر تحميل المراجعة')) }, [examId])
  if (error) return <div className="card p-6" role="alert">{error}<button onClick={onBack} className="btn-ghost mr-3">رجوع</button></div>
  if (!data) return <PageLoader />
  const rows = data.answers.filter(a => filter === 'all' || (filter === 'wrong' ? a.correct === false : filter === 'correct' ? a.correct === true : a.correct == null))
  const counts = { correct: data.answers.filter(a => a.correct === true).length, wrong: data.answers.filter(a => a.correct === false).length, pending: data.answers.filter(a => a.correct == null).length }
  return <div className="space-y-4">
    <button onClick={onBack} className="btn-ghost"><ArrowRight size={16} /> رجوع</button>
    <div className="rounded-3xl bg-brand-950 p-6 text-white">
      <p className="text-xs text-cyan-200">مراجعة إجاباتك</p><h3 className="mt-1 text-xl font-black">{data.title}</h3>
      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">{[['الدرجة', `${data.score} / ${data.maxScore}`], ['النسبة', Math.round(data.percent) + '٪'], ['صحيحة', counts.correct], ['خاطئة', counts.wrong]].map(([l, v]) => <div key={l} className="rounded-2xl bg-white/10 p-3"><b className="text-lg font-black">{v}</b><p className="text-[11px] text-slate-300">{l}</p></div>)}</div>
      {data.needsManualGrade && <p className="mt-3 text-xs text-amber-200">بعض الأسئلة المقالية ما زالت بانتظار تصحيح المدرس؛ الدرجة ستُحدَّث بعد اعتمادها.</p>}
      {!data.showCorrectAnswers && <p className="mt-3 text-xs text-slate-300">المدرس أتاح عرض إجاباتك ونتيجة كل سؤال دون كشف الإجابات النموذجية.</p>}
    </div>
    <div className="flex flex-wrap gap-2">{[['all', 'الكل'], ['wrong', `خاطئة (${counts.wrong})`], ['correct', `صحيحة (${counts.correct})`], ...(counts.pending ? [['pending', `بانتظار التصحيح (${counts.pending})`]] : [])].map(([k, v]) => <button key={k} className={filter === k ? 'btn-primary' : 'btn-ghost'} onClick={() => setFilter(k)}>{v}</button>)}</div>
    {rows.map((a, i) => <div key={a.questionId} className={`rounded-3xl border bg-white p-5 ${a.correct === true ? 'border-emerald-200' : a.correct === false ? 'border-rose-200' : 'border-amber-200'}`}>
      <div className="flex flex-wrap items-center justify-between gap-2"><span className="inline-flex items-center gap-1.5 text-xs font-bold">{a.correct === true ? <CheckCircle2 size={16} className="text-emerald-600" /> : a.correct === false ? <XCircle size={16} className="text-rose-600" /> : <Clock size={16} className="text-amber-600" />}{a.correct === true ? 'إجابة صحيحة' : a.correct === false ? 'إجابة خاطئة' : 'بانتظار التصحيح'}</span><span className="text-xs font-bold text-ink-500">{a.awardedPoints} / {a.points} درجة</span></div>
      <p className="my-4 whitespace-pre-wrap font-bold leading-8">{data.answers.indexOf(a) + 1}. {a.stem}</p>
      {a.imageKey && <img src={fileUrl(a.imageKey)} alt="صورة السؤال" className="mb-4 max-h-80 w-auto max-w-full rounded-xl border border-ink-200" />}
      {a.options.length ? <div className="space-y-2">{a.options.map(o => { const tone = o.correct === true ? (o.selected ? 'border-emerald-500 bg-emerald-50' : 'border-emerald-300 border-dashed') : o.selected ? 'border-rose-400 bg-rose-50' : 'border-ink-200'; return <div key={o.id} className={`flex items-center gap-2 rounded-xl border-2 px-3 py-2 text-sm ${tone}`}>{o.correct === true ? <CheckCircle2 size={15} className="text-emerald-600" /> : o.selected ? <XCircle size={15} className="text-rose-500" /> : <span className="h-[15px] w-[15px]" />}<span className="flex-1">{o.text}</span>{o.selected && <span className="text-[11px] font-bold text-ink-500">اختيارك</span>}</div> })}</div>
      : <div className="grid gap-3 sm:grid-cols-2"><div className="rounded-xl bg-ink-50 p-3 text-sm"><b className="block text-xs text-ink-500">إجابتك</b><p className="mt-1 whitespace-pre-wrap leading-7">{a.myAnswerText || 'بدون إجابة'}</p></div>{a.correctAnswer && <div className="rounded-xl bg-emerald-50 p-3 text-sm"><b className="block text-xs text-emerald-700">الإجابة النموذجية</b><p className="mt-1 leading-7">{a.correctAnswer.split('|').join(' أو ')}</p></div>}</div>}
      {a.explanation && <p className="mt-3 flex gap-2 rounded-xl bg-amber-50 p-3 text-sm leading-7 text-amber-900"><Lightbulb size={17} className="mt-1 shrink-0" /><span><b className="block text-xs">لماذا؟</b>{a.explanation}</span></p>}
      {a.feedback && <p className="mt-3 flex gap-2 rounded-xl bg-brand-50 p-3 text-sm leading-7 text-brand-900"><MessageSquare size={17} className="mt-1 shrink-0" /><span><b className="block text-xs">ملاحظة المدرس</b>{a.feedback}</span></p>}
    </div>)}
    {!rows.length && <p className="py-8 text-center text-sm text-ink-400">لا توجد أسئلة في هذا التصنيف</p>}
  </div>
}
