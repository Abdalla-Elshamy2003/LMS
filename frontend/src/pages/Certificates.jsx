import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Award, Plus, Search } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, Avatar, stagger, fadeUp } from '../components/ui'
import CertificateCard from '../components/CertificateCard'

const STAFF_ISSUERS = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER']

export default function Certificates() {
  const { user } = useAuth()
  const canIssue = STAFF_ISSUERS.includes(user.role)

  if (user.role === 'STUDENT') return <MyCertificates />
  if (user.role === 'PARENT') return <ChildrenCertificates />
  return <StaffCertificates canIssue={canIssue} />
}

/* ---------------- Student view ---------------- */

function MyCertificates() {
  const [certs, setCerts] = useState(null)
  useEffect(() => {
    api.get('/students/me').then((r) => api.get(`/certificates/student/${r.data.summary.id}`))
      .then((r) => setCerts(r.data)).catch(() => setCerts([]))
  }, [])
  if (!certs) return <PageLoader />
  if (certs.length === 0) return <div className="card"><EmptyState icon={Award} title="لا توجد شهادات بعد" hint="ستظهر شهاداتك هنا فور إصدارها" /></div>
  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-8 lg:grid-cols-2">
      {certs.map((c) => (
        <motion.div variants={fadeUp} key={c.id} className="card p-6"><CertificateCard cert={c} /></motion.div>
      ))}
    </motion.div>
  )
}

/* ---------------- Parent view ---------------- */

function ChildrenCertificates() {
  const [children, setChildren] = useState(null)
  const [certsByChild, setCertsByChild] = useState({})
  useEffect(() => {
    api.get('/students/children').then(async (r) => {
      setChildren(r.data)
      const entries = await Promise.all(r.data.map((c) => api.get(`/certificates/student/${c.id}`).then((res) => [c.id, res.data])))
      setCertsByChild(Object.fromEntries(entries))
    })
  }, [])
  if (!children) return <PageLoader />
  const all = children.flatMap((c) => (certsByChild[c.id] || []))
  if (all.length === 0) return <div className="card"><EmptyState icon={Award} title="لا توجد شهادات لأبنائك بعد" /></div>
  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-8 lg:grid-cols-2">
      {all.map((c) => (
        <motion.div variants={fadeUp} key={c.id} className="card p-6"><CertificateCard cert={c} /></motion.div>
      ))}
    </motion.div>
  )
}

/* ---------------- Staff view ---------------- */

function StaffCertificates({ canIssue }) {
  const [students, setStudents] = useState([])
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState(null)
  const [certs, setCerts] = useState(null)
  const [showIssue, setShowIssue] = useState(false)

  useEffect(() => { api.get('/students/all').then((r) => setStudents(r.data)) }, [])

  const loadCerts = (studentId) => api.get(`/certificates/student/${studentId}`).then((r) => setCerts(r.data))
  useEffect(() => { if (selected) loadCerts(selected.id) }, [selected])

  const filtered = students.filter((s) => !q || s.fullName.includes(q) || s.code.includes(q))

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="card p-5 lg:col-span-1">
        <div className="relative mb-3">
          <Search size={16} className="absolute right-3 top-2.5 text-ink-400" />
          <input value={q} onChange={(e) => setQ(e.target.value)} className="input pr-9 py-2" placeholder="ابحث عن طالب..." />
        </div>
        <div className="max-h-[60vh] space-y-1.5 overflow-y-auto">
          {filtered.map((s) => (
            <button key={s.id} onClick={() => setSelected(s)}
              className={`flex w-full items-center gap-2.5 rounded-2xl px-3 py-2 text-right transition ${selected?.id === s.id ? 'bg-brand-50 ring-1 ring-brand-200' : 'hover:bg-ink-50'}`}>
              <Avatar name={s.fullName} size={32} />
              <div className="min-w-0 flex-1"><p className="truncate text-sm font-bold text-ink-700">{s.fullName}</p><p className="text-xs text-ink-400">{s.code}</p></div>
            </button>
          ))}
          {filtered.length === 0 && <p className="py-6 text-center text-sm text-ink-400">لا يوجد طلاب مطابقون</p>}
        </div>
      </div>

      <div className="lg:col-span-2">
        {!selected ? (
          <div className="card"><EmptyState icon={Award} title="اختر طالباً لعرض شهاداته" /></div>
        ) : (
          <div className="space-y-5">
            <div className="card flex items-center justify-between p-5">
              <div className="flex items-center gap-3">
                <Avatar name={selected.fullName} size={44} />
                <div><p className="font-extrabold text-ink-800">{selected.fullName}</p><p className="text-xs text-ink-400">{selected.code}</p></div>
              </div>
              {canIssue && <button onClick={() => setShowIssue(true)} className="btn-primary"><Plus size={16} /> إصدار شهادة</button>}
            </div>
            {!certs ? <PageLoader /> : certs.length === 0 ? (
              <div className="card"><EmptyState icon={Award} title="لا توجد شهادات لهذا الطالب" /></div>
            ) : (
              <div className="grid gap-8">
                {certs.map((c) => <div key={c.id} className="card p-6"><CertificateCard cert={c} /></div>)}
              </div>
            )}
          </div>
        )}
      </div>

      {showIssue && selected && (
        <IssueModal student={selected} onClose={() => setShowIssue(false)} onIssued={() => { setShowIssue(false); loadCerts(selected.id) }} />
      )}
    </div>
  )
}

function IssueModal({ student, onClose, onIssued }) {
  const [courses, setCourses] = useState([])
  const [courseId, setCourseId] = useState('')
  const [grade, setGrade] = useState('ممتاز')
  const [saving, setSaving] = useState(false)
  useEffect(() => { api.get('/courses').then((r) => setCourses(r.data)) }, [])

  const issue = async () => {
    setSaving(true)
    try {
      await api.post('/certificates/issue', { studentId: student.id, courseId: courseId || null, grade })
      onIssued()
    } finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title={`إصدار شهادة — ${student.fullName}`}>
      <div className="space-y-4">
        <div><label className="label">الكورس (اختياري)</label>
          <select className="input" value={courseId} onChange={(e) => setCourseId(e.target.value)}>
            <option value="">— شهادة عامة بدون كورس محدد —</option>
            {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
          </select>
        </div>
        <div><label className="label">التقدير</label>
          <select className="input" value={grade} onChange={(e) => setGrade(e.target.value)}>
            {['ممتاز', 'جيد جداً', 'جيد', 'مقبول'].map((g) => <option key={g} value={g}>{g}</option>)}
          </select>
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={issue} disabled={saving} className="btn-primary">{saving ? 'جارٍ الإصدار...' : 'إصدار الشهادة'}</button>
        </div>
      </div>
    </Modal>
  )
}
