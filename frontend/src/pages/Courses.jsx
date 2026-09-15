import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { BookOpen, Plus, Users2, Layers, CheckCircle2, UserPlus, CreditCard, Smartphone, Wallet, ShieldAlert, KeyRound } from 'lucide-react'
import api from '../lib/api'
import { Modal, PageLoader, EmptyState, Spinner, stagger, fadeUp } from '../components/ui'
import { fmtMoney, GRADES } from '../lib/format'
import { useAuth } from '../lib/auth'
import { courseHref } from '../lib/courseNavigation'

const GRADIENTS = ['from-brand-500 to-indigo-700', 'from-emerald-500 to-teal-700', 'from-sky-500 to-blue-700', 'from-amber-500 to-orange-700', 'from-rose-500 to-pink-700', 'from-violet-500 to-purple-700']

export default function Courses() {
  const { user } = useAuth()
  const [courses, setCourses] = useState(null)
  const [teachers, setTeachers] = useState([])
  const [showNew, setShowNew] = useState(false)
  const [myCourseIds, setMyCourseIds] = useState(null)
  const [enrollingId, setEnrollingId] = useState(null)
  const canManage = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'CONTENT_MANAGER'].includes(user.role)
  const isStudent = user.role === 'STUDENT'
  const isParent = user.role === 'PARENT'
  const [childrenCourseIds, setChildrenCourseIds] = useState(null)
  const [payingOrder, setPayingOrder] = useState(null)
  const [redeemFor, setRedeemFor] = useState(null)

  const [subject, setSubject] = useState('')
  const [teacherId, setTeacherId] = useState('')
  const load = () => api.get('/courses').then((r) => setCourses(r.data))
  const loadMine = () => api.get('/students/me').then((r) => setMyCourseIds(new Set(r.data.enrollments.map((e) => e.courseId))))
  const loadChildren = () => api.get('/dashboard/parent').then(async (r) => {
    const children = r.data.children || []
    const details = await Promise.all(children.map((c) => api.get(`/students/${c.id}`)))
    const ids = new Set(details.flatMap((d) => d.data.enrollments.map((e) => e.courseId)))
    setChildrenCourseIds(ids)
  })

  useEffect(() => {
    load()
    api.get('/users/by-role/TEACHER').then((r) => setTeachers(r.data)).catch(() => {})
    if (isStudent) loadMine()
    if (isParent) loadChildren()
  }, [])

  if (!courses || (isParent && !childrenCourseIds)) return <PageLoader />
  const scoped = isParent ? courses.filter((c) => childrenCourseIds.has(c.id)) : user.role === 'TEACHER' ? courses.filter(c => c.teacherId === user.id) : courses
  const subjects = [...new Set(scoped.map((c) => c.subject).filter(Boolean))]
  const filtered = scoped.filter((c) => (!subject || c.subject === subject) && (!teacherId || String(c.teacherId) === teacherId))

  const enroll = async (course) => {
    setEnrollingId(course.id)
    try {
      if (!course.price || Number(course.price) === 0) {
        await api.post('/enrollments/self', { courseId: course.id })
        await loadMine()
        return
      }
      const { data } = await api.post('/checkout/orders', { courseId: course.id })
      if (data.liveGateway && data.checkoutUrl) {
        window.location.href = data.checkoutUrl
        return
      }
      setPayingOrder(data)
    } finally {
      setEnrollingId(null)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <select className="input max-w-[180px]" value={subject} onChange={(e) => setSubject(e.target.value)}>
          <option value="">كل المواد</option>
          {subjects.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <select className="input max-w-[200px]" value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
          <option value="">كل المدرسين</option>
          {teachers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
        </select>
        <p className="text-sm text-ink-400">{filtered.length} كورس</p>
        {canManage && <button onClick={() => setShowNew(true)} className="btn-primary mr-auto"><Plus size={18} /> كورس جديد</button>}
      </div>

      {filtered.length === 0 ? <div className="card"><EmptyState icon={BookOpen} title="لا توجد كورسات" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((c, i) => {
            const enrolled = myCourseIds?.has(c.id)
            return (
              <motion.div variants={fadeUp} key={c.id} className="card overflow-hidden">
                <Link to={courseHref(c, user.role)} className="block hover:shadow-glow transition-shadow">
                  <div className={`relative h-28 bg-gradient-to-br ${GRADIENTS[i % GRADIENTS.length]} p-5`}>
                    <div className="absolute inset-0 bg-grid opacity-30" />
                    <BookOpen className="text-white/90" size={26} />
                    <span className="absolute bottom-4 left-5 chip bg-white/20 text-white backdrop-blur">{c.subject}</span>
                    {c.discountPercent > 0 && <span className="absolute bottom-4 right-5 chip bg-rose-500 text-white">خصم {c.discountPercent}%</span>}
                  </div>
                  <div className="p-5 pb-3">
                    <p className="font-extrabold text-ink-800 leading-snug">{c.title}</p>
                    <p className="mt-1 text-xs text-ink-400">{c.teacherName || 'بدون مدرس'}{c.schedule ? ` · ${c.schedule}` : ''}</p>
                    <div className="mt-4 flex items-center justify-between">
                      <span className="inline-flex items-center gap-1.5 text-sm text-ink-500"><Users2 size={15} /> {c.studentCount} طالب</span>
                      {c.discountPercent > 0 ? (
                        <span className="flex items-baseline gap-1.5">
                          <span className="text-xs text-ink-400 line-through">{fmtMoney(c.price)}</span>
                          <span className="font-bold text-rose-600">{fmtMoney(c.finalPrice)}</span>
                        </span>
                      ) : (
                        <span className="font-bold text-brand-600">{fmtMoney(c.price)}</span>
                      )}
                    </div>
                  </div>
                </Link>
                {isStudent && (
                  <div className="px-5 pb-5">
                    {enrolled ? (
                      <span className="flex items-center justify-center gap-1.5 rounded-2xl bg-emerald-50 py-2.5 text-sm font-bold text-emerald-700">
                        <CheckCircle2 size={16} /> أنت ملتحق بهذا الكورس
                      </span>
                    ) : (
                      <div className="space-y-1.5">
                        <button onClick={() => enroll(c)} disabled={enrollingId === c.id} className="btn-primary w-full">
                          {enrollingId === c.id ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <UserPlus size={16} />}
                          {c.price > 0 ? 'اشترك الآن' : 'التحق بالكورس'}
                        </button>
                        {c.price > 0 && (
                          <button type="button" onClick={() => setRedeemFor(c)} className="btn-ghost w-full text-xs">
                            <KeyRound size={14} /> عندك كود اشتراك؟
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </motion.div>
            )
          })}
        </motion.div>
      )}

      <NewCourse open={showNew} onClose={() => setShowNew(false)} teachers={teachers} onSaved={() => { setShowNew(false); load() }} />
      {payingOrder && (
        <PaymentModal
          order={payingOrder}
          onClose={() => setPayingOrder(null)}
          onPaid={async () => { setPayingOrder(null); await loadMine() }}
        />
      )}
      {redeemFor && (
        <RedeemCodeModal
          course={redeemFor}
          onClose={() => setRedeemFor(null)}
          onRedeemed={async () => { setRedeemFor(null); await loadMine() }}
        />
      )}
    </div>
  )
}

function RedeemCodeModal({ course, onClose, onRedeemed }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (e) => {
    e.preventDefault()
    if (!code.trim()) { setError('اكتب الكود'); return }
    setBusy(true); setError('')
    try { await api.post('/courses/redeem-code', { code: code.trim() }); onRedeemed() }
    catch (err) { setError(err.response?.data?.message || 'تعذّر تفعيل الكود') } finally { setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={`تفعيل كود — ${course.title}`}>
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm leading-7 text-ink-500">اكتب الكود اللي استلمته من المستر بعد تأكيد التحويل.</p>
        <div className="relative"><KeyRound size={18} className="absolute right-3.5 top-3 text-brand-500" />
          <input dir="ltr" autoFocus className="input pr-11 text-center font-mono tracking-widest" value={code} onChange={(e) => setCode(e.target.value)} placeholder="XXXX-XXXX" />
        </div>
        {error && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center py-3">
          {busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : 'تفعيل'}
        </button>
      </form>
    </Modal>
  )
}

const DEMO_METHODS = [
  { key: 'CARD', label: 'بطاقة بنكية', icon: CreditCard },
  { key: 'WALLET', label: 'محفظة إلكترونية', icon: Wallet },
  { key: 'FAWRY', label: 'فوري', icon: Smartphone },
]

function PaymentModal({ order, onClose, onPaid }) {
  const [method, setMethod] = useState('CARD')
  const [paying, setPaying] = useState(false)
  const [error, setError] = useState('')
  const confirmDemoPay = async () => {
    setPaying(true); setError('')
    try { await api.post(`/checkout/orders/${order.reference}/pay`, { method }); onPaid() }
    catch (e) { setError(e.response?.data?.message || 'تعذّر إتمام الدفع') }
    finally { setPaying(false) }
  }
  return (
    <Modal open onClose={onClose} title="إتمام الدفع">
      <div className="space-y-4">
        <div className="rounded-2xl bg-ink-50 p-4">
          <p className="font-extrabold text-ink-800">{order.courseTitle}</p>
          <p className="mt-1 text-2xl font-black text-brand-600">{fmtMoney(order.amount)}</p>
        </div>
        {order.liveGateway ? (
          <p className="flex items-start gap-2 rounded-2xl bg-rose-50 p-4 text-sm leading-7 text-rose-700">
            <ShieldAlert size={18} className="mt-0.5 shrink-0" />
            تعذّر تجهيز رابط بوابة الدفع الحقيقية في الوقت الحالي. حاول مرة أخرى بعد قليل، أو تواصل مع الإدارة.
          </p>
        ) : (
          <>
            <p className="flex items-start gap-2 rounded-2xl bg-amber-50 p-4 text-xs leading-6 text-amber-800">
              <ShieldAlert size={16} className="mt-0.5 shrink-0" />
              وضع تجريبي: لا تُوجد بوابة دفع إلكتروني حقيقية مفعّلة بعد على هذه المنصة، فلن يُخصم أي مبلغ فعلي — اختيارك هنا لتجربة تدفّق الشراء فقط.
            </p>
            <div>
              <label className="label">طريقة الدفع (تجريبي)</label>
              <div className="flex flex-wrap gap-2">
                {DEMO_METHODS.map((m) => (
                  <button key={m.key} type="button" onClick={() => setMethod(m.key)}
                    className={`chip border ${method === m.key ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>
                    <m.icon size={14} /> {m.label}
                  </button>
                ))}
              </div>
            </div>
            {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
            <div className="flex justify-end gap-2">
              <button onClick={onClose} className="btn-ghost">إلغاء</button>
              <button onClick={confirmDemoPay} disabled={paying} className="btn-primary">
                {paying ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <CreditCard size={16} />}
                تأكيد الدفع التجريبي
              </button>
            </div>
          </>
        )}
      </div>
    </Modal>
  )
}

function NewCourse({ open, onClose, teachers, onSaved }) {
  const [form, setForm] = useState({ title: '', subject: '', gradeLevel: 'ثانوي', grade: '', price: 0, teacherId: '', schedule: '' })
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async () => {
    setSaving(true)
    try { await api.post('/courses', { ...form, price: Number(form.price), teacherId: form.teacherId || null }); onSaved() } finally { setSaving(false) }
  }
  return (
    <Modal open={open} onClose={onClose} title="إضافة كورس جديد">
      <div className="space-y-4">
        <div><label className="label">عنوان الكورس</label><input className="input" value={form.title} onChange={set('title')} placeholder="مثال: الرياضيات — الصف الثالث الثانوي" /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">المادة</label><input className="input" value={form.subject} onChange={set('subject')} placeholder="رياضيات" /></div>
          <div><label className="label">السنة الدراسية</label>
            <select className="input" value={form.grade} onChange={set('grade')}>
              <option value="">— اختر —</option>
              {GRADES.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">المدرس</label>
            <select className="input" value={form.teacherId} onChange={set('teacherId')}>
              <option value="">— اختر مدرساً —</option>
              {teachers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
            </select>
          </div>
          <div><label className="label">السعر (ج.م)</label><input type="number" className="input" value={form.price} onChange={set('price')} /></div>
        </div>
        <div><label className="label">المواعيد</label><input className="input" value={form.schedule} onChange={set('schedule')} placeholder="الأحد والثلاثاء 6:00م" /></div>
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || !form.title} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ'}</button>
        </div>
      </div>
    </Modal>
  )
}
