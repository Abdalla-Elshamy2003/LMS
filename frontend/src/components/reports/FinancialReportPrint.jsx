import { forwardRef } from 'react'
import { ReportHeader, ReportFooter, ReportPage, StatBox } from './ReportChrome'
import { fmtMoney, INVOICE_STATUS } from '../../lib/format'

/** Printable financial report (§46): revenue, outstanding, per-invoice breakdown. */
const FinancialReportPrint = forwardRef(function FinancialReportPrint({ invoices }, ref) {
  const collected = invoices.reduce((a, i) => a + Number(i.paidAmount || 0), 0)
  const outstanding = invoices.reduce((a, i) => a + Number(i.remaining || 0), 0)
  const paidCount = invoices.filter((i) => i.status === 'PAID').length

  return (
    <div ref={ref}>
      <ReportPage>
        <ReportHeader title="التقرير المالي" subtitle={`${invoices.length} فاتورة`} />

        <div className="mt-6 flex gap-4">
          <StatBox label="إجمالي المحصّل" value={fmtMoney(collected)} color="#059669" />
          <StatBox label="إجمالي المتأخرات" value={fmtMoney(outstanding)} color="#dc2626" />
          <StatBox label="فواتير مدفوعة بالكامل" value={paidCount} color="#0284c7" />
        </div>

        <div className="mt-8">
          <p className="mb-3 text-base font-extrabold text-ink-800">تفاصيل الفواتير</p>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-ink-200 text-right text-ink-400">
                <th className="py-2 font-semibold">الطالب</th>
                <th className="py-2 font-semibold">الإجمالي</th>
                <th className="py-2 font-semibold">المدفوع</th>
                <th className="py-2 font-semibold">المتبقي</th>
                <th className="py-2 font-semibold">الحالة</th>
              </tr>
            </thead>
            <tbody>
              {invoices.slice(0, 30).map((i) => (
                <tr key={i.id} className="border-b border-ink-100">
                  <td className="py-2 font-semibold">{i.studentName}</td>
                  <td className="py-2">{fmtMoney(i.totalAmount)}</td>
                  <td className="py-2 text-emerald-700">{fmtMoney(i.paidAmount)}</td>
                  <td className="py-2 text-rose-600">{fmtMoney(i.remaining)}</td>
                  <td className="py-2 text-xs font-bold">{INVOICE_STATUS[i.status]?.label || i.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {invoices.length > 30 && <p className="mt-2 text-xs text-ink-400">تم عرض أول 30 فاتورة من إجمالي {invoices.length}.</p>}
        </div>

        <div className="mt-auto">
          <ReportFooter />
        </div>
      </ReportPage>
    </div>
  )
})

export default FinancialReportPrint
