import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X } from 'lucide-react'
import { initials } from '../lib/format'

export function Badge({ children, className = '' }) {
  return <span className={`chip border ${className}`}>{children}</span>
}

export function Avatar({ name, size = 40, color = 'from-brand-500 to-brand-700' }) {
  return (
    <div
      className={`inline-flex items-center justify-center rounded-2xl bg-gradient-to-br ${color} text-white font-bold shadow-soft`}
      style={{ width: size, height: size, fontSize: size * 0.36 }}
    >
      {initials(name)}
    </div>
  )
}

export function CountUp({ value = 0, decimals = 0, suffix = '', duration = 1100 }) {
  const [display, setDisplay] = useState(0)
  const ref = useRef()
  useEffect(() => {
    const start = performance.now()
    const from = 0
    const to = Number(value) || 0
    const tick = (now) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      setDisplay(from + (to - from) * eased)
      if (t < 1) ref.current = requestAnimationFrame(tick)
    }
    ref.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(ref.current)
  }, [value, duration])
  const formatted = display.toLocaleString('ar-EG', { maximumFractionDigits: decimals, minimumFractionDigits: decimals })
  return <span>{formatted}{suffix}</span>
}

export function ProgressRing({ value = 0, size = 96, stroke = 9, color = '#4f46e5', track = '#e2e8f0', label }) {
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const pct = Math.max(0, Math.min(100, value))
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={track} strokeWidth={stroke} />
        <motion.circle
          cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c - (pct / 100) * c }}
          transition={{ duration: 1, ease: 'easeOut' }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-xl font-extrabold text-ink-800"><CountUp value={value} decimals={0} suffix="٪" /></span>
        {label && <span className="text-[11px] text-ink-400 font-semibold">{label}</span>}
      </div>
    </div>
  )
}

export function ProgressBar({ value = 0, color = 'bg-brand-500', className = '' }) {
  return (
    <div className={`h-2 w-full rounded-full bg-ink-100 overflow-hidden ${className}`}>
      <motion.div className={`h-full rounded-full ${color}`}
        initial={{ width: 0 }} animate={{ width: `${Math.max(0, Math.min(100, value))}%` }}
        transition={{ duration: 0.9, ease: 'easeOut' }} />
    </div>
  )
}

export function EmptyState({ icon: Icon, title, hint }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      {Icon && <div className="mb-3 rounded-2xl bg-ink-100 p-4 text-ink-400"><Icon size={28} /></div>}
      <p className="font-bold text-ink-700">{title}</p>
      {hint && <p className="text-sm text-ink-400 mt-1">{hint}</p>}
    </div>
  )
}

export function Spinner({ className = '' }) {
  return <div className={`h-6 w-6 rounded-full border-2 border-brand-200 border-t-brand-600 animate-spin ${className}`} />
}

export function PageLoader() {
  return <div className="flex items-center justify-center py-24"><Spinner className="h-8 w-8" /></div>
}

/** Elements a focus trap should cycle through — matches the shared .input/.btn classes. */
const FOCUSABLE = 'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])'

export function Modal({ open, onClose, title, children, wide = false, size }) {
  const dialogRef = useRef(null)
  const triggerRef = useRef(null)

  // Focus trap + Escape + focus return: captured once per open (see design-system skill's
  // dialog contract — title, role="dialog", trap, Escape, background inertness, focus return).
  useEffect(() => {
    if (!open) return
    triggerRef.current = document.activeElement
    const node = dialogRef.current
    const focusables = () => node ? Array.from(node.querySelectorAll(FOCUSABLE)) : []
    const first = focusables()[0]
    ;(first || node)?.focus()

    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        onClose?.()
        return
      }
      if (e.key !== 'Tab') return
      const items = focusables()
      if (items.length === 0) return
      const firstEl = items[0]
      const lastEl = items[items.length - 1]
      if (e.shiftKey && document.activeElement === firstEl) {
        e.preventDefault(); lastEl.focus()
      } else if (!e.shiftKey && document.activeElement === lastEl) {
        e.preventDefault(); firstEl.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('keydown', onKeyDown, true)
      // Return focus to whatever opened the dialog once it's gone.
      triggerRef.current?.focus?.()
    }
  }, [open, onClose])

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-50 flex items-center justify-center p-4"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="absolute inset-0 bg-ink-950/40 backdrop-blur-sm" onClick={onClose} aria-hidden="true" />
          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            tabIndex={-1}
            className={`relative card p-5 sm:p-6 w-full ${size === 'xl' ? 'max-w-6xl' : size === 'lg' ? 'max-w-4xl' : wide ? 'max-w-2xl' : 'max-w-lg'} max-h-[92vh] overflow-y-auto outline-none`}
            initial={{ scale: 0.94, y: 20, opacity: 0 }} animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.94, y: 20, opacity: 0 }} transition={{ type: 'spring', stiffness: 300, damping: 26 }}>
            <div className="flex items-center justify-between mb-4">
              <h3 id="modal-title" className="text-lg font-extrabold text-ink-800">{title}</h3>
              <button onClick={onClose} aria-label="إغلاق" className="rounded-xl p-1.5 text-ink-400 hover:bg-ink-100"><X size={20} /></button>
            </div>
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
}
export const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
}
