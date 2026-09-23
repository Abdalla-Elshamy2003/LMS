import { Link } from 'react-router-dom'
import { motion, useReducedMotion } from 'framer-motion'
import { ArrowUpLeft, Layers, Plus, Sparkles, Users } from 'lucide-react'
import { glyphFor, tintStyle } from './bundleArt'
import './bundles.css'

/** How much smaller each portrait is than the middle one, so the row rises to a peak in the centre. */
const scaleAt = (i, n) => 1 - 0.1 * Math.abs(i - (n - 1) / 2)

/**
 * The package's teachers as arched portraits, side by side. Each rises in on its own beat, then floats
 * out of phase with its neighbours; hovering the row fans it out. `onPick` makes each portrait a button
 * (the package page uses it to jump to that teacher's section).
 */
export function BundleStage({ members, base = 'clamp(58px, 15vw, 148px)', showNames = false, onPick }) {
  const reduced = useReducedMotion()
  const n = members.length
  return (
    <div className="bx-stage" style={{ '--base': base }}>
      {members.map((m, i) => {
        const Tag = onPick ? 'button' : 'div'
        return (
          <motion.div key={i} className="bx-slot"
            style={{ ...tintStyle(i), '--s': scaleAt(i, n), '--z': 10 - Math.round(Math.abs(i - (n - 1) / 2)), '--i': i }}
            initial={reduced ? false : { opacity: 0, y: 70, rotate: (i - (n - 1) / 2) * 4 }}
            whileInView={{ opacity: 1, y: 0, rotate: 0 }} viewport={{ once: true, amount: 0.3 }}
            transition={{ type: 'spring', stiffness: 90, damping: 14, delay: 0.1 + i * 0.12 }}>
            <Tag {...(onPick ? { type: 'button', onClick: () => onPick(i), 'aria-label': `انتقل إلى ${m.name || m.subject}` } : {})} className="block w-full text-center">
              <span className="bx-float block">
                <span className="bx-arch">
                  <span className="bx-arch-photo">
                    {m.photoUrl
                      ? <img src={m.photoUrl} alt={m.name ? `${m.name} — ${m.subject}` : `مدرس ${m.subject}`} loading="lazy" />
                      : <span className="bx-arch-fallback" dir="ltr">{glyphFor(m.subject)}</span>}
                  </span>
                </span>
              </span>
              <span className="bx-label"><i />{m.subject}{showNames && m.name && <small>{m.name}</small>}</span>
            </Tag>
          </motion.div>
        )
      })}
    </div>
  )
}

/** The home-page card for one package: the portraits on a dark stage, and the way in to the package page. */
export function BundleCard({ bundle, className = '' }) {
  const members = bundle.members || []
  return (
    <Link to={`/packages/${bundle.slug}`} className={`bx-card group flex min-h-[30rem] flex-col p-6 sm:p-9 ${className}`}
      aria-label={`افتح ${bundle.name}`}>
      <span className="bx-grid" aria-hidden="true" />
      <span className="bx-orbit" aria-hidden="true" />
      <span className="bx-orbit two" aria-hidden="true" />
      <span className="bx-glow -top-24 -right-16 bg-cyan-400" aria-hidden="true" />
      <span className="bx-glow -bottom-28 -left-20 bg-fuchsia-500" style={{ animationDelay: '-5s' }} aria-hidden="true" />

      <div className="flex items-start justify-between gap-3">
        <span className="chip border border-white/15 bg-white/10 text-cyan-100 backdrop-blur"><Layers size={14} /> باقة مدرسين</span>
        <span className="chip bg-amber-300/15 text-amber-200"><Users size={14} /> {members.length.toLocaleString('ar-EG')} مدرسين</span>
      </div>

      <div className="flex flex-1 items-end justify-center pb-2 pt-10">
        <BundleStage members={members} />
      </div>

      <div className="mt-8 flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0">
          <h3 className="text-2xl font-black leading-tight sm:text-3xl">{bundle.name}</h3>
          {bundle.tagline && <p className="mt-2 text-sm text-cyan-100/80">{bundle.tagline}</p>}
        </div>
        <span className="bx-cta inline-flex items-center gap-2 rounded-2xl bg-white/15 px-5 py-3 text-sm font-black text-white backdrop-blur">
          افتح الباقة <ArrowUpLeft size={17} />
        </span>
      </div>
    </Link>
  )
}

/** A reserved slot next to the real packages: the next group of teachers goes here. */
export function EmptyBundleCard({ i = 0 }) {
  return (
    <motion.div initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.15 + i * 0.1 }}
      className="bx-empty flex min-h-[14rem] flex-col items-center justify-center gap-5 p-6 text-center">
      <div className="flex w-40 items-end justify-center gap-2" aria-hidden="true">
        <span className="bx-ghost w-10" /><span className="bx-ghost grid w-12 place-items-center text-sky-400"><Plus size={18} /></span><span className="bx-ghost w-10" />
      </div>
      <div>
        <p className="font-black text-ink-700">باقة جديدة.. قريباً</p>
        <p className="mt-1 text-xs text-ink-400">مكان محجوز لمجموعة المدرسين الجاية</p>
      </div>
      <Link to="/contact" className="chip bg-white text-brand-700 shadow-soft transition hover:bg-brand-600 hover:text-white"><Sparkles size={13} /> كوّن باقة مع زمايلك</Link>
    </motion.div>
  )
}
