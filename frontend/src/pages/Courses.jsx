import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { BookOpen, Plus, Users2 } from 'lucide-react'
import api from '../lib/api'
import { Modal, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { fmtMoney, GRADES } from '../lib/format'
import { useAuth } from '../lib/auth'
import { courseHref } from '../lib/courseNavigation'
import ImageUpload from '../components/ImageUpload'
import StudentCatalog from '../features/student-catalog/StudentCatalog'

const GRADIENTS = ['from-brand-500 to-brand-800', 'from-emerald-500 to-teal-700', 'from-sky-500 to-blue-700', 'from-amber-500 to-orange-700', 'from-rose-500 to-pink-700', 'from-teal-500 to-cyan-700']

export default function Courses() {
  const { user } = useAuth()
  const [courses, setCourses] = useState(null)
  const [teachers, setTeachers] = useState([])
  const [showNew, setShowNew] = useState(false)
  const canManage = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER'].includes(user.role)
  const isStudent = user.role === 'STUDENT'
  const isParent = user.role === 'PARENT'
  const [childrenCourseIds, setChildrenCourseIds] = useState(null)

  const [subject, setSubject] = useState('')
  const [teacherId, setTeacherId] = useState('')
  const load = () => api.get('/courses').then((r) => setCourses(r.data))
  const loadChildren = () => api.get('/dashboard/parent').then(async (r) => {
    const children = r.data.children || []
    const details = await Promise.all(children.map((c) => api.get(`/students/${c.id}`)))
    const ids = new Set(details.flatMap((d) => d.data.enrollments.map((e) => e.courseId)))
    setChildrenCourseIds(ids)
  })

  useEffect(() => {
    if (isStudent) return
    load()
    api.get('/users/by-role/TEACHER').then((r) => setTeachers(r.data)).catch(() => {})
    if (isParent) loadChildren()
  }, [])

  // A student's page is their own: their courses, what waits for payment, and what's offered for their year.
  if (isStudent) return <StudentCatalog />

  if (!courses || (isParent && !childrenCourseIds)) return <PageLoader />
  const scoped = isParent ? courses.filter((c) => childrenCourseIds.has(c.id)) : user.role === 'TEACHER' ? courses.filter(c => c.teacherId === user.id) : courses
  const subjects = [...new Set(scoped.map((c) => c.subject).filter(Boolean))]
  const filtered = scoped.filter((c) => (!subject || c.subject === subject) && (!teacherId || String(c.teacherId) === teacherId))

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
            return (
              <motion.div variants={fadeUp} key={c.id} className="card overflow-hidden">
                <Link to={courseHref(c, user.role)} className="block hover:shadow-glow transition-shadow">
                  <div className={`relative h-28 bg-gradient-to-br ${GRADIENTS[i % GRADIENTS.length]} p-5`}>
                    {c.coverUrl && <img src={c.coverUrl} alt="" className="absolute inset-0 h-full w-full object-cover" />}
                    <div className="absolute inset-0 bg-grid opacity-30" />
                    <BookOpen className="relative text-white/90" size={26} />
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
              </motion.div>
            )
          })}
        </motion.div>
      )}

      <NewCourse open={showNew} onClose={() => setShowNew(false)} teachers={teachers} onSaved={() => { setShowNew(false); load() }} />
    </div>
  )
}

function NewCourse({ open, onClose, teachers, onSaved }) {
  const [form, setForm] = useState({ title: '', subject: '', gradeLevel: 'ثانوي', grade: '', price: 0, teacherId: '', schedule: '', coverUrl: '' })
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
        <div>
          <label className="label">صورة الكورس (اختياري)</label>
          <ImageUpload value={form.coverUrl} label="رفع صورة الكورس" onChange={(url) => setForm((f) => ({ ...f, coverUrl: url }))} />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || !form.title} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ'}</button>
        </div>
      </div>
    </Modal>
  )
}
