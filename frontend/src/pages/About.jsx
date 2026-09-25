import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Target, Eye, Lightbulb, Gem, Heart, MapPin, ArrowLeft } from 'lucide-react'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import PageHero from '../components/marketing/PageHero'
import Hero3D from '../components/Hero3D'

const VALUES = [
  { icon: Lightbulb, title: 'الابتكار', desc: 'بنبني حلول تناسب احتياج المدرّس الحقيقي، مش بس نقلّد الموجود.' },
  { icon: Gem, title: 'الجودة', desc: 'كل تفصيلة في المنصة بتتراجع وتتختبر قبل ما توصلك.' },
  { icon: Heart, title: 'التأثير', desc: 'كل ميزة بنبنيها هدفها إنها توفّر وقت المدرّس وتحسّن تجربة الطالب.' },
  { icon: MapPin, title: 'عربي أولاً', desc: 'مصممة من الأساس للغة العربية وRTL — مش ترجمة لنظام أجنبي.' },
]

export default function About() {
  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <PageHero badge="مش بس منصة..." title="دي نقلة" highlight="في التعليم" subtitle="دروس صُممت لتكون شريكك التقني الكامل في إدارة رحلة التعليم — من أول تسجيل طالب لحد إصدار الشهادة." />

      <section className="mx-auto max-w-4xl px-5 py-8">
        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}>
          <h2 className="text-2xl font-black text-ink-800">قصة دروس</h2>
          <p className="mt-4 leading-relaxed text-ink-600">
            بدأت فكرة دروس من ملاحظة بسيطة: المراكز التعليمية والمدارس في مصر والوطن العربي بتدير جزءاً كبيراً من عملها
            بطرق متفرقة — ورق، إكسل، مجموعات واتساب — وكل ده بياخد وقت ومجهود كان ممكن يتصرف على تطوير المحتوى ومتابعة الطلاب فعلياً.
            قررنا نبني منظومة واحدة متكاملة، عربية أولاً، تجمع كل حاجة محتاجها أي مؤسسة تعليمية في مكان واحد — من إدارة
            الطلاب والحضور، للامتحانات والواجبات، لمتابعة أولياء الأمور، وحتى الشهادات — بتجربة استخدام بسيطة واحترافية.
          </p>
        </motion.div>
      </section>

      <section className="mx-auto max-w-6xl px-5 py-8">
        <div className="grid gap-6 sm:grid-cols-2">
          <motion.div initial={{ opacity: 0, x: 20 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} className="card p-8">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600"><Target size={22} /></div>
            <p className="mt-4 text-lg font-extrabold text-ink-800">مهمتنا</p>
            <p className="mt-2 leading-relaxed text-ink-500">نمكّن كل معلم ومؤسسة تعليمية من تقديم أفضل تجربة تعلّم رقمية — بأدوات ذكية وتصميم يفهم احتياج السوق العربي.</p>
          </motion.div>
          <motion.div initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} className="card p-8">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600"><Eye size={22} /></div>
            <p className="mt-4 text-lg font-extrabold text-ink-800">رؤيتنا</p>
            <p className="mt-2 leading-relaxed text-ink-500">نكون المنصة اللي كل أكاديمية عربية تعتمد عليها لإدارة عملها التعليمي بالكامل — بسهولة واحترافية.</p>
          </motion.div>
        </div>
      </section>

      <section className="relative py-16">
        <div className="mx-auto grid max-w-6xl items-center gap-10 px-5 lg:grid-cols-2">
          <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}>
            <h2 className="text-2xl font-black text-ink-800">قيمنا</h2>
            <div className="mt-6 grid gap-5 sm:grid-cols-2">
              {VALUES.map((v) => (
                <div key={v.title}>
                  <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand-50 text-brand-600"><v.icon size={18} /></div>
                  <p className="mt-3 font-bold text-ink-800">{v.title}</p>
                  <p className="mt-1 text-sm text-ink-500">{v.desc}</p>
                </div>
              ))}
            </div>
          </motion.div>
          <motion.div initial={{ opacity: 0, scale: 0.94 }} whileInView={{ opacity: 1, scale: 1 }} viewport={{ once: true }} className="relative h-72 sm:h-80">
            <Hero3D className="absolute inset-0 h-full w-full" />
          </motion.div>
        </div>
      </section>

      <section className="relative overflow-hidden py-20">
        <div className="absolute inset-0 -z-10 bg-gradient-to-br from-brand-700 to-brand-950" />
        <div className="absolute inset-0 -z-10 bg-grid opacity-30" />
        <div className="mx-auto max-w-2xl px-5 text-center text-white">
          <h2 className="text-3xl font-black sm:text-4xl">جاهز تبدأ رحلتك مع دروس؟</h2>
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
