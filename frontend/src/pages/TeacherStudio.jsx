import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowRight, BookOpen, ExternalLink, Eye, EyeOff, GraduationCap, ImagePlus, KeyRound, LayoutTemplate,
  Pencil, PlayCircle, Plus, Save, Trash2, TriangleAlert, UserRound,
} from 'lucide-react'
import ActionMenu from '../components/ActionMenu'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { useAuth } from '../lib/auth'
import { GRADES, fmtMoney } from '../lib/format'
import { Modal, PageLoader, Spinner } from '../components/ui'
import ImageUpload from '../components/ImageUpload'

const STAGES = ['ابتدائي', 'إعدادي', 'ثانوي']
const MAX_IMAGE = 3 * 1024 * 1024
const blankTeacher = { name: '', subject: '', slug: '', phone: '', tagline: '', headline: '', description: '', aboutText: '', username: '', password: '', email: '', published: true }
let draftKey = 0
const blankCourse = () => ({ key: `new-${++draftKey}`, title: '', subject: '', gradeLevel: 'ثانوي', grade: '', description: '', price: 0, discountPercent: 0, coverUrl: '', status: 'ACTIVE', students: 0 })
// PUT /admin/courses answers with the control center's row, which calls the year "year".
const asCourse = (c) => ({ ...c, key: `c-${c.id}`, grade: c.grade ?? c.year ?? '', discountPercent: c.discountPercent ?? 0 })
const coursePayload = (c) => ({
  title: c.title, subject: c.subject || '', gradeLevel: c.gradeLevel || '', grade: c.grade || '', description: c.description || '',
  price: Number(c.price) || 0, discountPercent: Number(c.discountPercent) || 0, coverUrl: c.coverUrl || '', status: c.status,
})

/**
 * Head office's one page for a teacher: who they are, their public page, their photos, how they sign in, whether they
 * show on the site, and every course they teach with its details — added, edited, hidden or deleted from here.
 * `/app/control/teachers/new` makes the teacher and their courses in one go; `/app/control/teachers/:id` edits them.
 */
export default function TeacherStudio() {
  const { academyId } = useParams()
  const creating = academyId === 'new'
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const [detail, setDetail] = useState(null)
  const [form, setForm] = useState(blankTeacher)
  const [courses, setCourses] = useState(() => (creating ? [blankCourse()] : []))
  const [files, setFiles] = useState({ photo: null, cover: null })
  const [busy, setBusy] = useState(false)
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const [deleting, setDeleting] = useState(false)
  const say = (ok) => { setFlash({ ok, error: '' }); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  const fail = (e, fallback) => { setFlash({ ok: '', error: apiErrorMessage(e, fallback) }); window.scrollTo({ top: 0, behavior: 'smooth' }) }

  const fill = (d) => {
    setDetail(d)
    setForm({ name: d.name, subject: d.subject, slug: d.slug, phone: d.phone, tagline: d.tagline, headline: d.headline, description: d.description,
      aboutText: d.aboutText, username: d.username, password: '', email: d.email, published: d.published })
    setCourses(d.courses.map(asCourse))
  }
  useEffect(() => {
    setFiles({ photo: null, cover: null })
    if (creating) { setDetail(null); setForm(blankTeacher); setCourses([blankCourse()]); return }
    setDetail(null)
    api.get(`/admin/teachers/${academyId}`).then(r => fill(r.data)).catch(e => { fail(e, 'تعذّر تحميل بيانات المدرس'); setDetail(false) })
  }, [academyId])
  useEffect(() => { if (location.state?.flash) setFlash({ ok: location.state.flash, error: '' }) }, [location.state])

  if (!creating && detail === null) return <PageLoader />
  if (!creating && detail === false) return <Back flash={flash} />

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }))

  const saveTeacher = async (e) => {
    e.preventDefault()
    if (!form.name.trim() || !form.subject.trim()) return fail(null, 'اكتب اسم المدرس والمادة')
    setBusy(true)
    try {
      if (creating) {
        if (courses.some(c => !c.title.trim())) return fail(null, 'اكتب اسم كل كورس أو شيل الكورس الفاضي')
        const { data } = await api.post('/admin/teachers', { ...form, courses: courses.map(coursePayload) })
        const failed = []
        for (const slot of ['photo', 'cover']) {
          if (!files[slot]) continue
          try { const body = new FormData(); body.append('file', files[slot]); await api.post(`/academies/${data.academyId}/images/${slot}`, body) }
          catch { failed.push(slot === 'photo' ? 'صورة المدرس' : 'صورة الغلاف') }
        }
        const msg = `اتضاف ${data.name} و${data.courses.length.toLocaleString('ar-EG')} كورس`
          + (failed.length ? ` — لكن ${failed.join(' و')} ما اترفعتش، ارفعها تاني من هنا` : '')
        navigate(`/app/control/teachers/${data.academyId}`, { replace: true, state: { flash: msg } })
      } else {
        const { published, slug, ...rest } = form
        const { data } = await api.put(`/admin/teachers/${detail.academyId}`, rest)
        // Course cards keep whatever is still being typed in them; only the teacher's own fields refresh.
        setDetail(data)
        setForm(f => ({ ...f, name: data.name, subject: data.subject, tagline: data.tagline, headline: data.headline, description: data.description,
          aboutText: data.aboutText, phone: data.phone, username: data.username, password: '', email: data.email }))
        say('اتحفظت بيانات المدرس')
      }
    } catch (err) { fail(err, creating ? 'تعذّر إضافة المدرس' : 'تعذّر حفظ بيانات المدرس') }
    finally { setBusy(false) }
  }

  const togglePublished = async () => {
    if (creating) return setForm(f => ({ ...f, published: !f.published }))
    try {
      await api.put(`/admin/teachers/${detail.academyId}/published`, { published: !detail.published })
      setDetail(d => ({ ...d, published: !d.published }))
      say(detail.published ? `${detail.name} اتخفى من الموقع` : `${detail.name} بقى ظاهر في الموقع`)
    } catch (e) { fail(e, 'تعذّر تغيير ظهور المدرس') }
  }

  const uploadImage = async (slot, file) => {
    if (creating) return setFiles(f => ({ ...f, [slot]: file }))
    try {
      const body = new FormData(); body.append('file', file)
      await api.post(`/academies/${detail.academyId}/images/${slot}`, body)
      const { data } = await api.get(`/admin/teachers/${detail.academyId}`)
      setDetail(d => ({ ...d, photoUrl: data.photoUrl, coverUrl: data.coverUrl }))
      say(slot === 'photo' ? 'اتغيرت صورة المدرس' : 'اتغيرت صورة الغلاف')
    } catch (e) { fail(e, 'تعذّر رفع الصورة') }
  }

  const published = creating ? form.published : detail.published
  const savedCourses = courses.filter(c => c.id).length

  return (
    <>
    <form onSubmit={saveTeacher} className="space-y-6">
      <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] via-[#0a2335] to-[#05111c] p-6 text-white sm:p-7">
        <Link to="/app/control?tab=teachers" className="inline-flex items-center gap-1.5 text-sm text-sky-100/80 hover:text-white"><ArrowRight size={16} /> كل المدرسين</Link>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <span className="grid h-16 w-16 shrink-0 place-items-center overflow-hidden rounded-2xl bg-white/10">
              {!creating && detail.photoUrl ? <img src={detail.photoUrl} alt="" className="h-full w-full object-cover object-top" /> : <UserRound className="text-sky-100/60" size={30} />}
            </span>
            <div>
              <h2 className="text-2xl font-black">{creating ? (form.name.trim() || 'مدرس جديد') : detail.name}</h2>
              <p className="mt-1 text-sm text-sky-100/80">
                {creating ? 'املا بيانات المدرس وكورساته، واضغط «إضافة المدرس» مرة واحدة.'
                  : `${detail.subject} · ${detail.students.toLocaleString('ar-EG')} طالب · ${savedCourses.toLocaleString('ar-EG')} كورس`}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={togglePublished} aria-pressed={published}
              className={`chip px-3 py-2 text-sm ${published ? 'bg-emerald-400/15 text-emerald-200' : 'bg-white/10 text-sky-100'}`}>
              {published ? <><Eye size={15} /> ظاهر في الموقع</> : <><EyeOff size={15} /> مخفي من الموقع</>}
            </button>
            {!creating && detail.published && <a href={`/t/${detail.slug}`} target="_blank" rel="noreferrer" className="chip bg-white/10 px-3 py-2 text-sm text-sky-100 hover:bg-white/20"><ExternalLink size={15} /> صفحته</a>}
          </div>
        </div>
      </div>

      {flash.error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{flash.error}</div>}
      {flash.ok && <div role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">{flash.ok}</div>}

      <div className="grid gap-6 xl:grid-cols-2">
        <Section icon={GraduationCap} title="بيانات المدرس">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="اسم المدرس *"><input className="input" value={form.name} onChange={set('name')} placeholder="مستر ..." maxLength={120} /></Field>
            <Field label="المادة *"><input className="input" value={form.subject} onChange={set('subject')} placeholder="الكيمياء" maxLength={100} /></Field>
            <Field label="رابط صفحته *" hint={creating ? 'حروف إنجليزية صغيرة وأرقام وشرطات — مبيتغيرش بعد كده' : 'الرابط مبيتغيرش'}>
              <div className="flex items-center gap-2" dir="ltr">
                <span className="text-xs text-ink-400">/t/</span>
                <input className="input" value={form.slug} onChange={set('slug')} placeholder="mr-name" disabled={!creating} maxLength={70} />
              </div>
            </Field>
            <Field label="رقم التواصل"><input dir="ltr" className="input text-right" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" maxLength={25} /></Field>
          </div>
        </Section>

        <Section icon={KeyRound} title="بيانات الدخول" hint="اللي المدرس بيدخل بيها على لوحته.">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="اسم المستخدم *"><input dir="ltr" className="input" value={form.username} onChange={set('username')} placeholder="mr.name" maxLength={50} autoComplete="off" /></Field>
            <Field label={creating ? 'كلمة المرور *' : 'كلمة مرور جديدة'} hint={creating ? '١٠ أحرف على الأقل، حروف وأرقام' : 'سيبها فاضية لو مش عايز تغيّرها'}>
              <input dir="ltr" type="password" className="input" value={form.password} onChange={set('password')} autoComplete="new-password" />
            </Field>
            <Field label="الإيميل (اختياري)" hint="يقدر يدخل بيه بدل اسم المستخدم" wide>
              <input dir="ltr" type="email" className="input" value={form.email} onChange={set('email')} placeholder="teacher@example.com" maxLength={120} />
            </Field>
          </div>
        </Section>
      </div>

      <Section icon={LayoutTemplate} title="صفحته في الموقع" hint="اللي بيظهر للطلاب في صفحة المدرس. أي خانة تسيبها فاضية هنكتبلها سطر مناسب من الاسم والمادة.">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="اللقب والتخصص"><input className="input" value={form.tagline} onChange={set('tagline')} placeholder="مدرس الكيمياء للثانوية العامة" maxLength={150} /></Field>
          <Field label="العنوان الرئيسي"><input className="input" value={form.headline} onChange={set('headline')} placeholder="الكيمياء مش حفظ.. الكيمياء فهم" maxLength={220} /></Field>
          <Field label="نص الترحيب" wide><textarea rows={3} className="input" value={form.description} onChange={set('description')} maxLength={1500} /></Field>
          <Field label="نبذة عن المدرس" wide><textarea rows={4} className="input" value={form.aboutText} onChange={set('aboutText')} maxLength={3000} /></Field>
        </div>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <TeacherImage label="صورة المدرس" current={creating ? '' : detail.photoUrl} file={files.photo} onPick={(f) => uploadImage('photo', f)} tall />
          <TeacherImage label="صورة الغلاف" current={creating ? '' : detail.coverUrl} file={files.cover} onPick={(f) => uploadImage('cover', f)} />
        </div>
      </Section>

      <Section icon={BookOpen} title={`الكورسات (${(creating ? courses.length : savedCourses).toLocaleString('ar-EG')})`}
        hint="كل كورس بسنته وسعره ووصفه وصورته. «مخفي» بيشيله من صفحة المدرس والموقع والطلاب المشتركين بيفضلوا شايفينه؛ «حذف» بيشيله من كل حتة.">
        <div className="space-y-3">
          {courses.length === 0 && <p className="rounded-2xl border border-dashed border-ink-200 p-6 text-center text-sm text-ink-400">لسه مفيش كورسات للمدرس ده.</p>}
          {courses.map((c, i) => (
            <CourseCard key={c.key} course={c} creating={creating} academy={detail} teacherSubject={form.subject}
              onChange={(next) => setCourses(list => list.map((x, j) => j === i ? next : x))}
              onRemove={() => setCourses(list => list.filter((_, j) => j !== i))}
              say={say} fail={fail} />
          ))}
        </div>
        <datalist id="teacher-studio-years">{GRADES.map(g => <option key={g} value={g} />)}</datalist>
        <button type="button" className="btn-soft mt-4" onClick={() => setCourses(list => [...list, blankCourse()])}><Plus size={16} /> إضافة كورس</button>
      </Section>

      {!creating && user?.role === 'SUPER_ADMIN' && (
        <Section icon={TriangleAlert} title="حذف المدرس" danger>
          <p className="text-sm leading-7 text-ink-600">المدرس هيختفي من الموقع ومن لوحة التحكم ومن قوايم طلابه، ومش هيقدر يدخل هو ولا مساعدينه، وكل كورساته هتتقفل. المدفوعات والسجلات القديمة بتفضل محفوظة، ورابط صفحته واسم المستخدم بيبقوا متاحين لمدرس جديد.</p>
          <button type="button" className="mt-4 inline-flex items-center gap-2 rounded-2xl bg-rose-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-rose-700" onClick={() => setDeleting(true)}><Trash2 size={16} /> حذف {detail.name}</button>
        </Section>
      )}

      <div className="card sticky bottom-3 z-20 flex flex-wrap items-center justify-between gap-3 p-3">
        <p className="text-xs text-ink-500">{creating ? 'المدرس وكورساته بيتضافوا مع بعض — لو في غلطة مفيش حاجة بتتحفظ لحد ما تصلحها.' : 'الزرار ده بيحفظ بيانات المدرس وصفحته ودخوله. كل كورس ليه زرار حفظ لوحده.'}</p>
        <button type="submit" disabled={busy} className="btn-primary min-w-40 justify-center">
          {busy ? <Spinner className="h-4 w-4" /> : creating ? <><Plus size={17} /> إضافة المدرس</> : <><Save size={17} /> حفظ بيانات المدرس</>}
        </button>
      </div>
    </form>
    {/* Outside the form, so Enter in its confirm box never submits the teacher's details. */}
    {!creating && <DeleteTeacherModal open={deleting} teacher={{ ...detail, courses }} onClose={() => setDeleting(false)}
      onDeleted={(msg) => navigate('/app/control?tab=teachers', { state: { flash: msg } })} />}
    </>
  )
}

function Back({ flash }) {
  return (
    <div className="space-y-4">
      {flash.error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{flash.error}</div>}
      <Link to="/app/control?tab=teachers" className="btn-soft"><ArrowRight size={16} /> رجوع لكل المدرسين</Link>
    </div>
  )
}

function Section({ icon: Icon, title, hint, danger, children }) {
  return (
    <section className={`card p-5 sm:p-6 ${danger ? 'border border-rose-100' : ''}`}>
      <h3 className={`flex items-center gap-2 text-lg font-extrabold ${danger ? 'text-rose-700' : 'text-ink-800'}`}><Icon size={19} className={danger ? 'text-rose-600' : 'text-brand-600'} /> {title}</h3>
      {hint && <p className="mt-1 text-sm text-ink-500">{hint}</p>}
      <div className="mt-5">{children}</div>
    </section>
  )
}

function Field({ label, hint, wide, children }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <label className="label">{label}</label>
      {children}
      {hint && <p className="mt-1 text-xs text-ink-400">{hint}</p>}
    </div>
  )
}

/** The teacher's photo or cover: picked now and sent once the teacher exists, or uploaded straight away when editing. */
function TeacherImage({ label, current, file, onPick, tall }) {
  const input = useRef(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [local, setLocal] = useState('')
  useEffect(() => {
    if (!file) { setLocal(''); return }
    const url = URL.createObjectURL(file); setLocal(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  const preview = local || current
  const pick = async (e) => {
    const f = e.target.files?.[0]; e.target.value = ''
    if (!f) return
    if (!/^image\/(png|jpeg)$/.test(f.type)) return setError('الصورة لازم تكون PNG أو JPG')
    if (f.size > MAX_IMAGE) return setError('اختر صورة أصغر من 3 ميجابايت')
    setError(''); setBusy(true)
    try { await onPick(f) } finally { setBusy(false) }
  }
  return (
    <div>
      <p className="label">{label}</p>
      <div className="flex items-center gap-3">
        <span className={`grid shrink-0 place-items-center overflow-hidden rounded-2xl border border-ink-100 bg-ink-50 text-ink-300 ${tall ? 'h-24 w-20' : 'h-20 w-36'}`}>
          {preview ? <img src={preview} alt="" className="h-full w-full object-cover object-top" /> : <ImagePlus size={22} />}
        </span>
        <button type="button" className="btn-soft" disabled={busy} onClick={() => input.current?.click()}>
          {busy ? <Spinner className="h-4 w-4" /> : <ImagePlus size={16} />} {preview ? 'تغيير' : 'اختيار صورة'}
        </button>
      </div>
      <input ref={input} type="file" accept="image/png,image/jpeg" className="hidden" onChange={pick} />
      {error && <p role="alert" className="mt-1 text-xs font-semibold text-rose-600">{error}</p>}
    </div>
  )
}

// ---- Courses ---------------------------------------------------------------------------------------------------

/**
 * One course. While the teacher is being made it is just part of the form; afterwards each course saves, hides or
 * deletes on its own.
 */
function CourseCard({ course, creating, academy, teacherSubject, onChange, onRemove, say, fail }) {
  const saved = !!course.id
  const [open, setOpen] = useState(!saved)
  const [draft, setDraft] = useState(course)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  useEffect(() => { setDraft(course) }, [course])
  const value = creating ? course : draft
  const setField = (k, v) => { const next = { ...value, [k]: v }; creating ? onChange(next) : setDraft(next) }
  const edit = (k) => (e) => setField(k, e.target.value)
  const dirty = !creating && JSON.stringify(coursePayload(draft)) !== JSON.stringify(coursePayload(course))
  const final = Number(value.discountPercent) > 0 ? Math.round(Number(value.price) * (100 - Number(value.discountPercent))) / 100 : Number(value.price)

  const save = async () => {
    if (!draft.title.trim()) return fail(null, 'اكتب اسم الكورس')
    setBusy(true)
    try {
      const { data } = saved
        ? await api.put(`/admin/courses/${course.id}`, coursePayload(draft))
        : await api.post(`/admin/teachers/${academy.academyId}/courses`, coursePayload(draft))
      onChange(asCourse(data)); setOpen(false); say(saved ? `اتحفظ كورس ${data.title}` : `اتضاف كورس ${data.title}`)
    } catch (e) { fail(e, 'تعذّر حفظ الكورس') }
    finally { setBusy(false) }
  }
  const toggle = async () => {
    const status = course.status === 'ACTIVE' ? 'HIDDEN' : 'ACTIVE'
    try {
      const { data } = await api.put(`/admin/courses/${course.id}`, { status })
      onChange(asCourse(data)); say(status === 'ACTIVE' ? `${course.title} بقى ظاهر` : `${course.title} اتخفى`)
    } catch (e) { fail(e, 'تعذّر تغيير ظهور الكورس') }
  }
  // Lessons and videos are built in the teacher's own workspace; head office steps into it for this course.
  const enter = () => {
    sessionStorage.setItem('manarah_academy', JSON.stringify({ id: academy.academyId, name: academy.name, slug: academy.slug }))
    location.href = `/app/courses/${course.id}`
  }
  // A saved course shows what the site shows now; its visibility changes from the menu, not the edit form.
  const visible = (saved ? course : value).status === 'ACTIVE'

  return (
    <div className="rounded-2xl border border-ink-100 bg-white">
      <div className="flex flex-wrap items-center gap-3 p-3">
        <span className="h-12 w-20 shrink-0 overflow-hidden rounded-xl bg-ink-100">{value.coverUrl && <img src={value.coverUrl} alt="" className="h-full w-full object-cover" />}</span>
        <div className="min-w-0 flex-1">
          <b className="block truncate text-ink-800">{value.title || 'كورس جديد'}</b>
          <small className="text-xs text-ink-400">
            {value.grade || 'كل السنين'} · {Number(value.price) === 0 ? 'مجاني' : fmtMoney(final)}
            {saved && ` · ${course.students.toLocaleString('ar-EG')} طالب`}
          </small>
        </div>
        <div className="flex items-center gap-1.5">
          <span className={`chip ${visible ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-500'}`}>
            {visible ? <><Eye size={13} /> ظاهر في الموقع</> : <><EyeOff size={13} /> مخفي من الموقع</>}
          </span>
          <ActionMenu label={`خيارات ${value.title || 'الكورس'}`} items={[
            { label: open ? 'قفل التعديل' : 'تعديل', icon: Pencil, onClick: () => setOpen(o => !o) },
            { label: visible ? 'إخفاء من الموقع' : 'إظهار في الموقع', icon: visible ? EyeOff : Eye,
              onClick: saved ? toggle : () => setField('status', visible ? 'HIDDEN' : 'ACTIVE') },
            { label: 'الدروس والفيديوهات', icon: PlayCircle, onClick: enter, hidden: !saved },
            { label: saved ? 'حذف' : 'شيل من القايمة', icon: Trash2, danger: true, onClick: saved ? () => setConfirm(true) : onRemove },
          ]} />
        </div>
      </div>

      {open && (
        <div className="grid gap-4 border-t border-ink-100 p-4 sm:grid-cols-2">
          <Field label="اسم الكورس *" wide><input className="input" value={value.title} onChange={edit('title')} placeholder="كيمياء — الباب الأول" maxLength={200} /></Field>
          <Field label="السنة الدراسية" hint="اختار من القايمة أو اكتبها">
            <input className="input" list="teacher-studio-years" value={value.grade} onChange={edit('grade')} placeholder="الصف الثالث الثانوي" maxLength={80} />
          </Field>
          <Field label="المرحلة">
            <select className="input" value={value.gradeLevel || ''} onChange={edit('gradeLevel')}>
              <option value="">—</option>
              {STAGES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </Field>
          <Field label="المادة" hint={`لو سبتها فاضية: ${teacherSubject || 'مادة المدرس'}`}><input className="input" value={value.subject} onChange={edit('subject')} maxLength={100} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="السعر (ج.م)"><input type="number" min="0" className="input" value={value.price} onChange={edit('price')} /></Field>
            <Field label="الخصم %"><input type="number" min="0" max="100" className="input" value={value.discountPercent} onChange={edit('discountPercent')} /></Field>
          </div>
          <Field label="وصف الكورس" wide><textarea rows={3} className="input" value={value.description} onChange={edit('description')} maxLength={5000} /></Field>
          <Field label="صورة الكورس" wide><ImageUpload value={value.coverUrl} label="رفع صورة الكورس" onChange={(url) => setField('coverUrl', url)} /></Field>
          {!creating && (
            <div className="flex flex-wrap justify-end gap-2 sm:col-span-2">
              {saved && <button type="button" className="btn-ghost" onClick={() => { setDraft(course); setOpen(false) }}>إلغاء</button>}
              <button type="button" className="btn-primary" disabled={busy || (saved && !dirty)} onClick={save}>
                {busy ? <Spinner className="h-4 w-4" /> : saved ? <><Save size={16} /> حفظ الكورس</> : <><Plus size={16} /> إضافة الكورس</>}
              </button>
            </div>
          )}
        </div>
      )}
      {saved && <DeleteCourseModal open={confirm} course={course} onClose={() => setConfirm(false)}
        onDeleted={() => { setConfirm(false); onRemove(); say(`اتحذف كورس ${course.title}`) }} fail={fail} />}
    </div>
  )
}

/** Deleting a course: says how many students lose it, and points at hiding for anyone who wants them to keep it. */
export function DeleteCourseModal({ open, course, onClose, onDeleted, fail }) {
  const [busy, setBusy] = useState(false)
  const remove = async () => {
    setBusy(true)
    try { await api.delete(`/admin/courses/${course.id}`); onDeleted() }
    catch (e) { fail(e, 'تعذّر حذف الكورس'); onClose() }
    finally { setBusy(false) }
  }
  return (
    <Modal open={open} onClose={onClose} title={`حذف ${course?.title || 'الكورس'}؟`}>
      <div className="space-y-4 text-sm leading-7 text-ink-600">
        <p>الكورس هيختفي من صفحة المدرس والموقع ولوحة التحكم ولوحة المدرس، والأكواد اللي لسه متستخدمتش هتتلغي.</p>
        {course?.students > 0
          ? <p className="rounded-2xl bg-rose-50 p-3 font-bold text-rose-700">فيه {course.students.toLocaleString('ar-EG')} طالب مشترك فيه هيفقدوا الوصول ليه. لو عايزهم يفضلوا شايفينه، اخفيه بدل ما تحذفه.</p>
          : <p className="rounded-2xl bg-ink-50 p-3 font-semibold text-ink-600">مفيش طلاب مشتركين فيه دلوقتي.</p>}
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-ghost" onClick={onClose}>إلغاء</button>
          <button type="button" disabled={busy} onClick={remove} className="inline-flex items-center gap-2 rounded-2xl bg-rose-600 px-4 py-2.5 font-bold text-white hover:bg-rose-700">
            {busy ? <Spinner className="h-4 w-4" /> : <><Trash2 size={16} /> احذف الكورس</>}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/** Deleting a teacher: the name has to be typed back, since it closes every course and sign-in they have. */
export function DeleteTeacherModal({ open, teacher, onClose, onDeleted }) {
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { if (open) { setTyped(''); setError('') } }, [open])
  const matches = typed.trim() !== '' && typed.trim() === (teacher?.name || '').trim()
  // The teacher page passes its course cards; the control center's row passes a plain count.
  const courseCount = Array.isArray(teacher?.courses) ? teacher.courses.filter(c => c.id !== undefined).length : (teacher?.courses ?? 0)
  const remove = async () => {
    setBusy(true); setError('')
    try {
      await api.delete(`/admin/teachers/${teacher.academyId}`)
      // The admin may be "inside" this teacher's space; that space is gone now.
      const scoped = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
      if (scoped && String(scoped.id) === String(teacher.academyId)) sessionStorage.removeItem('manarah_academy')
      onDeleted(`اتحذف ${teacher.name}`)
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر حذف المدرس')) }
    finally { setBusy(false) }
  }
  return (
    <Modal open={open} onClose={onClose} title={`حذف ${teacher?.name || 'المدرس'}؟`}>
      <div className="space-y-4 text-sm leading-7 text-ink-600">
        <p>هيختفي من الموقع ولوحة التحكم وقوايم طلابه، ومش هيقدر يدخل هو ولا مساعدينه، وكل كورساته هتتقفل{courseCount ? ` (${courseCount.toLocaleString('ar-EG')} كورس)` : ''}.</p>
        {teacher?.students > 0 && <p className="rounded-2xl bg-rose-50 p-3 font-bold text-rose-700">عنده {teacher.students.toLocaleString('ar-EG')} طالب هيفقدوا الوصول لكورساته.</p>}
        <div>
          <label className="label" htmlFor="confirm-teacher-name">للتأكيد اكتب اسم المدرس: <b className="text-ink-800">{teacher?.name}</b></label>
          <input id="confirm-teacher-name" className="input" value={typed} onChange={e => setTyped(e.target.value)} autoComplete="off" />
        </div>
        {error && <p role="alert" className="font-semibold text-rose-600">{error}</p>}
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-ghost" onClick={onClose}>إلغاء</button>
          <button type="button" disabled={!matches || busy} onClick={remove}
            className="inline-flex items-center gap-2 rounded-2xl bg-rose-600 px-4 py-2.5 font-bold text-white hover:bg-rose-700 disabled:opacity-40">
            {busy ? <Spinner className="h-4 w-4" /> : <><Trash2 size={16} /> احذف المدرس</>}
          </button>
        </div>
      </div>
    </Modal>
  )
}
