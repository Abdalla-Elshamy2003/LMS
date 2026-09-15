import { useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Wallet, Bell, TrendingUp, AlertTriangle, CheckCircle2, Download } from 'lucide-react'
import api from '../lib/api'
import { PageLoader, EmptyState, CountUp, Spinner, stagger, fadeUp } from '../components/ui'
import { fmtMoney, INVOICE_STATUS } from '../lib/format'
import { exportElementToPdf } from '../lib/pdf'
import FinancialReportPrint from '../components/reports/FinancialReportPrint'

export default function Payments() {
  const [invoices, setInvoices] = useState(null)
  const [courses, setCourses] = useState([])
  const [courseFilter, setCourseFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [q, setQ] = useState('')
  const [msg, setMsg] = useState('')
  const [exporting, setExporting] = useState(false)
  const printRef = useRef(null)
  const load = () => api.get('/payments/invoices').then((r) => setInvoices(r.data))
  useEffect(() => { load(); api.get('/courses').then((r) => setCourses(r.data)).catch(() => {}) }, [])

  const totals = useMemo(() => {
    if (!invoices) return { collected: 0, outstanding: 0, count: 0 }
    return invoices.reduce((a, i) => ({
      collected: a.collected + Number(i.paidAmount || 0),
      outstanding: a.outstanding + Number(i.remaining || 0),
      count: a.count + 1,
    }), { collected: 0, outstanding: 0, count: 0 })
  }, [invoices])

  const runReminders = async () => {
    const r = await api.post('/payments/run-reminders')
    setMsg(`تم إرسال ${r.data} تذكير بالأقساط المستحقة`)
    setTimeout(() => setMsg(''), 4000)
    load()
  }

  if (!invoices) return <PageLoader />

  const exportReport = async () => {
    setExporting(true)
    try {
      await exportElementToPdf(printRef.current, 'التقرير-المالي.pdf')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-5">
      <div style={{ position: 'fixed', top: 0, left: -10000, zIndex: -1 }}>
        <FinancialReportPrint ref={printRef} invoices={invoices} />
      </div>

      <div className="flex justify-end">
        <button onClick={exportReport} disabled={exporting} className="btn-soft">
          {exporting ? <Spinner className="h-4 w-4 border-brand-200 border-t-brand-600" /> : <Download size={16} />}
          تصدير التقرير المالي PDF
        </button>
      </div>

      <motion.div variants={stagger} initial="hidden" animate="show" className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <motion.div variants={fadeUp} className="card p-5"><div className="grid h-11 w-11 place-items-center rounded-2xl bg-emerald-50 text-emerald-600"><TrendingUp size={20} /></div><p className="mt-3 text-2xl font-black text-ink-800">{fmtMoney(totals.collected)}</p><p className="text-sm text-ink-400">إجمالي المحصّل</p></motion.div>
        <motion.div variants={fadeUp} className="card p-5"><div className="grid h-11 w-11 place-items-center rounded-2xl bg-rose-50 text-rose-600"><AlertTriangle size={20} /></div><p className="mt-3 text-2xl font-black text-ink-800">{fmtMoney(totals.outstanding)}</p><p className="text-sm text-ink-400">إجمالي المتأخرات</p></motion.div>
        <motion.div variants={fadeUp} className="card p-5 flex flex-col justify-between"><div className="grid h-11 w-11 place-items-center rounded-2xl bg-brand-50 text-brand-600"><Wallet size={20} /></div><button onClick={runReminders} className="btn-primary mt-3"><Bell size={16} /> إرسال تذكيرات الأقساط</button></motion.div>
      </motion.div>

      {msg && <motion.div initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} className="flex items-center gap-2 rounded-2xl bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700"><CheckCircle2 size={16} /> {msg}</motion.div>}

      <div className="flex flex-wrap items-center gap-2">
        <input value={q} onChange={(e) => setQ(e.target.value)} className="input max-w-[200px]" placeholder="ابحث باسم الطالب..." />
        <select className="input max-w-[200px]" value={courseFilter} onChange={(e) => setCourseFilter(e.target.value)}>
          <option value="">كل الكورسات</option>
          {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
        </select>
        {[['', 'الكل'], ['PENDING', 'غير مدفوعة'], ['PARTIAL', 'جزئية'], ['PAID', 'مدفوعة']].map(([k, l]) => (
          <button key={k} onClick={() => setStatusFilter(k)} className={`chip border ${statusFilter === k ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}>{l}</button>
        ))}
      </div>

      {(() => { const filtered = invoices.filter((i) => (!courseFilter || String(i.courseId) === courseFilter) && (!statusFilter || i.status === statusFilter) && (!q || (i.studentName || '').includes(q))); return (
      <div className="card overflow-hidden">
        <div className="border-b border-ink-100 px-6 py-4"><h3 className="font-extrabold text-ink-800">الفواتير ({filtered.length})</h3></div>
        {filtered.length === 0 ? <EmptyState icon={Wallet} title="لا توجد فواتير" /> : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-right text-ink-400"><th className="px-6 py-3 font-semibold">الطالب</th><th className="px-6 py-3 font-semibold">البيان</th><th className="px-6 py-3 font-semibold">الإجمالي</th><th className="px-6 py-3 font-semibold">المدفوع</th><th className="px-6 py-3 font-semibold">المتبقي</th><th className="px-6 py-3 font-semibold">الحالة</th></tr></thead>
              <tbody className="divide-y divide-ink-100">
                {filtered.map((i) => (
                  <tr key={i.id} className="hover:bg-ink-50/50">
                    <td className="px-6 py-3 font-bold text-ink-700">{i.studentName}</td>
                    <td className="px-6 py-3 text-ink-500">{i.title}</td>
                    <td className="px-6 py-3 font-semibold">{fmtMoney(i.totalAmount)}</td>
                    <td className="px-6 py-3 text-emerald-600 font-semibold">{fmtMoney(i.paidAmount)}</td>
                    <td className="px-6 py-3 text-rose-600 font-semibold">{fmtMoney(i.remaining)}</td>
                    <td className="px-6 py-3"><span className={`chip ${INVOICE_STATUS[i.status]?.color}`}>{INVOICE_STATUS[i.status]?.label}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      ) })()}
    </div>
  )
}
