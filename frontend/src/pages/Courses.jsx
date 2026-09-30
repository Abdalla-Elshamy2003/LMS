import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { BookOpen, Eye, EyeOff, Pencil, Plus, Save, Trash2, Users2 } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { ADMIN_ROLES } from '../lib/roles'
import ActionMenu from '../components/ActionMenu'
import { DeleteCourseModal } from './TeacherStudio'
import { Modal, PageLoader, EmptyState, Spinner, stagger, fadeUp } from '../components/ui'
import { fmtMoney, GRADES } from '../lib/format'
import { useAuth } from '../lib/auth'
import { courseHref } from '../lib/courseNavigation'
import ImageUpload from '../components/ImageUpload'
import WeeklySchedulePicker, { emptySchedule, scheduleProblem, scheduleText } from '../components/WeeklySchedulePicker'
import StudentCatalog from '../features/student-catalog/StudentCatalog'

const GRADIENTS = ['from-brand-500 to-brand-800', 'from-emerald-500 to-teal-700', 'from-sky-500 to-blue-700', 'from-amber-500 to-orange-700', 'from-rose-500 to-pink-700', 'from-teal-500 to-cyan-700']

export default function Courses() {
  const { user } = useAuth()
  const [courses, setCourses] = useState(null)
  const [teachers, setTeachers] = useState([])
  const [showNew, setShowNew] = useState(false)
  const canManage = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER'].includes(user.role)
  // Editing, hiding and deleting a course is the teacher's (and head office's), not their assistants'.
  const canEdit = [...ADMIN_ROLES, 'TEACHER', 'CONTENT_MANAGER'].includes(user.role)
  const navigate = useNavigate()
  const [editing, setEditing] = useState(null)
  const [removing, setRemoving] = useState(null)
  const [flash, setFlash] = useState({ ok: '', error: '' })
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

  // Head office changes a teacher's course through its own endpoints (and edits it on the teacher's page); the teacher,
  // or head office in its own school, through the course's.
  const viaAdmin = (c) => ADMIN_ROLES.includes(user.role) && c.academyId
  const courseUrl = (c) => viaAdmin(c) ? `/admin/courses/${c.id}` : `/courses/${c.id}`
  const setVisible = async (c, visible) => {
    try {
      await api.put(courseUrl(c), { status: visible ? 'ACTIVE' : 'HIDDEN' })
      await load(); setFlash({ ok: visible ? `${c.title} بقى ظاهر في الموقع` : `${c.title} اتخفى من الموقع`, error: '' })
    } catch (e) { setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر تغيير ظهور الكورس') }) }
  }
  const courseActions = (c) => [
    { label: 'تعديل', icon: Pencil, onClick: () => viaAdmin(c) ? navigate(`/app/control/teachers/${c.academyId}`) : setEditing(c) },
    c.status === 'ACTIVE'
      ? { label: 'إخفاء من الموقع', icon: EyeOff, onClick: () => setVisible(c, false) }
      : { label: 'إظهار في الموقع', icon: Eye, onClick: () => setVisible(c, true) },
    { label: 'حذف', icon: Trash2, danger: true, onClick: () => setRemoving(c) },
  ]

  const scoped =isParent ? courses.filter((c) => childrenCourseIds.has(c.id)) : user.role === 'TEACHER' ? courses.filter(c => c.teacherId === user.id) : courses
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
      {flash.error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{flash.error}</div>}
      {flash.ok && <div role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">{flash.ok}</div>}

      {filtered.length === 0 ? <div className="card"><EmptyState icon={BookOpen} title="لا توجد كورسات" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((c, i) => {
            return (
              <motion.div variants={fadeUp} key={c.id} className="card relative overflow-hidden">
                {canEdit && (
                  <div className="absolute left-3 top-3 z-10 flex items-center gap-1.5">
                    {c.status === 'HIDDEN' && <span className="chip bg-white/90 text-ink-600 shadow-sm"><EyeOff size={13} /> مخفي من الموقع</span>}
                    <span className="rounded-xl bg-white/90 shadow-sm">
                      <ActionMenu label={`خيارات ${c.title}`} items={courseActions(c)} />
                    </span>
                  </div>
                )}
                <Link to={courseHref(c, user.role)} className="block hover:shadow-glow transition-shadow">
                  <div className={`relative bg-gradient-to-br ${GRADIENTS[i % GRADIENTS.length]} p-5 ${c.coverUrl ? 'aspect-[4/3]' : 'h-28'}`}>
                    {c.coverUrl
                      ? <img src={c.coverUrl} alt={c.title} className="absolute inset-0 h-full w-full object-cover" />
                      : <>
                          <div className="absolute inset-0 bg-grid opacity-30" />
                          <BookOpen className="relative text-white/90" size={26} />
                          <span className="absolute bottom-4 left-5 chip bg-white/20 text-white backdrop-blur">{c.subject}</span>
                        </>}
                    {c.discountPercent > 0 && <span className="absolute bottom-4 right-5 chip bg-rose-500 text-white">خصم {c.discountPercent}%</span>}
                  </div>
                  <div className="p-5 pb-3">
                    <p className="font-extrabold text-ink-800 leading-snug">{c.title}</p>
                    <p className="mt-1 text-xs text-ink-400">{c.grade ? `${c.grade} · ` : ''}{c.teacherName || 'بدون مدرس'}{c.schedule ? ` · ${c.schedule}` : ''}</p>
                    <div className="mt-4 flex items-center justify-between">
                      <span className="inline-flex items-center gap-1.5 text-sm text-ink-500"><Users2 size={15} /> {c.studentCount} طالب</span>
                      {/* A paid course of a school year is sold with that year's subscription, priced in «خطط الاشتراك». */}
                      {c.grade && Number(c.finalPrice ?? c.price) > 0 ? (
                        <span className="chip bg-brand-50 text-brand-700">ضمن اشتراك السنة</span>
                      ) : Number(c.finalPrice ?? c.price) <= 0 ? (
                        <span className="font-bold text-emerald-600">مجاني</span>
                      ) : c.discountPercent > 0 ? (
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

      <NewCourse open={showNew} onClose={() => setShowNew(false)} teachers={teachers}
        onSaved={(warning) => { setShowNew(false); load(); setFlash(warning ? { ok: '', error: warning } : { ok: 'اتضاف الكورس', error: '' }) }} />
      <EditCourse course={editing} onClose={() => setEditing(null)}
        onSaved={async (title) => { setEditing(null); await load(); setFlash({ ok: `اتحفظ كورس ${title}`, error: '' }) }} />
      <DeleteCourseModal open={!!removing} course={removing && { ...removing, students: removing.studentCount }} url={removing && courseUrl(removing)}
        onClose={() => setRemoving(null)} fail={(e, fallback) => setFlash({ ok: '', error: apiErrorMessage(e, fallback) })}
        onDeleted={async () => { const title = removing.title; setRemoving(null); await load(); setFlash({ ok: `اتحذف كورس ${title}`, error: '' }) }} />
    </div>
  )
}

/** A teacher editing one of their courses from the courses page. */
function EditCourse({ course, onClose, onSaved }) {
  const [form, setForm] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    if (!course) { setForm(null); return }
    setError('')
    const s = course
    setForm({ title: s.title || '', subject: s.subject || '', gradeLevel: s.gradeLevel || '', grade: s.grade || '', price: s.price ?? 0,
      discountPercent: s.discountPercent ?? 0, coverUrl: s.coverUrl || '', description: '' })
    // The list has no descriptions; the course itself does.
    api.get(`/courses/${course.id}`).then(r => setForm(f => f && ({ ...f, description: r.data.description || '' }))).catch(() => {})
  }, [course])
  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }))
  const save = async (e) => {
    e.preventDefault()
    if (!form.title.trim()) return setError('اكتب اسم الكورس')
    setBusy(true); setError('')
    try {
      const { data } = await api.put(`/courses/${course.id}`, { ...form, price: Number(form.price) || 0, discountPercent: Number(form.discountPercent) || 0 })
      onSaved(data.title)
    } catch (err) { setError(apiErrorMessage(err, 'تعذّر حفظ الكورس')) }
    finally { setBusy(false) }
  }
  return (
    <Modal open={!!course} onClose={onClose} title={`تعديل ${course?.title || 'الكورس'}`} wide>
      {form && (
        <form onSubmit={save} className="space-y-4">
          {error && <p role="alert" className="rounded-2xl bg-rose-50 p-3 text-sm font-semibold text-rose-700">{error}</p>}
          <div><label className="label">اسم الكورس *</label><input className="input" value={form.title} onChange={set('title')} maxLength={200} /></div>
          <div className="grid gap-3 sm:grid-cols-3">
            <div><label className="label">السنة الدراسية</label>
              <input className="input" list="course-edit-years" value={form.grade} onChange={set('grade')} maxLength={80} />
              <datalist id="course-edit-years">{GRADES.map(g => <option key={g} value={g} />)}</datalist></div>
            <div><label className="label">المرحلة</label>
              <select className="input" value={form.gradeLevel} onChange={set('gradeLevel')}>
                <option value="">—</option>{['ابتدائي', 'إعدادي', 'ثانوي'].map(g => <option key={g} value={g}>{g}</option>)}
              </select></div>
            <div><label className="label">المادة</label><input className="input" value={form.subject} onChange={set('subject')} maxLength={100} /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label">السعر (ج.م)</label><input type="number" min="0" className="input" value={form.price} onChange={set('price')} /></div>
            <div><label className="label">الخصم %</label><input type="number" min="0" max="100" className="input" value={form.discountPercent} onChange={set('discountPercent')} /></div>
          </div>
          <div><label className="label">وصف الكورس</label><textarea rows={3} className="input" value={form.description} onChange={set('description')} maxLength={5000} /></div>
          <div><label className="label">صورة الكورس</label><ImageUpload value={form.coverUrl} label="رفع صورة الكورس" onChange={(url) => setForm(f => ({ ...f, coverUrl: url }))} /></div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-ghost" onClick={onClose}>إلغاء</button>
            <button type="submit" disabled={busy} className="btn-primary">{busy ? <Spinner className="h-4 w-4" /> : <><Save size={16} /> حفظ</>}</button>
          </div>
        </form>
      )}
    </Modal>
  )
}

function NewCourse({ open, onClose, teachers, onSaved }) {
  const [form, setForm] = useState({ title: '', subject: '', gradeLevel: 'ثانوي', grade: '', price: 0, teacherId: '', coverUrl: '' })
  const [when, setWhen] = useState(emptySchedule)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  // The picked days and hours become the course's schedule line and its weekly slots in «الجدول الدراسي».
  const save = async () => {
    const problem = scheduleProblem(when, { needEnd: true })
    if (problem) return setError(problem)
    setSaving(true); setError('')
    try {
      const { data } = await api.post('/courses', { ...form, schedule: scheduleText(when), price: Number(form.price), teacherId: form.teacherId || null })
      const failed = []
      for (const day of when.days) {
        try { await api.post('/schedule', { courseId: data.summary.id, dayOfWeek: day, startTime: when.start, endTime: when.end }) }
        catch (e) { failed.push(apiErrorMessage(e, 'تعذّر إضافة الموعد')) }
      }
      setForm({ title: '', subject: '', gradeLevel: 'ثانوي', grade: '', price: 0, teacherId: '', coverUrl: '' }); setWhen(emptySchedule())
      onSaved(failed.length ? `اتضاف الكورس، بس في موعد ما اتضافش للجدول: ${[...new Set(failed)].join(' — ')}` : '')
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر إضافة الكورس')) }
    finally { setSaving(false) }
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
        <WeeklySchedulePicker value={when} onChange={setWhen} needEnd label="مواعيد الحصص (اختياري)" />
        <div>
          <label className="label">صورة الكورس (اختياري)</label>
          <ImageUpload value={form.coverUrl} label="رفع صورة الكورس" onChange={(url) => setForm((f) => ({ ...f, coverUrl: url }))} />
        </div>
        {error && <p role="alert" className="rounded-2xl bg-rose-50 p-3 text-sm font-semibold text-rose-700">{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || !form.title} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ'}</button>
        </div>
      </div>
    </Modal>
  )
}
