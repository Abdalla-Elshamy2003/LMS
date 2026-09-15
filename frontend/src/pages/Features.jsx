import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import {
  BarChart3, QrCode, ShieldCheck, MessageCircle, Wallet, Award, Sparkles, Users2, GraduationCap,
  BookOpen, ClipboardList, Trophy, Bell, Layers, Lock, Smartphone, ArrowLeft, CheckCircle2,
} from 'lucide-react'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import PageHero from '../components/marketing/PageHero'
import TiltCard from '../components/TiltCard'

const FLAGSHIP = [
  {
    icon: Sparkles, tint: 'from-brand-600 to-brand-800',
    title: 'توليد أسئلة بالذكاء الاصطناعي', tag: 'مدعوم بالذكاء الاصطناعي',
    desc: 'اكتب المادة والموضوع ومستوى الصعوبة، والنظام يقترح أسئلة اختيار من متعدد أو صح/خطأ جاهزة للمراجعة — راجع، عدّل، واحفظ في بنك الأسئلة بضغطة واحدة.',
    points: ['توليد حسب المادة والموضوع والصعوبة', 'مراجعة وتعديل قبل الحفظ', 'يُضاف مباشرة لبنك الأسئلة'],
  },
  {
    icon: QrCode, tint: 'from-emerald-600 to-teal-700', tag: 'حضور ذكي',
    title: 'حضور بـ QR ديناميكي',
    desc: 'رمز يتجدد كل دقيقتين يمسحه الطالب بكاميرا هاتفه العادية — بدون تطبيق إضافي — فيسجَّل حضوره فوراً، مع كشف تلقائي للتأخير.',
    points: ['رمز متغيّر يمنع مشاركة الصور بين الطلاب', 'تسجيل يدوي احتياطي من المدرس', 'تقارير حضور تفصيلية لكل طالب'],
  },
  {
    icon: ShieldCheck, tint: 'from-violet-600 to-purple-800', tag: 'امتحانات آمنة',
    title: 'بنك أسئلة وامتحانات محمية',
    desc: 'ترتيب عشوائي للأسئلة والاختيارات لكل طالب، رصد الخروج من وضع ملء الشاشة، ومؤقّت لحظي — مع تصحيح آلي فوري للأسئلة الموضوعية.',
    points: ['ترتيب عشوائي للأسئلة والاختيارات', 'رصد الخروج من الشاشة والتنبيه عنه', 'تصحيح آلي فوري + تصحيح يدوي للمقالي'],
  },
  {
    icon: MessageCircle, tint: 'from-amber-600 to-orange-700', tag: 'تواصل تلقائي',
    title: 'تنبيهات واتساب لأولياء الأمور',
    desc: 'رسائل تلقائية عند الغياب أو انخفاض الدرجات أو صدور واجب جديد — تصل لولي الأمر بدون أي تدخل يدوي من المدرس أو الإدارة.',
    points: ['تنبيه فوري عند الغياب', 'تحديثات الدرجات والواجبات', 'لوحة تحكم مستقلة لولي الأمر لمتابعة أبنائه'],
  },
]

const GRID = [
  { icon: BarChart3, title: 'تحليلات ورصد التعثّر', desc: 'معدل وحضور ونشاط كل طالب محسوبين تلقائياً، مع رصد فوري لأي تراجع في الأداء.' },
  { icon: Wallet, title: 'متابعة المدفوعات والأقساط', desc: 'فواتير، أقساط، وتذكيرات تلقائية بالمواعيد — صورة واضحة لكل التحصيل والمتأخرات.' },
  { icon: Award, title: 'شهادات موثّقة برمز QR', desc: 'شهادة PDF لكل طالب مع رمز تحقق فريد — أي جهة تقدر تتأكد من صحتها فوراً.' },
  { icon: Trophy, title: 'لوحة شرف وتحفيز', desc: 'نقاط ومستويات تحفّز الطلاب على الحضور والتفوق والمشاركة المستمرة.' },
  { icon: ClipboardList, title: 'واجبات برفع ملفات', desc: 'المدرس يرفع ملف الواجب مرة واحدة فيصل لكل طلاب الكورس، ويسلّم كل طالب من حسابه.' },
  { icon: Layers, title: 'متعدد الفروع والمستأجرين', desc: 'مؤسسة واحدة أو شبكة فروع كاملة — كل بيانات مؤسسة معزولة تماماً عن غيرها.' },
  { icon: Lock, title: 'صلاحيات دقيقة لكل دور', desc: 'مدير، مدرس، محاسب، طالب، وولي أمر — كل دور يشوف بالظبط اللي يخصه، مش أكتر.' },
  { icon: Bell, title: 'سجل تدقيق كامل', desc: 'كل تعديل حساس في النظام مسجَّل بتوقيته ومنفّذه — شفافية كاملة للإدارة.' },
  { icon: Smartphone, title: 'يعمل على كل الأجهزة', desc: 'واجهة متجاوبة بالكامل — ويب، موبايل، تابلت — بدون أي تثبيت.' },
]

const ROLES = [
  { icon: GraduationCap, role: 'مدير المؤسسة', points: ['لوحة تحكم شاملة لكل الفروع والأداء المالي', 'إدارة المدرسين والصلاحيات', 'تقارير وتحليلات فورية'] },
  { icon: Users2, role: 'المدرس', points: ['بنك أسئلة + توليد أسئلة بالذكاء الاصطناعي', 'تسجيل حضور بـ QR وتصحيح آلي', 'رفع واجبات تصل تلقائياً لكل الطلاب'] },
  { icon: BookOpen, role: 'الطالب', points: ['اختيار الكورسات وتسجيل الحضور بنفسه', 'متابعة الدرجات والشهادات ولوحة الشرف', 'تسليم الواجبات ودخول الامتحانات مباشرة'] },
  { icon: Bell, role: 'ولي الأمر', points: ['متابعة كل أبنائه من حساب واحد', 'تنبيهات واتساب فورية بالحضور والدرجات', 'عرض تفصيلي للامتحانات والواجبات (بدون تدخل)'] },
]

export default function Features() {
  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <PageHero badge="كل الأدوات اللي هتحتاجها" title="منظومة تعليمية" highlight="متكاملة بكل التفاصيل"
        subtitle="من تسجيل الطالب الأول لحد إصدار الشهادة — منارة بتدير كل خطوة في رحلة التعليم." />

      {/* Flagship features */}
      <section className="mx-auto max-w-7xl space-y-6 px-5 pb-6">
        {FLAGSHIP.map((f, i) => (
          <motion.div key={f.title} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-80px' }}
            className={`grid items-center gap-8 rounded-3xl bg-gradient-to-br ${f.tint} p-8 text-white sm:p-10 lg:grid-cols-2 ${i % 2 ? 'lg:[&>*:first-child]:order-2' : ''}`}>
            <div>
              <span className="chip bg-white/15 text-white backdrop-blur">{f.tag}</span>
              <h3 className="mt-4 text-2xl font-black sm:text-3xl">{f.title}</h3>
              <p className="mt-3 leading-relaxed text-white/85">{f.desc}</p>
              <div className="mt-5 space-y-2.5">
                {f.points.map((p) => (
                  <div key={p} className="flex items-center gap-2.5"><CheckCircle2 size={18} className="shrink-0 text-white/90" /><span className="text-sm text-white/90">{p}</span></div>
                ))}
              </div>
            </div>
            <div className="grid h-40 place-items-center rounded-2xl bg-white/10 backdrop-blur sm:h-56">
              <f.icon size={72} className="text-white/80" />
            </div>
          </motion.div>
        ))}
      </section>

      {/* Feature grid */}
      <section className="mx-auto max-w-7xl px-5 py-16">
        <div className="mb-12 text-center">
          <h2 className="text-3xl font-black sm:text-4xl">وكمان...</h2>
          <p className="mt-2 text-ink-500">مميزات كتير تانية جاهزة من أول يوم</p>
        </div>
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {GRID.map((g, i) => (
            <motion.div key={g.title} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }}
              transition={{ delay: (i % 3) * 0.07 }}>
              <TiltCard max={5} className="card h-full p-6">
                <div className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600"><g.icon size={22} /></div>
                <p className="mt-4 font-extrabold text-ink-800">{g.title}</p>
                <p className="mt-1.5 text-sm leading-relaxed text-ink-500">{g.desc}</p>
              </TiltCard>
            </motion.div>
          ))}
        </div>
      </section>

      {/* Per-role */}
      <section className="bg-white py-20">
        <div className="mx-auto max-w-7xl px-5">
          <div className="mb-12 text-center">
            <h2 className="text-3xl font-black sm:text-4xl">لوحة تحكم مخصصة لكل دور</h2>
            <p className="mt-2 text-ink-500">كل مستخدم يشوف بالظبط اللي يخصه — لا أكتر ولا أقل</p>
          </div>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {ROLES.map((r) => (
              <motion.div key={r.role} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="card p-6">
                <div className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-white"><r.icon size={22} /></div>
                <p className="mt-4 font-extrabold text-ink-800">{r.role}</p>
                <div className="mt-3 space-y-2">
                  {r.points.map((p) => (
                    <p key={p} className="flex items-start gap-2 text-sm text-ink-500"><CheckCircle2 size={15} className="mt-0.5 shrink-0 text-emerald-500" /> {p}</p>
                  ))}
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <section className="relative overflow-hidden py-20">
        <div className="absolute inset-0 -z-10 bg-gradient-to-br from-brand-700 to-brand-950" />
        <div className="absolute inset-0 -z-10 bg-grid opacity-30" />
        <div className="mx-auto max-w-2xl px-5 text-center text-white">
          <h2 className="text-3xl font-black sm:text-4xl">جرّب كل المميزات دي بنفسك</h2>
          <p className="mt-3 text-brand-100">14 يوم مجاناً — بدون بطاقة ائتمان</p>
          <Link to="/register" className="btn mt-6 inline-flex bg-white px-7 py-3.5 text-base text-brand-700 hover:bg-brand-50">
            ابدأ الآن مجاناً <ArrowLeft size={18} />
          </Link>
        </div>
      </section>

      <MarketingFooter />
    </div>
  )
}
