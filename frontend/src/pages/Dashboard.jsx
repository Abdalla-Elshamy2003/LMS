import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { PieChart, Pie, Cell, ResponsiveContainer, BarChart, Bar, XAxis, Tooltip, CartesianGrid } from 'recharts'
import { Users, GraduationCap, BookOpen, TrendingUp, Wallet, AlertTriangle, Trophy, ArrowLeft, Download, CalendarCheck, Award, ClipboardList, FileQuestion, Paperclip, UserRound, LifeBuoy, HeartHandshake } from 'lucide-react'
import api, { fileUrl } from '../lib/api'
import { useAuth } from '../lib/auth'
import { CountUp, ProgressBar, ProgressRing, Avatar, Modal, PageLoader, EmptyState, Spinner, stagger, fadeUp } from '../components/ui'
import { fmtMoney, fmtDate, ACADEMIC_STATUS } from '../lib/format'
import { exportElementToPdf } from '../lib/pdf'
import ParentReportPrint from '../components/reports/ParentReportPrint'
import LearningHub from '../components/LearningHub'
import { SchedulePreview } from './Schedule'

const HW_STATUS = {
  PENDING: { label: 'لم يُسلَّم بعد', c: 'bg-ink-100 text-ink-500' },
  SUBMITTED: { label: 'بانتظار التصحيح', c: 'bg-sky-50 text-sky-700' },
  LATE: { label: 'مُسلَّم متأخراً', c: 'bg-amber-50 text-amber-700' },
  GRADED: { label: 'تم التصحيح', c: 'bg-emerald-50 text-emerald-700' },
  MISSING: { label: 'غائب', c: 'bg-rose-50 text-rose-700' },
}

const DIST_COLORS = { EXCELLENT: '#10b981', GOOD: '#0ea5e9', AVERAGE: '#f59e0b', NEEDS_ATTENTION: '#f97316', AT_RISK: '#f43f5e' }
const ADMIN = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'ACCOUNTANT']

function TabBtn({ active, onClick, icon: Icon, children }) {
  return <button onClick={onClick} className={`chip border ${active ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}><Icon size={15} /> {children}</button>
}

function Kpi({ icon: Icon, label, value, money, suffix, tint }) {
  return (
    <motion.div variants={fadeUp} className="card p-5">
      <div className="flex items-center justify-between">
        <div className={`grid h-12 w-12 place-items-center rounded-2xl ${tint}`}><Icon size={22} /></div>
      </div>
      <p className="mt-4 text-3xl font-black text-ink-800">
        {money ? fmtMoney(value) : <CountUp value={value} suffix={suffix || ''} />}
      </p>
      <p className="mt-1 text-sm font-semibold text-ink-400">{label}</p>
    </motion.div>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    const r = user.role
    const url = ADMIN.includes(r) ? '/dashboard/admin'
      : (r === 'TEACHER' || r === 'ASSISTANT') ? '/dashboard/teacher'
      : r === 'STUDENT' ? '/dashboard/me'
      : r === 'PARENT' ? '/dashboard/parent' : null
    if (!url) { setLoading(false); return }
    api.get(url).then((res) => setData(res.data)).catch(() => setError(true)).finally(() => setLoading(false))
  }, [user.role])

  if (loading) return <PageLoader />
  if (error) return <div className="card p-6" role="alert">تعذّر تحميل لوحة التحكم. <button className="btn-soft" onClick={() => window.location.reload()}>إعادة المحاولة</button></div>

  if (user.role === 'STUDENT') return <StudentDashboard user={user} data={data} />
  if (user.role === 'PARENT') return <ParentDashboard user={user} data={data} />
  if (['TEACHER', 'ASSISTANT'].includes(user.role)) return <TeacherDashboard data={data} />
  if (user.role === 'CONTENT_MANAGER') return <LearningHub />
  if (!ADMIN.includes(user.role)) return <WelcomeCard user={user} />

  const k = data.kpis
  const dist = Object.entries(data.academicDistribution).map(([key, value]) => ({
    key, name: ACADEMIC_STATUS[key]?.label || key, value, color: DIST_COLORS[key],
  }))

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      {user.role !== 'ACCOUNTANT' && <LearningHub compact />}
      {user.role !== 'ACCOUNTANT' && <SchedulePreview />}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        <Kpi icon={Users} label="إجمالي الطلاب" value={k.students} tint="bg-brand-50 text-brand-600" />
        <Kpi icon={GraduationCap} label="المدرسون" value={k.teachers} tint="bg-violet-50 text-violet-600" />
        <Kpi icon={BookOpen} label="الكورسات" value={k.courses} tint="bg-sky-50 text-sky-600" />
        <Kpi icon={TrendingUp} label="متوسط الأداء" value={k.avgOverall} suffix="٪" tint="bg-emerald-50 text-emerald-600" />
        <Kpi icon={Wallet} label="المحصّل" value={k.collected} money tint="bg-teal-50 text-teal-600" />
        <Kpi icon={AlertTriangle} label="المتأخرات" value={k.outstanding} money tint="bg-rose-50 text-rose-600" />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Distribution donut */}
        <motion.div variants={fadeUp} className="card p-6">
          <h3 className="text-base font-extrabold text-ink-800">التوزيع الأكاديمي</h3>
          <p className="text-xs text-ink-400 mb-2">مستويات الطلاب حسب الأداء</p>
          <div className="relative h-52">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={dist} dataKey="value" nameKey="name" innerRadius={55} outerRadius={82} paddingAngle={3} stroke="none">
                  {dist.map((d) => <Cell key={d.key} fill={d.color} />)}
                </Pie>
                <Tooltip contentStyle={{ borderRadius: 16, border: 'none', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', fontFamily: 'Cairo' }} />
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-3xl font-black text-ink-800"><CountUp value={k.students} /></span>
              <span className="text-xs text-ink-400">طالب</span>
            </div>
          </div>
          <div className="mt-3 grid grid-cols-2 gap-2">
            {dist.map((d) => (
              <div key={d.key} className="flex items-center gap-2 text-xs">
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: d.color }} />
                <span className="text-ink-600 font-semibold">{d.name}</span>
                <span className="text-ink-400 mr-auto">{d.value}</span>
              </div>
            ))}
          </div>
        </motion.div>

        {/* Top students */}
        <motion.div variants={fadeUp} className="card p-6">
          <div className="mb-4 flex items-center gap-2">
            <Trophy size={18} className="text-amber-500" />
            <h3 className="text-base font-extrabold text-ink-800">الأعلى أداءً</h3>
          </div>
          <div className="space-y-3.5">
            {data.topStudents.slice(0, 5).map((s, i) => (
              <Link to={`/app/students/${s.id}`} key={s.id} className="flex items-center gap-3 group">
                <span className={`grid h-7 w-7 shrink-0 place-items-center rounded-lg text-xs font-black ${i === 0 ? 'bg-amber-100 text-amber-700' : 'bg-ink-100 text-ink-500'}`}>{i + 1}</span>
                <Avatar name={s.name} size={34} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-ink-700 group-hover:text-brand-600">{s.name}</p>
                  <ProgressBar value={s.overallPercent} className="mt-1.5" />
                </div>
                <span className="text-sm font-extrabold text-ink-700">{Math.round(s.overallPercent)}٪</span>
              </Link>
            ))}
          </div>
        </motion.div>

        {/* At risk */}
        <motion.div variants={fadeUp} className="card p-6">
          <div className="mb-4 flex items-center gap-2">
            <AlertTriangle size={18} className="text-rose-500" />
            <h3 className="text-base font-extrabold text-ink-800">طلاب بحاجة لمتابعة</h3>
          </div>
          {data.atRiskStudents.length === 0 ? (
            <EmptyState icon={TrendingUp} title="لا يوجد طلاب متعثرون" hint="جميع الطلاب في مستوى جيد" />
          ) : (
            <div className="space-y-2.5">
              {data.atRiskStudents.slice(0, 6).map((s) => {
                const st = ACADEMIC_STATUS[s.academicStatus] || {}
                return (
                  <Link to={`/app/students/${s.id}`} key={s.id} className="flex items-center gap-3 rounded-2xl p-2 hover:bg-ink-50 transition">
                    <Avatar name={s.name} size={34} color="from-rose-400 to-rose-600" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-ink-700">{s.name}</p>
                      <p className="text-xs text-ink-400">حضور {Math.round(s.attendanceRate)}٪ · معدل {Math.round(s.overallPercent)}٪</p>
                    </div>
                    <span className={`chip border ${st.color}`}>{st.label}</span>
                  </Link>
                )
              })}
            </div>
          )}
        </motion.div>
      </div>
    </motion.div>
  )
}

function TeacherDashboard({ data }) {
  const { user } = useAuth()
  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      {user.role === 'TEACHER' && <LearningHub compact />}
      <SchedulePreview />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi icon={BookOpen} label="كورساتي" value={data.courses} tint="bg-brand-50 text-brand-600" />
        <Kpi icon={Users} label="طلابي" value={data.students} tint="bg-emerald-50 text-emerald-600" />
      </div>
      <motion.div variants={fadeUp} className="card p-6">
        <h3 className="mb-4 text-base font-extrabold text-ink-800">كورساتي</h3>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(data.myCourses || []).map((c) => (
            <Link to={`/app/courses/${c.id}`} key={c.id} className="rounded-2xl border border-ink-100 p-4 hover:border-brand-300 hover:bg-brand-50/40 transition">
              <p className="font-bold text-ink-800">{c.title}</p>
              <p className="mt-1 text-sm text-ink-400">{c.students} طالب</p>
            </Link>
          ))}
        </div>
      </motion.div>
    </motion.div>
  )
}

function StudentDashboard({ user, data }) {
  const d = data?.dashboard || {}
  const g = data?.gamification || {}
  const [enrollments, setEnrollments] = useState(null)
  const LEVELS = { DIAMOND: 'ماسي', PLATINUM: 'بلاتيني', GOLD: 'ذهبي', SILVER: 'فضي', BRONZE: 'برونزي' }

  useEffect(() => { api.get('/students/me').then((r) => setEnrollments(r.data.enrollments)).catch(() => setEnrollments([])) }, [])

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <LearningHub compact />
      <SchedulePreview />
      <motion.div variants={fadeUp} className="card overflow-hidden">
        <div className="relative overflow-hidden bg-gradient-to-l from-brand-600 to-brand-800 p-7 text-white">
          <motion.div aria-hidden animate={{ y: [0, -10, 0], rotate: [0, 5, 0] }} transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }} className="absolute -bottom-7 left-8 grid h-28 w-28 place-items-center rounded-[32px] bg-white/10 text-white/30"><GraduationCap size={54} /></motion.div>
          <div className="relative"><h2 className="text-2xl font-black">أهلاً {user.fullName} 👋</h2>
          <p className="mt-1 text-brand-100">ترتيبك {d.rank} من {d.totalStudents} · {g.points || 0} نقطة ({LEVELS[g.level] || 'برونزي'})</p></div>
        </div>
        <div className="grid grid-cols-2 gap-4 p-6 sm:grid-cols-4">
          <RingCard label="المعدل العام" value={d.overallPercent || 0} color="#0284c7" />
          <RingCard label="نسبة الحضور" value={d.attendanceRate || 0} color="#10b981" />
          <RingCard label="الواجبات" value={d.homeworkRate || 0} color="#f59e0b" />
          <RingCard label="متوسط الدرجات" value={d.avgScore || 0} color="#8b5cf6" />
        </div>
      </motion.div>

      <motion.div variants={fadeUp} className="flex flex-wrap gap-3">
        <Link to="/app/courses" className="btn-soft"><BookOpen size={16} /> الكورسات</Link>
        <Link to="/app/academic-profile" className="btn-soft"><UserRound size={16} /> ملفي ودرجاتي</Link>
        <Link to="/app/schedule" className="btn-soft"><CalendarCheck size={16} /> جدولي الدراسي</Link>
        <Link to="/app/exams" className="btn-soft"><FileQuestion size={16} /> الامتحانات</Link>
        <Link to="/app/homework" className="btn-soft"><ClipboardList size={16} /> الواجبات</Link>
        <Link to="/app/certificates" className="btn-soft"><Award size={16} /> شهاداتي</Link>
        <Link to="/app/leaderboard" className="btn-soft"><Trophy size={16} /> لوحة الشرف</Link>
        <Link to="/app/support" className="btn-soft"><LifeBuoy size={16} /> اسأل مدرسك أو الإدارة</Link>
      </motion.div>

      <motion.div variants={fadeUp} className="card p-6">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-extrabold text-ink-800">كورساتي</h3>
          <Link to="/app/courses" className="text-sm font-semibold text-brand-600">تصفّح كل الكورسات ←</Link>
        </div>
        {!enrollments ? <Spinner className="h-5 w-5 border-brand-200 border-t-brand-600" /> : enrollments.length === 0 ? (
          <EmptyState icon={BookOpen} title="لست ملتحقاً بأي كورس بعد" hint="تصفّح الكورسات المتاحة والتحق بأول كورس لك" />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {enrollments.map((e) => (
              <Link key={e.id} to={`/app/courses/${e.courseId}`}
                className="rounded-2xl border border-ink-100 p-4 transition hover:border-brand-300 hover:bg-brand-50/40">
                <p className="font-bold text-ink-800">{e.courseTitle}</p>
                <span className="mt-1 inline-block chip bg-brand-50 text-brand-700">{e.status === 'ACTIVE' ? 'نشط' : e.status}</span>
              </Link>
            ))}
          </div>
        )}
      </motion.div>

      {/* Everything offered for the year this student signed up for. */}
      {(d.yearCourses || []).length > 0 && (
        <motion.div variants={fadeUp} className="card p-5 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-lg font-extrabold text-ink-800">كورسات سنتك الدراسية</h3>
              <p className="mt-1 text-xs text-ink-400">{d.grade}</p>
            </div>
            <span className="chip bg-brand-50 text-brand-700">{d.yearCourses.length} كورس</span>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {d.yearCourses.map((c) => (
              <Link key={c.id} to={c.enrolled ? `/app/courses/${c.id}` : '/app/courses'}
                className="group overflow-hidden rounded-2xl border border-ink-100 transition hover:-translate-y-1 hover:border-brand-300 hover:shadow-glow">
                <div className="relative h-28 overflow-hidden bg-ink-50">
                  {c.coverUrl && <img src={c.coverUrl} alt="" className="h-full w-full object-cover transition duration-500 group-hover:scale-110" />}
                  {c.discountPercent > 0 && (
                    <span className="absolute right-2 top-2 rounded-full bg-rose-500 px-2 py-0.5 text-[10px] font-bold text-white">
                      خصم {c.discountPercent}%
                    </span>
                  )}
                </div>
                <div className="p-4">
                  <p className="text-xs text-ink-400">{c.subject}</p>
                  <p className="mt-1 line-clamp-2 text-sm font-bold text-ink-800">{c.title}</p>
                  <div className="mt-3 flex items-baseline justify-between gap-2">
                    <span className="flex items-baseline gap-1.5">
                      <b className={c.discountPercent > 0 ? 'text-rose-600' : 'text-brand-600'}>{fmtMoney(c.finalPrice)}</b>
                      {c.discountPercent > 0 && <del className="text-[10px] text-ink-400">{fmtMoney(c.price)}</del>}
                    </span>
                    {c.enrolled && <span className="chip bg-emerald-50 text-[10px] text-emerald-700">ملتحق</span>}
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </motion.div>
      )}
    </motion.div>
  )
}

function ParentDashboard({ user, data }) {
  const children = data?.children || []
  const printRefs = useRef({})
  const [exportingId, setExportingId] = useState(null)
  const [details, setDetails] = useState({})
  const [active, setActive] = useState(null)

  useEffect(() => {
    children.forEach((c) => {
      if (details[c.id]) return
      Promise.all([
        api.get(`/students/${c.id}`),
        api.get(`/exams/student/${c.id}`),
        api.get(`/homework/student/${c.id}`),
      ]).then(([sRes, eRes, hRes]) => {
        setDetails((d) => ({ ...d, [c.id]: {
          enrollments: sRes.data.enrollments || [],
          exams: eRes.data || [],
          homework: hRes.data || [],
        } }))
      }).catch(() => {})
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [children.length])

  const exportChild = async (child) => {
    setExportingId(child.id)
    try {
      await exportElementToPdf(printRefs.current[child.id], `تقرير-${child.fullName}.pdf`)
    } finally {
      setExportingId(null)
    }
  }

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={fadeUp} className="card relative overflow-hidden bg-gradient-to-l from-brand-600 to-brand-800 p-7 text-white">
        <motion.div aria-hidden animate={{ y: [0, -9, 0], rotate: [0, -4, 0] }} transition={{ duration: 5.5, repeat: Infinity, ease: 'easeInOut' }} className="absolute -bottom-8 left-8 grid h-28 w-28 place-items-center rounded-full bg-white/10 text-white/30"><HeartHandshake size={52} /></motion.div>
        <div className="relative"><h2 className="text-2xl font-black">أهلاً {user.fullName} 👋</h2>
        <p className="mt-1 text-brand-100">متابعة أبنائك ({children.length}) من الدراسة وحتى التواصل مع المدرس</p>
        <div className="mt-4 flex flex-wrap gap-2"><Link to="/app/schedule" className="rounded-xl bg-white/15 px-4 py-2 text-xs font-bold text-white backdrop-blur transition hover:bg-white/25"><CalendarCheck size={15} className="ml-1 inline" />جداول الأبناء</Link><Link to="/app/family/finance" className="rounded-xl bg-white/15 px-4 py-2 text-xs font-bold text-white backdrop-blur transition hover:bg-white/25"><Wallet size={15} className="ml-1 inline" />المصروفات</Link><Link to="/app/support" className="rounded-xl bg-cyan-300 px-4 py-2 text-xs font-black text-slate-900 transition hover:bg-cyan-200"><LifeBuoy size={15} className="ml-1 inline" />تواصل أو قدّم شكوى</Link></div></div>
      </motion.div>

      <SchedulePreview />

      {/* Off-screen printable monthly report per child */}
      <div style={{ position: 'fixed', top: 0, left: -10000, zIndex: -1 }}>
        {children.map((c) => (
          <ParentReportPrint key={c.id} ref={(el) => { printRefs.current[c.id] = el }} child={c} />
        ))}
      </div>

      {children.length === 0 ? <div className="card"><EmptyState icon={Users} title="لا يوجد أبناء مرتبطون بحسابك" /></div> : (
        <div className="grid gap-5 lg:grid-cols-2">
          {children.map((c) => {
            const st = ACADEMIC_STATUS[c.academicStatus] || {}
            const det = details[c.id]
            return (
              <motion.div variants={fadeUp} key={c.id} className="card p-5">
                <Link to={`/app/students/${c.id}`} className="block hover:opacity-90 transition-opacity">
                  <div className="flex items-center gap-3">
                    <Avatar name={c.fullName} size={52} />
                    <div className="flex-1 min-w-0">
                      <p className="truncate font-bold text-ink-800">{c.fullName}</p>
                      <p className="text-xs text-ink-400">{c.code} · {c.grade}</p>
                    </div>
                    <span className={`chip border ${st.color}`}>{st.label}</span>
                  </div>
                  <div className="mt-4 grid grid-cols-3 gap-3 text-center">
                    <div><p className="text-lg font-black text-ink-800">{Math.round(c.overallPercent)}٪</p><ProgressBar value={c.overallPercent} className="mt-1" /><p className="mt-1 text-[11px] text-ink-400">المعدل</p></div>
                    <div><p className="text-lg font-black text-ink-800">{Math.round(c.attendanceRate)}٪</p><ProgressBar value={c.attendanceRate} className="mt-1" /><p className="mt-1 text-[11px] text-ink-400">الحضور</p></div>
                    <div><p className="text-lg font-black text-ink-800">{Math.round(c.homeworkRate)}٪</p><ProgressBar value={c.homeworkRate} className="mt-1" /><p className="mt-1 text-[11px] text-ink-400">الواجبات</p></div>
                  </div>
                </Link>

                <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
                  <div className="rounded-2xl bg-ink-50 py-2.5"><p className="text-base font-black text-ink-800">{det ? det.enrollments.length : '—'}</p><p className="mt-0.5 text-ink-400">كورس</p></div>
                  <div className="rounded-2xl bg-ink-50 py-2.5"><p className="text-base font-black text-ink-800">{det ? det.exams.length : '—'}</p><p className="mt-0.5 text-ink-400">امتحان</p></div>
                  <div className="rounded-2xl bg-ink-50 py-2.5"><p className="text-base font-black text-ink-800">{det ? det.homework.length : '—'}</p><p className="mt-0.5 text-ink-400">واجب</p></div>
                </div>

                <div className="mt-3 flex gap-2">
                  <button onClick={() => setActive(c)} className="btn-soft flex-1"><BookOpen size={16} /> عرض التفاصيل</button>
                  <button onClick={() => exportChild(c)} disabled={exportingId === c.id} className="btn-ghost px-3">
                    {exportingId === c.id ? <Spinner className="h-4 w-4 border-ink-300 border-t-ink-600" /> : <Download size={16} />}
                  </button>
                </div>
              </motion.div>
            )
          })}
        </div>
      )}

      {active && <ChildDetailModal child={active} detail={details[active.id]} onClose={() => setActive(null)} />}
    </motion.div>
  )
}

function ChildDetailModal({ child, detail, onClose }) {
  const [tab, setTab] = useState('courses')
  const enrollments = detail?.enrollments || []
  const exams = detail?.exams || []
  const homework = detail?.homework || []

  return (
    <Modal open onClose={onClose} title={child.fullName} wide>
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <TabBtn active={tab === 'courses'} onClick={() => setTab('courses')} icon={BookOpen}>الكورسات ({enrollments.length})</TabBtn>
          <TabBtn active={tab === 'exams'} onClick={() => setTab('exams')} icon={FileQuestion}>الامتحانات ({exams.length})</TabBtn>
          <TabBtn active={tab === 'homework'} onClick={() => setTab('homework')} icon={ClipboardList}>الواجبات ({homework.length})</TabBtn>
        </div>

        {!detail ? <PageLoader /> : tab === 'courses' ? (
          enrollments.length === 0 ? <EmptyState icon={BookOpen} title="غير مسجَّل في أي كورس بعد" /> : (
            <div className="max-h-[50vh] space-y-2 overflow-y-auto pl-1">
              {enrollments.map((e) => (
                <div key={e.id} className="flex items-center justify-between rounded-2xl border border-ink-100 p-3">
                  <span className="text-sm font-bold text-ink-700">{e.courseTitle}</span>
                  <span className="chip bg-brand-50 text-brand-700">{e.status}</span>
                </div>
              ))}
            </div>
          )
        ) : tab === 'exams' ? (
          exams.length === 0 ? <EmptyState icon={FileQuestion} title="لا توجد امتحانات بعد" /> : (
            <div className="max-h-[50vh] space-y-2 overflow-y-auto pl-1">
              {exams.map((e) => (
                <div key={e.examId} className="flex items-center justify-between rounded-2xl border border-ink-100 p-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-bold text-ink-700">{e.examTitle}</p>
                    <p className="text-xs text-ink-400">{e.courseTitle}</p>
                  </div>
                  {e.percent != null ? (
                    <span className={`chip shrink-0 ${e.percent >= 50 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}>{e.score}/{e.maxScore} ({e.percent}٪)</span>
                  ) : (
                    <span className="chip shrink-0 bg-ink-100 text-ink-500">لم يُؤدَّ بعد</span>
                  )}
                </div>
              ))}
            </div>
          )
        ) : (
          homework.length === 0 ? <EmptyState icon={ClipboardList} title="لا توجد واجبات بعد" /> : (
            <div className="max-h-[50vh] space-y-2 overflow-y-auto pl-1">
              {homework.map((h) => {
                const cfg = HW_STATUS[h.myStatus] || HW_STATUS.PENDING
                return (
                  <div key={h.id} className="rounded-2xl border border-ink-100 p-3">
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-ink-700">{h.title}</p>
                        <p className="text-xs text-ink-400">{h.courseTitle} · الموعد: {fmtDate(h.deadline)}</p>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        {h.myScore != null && <span className="font-black text-emerald-600">{h.myScore}/{h.maxScore}</span>}
                        <span className={`chip ${cfg.c}`}>{cfg.label}</span>
                      </div>
                    </div>
                    {h.myFeedback && <p className="mt-2 rounded-xl bg-brand-50 p-2 text-xs text-brand-700"><strong>ملاحظة المدرس:</strong> {h.myFeedback}</p>}
                    {h.myFileKey && (
                      <a href={fileUrl(h.myFileKey)} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-brand-600 hover:underline">
                        <Paperclip size={13} /> الملف المُسلَّم
                      </a>
                    )}
                  </div>
                )
              })}
            </div>
          )
        )}
      </div>
    </Modal>
  )
}

function RingCard({ label, value, color }) {
  return (
    <div className="flex flex-col items-center rounded-2xl bg-ink-50/60 p-4">
      <ProgressRing value={value} size={88} color={color} />
      <p className="mt-2 text-xs font-semibold text-ink-500">{label}</p>
    </div>
  )
}

function WelcomeCard({ user }) {
  return (
    <div className="card overflow-hidden">
      <div className="relative bg-gradient-to-br from-brand-600 to-brand-800 p-8 text-white">
        <h2 className="text-2xl font-black">أهلاً {user.fullName} 👋</h2>
        <p className="mt-2 text-brand-100">مرحباً بك في منصة منارة. يمكنك متابعة كل ما يخصّك من هنا.</p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link to="/app/courses" className="btn bg-white text-brand-700 hover:bg-brand-50">الكورسات <ArrowLeft size={16} /></Link>
          <Link to="/app/exams" className="btn bg-white/15 text-white hover:bg-white/25">الامتحانات</Link>
          <Link to="/app/notifications" className="btn bg-white/15 text-white hover:bg-white/25">الإشعارات</Link>
        </div>
      </div>
    </div>
  )
}
