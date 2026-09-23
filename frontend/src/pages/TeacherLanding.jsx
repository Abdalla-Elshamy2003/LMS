import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { motion, useReducedMotion } from 'framer-motion'
import { ArrowUpLeft, ArrowLeft, Play, Check, Sparkles, BookOpen, PenTool, Target, ChevronDown, GraduationCap, Phone, Menu, X } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { apiErrorMessage } from '../lib/apiError'
import { fmtMoney } from '../lib/format'
import MathBackdrop from '../components/MathBackdrop'
import { embedUrl } from '../lib/videoEmbed'
import './teacher-landing.css'

const samples = [
  { id: 'demo-1', title: 'أسّس صح.. وانطلق بثقة', grade: 'الصف الأول الثانوي', description: 'الجبر وحساب المثلثات، من المفهوم الأول لحد حل المسألة بنفسك.', price: 300, discountPercent: 30, finalPrice: 210, symbol: '𝑥² + 𝑦²', label: 'بداية قوية', tone: 'blue' },
  { id: 'demo-2', title: 'فكّ شفرة الرياضيات', grade: 'الصف الثاني الثانوي', description: 'نفهم العلاقات والدوال ونربط الأفكار بتدريبات متدرجة.', price: 350, discountPercent: 0, finalPrice: 350, symbol: 'ƒ(𝑥)', label: 'افهم الفكرة', tone: 'peach' },
  { id: 'demo-3', title: 'كل مسألة ليها مفتاح', grade: 'الصف الثالث الثانوي', description: 'التفاضل والتكامل بشكل منظّم، مع مراجعة على أهم الأفكار.', price: 400, discountPercent: 0, finalPrice: 400, symbol: '∫ 𝑥 d𝑥', label: 'جاهز للتحدي؟', tone: 'green' },
  { id: 'demo-4', title: 'الهندسة بتتشاف مش بتتحفظ', grade: 'الصف الأول الإعدادي', description: 'المساحات والحجوم بالرسم والتخيّل قبل أي قانون.', price: 250, discountPercent: 20, finalPrice: 200, symbol: 'V = ⅓πr²h', label: 'إبدأ من هنا', tone: 'blue' },
  { id: 'demo-5', title: 'الإحصاء والاحتمالات ببساطة', grade: 'الصف الثاني الإعدادي', description: 'من قراءة الجدول لحد حساب الاحتمال، خطوة خطوة.', price: 270, discountPercent: 0, finalPrice: 270, symbol: 'P(A)', label: 'أرقام ليها معنى', tone: 'peach' },
  { id: 'demo-6', title: 'أدواتك في الجبر والهندسة', grade: 'الصف الثالث الإعدادي', description: 'مراجعة شاملة تجمع كل أدوات السنة في مكان واحد.', price: 290, discountPercent: 15, finalPrice: 247, symbol: 'y = x²', label: 'جاهز للثانوي', tone: 'green' },
]
const marqueeSymbols = ['a² + b² = c²', 'ƒ(x) = 2x + 3', '∫ x dx', 'π ≈ 3.14159', '∑ⁿ', 'lim x→0', 'd/dx (x²) = 2x', '√x', 'sin²θ + cos²θ = 1', 'n!', 'Δ = b² − 4ac', 'y = mx + b']
const lessons = [
  { title: 'الدالة مش مجرد قانون', tag: 'جبر', formula: 'ƒ(𝑥) = 2𝑥 + 3', text: 'الدالة زي ماكينة: بتدخل لها قيمة، فتطلع لك قيمة جديدة حسب قاعدة ثابتة. لو س = ٢، يبقى د(٢) = ٢ × ٢ + ٣ = ٧. جرّب بنفسك لما س = ٥!', answer: 'الإجابة: د(٥) = ١٣' },
  { title: 'المثلث بيحكيلك إيه؟', tag: 'هندسة', formula: 'a² + b² = c²', text: 'في المثلث القائم: مربع الوتر يساوي مجموع مربعي الضلعين الآخرين. لو الضلعان ٣ و٤، مربع الوتر = ٩ + ١٦ = ٢٥، يعني الوتر = ٥.', answer: 'جرّب: لو الضلعان ٦ و٨، الوتر = ١٠' },
  { title: 'أول خطوة في التفاضل', tag: 'تفاضل', formula: 'd/d𝑥 (𝑥²) = 2𝑥', text: 'المشتقة بتوصف معدل تغيّر الدالة. في د(س) = س²، المشتقة = ٢س. عند س = ٣، ميل المماس يساوي ٦.', answer: 'فكرة الدرس: المشتقة تربط الدالة بمعدل تغيّرها.' },
]
export default function TeacherLanding() {
  const { slug } = useParams()
  const [data, setData] = useState(null), [error, setError] = useState(''), [menu, setMenu] = useState(false), [lesson, setLesson] = useState(null)
  // A signed-in student joins this teacher with the account they already have (or, if they already study with
  // them, just goes in) — no second registration, no second password.
  const { user, joinTeacher, switchTeacher } = useAuth()
  const asStudent = user?.role === 'STUDENT'
  const [mine, setMine] = useState(null), [joining, setJoining] = useState(false), [joinError, setJoinError] = useState('')
  useEffect(() => { if (asStudent) api.get('/me/teachers').then(r => setMine(r.data)).catch(() => setMine([])) }, [asStudent])
  const reduced = useReducedMotion()
  const dialog = useRef(null)
  useEffect(() => { let active = true; setData(null); setError(''); api.get(`/public/academies/${slug || 'default'}`).then(r => active && setData(r.data)).catch(() => active && setError('صفحة المدرس غير متاحة حالياً. حاول مرة أخرى لاحقاً.')); return () => { active = false } }, [slug])
  useEffect(() => { if (data) { const old = document.title; document.title = `${data.profile.name} | ${data.profile.subject}`; return () => { document.title = old } } }, [data])
  useEffect(() => {
    if (!lesson) return
    const previous = document.activeElement, overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const close = e => {
      if (e.key === 'Escape') setLesson(null)
      if (e.key === 'Tab') {
        const buttons = dialog.current?.querySelectorAll('button'), first = buttons?.[0], last = buttons?.[buttons.length - 1]
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus() }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus() }
      }
    }
    window.addEventListener('keydown', close)
    return () => { window.removeEventListener('keydown', close); document.body.style.overflow = overflow; previous?.focus() }
  }, [lesson])
  if (!data) return <div className="teacher-site tl-loading" dir="rtl"><GraduationCap size={40}/><p>{error || 'بنجهّز لك مساحة التعلّم…'}</p>{error && <Link to="/login" className="tl-button">دخول الإدارة</Link>}</div>
  const p = data.profile, demo = p.demoContent
  // Courses and published videos share one slider — whichever the teacher adds shows up here.
  const videoCards = (data.videos || []).map((v, i) => ({
    id: `video-${i}`, title: v.title, description: v.description, coverUrl: v.poster,
    year: v.category, isVideo: true, video: v,
  }))
  const cards = [...data.courses, ...videoCards, ...(demo ? samples : [])]
  const login = `/login?academy=${encodeURIComponent(p.slug)}`
  const seat = mine?.find(m => m.slug === p.slug)
  const enter = async (courseId) => {
    if (joining) return
    setJoining(true); setJoinError('')
    try { seat ? await switchTeacher(seat.userId, '/app/courses') : await joinTeacher(p.slug, courseId, '/app/courses') }
    catch (e) { setJoinError(apiErrorMessage(e, 'تعذّر الانضمام للمستر، حاول تاني')); setJoining(false) }
  }
  const reveal = { initial: reduced ? false : { opacity: 0, y: 22 }, whileInView: { opacity: 1, y: 0 }, viewport: { once: true, amount: 0.12 }, transition: { duration: .55 } }
  const cta = p.phone ? `tel:${p.phone.replace(/[^+0-9]/g, '')}` : login
  return <div className="teacher-site" dir="rtl">
    <div className="tl-announcement"><Sparkles size={14}/><span>خطوة جديدة، فهم أعمق، وثقة أكبر. يلا نبدأ!</span><span className="tl-mini-dot"/> العام الدراسي ٢٠٢٦ / ٢٠٢٧</div>
    <header className="tl-header"><div className="tl-container tl-nav">
      <Link to={slug ? `/t/${slug}` : '/'} className="tl-brand"><img src="/images/logo.png" alt="" className="tl-logo" /><span><strong>{p.name}</strong><small>{p.tagline}</small></span></Link>
      <nav className={menu ? 'tl-links open' : 'tl-links'} aria-label="القائمة الرئيسية"><a href="#home" onClick={() => setMenu(false)}>الرئيسية</a><a href="#about" onClick={() => setMenu(false)}>عن المستر</a><a href="#courses" onClick={() => setMenu(false)}>الكورسات</a>{demo && <a href="#lessons" onClick={() => setMenu(false)}>جرّب الشرح</a>}<a href="#faq" onClick={() => setMenu(false)}>الأسئلة الشائعة</a></nav>
      {asStudent
        ? <button type="button" onClick={() => enter()} disabled={joining || !mine} className="tl-button small">{seat ? 'ادخل على كورساتك' : 'انضم للمستر ده بحسابك'} <ArrowUpLeft size={16}/></button>
        : <Link to={login} className="tl-button small">دخول الطالب <ArrowUpLeft size={16}/></Link>}<button className="tl-menu" aria-label="فتح القائمة" aria-expanded={menu} onClick={() => setMenu(!menu)}>{menu ? <X/> : <Menu/>}</button>
    </div></header>
    <main>
      <section id="home" className="tl-hero"><MathBackdrop count={18} /><div className="tl-container tl-hero-grid">
        <motion.div {...reveal} className="tl-hero-copy"><span className="tl-eyebrow"><span/> أهلاً بيك في منصتك لـ{p.subject}</span>
          <h1>{p.headline}</h1><p className="tl-description">{p.description}</p>
          <div className="tl-actions"><a href="#courses" className="tl-button">اكتشف كورساتك <ArrowLeft size={19}/></a>{demo && <a href="#lessons" className="tl-watch"><span><Play size={17} fill="currentColor"/></span> خد فكرة عن الشرح</a>}</div>
          <div className="tl-trust"><span><Check size={16}/> شرح خطوة بخطوة</span><span><Check size={16}/> تدريب على كل فكرة</span><span><Check size={16}/> تعلّم على راحتك</span></div>
        </motion.div>
        <motion.div {...reveal} className="tl-portrait-wrap"><div className="tl-orbit"/><span className="tl-formula f1" aria-hidden="true">𝑥² + 𝑦²</span><span className="tl-formula f2" aria-hidden="true">π</span><span className="tl-formula f3" aria-hidden="true">√𝑥</span>
          <div className="tl-portrait"><img src={p.photoUrl} alt={`مستر ${p.name}، ${p.tagline}`} fetchPriority="high"/></div>
          <motion.div className="tl-float-note" animate={reduced ? {} : { y: [0, -9, 0] }} transition={{ duration: 5, repeat: Infinity }}><span className="tl-note-icon"><Target size={24}/></span><div><strong>نفهمها.. نحلّها!</strong><small>كل فكرة بتقرّبك لهدفك</small></div><Sparkles size={18}/></motion.div>
          <div className="tl-portrait-label"><span>معاك خطوة بخطوة</span><strong>مستر {p.name}</strong></div>
        </motion.div>
      </div><div className="tl-container tl-method-strip"><span><BookOpen/> شرح يبسّط الصعب</span><i/><span><PenTool/> تطبيق يثبّت الفكرة</span><i/><span><Target/> مراجعة تربط المنهج</span><a href="#courses">ابدأ رحلتك <ArrowUpLeft size={17}/></a></div></section>

      {/* Continuously scrolling notation strip — duplicated once so the loop has no visible seam. */}
      <div className="tl-marquee" aria-hidden="true"><div>
        {[...marqueeSymbols, ...marqueeSymbols].map((s, i) => <span key={i}>{s}</span>)}
      </div></div>

      <section id="courses" className="tl-section tl-container"><motion.div {...reveal} className="tl-section-head"><div><span className="tl-kicker">كل مرحلة.. وليها خطتها</span><h2>اختار خطوتك الجاية<span>.</span></h2></div><p>شرح منظّم، وأفكار مترابطة، وتدريب يخليك<br/> تدخل على كل مسألة بثقة.</p></motion.div>
        {cards.length === 0
          ? <div className="tl-empty">الكورسات الجديدة في الطريق. تواصل مع المستر لمعرفة تفاصيل الاشتراك.</div>
          : <Slider items={cards} subject={p.subject} cta={cta} login={login} reveal={reveal} onPreview={(c, i) => setLesson(c.video || lessons[i % 3])}
              enroll={(c) => Number(c.finalPrice ?? c.price) > 0 ? `/checkout/${c.id}` : `/register?academy=${encodeURIComponent(p.slug)}&course=${c.id}`}
              onEnrollFree={asStudent ? (c) => enter(c.id) : undefined} />}
        {demo && <p className="tl-demo-note">الكروت المعلّمة «نموذج تجريبي» أمثلة للعرض وأسعارها توضيحية. الاشتراك وإتاحة الكورسات الفعلية عن طريق المستر.</p>}
      </section>

      <section id="about" className="tl-about-section"><div className="tl-container tl-about-grid"><motion.div {...reveal} className="tl-about-visual"><div className="tl-about-photo"><img src={data.coverUrl || p.photoUrl} alt={`مستر ${p.name}`} loading="lazy"/></div><div className="tl-about-note"><PenTool size={22}/><span>الفهم الأول.<br/><strong>الدرجات بتيجي بعده.</strong></span></div></motion.div><motion.div {...reveal}><span className="tl-kicker">اعرف مسترك</span><h2>أهلاً، أنا<br/><span>مستر {p.name}</span></h2><p className="tl-description">{p.aboutText}</p><div className="tl-about-features">{['نبسّط الفكرة قبل ما نحفظ القانون', 'نحل مع بعض، وبعدها تجرّب بنفسك', 'نربط الدروس ببعض في كل مراجعة'].map(t => <p key={t}><span><Check size={16}/></span>{t}</p>)}</div><a className="tl-text-link" href="#courses">خلّينا نبدأ رحلتك سوا <ArrowLeft size={18}/></a></motion.div></div></section>

      {demo && <section id="lessons" className="tl-section tl-container"><motion.div {...reveal} className="tl-section-head"><div><span className="tl-kicker">فكرة بسيطة تعمل فرق</span><h2>جرّب تفهمها معانا<span>.</span></h2></div><p>أمثلة تعليمية قصيرة للمعاينة.<br/>افتح أي كارت وجرّب تحل بنفسك.</p></motion.div><div className="tl-lessons">{lessons.map((l, i) => <motion.button {...reveal} key={l.title} onClick={() => setLesson(l)} className="tl-lesson"><div className={`tl-lesson-preview lesson-${i}`}><span dir="ltr">{l.formula}</span><i><ArrowUpLeft size={22}/></i></div><div className="tl-lesson-info"><small>{l.tag} · قراءة قصيرة</small><h3>{l.title}</h3><span>افتح المثال <ArrowLeft size={16}/></span></div></motion.button>)}</div></section>}

      <section className="tl-container tl-steps-section"><div><span className="tl-kicker">رحلتك هنا بسيطة</span><h2>من أول دخول..<br/>لأول «أنا فهمتها!»</h2></div><div className="tl-steps">{[['01', 'استلم حسابك', 'المستر أو الإدارة بيسلّموك اسم المستخدم وكلمة المرور.'], ['02', 'ادخل على كورساتك', 'هتلاقي الكورسات اللي اتحددت لك، وكل محتواها في مكان واحد.'], ['03', 'افهم وطبّق وراجع', 'تابع دروسك وتدريباتك، وارجع للفكرة وقت ما تحتاج.']].map(([n, title, text]) => <div key={n}><b>{n}</b><h3>{title}</h3><p>{text}</p></div>)}</div></section>
      <section id="faq" className="tl-section tl-container tl-faq"><div><span className="tl-kicker">قبل ما تبدأ</span><h2>عندك سؤال؟<br/>خلّينا نوضّحه.</h2><p>كل حاجة تحتاج تعرفها عن دخولك للمنصة.</p></div><div>{[['إزاي أعمل حساب وأشترك؟', 'تواصل مع المستر أو الإدارة. هيتم إنشاء اسم مستخدم وكلمة مرور خاصة بيك، وتحديد الكورسات المتاحة لحسابك.'], ['هل هلاقي كورسات مدرسين تانيين؟', `دي مساحة خاصة بمستر ${p.name}. كل المحتوى هنا يخص المستر، وحسابك بيعرض الكورسات المسموح لك بيها.`], ['مش لاقي كورس اتفقت عليه، أعمل إيه؟', 'تواصل مع المستر أو الإدارة لمراجعة إتاحة الكورس لحسابك، وبعد التعديل حدّث صفحة الكورسات.'], ['نسيت كلمة المرور؟', 'اطلب من المستر أو الإدارة تعيين كلمة مرور جديدة، وبعد الدخول تقدر تغيّرها من ملفك الشخصي.']].map(([q, a]) => <details key={q}><summary>{q}<ChevronDown size={18}/></summary><p>{a}</p></details>)}</div></section>
      <section className="tl-container tl-final-cta"><div><span>مسألتك الجاية.. إنت قدّها.</span><h2>جاهز تبدأ وتفهمها صح؟</h2><p>خطوة بسيطة دلوقتي، تفرق في رحلتك كلها.</p></div><a href={cta} className="tl-button light">{p.phone ? 'تواصل مع المستر' : 'ادخل على حسابك'} <ArrowUpLeft size={20}/></a><span className="tl-cta-math" aria-hidden="true">∑</span></section>
    </main>
    <footer className="tl-container tl-footer"><Link to={slug ? `/t/${slug}` : '/'} className="tl-brand"><img src="/images/logo.png" alt="" className="tl-logo" /><span><strong>{p.name}</strong><small>{p.tagline}</small></span></Link><p>مساحتك للفهم، والتطبيق، والثقة.</p><span>© {new Date().getFullYear()} · مستر {p.name}</span></footer>
    {joinError && <div role="alert" className="fixed inset-x-4 bottom-4 z-50 mx-auto max-w-md rounded-2xl bg-rose-600 px-5 py-3 text-center text-sm font-bold text-white shadow-lg" onClick={() => setJoinError('')}>{joinError}</div>}
    {lesson && <div className="tl-modal" onClick={() => setLesson(null)}><div ref={dialog} className="tl-modal-card" role="dialog" aria-modal="true" aria-labelledby="lesson-title" onClick={e => e.stopPropagation()}><button autoFocus aria-label="إغلاق المثال" className="tl-modal-close" onClick={() => setLesson(null)}><X/></button><span className="tl-kicker">{lesson.url ? `درس بالفيديو${lesson.category ? ` · ${lesson.category}` : ''}` : `مثال تعليمي للمعاينة · ${lesson.tag}`}</span><h2 id="lesson-title">{lesson.title}</h2>
      {lesson.url
        ? <div className="tl-modal-video">{embedUrl(lesson.url)
            ? <iframe src={embedUrl(lesson.url)} title={lesson.title} allow="accelerometer; encrypted-media; picture-in-picture; fullscreen" allowFullScreen/>
            : <video src={lesson.url} controls playsInline poster={lesson.poster || undefined}/>}</div>
        : <div className="tl-modal-formula" dir="ltr">{lesson.formula}</div>}
      <p>{lesson.text || lesson.description}</p>{lesson.answer && <strong>{lesson.answer}</strong>}<button className="tl-button" onClick={() => setLesson(null)}>{lesson.url ? 'إغلاق' : 'تمام، فهمتها'} <Check size={18}/></button></div></div>}
  </div>
}

/** Default cover art, cycled by position for anything published without its own image. */
const covers = ['/images/course-1.jpg', '/images/course-2.jpg', '/images/course-3.jpg',
  '/images/course-4.jpg', '/images/course-5.jpg', '/images/course-6.jpg']

/**
 * Horizontal snap-slider for courses and lesson videos. Arrows page by one card width and
 * disable at each end; the track stays plain overflow-scroll underneath, so touch swipe and
 * keyboard scrolling keep working even if the arrows are hidden on small screens.
 */
function Slider({ items, subject, cta, login, reveal, onPreview, enroll, onEnrollFree }) {
  const track = useRef(null)
  const [edge, setEdge] = useState({ start: true, end: false })

  const sync = () => {
    const el = track.current
    if (!el) return
    // RTL reports scrollLeft as 0 at the start and negative as you page forward, so compare on
    // absolute distance rather than raw sign — that way this works in both directions.
    const pos = Math.abs(el.scrollLeft)
    const max = el.scrollWidth - el.clientWidth
    setEdge({ start: pos < 8, end: pos >= max - 8 })
  }
  useEffect(() => { sync(); const el = track.current; if (!el) return
    el.addEventListener('scroll', sync, { passive: true })
    window.addEventListener('resize', sync)
    return () => { el.removeEventListener('scroll', sync); window.removeEventListener('resize', sync) }
  }, [items.length])

  const page = (dir) => {
    const el = track.current
    if (!el) return
    const step = el.querySelector('.tl-slide')?.offsetWidth || 300
    el.scrollBy({ left: (el.scrollLeft <= 0 ? -1 : 1) * dir * (step + 22), behavior: 'smooth' })
  }

  return (
    <div className="tl-slider-wrap">
      <div className="tl-slide-nav">
        <button onClick={() => page(1)} disabled={edge.start} aria-label="السابق"><ArrowUpLeft size={17} style={{ transform: 'rotate(45deg)' }} /></button>
        <button onClick={() => page(-1)} disabled={edge.end} aria-label="التالي"><ArrowLeft size={17} /></button>
      </div>
      <div className="tl-slider" ref={track}>
        {items.map((c, i) => {
          const sample = String(c.id).startsWith('demo')
          const cover = c.coverUrl || covers[i % covers.length]
          return (
            <motion.article {...reveal} key={c.id} className="tl-slide">
              {/* Separate element for the bobbing motion: framer-motion writes an inline transform
                  on .tl-slide for the reveal, and .tl-course owns the hover lift — a third
                  transform animation on either of those would fight the other two. */}
              <div className="tl-bob">
              <div className="tl-course">
                {/* Tapping the artwork sends visitors to sign in — the actual content lives
                    behind the account, so that's the honest next step rather than a dead image. */}
                <Link to={login} className="tl-cover" aria-label={`سجّل الدخول لمشاهدة ${c.title}`}>
                  <img src={cover} alt={c.title} loading="lazy" />
                  <span className="tl-cover-veil" />
                  <div className="tl-cover-top">
                    {(c.year || c.grade) && <span className="tl-cover-year">{c.year || c.grade}</span>}
                    {c.discountPercent > 0 && <span className="tl-cover-flag">خصم {c.discountPercent}%</span>}
                  </div>
                  <span className="tl-cover-play" aria-hidden="true"><span><Play size={20} /></span></span>
                </Link>
                <div className="tl-course-body">
                  <div className="tl-course-meta">
                    <span>{c.isVideo ? 'درس بالفيديو' : subject}</span>
                    {sample && <span>نموذج تجريبي</span>}
                  </div>
                  <h3>{c.title}</h3>
                  <p>{c.description}</p>
                  <div className="tl-course-footer">
                    {c.isVideo
                      ? <span className="tl-price"><strong>مشاهدة</strong></span>
                      : <div className="tl-price"><strong>{fmtMoney(c.finalPrice)}</strong>{c.discountPercent > 0 && <del>{fmtMoney(c.price)}</del>}</div>}
                    {(sample || c.isVideo)
                      ? <button aria-label={`معاينة ${c.title}`} onClick={() => onPreview(c, i)}>معاينة <ArrowUpLeft size={18} /></button>
                      : onEnrollFree && Number(c.finalPrice ?? c.price) <= 0
                        ? <button type="button" onClick={() => onEnrollFree(c)}>اشترك مجاناً <ArrowUpLeft size={17} /></button>
                      : enroll
                        ? <Link to={enroll(c)}>{Number(c.finalPrice ?? c.price) > 0 ? 'اشترك الآن' : 'اشترك مجاناً'} <ArrowUpLeft size={17} /></Link>
                        : <a href={cta}>التواصل للاشتراك <ArrowUpLeft size={17} /></a>}
                  </div>
                </div>
              </div>
              </div>
            </motion.article>
          )
        })}
      </div>
      <p className="tl-slider-hint">اسحب جانبياً لتصفّح باقي الكورسات والدروس ←</p>
    </div>
  )
}
