import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  ArrowLeft, BookMarked, CalendarCheck, CheckCircle2, Clock, CreditCard, GraduationCap, HandCoins, MapPin, ScanLine, Users, Wallet,
} from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { CountUp, PageLoader, ProgressBar, fadeUp, stagger } from '../../components/ui'
import { fmtDay, money, num } from './centerUtils'

/** The center's home: today's groups and how full they are, today's money, and the next books coming out. */
export default function CenterHome() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    api.get('/center/overview').then((r) => setData(r.data)).catch((e) => setError(apiErrorMessage(e, 'تعذّر تحميل لوحة السنتر')))
  }, [])
  if (error) return <p role="alert" className="card p-6 text-sm font-bold text-rose-700">{error}</p>
  if (!data) return <PageLoader />

  const empty = data.teachers === 0
  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={fadeUp} className="relative overflow-hidden rounded-3xl bg-gradient-to-l from-[#0b2e47] via-[#0a2335] to-[#05111c] p-6 text-white sm:p-8">
        <div className="pointer-events-none absolute -left-16 -top-20 h-64 w-64 rounded-full bg-[#0e7490]/40 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-24 left-1/3 h-56 w-56 rounded-full bg-[#0369a1]/40 blur-3xl" />
        <div className="relative flex flex-wrap items-end justify-between gap-5">
          <div>
            <span className="chip bg-white/10 text-sky-100"><CalendarCheck size={14} /> {fmtDay(data.date)}</span>
            <h2 className="mt-3 text-2xl font-black sm:text-3xl">{data.centerName}</h2>
            <p className="mt-1 text-sm text-sky-100/80">
              {data.today.length ? `عندك النهارده ${num(data.today.length)} ${data.today.length === 1 ? 'مجموعة' : 'مجموعات'}` : 'مفيش مجموعات ميعادها النهارده'}
            </p>
          </div>
          <Link to="/app/center/scan" className="group inline-flex items-center gap-3 rounded-2xl bg-white px-5 py-3.5 font-black text-[#0b2e47] shadow-glow transition hover:-translate-y-0.5">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-[#0284c7] to-[#0e7490] text-white animate-pulse-ring"><ScanLine size={20} /></span>
            ابدأ مسح الكارتات
            <ArrowLeft size={18} className="transition group-hover:-translate-x-1" />
          </Link>
        </div>
      </motion.div>

      {empty ? <Onboarding /> : (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Kpi icon={CheckCircle2} label="حضروا النهارده" value={data.presentToday} tint="bg-emerald-50 text-emerald-600" />
            <Kpi icon={HandCoins} label="اتحصّل النهارده" money={data.collectedToday} tint="bg-sky-50 text-sky-600" />
            <Kpi icon={Wallet} label="لسه متدفعش النهارده" money={data.outstandingToday} tint="bg-amber-50 text-amber-600" />
            <Kpi icon={Users} label="طلاب السنتر" value={data.students} tint="bg-teal-50 text-teal-600"
              hint={`${num(data.teachers)} مدرس · ${num(data.groups)} مجموعة`} />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.6fr_1fr]">
            <motion.section variants={fadeUp} className="card p-5 sm:p-6">
              <div className="flex items-center justify-between gap-3">
                <h3 className="flex items-center gap-2 text-base font-extrabold text-ink-800"><Clock size={18} className="text-brand-600" /> حصص النهارده</h3>
                <Link to="/app/center/attendance" className="text-xs font-bold text-brand-700">كشف الحضور ←</Link>
              </div>
              {data.today.length === 0 ? (
                <p className="mt-4 rounded-2xl bg-ink-50 p-5 text-center text-sm text-ink-500">مفيش مجموعات ميعادها النهارده. أي كارت يتمسح هيتسجل حضور استثنائي لمجموعته.</p>
              ) : (
                <div className="mt-4 space-y-3">
                  {data.today.map((g) => {
                    const pct = g.students ? Math.round((g.present / g.students) * 100) : 0
                    return (
                      <Link key={g.groupId} to={`/app/center/attendance?group=${g.groupId}`}
                        className="block rounded-2xl border border-ink-100 p-4 transition hover:border-brand-200 hover:bg-brand-50/40">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="min-w-0">
                            <p className="truncate font-extrabold text-ink-800">{g.teacherName} <span className="font-semibold text-ink-400">· {g.subject}</span></p>
                            <p className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-ink-500">
                              <span className="inline-flex items-center gap-1"><Clock size={12} /> {g.timeLabel}</span>
                              {g.room && <span className="inline-flex items-center gap-1"><MapPin size={12} /> {g.room}</span>}
                              <span>{g.groupName}</span>
                            </p>
                          </div>
                          <div className="text-left">
                            <p className="text-lg font-black text-ink-800">{num(g.present)}<span className="text-sm font-bold text-ink-400"> / {num(g.students)}</span></p>
                            <p className="text-[11px] font-bold text-emerald-700">{money(g.collected)}</p>
                          </div>
                        </div>
                        <ProgressBar value={pct} className="mt-3" color={pct >= 80 ? 'bg-emerald-500' : pct >= 40 ? 'bg-brand-500' : 'bg-amber-500'} />
                      </Link>
                    )
                  })}
                </div>
              )}
            </motion.section>

            <div className="space-y-6">
              <motion.section variants={fadeUp} className="card p-5 sm:p-6">
                <div className="flex items-center justify-between">
                  <h3 className="flex items-center gap-2 text-base font-extrabold text-ink-800"><BookMarked size={18} className="text-brand-600" /> الكتب اللي نازلة</h3>
                  <Link to="/app/center/books" className="text-xs font-bold text-brand-700">كل الكتب ←</Link>
                </div>
                {data.upcomingBooks.length === 0 ? (
                  <p className="mt-4 rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">مفيش كتب نازلة قريب.</p>
                ) : (
                  <ul className="mt-4 divide-y divide-ink-100">
                    {data.upcomingBooks.map((b) => (
                      <li key={b.id} className="flex items-center justify-between gap-3 py-3">
                        <div className="min-w-0">
                          <p className="truncate font-bold text-ink-800">{b.title}</p>
                          <p className="text-xs text-ink-400">{b.teacherName || 'لكل الطلاب'} · {fmtDay(b.releaseDate, false)}</p>
                        </div>
                        <span className="chip shrink-0 bg-sky-50 text-sky-700">{num(b.reserved)} حجز</span>
                      </li>
                    ))}
                  </ul>
                )}
                {data.openReservations > 0 && (
                  <p className="mt-3 rounded-xl bg-amber-50 p-3 text-xs font-bold text-amber-800">{num(data.openReservations)} حجز مستني الاستلام</p>
                )}
              </motion.section>

              <motion.section variants={fadeUp} className="card grid grid-cols-2 gap-3 p-5">
                {[
                  ['/app/center/students', Users, 'ضيف طلاب'],
                  ['/app/center/cards', CreditCard, 'اطبع كارتات'],
                  ['/app/center/teachers', GraduationCap, 'المدرسين'],
                  ['/app/center/accounts', Wallet, 'الحسابات'],
                ].map(([to, Icon, label]) => (
                  <Link key={to} to={to} className="flex items-center gap-2 rounded-2xl border border-ink-100 p-3 text-sm font-bold text-ink-700 transition hover:border-brand-200 hover:text-brand-700">
                    <Icon size={17} className="text-brand-600" /> {label}
                  </Link>
                ))}
              </motion.section>
            </div>
          </div>
        </>
      )}
    </motion.div>
  )
}

function Kpi({ icon: Icon, label, value, money: amount, tint, hint }) {
  return (
    <motion.div variants={fadeUp} className="card p-5">
      <div className={`grid h-11 w-11 place-items-center rounded-2xl ${tint}`}><Icon size={21} /></div>
      <p className="mt-4 text-2xl font-black text-ink-800">{amount !== undefined ? money(amount) : <CountUp value={value} />}</p>
      <p className="mt-1 text-sm font-semibold text-ink-400">{label}</p>
      {hint && <p className="mt-1 text-[11px] text-ink-400">{hint}</p>}
    </motion.div>
  )
}

function Onboarding() {
  const steps = [
    ['/app/center/teachers', GraduationCap, 'ضيف المدرسين', 'اسم المدرس ومادته ونسبة السنتر من الحصة.'],
    ['/app/center/teachers', Clock, 'اعمل المجموعات', 'لكل مدرس: الأيام والساعة وسعر الحصة والقاعة.'],
    ['/app/center/students', Users, 'اكتب أسماء الطلاب', 'اسم في كل سطر — كل طالب بياخد كود وQR لوحده.'],
    ['/app/center/cards', CreditCard, 'اطبع الكارتات', 'الكارت عليه المدرس والمادة والمعاد واسم السنتر.'],
    ['/app/center/scan', ScanLine, 'امسح عند الباب', 'بالموبايل أو قارئ الباركود — الحضور والفلوس بيتسجلوا لوحدهم.'],
  ]
  return (
    <motion.div variants={fadeUp} className="card p-6 sm:p-8">
      <h3 className="text-lg font-black text-ink-800">يلا نجهّز السنتر في ٥ خطوات</h3>
      <p className="mt-1 text-sm text-ink-500">ابدأ بالمدرسين، والباقي هيمشي ورا بعضه.</p>
      <ol className="mt-6 grid gap-4 md:grid-cols-5">
        {steps.map(([to, Icon, title, text], i) => (
          <li key={title}>
            <Link to={to} className="group block h-full rounded-2xl border border-ink-100 p-4 transition hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft">
              <span className="flex items-center justify-between">
                <span className="grid h-10 w-10 place-items-center rounded-xl bg-brand-50 text-brand-700"><Icon size={19} /></span>
                <span className="text-2xl font-black text-ink-200">{(i + 1).toLocaleString('ar-EG')}</span>
              </span>
              <p className="mt-3 font-extrabold text-ink-800 group-hover:text-brand-700">{title}</p>
              <p className="mt-1 text-xs leading-6 text-ink-500">{text}</p>
            </Link>
          </li>
        ))}
      </ol>
    </motion.div>
  )
}
