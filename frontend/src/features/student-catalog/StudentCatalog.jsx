import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { BookOpen, CalendarClock, CheckCircle2, Clock3, Copy, GraduationCap, KeyRound, Lock, PlayCircle, RefreshCw, Smartphone, Sparkles, Wallet } from 'lucide-react'
import api from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { fmtMoney, SCHOOL_YEARS } from '../../lib/format'
import { distinctYears, forYear } from '../../lib/schoolYears'
import { fmtDay, monthsLabel, planLabel, planPrice } from '../../lib/subscriptions'
import { courseHref } from '../../lib/courseNavigation'
import { apiErrorMessage } from '../../lib/apiError'
import { Modal, PageLoader, Spinner, EmptyState, stagger, fadeUp } from '../../components/ui'

const priceOf = (c) => Number(c.finalPrice ?? c.price ?? 0)

/**
 * The student's courses with their current teacher (GET /me/catalog): their subscription to a year and subject (when
 * it started, when it ends), what they study, what waits for payment, and everything the teacher offers for their
 * school year — a course the teacher publishes later appears here by itself, and opens by itself while subscribed.
 * Free courses open on the spot; paid ones come with the year's subscription, which waits here with the teacher's
 * payment numbers until the teacher's code is entered (or the teacher activates it).
 */
export default function StudentCatalog({ compact = false }) {
  const { user, switchTeacher } = useAuth()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const focus = Number(params.get('focus')) || null
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [payFor, setPayFor] = useState(null)
  const [codeFor, setCodeFor] = useState(null)
  const [pickYear, setPickYear] = useState(false)
  const opened = useRef(false)

  const load = () => api.get('/me/catalog').then((r) => { setData(r.data); return r.data }).catch((e) => setError(apiErrorMessage(e, 'تعذّر تحميل كورساتك')))
  useEffect(() => { load() }, [])

  // Arriving from a course picked on the teacher's page: bring that course into view, and if it waits for payment,
  // show how to pay right away.
  useEffect(() => {
    if (!data || !focus || opened.current) return
    opened.current = true
    const c = data.courses.find((x) => x.id === focus)
    if (c?.state === 'PENDING') setPayFor({ course: c, plan: data.plans?.find((p) => p.id === c.planId) })
    setTimeout(() => document.getElementById(`course-${focus}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 250)
  }, [data, focus])

  if (error) return <div className="card p-6 text-sm text-rose-600" role="alert">{error}</div>
  if (!data) return compact ? <div className="card p-6"><Spinner className="h-5 w-5 border-brand-200 border-t-brand-600" /></div> : <PageLoader />

  const studying = data.courses.filter((c) => c.state === 'ACTIVE' || c.state === 'COMPLETED')
  const waiting = data.courses.filter((c) => c.state === 'PENDING')
  const offered = data.courses.filter((c) => c.state === 'NONE')
  const closed = data.courses.filter((c) => c.state === 'CLOSED')
  const expired = data.courses.filter((c) => c.state === 'EXPIRED')
  const shownOffered = compact ? offered.slice(0, 6) : offered
  const planOf = (c) => (c.planId ? data.plans?.find((p) => p.id === c.planId) : null)

  // A free course opens now; a paid one asks for its year's subscription and shows how to pay.
  const request = async (c) => {
    setBusyId(c.id)
    try {
      const { data: r } = await api.post(`/me/catalog/${c.id}/request`)
      const fresh = await load()
      if (r.state === 'ACTIVE') nav(courseHref(c, user.role))
      else if (r.state === 'PENDING') {
        const course = fresh?.courses.find((x) => x.id === c.id) || c
        setPayFor({ course, plan: fresh?.plans?.find((p) => p.id === course.planId) })
      }
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر الاشتراك'))
    } finally {
      setBusyId(null)
    }
  }

  // Subscribe to (or renew) a year's plan straight from its card.
  const subscribe = async (plan) => {
    setBusyId(`plan-${plan.id}`)
    try {
      await api.post(`/me/plans/${plan.id}/request`)
      const fresh = await load()
      setPayFor({ plan: fresh?.plans?.find((p) => p.id === plan.id) || plan })
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر إرسال الطلب'))
    } finally {
      setBusyId(null)
    }
  }

  const redeemed = async (result) => {
    setCodeFor(null); setPayFor(null)
    // A code from another teacher opened the course with them (the same account joined them); a package code
    // opens a whole package.
    if (result?.switchTo) return switchTeacher(result.switchTo, '/app/courses')
    if (result?.bundleId) return nav('/app/my-package')
    await load()
  }

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={fadeUp} className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-4 bg-gradient-to-l from-brand-700 to-brand-900 p-5 text-white sm:p-6">
          {data.teacher?.photoUrl
            ? <img src={data.teacher.photoUrl} alt="" className="h-14 w-14 rounded-2xl bg-white/10 object-cover object-top" />
            : <span className="grid h-14 w-14 place-items-center rounded-2xl bg-white/10"><GraduationCap size={26} /></span>}
          <div className="min-w-0 flex-1">
            <p className="text-xs text-brand-100">{data.teacher ? `كورساتك مع ${data.teacher.name}` : 'كورساتك'}</p>
            <h2 className="mt-0.5 text-xl font-black">{data.teacher?.subject || 'الكورسات'}</h2>
          </div>
          <button type="button" onClick={() => setPickYear(true)}
            className="basis-full rounded-2xl bg-white/10 px-4 py-2 text-right text-xs font-bold transition hover:bg-white/20 sm:basis-auto">
            <span className="block text-[11px] font-semibold text-brand-100">سنتك الدراسية</span>
            {data.grade || 'اختار سنتك'} <span className="text-brand-200">· تغيير</span>
          </button>
        </div>
        {!data.grade && (
          <p className="bg-amber-50 px-5 py-3 text-xs font-semibold leading-6 text-amber-800">
            اختار سنتك الدراسية عشان نعرضلك كورسات سنتك بس، وأول ما المدرس ينزّل حاجة جديدة لسنتك تظهرلك هنا.
          </p>
        )}
      </motion.div>

      {(data.plans || []).length > 0 && (
        <motion.div variants={fadeUp} className="grid gap-3 md:grid-cols-2">
          {data.plans.map((p) => <PlanCard key={p.id} p={p} busy={busyId === `plan-${p.id}`}
            onSubscribe={() => subscribe(p)} onPay={() => setPayFor({ plan: p })} onCode={() => setCodeFor({ title: planLabel(p) })} />)}
        </motion.div>
      )}

      {data.courses.length === 0 && (
        <motion.div variants={fadeUp} className="card">
          <EmptyState icon={BookOpen} title="مفيش كورسات لسنتك دلوقتي" hint="أول ما المدرس ينزّل كورس لسنتك هيظهرلك هنا على طول، وهيوصلك إشعار." />
        </motion.div>
      )}

      {waiting.length > 0 && (
        <Section title="مستني الدفع" hint="حوّل للمدرس، وبعدها اكتب الكود اللي هيبعتهولك — أو المدرس يفعّله من عنده." tone="amber">
          {waiting.map((c) => (
            <CourseCard key={c.id} c={c} plan={planOf(c)} focused={focus === c.id} badge={<Chip tone="amber"><Clock3 size={12} /> مستني الدفع</Chip>}>
              <button type="button" onClick={() => setPayFor({ course: c, plan: planOf(c) })} className="btn-primary w-full justify-center"><Wallet size={16} /> إزاي أدفع؟</button>
              <button type="button" onClick={() => setCodeFor(c)} className="btn-ghost w-full justify-center text-xs"><KeyRound size={14} /> معايا الكود</button>
            </CourseCard>
          ))}
        </Section>
      )}

      {studying.length > 0 && (
        <Section title="كورساتك" hint={`${studying.length} كورس مفتوح`}>
          {studying.map((c) => (
            <CourseCard key={c.id} c={c} owned focused={focus === c.id}
              badge={<Chip tone="emerald"><CheckCircle2 size={12} /> {c.state === 'COMPLETED' ? 'خلّصته' : 'مفتوح'}</Chip>}>
              <Link to={courseHref(c, user.role)} className="btn-primary w-full justify-center"><PlayCircle size={16} /> ادخل على الكورس</Link>
            </CourseCard>
          ))}
        </Section>
      )}

      {expired.length > 0 && (
        <Section title="اشتراكك خلص" hint="جدّد الاشتراك والكورسات دي تتفتح تاني بكل اللي فيها.">
          {expired.map((c) => (
            <CourseCard key={c.id} c={c} plan={planOf(c)} badge={<Chip><Lock size={12} /> مقفول</Chip>}>
              <button type="button" disabled={busyId === c.id} onClick={() => request(c)} className="btn-primary w-full justify-center">
                {busyId === c.id ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><RefreshCw size={15} /> جدّد الاشتراك</>}
              </button>
            </CourseCard>
          ))}
        </Section>
      )}

      {shownOffered.length > 0 && (
        <Section title={data.grade ? `متاح ${forYear(data.grade)}` : 'كورسات المدرس'} hint="اشتراك واحد بيفتح كل كورسات السنة، واللي المدرس ينزّله بعدين بيتفتح لوحده.">
          {shownOffered.map((c) => {
            const free = c.free || priceOf(c) <= 0
            return (
              <CourseCard key={c.id} c={c} plan={planOf(c)} focused={focus === c.id}
                badge={c.isNew ? <Chip tone="brand"><Sparkles size={12} /> جديد</Chip> : !c.year ? <Chip>لكل السنين</Chip> : null}>
                <button type="button" disabled={busyId === c.id} onClick={() => request(c)} className="btn-primary w-full justify-center">
                  {busyId === c.id ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : free ? 'ابدأ مجاناً' : `اشترك في ${c.year || data.grade || 'السنة'}`}
                </button>
                {!free && <button type="button" onClick={() => setCodeFor(c)} className="btn-ghost w-full justify-center text-xs"><KeyRound size={14} /> معايا كود اشتراك</button>}
              </CourseCard>
            )
          })}
          {compact && offered.length > shownOffered.length && (
            <Link to="/app/courses" className="card grid min-h-40 place-items-center p-6 text-sm font-bold text-brand-700 hover:shadow-glow">
              شوف الـ{offered.length} كورس ←
            </Link>
          )}
        </Section>
      )}

      {!compact && closed.length > 0 && (
        <Section title="موقوفة" hint="المدرس وقّف اشتراكك في الكورسات دي. لو ده غلط تواصل معاه.">
          {closed.map((c) => <CourseCard key={c.id} c={c} badge={<Chip><Lock size={12} /> موقوف</Chip>} />)}
        </Section>
      )}

      {payFor && <PayModal course={payFor.course} plan={payFor.plan} payment={data.payment} teacher={data.teacher} onClose={() => {
        setPayFor(null)
        if (focus) { params.delete('focus'); setParams(params, { replace: true }) }
      }} onHaveCode={() => { setCodeFor(payFor.course || { title: planLabel(payFor.plan) }); setPayFor(null) }} />}
      {codeFor && <CodeModal course={codeFor} onClose={() => setCodeFor(null)} onDone={redeemed} />}
      {pickYear && <YearModal current={data.grade} years={data.years} onClose={() => setPickYear(false)}
        onSaved={async () => { setPickYear(false); await load() }} />}
    </motion.div>
  )
}

/** One year-and-subject subscription: its dates while it runs, or how to subscribe, pay or renew. */
function PlanCard({ p, busy, onSubscribe, onPay, onCode }) {
  const running = p.status === 'ACTIVE'
  const waiting = p.status === 'PENDING' || p.renewalPending
  const tone = running ? 'border-emerald-200 bg-emerald-50/50' : waiting ? 'border-amber-200 bg-amber-50/50' : 'border-brand-100 bg-white'
  return (
    <div className={`card border p-4 ${tone}`}>
      <div className="flex items-start gap-3">
        <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${running ? 'bg-emerald-100 text-emerald-700' : 'bg-brand-50 text-brand-700'}`}><CalendarClock size={19} /></span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-bold text-ink-500">اشتراك {planLabel(p)}</p>
          {running ? (
            <>
              <p className="mt-1 text-sm font-extrabold text-ink-800">من {fmtDay(p.startsAt)} لحد {fmtDay(p.endsAt)}</p>
              <p className={`mt-1 text-xs font-bold ${p.daysLeft <= 7 ? 'text-amber-700' : 'text-emerald-700'}`}>باقي {p.daysLeft.toLocaleString('ar-EG')} يوم</p>
            </>
          ) : p.status === 'ENDED' || p.status === 'CANCELLED' ? (
            <p className="mt-1 text-sm font-extrabold text-ink-800">{p.status === 'CANCELLED' ? 'اتوقف' : 'خلص'} يوم {fmtDay(p.endsAt)}</p>
          ) : p.status === 'PENDING' ? (
            <p className="mt-1 text-sm font-extrabold text-amber-800">مستني الدفع</p>
          ) : (
            <p className="mt-1 text-sm font-extrabold text-ink-800">كل كورسات السنة لمدة {monthsLabel(p.months)}</p>
          )}
          <p className="mt-1 text-xs text-ink-500">{planPrice(p)}</p>
        </div>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {waiting ? (
          <>
            <button type="button" onClick={onPay} className="btn-primary flex-1 justify-center"><Wallet size={15} /> {p.renewalPending ? 'ادفع التجديد' : 'إزاي أدفع؟'}</button>
            <button type="button" onClick={onCode} className="btn-ghost"><KeyRound size={14} /> معايا الكود</button>
          </>
        ) : p.active !== false && (
          <button type="button" disabled={busy} onClick={onSubscribe} className={`${running ? 'btn-ghost' : 'btn-primary flex-1'} justify-center`}>
            {busy ? <Spinner className="h-4 w-4 border-current/40 border-t-current" /> : running ? <><RefreshCw size={14} /> جدّد بدري</> : p.status === 'NONE' ? 'اشترك' : <><RefreshCw size={14} /> جدّد الاشتراك</>}
          </button>
        )}
      </div>
    </div>
  )
}

function Section({ title, hint, tone, children }) {
  return (
    <motion.section variants={fadeUp}>
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className={`text-base font-extrabold ${tone === 'amber' ? 'text-amber-800' : 'text-ink-800'}`}>{title}</h3>
        {hint && <p className="text-xs text-ink-400">{hint}</p>}
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
    </motion.section>
  )
}

const TONES = {
  amber: 'bg-amber-50 text-amber-800', emerald: 'bg-emerald-50 text-emerald-700',
  brand: 'bg-brand-50 text-brand-700', default: 'bg-ink-100 text-ink-600',
}
function Chip({ tone = 'default', children }) {
  return <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold ${TONES[tone]}`}>{children}</span>
}

function CourseCard({ c, plan, owned, badge, focused, children }) {
  const price = priceOf(c)
  return (
    <div id={`course-${c.id}`}
      className={`card flex flex-col overflow-hidden transition ${focused ? 'ring-2 ring-brand-500 ring-offset-2' : ''}`}>
      <div className="relative h-32 overflow-hidden bg-gradient-to-br from-brand-600 to-brand-900">
        {c.coverUrl && <img src={c.coverUrl} alt="" loading="lazy" className="h-full w-full object-cover" />}
        <div className="absolute inset-x-3 top-3 flex items-start justify-between gap-2">
          {c.year ? <span className="rounded-full bg-white/90 px-2.5 py-1 text-[11px] font-bold text-ink-700">{c.year}</span> : <span />}
          {badge}
        </div>
      </div>
      <div className="flex flex-1 flex-col p-4">
        <p className="text-xs text-ink-400">{c.subject}</p>
        <p className="mt-1 font-extrabold leading-snug text-ink-800">{c.title}</p>
        {c.description && <p className="mt-1.5 line-clamp-2 text-xs leading-6 text-ink-500">{c.description}</p>}
        {owned ? null : price > 0 ? (
          <p className="mt-3 text-xs text-ink-500">ضمن اشتراك {plan ? planLabel(plan) : (c.year || 'السنة')}: <b className="text-brand-700">{plan ? planPrice(plan) : 'السعر عند المدرس'}</b></p>
        ) : (
          <div className="mt-3 flex items-baseline gap-2">
            <b className={price > 0 ? 'text-brand-700' : 'text-emerald-600'}>{price > 0 ? fmtMoney(price) : 'مجاني'}</b>
            {c.discountPercent > 0 && <del className="text-xs text-ink-400">{fmtMoney(c.price)}</del>}
          </div>
        )}
        {children && <div className="mt-auto space-y-1.5 pt-4">{children}</div>}
      </div>
    </div>
  )
}

function PayModal({ course, plan, payment, teacher, onClose, onHaveCode }) {
  const [copied, setCopied] = useState('')
  const copy = (text, key) => navigator.clipboard?.writeText(text).then(() => { setCopied(key); setTimeout(() => setCopied(''), 1500) })
  return (
    <Modal open onClose={onClose} title={plan ? 'طلب الاشتراك اتبعت — فاضل الدفع' : 'الكورس اتضاف لحسابك — فاضل الدفع'}>
      <div className="space-y-4">
        <div className="rounded-2xl bg-brand-50 p-4">
          <p className="font-extrabold text-ink-800">{plan ? `اشتراك ${planLabel(plan)}` : course.title}</p>
          <p className="mt-1 text-2xl font-black text-brand-700">{plan ? (plan.finalPrice != null ? fmtMoney(plan.finalPrice) : 'السعر عند المدرس') : fmtMoney(priceOf(course))}</p>
          {plan && <p className="mt-1 text-xs leading-6 text-ink-600">لمدة {monthsLabel(plan.months)} — بيفتحلك كل كورسات السنة{course ? `، ومنها «${course.title}»` : ''}، واللي المدرس هينزّله بعدين كمان.</p>}
        </div>
        {payment ? (
          <div className="space-y-2">
            <p className="text-sm font-bold text-ink-700">حوّل المبلغ على:</p>
            {payment.instapayNumber && (
              <button type="button" onClick={() => copy(payment.instapayNumber, 'ip')} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-sky-50 p-3.5 text-right">
                <span className="flex items-center gap-2 text-sm font-bold text-sky-800"><Smartphone size={17} /> إنستاباي</span>
                <span dir="ltr" className="flex items-center gap-2 font-mono text-sm text-sky-900">{payment.instapayNumber} <Copy size={14} />{copied === 'ip' && <span className="text-[11px]">تم النسخ</span>}</span>
              </button>
            )}
            {payment.vodafoneCashNumber && (
              <button type="button" onClick={() => copy(payment.vodafoneCashNumber, 'vf')} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-rose-50 p-3.5 text-right">
                <span className="flex items-center gap-2 text-sm font-bold text-rose-800"><Wallet size={17} /> فودافون كاش</span>
                <span dir="ltr" className="flex items-center gap-2 font-mono text-sm text-rose-900">{payment.vodafoneCashNumber} <Copy size={14} />{copied === 'vf' && <span className="text-[11px]">تم النسخ</span>}</span>
              </button>
            )}
            {payment.note && <p className="text-xs leading-6 text-ink-500">{payment.note}</p>}
          </div>
        ) : (
          <p className="rounded-2xl bg-ink-50 p-4 text-sm leading-7 text-ink-600">
            {teacher ? `${teacher.name} لسه ما حددش طريقة دفع هنا.` : 'المدرس لسه ما حددش طريقة دفع هنا.'} تواصل معاه عشان تدفع ويبعتلك كود الاشتراك.
          </p>
        )}
        <ol className="space-y-1.5 rounded-2xl border border-ink-100 p-4 text-xs leading-6 text-ink-600">
          <li>١. حوّل المبلغ وابعت صورة الإيصال للمدرس.</li>
          <li>٢. المدرس هيبعتلك <b>كود اشتراك</b> — اكتبه هنا و{plan ? 'الاشتراك يبدأ' : 'الكورس يتفتح'} على طول.</li>
          <li>٣. أو المدرس يفعّله من عنده، ويوصلك إشعار.</li>
        </ol>
        <div className="flex gap-2">
          <button type="button" onClick={onHaveCode} className="btn-primary flex-1 justify-center"><KeyRound size={16} /> معايا الكود</button>
          <button type="button" onClick={onClose} className="btn-ghost">بعدين</button>
        </div>
      </div>
    </Modal>
  )
}

function CodeModal({ course, onClose, onDone }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (e) => {
    e.preventDefault()
    if (!code.trim()) return setError('اكتب الكود اللي استلمته من المدرس')
    setBusy(true); setError('')
    try {
      const { data } = await api.post('/courses/redeem-code', { code: code.trim() })
      await onDone(data)
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر تفعيل الكود، راجعه وجرّب تاني'))
      setBusy(false)
    }
  }
  return (
    <Modal open onClose={onClose} title={`تفعيل كود — ${course.title}`}>
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm leading-7 text-ink-500">اكتب الكود اللي المدرس بعتهولك بعد ما أكّد التحويل.</p>
        <div className="relative"><KeyRound size={18} className="absolute right-3.5 top-3 text-brand-500" />
          <input dir="ltr" autoFocus className="input pr-11 text-center font-mono tracking-widest" value={code} onChange={(e) => setCode(e.target.value)} placeholder="XXXX-XXXX" />
        </div>
        {error && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center py-3">
          {busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : 'فعّل الكورس'}
        </button>
      </form>
    </Modal>
  )
}

function YearModal({ current, years, onClose, onSaved }) {
  const options = distinctYears([current, ...(years || []), ...SCHOOL_YEARS])
  const [grade, setGrade] = useState(current || '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const save = async () => {
    if (!grade) return setError('اختار سنتك')
    setBusy(true); setError('')
    try { await api.put('/me/catalog/grade', { grade }); await onSaved() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ السنة')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title="سنتك الدراسية">
      <div className="space-y-4">
        <p className="text-sm leading-7 text-ink-500">بنعرضلك كورسات سنتك بس، والكورسات اللي إنت مشترك فيها بتفضل معاك. السنة بتتغيّر عند كل مدرسينك.</p>
        <select className="input" value={grade} onChange={(e) => setGrade(e.target.value)}>
          <option value="">— اختار —</option>
          {options.map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="button" disabled={busy} onClick={save} className="btn-primary w-full justify-center">
          {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ'}
        </button>
      </div>
    </Modal>
  )
}
