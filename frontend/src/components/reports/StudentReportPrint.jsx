import { forwardRef } from 'react'
import { ReportHeader, ReportFooter, ReportPage, StatBox } from './ReportChrome'
import { ACADEMIC_STATUS, fmtDate } from '../../lib/format'

const catLabel = (c) => ({ QUIZ: 'اختبار قصير', EXAM: 'امتحان', HOMEWORK: 'واجب', MIDTERM: 'نصف الفصل', FINAL: 'نهائي', OTHER: 'أخرى' }[c] || c)

/** Printable student report (§46): attendance, grades, homework, progress — one A4 page. */
const StudentReportPrint = forwardRef(function StudentReportPrint({ detail, grades, timeline }, ref) {
  const s = detail.summary
  const st = ACADEMIC_STATUS[s.academicStatus] || {}

  return (
    <div ref={ref}>
      <ReportPage>
        <ReportHeader title="تقرير الطالب" subtitle={fmtDate(new Date().toISOString())} />

        <div className="mt-6 flex items-center gap-4 rounded-2xl bg-brand-50 p-5">
          <div className="grid h-16 w-16 place-items-center rounded-2xl bg-brand-600 text-2xl font-bold text-white">
            {s.fullName?.trim()?.split(/\s+/).slice(0, 2).map((w) => w[0]).join('')}
          </div>
          <div>
            <p className="text-xl font-black text-ink-900">{s.fullName}</p>
            <p className="text-sm text-ink-500">{s.code} · {s.grade || '—'}</p>
          </div>
          <span className="mr-auto rounded-full bg-white px-4 py-1.5 text-sm font-bold text-brand-700">{st.label}</span>
        </div>

        <div className="mt-6 flex gap-4">
          <StatBox label="المعدل العام" value={`${Math.round(s.overallPercent)}٪`} color="#0284c7" />
          <StatBox label="نسبة الحضور" value={`${Math.round(s.attendanceRate)}٪`} color="#10b981" />
          <StatBox label="الواجبات" value={`${Math.round(s.homeworkRate)}٪`} color="#f59e0b" />
          <StatBox label="متوسط الدرجات" value={`${Math.round(s.avgScore)}٪`} color="#8b5cf6" />
        </div>

        <div className="mt-8">
          <p className="mb-3 text-base font-extrabold text-ink-800">سجل الدرجات</p>
          {!grades || grades.items.length === 0 ? (
            <p className="text-sm text-ink-400">لا توجد درجات مسجّلة بعد.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-ink-200 text-right text-ink-400">
                  <th className="py-2 font-semibold">البند</th>
                  <th className="py-2 font-semibold">النوع</th>
                  <th className="py-2 font-semibold">الدرجة</th>
                  <th className="py-2 font-semibold">النسبة</th>
                </tr>
              </thead>
              <tbody>
                {grades.items.map((g) => {
                  const pct = g.maxScore > 0 ? Math.round((g.score / g.maxScore) * 100) : 0
                  return (
                    <tr key={g.id} className="border-b border-ink-100">
                      <td className="py-2 font-semibold">{g.title}</td>
                      <td className="py-2 text-ink-500">{catLabel(g.category)}</td>
                      <td className="py-2 font-bold">{g.score}/{g.maxScore}</td>
                      <td className="py-2 font-bold" style={{ color: pct >= 60 ? '#059669' : '#dc2626' }}>{pct}٪</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>

        <div className="mt-8">
          <p className="mb-3 text-base font-extrabold text-ink-800">آخر الأنشطة</p>
          {timeline.length === 0 ? (
            <p className="text-sm text-ink-400">لا يوجد نشاط مسجّل بعد.</p>
          ) : (
            <div className="space-y-2">
              {timeline.slice(0, 8).map((e) => (
                <div key={e.id} className="flex items-center justify-between border-b border-dashed border-ink-100 py-1.5 text-sm">
                  <span className="font-semibold text-ink-700">{e.title}{e.detail ? ` — ${e.detail}` : ''}</span>
                  <span className="text-xs text-ink-400">{fmtDate(e.occurredAt)}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="mt-auto">
          <ReportFooter />
        </div>
      </ReportPage>
    </div>
  )
})

export default StudentReportPrint
