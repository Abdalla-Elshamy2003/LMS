import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion, useScroll, useTransform } from 'framer-motion'
import {
  GraduationCap, Search, ArrowLeft, Sparkles, BarChart3, Bell, ShieldCheck, QrCode, Trophy, Wallet,
  BookOpen, Users, Star, CheckCircle2, XCircle, FileText, MessageCircle, Award,
  PlayCircle, TrendingUp, Clock, UserPlus, Palette, Rocket,
} from 'lucide-react'
import api from '../lib/api'
import { CountUp } from '../components/ui'
import { initials } from '../lib/format'
import Hero3D from '../components/Hero3D'
import TiltCard from '../components/TiltCard'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import FAQAccordion from '../components/marketing/FAQAccordion'
import LearningJourney from '../components/marketing/LearningJourney'

const HOME_FAQS = [
  { q: 'منارة تناسب أي نوع مؤسسات؟', a: 'أي مؤسسة تعليمية — مدرس مستقل، مركز تدريب، مدرسة، أو حتى جامعة. النظام يدعم فروع ومستأجرين متعددين، فيصلح لمؤسسة واحدة أو شبكة فروع كاملة.' },
  { q: 'هل بياناتي وبيانات طلابي آمنة؟', a: 'كل مؤسسة معزولة تماماً عن غيرها (multi-tenant)، مع صلاحيات دقيقة لكل دور، وسجل تدقيق كامل لكل تعديل حساس في النظام.' },
  { q: 'إزاي بتتابعوا الحضور؟', a: 'رمز QR ديناميكي يتجدد كل دقيقتين لمنع مشاركة الصور بين الطلاب، بالإضافة لتسجيل يدوي من المدرس، وتقارير حضور تفصيلية لكل طالب.' },
  { q: 'هل فيه تنبيهات لأولياء الأمور؟', a: 'أيوه — تنبيهات واتساب تلقائية عند الغياب أو انخفاض الدرجات، ولوحة تحكم مخصصة لولي الأمر يتابع منها كل أبنائه.' },
  { q: 'هل يمكن تجربة النظام قبل الاشتراك؟', a: 'تقدر تسجّل حساب مجاني الآن وتستكشف المنصة بنفسك — لا حاجة لبطاقة ائتمان.' },
  { q: 'هل تدعم المنصة الامتحانات الإلكترونية؟', a: 'أيوه، بنك أسئلة كامل مع توليد أسئلة بالذكاء الاصطناعي، ترتيب عشوائي للأسئلة، رصد الخروج من الشاشة، وتصحيح آلي فوري.' },
]

const MARQUEE_WORDS = [
  'امتحانات ذكية', 'حضور بـ QR', 'تنبيهات فورية لأولياء الأمور', 'تقارير PDF', 'شهادات موثّقة',
  'لوحة شرف وتحفيز', 'متابعة الحضور والدرجات', 'مدفوعات وأقساط', 'محتوى فيديو وملفات',
]

const BENTO = [
  {
    icon: BarChart3, big: true,
    title: 'تحليلات ذكية تكتشف التعثّر مبكراً',
    desc: 'كل طالب له معدل وحضور ونشاط محسوبين تلقائياً، ونظام يرصد التراجع لحظة حدوثه ويُنبّه المدرس وولي الأمر قبل فوات الأوان.',
  },
  { icon: QrCode, title: 'حضور بـ QR متغيّر', desc: 'رمز يتجدد كل دقيقتين يمنع مشاركة الصور بين الطلاب.' },
  { icon: ShieldCheck, title: 'امتحانات آمنة', desc: 'ترتيب عشوائي للأسئلة ورصد الخروج من الشاشة.' },
  { icon: MessageCircle, title: 'تنبيهات واتساب', desc: 'رسائل تلقائية لولي الأمر عند الغياب أو انخفاض الدرجات.' },
  { icon: Wallet, title: 'مدفوعات وأقساط', desc: 'متابعة التحصيل وتذكيرات تلقائية بالمواعيد.' },
  { icon: Award, title: 'شهادات موثّقة', desc: 'شهادة PDF لكل طالب برمز QR للتحقق من صحتها.' },
]

export default function Landing() {
  const [data, setData] = useState(null)
  const [q, setQ] = useState('')
  const [subjectFilter, setSubjectFilter] = useState('')

  useEffect(() => { api.get('/public/landing').then((r) => setData(r.data)).catch(() => {}) }, [])

  const subjects = useMemo(
    () => [...new Set((data?.teachers || []).map((t) => (t.subjects || '').split('·')[0]?.trim()).filter(Boolean))],
    [data],
  )
  const teachers = (data?.teachers || []).filter((t) =>
    (!q || t.name.includes(q) || (t.subjects || '').includes(q)) && (!subjectFilter || (t.subjects || '').includes(subjectFilter)))

  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav />

      <Hero data={data} />
      <Marquee />
      <LearningJourney />

      <TeacherSection teachers={teachers} subjects={subjects} q={q} setQ={setQ} subjectFilter={subjectFilter} setSubjectFilter={setSubjectFilter} />
      <CoursesSection courses={data?.courses || []} />
      <FeaturesSection />
      <StepsSection />
      <StatsBand data={data} />
      <AboutSection />
      <FaqSection />
      <RegisterSection courses={data?.courses || []} />
      <MarketingFooter />
    </div>
  )
}

/* ================= Comparison: without Manarah / with Manarah ================= */

function ComparisonSection() {
  const problems = [
    { title: 'بتضيع وقتك في متابعة كل طالب يدوياً؟', desc: 'ساعات كل يوم بتروح في المتابعة والتقارير ورسائل أولياء الأمور واحداً واحداً.' },
    { title: 'بيانات الطلاب متفرقة بين ورق وإكسل وواتساب؟', desc: 'كل حاجة في مكان مختلف، وصعب توصل لصورة كاملة عن أداء أي طالب بسرعة.' },
    { title: 'مفيش رؤية واضحة لمين محتاج متابعة؟', desc: 'التعثر بيتكشف متأخر، وولي الأمر بيعرف بعد فوات الأوان.' },
  ]
  const solutions = [
    { title: 'متابعة تلقائية لكل طالب', desc: 'تنبيهات واتساب تلقائية لولي الأمر: حضور، درجات، وتنبيهات ذكية — من غير ما تلمس موبايلك.' },
    { title: 'كل بيانات أكاديميتك في مكان واحد', desc: 'الطلاب، الحضور، الدرجات، المدفوعات، والشهادات — منظومة واحدة متكاملة.' },
    { title: 'رصد التعثر لحظة حدوثه', desc: 'تحليلات تلقائية تكتشف انخفاض الأداء أو الغياب المتكرر وتنبّه المدرس فوراً.' },
  ]
  return (
    <Section title="ليه منارة؟" subtitle="المشاكل اللي بتواجه كل أكاديمية... والحل مع منارة" tint>
      <div className="grid gap-6 lg:grid-cols-2">
        <motion.div initial={{ opacity: 0, x: 20 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} className="card p-7">
          <span className="chip bg-rose-50 text-rose-600">بدون منارة</span>
          <div className="mt-5 space-y-5">
            {problems.map((p) => (
              <div key={p.title} className="flex gap-3">
                <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full bg-rose-50 text-rose-500"><XCircle size={16} /></span>
                <div><p className="font-bold text-ink-800">{p.title}</p><p className="mt-1 text-sm text-ink-500">{p.desc}</p></div>
              </div>
            ))}
          </div>
        </motion.div>
        <motion.div initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} className="card border-2 border-brand-200 bg-gradient-to-br from-brand-50/60 to-white p-7">
          <span className="chip bg-brand-600 text-white">مع منارة</span>
          <div className="mt-5 space-y-5">
            {solutions.map((s) => (
              <div key={s.title} className="flex gap-3">
                <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-600"><CheckCircle2 size={16} /></span>
                <div><p className="font-bold text-ink-800">{s.title}</p><p className="mt-1 text-sm text-ink-500">{s.desc}</p></div>
              </div>
            ))}
          </div>
        </motion.div>
      </div>
    </Section>
  )
}

/* ================= 3 steps to start ================= */

function StepsSection() {
  const steps = [
    { n: '01', icon: UserPlus, time: '5 دقائق', title: 'سجّل حسابك', desc: 'أنشئ حسابك وحساب مؤسستك في أقل من 5 دقائق — بدون تعقيد.' },
    { n: '02', icon: Palette, time: '30 دقيقة', title: 'خصّص أكاديميتك', desc: 'أضف كورساتك ومدرسيك وطلابك، واضبط الإعدادات اللي تناسبك.' },
    { n: '03', icon: Rocket, time: 'فوري', title: 'ابدأ التعليم', desc: 'طلابك يقدروا يلتحقوا ويبدأوا فوراً — وانت تتابع كل حاجة من لوحة تحكم واحدة.' },
  ]
  return (
    <Section title="إزاي تبدأ في 3 خطوات" subtitle="من التسجيل للانطلاق في دقائق — مش أسابيع">
      <div className="grid gap-6 md:grid-cols-3">
        {steps.map((s, i) => (
          <motion.div key={s.n} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
            transition={{ delay: i * 0.1 }} className="card relative overflow-hidden p-7">
            <span className="text-6xl font-black text-brand-50">{s.n}</span>
            <div className="-mt-8 grid h-14 w-14 place-items-center rounded-2xl bg-brand-50 text-brand-600"><s.icon size={24} /></div>
            <p className="mt-4 font-extrabold text-ink-800">{s.title}</p>
            <p className="mt-1.5 text-sm leading-relaxed text-ink-500">{s.desc}</p>
            <span className="mt-4 inline-block chip bg-ink-100 text-ink-500"><Clock size={12} /> {s.time}</span>
          </motion.div>
        ))}
      </div>
    </Section>
  )
}

/* ================= FAQ ================= */

function FaqSection() {
  return (
    <Section id="faq" title="أسئلة شائعة" subtitle="إجابات على أكتر الأسئلة اللي بتتسأل" tint>
      <FAQAccordion items={HOME_FAQS} />
    </Section>
  )
}

/* ================= Hero ================= */

function Hero({ data }) {
  const ref = useRef(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start start', 'end start'] })
  const fade = useTransform(scrollYProgress, [0, 1], [1, 0.3])
  const shiftUp = useTransform(scrollYProgress, [0, 1], [0, -60])

  return (
    <section id="top" ref={ref} className="landing-hero relative overflow-hidden pb-20 pt-32 sm:pt-40">
      <div className="absolute inset-0 -z-20 mesh-bg" />
      <div className="absolute -top-32 -right-20 -z-10 h-[420px] w-[420px] rounded-full bg-brand-300/30 blur-3xl animate-blob" />
      <div className="absolute top-40 -left-20 -z-10 h-[380px] w-[380px] rounded-full bg-sky-400/25 blur-3xl animate-blob" style={{ animationDelay: '4s' }} />
      <div className="absolute bottom-0 right-1/3 -z-10 h-72 w-72 rounded-full bg-cyan-300/25 blur-3xl animate-blob" style={{ animationDelay: '8s' }} />
      <div className="absolute inset-0 -z-10 bg-dots opacity-40 [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_40%,transparent_100%)]" />

      <motion.div style={{ opacity: fade, y: shiftUp }} className="mx-auto grid max-w-7xl items-center gap-10 px-5 lg:grid-cols-2 lg:gap-6">
        <motion.div initial={{ opacity: 0, y: 28 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}>
          <motion.span
            initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.15 }}
            className="chip border border-brand-200 bg-white/80 text-brand-700 shadow-soft backdrop-blur">
            <Sparkles size={14} className="animate-pulse" /> عالم من المعرفة. في مكان واحد.
          </motion.span>
          <h1 className="mt-6 text-4xl font-black leading-[1.4] sm:text-5xl lg:text-[3.8rem]">
            افتح كتاباً.<br />
            <span className="gradient-text bg-[length:200%_auto] animate-gradient-x">وافتح لمستقبلك باباً.</span>
          </h1>
          <p className="mt-5 max-w-lg text-lg leading-relaxed text-ink-500">
            من أول شرح لحد لحظة نجاحك. فيديوهات ودروس وملفات تجمعك بمدرسك،
            وتجربة متكاملة تخلي الطالب يتعلّم، والمدرس يُلهم، والإدارة تتابع كل خطوة.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <motion.div whileHover={{ scale: 1.04 }} whileTap={{ scale: 0.97 }} className="inline-block">
              <Link to="/register" className="btn-primary px-7 py-3.5 text-base">
                ابدأ رحلتك <ArrowLeft size={18} />
              </Link>
            </motion.div>
            <motion.a whileHover={{ scale: 1.04 }} whileTap={{ scale: 0.97 }} href="#features" className="btn-ghost gap-2 px-6 py-3.5 text-base">
              <PlayCircle size={18} /> اكتشف المنصة
            </motion.a>
          </div>
          {data && (
            <div className="mt-11 flex flex-wrap gap-8 border-t border-ink-200/70 pt-6">
              <Stat n={data.stats.students} label="طالب" />
              <Stat n={data.stats.teachers} label="مدرس" />
              <Stat n={data.stats.courses} label="كورس" />
            </div>
          )}
        </motion.div>

        <HeroVisual />
      </motion.div>
    </section>
  )
}

function Stat({ n, suffix = '', label }) {
  return (
    <div>
      <p className="text-3xl font-black text-ink-800">
        {!suffix && '+'}<CountUp value={n} suffix={suffix} />
      </p>
      <p className="text-sm text-ink-400">{label}</p>
    </div>
  )
}

/** Real WebGL 3D canvas layered with floating 2D glass UI cards for depth. */
function HeroVisual() {
  return (
    <motion.div initial={{ opacity: 0, scale: 0.92 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.9, delay: 0.2 }}
      className="relative mx-auto h-[380px] w-full max-w-lg sm:h-[460px] lg:h-[520px]">
      <div className="hero-orbit absolute inset-8 rounded-full border border-teal-200/60" />
      <div className="absolute inset-20 rounded-full bg-cyan-100/50 blur-3xl" />
      <Hero3D className="absolute inset-0 h-full w-full" />

      <motion.div animate={{ y: [0, -12, 0] }} transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
        className="glass absolute right-0 top-6 w-52 rounded-3xl p-4 shadow-card sm:right-4 sm:top-10">
        <div className="flex items-center gap-2">
          <div className="grid h-9 w-9 place-items-center rounded-xl bg-brand-100 text-brand-600"><BarChart3 size={16} /></div>
          <div><p className="text-xs font-bold text-ink-800">رحلتك خطوة بخطوة</p><p className="text-[10px] text-ink-400">تعلّم · طبّق · تقدّم</p></div>
        </div>
        <div className="mt-3 flex gap-1.5">{[1, 2, 3, 4, 5].map(n => <span key={n} className="h-1.5 flex-1 rounded-full bg-teal-400/70" />)}</div>
      </motion.div>

      <motion.div animate={{ y: [0, 14, 0] }} transition={{ duration: 5.5, repeat: Infinity, ease: 'easeInOut', delay: 0.5 }}
        className="glass absolute bottom-8 left-0 w-56 rounded-3xl p-4 shadow-card sm:bottom-14 sm:left-2">
        <div className="flex items-center gap-2">
          <div className="grid h-9 w-9 place-items-center rounded-xl bg-emerald-100 text-emerald-600"><Bell size={16} /></div>
          <div><p className="text-xs font-bold text-ink-800">كل شرح، معاه تطبيق</p><p className="text-[10px] text-ink-400">فيديوهات + ملفات الدروس</p></div>
        </div>
      </motion.div>

      <motion.div animate={{ y: [0, -9, 0] }} transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut', delay: 1 }}
        className="glass absolute left-2 top-0 w-40 rounded-2xl p-3.5 shadow-card sm:top-2">
        <div className="flex items-center gap-2">
          <div className="grid h-8 w-8 place-items-center rounded-lg bg-amber-100 text-amber-500"><Trophy size={14} /></div>
          <div><p className="text-[11px] font-bold text-ink-800">اتعلّم بطريقتك</p><p className="text-[10px] text-ink-400">وفي الوقت المناسب ليك</p></div>
        </div>
      </motion.div>
    </motion.div>
  )
}

/* ================= Marquee ================= */

function Marquee() {
  const items = [...MARQUEE_WORDS, ...MARQUEE_WORDS]
  return (
    <div className="relative border-y border-ink-100 bg-white py-4">
      <div className="flex w-max animate-marquee gap-3 whitespace-nowrap">
        {items.map((w, i) => (
          <span key={i} className="chip mx-1.5 bg-brand-50 text-brand-700"><Sparkles size={12} /> {w}</span>
        ))}
      </div>
    </div>
  )
}

/* ================= Teachers ================= */

function TeacherSection({ teachers, subjects, q, setQ, subjectFilter, setSubjectFilter }) {
  return (
    <Section id="teachers" title="نخبة المدرسين" subtitle="أفضل الكفاءات التعليمية بين يديك">
      <div className="mx-auto mb-8 flex max-w-2xl flex-col items-center gap-3">
        <div className="relative w-full max-w-md">
          <Search size={18} className="absolute right-3.5 top-3 text-ink-400" />
          <input value={q} onChange={(e) => setQ(e.target.value)} className="input pr-11 shadow-soft" placeholder="ابحث عن مدرس أو مادة..." />
        </div>
        {subjects.length > 0 && (
          <div className="flex flex-wrap justify-center gap-2">
            <button onClick={() => setSubjectFilter('')} className={`chip border transition ${!subjectFilter ? 'border-brand-500 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>الكل</button>
            {subjects.map((s) => (
              <button key={s} onClick={() => setSubjectFilter(s)} className={`chip border transition ${subjectFilter === s ? 'border-brand-500 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>{s}</button>
            ))}
          </div>
        )}
      </div>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {teachers.map((t, i) => (
          <motion.div key={t.id} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }}
            transition={{ delay: (i % 4) * 0.09, duration: 0.5 }}>
            <Link to={`/teachers/${t.id}`} className="block">
              <TiltCard max={8} className="group card overflow-hidden text-center">
                <div className="relative h-24 bg-gradient-to-br from-brand-500 to-brand-700">
                  <div className="absolute inset-0 bg-grid opacity-30" />
                </div>
                <div className="-mt-12 px-5 pb-6" style={{ transform: 'translateZ(40px)' }}>
                  {t.photoUrl
                    ? <img src={t.photoUrl} alt={t.name} className="mx-auto h-24 w-24 rounded-3xl object-cover ring-4 ring-white shadow-soft" />
                    : <div className="mx-auto grid h-24 w-24 place-items-center rounded-3xl bg-brand-600 text-2xl font-bold text-white ring-4 ring-white">{initials(t.name)}</div>}
                  <p className="mt-3 font-extrabold text-ink-800">{t.name}</p>
                  <p className="text-sm font-semibold text-brand-600">{t.title}</p>
                  <p className="mt-1 text-xs text-ink-400">{t.subjects}</p>
                  <p className="mt-3 text-sm text-ink-500 line-clamp-2">{t.bio}</p>
                  <div className="mt-3 flex items-center justify-center gap-1 text-amber-400">
                    {[...Array(5)].map((_, s) => <Star key={s} size={14} fill="currentColor" />)}
                  </div>
                  <p className="mt-3 text-xs font-bold text-brand-600 opacity-0 transition-opacity group-hover:opacity-100">عرض الملف الكامل ←</p>
                </div>
              </TiltCard>
            </Link>
          </motion.div>
        ))}
        {teachers.length === 0 && <p className="col-span-full py-10 text-center text-ink-400">لا يوجد مدرسون مطابقون</p>}
      </div>
    </Section>
  )
}

/* ================= Courses ================= */

function CoursesSection({ courses }) {
  const gradients = ['from-brand-500 to-brand-700', 'from-emerald-500 to-teal-700', 'from-violet-500 to-purple-700', 'from-amber-500 to-orange-700', 'from-sky-500 to-blue-700', 'from-rose-500 to-pink-700']
  return (
    <Section id="courses" title="الكورسات المتاحة" subtitle="مناهج متكاملة بشرح وامتحانات وواجبات" tint>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {courses.map((c, i) => (
          <motion.div key={c.id} initial={{ opacity: 0, scale: 0.94 }} whileInView={{ opacity: 1, scale: 1 }} viewport={{ once: true, margin: '-60px' }}
            transition={{ delay: (i % 3) * 0.08 }}>
            <TiltCard max={6} className="group card flex overflow-hidden">
              <div className={`relative w-24 shrink-0 bg-gradient-to-br ${gradients[i % gradients.length]}`}>
                <div className="absolute inset-y-0 right-0 w-1.5 bg-white/25" />
                <BookOpen className="absolute bottom-3 right-3 text-white/80" size={22} />
              </div>
              <div className="flex-1 p-5">
                <p className="font-extrabold text-ink-800 leading-snug">{c.title}</p>
                <p className="mt-1 text-xs text-ink-400">{c.teacherName} · {c.gradeLevel}</p>
                <div className="mt-3 flex items-center justify-between">
                  <span className="chip bg-brand-50 text-brand-700">{c.subject}</span>
                  <span className="font-bold text-brand-600">{Number(c.price).toLocaleString('ar-EG')} ج.م</span>
                </div>
              </div>
            </TiltCard>
          </motion.div>
        ))}
        {courses.length === 0 && <p className="col-span-full py-10 text-center text-ink-400">لا توجد كورسات متاحة حالياً</p>}
      </div>
    </Section>
  )
}

/* ================= Features (bento grid) ================= */

function FeaturesSection() {
  return (
    <Section id="features" title="لماذا منارة؟" subtitle="مميزات ذكية تجعل إدارة التعليم أسهل وأمتع">
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 lg:grid-rows-2">
        {BENTO.map((f, i) => (
          <motion.div key={f.title} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }}
            transition={{ delay: (i % 3) * 0.08 }}
            className={f.big ? 'sm:col-span-2 lg:row-span-2' : ''}>
            <TiltCard max={5} className={`card group relative h-full overflow-hidden p-7 ${f.big ? 'bg-gradient-to-br from-brand-600 to-brand-800 text-white' : ''}`}>
              <div className={`grid h-12 w-12 place-items-center rounded-2xl ${f.big ? 'bg-white/15' : 'bg-brand-50 text-brand-600'}`}>
                <f.icon size={22} />
              </div>
              <p className={`mt-4 font-extrabold ${f.big ? 'text-2xl' : 'text-lg text-ink-800'}`}>{f.title}</p>
              <p className={`mt-1.5 text-sm leading-relaxed ${f.big ? 'text-brand-100' : 'text-ink-500'}`}>{f.desc}</p>
              {f.big && (
                <div className="mt-6 grid grid-cols-3 gap-3">
                  {[['حضور', '86٪'], ['متوسط', '75٪'], ['متعثرون', '↓ 40٪']].map(([l, v]) => (
                    <div key={l} className="rounded-2xl bg-white/10 p-3 text-center backdrop-blur">
                      <p className="text-lg font-black">{v}</p><p className="text-[11px] text-brand-100/80">{l}</p>
                    </div>
                  ))}
                </div>
              )}
              <div className={`pointer-events-none absolute -bottom-10 -left-10 h-32 w-32 rounded-full blur-2xl transition-opacity duration-500 group-hover:opacity-100 ${f.big ? 'bg-white/10 opacity-40' : 'bg-brand-200/40 opacity-0'}`} />
            </TiltCard>
          </motion.div>
        ))}
      </div>
    </Section>
  )
}

/* ================= Stats band ================= */

function StatsBand({ data }) {
  const items = [
    { icon: Users, n: data?.stats?.students || 0, label: 'طالب مسجّل' },
    { icon: GraduationCap, n: data?.stats?.teachers || 0, label: 'مدرس معتمد' },
    { icon: BookOpen, n: data?.stats?.courses || 0, label: 'كورس متاح' },
    { icon: Users, n: 3, label: 'مساحات للطالب والمدرس والإدارة' },
  ]
  return (
    <section className="relative overflow-hidden bg-gradient-to-l from-brand-700 via-brand-800 to-brand-900 py-16">
      <div className="absolute inset-0 bg-grid opacity-20" />
      <div className="mx-auto grid max-w-6xl grid-cols-2 gap-8 px-5 text-center text-white lg:grid-cols-4">
        {items.map((it, i) => (
          <motion.div key={it.label} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.1 }}>
            <div className="mx-auto mb-2 grid h-12 w-12 place-items-center rounded-2xl bg-white/10"><it.icon size={22} /></div>
            <p className="text-4xl font-black">{!it.suffix && '+'}<CountUp value={it.n} suffix={it.suffix || ''} /></p>
            <p className="mt-1 text-sm text-brand-100">{it.label}</p>
          </motion.div>
        ))}
      </div>
    </section>
  )
}

/* ================= About ================= */

function AboutSection() {
  return (
    <section id="about" className="mx-auto max-w-7xl px-5 py-20">
      <div className="grid items-center gap-10 lg:grid-cols-2">
        <motion.div initial={{ opacity: 0, x: 30 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }}>
          <span className="chip border border-brand-200 bg-brand-50 text-brand-700">من نحن</span>
          <h3 className="mt-4 text-3xl font-black text-ink-800 sm:text-4xl">شريكك التقني في رحلة التعليم</h3>
          <p className="mt-4 leading-relaxed text-ink-500">
            منارة منصة تعليمية متكاملة صُمّمت خصيصاً للمراكز والأكاديميات والمدارس في مصر والوطن العربي.
            نساعدك على إدارة كل تفاصيل العملية التعليمية بذكاء وسلاسة، لتتفرّغ لما يهم فعلاً: نجاح طلابك.
          </p>
          <div className="mt-6 space-y-3">
            {['واجهة عربية أنيقة وسهلة الاستخدام', 'دعم متعدد الفروع والمستأجرين', 'تقارير فورية قابلة للتصدير PDF', 'أمان وخصوصية كاملة للبيانات'].map((x) => (
              <motion.div key={x} initial={{ opacity: 0, x: 12 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} className="flex items-center gap-2.5">
                <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-600"><CheckCircle2 size={14} /></span>
                <span className="text-ink-700">{x}</span>
              </motion.div>
            ))}
          </div>
          <Link to="/about" className="mt-6 inline-flex items-center gap-1.5 font-bold text-brand-600 hover:underline">
            اقرأ قصتنا كاملة <ArrowLeft size={16} />
          </Link>
        </motion.div>

        <motion.div initial={{ opacity: 0, x: -30 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} className="relative h-80 sm:h-96">
          <Hero3D className="absolute inset-0 h-full w-full" />
          <div className="pointer-events-none absolute inset-0 rounded-3xl bg-gradient-to-t from-[#f6fbff] via-transparent to-transparent" />
        </motion.div>
      </div>
    </section>
  )
}

/* ================= Register ================= */

function RegisterSection({ courses }) {
  return (
    <section id="register" className="relative overflow-hidden py-20">
      <div className="absolute inset-0 -z-10 bg-gradient-to-br from-brand-700 to-brand-950" />
      <div className="absolute inset-0 -z-10 bg-grid opacity-30" />
      <div className="absolute -bottom-20 -left-20 -z-10 h-72 w-72 rounded-full bg-brand-400/20 blur-3xl animate-blob" />
      <div className="mx-auto grid max-w-5xl items-center gap-10 px-5 lg:grid-cols-2">
        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-white">
          <h2 className="text-3xl font-black sm:text-4xl">سجّل معنا وابدأ رحلتك</h2>
          <p className="mt-4 leading-relaxed text-brand-100">
            أنشئ حسابك في دقيقتين وادخل مباشرة لمنصتك — اختر كورسك، وابدأ حصتك التجريبية المجانية اليوم.
          </p>
          <div className="mt-6 space-y-2 text-brand-100">
            {['حصة تجريبية مجانية', 'متابعة لحظية لولي الأمر', 'مدرسون معتمدون'].map((x) => (
              <p key={x} className="flex items-center gap-2"><CheckCircle2 size={18} /> {x}</p>
            ))}
          </div>
        </motion.div>
        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="card p-8 text-center">
          <div className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-brand-50 text-brand-600">
            <GraduationCap size={32} />
          </div>
          <h3 className="mt-4 text-xl font-black text-ink-800">{courses.length > 0 ? `أكثر من ${courses.length} كورس بانتظارك` : 'كورسات متنوعة بانتظارك'}</h3>
          <p className="mt-2 text-sm text-ink-500">أنشئ حسابك الآن واختر الكورس المناسب لك من داخل المنصة.</p>
          <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} className="mt-6">
            <Link to="/register" className="btn-primary w-full justify-center py-3">
              أنشئ حسابك الآن <ArrowLeft size={18} />
            </Link>
          </motion.div>
          <p className="mt-4 text-xs text-ink-400">لديك حساب بالفعل؟ <Link to="/login" className="font-bold text-brand-600 hover:underline">سجّل الدخول</Link></p>
        </motion.div>
      </div>
    </section>
  )
}

/* ================= Shared section shell ================= */

function Section({ id, title, subtitle, children, tint }) {
  return (
    <section id={id} className={tint ? 'bg-white py-20' : 'py-20'}>
      <div className="mx-auto max-w-7xl px-5">
        <motion.div initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="mb-12 text-center">
          <h2 className="text-3xl font-black text-ink-800 sm:text-4xl">{title}</h2>
          <p className="mt-2 text-ink-500">{subtitle}</p>
          <div className="mx-auto mt-4 h-1 w-16 rounded-full bg-gradient-to-l from-brand-400 to-brand-600" />
        </motion.div>
        {children}
      </div>
    </section>
  )
}
