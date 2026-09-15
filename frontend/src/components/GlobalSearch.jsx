import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Search, Users, BookOpen, GraduationCap, FileQuestion, CornerDownLeft } from 'lucide-react'
import api from '../lib/api'

const ICON = { student: Users, course: BookOpen, teacher: GraduationCap, exam: FileQuestion }
const TYPE_LABEL = { student: 'طالب', course: 'كورس', teacher: 'مدرس', exam: 'امتحان' }

export default function GlobalSearch() {
  const [open, setOpen] = useState(false)
  const [q, setQ] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [active, setActive] = useState(0)
  const inputRef = useRef()
  const nav = useNavigate()

  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setOpen(true) }
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => { if (open) setTimeout(() => inputRef.current?.focus(), 50) }, [open])

  useEffect(() => {
    if (!q.trim()) { setResults([]); return }
    setLoading(true)
    const t = setTimeout(() => {
      api.get('/search', { params: { q } }).then((r) => { setResults(r.data.results); setActive(0) })
        .catch(() => setResults([])).finally(() => setLoading(false))
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  const go = (r) => { setOpen(false); setQ(''); nav(r.href) }

  const onInputKey = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, results.length - 1)) }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
    if (e.key === 'Enter' && results[active]) go(results[active])
  }

  return (
    <>
      <button onClick={() => setOpen(true)}
        className="flex items-center gap-2 rounded-2xl border border-ink-100 bg-white px-3 py-2.5 text-sm text-ink-400 shadow-soft transition hover:bg-ink-50 md:min-w-[220px]">
        <Search size={18} />
        <span className="hidden md:inline">بحث سريع...</span>
        <kbd className="mr-auto hidden rounded-lg bg-ink-100 px-1.5 py-0.5 text-[10px] font-bold text-ink-500 md:inline">Ctrl K</kbd>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div className="fixed inset-0 z-[60] flex items-start justify-center p-4 pt-[12vh]"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <div className="absolute inset-0 bg-ink-950/40 backdrop-blur-sm" onClick={() => setOpen(false)} />
            <motion.div className="relative w-full max-w-xl overflow-hidden rounded-3xl bg-white shadow-card"
              initial={{ scale: 0.96, y: -10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.96, y: -10 }}>
              <div className="flex items-center gap-3 border-b border-ink-100 px-5 py-4">
                <Search size={20} className="text-ink-400" />
                <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onInputKey}
                  placeholder="ابحث عن طالب أو كورس أو مدرس أو امتحان..." className="flex-1 bg-transparent text-sm outline-none" />
                {loading && <div className="h-4 w-4 rounded-full border-2 border-brand-200 border-t-brand-600 animate-spin" />}
              </div>
              <div className="max-h-[50vh] overflow-y-auto p-2">
                {results.length === 0 ? (
                  <p className="py-10 text-center text-sm text-ink-400">{q ? 'لا توجد نتائج' : 'ابدأ الكتابة للبحث...'}</p>
                ) : results.map((r, i) => {
                  const Icon = ICON[r.type] || Search
                  return (
                    <button key={r.type + r.id} onClick={() => go(r)} onMouseEnter={() => setActive(i)}
                      className={`flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-right transition ${i === active ? 'bg-brand-50' : 'hover:bg-ink-50'}`}>
                      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-brand-100 text-brand-600"><Icon size={17} /></span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-bold text-ink-800">{r.title}</span>
                        <span className="block truncate text-xs text-ink-400">{r.subtitle}</span>
                      </span>
                      <span className="chip bg-ink-100 text-ink-500">{TYPE_LABEL[r.type]}</span>
                      {i === active && <CornerDownLeft size={15} className="text-ink-300" />}
                    </button>
                  )
                })}
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}
