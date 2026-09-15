import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import {
  CalendarCheck, QrCode, Check, X, Clock, ShieldCheck, RefreshCw, BookOpen, KeyRound,
  CheckCircle2, XCircle, History, Users2,
} from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, stagger, fadeUp, Spinner } from '../components/ui'
import { fmtDateTime } from '../lib/format'
import { qrDataUrl } from '../lib/qr'

const STATUSES = [
  { key: 'PRESENT', label: 'حاضر', icon: Check, cls: 'bg-emerald-500 text-white', idle: 'text-emerald-600 bg-emerald-50' },
  { key: 'LATE', label: 'متأخر', icon: Clock, cls: 'bg-amber-500 text-white', idle: 'text-amber-600 bg-amber-50' },
  { key: 'ABSENT', label: 'غائب', icon: X, cls: 'bg-rose-500 text-white', idle: 'text-rose-600 bg-rose-50' },
  { key: 'EXCUSED', label: 'بعذر', icon: ShieldCheck, cls: 'bg-sky-500 text-white', idle: 'text-sky-600 bg-sky-50' },
]

const STATUS_CFG = {
  PRESENT: { label: 'حاضر', c: 'bg-emerald-50 text-emerald-700' },
  LATE: { label: 'متأخر', c: 'bg-amber-50 text-amber-700' },
  ABSENT: { label: 'غائب', c: 'bg-rose-50 text-rose-700' },
  EXCUSED: { label: 'بعذر', c: 'bg-sky-50 text-sky-700' },
  UNMARKED: { label: 'لم يُسجَّل بعد', c: 'bg-ink-100 text-ink-500' },
}

export default function Attendance() {
  const { user } = useAuth()
  if (user.role === 'STUDENT') return <StudentAttendance />
  return <StaffAttendance />
}

/* ================= Student self-service ================= */

function StudentAttendance() {
  const [tab, setTab] = useState('sessions')
  const [courses, setCourses] = useState(null)
  const [selectedCourse, setSelectedCourse] = useState(null)
  const [history, setHistory] = useState(null)
  const [checkinFor, setCheckinFor] = useState(null)

  const loadCourses = () => api.get('/attendance/my-courses').then((r) => {
    setCourses(r.data)
    if (r.data.length > 0 && !selectedCourse) setSelectedCourse(r.data[0].courseId)
  })
  const loadHistory = () => api.get('/attendance/my').then((r) => setHistory(r.data))

  useEffect(() => { loadCourses(); loadHistory() }, [])

  const current = courses?.find((c) => c.courseId === selectedCourse)

  return (
    <div className="space-y-5">
      <div className="flex gap-2">
        <TabBtn active={tab === 'sessions'} onClick={() => setTab('sessions')} icon={BookOpen}>اختر الكورس وسجّل حضورك</TabBtn>
        <TabBtn active={tab === 'history'} onClick={() => setTab('history')} icon={History}>سجل حضوري</TabBtn>
      </div>

      {tab === 'sessions' ? (
        !courses ? <PageLoader /> : courses.length === 0 ? (
          <div className="card"><EmptyState icon={BookOpen} title="لست مسجَّلاً في أي كورس بعد" hint="التحق بكورس من صفحة الكورسات أولاً" /></div>
        ) : (
          <div className="grid gap-5 lg:grid-cols-3">
            <div className="card p-4 lg:col-span-1">
              <p className="mb-3 px-1 text-xs font-bold text-ink-400">كورساتي</p>
              <div className="space-y-1.5">
                {courses.map((c) => (
                  <button key={c.courseId} onClick={() => setSelectedCourse(c.courseId)}
                    className={`flex w-full items-center justify-between rounded-2xl px-3 py-2.5 text-right transition ${selectedCourse === c.courseId ? 'bg-brand-50 ring-1 ring-brand-200' : 'hover:bg-ink-50'}`}>
                    <span className="text-sm font-bold text-ink-700">{c.courseTitle}</span>
                    <span className="chip bg-ink-100 text-ink-500">{c.sessions.length}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="lg:col-span-2">
              {!current ? <div className="card"><EmptyState icon={CalendarCheck} title="اختر كورساً لعرض حصصه" /></div> : (
                current.sessions.length === 0 ? <div className="card"><EmptyState icon={CalendarCheck} title="لا توجد حصص مسجّلة لهذا الكورس بعد" /></div> : (
                  <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
                    {current.sessions.map((s) => (
                      <motion.div variants={fadeUp} key={s.id} className="card flex items-center gap-4 p-4">
                        <div className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl ${s.status === 'OPEN' ? 'bg-emerald-50 text-emerald-600' : 'bg-ink-100 text-ink-400'}`}>
                          <CalendarCheck size={18} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate font-bold text-ink-800">{s.title}</p>
                          <p className="text-xs text-ink-400">{fmtDateTime(s.scheduledStart)}</p>
                        </div>
                        {s.status === 'OPEN' ? (
                          <button onClick={() => setCheckinFor(s)} className="btn-primary shrink-0"><KeyRound size={16} /> تسجيل الحضور</button>
                        ) : (
                          <span className="chip bg-ink-100 text-ink-500 shrink-0">{s.status === 'CLOSED' ? 'انتهت' : 'لم تبدأ بعد'}</span>
                        )}
                      </motion.div>
                    ))}
                  </motion.div>
                )
              )}
            </div>
          </div>
        )
      ) : (
        !history ? <PageLoader /> : history.length === 0 ? (
          <div className="card"><EmptyState icon={History} title="لا يوجد سجل حضور بعد" /></div>
        ) : (
          <motion.div variants={stagger} initial="hidden" animate="show" className="card divide-y divide-ink-100">
            {history.map((h) => (
              <motion.div variants={fadeUp} key={h.sessionId} className="flex items-center gap-4 px-5 py-3.5">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold text-ink-700">{h.sessionTitle}</p>
                  <p className="text-xs text-ink-400">{h.courseTitle} · {fmtDateTime(h.scheduledStart)}</p>
                </div>
                <span className={`chip ${STATUS_CFG[h.status]?.c || STATUS_CFG.UNMARKED.c}`}>{STATUS_CFG[h.status]?.label || h.status}</span>
              </motion.div>
            ))}
          </motion.div>
        )
      )}

      {checkinFor && <StudentCheckInModal session={checkinFor} onClose={() => { setCheckinFor(null); loadCourses(); loadHistory() }} />}
    </div>
  )
}

function TabBtn({ active, onClick, icon: Icon, children }) {
  return <button onClick={onClick} className={`chip border ${active ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}><Icon size={15} /> {children}</button>
}

/** Two ways in: scan the teacher's real QR with a phone camera (opens /checkin/:token directly),
 *  or — same device as the display, or the teacher reads the code aloud — type it here. */
function StudentCheckInModal({ session, onClose }) {
  const [token, setToken] = useState('')
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState(null)
  const [err, setErr] = useState('')

  const submit = async () => {
    if (!token.trim()) { setErr('اكتب رمز الحضور'); return }
    setSaving(true); setErr('')
    try {
      const r = await api.post('/attendance/check-in', { token: token.trim() })
      setResult(r.data)
    } catch (e) {
      setErr(e.response?.data?.message || 'تعذّر تسجيل الحضور')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={`تسجيل الحضور — ${session.title}`}>
      {result ? (
        <div className="flex flex-col items-center py-4 text-center">
          <CheckCircle2 size={56} className="text-emerald-500" />
          <h3 className="mt-3 text-lg font-black text-ink-800">تم تسجيل حضورك بنجاح</h3>
          <p className="mt-1 text-sm text-ink-500">{result.status === 'LATE' ? `تم تسجيلك متأخراً (${result.lateMinutes} دقيقة)` : 'تم تسجيلك حاضراً'}</p>
          <button onClick={onClose} className="btn-primary mt-5">تم</button>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-ink-500">امسح رمز QR المعروض من المدرس بكاميرا هاتفك، أو اكتب الرمز يدوياً هنا:</p>
          <div>
            <label className="label">رمز الحضور</label>
            <input value={token} onChange={(e) => setToken(e.target.value)} className="input font-mono" placeholder="مثال: 12.a1b2c3d4e5f6..." dir="ltr" />
          </div>
          {err && <p className="rounded-2xl bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600">{err}</p>}
          <div className="flex justify-end gap-2">
            <button onClick={onClose} className="btn-ghost">إلغاء</button>
            <button onClick={submit} disabled={saving} className="btn-primary">
              {saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'تسجيل الحضور'}
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}

/* ================= Staff workspace ================= */

function StaffAttendance() {
  const { user } = useAuth()
  const isTeacher = user.role === 'TEACHER'
  const [sessions, setSessions] = useState(null)
  const [active, setActive] = useState(null)
  const [qr, setQr] = useState(null)
  const [subject, setSubject] = useState('')
  const [teacherName, setTeacherName] = useState('')
  const [date, setDate] = useState('')

  const load = () => api.get('/attendance/sessions').then((r) => setSessions(r.data))
  useEffect(() => { load() }, [])
  if (!sessions) return <PageLoader />

  const subjects = [...new Set(sessions.map((s) => s.subject).filter(Boolean))]
  const teacherNames = [...new Set(sessions.map((s) => s.teacherName).filter(Boolean))]
  const filtered = sessions.filter((s) =>
    (!subject || s.subject === subject) &&
    (!teacherName || s.teacherName === teacherName) &&
    (!date || (s.scheduledStart && s.scheduledStart.slice(0, 10) === date)))

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        {!isTeacher && (
          <>
            <select className="input max-w-[180px]" value={subject} onChange={(e) => setSubject(e.target.value)}>
              <option value="">كل المواد</option>
              {subjects.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
            <select className="input max-w-[200px]" value={teacherName} onChange={(e) => setTeacherName(e.target.value)}>
              <option value="">كل المدرسين</option>
              {teacherNames.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </>
        )}
        <input type="date" className="input max-w-[170px]" value={date} onChange={(e) => setDate(e.target.value)} title="فلترة بالموعد" />
        {(subject || teacherName || date) && (
          <button onClick={() => { setSubject(''); setTeacherName(''); setDate('') }} className="btn-ghost">مسح الفلاتر</button>
        )}
        <p className="text-sm text-ink-400 mr-auto">{filtered.length} جلسة</p>
      </div>

      {filtered.length === 0 ? <div className="card"><EmptyState icon={CalendarCheck} title="لا توجد جلسات" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((s) => (
            <motion.div variants={fadeUp} key={s.id} className="card p-5">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-bold text-ink-800">{s.title}</p>
                  <p className="text-xs text-ink-400 mt-0.5">{s.courseTitle} · {fmtDateTime(s.scheduledStart)}</p>
                  {!isTeacher && s.teacherName && <p className="text-xs text-brand-600 mt-0.5">{s.teacherName}</p>}
                </div>
                <span className="chip bg-ink-100 text-ink-500">{s.total} طالب</span>
              </div>
              <div className="mt-4 grid grid-cols-3 gap-2 text-center text-sm">
                <div className="rounded-2xl bg-emerald-50 py-2"><p className="font-black text-emerald-600">{s.present}</p><p className="text-[11px] text-emerald-700/70">حاضر</p></div>
                <div className="rounded-2xl bg-amber-50 py-2"><p className="font-black text-amber-600">{s.late}</p><p className="text-[11px] text-amber-700/70">متأخر</p></div>
                <div className="rounded-2xl bg-rose-50 py-2"><p className="font-black text-rose-600">{s.absent}</p><p className="text-[11px] text-rose-700/70">غائب</p></div>
              </div>
              <div className="mt-4 flex gap-2">
                <button onClick={() => setActive(s)} className="btn-soft flex-1"><CalendarCheck size={16} /> تسجيل الحضور</button>
                <button onClick={() => setQr(s)} className="btn-ghost"><QrCode size={16} /></button>
              </div>
            </motion.div>
          ))}
        </motion.div>
      )}

      {active && <RosterModal session={active} onClose={() => { setActive(null); load() }} />}
      {qr && <QrModal session={qr} onClose={() => setQr(null)} />}
    </div>
  )
}

function RosterModal({ session, onClose }) {
  const [rows, setRows] = useState(null)
  const [saving, setSaving] = useState(false)
  useEffect(() => { api.get(`/attendance/sessions/${session.id}/roster`).then((r) => setRows(r.data)) }, [session.id])

  const setStatus = (studentId, status) => setRows((rs) => rs.map((r) => r.studentId === studentId ? { ...r, status } : r))
  const markAll = (status) => setRows((rs) => rs.map((r) => ({ ...r, status })))
  const save = async () => {
    setSaving(true)
    try {
      await api.post(`/attendance/sessions/${session.id}/mark`, {
        rows: rows.filter((r) => r.status && r.status !== 'UNMARKED').map((r) => ({ studentId: r.studentId, status: r.status, lateMinutes: r.lateMinutes || 0 })),
      })
      onClose()
    } finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title={`الحضور — ${session.title}`} wide>
      {!rows ? <PageLoader /> : (
        <div className="space-y-3">
          <div className="flex gap-2">
            <button onClick={() => markAll('PRESENT')} className="chip bg-emerald-50 text-emerald-700 border border-emerald-200">تحديد الكل حاضر</button>
            <button onClick={() => markAll('ABSENT')} className="chip bg-rose-50 text-rose-700 border border-rose-200">تحديد الكل غائب</button>
          </div>
          <div className="max-h-[52vh] space-y-2 overflow-y-auto pl-1">
            {rows.map((r) => (
              <div key={r.studentId} className="flex items-center gap-3 rounded-2xl border border-ink-100 p-2.5">
                <div className="min-w-0 flex-1"><p className="truncate text-sm font-bold text-ink-700">{r.studentName}</p><p className="text-[11px] text-ink-400">{r.code}</p></div>
                <div className="flex gap-1.5">
                  {STATUSES.map((st) => (
                    <button key={st.key} onClick={() => setStatus(r.studentId, st.key)} title={st.label}
                      className={`grid h-9 w-9 place-items-center rounded-xl transition ${r.status === st.key ? st.cls : st.idle + ' hover:brightness-95'}`}>
                      <st.icon size={16} />
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={onClose} className="btn-ghost">إلغاء</button>
            <button onClick={save} disabled={saving} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ الحضور'}</button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function QrModal({ session, onClose }) {
  const [qr, setQr] = useState(null)
  const [qrImg, setQrImg] = useState('')
  const [left, setLeft] = useState(0)
  const rotate = () => api.post(`/attendance/sessions/${session.id}/qr`).then(async (r) => {
    setQr(r.data)
    setLeft(r.data.ttlSeconds)
    // Encode a URL a phone's native camera app can open directly — no in-app scanner needed.
    const url = `${window.location.origin}/app/checkin/${r.data.token}`
    setQrImg(await qrDataUrl(url, { width: 240 }))
  })
  useEffect(() => { rotate() }, [])
  useEffect(() => { const id = setInterval(() => setLeft((l) => (l > 0 ? l - 1 : 0)), 1000); return () => clearInterval(id) }, [qr])

  return (
    <Modal open onClose={onClose} title={`رمز الحضور — ${session.title}`}>
      {!qr ? <PageLoader /> : (
        <div className="flex flex-col items-center text-center">
          <div className="relative grid place-items-center rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 p-6 shadow-glow">
            {qrImg && <img src={qrImg} alt="رمز QR للحضور" className="h-60 w-60 rounded-xl bg-white p-2" />}
          </div>
          <p className="mt-4 font-bold text-ink-700">امسح الرمز بكاميرا هاتفك لتسجيل الحضور</p>
          <p className="mt-1 text-sm text-ink-400">رمز ديناميكي يتغيّر كل دقيقتين لمنع الغش</p>
          <div className="mt-4 flex items-center gap-3">
            <span className={`chip ${left > 20 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}><Clock size={13} /> {left} ثانية</span>
            <button onClick={rotate} className="btn-ghost"><RefreshCw size={15} /> تحديث الرمز</button>
          </div>
        </div>
      )}
    </Modal>
  )
}
