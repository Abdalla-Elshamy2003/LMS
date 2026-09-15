import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { User, Building2, School, GraduationCap, ArrowLeft, XCircle, CheckCircle2 } from 'lucide-react'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import PageHero from '../components/marketing/PageHero'

const SCENARIOS = [
  {
    icon: User, tint: 'from-brand-500 to-brand-700', persona: 'المدرس المستقل',
    challenge: 'بيدي دروس خصوصية لعشرات الطلاب، ومتابعة الحضور والدرجات والواجبات بقت بتاخد وقت أكتر من التدريس نفسه.',
    outcome: 'مع منارة، بيسجّل حضوره بـ QR في ثواني، ويولّد أسئلة امتحان بالذكاء الاصطناعي في دقايق، وأولياء الأمور بياخدوا تحديث تلقائي على الواتساب — من غير ما يبعت رسالة واحدة بنفسه.',
  },
  {
    icon: Building2, tint: 'from-emerald-500 to-teal-700', persona: 'مركز التدريب',
    challenge: 'بيدير كذا كورس وكذا مدرّس في نفس الوقت، وصعب يبقى عنده صورة واحدة واضحة لأداء كل الكورسات ومدى تحصيل الدفعات.',
    outcome: 'لوحة تحكم واحدة بتوريه كل الكورسات والمدرّسين والطلاب، مع متابعة مدفوعات وأقساط منظّمة، وتقارير أداء فورية لكل كورس.',
  },
  {
    icon: School, tint: 'from-violet-500 to-purple-700', persona: 'المدرسة',
    challenge: 'محتاجة تدير فروع ومراحل دراسية مختلفة، مع صلاحيات مختلفة للمدير والمدرّس والمحاسب، وتواصل مستمر مع أولياء الأمور.',
    outcome: 'دعم كامل للفروع المتعددة وصلاحيات دقيقة لكل دور، مع تنبيهات تلقائية لأولياء الأمور ولوحة تحكم مستقلة لكل واحد منهم يتابع منها أبناءه.',
  },
  {
    icon: GraduationCap, tint: 'from-amber-500 to-orange-700', persona: 'الجامعة ومركز التأهيل',
    challenge: 'أعداد طلاب كبيرة، ومحتاجة نظام امتحانات آمن يمنع الغش، مع بنك أسئلة ضخم يصعب إدارته يدوياً.',
    outcome: 'بنك أسئلة مركزي مع توليد بالذكاء الاصطناعي لتوفير وقت إعداد الامتحانات، وامتحانات محمية برصد الخروج من الشاشة وترتيب عشوائي للأسئلة.',
  },
]

export default function SuccessStories() {
  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <PageHero badge="لمين منارة؟" title="مصمّمة لكل" highlight="أنواع المؤسسات التعليمية"
        subtitle="من المدرس المستقل للجامعة الكبيرة — شوف إزاي منارة بتحل تحديات كل نوع مؤسسة." />

      <section className="mx-auto max-w-6xl space-y-6 px-5 pb-20">
        {SCENARIOS.map((s, i) => (
          <motion.div key={s.persona} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }}
            transition={{ delay: i * 0.06 }} className="card overflow-hidden">
            <div className={`flex items-center gap-4 bg-gradient-to-br ${s.tint} p-6 text-white`}>
              <div className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-white/15"><s.icon size={26} /></div>
              <p className="text-xl font-black">{s.persona}</p>
            </div>
            <div className="grid gap-6 p-6 sm:grid-cols-2 sm:p-8">
              <div>
                <span className="chip bg-rose-50 text-rose-600"><XCircle size={13} /> التحدي</span>
                <p className="mt-3 leading-relaxed text-ink-600">{s.challenge}</p>
              </div>
              <div>
                <span className="chip bg-emerald-50 text-emerald-700"><CheckCircle2 size={13} /> الحل مع منارة</span>
                <p className="mt-3 leading-relaxed text-ink-600">{s.outcome}</p>
              </div>
            </div>
          </motion.div>
        ))}
      </section>

      <section className="relative overflow-hidden py-20">
        <div className="absolute inset-0 -z-10 bg-gradient-to-br from-brand-700 to-brand-950" />
        <div className="absolute inset-0 -z-10 bg-grid opacity-30" />
        <div className="mx-auto max-w-2xl px-5 text-center text-white">
          <h2 className="text-3xl font-black sm:text-4xl">كن أنت القصة الجاية</h2>
          <p className="mt-3 text-brand-100">ابدأ تجربتك المجانية الآن وشوف الفرق بنفسك</p>
          <Link to="/register" className="btn mt-6 inline-flex bg-white px-7 py-3.5 text-base text-brand-700 hover:bg-brand-50">
            ابدأ الآن مجاناً <ArrowLeft size={18} />
          </Link>
        </div>
      </section>

      <MarketingFooter />
    </div>
  )
}
