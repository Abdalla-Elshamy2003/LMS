import { forwardRef } from 'react'
import { ReportHeader, ReportFooter, ReportPage, StatBox } from './ReportChrome'
import { fmtDate } from '../../lib/format'

/** Printable monthly parent report (§47) for one child. */
const ParentReportPrint = forwardRef(function ParentReportPrint({ child }, ref) {
  return (
    <div ref={ref}>
      <ReportPage>
        <ReportHeader title="التقرير الشهري لولي الأمر" subtitle={fmtDate(new Date().toISOString())} />

        <div className="mt-6 rounded-2xl bg-brand-50 p-5 text-center">
          <p className="text-xl font-black text-ink-900">{child.fullName}</p>
          <p className="text-sm text-ink-500">{child.code} · {child.grade || '—'}</p>
        </div>

        <div className="mt-6 flex gap-4">
          <StatBox label="نسبة الحضور" value={`${Math.round(child.attendanceRate)}٪`} color="#10b981" />
          <StatBox label="متوسط الدرجات" value={`${Math.round(child.avgScore)}٪`} color="#0284c7" />
          <StatBox label="الواجبات المكتملة" value={`${Math.round(child.homeworkRate)}٪`} color="#f59e0b" />
          <StatBox label="المعدل العام" value={`${Math.round(child.overallPercent)}٪`} color="#8b5cf6" />
        </div>

        <div className="mt-8 rounded-2xl border border-ink-100 p-6">
          <p className="mb-3 text-base font-extrabold text-ink-800">ملخص الأداء هذا الشهر</p>
          <p className="leading-relaxed text-ink-600">
            حقق الطالب <strong>{child.fullName}</strong> نسبة حضور <strong>{Math.round(child.attendanceRate)}٪</strong>،
            بمتوسط درجات <strong>{Math.round(child.avgScore)}٪</strong>، ونسبة إنجاز واجبات <strong>{Math.round(child.homeworkRate)}٪</strong>.
            المعدل التراكمي العام للطالب حالياً <strong>{Math.round(child.overallPercent)}٪</strong>
            {child.academicStatus === 'AT_RISK' || child.academicStatus === 'NEEDS_ATTENTION'
              ? '، وننصح بمتابعة أقرب مع المدرس لدعم الطالب في الفترة القادمة.'
              : '، وهو أداء جيد نتمنى استمراره وتحسّنه.'}
          </p>
        </div>

        <div className="mt-auto">
          <ReportFooter />
        </div>
      </ReportPage>
    </div>
  )
})

export default ParentReportPrint
