import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Megaphone, Send, Users, MessageCircle, Smartphone, Mail, Bell, CheckCircle2, Tag, X } from 'lucide-react'
import api from '../lib/api'
import { PageLoader, EmptyState, Spinner, stagger, fadeUp } from '../components/ui'
import { fmtDateTime, fmtMoney } from '../lib/format'

const AUDIENCES = [
  { key: 'ALL', label: 'الجميع' },
  { key: 'STUDENTS', label: 'الطلاب' },
  { key: 'TEACHERS', label: 'المدرسون' },
  { key: 'PARENTS', label: 'أولياء الأمور' },
]

const CHANNELS = [
  { key: 'IN_APP', label: 'داخل المنصة', icon: Bell, locked: true },
  { key: 'WHATSAPP', label: 'واتساب', icon: MessageCircle },
  { key: 'SMS', label: 'رسالة نصية', icon: Smartphone },
  { key: 'EMAIL', label: 'بريد إلكتروني', icon: Mail },
]

export default function Campaigns() {
  const [items, setItems] = useState(null)
  const [form, setForm] = useState({ title: '', body: '', audience: 'ALL', channels: ['IN_APP'] })
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const [courses, setCourses] = useState([])
  const [discountCourseId, setDiscountCourseId] = useState('')
  const [discountPercent, setDiscountPercent] = useState(20)
  const [applyingDiscount, setApplyingDiscount] = useState(false)
  const [discountError, setDiscountError] = useState('')

  const load = () => api.get('/communication/announcements').then((r) => setItems(r.data))
  const loadCourses = () => api.get('/courses').then((r) => setCourses(r.data))
  useEffect(() => { load(); loadCourses() }, [])

  const discountedCourses = courses.filter((c) => c.discountPercent > 0)

  const applyDiscount = async (courseId, percent) => {
    setApplyingDiscount(true); setDiscountError('')
    try {
      const { data } = await api.put(`/courses/${courseId}/discount`, { discountPercent: percent })
      setCourses((cs) => cs.map((c) => (c.id === courseId ? data : c)))
      if (percent > 0) {
        setForm((f) => ({
          ...f,
          audience: 'STUDENTS',
          title: `خصم ${percent}% على كورس ${data.title}`,
          body: `عرض خاص لفترة محدودة! اشترك الآن في كورس "${data.title}" بسعر ${fmtMoney(data.finalPrice)} بدلاً من ${fmtMoney(data.price)}.`,
        }))
        setDiscountCourseId('')
      }
    } catch (e) {
      setDiscountError(e.response?.data?.message || 'تعذّر تطبيق الخصم')
    } finally {
      setApplyingDiscount(false)
    }
  }

  const toggleChannel = (key) => {
    if (key === 'IN_APP') return
    setForm((f) => ({ ...f, channels: f.channels.includes(key) ? f.channels.filter((c) => c !== key) : [...f.channels, key] }))
  }

  const send = async (e) => {
    e.preventDefault()
    if (!form.title.trim() || !form.body.trim()) { setError('العنوان والنص مطلوبان'); return }
    setSending(true); setError(''); setResult(null)
    try {
      const { data } = await api.post('/communication/broadcast', form)
      setResult(data)
      setForm({ title: '', body: '', audience: 'ALL', channels: ['IN_APP'] })
      load()
    } catch (e) {
      setError(e.response?.data?.message || 'تعذّر إرسال الحملة')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-5">
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="card space-y-4 p-6 lg:col-span-2">
        <div className="flex items-center gap-2.5">
          <span className="grid h-10 w-10 place-items-center rounded-2xl bg-amber-50 text-amber-600"><Tag size={20} /></span>
          <div><p className="font-extrabold text-ink-800">خصم على كورس</p><p className="text-xs text-ink-400">اختر كورساً ونسبة الخصم — هيتحسب السعر تلقائياً في كل مكان وفي الدفع</p></div>
        </div>
        <div className="grid grid-cols-[1fr_100px] gap-2">
          <select className="input" value={discountCourseId} onChange={(e) => setDiscountCourseId(e.target.value)}>
            <option value="">اختر كورساً</option>
            {courses.map((c) => <option key={c.id} value={c.id}>{c.title} ({fmtMoney(c.price)})</option>)}
          </select>
          <input type="number" min={1} max={99} className="input" value={discountPercent} onChange={(e) => setDiscountPercent(Number(e.target.value))} />
        </div>
        {discountError && <p className="rounded-xl bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-600">{discountError}</p>}
        <button type="button" disabled={!discountCourseId || applyingDiscount} onClick={() => applyDiscount(Number(discountCourseId), discountPercent)} className="btn-primary w-full py-2.5">
          {applyingDiscount ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Tag size={16} />} تطبيق الخصم وتجهيز إعلان
        </button>
        {discountedCourses.length > 0 && (
          <div className="space-y-2 border-t border-ink-100 pt-3">
            <p className="text-xs font-bold text-ink-500">كورسات عليها خصم حالياً</p>
            {discountedCourses.map((c) => (
              <div key={c.id} className="flex items-center justify-between gap-2 rounded-xl bg-amber-50 px-3 py-2 text-xs">
                <span className="font-bold text-ink-700">{c.title}</span>
                <span className="flex items-center gap-2">
                  <span className="text-ink-400 line-through">{fmtMoney(c.price)}</span>
                  <span className="font-black text-amber-700">{fmtMoney(c.finalPrice)}</span>
                  <button type="button" onClick={() => applyDiscount(c.id, 0)} className="text-rose-500 hover:text-rose-700" title="إلغاء الخصم"><X size={14} /></button>
                </span>
              </div>
            ))}
          </div>
        )}
      </motion.div>

      <motion.form onSubmit={send} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="card space-y-4 p-6 lg:col-span-3">
        <div className="flex items-center gap-2.5">
          <span className="grid h-10 w-10 place-items-center rounded-2xl bg-brand-50 text-brand-600"><Megaphone size={20} /></span>
          <div><p className="font-extrabold text-ink-800">حملة جديدة</p><p className="text-xs text-ink-400">إعلان أو رسالة تسويقية لجمهورك</p></div>
        </div>
        <div><label className="label">العنوان</label><input className="input" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="عرض خاص على كورسات الترم الجديد" /></div>
        <div><label className="label">نص الرسالة</label><textarea className="input" rows={4} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} placeholder="اكتب رسالتك هنا..." /></div>
        <div>
          <label className="label">الجمهور المستهدف</label>
          <div className="flex flex-wrap gap-2">
            {AUDIENCES.map((a) => (
              <button key={a.key} type="button" onClick={() => setForm({ ...form, audience: a.key })}
                className={`chip border ${form.audience === a.key ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}>
                <Users size={13} /> {a.label}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="label">قنوات الإرسال</label>
          <div className="flex flex-wrap gap-2">
            {CHANNELS.map((c) => (
              <button key={c.key} type="button" onClick={() => toggleChannel(c.key)} disabled={c.locked}
                className={`chip border ${form.channels.includes(c.key) ? 'bg-emerald-600 text-white border-emerald-600' : 'bg-white text-ink-600 border-ink-200'} ${c.locked ? 'opacity-70' : ''}`}>
                <c.icon size={13} /> {c.label}
              </button>
            ))}
          </div>
          <p className="mt-1.5 text-[11px] text-ink-400">قناة "داخل المنصة" مفعّلة دائماً كحد أدنى مضمون الوصول.</p>
        </div>
        {error && <p className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
        {result && (
          <p className="flex items-center gap-2 rounded-2xl bg-emerald-50 px-4 py-2.5 text-sm font-semibold text-emerald-700">
            <CheckCircle2 size={16} /> تم الإرسال إلى {result.recipients} مستلم
          </p>
        )}
        <button type="submit" disabled={sending} className="btn-primary w-full py-2.5">
          {sending ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Send size={16} />} إرسال الحملة
        </button>
      </motion.form>

      <div className="lg:col-span-3">
        <p className="mb-3 text-sm font-bold text-ink-500">الحملات السابقة</p>
        {!items ? <PageLoader /> : items.length === 0 ? (
          <div className="card"><EmptyState icon={Megaphone} title="لا توجد حملات بعد" /></div>
        ) : (
          <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
            {items.map((a) => (
              <motion.div variants={fadeUp} key={a.id} className="card p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-bold text-ink-800">{a.title}</p>
                    <p className="mt-1 line-clamp-2 text-sm text-ink-500">{a.body}</p>
                  </div>
                  <span className="chip shrink-0 bg-ink-100 text-ink-600">{AUDIENCES.find((x) => x.key === a.audience)?.label || a.audience}</span>
                </div>
                <p className="mt-2 text-xs text-ink-400">{fmtDateTime(a.createdAt)}</p>
              </motion.div>
            ))}
          </motion.div>
        )}
      </div>
    </div>
  )
}
