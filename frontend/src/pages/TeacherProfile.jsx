import { useEffect, useState } from 'react'
import { useParams, Link, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Star, Users2, BookOpen, Clock, ArrowLeft, GraduationCap, CheckCircle2 } from 'lucide-react'
import api from '../lib/api'
import { initials } from '../lib/format'
import { PageLoader } from '../components/ui'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'

const GRADIENTS = ['from-brand-500 to-brand-700', 'from-emerald-500 to-teal-700', 'from-violet-500 to-purple-700', 'from-amber-500 to-orange-700']

export default function TeacherProfile() {
  const { id } = useParams()
  const [teacher, setTeacher] = useState(null)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    api.get(`/public/teachers/${id}`).then((r) => setTeacher(r.data)).catch(() => setNotFound(true))
  }, [id])

  if (notFound) return <Navigate to="/features" replace />
  if (!teacher) return <div className="grid min-h-screen place-items-center bg-[#f6fbff]"><PageLoader /></div>

  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />

      <section className="relative overflow-hidden pb-10 pt-32 sm:pt-40">
        <div className="absolute inset-0 -z-20 mesh-bg" />
        <div className="absolute -top-32 -right-20 -z-10 h-[380px] w-[380px] rounded-full bg-brand-300/30 blur-3xl animate-blob" />
        <div className="absolute top-16 -left-24 -z-10 h-[320px] w-[320px] rounded-full bg-sky-400/25 blur-3xl animate-blob" style={{ animationDelay: '4s' }} />

        <div className="mx-auto max-w-5xl px-5">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="card overflow-hidden">
            <div className="relative h-28 bg-gradient-to-br from-brand-600 to-brand-800 sm:h-36">
              <div className="absolute inset-0 bg-grid opacity-25" />
            </div>
            <div className="px-6 pb-8 sm:px-10">
              <div className="relative -mt-14 flex flex-wrap items-end gap-5 sm:-mt-16">
                {teacher.photoUrl
                  ? <img src={teacher.photoUrl} alt={teacher.name} className="h-28 w-28 rounded-[28px] object-cover ring-4 ring-white shadow-card sm:h-32 sm:w-32" />
                  : <div className="grid h-28 w-28 place-items-center rounded-[28px] bg-brand-600 text-3xl font-bold text-white ring-4 ring-white shadow-card sm:h-32 sm:w-32">{initials(teacher.name)}</div>}
                <div className="min-w-0 flex-1 pb-2">
                  <h1 className="text-2xl font-black text-ink-800 sm:text-3xl">{teacher.name}</h1>
                  <p className="mt-1 font-semibold text-brand-600">{teacher.title}</p>
                  <div className="mt-1.5 flex items-center gap-1 text-amber-400">
                    {[...Array(5)].map((_, i) => <Star key={i} size={14} fill="currentColor" />)}
                    <span className="mr-1 text-xs font-semibold text-ink-400">(تقييم الطلاب)</span>
                  </div>
                </div>
              </div>

              {teacher.bio && <p className="mt-5 max-w-2xl leading-relaxed text-ink-600">{teacher.bio}</p>}

              <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <Stat icon={BookOpen} value={teacher.courseCount} label="كورس" />
                <Stat icon={Users2} value={teacher.studentCount} label="طالب" />
                {teacher.subjects && <div className="col-span-2 flex items-center gap-2.5 rounded-2xl border border-ink-100 p-3.5 sm:col-span-1"><GraduationCap size={16} className="shrink-0 text-brand-600" /><span className="truncate text-sm font-bold text-ink-700">{teacher.subjects}</span></div>}
                {teacher.schedule && <div className="col-span-2 flex items-center gap-2.5 rounded-2xl border border-ink-100 p-3.5 sm:col-span-1"><Clock size={16} className="shrink-0 text-brand-600" /><span className="truncate text-sm font-bold text-ink-700">{teacher.schedule}</span></div>}
              </div>
            </div>
          </motion.div>
        </div>
      </section>

      <section className="mx-auto max-w-5xl px-5 pb-24">
        <h2 className="mb-6 text-xl font-black text-ink-800">كورسات {teacher.name}</h2>
        {teacher.courses.length === 0 ? (
          <div className="card p-10 text-center text-ink-400">لا توجد كورسات متاحة حالياً</div>
        ) : (
          <div className="grid gap-5 sm:grid-cols-2">
            {teacher.courses.map((c, i) => (
              <motion.div key={c.id} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
                transition={{ delay: i * 0.06 }} className="card overflow-hidden">
                <div className={`relative h-24 bg-gradient-to-br ${GRADIENTS[i % GRADIENTS.length]} p-5`}>
                  <div className="absolute inset-0 bg-grid opacity-25" />
                  <BookOpen className="text-white/90" size={24} />
                  {c.subject && <span className="absolute bottom-3 left-5 chip bg-white/20 text-white backdrop-blur">{c.subject}</span>}
                  {c.discountPercent > 0 && <span className="absolute bottom-3 right-5 chip bg-rose-500 text-white">خصم {c.discountPercent}%</span>}
                </div>
                <div className="p-5">
                  <p className="font-extrabold text-ink-800">{c.title}</p>
                  <p className="mt-1 text-xs text-ink-400">{c.grade || c.gradeLevel || '—'}</p>
                  {c.description && <p className="mt-2 line-clamp-2 text-sm text-ink-500">{c.description}</p>}
                  <div className="mt-3 flex flex-wrap gap-2 text-xs">
                    <span className="chip bg-ink-100 text-ink-600"><Users2 size={12} /> {c.students} طالب</span>
                    {c.schedule && <span className="chip bg-emerald-50 text-emerald-700"><Clock size={12} /> {c.schedule}</span>}
                  </div>
                  <div className="mt-4 flex items-center justify-between border-t border-ink-100 pt-4">
                    {c.discountPercent > 0 ? (
                      <span className="flex items-baseline gap-2">
                        <span className="text-sm text-ink-400 line-through">{Number(c.price).toLocaleString('ar-EG')} ج.م</span>
                        <span className="text-lg font-black text-rose-600">{Number(c.finalPrice).toLocaleString('ar-EG')} ج.م</span>
                      </span>
                    ) : (
                      <span className="text-lg font-black text-brand-600">{Number(c.price).toLocaleString('ar-EG')} ج.م</span>
                    )}
                    <Link to={`/checkout/${c.id}`} className="btn-primary">اشترك الآن <ArrowLeft size={16} /></Link>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        )}
        <div className="mt-8 flex items-center justify-center gap-2 text-sm text-ink-400">
          <CheckCircle2 size={15} className="text-emerald-500" /> الدفع آمن، وسيتواصل معك فريقنا لإتمام التسجيل
        </div>
      </section>

      <MarketingFooter />
    </div>
  )
}

function Stat({ icon: Icon, value, label }) {
  return (
    <div className="flex items-center gap-2.5 rounded-2xl border border-ink-100 p-3.5">
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600"><Icon size={16} /></span>
      <div><p className="text-base font-black text-ink-800">{value}</p><p className="text-[11px] text-ink-400">{label}</p></div>
    </div>
  )
}
