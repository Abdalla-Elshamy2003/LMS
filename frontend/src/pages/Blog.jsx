import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Clock, ArrowLeft } from 'lucide-react'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import PageHero from '../components/marketing/PageHero'
import { BLOG_POSTS } from '../lib/blogPosts'
import { fmtDate } from '../lib/format'

const GRADIENTS = ['from-brand-500 to-brand-700', 'from-emerald-500 to-teal-700', 'from-violet-500 to-purple-700']

export default function Blog() {
  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <PageHero badge="مدوّنة منارة" title="مقالات في" highlight="إدارة التعليم" subtitle="أفكار ونصائح عملية لكل من يدير مؤسسة تعليمية." />

      <section className="mx-auto max-w-6xl px-5 pb-20">
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {BLOG_POSTS.map((p, i) => (
            <motion.div key={p.slug} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: i * 0.07 }}>
              <Link to={`/blog/${p.slug}`} className="card block h-full overflow-hidden transition hover:shadow-glow">
                <div className={`h-28 bg-gradient-to-br ${GRADIENTS[i % GRADIENTS.length]} p-5`}>
                  <span className="chip bg-white/20 text-white backdrop-blur">{p.category}</span>
                </div>
                <div className="p-5">
                  <p className="font-extrabold leading-snug text-ink-800">{p.title}</p>
                  <p className="mt-2 line-clamp-2 text-sm text-ink-500">{p.excerpt}</p>
                  <div className="mt-4 flex items-center justify-between text-xs text-ink-400">
                    <span>{fmtDate(p.date)}</span>
                    <span className="flex items-center gap-1"><Clock size={12} /> {p.readTime}</span>
                  </div>
                </div>
              </Link>
            </motion.div>
          ))}
        </div>
      </section>

      <MarketingFooter />
    </div>
  )
}
