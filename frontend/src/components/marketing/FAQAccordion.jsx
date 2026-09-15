import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronDown } from 'lucide-react'

export default function FAQAccordion({ items }) {
  const [open, setOpen] = useState(0)
  return (
    <div className="mx-auto max-w-3xl space-y-3">
      {items.map((item, i) => {
        const isOpen = open === i
        return (
          <div key={item.q} className="card overflow-hidden">
            <button onClick={() => setOpen(isOpen ? -1 : i)} className="flex w-full items-center justify-between gap-4 px-6 py-4.5 text-right">
              <span className="font-bold text-ink-800">{item.q}</span>
              <motion.span animate={{ rotate: isOpen ? 180 : 0 }} className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-brand-50 text-brand-600">
                <ChevronDown size={16} />
              </motion.span>
            </button>
            <AnimatePresence initial={false}>
              {isOpen && (
                <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} className="overflow-hidden">
                  <p className="px-6 pb-5 leading-relaxed text-ink-500">{item.a}</p>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        )
      })}
    </div>
  )
}
