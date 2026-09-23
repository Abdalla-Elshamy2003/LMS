import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { motion, useReducedMotion, useScroll, useTransform } from 'framer-motion'
import {
  ArrowUpLeft, BookOpen, ChevronDown, Clock, Layers, MousePointerClick, Play, PlayCircle, Sparkles, Users, X,
} from 'lucide-react'
import api from '../lib/api'
import { fmtMoney } from '../lib/format'
import { embedUrl } from '../lib/videoEmbed'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import { BundleStage } from '../components/bundles/BundleCard'
import { glyphFor, ordinal, tintStyle } from '../components/bundles/bundleArt'
import '../components/bundles/bundles.css'

const GLYPH_SPOTS = ['top-[18%] right-[8%]', 'top-[30%] left-[7%]', 'top-[12%] left-[30%]', 'bottom-[26%] right-[18%]', 'bottom-[20%] left-[16%]', 'top-[40%] right-[30%]']
const MAX_VIDEOS = 6

/**
 * A teacher package's own landing page. The hero lines up every teacher with their subject; scrolling walks
 * through one section per teacher (their story and videos), with a sticky rail showing where you are. Every
 * video card leads into that teacher's own space, where all their courses live.
 */
export default function BundleLanding() {
  const { slug } = useParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [active, setActive] = useState(0)
  const [player, setPlayer] = useState(null)
  const sections = useRef([])
  const hero = useRef(null)
  const reduced = useReducedMotion()
  const { scrollYProgress } = useScroll({ target: hero, offset: ['start start', 'end start'] })
  const stageY = useTransform(scrollYProgress, [0, 1], [0, reduced ? 0 : 140])
  const stageFade = useTransform(scrollYProgress, [0, 0.85], [1, reduced ? 1 : 0.25])

  useEffect(() => {
    let live = true
    setData(null); setError('')
    api.get(`/public/bundles/${slug}`).then(r => live && setData(r.data)).catch(() => live && setError('الباقة دي مش متاحة دلوقتي.'))
    return () => { live = false }
  }, [slug])
  useEffect(() => { if (data) { const old = document.title; document.title = `${data.name} | منارة`; return () => { document.title = old } } }, [data])

  // Scroll-spy for the rail: the section crossing the middle of the screen is the "current" teacher.
  useEffect(() => {
    if (!data) return
    const io = new IntersectionObserver(entries => entries.forEach(e => {
      if (e.isIntersecting) setActive(Number(e.target.dataset.index))
    }), { rootMargin: '-45% 0px -50% 0px' })
    sections.current.forEach(el => el && io.observe(el))
    return () => io.disconnect()
  }, [data])

  const go = (i) => sections.current[i]?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })

  if (!data) return (
    <div dir="rtl" className="min-h-screen bg-[#f6fbff]">
      <MarketingNav solid />
      <div className="grid min-h-screen place-items-center px-5 text-center">
        {error
          ? <div><Layers className="mx-auto text-brand-500" size={40} /><p className="mt-4 font-bold text-ink-600">{error}</p><Link to="/#packages" className="btn-primary mt-6 inline-flex">شوف الباقات المتاحة</Link></div>
          : <div className="h-10 w-10 animate-spin rounded-full border-4 border-brand-200 border-t-brand-600" aria-label="جارٍ التحميل" />}
      </div>
    </div>
  )

  const members = data.members || []
  // Only what is really there: linked teachers' published videos and courses, plus intro videos.
  const videoCount = members.reduce((n, m) => n + (m.introVideoUrl ? 1 : 0) + (m.teacher?.videos?.length || 0), 0)
  const courseCount = members.reduce((n, m) => n + (m.teacher?.courses?.length || 0), 0)
  const totals = [[members.length, 'مدرسين', Users], [videoCount, 'فيديو', PlayCircle], [courseCount, 'كورس', BookOpen]].filter(([n]) => n > 0)

  return (
    <div dir="rtl" className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />

      {/* ---------- Hero: every teacher, side by side ---------- */}
      <section ref={hero} className="bx-hero pb-14 pt-28 sm:pt-36">
        <span className="bx-grid" aria-hidden="true" />
        <span className="bx-orbit" aria-hidden="true" />
        <span className="bx-orbit two" aria-hidden="true" />
        <span className="bx-glow -top-24 right-[10%] bg-cyan-400" aria-hidden="true" />
        <span className="bx-glow bottom-0 left-[5%] bg-sky-500" style={{ animationDelay: '-6s' }} aria-hidden="true" />
        <span className="bx-glow bottom-[18%] right-[4%] !w-56 bg-amber-400 !opacity-20" style={{ animationDelay: '-3s' }} aria-hidden="true" />
        {members.slice(0, GLYPH_SPOTS.length).map((m, i) => (
          <span key={i} dir="ltr" className={`bx-float-glyph hidden sm:block ${GLYPH_SPOTS[i]}`} style={{ animationDelay: `${-i * 2.3}s` }} aria-hidden="true">{glyphFor(m.subject)}</span>
        ))}

        <div className="mx-auto max-w-7xl px-5 text-center">
          <motion.nav initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex items-center justify-center gap-2 text-xs text-cyan-100/70" aria-label="مسار الصفحة">
            <Link to="/" className="hover:text-white">الرئيسية</Link><span>/</span><Link to="/#packages" className="hover:text-white">باقات المدرسين</Link>
          </motion.nav>
          <motion.span initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.05 }}
            className="chip mt-5 border border-white/15 bg-white/10 text-cyan-100 backdrop-blur"><Layers size={14} /> باقة مدرسين · {members.length.toLocaleString('ar-EG')} مدرسين</motion.span>
          <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
            className="mx-auto mt-5 max-w-4xl bg-gradient-to-l from-white via-sky-100 to-cyan-200 bg-clip-text pb-2 text-4xl font-black leading-[1.25] text-transparent sm:text-6xl">{data.name}</motion.h1>
          {data.tagline && <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.18 }} className="mt-3 text-lg font-bold text-cyan-100/90">{data.tagline}</motion.p>}
          {data.description && <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.24 }} className="mx-auto mt-4 max-w-2xl text-sm leading-8 text-sky-50/85">{data.description}</motion.p>}

          <motion.div style={{ y: stageY, opacity: stageFade }} className="mt-14">
            <BundleStage members={members} base="clamp(60px, 17vw, 196px)" showNames onPick={go} />
          </motion.div>

          <motion.div initial={reduced ? false : { opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.9 }}
            className="mx-auto mt-14 flex max-w-3xl flex-wrap items-center justify-center gap-2 sm:gap-4">
            {totals.map(([n, label, Icon]) => (
              <span key={label} className="inline-flex items-center gap-2.5 rounded-2xl border border-white/10 bg-white/[.06] px-3.5 py-2 backdrop-blur sm:px-4 sm:py-2.5">
                <span className="hidden h-8 w-8 place-items-center rounded-xl bg-sky-400/15 text-sky-200 sm:grid"><Icon size={16} /></span>
                <span className="text-right leading-tight"><b className="block text-lg font-black text-white">{n.toLocaleString('ar-EG')}</b><small className="text-[11px] text-sky-100/70">{label}</small></span>
              </span>
            ))}
          </motion.div>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <button type="button" onClick={() => go(0)} className="inline-flex items-center gap-2 rounded-2xl bg-white px-6 py-3 text-sm font-black text-brand-800 shadow-soft transition hover:-translate-y-0.5 hover:bg-sky-50">
              اتعرّف على المدرسين <ChevronDown className="bx-scroll-hint" size={18} />
            </button>
            <Link to="/#packages" className="inline-flex items-center gap-2 rounded-2xl border border-white/20 px-6 py-3 text-sm font-bold text-sky-50 transition hover:bg-white/10">كل الباقات <ArrowUpLeft size={16} /></Link>
          </div>
        </div>
      </section>

      {/* ---------- Sticky rail ---------- */}
      <div className="sticky top-[68px] z-30 border-b border-ink-100 bg-white/85 backdrop-blur-lg">
        <div className="bx-rail mx-auto flex max-w-7xl gap-2 overflow-x-auto px-5 py-2.5">
          {members.map((m, i) => (
            <button key={i} type="button" onClick={() => go(i)} aria-current={active === i} style={tintStyle(i)}
              className="bx-rail-item flex shrink-0 items-center gap-2 rounded-full bg-ink-50 py-1 pl-4 pr-1 text-sm font-bold text-ink-600 hover:bg-ink-100">
              <span className="h-8 w-8 overflow-hidden rounded-full bg-ink-200">{m.photoUrl && <img src={m.photoUrl} alt="" className="h-full w-full object-cover object-top" />}</span>
              {m.subject}
            </button>
          ))}
        </div>
      </div>

      {/* ---------- One section per teacher ---------- */}
      {members.map((m, i) => (
        <MemberSection key={m.id ?? i} m={m} i={i} onPlay={setPlayer}
          sectionRef={el => { sections.current[i] = el }} />
      ))}

      {/* ---------- How to join ---------- */}
      <section className="mx-auto max-w-7xl px-5 pt-20">
        <motion.div initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-center">
          <span className="chip bg-brand-50 text-brand-700"><MousePointerClick size={14} /> تبدأ إزاي؟</span>
          <h2 className="mt-4 text-3xl font-black sm:text-4xl">٣ خطوات وتبقى مع مدرسك</h2>
        </motion.div>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {[
            [Users, 'اختار مدرسك', 'اتفرّج على فيديوهات مدرسين الباقة واختار اللي مرتاح لشرحه.'],
            [BookOpen, 'ادخل مساحته', 'كل مدرس ليه مساحة خاصة فيها كل كورساته وتفاصيل الاشتراك.'],
            [Sparkles, 'اشترك وابدأ', 'الكورس المجاني يتفتح فوراً، والمدفوع بيتفتح بكود من المدرس بعد الدفع.'],
          ].map(([Icon, title, desc], i) => (
            <motion.div key={title} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.1 }}
              className="card relative overflow-hidden p-7">
              <span className="absolute -left-2 -top-4 font-serif text-7xl font-black italic text-brand-50">{ordinal(i)}</span>
              <span className="relative grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-glow"><Icon size={22} /></span>
              <h3 className="relative mt-4 text-lg font-black">{title}</h3>
              <p className="relative mt-2 text-sm leading-7 text-ink-500">{desc}</p>
            </motion.div>
          ))}
        </div>
      </section>

      {/* ---------- Final CTA ---------- */}
      <section className="mx-auto max-w-7xl px-5 py-20">
        <motion.div initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
          className="bx-card flex flex-col items-center gap-6 p-10 text-center sm:p-14">
          <span className="bx-grid" aria-hidden="true" />
          <h2 className="text-3xl font-black sm:text-4xl">مين هيكون مدرسك؟</h2>
          <p className="max-w-xl text-cyan-50/80">دوس على أي مدرس وارجع لقسمه، أو ادخل مساحته على طول.</p>
          <div className="flex flex-wrap justify-center gap-3">
            {members.map((m, i) => (
              <button key={i} type="button" onClick={() => go(i)} style={tintStyle(i)}
                className="group flex items-center gap-2 rounded-full bg-white/10 py-1.5 pl-4 pr-1.5 text-sm font-bold backdrop-blur transition hover:bg-white hover:text-ink-800">
                <span className="bx-accent-bg h-9 w-9 overflow-hidden rounded-full p-0.5">{m.photoUrl && <img src={m.photoUrl} alt="" className="h-full w-full rounded-full object-cover object-top" />}</span>
                {m.subject}
              </button>
            ))}
          </div>
        </motion.div>
      </section>

      <MarketingFooter />
      {player && <PlayerModal video={player} onClose={() => setPlayer(null)} />}
    </div>
  )
}

function MemberSection({ m, i, onPlay, sectionRef }) {
  const reduced = useReducedMotion()
  const t = m.teacher
  const href = t ? `/t/${t.slug}` : null
  const who = m.name || `مدرس ${m.subject}`
  const videos = [
    ...(m.introVideoUrl ? [{ title: `تعرّف على ${who}`, url: m.introVideoUrl, poster: '', category: 'فيديو تعريفي' }] : []),
    ...(t?.videos || []),
  ]
  const courses = t?.courses || []
  const flip = i % 2 === 1
  const from = (dir) => (reduced ? false : { opacity: 0, x: dir * 60 })

  return (
    <section ref={sectionRef} data-index={i} id={`teacher-${i + 1}`} style={tintStyle(i)}
      className={`bx-member relative scroll-mt-32 overflow-hidden py-20 sm:py-24 ${flip ? 'bg-white' : ''}`}>
      <span className="bx-accent-bg absolute inset-x-0 top-0 h-[3px] opacity-60" aria-hidden="true" />
      <div className={`mx-auto grid max-w-7xl items-center gap-12 px-5 lg:grid-cols-[0.85fr_1.15fr] ${flip ? 'lg:[&>*:first-child]:order-2' : ''}`}>
        {/* Portrait */}
        <motion.div initial={from(flip ? -1 : 1)} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true, amount: 0.3 }} transition={{ duration: 0.7, ease: 'easeOut' }}
          className="relative mx-auto w-full max-w-[21rem]">
          <span className="bx-accent-bg absolute inset-x-6 bottom-6 top-16 rounded-full opacity-25 blur-3xl" aria-hidden="true" />
          <span dir="ltr" className="bx-member-glyph left-1/2 top-[40%] -translate-x-1/2 -translate-y-1/2" aria-hidden="true">{glyphFor(m.subject)}</span>
          <div className="bx-float relative" style={{ '--i': i }}>
            <span className="bx-arch">
              <span className="bx-arch-photo">
                {m.photoUrl ? <img src={m.photoUrl} alt={who} loading="lazy" /> : <span className="bx-arch-fallback" dir="ltr">{glyphFor(m.subject)}</span>}
              </span>
            </span>
            <span className="absolute -bottom-5 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-2xl bg-white px-5 py-2.5 text-sm font-black shadow-card">
              <span className="bx-accent-text">{m.subject}</span>
            </span>
            <span className="bx-accent-bg bx-number absolute -top-3 right-2 grid h-12 w-12 place-items-center rounded-2xl text-white shadow-soft">{ordinal(i)}</span>
          </div>
        </motion.div>

        {/* Story */}
        <motion.div initial={reduced ? false : { opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.3 }} transition={{ duration: 0.6, delay: 0.1 }}
          className="relative text-center lg:text-right">
          <span className="chip bg-ink-100 text-ink-600"><span className="bx-accent-bg h-2 w-2 rounded-full" /> مادة {m.subject}</span>
          <h2 className="mt-4 text-3xl font-black leading-tight sm:text-5xl">{m.name || <span className="bx-accent-text">{m.subject}</span>}</h2>
          {t?.headline && <p className="mt-4 text-lg font-bold leading-8 text-ink-700">{t.headline}</p>}
          {t?.description && <p className="mt-3 text-sm leading-8 text-ink-500 lg:max-w-xl">{t.description}</p>}
          <div className="mt-6 flex flex-wrap justify-center gap-2 lg:justify-start">
            <span className="chip bg-white text-ink-700 shadow-soft ring-1 ring-ink-100"><PlayCircle size={14} /> {videos.length.toLocaleString('ar-EG')} فيديو</span>
            {t && <span className="chip bg-white text-ink-700 shadow-soft ring-1 ring-ink-100"><BookOpen size={14} /> {courses.length.toLocaleString('ar-EG')} كورس</span>}
          </div>
          <div className="mt-8">
            {href
              ? <Link to={href} className="bx-accent-bg inline-flex items-center gap-2 rounded-2xl px-6 py-3.5 text-sm font-black text-white shadow-soft transition hover:-translate-y-0.5 hover:shadow-card">
                  ادخل مساحة المدرس وكل كورساته <ArrowUpLeft size={17} />
                </Link>
              : <span className="inline-flex items-center gap-2 rounded-2xl border border-dashed border-ink-200 bg-white px-5 py-3 text-sm font-bold text-ink-500"><Clock size={16} /> صفحة المدرس قريباً</span>}
          </div>
        </motion.div>
      </div>

      {/* Videos (or, until there are any, the teacher's courses) */}
      <div className="mx-auto mt-16 max-w-7xl px-5">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h3 className="text-xl font-black">{videos.length ? 'فيديوهات المدرس' : courses.length ? 'كورسات المدرس' : 'فيديوهات المدرس'}</h3>
          {href && (videos.length > 0 || courses.length > 0) && <p className="text-xs font-semibold text-ink-400">اختار أي كارت وهتدخل على مساحة المدرس وكل كورساته</p>}
        </div>
        <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {videos.length > 0
            ? videos.slice(0, MAX_VIDEOS).map((v, k) => <VideoCard key={k} v={v} k={k} subject={m.subject} href={href} onPlay={() => onPlay(v)} />)
            : courses.length > 0
              ? courses.slice(0, MAX_VIDEOS).map((c, k) => <CourseCard key={c.id} c={c} k={k} href={href} />)
              : <div className="col-span-full grid place-items-center rounded-3xl border-2 border-dashed border-ink-200 bg-white/60 p-10 text-center">
                  <PlayCircle className="bx-accent-text text-ink-300" size={34} />
                  <p className="mt-3 font-bold text-ink-600">الفيديوهات هتنزل قريباً</p>
                  <p className="mt-1 text-xs text-ink-400">أول ما المدرس يرفع فيديوهاته هتظهر هنا.</p>
                </div>}
          {href && videos.length > MAX_VIDEOS && (
            <Link to={href} className="bx-video grid min-h-[12rem] place-items-center p-6 text-center">
              <span><span className="bx-accent-text text-4xl font-black">+{(videos.length - MAX_VIDEOS).toLocaleString('ar-EG')}</span><span className="mt-2 block text-sm font-bold text-ink-600">فيديو كمان في مساحة المدرس</span></span>
            </Link>
          )}
        </div>
      </div>
    </section>
  )
}

/**
 * The card opens the teacher's space (the whole card is one link); the play button above it previews the video
 * in place without leaving the page. Without a teacher space yet, the card itself plays the preview.
 */
function VideoCard({ v, k, subject, href, onPlay }) {
  const reduced = useReducedMotion()
  const body = (
    <>
      <span className="bx-video-cover block">
        {v.poster ? <img src={v.poster} alt="" loading="lazy" /> : <span className="bx-video-glyph" dir="ltr">{glyphFor(subject)}</span>}
        {v.category && <span className="absolute right-3 top-3 z-[1] rounded-full bg-white/90 px-3 py-1 text-[11px] font-black text-ink-700">{v.category}</span>}
      </span>
      <span className="block p-5">
        <span className="line-clamp-1 block font-black text-ink-800">{v.title}</span>
        {v.description && <span className="mt-1 line-clamp-2 block text-xs leading-6 text-ink-500">{v.description}</span>}
        <span className="bx-accent-text mt-3 inline-flex items-center gap-1.5 text-xs font-black">
          {href ? <>ادخل مساحة المدرس <ArrowUpLeft size={14} className="text-ink-400" /></> : <>شاهد الفيديو <Play size={13} className="text-ink-400" /></>}
        </span>
      </span>
    </>
  )
  return (
    <motion.div initial={reduced ? false : { opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }}
      transition={{ delay: (k % 3) * 0.1, duration: 0.5 }} className="relative">
      {href
        ? <Link to={href} className="bx-video" aria-label={`${v.title} — ادخل مساحة المدرس`}>{body}</Link>
        : <button type="button" onClick={onPlay} className="bx-video w-full text-right" aria-label={`شاهد ${v.title}`}>{body}</button>}
      {/* Sits over the cover only, so the play button stays centred on the artwork at any card width. */}
      <span className="pointer-events-none absolute inset-x-0 top-0 z-[3] aspect-[16/10]">
        {href
          ? <button type="button" onClick={onPlay} className="bx-play pointer-events-auto" aria-label={`معاينة ${v.title}`}><Play size={20} fill="currentColor" /></button>
          : <span className="bx-play" aria-hidden="true"><Play size={20} fill="currentColor" /></span>}
      </span>
    </motion.div>
  )
}

function CourseCard({ c, k, href }) {
  const reduced = useReducedMotion()
  return (
    <motion.div initial={reduced ? false : { opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ delay: (k % 3) * 0.1 }}>
      <Link to={href} className="bx-video">
        <span className="bx-video-cover block">
          {c.coverUrl ? <img src={c.coverUrl} alt="" loading="lazy" /> : <span className="bx-video-glyph"><BookOpen size={40} /></span>}
          {c.grade && <span className="absolute right-3 top-3 z-[1] rounded-full bg-white/90 px-3 py-1 text-[11px] font-black text-ink-700">{c.grade}</span>}
        </span>
        <span className="block p-5">
          <span className="line-clamp-1 block font-black text-ink-800">{c.title}</span>
          <span className="mt-3 flex items-center justify-between text-xs font-black">
            <span className="text-ink-600">{Number(c.finalPrice) > 0 ? fmtMoney(c.finalPrice) : 'مجاني'}</span>
            <span className="bx-accent-text inline-flex items-center gap-1">اشترك من مساحة المدرس <ArrowUpLeft size={14} className="text-ink-400" /></span>
          </span>
        </span>
      </Link>
    </motion.div>
  )
}

function PlayerModal({ video, onClose }) {
  const embed = embedUrl(video.url)
  useEffect(() => {
    const previous = document.activeElement, overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = overflow; previous?.focus?.() }
  }, [onClose])
  return (
    <div className="fixed inset-0 z-[100] grid place-items-center bg-[#08172ad9] p-4 backdrop-blur-sm" onClick={onClose}>
      <motion.div initial={{ opacity: 0, scale: 0.94, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} role="dialog" aria-modal="true" aria-labelledby="bx-player-title"
        className="w-full max-w-3xl rounded-3xl bg-white p-4 shadow-2xl sm:p-6" onClick={e => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="min-w-0"><p className="text-xs font-bold text-ink-400">{video.category || 'فيديو'}</p><h2 id="bx-player-title" className="truncate text-lg font-black">{video.title}</h2></div>
          <button type="button" autoFocus onClick={onClose} className="btn-ghost px-2.5" aria-label="إغلاق"><X size={18} /></button>
        </div>
        <div className="bx-modal-video">
          {embed
            ? <iframe src={embed} title={video.title} allow="accelerometer; encrypted-media; picture-in-picture; fullscreen" allowFullScreen />
            : <video src={video.url} controls autoPlay playsInline poster={video.poster || undefined} />}
        </div>
        {video.description && <p className="mt-4 text-sm leading-7 text-ink-500">{video.description}</p>}
      </motion.div>
    </div>
  )
}
