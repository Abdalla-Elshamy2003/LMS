import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence, useInView, useReducedMotion } from 'framer-motion'
import {
  Sparkles, ShieldCheck, QrCode, Video, Brain, Users2, MessageCircle, Trophy, BarChart3, ClipboardList,
  Wallet, Award, ArrowLeft, ArrowUpLeft, Play, CheckCircle2, ChevronDown, Search, BookOpen, GraduationCap,
  Smartphone, Lock, Bell, Layers, Zap, Rocket, Eye, FileQuestion, Timer, HeartHandshake, Star,
} from 'lucide-react'
import api from '../lib/api'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import TiltCard from '../components/TiltCard'
import BooksScene from '../components/BooksScene'
import { BundleCard, EmptyBundleCard } from '../components/bundles/BundleCard'

const reveal = { initial: { opacity: 0, y: 26 }, whileInView: { opacity: 1, y: 0 }, viewport: { once: true, margin: '-60px' }, transition: { duration: 0.55, ease: 'easeOut' } }

/** Counts from 0 to `value` the first time the number scrolls into view. */
function Counter({ value, suffix = '' }) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, margin: '-40px' })
  const reduced = useReducedMotion()
  const [n, setN] = useState(0)
  useEffect(() => {
    if (!inView) return
    if (reduced || !value) { setN(value); return }
    const start = performance.now(), dur = 1400
    let raf
    const tick = (t) => { const p = Math.min(1, (t - start) / dur); setN(Math.round(value * (1 - Math.pow(1 - p, 3)))); if (p < 1) raf = requestAnimationFrame(tick) }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [inView, value, reduced])
  return <span ref={ref}>{n.toLocaleString('ar-EG')}{suffix}</span>
}

const HEADLINES = [
  'منصة كاملة باسمه',
  'طلابه.. وبس',
  'فيديو محمي بيسأل',
  'امتحان بتقرير نزاهة',
  'ذكاء اصطناعي بيساعده',
  'ولي أمر متابع',
]

/** Types each phrase character by character, holds, deletes, then moves to the next — forever. */
function Typewriter({ phrases, typeMs = 70, deleteMs = 35, holdMs = 1700 }) {
  const reduced = useReducedMotion()
  const [i, setI] = useState(0)
  const [text, setText] = useState(reduced ? phrases[0] : '')
  const [deleting, setDeleting] = useState(false)
  useEffect(() => {
    if (reduced) return
    const full = phrases[i % phrases.length]
    let t
    if (!deleting && text === full) t = setTimeout(() => setDeleting(true), holdMs)
    else if (deleting && text === '') { setDeleting(false); setI(v => v + 1) }
    else t = setTimeout(() => setText(deleting ? full.slice(0, text.length - 1) : full.slice(0, text.length + 1)), deleting ? deleteMs : typeMs)
    return () => clearTimeout(t)
  }, [text, deleting, i, phrases, reduced, typeMs, deleteMs, holdMs])
  return <span className="inline-flex items-baseline">
    <span className="gradient-text bg-[length:200%_auto] animate-gradient-x">{text || ' '}</span>
    {!reduced && <span aria-hidden="true" className="typewriter-caret ml-1 inline-block h-[0.9em] w-[3px] rounded bg-brand-500 align-baseline" />}
  </span>
}

const TINTS = [
  ['from-brand-500 via-sky-500 to-cyan-400', '#0ea5e9'],
  ['from-violet-500 via-fuchsia-500 to-pink-400', '#a855f7'],
  ['from-emerald-500 via-teal-500 to-cyan-400', '#10b981'],
  ['from-amber-500 via-orange-500 to-rose-400', '#f59e0b'],
]

/**
 * Teacher card: the portrait floats above the card's top edge inside a glowing ring, drifts
 * gently, and lifts toward the pointer on hover; the card itself tilts in 3D and a light sweep
 * crosses it. Stagger-revealed on scroll.
 */
function TeacherCard({ t, i }) {
  const [tint, glow] = TINTS[i % TINTS.length]
  const reduced = useReducedMotion()
  const initials = (t.name || '').replace(/^أ\.|^م\.|^مس\.?|^مستر/u, '').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('')
  return (
    <motion.div initial={{ opacity: 0, y: 40, scale: 0.96 }} whileInView={{ opacity: 1, y: 0, scale: 1 }} viewport={{ once: true, margin: '-40px' }}
      transition={{ type: 'spring', stiffness: 120, damping: 16, delay: (i % 3) * 0.12 }} className="pt-16">
      <TiltCard max={7} glare={false} className="group h-full">
        <Link to={`/t/${t.slug}`} className="tcard relative flex h-full flex-col rounded-[1.75rem] border border-white/80 bg-white p-6 pt-20 text-center shadow-card transition-shadow duration-500 group-hover:shadow-[0_30px_60px_-24px_rgba(2,132,199,0.45)]"
          style={{ '--glow': glow }}>
          {/* gradient cap behind the floating portrait */}
          <div className={`absolute inset-x-0 top-0 h-28 rounded-t-[1.75rem] bg-gradient-to-br ${tint} opacity-90`} />
          <div className="absolute inset-x-0 top-0 h-28 rounded-t-[1.75rem] bg-dots opacity-30" />
          <span className="tcard-shine" aria-hidden="true" />

          {/* floating portrait, breaking out of the card's top edge */}
          <motion.div
            animate={reduced ? undefined : { y: [0, -8, 0] }}
            transition={{ duration: 4.5 + (i % 3), repeat: Infinity, ease: 'easeInOut', delay: i * 0.4 }}
            whileHover={reduced ? undefined : { y: -14, scale: 1.07, rotate: -2 }}
            className="absolute -top-16 left-1/2 z-10 h-36 w-36 -translate-x-1/2"
            style={{ transformStyle: 'preserve-3d', transform: 'translateZ(40px)' }}>
            <span className="tcard-ring absolute -inset-2 rounded-full" aria-hidden="true" />
            <span className="absolute inset-0 rounded-full bg-white p-1.5 shadow-[0_18px_40px_-14px_rgba(15,23,42,0.45)]">
              <span className={`flex h-full w-full items-center justify-center overflow-hidden rounded-full bg-gradient-to-br ${tint} text-3xl font-black text-white`}>
                {t.photoUrl
                  ? <img src={t.photoUrl} alt={t.name} loading="lazy" className="h-full w-full object-cover transition duration-700 group-hover:scale-110" onError={e => { e.currentTarget.replaceWith(document.createTextNode(initials)) }} />
                  : initials}
              </span>
            </span>
            <span className="absolute -bottom-1 -left-1 grid h-9 w-9 place-items-center rounded-full bg-white text-brand-600 shadow-soft transition duration-500 group-hover:rotate-12 group-hover:scale-110"><GraduationCap size={17} /></span>
          </motion.div>

          <div className="relative mt-2">
            <span className="chip mx-auto bg-white/90 text-brand-800 shadow-soft ring-1 ring-brand-100">{t.subject || 'مدرس'}</span>
            <h3 className="mt-4 text-xl font-black transition-colors duration-300 group-hover:text-brand-700">{t.name}</h3>
            <p className="mt-1 text-xs text-ink-400">{t.tagline}</p>
            {t.headline && <p className="mt-3 line-clamp-2 text-sm leading-7 text-ink-600">{t.headline}</p>}
            <div className="mt-4 flex flex-wrap justify-center gap-1.5">
              <span className="chip bg-ink-100 text-ink-600"><BookOpen size={13} /> {t.courseCount} كورس</span>
              {(t.grades || []).slice(0, 2).map(g => <span key={g} className="chip bg-sky-50 text-sky-700">{g}</span>)}
            </div>
            <span className="mt-6 inline-flex items-center gap-2 rounded-2xl bg-brand-50 px-4 py-2 text-sm font-bold text-brand-700 transition-all duration-300 group-hover:bg-brand-600 group-hover:text-white group-hover:shadow-glow">
              افتح صفحة المستر <ArrowUpLeft size={16} className="transition-transform duration-300 group-hover:-translate-x-1 group-hover:-translate-y-0.5" />
            </span>
          </div>
        </Link>
      </TiltCard>
    </motion.div>
  )
}

const PILLS = ['مساحة مستقلة لكل مستر', 'فيديو محمي ضد التحميل', 'أسئلة أثناء الفيديو', 'امتحانات بتقرير نزاهة', 'حضور وانصراف بالـ QR', 'ملخصات بالذكاء الاصطناعي', 'متابعة ولي الأمر', 'لوحة شرف ونقاط', 'واجبات وتصحيح', 'مدفوعات وأقساط']

const FEATURES = [
  { icon: Layers, tint: 'from-brand-500 to-brand-700', title: 'كل مستر.. عالم لوحده', desc: 'صفحة باسمه، طلابه، كورساته، امتحاناته، وبنك أسئلته — معزولة 100% عن أي مستر تاني. الطالب اللي بيشترك معاه بيشوف حاجته هو بس.' },
  { icon: Video, tint: 'from-violet-500 to-purple-700', title: 'فيديو محمي بجد', desc: 'مشاهدة داخل المنصة بس، جهاز واحد في نفس الوقت، علامة مائية بحساب الطالب، ورفض أي محاولة تحميل من برا المتصفح.' },
  { icon: FileQuestion, tint: 'from-sky-500 to-cyan-700', title: 'الفيديو بيسأل', desc: 'المستر بيحط سؤال عند دقيقة معينة، الفيديو يقف ويسأل الطالب وما يكمّلش غير لما يجاوب — واختبار قصير في آخر الدرس.' },
  { icon: ShieldCheck, tint: 'from-emerald-500 to-teal-700', title: 'امتحانات بتقرير نزاهة', desc: 'ترتيب عشوائي، ملء شاشة، رصد الخروج من الصفحة، حفظ تلقائي، تصحيح فوري، وأسئلة بالصور — وتحليل لكل سؤال بعد الامتحان.' },
  { icon: QrCode, tint: 'from-amber-500 to-orange-700', title: 'حضور وانصراف بالـ QR', desc: 'المستر بيعمل سكان لكارت الطالب من تليفونه، والنظام يحدد دخول ولا خروج لوحده — وسجل كامل لولي الأمر.' },
  { icon: HeartHandshake, tint: 'from-rose-500 to-pink-700', title: 'ولي الأمر شايف كل حاجة', desc: 'حساب خاص لولي الأمر يتابع منه الحضور والدرجات والواجبات لكل أبنائه، وتنبيهات تلقائية عند الغياب أو التراجع.' },
  { icon: Trophy, tint: 'from-yellow-500 to-amber-700', title: 'تحفيز بالنقاط ولوحة الشرف', desc: 'نقاط ومستويات وشارات على الحضور والتفوق، ولوحة شرف تخلّي الطالب عايز يرجع تاني.' },
  { icon: Wallet, tint: 'from-slate-600 to-slate-800', title: 'اشتراكات ومدفوعات', desc: 'كورس مجاني يتفتح فوراً، ومدفوع يتفتح بعد الدفع — فواتير وأقساط وتذكيرات من غير ما حد يلحق حد.' },
]

const AI = [
  { icon: Brain, title: 'ملخص الدرس في ثواني', desc: 'المستر يضغط زرار، والنظام يكتب ملخص واضح وأهم النقاط من شرح الدرس — ويظهر للطالب تحت الفيديو للمراجعة.' },
  { icon: Sparkles, title: 'توليد أسئلة جاهزة', desc: 'مادة + موضوع + مستوى صعوبة = أسئلة اختيار من متعدد أو صح/خطأ جاهزة، تراجعها وتعدّلها وتحفظها في البنك بضغطة.' },
  { icon: BarChart3, title: 'تحليل أداء الطالب', desc: 'نقاط قوة، نقاط ضعف، وتوصيات عملية لكل طالب — مبنية على حضوره ودرجاته وواجباته الفعلية، مش تخمين.' },
]

const STUDENT_STEPS = [
  { n: '١', title: 'اختار مسترك', desc: 'من الكروت اللي تحت، افتح صفحة المستر وشوف كورساته وطريقته.' },
  { n: '٢', title: 'اشترك في كورس', desc: 'دقيقة واحدة وحسابك جاهز — الكورس المجاني يتفتح فوراً.' },
  { n: '٣', title: 'اتعلّم واتقيّم', desc: 'فيديو محمي، أسئلة أثناء الشرح، واجبات، امتحانات، ونقاط على كل خطوة.' },
]
const TEACHER_STEPS = [
  { n: '١', title: 'مساحتك في دقايق', desc: 'صفحة باسمك ولينك خاص بيك، بتعدّل نصوصها وصورها بنفسك.' },
  { n: '٢', title: 'ارفع وجهّز', desc: 'فيديوهات محمية، أسئلة على الفيديو، بنك أسئلة، وامتحانات بتقرير نزاهة.' },
  { n: '٣', title: 'تابع طلابك', desc: 'حضور بالـ QR، درجات، تقارير، وولي الأمر بيتابع لوحده.' },
]

const FAQ = [
  { q: 'هل بيانات كل مستر منفصلة فعلاً؟', a: 'أيوة. كل مستر عنده مساحة بياناتها مستقلة تماماً — طلابه وكورساته وامتحاناته وبنك أسئلته. لا مستر يشوف حاجة مستر تاني، ولا الطالب يشوف غير اللي مشترك فيه.' },
  { q: 'الطالب بيشترك إزاي؟', a: 'من صفحة المستر يضغط "اشترك"، يعمل حساب في دقيقة، والكورس المجاني يتفتح على طول. الكورس المدفوع يتفتح بعد إتمام الدفع.' },
  { q: 'الفيديو ممكن يتحمّل أو يتشارك؟', a: 'المشاهدة داخل المنصة بس، بجهاز واحد في نفس الوقت، وعلامة مائية بحساب الطالب على الشاشة. أي محاولة تحميل من برا المتصفح بتترفض.' },
  { q: 'إزاي بيتم الحضور والانصراف؟', a: 'كل طالب عنده كارت (QR أو RFID). المستر أو المشرف يعمل سكان من تليفونه، والنظام يسجّل دخول ولا خروج لوحده ويظهر في سجل ولي الأمر.' },
  { q: 'إيه اللي بيعمله الذكاء الاصطناعي بالظبط؟', a: 'يلخّص الدروس، يولّد أسئلة للبنك، ويحلّل أداء كل طالب بتوصيات. كله بيتراجع من المستر قبل ما يظهر للطلاب.' },
  { q: 'بيشتغل على الموبايل؟', a: 'أيوة، من المتصفح مباشرة بدون تثبيت — للطالب والمستر وولي الأمر.' },
]

export default function Home() {
  const [data, setData] = useState(null)
  const [q, setQ] = useState('')
  const [subject, setSubject] = useState('')
  const [open, setOpen] = useState(0)

  const { hash } = useLocation()

  useEffect(() => { api.get('/public/home').then(r => setData(r.data)).catch(() => setData({ stats: {}, teachers: [], bundles: [] })) }, [])

  // Links like /#packages from other pages: the router doesn't scroll to a hash by itself, and the target
  // section only has its full height once the data is in.
  useEffect(() => {
    if (!hash || !data) return
    const t = setTimeout(() => document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 80)
    return () => clearTimeout(t)
  }, [hash, data])

  const teachers = data?.teachers || []
  const bundles = data?.bundles || []
  const subjects = useMemo(() => [...new Set(teachers.map(t => t.subject).filter(Boolean))], [teachers])
  const shown = useMemo(() => teachers.filter(t => (!subject || t.subject === subject) && (!q.trim() || [t.name, t.tagline, t.subject, t.headline].join(' ').includes(q.trim()))), [teachers, q, subject])
  const stats = data?.stats || {}

  return (
    <div dir="rtl" className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav />

      {/* ---------- Hero ---------- */}
      <section className="relative overflow-hidden pb-16 pt-32 sm:pt-40">
        <div className="absolute inset-0 -z-20 mesh-bg" />
        <div className="absolute -top-32 -right-24 -z-10 h-[460px] w-[460px] rounded-full bg-brand-300/30 blur-3xl animate-blob" />
        <div className="absolute top-24 -left-28 -z-10 h-[380px] w-[380px] rounded-full bg-sky-400/25 blur-3xl animate-blob" style={{ animationDelay: '4s' }} />
        <div className="absolute inset-0 -z-10 bg-dots opacity-40 [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_40%,transparent_100%)]" />
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 lg:grid-cols-[1.1fr_0.9fr]">
          <div>
            <motion.span initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="chip border border-brand-200 bg-white/80 text-brand-700 shadow-soft backdrop-blur"><Sparkles size={14} /> منصة تعليمية بالذكاء الاصطناعي · مساحة مستقلة لكل مستر</motion.span>
            <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="mt-6 text-4xl font-black leading-[1.15] sm:text-6xl">
              <span className="block">كل مستر..</span>
              <span className="block min-h-[1.25em]"><Typewriter phrases={HEADLINES} /></span>
            </motion.h1>
            <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }} className="mt-5 max-w-xl text-lg leading-relaxed text-ink-500">
              اختار المستر، اشترك في كورسه، واتعلّم بفيديو محمي بيسألك أثناء الشرح، وامتحانات بتقرير نزاهة، وملخصات جاهزة بالذكاء الاصطناعي — وولي أمرك متابع كل خطوة.
            </motion.p>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} className="mt-8 flex flex-wrap gap-3">
              <a href="#teachers" className="btn-primary px-6 py-3 text-base">استكشف المدرسين <ArrowLeft size={18} /></a>
              <Link to="/contact" className="btn-ghost px-6 py-3 text-base"><Rocket size={18} /> عايز مساحة كمستر</Link>
            </motion.div>
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }} className="mt-10 grid max-w-md grid-cols-3 gap-3">
              {[['مدرس', stats.teachers], ['كورس', stats.courses], ['طالب', stats.students]].map(([l, v]) => (
                <div key={l} className="rounded-2xl border border-white/70 bg-white/70 p-4 text-center shadow-soft backdrop-blur">
                  <p className="text-3xl font-black text-brand-700"><Counter value={v || 0} />{v ? '+' : ''}</p>
                  <p className="mt-1 text-xs font-semibold text-ink-500">{l}</p>
                </div>
              ))}
            </motion.div>
          </div>
          <motion.div initial={{ opacity: 0, scale: 0.94 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.25, duration: 0.7 }} className="relative h-[380px] sm:h-[460px]">
            <BooksScene className="h-full w-full" />
            {[['أسئلة أثناء الفيديو', FileQuestion, 'top-6 right-2'], ['حضور بالـ QR', QrCode, 'bottom-16 left-0'], ['ملخص بالذكاء الاصطناعي', Brain, 'top-1/2 -left-2']].map(([t, Icon, pos], i) => (
              <motion.div key={t} animate={{ y: [0, -10, 0] }} transition={{ duration: 5 + i, repeat: Infinity, ease: 'easeInOut', delay: i * 0.8 }}
                className={`absolute ${pos} flex items-center gap-2 rounded-2xl border border-white/80 bg-white/90 px-3 py-2 text-xs font-bold text-ink-700 shadow-card backdrop-blur`}>
                <span className="grid h-7 w-7 place-items-center rounded-xl bg-brand-50 text-brand-600"><Icon size={15} /></span>{t}
              </motion.div>
            ))}
          </motion.div>
        </div>
      </section>

      {/* ---------- Marquee ---------- */}
      <div className="border-y border-brand-100 bg-white/70 py-3 backdrop-blur">
        <div className="marquee">
          <div className="marquee-track">
            {[...PILLS, ...PILLS].map((p, i) => <span key={i} className="chip mx-2 bg-brand-50 text-brand-700"><CheckCircle2 size={13} /> {p}</span>)}
          </div>
        </div>
      </div>

      {/* ---------- Teachers ---------- */}
      <section id="teachers" className="mx-auto max-w-7xl px-5 pt-20">
        <motion.div {...reveal} className="text-center">
          <span className="chip bg-brand-50 text-brand-700"><GraduationCap size={14} /> المدرسون</span>
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">اختار المستر.. وافتح عالمه</h2>
          <p className="mx-auto mt-3 max-w-2xl text-ink-500">كل كارت هيوديك لصفحة المستر نفسها: كورساته، طريقته، وزرار الاشتراك.</p>
        </motion.div>
        <motion.div {...reveal} className="mx-auto mt-7 flex max-w-2xl flex-wrap items-center justify-center gap-2">
          <label className="relative min-w-[220px] flex-1"><Search size={16} className="absolute right-3 top-3.5 text-ink-400" /><input className="input pr-10" placeholder="ابحث باسم المستر أو المادة..." value={q} onChange={e => setQ(e.target.value)} /></label>
          {subjects.length > 1 && <select className="input max-w-[180px]" value={subject} onChange={e => setSubject(e.target.value)}><option value="">كل المواد</option>{subjects.map(s => <option key={s} value={s}>{s}</option>)}</select>}
        </motion.div>
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {!data ? Array.from({ length: 3 }).map((_, i) => <div key={i} className="card h-96 animate-pulse" />)
            : shown.length === 0 ? <p className="card col-span-full p-10 text-center text-ink-400">لا يوجد مدرسون مطابقون بعد.</p>
            : shown.map((t, i) => <TeacherCard key={t.slug} t={t} i={i} />)}
        </div>
      </section>

      {/* ---------- Teacher packages ---------- */}
      <section id="packages" className="mx-auto max-w-7xl scroll-mt-24 px-5 pt-24">
        <motion.div {...reveal} className="text-center">
          <span className="chip bg-brand-50 text-brand-700"><Layers size={14} /> باقات المدرسين</span>
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">مدرسين اشتركوا مع بعض.. في باقة واحدة</h2>
          <p className="mx-auto mt-3 max-w-2xl text-ink-500">افتح الباقة، اتعرّف على كل مدرس وشوف فيديوهاته، واختار اللي هتكمّل معاه — وكل مدرس ليه مساحته وكورساته.</p>
        </motion.div>
        <div className="mt-12 grid grid-flow-row-dense gap-6 lg:grid-cols-3">
          {!data ? <div className="bx-card min-h-[30rem] animate-pulse lg:col-span-2 lg:row-span-2" />
            : bundles.map((b, i) => (
              <motion.div key={b.slug} {...reveal} transition={{ ...reveal.transition, delay: i * 0.1 }} className="lg:col-span-2 lg:row-span-2">
                <BundleCard bundle={b} className="h-full" />
              </motion.div>
            ))}
          {Array.from({ length: !data || bundles.length ? 2 : 3 }).map((_, i) => <EmptyBundleCard key={`empty-${i}`} i={i} />)}
        </div>
      </section>

      {/* ---------- Features ---------- */}
      <section className="mx-auto max-w-7xl px-5 pt-24">
        <motion.div {...reveal} className="text-center">
          <span className="chip bg-brand-50 text-brand-700"><Zap size={14} /> ليه منارة؟</span>
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">مش مجرد فيديوهات.. منظومة كاملة</h2>
          <p className="mx-auto mt-3 max-w-2xl text-ink-500">كل اللي المستر والطالب وولي الأمر محتاجينه، في مكان واحد ومترابط.</p>
        </motion.div>
        <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((f, i) => (
            <motion.div key={f.title} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-40px' }} transition={{ delay: (i % 4) * 0.08 }}>
              <TiltCard max={8} className="group h-full">
                <div className="card h-full p-6">
                  <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br ${f.tint} text-white shadow-glow`}><f.icon size={22} /></span>
                  <h3 className="mt-4 text-lg font-black">{f.title}</h3>
                  <p className="mt-2 text-sm leading-7 text-ink-500">{f.desc}</p>
                </div>
              </TiltCard>
            </motion.div>
          ))}
        </div>
      </section>

      {/* ---------- AI ---------- */}
      <section className="mx-auto max-w-7xl px-5 pt-24">
        <motion.div {...reveal} className="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-[#0f2a3d] via-[#123e4a] to-[#0b1b2b] p-8 text-white sm:p-12">
          <div className="absolute -top-24 -left-24 h-72 w-72 rounded-full bg-cyan-400/20 blur-3xl" />
          <div className="absolute -bottom-24 -right-24 h-72 w-72 rounded-full bg-violet-400/20 blur-3xl" />
          <div className="relative grid items-center gap-10 lg:grid-cols-[0.9fr_1.1fr]">
            <div>
              <span className="chip bg-white/10 text-cyan-100"><Brain size={14} /> مدعوم بـ Gemini</span>
              <h2 className="mt-4 text-3xl font-black leading-tight sm:text-4xl">الذكاء الاصطناعي بيشتغل مع المستر.. مش بداله</h2>
              <p className="mt-4 text-sm leading-8 text-cyan-50/80">كل حاجة بيولّدها النظام بتتراجع من المستر قبل ما تظهر للطالب. الهدف توفير ساعات التحضير، مش استبدال الشرح.</p>
              <Link to="/features" className="btn-primary mt-6 bg-white text-brand-800 hover:bg-cyan-50">شوف كل المميزات <ArrowLeft size={16} /></Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
              {AI.map((a, i) => (
                <motion.div key={a.title} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.12 }} className="rounded-3xl border border-white/10 bg-white/5 p-5 backdrop-blur">
                  <span className="grid h-11 w-11 place-items-center rounded-2xl bg-cyan-400/20 text-cyan-200"><a.icon size={20} /></span>
                  <h3 className="mt-3 font-black">{a.title}</h3>
                  <p className="mt-2 text-xs leading-6 text-cyan-50/75">{a.desc}</p>
                </motion.div>
              ))}
            </div>
          </div>
        </motion.div>
      </section>

      {/* ---------- How it works ---------- */}
      <section className="mx-auto max-w-7xl px-5 pt-24">
        <motion.div {...reveal} className="text-center">
          <span className="chip bg-brand-50 text-brand-700"><Play size={14} /> إزاي بتشتغل</span>
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">٣ خطوات.. وتبدأ</h2>
        </motion.div>
        <div className="mt-12 grid gap-8 lg:grid-cols-2">
          {[['للطالب', Users2, STUDENT_STEPS, 'from-brand-500 to-brand-700'], ['للمستر', GraduationCap, TEACHER_STEPS, 'from-violet-500 to-purple-700']].map(([who, Icon, steps, tint]) => (
            <motion.div key={who} {...reveal} className="card p-7">
              <div className="flex items-center gap-3"><span className={`grid h-11 w-11 place-items-center rounded-2xl bg-gradient-to-br ${tint} text-white`}><Icon size={20} /></span><h3 className="text-xl font-black">{who}</h3></div>
              <ol className="relative mt-6 space-y-5 border-r-2 border-dashed border-brand-100 pr-6">
                {steps.map((s) => (
                  <li key={s.n} className="relative">
                    <span className="absolute -right-[2.05rem] top-0 grid h-8 w-8 place-items-center rounded-full bg-brand-600 text-sm font-black text-white shadow-glow">{s.n}</span>
                    <p className="font-black">{s.title}</p>
                    <p className="mt-1 text-sm leading-7 text-ink-500">{s.desc}</p>
                  </li>
                ))}
              </ol>
            </motion.div>
          ))}
        </div>
      </section>

      {/* ---------- Protection band ---------- */}
      <section className="mx-auto max-w-7xl px-5 pt-24">
        <motion.div {...reveal} className="grid items-center gap-8 rounded-[2rem] border border-brand-100 bg-white p-8 shadow-card sm:p-10 lg:grid-cols-2">
          <div>
            <span className="chip bg-emerald-50 text-emerald-700"><Lock size={14} /> حماية المحتوى والامتحانات</span>
            <h2 className="mt-4 text-3xl font-black">شرحك ليك.. وامتحانك بجد</h2>
            <p className="mt-3 text-sm leading-8 text-ink-500">اتبنت المنصة من الأول على إن المحتوى ما يتسرّبش والامتحان يقيس الطالب فعلاً — من غير ما نعقّد حياة حد.</p>
          </div>
          <ul className="grid gap-3 sm:grid-cols-2">
            {[[Eye, 'علامة مائية بحساب الطالب على الفيديو'], [Smartphone, 'جهاز واحد لكل حساب أثناء المشاهدة'], [Timer, 'مؤقّت وحفظ تلقائي أثناء الامتحان'], [ShieldCheck, 'رصد الخروج من الشاشة وتقرير نزاهة'], [Lock, 'رفض التحميل من خارج المتصفح'], [Bell, 'تنبيه فوري لولي الأمر عند الغياب']].map(([Icon, t]) => (
              <li key={t} className="flex items-center gap-3 rounded-2xl bg-ink-50 p-3 text-sm font-semibold"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white text-brand-600 shadow-soft"><Icon size={17} /></span>{t}</li>
            ))}
          </ul>
        </motion.div>
      </section>

      {/* ---------- FAQ ---------- */}
      <section className="mx-auto max-w-3xl px-5 pt-24">
        <motion.div {...reveal} className="text-center">
          <span className="chip bg-brand-50 text-brand-700"><MessageCircle size={14} /> أسئلة شائعة</span>
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">اللي بيتسأل كتير</h2>
        </motion.div>
        <div className="mt-10 space-y-3">
          {FAQ.map((f, i) => (
            <motion.div key={f.q} {...reveal} className="card overflow-hidden">
              <button onClick={() => setOpen(open === i ? -1 : i)} className="flex w-full items-center justify-between gap-4 p-5 text-right font-black" aria-expanded={open === i}>
                {f.q}<ChevronDown size={18} className={`shrink-0 text-brand-600 transition ${open === i ? 'rotate-180' : ''}`} />
              </button>
              <AnimatePresence initial={false}>
                {open === i && <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
                  <p className="px-5 pb-5 text-sm leading-8 text-ink-500">{f.a}</p>
                </motion.div>}
              </AnimatePresence>
            </motion.div>
          ))}
        </div>
      </section>

      {/* ---------- Final CTA ---------- */}
      <section className="mx-auto max-w-7xl px-5 py-24">
        <motion.div {...reveal} className="relative overflow-hidden rounded-[2rem] bg-gradient-to-l from-brand-600 to-brand-800 p-10 text-center text-white sm:p-14">
          <div className="absolute -top-20 right-1/3 h-64 w-64 rounded-full bg-white/10 blur-3xl" />
          <Star className="mx-auto text-amber-300" size={34} />
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">جاهز تبدأ؟</h2>
          <p className="mx-auto mt-3 max-w-xl text-brand-50/90">لو طالب.. اختار مسترك من فوق. ولو مستر.. مساحتك الخاصة بتتجهّز في دقايق.</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <a href="#teachers" className="btn-primary bg-white text-brand-800 hover:bg-brand-50 px-6 py-3">اختار مسترك <ArrowLeft size={18} /></a>
            <Link to="/contact" className="btn-ghost bg-white/15 text-white hover:bg-white/25 px-6 py-3"><Rocket size={18} /> اطلب مساحة كمستر</Link>
          </div>
        </motion.div>
      </section>

      <MarketingFooter />
    </div>
  )
}
