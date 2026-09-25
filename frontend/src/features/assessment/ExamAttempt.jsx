import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, Clock, Flag, ChevronLeft, ChevronRight, Cloud, CloudOff, Maximize, ShieldCheck, LogOut, ListChecks, Eye, Keyboard, AlertTriangle, XCircle } from 'lucide-react'
import api, { fileUrl } from '../../lib/api'
import { PageLoader } from '../../components/ui'
import StudentExamReview from './StudentExamReview'
import { apiErrorMessage } from '../../lib/apiError'

const TYPE_HINT = { MCQ: 'اختر إجابة واحدة', TRUE_FALSE: 'اختر صح أو خطأ', MULTI_SELECT: 'يمكن اختيار أكثر من إجابة', FILL_BLANK: 'أكمل الفراغ بكلمة أو عبارة قصيرة', SHORT_ANSWER: 'إجابة قصيرة', ESSAY: 'إجابة مقالية · تُصحَّح يدوياً', NUMERIC: 'اكتب الرقم فقط' }

/**
 * The exam hall: a full-screen focused canvas (no page chrome), server-authoritative timer, autosave,
 * question palette, keyboard navigation, and the exam's own lockdown rules (fullscreen, copy block,
 * focus-loss logging). Every integrity signal is sent with the draft so it survives a crash.
 */
export default function ExamAttempt({ exam, onClose }) {
  const [attempt, setAttempt] = useState(null), [answers, setAnswers] = useState({}), [result, setResult] = useState(null)
  const [index, setIndex] = useState(0), [flags, setFlags] = useState({}), [left, setLeft] = useState(0)
  const [error, setError] = useState(''), [save, setSave] = useState({ state: 'idle', text: '' }), [busy, setBusy] = useState(false)
  const [review, setReview] = useState(false), [closing, setClosing] = useState(false), [starting, setStarting] = useState(false)
  const [agreed, setAgreed] = useState(false), [fsWarning, setFsWarning] = useState(false), [showReview, setShowReview] = useState(false), [paletteOpen, setPaletteOpen] = useState(false)
  const latest = useRef({}), tabs = useRef(0), events = useRef([]), queue = useRef(Promise.resolve()), inFlight = useRef(false), submitRef = useRef(null), clockOffset = useRef(0), hall = useRef(null)
  const running = attempt && !result
  const log = (type) => { if (events.current.length < 300) events.current.push({ type, at: new Date().toISOString() }) }
  const payload = () => ({ answers: Object.entries(latest.current).map(([id, a]) => ({ questionId: Number(id), answerText: a.answerText || null, selectedOptions: a.selectedOptions || [] })), tabSwitches: tabs.current, events: events.current })

  const start = () => { if (starting) return; setStarting(true); setError(''); api.post(`/exams/${exam.id}/start`).then(({ data }) => {
    latest.current = Object.fromEntries((data.savedAnswers || []).map(a => [a.questionId, a])); setAnswers(latest.current)
    clockOffset.current = Date.parse(data.serverNow) - Date.now(); setAttempt(data)
    setLeft(Math.max(0, Math.ceil((Date.parse(data.expiresAt) - Date.now() - clockOffset.current) / 1000)))
    setSave({ state: 'ok', text: data.savedAt ? 'تمت استعادة إجاباتك المحفوظة' : 'إجاباتك تُحفظ تلقائياً' })
    if (data.savedAt) log('RESUME')
    if (data.fullscreen) hall.current?.requestFullscreen?.().catch(() => setFsWarning(true))
  }).catch(e => setError(apiErrorMessage(e, 'تعذّر بدء الامتحان'))).finally(() => setStarting(false)) }

  const persist = () => {
    if (!attempt || result || inFlight.current) return queue.current
    const snapshot = payload(); setSave({ state: 'saving', text: 'جارٍ الحفظ...' })
    queue.current = queue.current.catch(() => {}).then(() => api.put(`/exams/attempts/${attempt.studentExamId}/draft`, snapshot))
      .then(() => setSave({ state: 'ok', text: 'محفوظ · ' + new Date().toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' }) }))
      .catch(e => { setSave({ state: 'error', text: e.response?.status === 400 ? 'انتهى وقت التعديل؛ سيُعتمد آخر حفظ' : 'تعذّر الحفظ؛ تحقق من الاتصال' }); throw e })
    return queue.current
  }
  const saveRef = useRef(null); saveRef.current = persist
  useEffect(() => { if (!running) return; const t = setTimeout(() => saveRef.current?.().catch(() => {}), 1000); return () => clearTimeout(t) }, [answers, running])
  useEffect(() => {
    if (!running) return
    const timer = setInterval(() => {
      const seconds = Math.max(0, Math.ceil((Date.parse(attempt.expiresAt) - Date.now() - clockOffset.current) / 1000)); setLeft(seconds)
      if (!seconds) { clearInterval(timer); submitRef.current?.() }
    }, 1000)
    const retry = setInterval(() => saveRef.current?.().catch(() => {}), 15000)
    const visibility = () => { if (document.hidden) { if (attempt.detectTabSwitch) { tabs.current += 1; log('TAB_HIDDEN') } } else if (attempt.detectTabSwitch) log('TAB_VISIBLE') }
    const blur = () => { if (attempt.detectTabSwitch) log('WINDOW_BLUR') }
    const fullscreen = () => { if (!attempt.fullscreen) return; if (document.fullscreenElement) { setFsWarning(false); log('FULLSCREEN_ENTER') } else { setFsWarning(true); log('FULLSCREEN_EXIT'); saveRef.current?.().catch(() => {}) } }
    const unload = e => { e.preventDefault(); e.returnValue = '' }
    const online = () => saveRef.current?.().catch(() => {})
    document.addEventListener('visibilitychange', visibility); window.addEventListener('blur', blur); document.addEventListener('fullscreenchange', fullscreen)
    window.addEventListener('beforeunload', unload); window.addEventListener('online', online)
    return () => { clearInterval(timer); clearInterval(retry); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('blur', blur); document.removeEventListener('fullscreenchange', fullscreen); window.removeEventListener('beforeunload', unload); window.removeEventListener('online', online) }
  }, [attempt, result])
  useEffect(() => () => { if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {}) }, [])

  const submit = async () => {
    if (!attempt || result || inFlight.current) return
    inFlight.current = true; setBusy(true); setError('')
    try { await queue.current.catch(() => {}); const { data } = await api.post(`/exams/attempts/${attempt.studentExamId}/submit`, payload()); setResult(data); if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {}) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر تأكيد التسليم. الإجابات ما زالت في الصفحة؛ أعد المحاولة.')) }
    finally { inFlight.current = false; setBusy(false) }
  }; submitRef.current = submit

  const question = attempt?.questions[index]
  // Always mutate through latest.current so quick successive key presses never act on a stale render.
  const changeFor = (q, patch) => { const next = { ...latest.current, [q.questionId]: { ...latest.current[q.questionId], ...patch } }; latest.current = next; setAnswers(next) }
  const change = patch => changeFor(question, patch)
  const answered = q => !!answers[q.questionId]?.answerText?.trim() || !!answers[q.questionId]?.selectedOptions?.length
  const count = attempt?.questions.filter(answered).length || 0
  const flagged = Object.values(flags).filter(Boolean).length
  const go = i => { if (!attempt) return; setIndex(Math.max(0, Math.min(attempt.questions.length - 1, i))); setReview(false); setPaletteOpen(false) }
  const pickFor = (q, o) => { if (!q || !o) return; const cur = latest.current[q.questionId]?.selectedOptions || []; changeFor(q, { selectedOptions: q.type === 'MULTI_SELECT' ? (cur.includes(o.id) ? cur.filter(id => id !== o.id) : [...cur, o.id]) : [o.id] }) }
  const pick = o => pickFor(question, o)
  const cursor = useRef({}); cursor.current = { index, question, total: attempt?.questions.length || 0 }

  useEffect(() => {
    if (!running) return
    const key = e => {
      if (['TEXTAREA', 'INPUT'].includes(e.target.tagName) || busy) return
      const { index: i, question: q, total } = cursor.current
      if (e.key === 'ArrowLeft' || e.key === 'Enter') { e.preventDefault(); if (i < total - 1) { cursor.current = { ...cursor.current, index: i + 1, question: attempt.questions[i + 1] }; go(i + 1) } else setReview(true) }
      else if (e.key === 'ArrowRight') { e.preventDefault(); if (i > 0) { cursor.current = { ...cursor.current, index: i - 1, question: attempt.questions[i - 1] }; go(i - 1) } }
      else if (/^[1-9]$/.test(e.key) && q?.options?.length) { e.preventDefault(); pickFor(q, q.options[Number(e.key) - 1]) }
      else if (e.key.toLowerCase() === 'f' && q) setFlags(f => ({ ...f, [q.questionId]: !f[q.questionId] }))
    }
    window.addEventListener('keydown', key); return () => window.removeEventListener('keydown', key)
  }, [running, busy, attempt])

  const close = () => { if (busy) return; if (!attempt || result) onClose(); else setClosing(true) }
  const guard = e => { if (running && attempt.disableCopy) { e.preventDefault(); log(e.type === 'paste' ? 'PASTE_BLOCKED' : 'COPY_BLOCKED') } }
  const mins = Math.floor(left / 60), secs = String(left % 60).padStart(2, '0')
  const timerTone = left <= 60 ? 'bg-rose-600 text-white animate-pulse' : left <= 300 ? 'bg-amber-400 text-ink-900' : 'bg-white/10 text-white'

  return <div ref={hall} dir="rtl" className={`fixed inset-0 z-[70] flex flex-col bg-ink-50 text-ink-900 ${running && attempt.disableCopy ? 'select-none' : ''}`}
    onCopy={guard} onCut={guard} onPaste={guard} onContextMenu={e => { if (running && attempt.disableCopy) e.preventDefault() }} onDragStart={e => { if (running && attempt.disableCopy) e.preventDefault() }}>
    <header className="flex items-center justify-between gap-3 bg-brand-950 px-4 py-3 text-white sm:px-6">
      <div className="min-w-0"><p className="truncate text-xs text-cyan-200">{exam.courseTitle || 'مدارك'} · قاعة الامتحان</p><h2 className="truncate text-base font-black sm:text-lg">{exam.title}</h2></div>
      {running && <div className="flex items-center gap-2 sm:gap-3">
        <span className="hidden items-center gap-1 rounded-xl bg-white/10 px-3 py-2 text-xs font-bold sm:inline-flex"><ListChecks size={15} /> {count} / {attempt.questions.length}</span>
        <span role="timer" aria-live="off" className={`inline-flex items-center gap-2 rounded-xl px-3 py-2 font-black tabular-nums ${timerTone}`}><Clock size={17} />{mins}:{secs}</span>
        <button onClick={close} className="rounded-xl bg-white/10 p-2 hover:bg-white/20" aria-label="حفظ والخروج"><LogOut size={18} /></button>
      </div>}
      {!running && <button onClick={close} className="rounded-xl bg-white/10 px-3 py-2 text-sm font-bold hover:bg-white/20">إغلاق</button>}
    </header>

    {error && <p role="alert" className="mx-4 mt-3 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error} {!attempt && <button onClick={start} className="btn-ghost mr-2">إعادة المحاولة</button>}</p>}

    {!attempt ? <Lobby exam={exam} starting={starting} agreed={agreed} setAgreed={setAgreed} onStart={start} /> : result ? (
      showReview ? <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6"><div className="mx-auto max-w-3xl"><StudentExamReview examId={exam.id} onBack={() => setShowReview(false)} /></div></div>
      : <Result result={result} exam={exam} onClose={onClose} onReview={() => setShowReview(true)} />
    ) : <>
      {fsWarning && <div role="alert" className="flex flex-wrap items-center justify-between gap-3 bg-amber-100 px-4 py-3 text-sm text-amber-900"><span className="inline-flex items-center gap-2 font-bold"><AlertTriangle size={17} /> هذا الامتحان يتطلب وضع ملء الشاشة. الخروج منه يُسجَّل ويظهر للمدرس.</span><button className="btn-primary" onClick={() => hall.current?.requestFullscreen?.().catch(() => {})}><Maximize size={16} /> العودة لملء الشاشة</button></div>}
      <div className="flex items-center justify-between gap-2 border-b border-ink-200 bg-white px-4 py-2 text-xs text-ink-500">
        <span role="status" className={`inline-flex items-center gap-1.5 ${save.state === 'error' ? 'text-rose-600' : save.state === 'saving' ? 'text-ink-400' : 'text-emerald-700'}`}>{save.state === 'error' ? <CloudOff size={14} /> : <Cloud size={14} />}{save.text}</span>
        <span className="hidden items-center gap-1 sm:inline-flex"><Keyboard size={14} /> الأسهم للتنقل · الأرقام للاختيار · F للتعليم</span>
        <button className="btn-ghost px-3 py-1.5 lg:hidden" onClick={() => setPaletteOpen(o => !o)}><ListChecks size={15} /> الأسئلة</button>
      </div>
      <div className="grid min-h-0 flex-1 lg:grid-cols-[minmax(0,1fr)_280px]">
        <main className="min-h-0 overflow-y-auto p-4 sm:p-6">
          <div className="mx-auto max-w-3xl">
            {closing ? <div className="rounded-3xl border border-amber-200 bg-amber-50 p-6"><h3 className="text-lg font-black">حفظ والخروج من القاعة؟</h3><p className="my-3 text-sm leading-7">الوقت يستمر في العد حتى لو خرجت. عند انتهائه تُعتمد آخر إجابات محفوظة على المنصة. يمكنك العودة واستكمال المحاولة في أي وقت قبل انتهاء الوقت.</p><div className="flex gap-2"><button className="btn-primary" onClick={() => setClosing(false)}>أكمل الحل</button><button className="btn-ghost" onClick={async () => { try { await persist(); onClose() } catch { setError('لم يتم الحفظ. تأكد من الاتصال ثم حاول مرة أخرى.'); setClosing(false) } }}>حفظ وخروج</button></div></div>
            : review ? <div className="rounded-3xl border border-brand-200 bg-white p-6"><h3 className="text-xl font-black">راجع قبل التسليم النهائي</h3>
                <div className="my-5 grid grid-cols-3 gap-3 text-center">{[['مجاب', count, 'text-emerald-700 bg-emerald-50'], ['بدون إجابة', attempt.questions.length - count, 'text-rose-700 bg-rose-50'], ['للمراجعة', flagged, 'text-amber-700 bg-amber-50']].map(([l, v, c]) => <div key={l} className={`rounded-2xl p-4 ${c}`}><b className="text-2xl font-black">{v}</b><p className="mt-1 text-xs">{l}</p></div>)}</div>
                {attempt.questions.length - count > 0 && <div className="mb-4 flex flex-wrap gap-2 text-xs">{attempt.questions.map((q, i) => !answered(q) && <button key={q.questionId} onClick={() => go(i)} className="rounded-lg border border-rose-200 bg-rose-50 px-2 py-1 font-bold text-rose-700">سؤال {i + 1}</button>)}</div>}
                <p className="text-sm text-ink-500">بعد التأكيد لا يمكن تعديل الإجابات. آخر حفظ: {save.text}</p>
                <div className="mt-5 flex flex-wrap gap-2"><button disabled={busy} onClick={submit} className="btn-primary">{busy ? 'جارٍ التسليم...' : 'تأكيد التسليم النهائي'}</button><button disabled={busy} className="btn-ghost" onClick={() => setReview(false)}>العودة للحل</button></div></div>
            : question && <div className="rounded-3xl border border-ink-200 bg-white p-5 sm:p-7">
                <div className="flex flex-wrap items-center justify-between gap-2"><span className="text-xs font-bold text-brand-700">سؤال {index + 1} من {attempt.questions.length} · {question.points} درجة</span><span className="text-xs text-ink-400">{TYPE_HINT[question.type]}</span></div>
                <p className="my-5 whitespace-pre-wrap text-lg font-bold leading-9 sm:text-xl">{question.stem}</p>
                {question.imageKey && <img src={fileUrl(question.imageKey)} alt="صورة السؤال"
                  className="mb-5 max-h-[420px] w-auto max-w-full rounded-xl border border-ink-200" />}
                <fieldset disabled={busy || left === 0} className="space-y-3">
                  {question.options.length ? question.options.map((o, i) => { const on = !!answers[question.questionId]?.selectedOptions?.includes(o.id); return <label key={o.id} className={`flex cursor-pointer items-start gap-3 rounded-2xl border-2 p-4 text-sm transition ${on ? 'border-brand-500 bg-brand-50' : 'border-ink-200 hover:border-brand-300'}`}>
                    <span className={`grid h-7 w-7 shrink-0 place-items-center rounded-lg text-xs font-black ${on ? 'bg-brand-600 text-white' : 'bg-ink-100 text-ink-500'}`}>{i + 1}</span>
                    <input className="sr-only" type={question.type === 'MULTI_SELECT' ? 'checkbox' : 'radio'} name={`q-${question.questionId}`} checked={on} onChange={() => pick(o)} /><span className="leading-7">{o.text}</span></label> })
                  : <label className="block text-sm font-bold">إجابتك<textarea dir="auto" maxLength={20000} rows={question.type === 'ESSAY' ? 8 : 2} className="input mt-2 text-base leading-8" value={answers[question.questionId]?.answerText || ''} onChange={e => change({ answerText: e.target.value })} placeholder={question.type === 'NUMERIC' ? 'مثال: 12.5' : 'اكتب إجابتك هنا'} /></label>}
                </fieldset>
                <div className="mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-ink-100 pt-4">
                  <button onClick={() => setFlags(f => ({ ...f, [question.questionId]: !f[question.questionId] }))} aria-pressed={!!flags[question.questionId]} className={`inline-flex items-center gap-1 rounded-xl px-3 py-2 text-xs font-bold ${flags[question.questionId] ? 'bg-amber-100 text-amber-800' : 'bg-ink-100 text-ink-600'}`}><Flag size={15} />{flags[question.questionId] ? 'معلَّم للمراجعة' : 'علّم للمراجعة'}</button>
                  <div className="flex gap-2"><button className="btn-ghost" disabled={index === 0} onClick={() => go(index - 1)}><ChevronRight size={17} />السابق</button>{index < attempt.questions.length - 1 ? <button className="btn-primary" onClick={() => go(index + 1)}>التالي<ChevronLeft size={17} /></button> : <button className="btn-primary" onClick={() => setReview(true)}><Eye size={16} /> مراجعة وتسليم</button>}</div>
                </div>
              </div>}
            {left === 0 && !busy && !result && <button onClick={submit} className="btn-primary mt-4">إعادة تأكيد تسليم الإجابات المحفوظة</button>}
          </div>
        </main>
        <aside className={`${paletteOpen ? 'block' : 'hidden'} border-t border-ink-200 bg-white p-4 lg:block lg:border-r lg:border-t-0 lg:overflow-y-auto`}>
          <p className="mb-3 text-xs font-bold text-ink-500">خريطة الأسئلة</p>
          <div className="grid grid-cols-6 gap-2 lg:grid-cols-5" aria-label="التنقل بين الأسئلة">{attempt.questions.map((q, i) => <button key={q.questionId} aria-label={`سؤال ${i + 1}${answered(q) ? ' مجاب' : ' غير مجاب'}${flags[q.questionId] ? ' للمراجعة' : ''}`} aria-current={i === index && !review ? 'step' : undefined} onClick={() => go(i)} className={`relative h-10 rounded-xl border text-sm font-bold transition ${i === index && !review ? 'border-brand-600 bg-brand-600 text-white' : answered(q) ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-ink-200 bg-white text-ink-500'} ${flags[q.questionId] ? 'ring-2 ring-amber-400' : ''}`}>{i + 1}</button>)}</div>
          <div className="mt-4 space-y-1.5 text-[11px] text-ink-500"><p><span className="ml-1 inline-block h-3 w-3 rounded bg-emerald-200 align-middle" /> مجاب ({count})</p><p><span className="ml-1 inline-block h-3 w-3 rounded border border-ink-300 align-middle" /> بدون إجابة ({attempt.questions.length - count})</p><p><span className="ml-1 inline-block h-3 w-3 rounded ring-2 ring-amber-400 align-middle" /> للمراجعة ({flagged})</p></div>
          <button className="btn-primary mt-5 w-full" onClick={() => { setReview(true); setPaletteOpen(false) }}><Eye size={16} /> مراجعة وتسليم</button>
          <div className="mt-4 space-y-1 text-[11px] text-ink-400">{attempt.fullscreen && <p className="inline-flex items-center gap-1"><ShieldCheck size={12} /> ملء الشاشة إلزامي</p>}{attempt.disableCopy && <p className="inline-flex items-center gap-1"><ShieldCheck size={12} /> النسخ واللصق معطّلان</p>}{attempt.detectTabSwitch && <p className="inline-flex items-center gap-1"><ShieldCheck size={12} /> مغادرة الصفحة تُسجَّل</p>}</div>
        </aside>
      </div>
    </>}
  </div>
}

function Lobby({ exam, starting, agreed, setAgreed, onStart }) {
  const resume = exam.myStatus === 'IN_PROGRESS'
  return <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-8"><div className="mx-auto max-w-2xl">
    {starting ? <PageLoader /> : <div className="rounded-3xl border border-ink-200 bg-white p-6 sm:p-8">
      <span className="chip bg-brand-50 text-brand-700">{resume ? 'لديك محاولة جارية' : 'قبل ما تبدأ'}</span>
      <h3 className="mt-3 text-2xl font-black">{resume ? 'استكمل من حيث توقفت' : 'جهّز نفسك للامتحان'}</h3>
      <div className="my-6 grid grid-cols-3 gap-3 text-center">{[['المدة', exam.durationMinutes + ' دقيقة'], ['الأسئلة', exam.questionCount], ['الدرجة', exam.totalPoints]].map(([k, v]) => <div key={k} className="rounded-2xl bg-ink-50 p-4"><b className="text-xl font-black">{v}</b><p className="mt-1 text-xs text-ink-500">{k}</p></div>)}</div>
      {exam.description && <div className="mb-5 rounded-2xl bg-brand-50 p-4 text-sm leading-8 text-brand-900"><b className="block text-xs">تعليمات المدرس</b>{exam.description}</div>}
      <ul className="space-y-2 text-sm leading-7 text-ink-600">
        <li className="flex gap-2"><CheckCircle2 size={17} className="mt-1 shrink-0 text-emerald-600" /> الوقت يبدأ عند الضغط ويستمر لو خرجت من الصفحة. عند انتهائه تُعتمد آخر إجابات محفوظة على المنصة.</li>
        <li className="flex gap-2"><CheckCircle2 size={17} className="mt-1 shrink-0 text-emerald-600" /> إجاباتك تُحفظ تلقائياً كل ثانية بعد الكتابة، ويمكنك العودة واستكمال المحاولة قبل انتهاء الوقت.</li>
        {exam.fullscreen && <li className="flex gap-2"><ShieldCheck size={17} className="mt-1 shrink-0 text-amber-600" /> الامتحان يعمل بوضع ملء الشاشة. الخروج منه يُسجَّل ويظهر للمدرس.</li>}
        {exam.detectTabSwitch && <li className="flex gap-2"><ShieldCheck size={17} className="mt-1 shrink-0 text-amber-600" /> مغادرة صفحة الامتحان أو التبديل لنافذة أخرى يُسجَّل في تقرير المحاولة.</li>}
        {exam.disableCopy && <li className="flex gap-2"><ShieldCheck size={17} className="mt-1 shrink-0 text-amber-600" /> النسخ واللصق معطّلان داخل القاعة.</li>}
        <li className="flex gap-2"><Keyboard size={17} className="mt-1 shrink-0 text-ink-400" /> اختصارات: الأسهم للتنقل، الأرقام 1-9 لاختيار الإجابة، F لتعليم السؤال للمراجعة.</li>
      </ul>
      <label className="mt-6 flex cursor-pointer items-center gap-2 text-sm font-bold"><input type="checkbox" checked={agreed} onChange={e => setAgreed(e.target.checked)} /> قرأت التعليمات وأنا جاهز</label>
      <button className="btn-primary mt-4 w-full sm:w-auto" disabled={!agreed} onClick={onStart}>{resume ? 'استكمال المحاولة' : 'بدء الامتحان الآن'}</button>
    </div>}
  </div></div>
}

function Result({ result, exam, onClose, onReview }) {
  const pct = Math.round(result.percent)
  return <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-8"><div className="mx-auto max-w-xl rounded-3xl border border-ink-200 bg-white p-8 text-center">
    {result.needsManualGrade ? <Clock size={56} className="mx-auto text-amber-500" /> : result.passed ? <CheckCircle2 size={56} className="mx-auto text-emerald-600" /> : <XCircle size={56} className="mx-auto text-rose-500" />}
    <h3 className="mt-4 text-2xl font-black">{result.needsManualGrade ? 'تم التسليم · بانتظار التصحيح' : result.passed ? 'أحسنت! اجتزت الامتحان' : 'تم تسليم الامتحان'}</h3>
    <p className="mt-4 text-4xl font-black text-brand-700">{result.score} <span className="text-lg text-ink-400">/ {result.maxScore}</span></p>
    <p className="mt-1 text-sm font-bold text-ink-500">{pct}٪ · نسبة النجاح {exam.passPercent}٪</p>
    <p className="mt-4 text-sm leading-7 text-ink-500">{result.needsManualGrade ? 'الدرجة الحالية مبدئية للأسئلة الآلية فقط. ستكتمل بعد مراجعة المدرس للأسئلة المقالية.' : result.passed ? 'استمر على نفس المستوى. راجع الأسئلة التي أخطأت فيها لتثبيت المعلومة.' : 'راجع إجاباتك وملاحظات المدرس لتحديد ما يحتاج إلى تحسين.'}</p>
    <div className="mt-6 flex flex-wrap justify-center gap-2">{result.canReview && <button onClick={onReview} className="btn-primary"><Eye size={16} /> مراجعة إجاباتي</button>}<button onClick={onClose} className="btn-ghost">العودة للامتحانات</button></div>
  </div></div>
}
