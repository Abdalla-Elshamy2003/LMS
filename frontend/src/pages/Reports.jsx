import { useEffect, useRef, useState } from 'react'
import { BarChart3, Download, FileText, Printer, Users } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { EmptyState, Spinner } from '../components/ui'
import { exportElementToPdf } from '../lib/pdf'
import { apiErrorMessage } from '../lib/apiError'

/**
 * Periodic attendance + grade reports (monthly / term).
 *
 * The backend returns aggregates only; the printable document is built here, then photographed to
 * PDF by lib/pdf.js — which is how the certificates already work, and which sidesteps Arabic
 * shaping and font embedding entirely because the browser has already laid the text out.
 */
const TERMS = [
  { key: 'month', label: 'شهري' },
  { key: 'T1', label: 'الفصل الأول' },
  { key: 'T2', label: 'الفصل الثاني' },
  { key: 'T3', label: 'الدور الصيفي' },
]

export default function Reports() {
  const { user } = useAuth()
  const staff = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER'].includes(user.role)
  // A parent has no student record of their own, so /reports/me would fail for them — they always
  // report on one of their children, picked from the list below.
  const isParent = user.role === 'PARENT'

  const [scope, setScope] = useState(staff ? 'academy' : 'student')
  const [range, setRange] = useState('month')
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const [courseId, setCourseId] = useState('')
  const [studentId, setStudentId] = useState('')
  const [courses, setCourses] = useState([])
  const [students, setStudents] = useState([])
  const [data, setData] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)
  const sheet = useRef(null)

  useEffect(() => {
    if (isParent) {
      // Same list the certificates page uses for a parent's children.
      api.get('/students/children')
        .then((r) => {
          const kids = r.data || []
          setStudents(kids)
          if (kids.length === 1) setStudentId(String(kids[0].id))
        })
        .catch(() => setError('تعذّر تحميل قائمة الأبناء'))
      return
    }
    if (!staff) return
    api.get('/courses').then((r) => setCourses(r.data || [])).catch(() => {})
    api.get('/students?size=500').then((r) => setStudents(r.data?.content || r.data || [])).catch(() => {})
  }, [staff, isParent])

  const params = () => {
    const p = range === 'month' ? { month } : { term: range }
    if (scope === 'academy' && courseId) p.courseId = courseId
    return p
  }

  const load = async () => {
    setBusy(true); setError(''); setData(null)
    try {
      // A student pulling their own report has no idea what their studentId is — /reports/me
      // resolves it from the session instead of making the client look it up first.
      const url = scope === 'academy' ? '/reports/academy'
        : studentId ? `/reports/student/${studentId}` : '/reports/me'
      const { data: d } = await api.get(url, { params: params() })
      setData(d)
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر تجهيز التقرير'))
    } finally {
      setBusy(false)
    }
  }

  const download = async () => {
    setExporting(true)
    try {
      await exportElementToPdf(sheet.current, `تقرير-${data?.period?.label || ''}.pdf`)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="rounded-3xl bg-gradient-to-l from-[#173f53] to-[#086f98] p-7 text-white">
        <span className="text-xs text-cyan-100">تقارير دورية</span>
        <h2 className="mt-2 flex items-center gap-3 text-2xl font-black"><BarChart3 size={26} /> تقارير الحضور والدرجات</h2>
        <p className="mt-2 text-sm leading-7 text-cyan-100/80">
          اختار الفترة والنطاق، واطبع التقرير أو حمّله PDF لتسليمه لولي الأمر أو حفظه في الأرشيف.
        </p>
      </div>

      <div className="card space-y-4 p-6 print:hidden">
        <div className="flex flex-wrap items-end gap-3">
          {staff && (
            <label className="block text-xs font-bold">
              نطاق التقرير
              <select className="input mt-2 max-w-[190px]" value={scope} onChange={(e) => { setScope(e.target.value); setData(null) }}>
                <option value="academy">كل الطلاب</option>
                <option value="student">طالب واحد</option>
              </select>
            </label>
          )}
          <label className="block text-xs font-bold">
            الفترة
            <select className="input mt-2 max-w-[170px]" value={range} onChange={(e) => setRange(e.target.value)}>
              {TERMS.map((t) => <option key={t.key} value={t.key}>{t.label}</option>)}
            </select>
          </label>
          {range === 'month' && (
            <label className="block text-xs font-bold">
              الشهر
              <input type="month" className="input mt-2 max-w-[170px]" value={month} onChange={(e) => setMonth(e.target.value)} />
            </label>
          )}
          {staff && scope === 'academy' && (
            <label className="block text-xs font-bold">
              الكورس (اختياري)
              <select className="input mt-2 max-w-[220px]" value={courseId} onChange={(e) => setCourseId(e.target.value)}>
                <option value="">كل الكورسات</option>
                {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
              </select>
            </label>
          )}
          {((staff && scope === 'student') || isParent) && (
            <label className="block text-xs font-bold">
              {isParent ? 'الابن/الابنة' : 'الطالب'}
              <select className="input mt-2 max-w-[240px]" value={studentId} onChange={(e) => setStudentId(e.target.value)}>
                <option value="">{isParent ? 'اختر الابن' : 'اختر الطالب'}</option>
                {students.map((s) => <option key={s.id} value={s.id}>{s.fullName}{s.code ? ` — ${s.code}` : ''}</option>)}
              </select>
            </label>
          )}
          <button onClick={load} disabled={busy || ((staff && scope === 'student') || isParent ? !studentId : false)} className="btn-primary">
            {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <FileText size={16} />} اعرض التقرير
          </button>
          {data && (
            <>
              <button onClick={() => window.print()} className="btn-secondary"><Printer size={16} /> طباعة</button>
              <button onClick={download} disabled={exporting} className="btn-secondary">
                {exporting ? <Spinner className="h-4 w-4" /> : <Download size={16} />} تحميل PDF
              </button>
            </>
          )}
        </div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      </div>

      {!data && !busy && (
        <div className="card p-6"><EmptyState icon={BarChart3} title="اختار الفترة واضغط اعرض التقرير" hint="التقرير بيغطي الحضور والدرجات في الفترة المحددة" /></div>
      )}

      {data && (
        <div ref={sheet} className="card bg-white p-8" dir="rtl">
          <ReportHeader period={data.period} course={data.course} student={data.student} />
          {data.rows ? <AcademySheet data={data} /> : <StudentSheet data={data} />}
          <p className="mt-8 border-t border-ink-100 pt-4 text-center text-[11px] text-ink-400">
            تقرير صادر من منصة منارة — {new Date().toLocaleDateString('ar-EG')}
          </p>
        </div>
      )}
    </div>
  )
}

function ReportHeader({ period, course, student }) {
  return (
    <div className="mb-6 border-b border-ink-100 pb-5 text-center">
      <img src="/images/logo.png" alt="" className="mx-auto mb-3 h-14 w-14 object-contain" />
      <h1 className="text-2xl font-black text-ink-800">
        {student ? `تقرير الطالب — ${student.fullName}` : 'تقرير الحضور والدرجات'}
      </h1>
      <p className="mt-2 text-sm text-ink-500">
        {period?.label}
        {period?.from ? ` · من ${period.from} إلى ${period.to}` : ''}
        {course ? ` · ${course.title}` : ''}
      </p>
      {student && <p className="mt-1 text-xs text-ink-400">{student.code}{student.grade ? ` · ${student.grade}` : ''}</p>}
    </div>
  )
}

function Stat({ label, value, suffix = '' }) {
  return (
    <div className="rounded-2xl bg-ink-50 p-4 text-center">
      <p className="text-xs text-ink-400">{label}</p>
      <p className="mt-1 text-2xl font-black text-ink-800">{value ?? 0}{suffix}</p>
    </div>
  )
}

function StudentSheet({ data }) {
  const a = data.attendance || {}, g = data.grades || {}
  return (
    <div className="space-y-7">
      <section>
        <h2 className="mb-3 font-extrabold text-ink-800">الحضور</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          <Stat label="عدد الحصص" value={a.total} />
          <Stat label="حضر" value={a.attended} />
          <Stat label="غاب" value={a.absent} />
          <Stat label="تأخّر" value={a.late} />
          <Stat label="نسبة الحضور" value={a.rate} suffix="%" />
        </div>
      </section>

      <section>
        <h2 className="mb-3 font-extrabold text-ink-800">الدرجات</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Stat label="عدد التقييمات" value={g.count} />
          <Stat label="المتوسط" value={g.averagePercent} suffix="%" />
          <Stat label="أعلى درجة" value={g.best} suffix="%" />
          <Stat label="أقل درجة" value={g.worst} suffix="%" />
        </div>
        {data.gradeRows?.length > 0 && (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-right text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs text-ink-400">
                  <th className="py-2">التقييم</th><th>النوع</th><th>الدرجة</th><th>النسبة</th><th>التاريخ</th>
                </tr>
              </thead>
              <tbody>
                {data.gradeRows.map((r) => (
                  <tr key={r.id} className="border-b border-ink-50">
                    <td className="py-2 font-bold">{r.title}</td>
                    <td className="text-xs text-ink-400">{r.category}</td>
                    <td className="tabular-nums">{r.score} / {r.maxScore}</td>
                    <td className="tabular-nums font-bold">{r.percent}%</td>
                    <td className="text-xs text-ink-400">{fmtDate(r.recordedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {data.gateDays?.length > 0 && (
        <section>
          <h2 className="mb-3 font-extrabold text-ink-800">سجل الدخول والخروج من الباب</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-right text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs text-ink-400">
                  <th className="py-2">اليوم</th><th>أول دخول</th><th>آخر خروج</th><th>مدة التواجد</th>
                </tr>
              </thead>
              <tbody>
                {data.gateDays.map((d) => (
                  <tr key={d.date} className="border-b border-ink-50">
                    <td className="py-2 font-bold">{d.date}</td>
                    <td className="tabular-nums">{fmtTime(d.firstIn)}</td>
                    <td className="tabular-nums">{fmtTime(d.lastOut)}</td>
                    <td className="tabular-nums text-ink-500">{d.minutesOnSite == null ? '—' : `${Math.floor(d.minutesOnSite / 60)}س ${d.minutesOnSite % 60}د`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}

function AcademySheet({ data }) {
  const t = data.totals || {}
  if (!data.rows?.length) return <EmptyState icon={Users} title="لا توجد بيانات في هذه الفترة" hint="جرّب فترة تانية أو كورس مختلف" />
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-3 gap-3">
        <Stat label="عدد الطلاب" value={t.students} />
        <Stat label="متوسط الدرجات" value={t.averagePercent} suffix="%" />
        <Stat label="متوسط الحضور" value={t.attendanceRate} suffix="%" />
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-right text-sm">
          <thead>
            <tr className="border-b border-ink-100 text-xs text-ink-400">
              <th className="py-2">#</th><th>الطالب</th><th>الكود</th><th>الصف</th>
              <th>الحصص</th><th>حضر</th><th>غاب</th><th>الحضور</th><th>التقييمات</th><th>المتوسط</th>
            </tr>
          </thead>
          <tbody>
            {data.rows.map((r) => (
              <tr key={r.studentId} className="border-b border-ink-50">
                <td className="py-2 font-black text-ink-400">{r.rank}</td>
                <td className="font-bold">{r.fullName}</td>
                <td className="text-xs text-ink-400">{r.code}</td>
                <td className="text-xs text-ink-400">{r.grade}</td>
                <td className="tabular-nums">{r.sessions}</td>
                <td className="tabular-nums">{r.attended}</td>
                <td className="tabular-nums">{r.absent}</td>
                <td className="tabular-nums">{r.attendanceRate}%</td>
                <td className="tabular-nums">{r.assessments}</td>
                <td className="tabular-nums font-bold">{r.averagePercent}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function fmtDate(v) {
  return v ? new Date(v).toLocaleDateString('ar-EG') : '—'
}

function fmtTime(v) {
  return v ? new Date(v).toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' }) : '—'
}
