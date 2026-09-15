import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Search, Plus, Users, ChevronLeft, ChevronRight } from 'lucide-react'
import api from '../lib/api'
import { Avatar, Badge, Modal, PageLoader, EmptyState, ProgressBar, stagger, fadeUp } from '../components/ui'
import { ACADEMIC_STATUS, STUDENT_STATUS } from '../lib/format'

const FILTERS = [
  { key: '', label: 'الكل' },
  { key: 'EXCELLENT', label: 'متفوق' },
  { key: 'GOOD', label: 'جيد' },
  { key: 'AVERAGE', label: 'متوسط' },
  { key: 'NEEDS_ATTENTION', label: 'يحتاج متابعة' },
  { key: 'AT_RISK', label: 'معرّض للخطر' },
]

export default function Students() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [showNew, setShowNew] = useState(false)

  const load = () => {
    setLoading(true)
    api.get('/students', { params: { q, academicStatus: status, page, size: 12 } })
      .then((r) => setData(r.data)).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [status, page])
  useEffect(() => { const t = setTimeout(() => { setPage(0); load() }, 350); return () => clearTimeout(t) }, [q])

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[240px]">
          <Search size={18} className="absolute right-3.5 top-3 text-ink-400" />
          <input value={q} onChange={(e) => setQ(e.target.value)} className="input pr-11" placeholder="ابحث بالاسم أو الكود أو الهاتف..." />
        </div>
        <button onClick={() => setShowNew(true)} className="btn-primary"><Plus size={18} /> طالب جديد</button>
      </div>

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button key={f.key} onClick={() => { setStatus(f.key); setPage(0) }}
            className={`chip border transition ${status === f.key ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200 hover:bg-ink-50'}`}>
            {f.label}
          </button>
        ))}
      </div>

      {loading ? <PageLoader /> : !data || data.content.length === 0 ? (
        <div className="card"><EmptyState icon={Users} title="لا يوجد طلاب" hint="جرّب تعديل البحث أو أضف طالباً جديداً" /></div>
      ) : (
        <>
          <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.content.map((s) => {
              const st = ACADEMIC_STATUS[s.academicStatus] || {}
              const status = STUDENT_STATUS[s.status] || {}
              return (
                <motion.div variants={fadeUp} key={s.id}>
                  <Link to={`/app/students/${s.id}`} className="card block p-5 hover:shadow-glow transition-shadow">
                    <div className="flex items-center gap-3">
                      <Avatar name={s.fullName} size={48} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-bold text-ink-800">{s.fullName}</p>
                        <p className="text-xs text-ink-400">{s.code} · {s.grade || '—'}</p>
                      </div>
                      <span className={`chip border ${st.color}`}><span className={`h-1.5 w-1.5 rounded-full ${st.dot}`} />{st.label}</span>
                    </div>
                    <div className="mt-4 grid grid-cols-3 gap-3 text-center">
                      <Metric label="المعدل" value={s.overallPercent} />
                      <Metric label="الحضور" value={s.attendanceRate} />
                      <Metric label="الواجبات" value={s.homeworkRate} />
                    </div>
                    <div className="mt-3 flex items-center justify-between">
                      <span className={`chip ${status.color}`}>{status.label}</span>
                      <span className="text-xs font-semibold text-brand-600">عرض الملف ←</span>
                    </div>
                  </Link>
                </motion.div>
              )
            })}
          </motion.div>

          <div className="flex items-center justify-between">
            <p className="text-sm text-ink-400">إجمالي {data.totalElements} طالب</p>
            <div className="flex items-center gap-2">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="btn-ghost px-3 py-2 disabled:opacity-40"><ChevronRight size={18} /></button>
              <span className="text-sm font-bold text-ink-600">{page + 1} / {Math.max(1, data.totalPages)}</span>
              <button disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} className="btn-ghost px-3 py-2 disabled:opacity-40"><ChevronLeft size={18} /></button>
            </div>
          </div>
        </>
      )}

      <NewStudentModal open={showNew} onClose={() => setShowNew(false)} onSaved={() => { setShowNew(false); load() }} />
    </div>
  )
}

function Metric({ label, value }) {
  return (
    <div>
      <p className="text-lg font-black text-ink-800">{Math.round(value)}٪</p>
      <ProgressBar value={value} className="mt-1" />
      <p className="mt-1 text-[11px] text-ink-400">{label}</p>
    </div>
  )
}

function NewStudentModal({ open, onClose, onSaved }) {
  const [form, setForm] = useState({ fullName: '', grade: 'الصف الثالث الثانوي', gradeLevel: 'ثانوي', phone: '', school: '' })
  const [saving, setSaving] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async () => {
    setSaving(true)
    try { await api.post('/students', form); onSaved() } finally { setSaving(false) }
  }
  return (
    <Modal open={open} onClose={onClose} title="إضافة طالب جديد">
      <div className="space-y-4">
        <div><label className="label">الاسم الكامل</label><input className="input" value={form.fullName} onChange={set('fullName')} placeholder="مثال: أحمد محمد" /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">المرحلة</label><input className="input" value={form.gradeLevel} onChange={set('gradeLevel')} /></div>
          <div><label className="label">الصف</label><input className="input" value={form.grade} onChange={set('grade')} /></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">الهاتف</label><input className="input" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" /></div>
          <div><label className="label">المدرسة</label><input className="input" value={form.school} onChange={set('school')} /></div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || !form.fullName} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ الطالب'}</button>
        </div>
      </div>
    </Modal>
  )
}
