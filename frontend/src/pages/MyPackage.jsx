import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { BookOpen, GraduationCap, Layers, PlayCircle, Sparkles } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { apiErrorMessage } from '../lib/apiError'
import { EmptyState, PageLoader } from '../components/ui'
import { tintStyle } from '../components/bundles/bundleArt'
import '../components/bundles/bundles.css'

/**
 * "باقتي": everything a package opened, in one place — every teacher's courses side by side. A course belongs to its
 * teacher's space, so opening one moves the student into that teacher (same account) and straight into the course.
 */
export default function MyPackage() {
  const { switchTeacher } = useAuth()
  const navigate = useNavigate()
  const [packages, setPackages] = useState(null)
  const [teacher, setTeacher] = useState('all')
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => { api.get('/me/packages').then(r => setPackages(r.data)).catch(e => { setPackages([]); setError(apiErrorMessage(e, 'تعذّر تحميل باقتك')) }) }, [])

  const pkg = packages?.[0]
  const teachers = pkg?.teachers || []
  const cards = useMemo(() => teachers.flatMap((t, i) => t.courses.map(c => ({ ...c, t, i }))), [teachers])
  const shown = teacher === 'all' ? cards : cards.filter(c => c.t.slug === teacher)

  const open = async (c) => {
    if (busy) return
    if (c.t.current) return navigate(`/app/courses/${c.id}`)
    setBusy(c.id); setError('')
    try { await switchTeacher(c.t.seatUserId, `/app/courses/${c.id}`) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر فتح الكورس')); setBusy(null) }
  }

  if (!packages) return <PageLoader />
  if (!pkg) return (
    <div className="card">
      <EmptyState icon={Layers} title="مفيش باقة مفعّلة على حسابك" hint="اشترك في باقة مدرسين وكل فيديوهاتهم هتتجمع هنا في مكان واحد." />
      <div className="pb-8 text-center"><Link to="/#packages" className="btn-primary">شوف باقات المدرسين</Link></div>
    </div>
  )

  return (
    <div className="space-y-6">
      <motion.div initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} className="bx-card p-7 sm:p-9">
        <span className="bx-grid" aria-hidden="true" />
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div>
            <span className="chip border border-white/15 bg-white/10 text-sky-100"><Sparkles size={14} /> باقتي</span>
            <h2 className="mt-3 text-2xl font-black sm:text-3xl">{pkg.name}</h2>
            <p className="mt-1 text-sm text-sky-100/80">{teachers.length.toLocaleString('ar-EG')} مدرسين · {cards.length.toLocaleString('ar-EG')} كورس مفتوح لك</p>
          </div>
          <div className="flex -space-x-3 space-x-reverse">
            {teachers.map((t, i) => (
              <span key={t.slug} style={tintStyle(i)} className="bx-accent-bg h-14 w-14 overflow-hidden rounded-full p-0.5 shadow-soft">
                {t.photoUrl ? <img src={t.photoUrl} alt={t.subject} className="h-full w-full rounded-full object-cover object-top" /> : <span className="grid h-full w-full place-items-center rounded-full bg-brand-700"><GraduationCap size={20} /></span>}
              </span>
            ))}
          </div>
        </div>
      </motion.div>

      <div className="flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none]">
        <button type="button" onClick={() => setTeacher('all')}
          className={`chip shrink-0 border px-4 py-2 text-sm ${teacher === 'all' ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>كل المدرسين</button>
        {teachers.map((t, i) => (
          <button key={t.slug} type="button" onClick={() => setTeacher(t.slug)} style={tintStyle(i)}
            className={`flex shrink-0 items-center gap-2 rounded-full border py-1 pl-4 pr-1 text-sm font-bold transition ${teacher === t.slug ? 'bx-accent-bg border-transparent text-white' : 'border-ink-200 bg-white text-ink-600'}`}>
            <span className="h-7 w-7 overflow-hidden rounded-full bg-ink-100">{t.photoUrl && <img src={t.photoUrl} alt="" className="h-full w-full object-cover object-top" />}</span>
            {t.subject}
          </button>
        ))}
      </div>

      {error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{error}</div>}

      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {shown.map((c, k) => (
          <motion.button key={c.id} type="button" onClick={() => open(c)} style={tintStyle(c.i)}
            initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(k, 9) * 0.03 }}
            className={`bx-video text-right ${busy === c.id ? 'opacity-60' : ''}`}>
            <span className="bx-video-cover block">
              {c.coverUrl ? <img src={c.coverUrl} alt="" loading="lazy" /> : <span className="bx-video-glyph"><BookOpen size={40} /></span>}
              {c.year && <span className="absolute right-3 top-3 z-[1] rounded-full bg-white/95 px-3 py-1 text-[11px] font-black text-ink-700">{c.year}</span>}
              <span className="bx-play" aria-hidden="true"><PlayCircle size={22} /></span>
            </span>
            <span className="block p-5">
              <span className="flex items-center gap-2 text-xs font-bold text-ink-500">
                <span className="h-6 w-6 overflow-hidden rounded-full bg-ink-100">{c.t.photoUrl && <img src={c.t.photoUrl} alt="" className="h-full w-full object-cover object-top" />}</span>
                {c.t.name} · <span className="bx-accent-text">{c.t.subject}</span>
              </span>
              <span className="mt-2 line-clamp-1 block text-[15px] font-black text-ink-800">{c.title}</span>
              {c.description && <span className="mt-1 line-clamp-2 block text-xs leading-6 text-ink-500">{c.description}</span>}
              <span className="bx-accent-text mt-3 inline-block text-xs font-black">ابدأ الكورس ←</span>
            </span>
          </motion.button>
        ))}
      </div>
      <p className="text-center text-xs text-ink-400">كل مدرس ليه حضوره وامتحاناته وواجباته في مساحته — تقدر تتنقل بينهم من «مدرسيني» فوق.</p>
    </div>
  )
}
