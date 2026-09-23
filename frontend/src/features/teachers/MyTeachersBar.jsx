import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { GraduationCap, Plus } from 'lucide-react'
import api from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { apiErrorMessage } from '../../lib/apiError'

/**
 * "مدرسيني": a student's teachers, one tap apart. Each teacher is a separate space (their own courses, exams,
 * attendance), and the student moves between them with the same account — no second password.
 */
export default function MyTeachersBar() {
  const { switchTeacher } = useAuth()
  const [teachers, setTeachers] = useState([])
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => { api.get('/me/teachers').then(r => setTeachers(r.data)).catch(() => setTeachers([])) }, [])
  if (teachers.length === 0) return null

  const go = async (t) => {
    if (t.current || busy) return
    setBusy(t.userId); setError('')
    try { await switchTeacher(t.userId, '/app') }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر فتح مساحة المدرس')); setBusy(null) }
  }

  return (
    <div className="border-t border-ink-100/70 px-4 pb-2.5 pt-2 sm:px-6">
      <div className="flex items-center gap-2 overflow-x-auto [scrollbar-width:none]">
        <span className="shrink-0 text-xs font-black text-ink-500">مدرسيني</span>
        {teachers.map((t) => (
          <button key={t.userId} type="button" onClick={() => go(t)} aria-current={t.current}
            title={t.current ? 'إنت هنا دلوقتي' : `افتح مساحة ${t.name}`}
            className={`flex shrink-0 items-center gap-2 rounded-full py-1 pl-3.5 pr-1 text-sm font-bold transition ${t.current
              ? 'bg-gradient-to-l from-brand-500 to-brand-700 text-white shadow-glow'
              : 'border border-ink-100 bg-white text-ink-600 hover:border-brand-200 hover:text-brand-700'} ${busy === t.userId ? 'opacity-60' : ''}`}>
            <span className="grid h-7 w-7 place-items-center overflow-hidden rounded-full bg-brand-100 text-brand-700">
              {t.photoUrl ? <img src={t.photoUrl} alt="" className="h-full w-full object-cover object-top" /> : <GraduationCap size={14} />}
            </span>
            <span className="max-w-[9rem] truncate">{t.subject || t.name}</span>
          </button>
        ))}
        <Link to="/#teachers" className="flex shrink-0 items-center gap-1 rounded-full border border-dashed border-brand-200 px-3 py-1.5 text-xs font-bold text-brand-600 hover:bg-brand-50">
          <Plus size={14} /> مدرس جديد
        </Link>
      </div>
      {error && <p role="alert" className="mt-1 text-xs font-semibold text-rose-600">{error}</p>}
    </div>
  )
}
