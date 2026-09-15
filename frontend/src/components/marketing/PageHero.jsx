import { motion } from 'framer-motion'
import { Sparkles } from 'lucide-react'

/** Small hero banner shared by every inner marketing page (Features/Pricing/About/Contact/...) —
 *  the same mesh/blob/dots treatment as the home page hero, scaled down for a page that isn't
 *  itself the landing page. */
export default function PageHero({ badge, title, highlight, subtitle, children }) {
  return (
    <section className="relative overflow-hidden pb-14 pt-32 sm:pt-40">
      <div className="absolute inset-0 -z-20 mesh-bg" />
      <div className="absolute -top-32 -right-20 -z-10 h-[380px] w-[380px] rounded-full bg-brand-300/30 blur-3xl animate-blob" />
      <div className="absolute top-16 -left-24 -z-10 h-[320px] w-[320px] rounded-full bg-sky-400/25 blur-3xl animate-blob" style={{ animationDelay: '4s' }} />
      <div className="absolute inset-0 -z-10 bg-dots opacity-40 [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_40%,transparent_100%)]" />
      <div className="mx-auto max-w-4xl px-5 text-center">
        {badge && (
          <motion.span initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }}
            className="chip border border-brand-200 bg-white/80 text-brand-700 shadow-soft backdrop-blur">
            <Sparkles size={14} /> {badge}
          </motion.span>
        )}
        <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
          className="mt-6 text-4xl font-black leading-[1.15] sm:text-5xl">
          {title}{' '}
          {highlight && <span className="gradient-text bg-[length:200%_auto] animate-gradient-x">{highlight}</span>}
        </motion.h1>
        {subtitle && (
          <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
            className="mx-auto mt-5 max-w-2xl text-lg leading-relaxed text-ink-500">
            {subtitle}
          </motion.p>
        )}
        {children}
      </div>
    </section>
  )
}
