import { useEffect, useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import { Wallet, CheckCircle2, AlertTriangle, ReceiptText, LifeBuoy } from 'lucide-react'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import { PageLoader, EmptyState, ProgressBar, fadeUp, stagger } from '../components/ui'
import { fmtMoney, fmtDate } from '../lib/format'

const STATUS = { PAID: ['مدفوعة', 'bg-emerald-50 text-emerald-700'], PARTIAL: ['مدفوعة جزئياً', 'bg-amber-50 text-amber-700'], PENDING: ['مستحقة', 'bg-rose-50 text-rose-700'] }

export default function FamilyFinance() {
  const [children, setChildren] = useState(null)
  const [studentId, setStudentId] = useState('')
  useEffect(() => { api.get('/family/finance').then(r => { setChildren(r.data); if (r.data.length === 1) setStudentId(String(r.data[0].studentId)) }) }, [])
  const visible = useMemo(() => (children || []).filter(c => !studentId || String(c.studentId) === studentId), [children, studentId])
  const total = visible.reduce((sum, c) => sum + Number(c.total || 0), 0)
  const paid = visible.reduce((sum, c) => sum + Number(c.paid || 0), 0)
  const remaining = visible.reduce((sum, c) => sum + Number(c.remaining || 0), 0)
  if (!children) return <PageLoader />
  return <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
    <motion.section variants={fadeUp} className="card overflow-hidden bg-gradient-to-l from-teal-700 to-slate-900 p-6 text-white sm:p-8"><div className="flex flex-wrap items-end justify-between gap-5"><div><p className="text-xs font-black text-lime-300">وضوح مالي بدون مفاجآت</p><h2 className="mt-3 text-2xl font-black">مصروفات الأبناء والأقساط في مكان واحد.</h2><p className="mt-2 max-w-xl text-sm leading-7 text-slate-300">راجع كل فاتورة، المبلغ المدفوع والمتبقي وتاريخ الاستحقاق، وتواصل مع الإدارة مباشرة عند وجود استفسار.</p></div><Link to="/app/support" className="rounded-xl bg-white/15 px-4 py-3 text-sm font-bold text-white backdrop-blur hover:bg-white/25"><LifeBuoy size={16} className="ml-2 inline" />استفسار مالي</Link></div></motion.section>
    <div className="flex flex-wrap items-center gap-3"><select aria-label="اختر الطالب" className="input max-w-xs" value={studentId} onChange={e => setStudentId(e.target.value)}><option value="">كل الأبناء</option>{children.map(c => <option key={c.studentId} value={c.studentId}>{c.studentName}</option>)}</select></div>
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3"><FinanceStat icon={Wallet} label="إجمالي الرسوم" value={total} /><FinanceStat icon={CheckCircle2} label="تم دفعه" value={paid} green /><FinanceStat icon={AlertTriangle} label="المتبقي" value={remaining} danger /></div>
    {!visible.some(c => c.invoices.length) ? <div className="card"><EmptyState icon={ReceiptText} title="لا توجد فواتير مسجلة" hint="أي رسوم أو أقساط جديدة ستظهر هنا تلقائياً." /></div> : visible.map(child => <motion.section variants={fadeUp} key={child.studentId} className="card p-5 sm:p-6"><div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div><p className="font-black text-ink-800">{child.studentName}</p><p className="text-xs text-ink-400">{child.invoices.length} فاتورة</p></div><div className="min-w-48"><div className="mb-1 flex justify-between text-[10px] text-ink-400"><span>نسبة السداد</span><span>{child.total ? Math.round(Number(child.paid) / Number(child.total) * 100) : 0}٪</span></div><ProgressBar value={child.total ? Number(child.paid) / Number(child.total) * 100 : 0} /></div></div><div className="space-y-3">{child.invoices.map(invoice => { const cfg = STATUS[invoice.status] || STATUS.PENDING; return <div key={invoice.id} className="flex flex-wrap items-center gap-4 rounded-2xl border border-ink-100 p-4"><span className="grid h-11 w-11 place-items-center rounded-xl bg-teal-50 text-teal-700"><ReceiptText size={20} /></span><div className="min-w-0 flex-1"><p className="truncate text-sm font-extrabold text-ink-800">{invoice.title}</p><p className="mt-1 text-xs text-ink-400">الاستحقاق: {fmtDate(invoice.dueDate)}</p></div><div className="text-left"><p className="text-sm font-black text-ink-800">{fmtMoney(invoice.remaining)} متبقي</p><span className={`chip mt-1 ${cfg[1]}`}>{cfg[0]}</span></div></div> })}</div></motion.section>)}
  </motion.div>
}

function FinanceStat({ icon: Icon, label, value, green, danger }) { return <motion.div variants={fadeUp} className="card p-5"><span className={`grid h-11 w-11 place-items-center rounded-xl ${danger ? 'bg-rose-50 text-rose-700' : green ? 'bg-emerald-50 text-emerald-700' : 'bg-brand-50 text-brand-700'}`}><Icon size={20} /></span><p className="mt-3 text-2xl font-black text-ink-800">{fmtMoney(value)}</p><p className="text-xs font-bold text-ink-400">{label}</p></motion.div> }
