import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CheckCircle2, ArrowLeft, Sparkles } from 'lucide-react'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import PageHero from '../components/marketing/PageHero'
import FAQAccordion from '../components/marketing/FAQAccordion'

const TIERS = [
  {
    key: 'starter', name: 'البداية', monthly: 799, yearly: 663, cap: '100 طالب · معلم واحد',
    features: ['إدارة الطلاب وأولياء الأمور', 'حضور بـ QR ديناميكي', 'امتحانات وبنك أسئلة', 'واجبات وتصحيح', 'دعم فني خلال ساعات العمل'],
  },
  {
    key: 'growth', name: 'النمو', monthly: 1999, yearly: 1666, cap: '300 طالب · 3 معلمين',
    features: ['كل مزايا باقة البداية', 'توليد أسئلة بالذكاء الاصطناعي', 'متابعة واتساب تلقائية لأولياء الأمور', 'شهادات موثّقة برمز QR', 'تقارير وتحليلات متقدمة'],
  },
  {
    key: 'pro', name: 'المحترف', monthly: 3999, yearly: 3333, cap: '600 طالب · 5 معلمين', popular: true,
    features: ['كل مزايا باقة النمو', 'فروع ومستأجرين متعددين', 'أدوار وصلاحيات مخصصة', 'لوحة شرف وتحفيز الطلاب', 'دعم فني بأولوية'],
  },
  {
    key: 'enterprise', name: 'المؤسسات', monthly: null, cap: 'طلاب ومعلمين غير محدودين',
    features: ['كل مزايا الباقة المحترفة', 'سجل تدقيق كامل للنظام', 'تكامل مخصص حسب احتياجك', 'مدير حساب مخصص', 'دعم فني 24/7'],
  },
]

const PRICING_FAQS = [
  { q: 'هل فيه تجربة مجانية؟', a: 'أيوه، تقدر تسجّل حساب مجاني الآن وتستكشف كل مميزات المنصة — بدون بطاقة ائتمان.' },
  { q: 'أقدر أغيّر الباقة بعد الاشتراك؟', a: 'طبعاً، تقدر تترقّى لباقة أعلى في أي وقت حسب نمو عدد طلابك.' },
  { q: 'الأسعار شاملة الضرائب؟', a: 'الأسعار المعروضة تقديرية وقابلة للتخصيص حسب حجم مؤسستك — تواصل معنا لعرض سعر دقيق.' },
  { q: 'محتاج باقة مخصصة لمؤسسة كبيرة، أعمل إيه؟', a: 'كلّمنا من صفحة تواصل معنا وهنصمّملك باقة تناسب احتياجاتك بالظبط.' },
]

export default function Pricing() {
  const [yearly, setYearly] = useState(false)
  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <PageHero badge="ابدأ مجاناً لمدة 14 يوم" title="باقات وأسعار" highlight="تناسب كل الأحجام"
        subtitle="من المدرس المستقل للمؤسسة الكبيرة — اختر الباقة اللي تناسب حجم أكاديميتك.">
        <div className="mt-8 inline-flex items-center gap-3 rounded-full border border-ink-200 bg-white p-1.5 shadow-soft">
          <button onClick={() => setYearly(false)} className={`rounded-full px-5 py-2 text-sm font-bold transition ${!yearly ? 'bg-brand-600 text-white' : 'text-ink-500'}`}>شهري</button>
          <button onClick={() => setYearly(true)} className={`flex items-center gap-1.5 rounded-full px-5 py-2 text-sm font-bold transition ${yearly ? 'bg-brand-600 text-white' : 'text-ink-500'}`}>
            سنوي <span className="chip bg-emerald-100 text-emerald-700">وفّر 17٪</span>
          </button>
        </div>
      </PageHero>

      <section className="mx-auto max-w-7xl px-5 pb-20">
        <div className="grid gap-6 lg:grid-cols-4">
          {TIERS.map((t, i) => (
            <motion.div key={t.key} initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
              transition={{ delay: i * 0.07 }}
              className={`card relative flex flex-col p-7 ${t.popular ? 'border-2 border-brand-400 shadow-glow' : ''}`}>
              {t.popular && <span className="absolute -top-3.5 right-1/2 translate-x-1/2 chip bg-brand-600 text-white"><Sparkles size={12} /> الأكثر طلباً</span>}
              <p className="font-extrabold text-ink-800">{t.name}</p>
              <p className="mt-1 text-xs text-ink-400">{t.cap}</p>
              <div className="mt-5">
                {t.monthly == null ? (
                  <p className="text-3xl font-black text-ink-800">حسب الطلب</p>
                ) : (
                  <p className="text-3xl font-black text-ink-800">
                    {(yearly ? t.yearly : t.monthly).toLocaleString('ar-EG')} <span className="text-base font-bold text-ink-400">ج.م / شهر</span>
                  </p>
                )}
              </div>
              <Link to={t.monthly == null ? '/contact' : '/register'} className={`mt-5 btn justify-center py-2.5 ${t.popular ? 'btn-primary' : 'btn-soft'}`}>
                {t.monthly == null ? 'تواصل معنا' : 'ابدأ الآن'}
              </Link>
              <div className="mt-6 space-y-2.5 border-t border-ink-100 pt-6">
                {t.features.map((f) => (
                  <div key={f} className="flex items-start gap-2 text-sm text-ink-600"><CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-500" /> {f}</div>
                ))}
              </div>
            </motion.div>
          ))}
        </div>

        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
          className="mt-10 flex flex-col items-center justify-between gap-4 rounded-3xl bg-gradient-to-l from-brand-600 to-brand-800 p-8 text-center text-white sm:flex-row sm:text-right">
          <div>
            <p className="text-xl font-black">محتاج خطة مخصصة؟</p>
            <p className="mt-1 text-brand-100">كلّمنا وهنصمّملك الباقة المناسبة لأكاديميتك بأفضل سعر.</p>
          </div>
          <Link to="/contact" className="btn shrink-0 bg-white px-6 py-3 text-brand-700 hover:bg-brand-50">تواصل معنا</Link>
        </motion.div>
      </section>

      <section className="bg-white py-20">
        <div className="mx-auto max-w-7xl px-5">
          <div className="mb-12 text-center">
            <h2 className="text-3xl font-black sm:text-4xl">أسئلة عن الأسعار</h2>
          </div>
          <FAQAccordion items={PRICING_FAQS} />
        </div>
      </section>

      <MarketingFooter />
    </div>
  )
}
