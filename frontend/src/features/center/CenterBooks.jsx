import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { BookMarked, CalendarClock, CheckCircle2, PackageCheck, Pencil, Plus, Search, Ticket, Trash2, XCircle } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { SCHOOL_YEARS } from '../../lib/format'
import { EmptyState, Modal, PageLoader, Spinner, fadeUp, stagger } from '../../components/ui'
import BookCover from './BookCover'
import { cairoToday, fmtClock, fmtDay, money, num } from './centerUtils'

const STATUS = {
  RESERVED: ['محجوز', 'bg-amber-50 text-amber-800'],
  DELIVERED: ['اتسلم', 'bg-emerald-50 text-emerald-700'],
  CANCELLED: ['اتلغى', 'bg-ink-100 text-ink-500'],
}

/**
 * The center's books and notes as cards, each with its release day and copies left; the reservations students made
 * from their card's page; and handing a book over against the reservation code.
 */
export default function CenterBooks() {
  const [books, setBooks] = useState(null)
  const [reservations, setReservations] = useState([])
  const [teachers, setTeachers] = useState([])
  const [editing, setEditing] = useState(null)
  const [reserveFor, setReserveFor] = useState(null)
  const [filter, setFilter] = useState({ status: 'RESERVED', q: '', bookId: '' })
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const load = () => Promise.all([api.get('/center/books'), api.get('/center/reservations')])
    .then(([b, r]) => { setBooks(b.data); setReservations(r.data) })
    .catch((e) => { setBooks([]); setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر تحميل الكتب') }) })
  useEffect(() => { load(); api.get('/center/teachers').then((r) => setTeachers(r.data)).catch(() => {}) }, [])
  if (!books) return <PageLoader />

  const say = (ok) => setFlash({ ok, error: '' })
  const fail = (e, fallback) => setFlash({ ok: '', error: apiErrorMessage(e, fallback) })
  const today = cairoToday()
  const needle = filter.q.trim().toLowerCase()
  const shownReservations = reservations.filter((r) => (!filter.status || r.status === filter.status)
    && (!filter.bookId || String(r.bookId) === filter.bookId)
    && (!needle || r.code.toLowerCase().includes(needle) || r.studentName.toLowerCase().includes(needle) || r.studentCode === needle))

  const deliver = async (r) => {
    try { await api.post(`/center/reservations/${r.id}/deliver`); await load(); say(`اتسلم «${r.bookTitle}» لـ ${r.studentName}`) }
    catch (e) { fail(e, 'تعذّر التسليم') }
  }
  const cancel = async (r) => {
    if (!window.confirm(`إلغاء حجز ${r.studentName}؟`)) return
    try { await api.post(`/center/reservations/${r.id}/cancel`); await load(); say('اتلغى الحجز') } catch (e) { fail(e, 'تعذّر الإلغاء') }
  }
  const remove = async (b) => {
    if (!window.confirm(`مسح «${b.title}»؟`)) return
    try { await api.delete(`/center/books/${b.id}`); await load(); say('اتمسح الكتاب') } catch (e) { fail(e, 'تعذّر المسح') }
  }

  return (
    <div className="space-y-6">
      <DeliverBox onDone={async (r) => { await load(); say(`اتسلم «${r.bookTitle}» لـ ${r.studentName} — ${money(r.price)}`) }} onFail={fail} />

      {flash.error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{flash.error}</p>}
      {flash.ok && <p role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-700">{flash.ok}</p>}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 text-lg font-black text-ink-800"><BookMarked size={20} className="text-brand-600" /> الكتب والملازم</h3>
          <p className="text-xs text-ink-500">الطالب بيشوفهم كروت في صفحة الكارت بتاعه ويحجز منها ويستلم كود الحجز.</p>
        </div>
        <button type="button" className="btn-primary" onClick={() => setEditing({})}><Plus size={17} /> كتاب جديد</button>
      </div>

      {books.length === 0 ? <div className="card"><EmptyState icon={BookMarked} title="لسه مفيش كتب" hint="ضيف كتاب أو ملزمة بميعاد نزولها" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          {books.map((b) => (
            <motion.article key={b.id} variants={fadeUp} className={`card overflow-hidden p-0 ${b.active ? '' : 'opacity-60'}`}>
              <BookCover book={b} className="h-40" />
              <div className="space-y-3 p-4">
                <div className="flex items-center justify-between gap-2">
                  <b className="text-lg font-black text-brand-700">{money(b.price)}</b>
                  {b.released
                    ? <span className="chip bg-emerald-50 text-emerald-700"><CheckCircle2 size={12} /> نزل</span>
                    : <span className="chip bg-sky-50 text-sky-700"><CalendarClock size={12} /> ينزل {fmtDay(b.releaseDate, false)}</span>}
                </div>
                {b.grade && <p className="text-xs text-ink-500">{b.grade}</p>}
                <div className="grid grid-cols-3 gap-2 text-center text-[11px]">
                  <p className="rounded-xl bg-amber-50 p-2"><b className="block text-base text-amber-700">{num(b.reserved)}</b>محجوز</p>
                  <p className="rounded-xl bg-emerald-50 p-2"><b className="block text-base text-emerald-700">{num(b.delivered)}</b>اتسلم</p>
                  <p className="rounded-xl bg-ink-50 p-2"><b className="block text-base text-ink-700">{b.remaining == null ? '∞' : num(b.remaining)}</b>باقي</p>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <button type="button" className="btn-soft px-2.5 py-1.5 text-xs" onClick={() => setReserveFor(b)} disabled={!b.active}><Ticket size={13} /> احجز لطالب</button>
                  <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setFilter({ ...filter, bookId: String(b.id), status: '' })}>الحجوزات</button>
                  <button type="button" aria-label={`تعديل ${b.title}`} className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setEditing(b)}><Pencil size={13} /></button>
                  {b.reserved + b.delivered === 0 && <button type="button" aria-label={`مسح ${b.title}`} className="btn-ghost px-2.5 py-1.5 text-xs text-rose-600" onClick={() => remove(b)}><Trash2 size={13} /></button>}
                </div>
              </div>
            </motion.article>
          ))}
        </motion.div>
      )}

      <section className="card p-0">
        <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 p-4">
          <h3 className="font-extrabold text-ink-800">الحجوزات</h3>
          <div className="relative min-w-[200px] flex-1">
            <Search size={16} className="absolute right-3.5 top-3 text-ink-400" />
            <input className="input pr-10" placeholder="كود الحجز أو اسم الطالب" value={filter.q} onChange={(e) => setFilter({ ...filter, q: e.target.value })} aria-label="بحث في الحجوزات" />
          </div>
          <select className="input w-auto max-w-full" value={filter.bookId} onChange={(e) => setFilter({ ...filter, bookId: e.target.value })} aria-label="الكتاب">
            <option value="">كل الكتب</option>
            {books.map((b) => <option key={b.id} value={b.id}>{b.title}</option>)}
          </select>
          <select className="input w-auto max-w-full" value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })} aria-label="الحالة">
            <option value="">كل الحالات</option>
            {Object.entries(STATUS).map(([k, [label]]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </div>
        {shownReservations.length === 0 ? <p className="p-6 text-center text-sm text-ink-400">مفيش حجوزات هنا.</p> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-right text-sm">
              <thead><tr className="border-b border-ink-100 text-xs text-ink-400"><th className="p-3">الكود</th><th>الطالب</th><th>الكتاب</th><th>السعر</th><th>الحالة</th><th /></tr></thead>
              <tbody>
                {shownReservations.map((r) => {
                  const [label, tone] = STATUS[r.status] || [r.status, 'bg-ink-100']
                  return (
                    <tr key={r.id} className="border-b border-ink-50">
                      <td className="p-3 font-mono text-xs font-bold text-brand-700" dir="ltr">{r.code}</td>
                      <td><b className="text-ink-800">{r.studentName}</b><span className="block text-xs text-ink-400"><span dir="ltr">{r.studentCode}</span> · {r.teacherName}</span></td>
                      <td>{r.bookTitle}{r.releaseDate > today && <span className="block text-[11px] text-sky-700">ينزل {fmtDay(r.releaseDate, false)}</span>}</td>
                      <td>{money(r.price)}</td>
                      <td><span className={`chip ${tone}`}>{label}</span>{r.deliveredAt && <span className="block text-[10px] text-ink-400">{fmtClock(r.deliveredAt)}</span>}</td>
                      <td>
                        {r.status === 'RESERVED' && (
                          <div className="flex justify-end gap-1 pl-3">
                            <button type="button" className="btn-soft px-2.5 py-1.5 text-xs" onClick={() => deliver(r)}><PackageCheck size={13} /> سلّم</button>
                            <button type="button" aria-label="إلغاء الحجز" className="btn-ghost px-2.5 py-1.5 text-xs text-rose-600" onClick={() => cancel(r)}><XCircle size={13} /></button>
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {editing && <BookModal book={editing} teachers={teachers} onClose={() => setEditing(null)}
        onSaved={async (msg) => { setEditing(null); await load(); say(msg) }} />}
      {reserveFor && <ReserveModal book={reserveFor} onClose={() => setReserveFor(null)}
        onDone={async (r) => { setReserveFor(null); await load(); say(`اتحجز لـ ${r.studentName} — كود الحجز ${r.code}`) }} />}
    </div>
  )
}

function DeliverBox({ onDone, onFail }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (e) => {
    e.preventDefault()
    if (!code.trim()) return
    setBusy(true)
    try { const { data } = await api.post('/center/reservations/deliver', { code }); setCode(''); await onDone(data) }
    catch (err) { onFail(err, 'تعذّر التسليم') } finally { setBusy(false) }
  }
  return (
    <form onSubmit={submit} className="rounded-3xl bg-gradient-to-l from-[#0b2e47] to-[#0e7490] p-5 text-white sm:p-6">
      <p className="flex items-center gap-2 font-black"><PackageCheck size={20} /> تسليم كتاب بكود الحجز</p>
      <div className="mt-3 flex flex-wrap gap-2">
        <input dir="ltr" className="input max-w-xs flex-1 text-center font-mono tracking-widest text-ink-800" placeholder="BK-XXXXXX"
          value={code} onChange={(e) => setCode(e.target.value)} aria-label="كود الحجز" />
        <button type="submit" disabled={busy} className="btn bg-white text-[#0b2e47] hover:bg-sky-50">{busy ? <Spinner className="h-4 w-4" /> : 'سلّم الكتاب'}</button>
      </div>
      <p className="mt-2 text-xs text-sky-100/80">الطالب بيوريك الكود من صفحة الكارت بتاعه.</p>
    </form>
  )
}

function BookModal({ book, teachers, onClose, onSaved }) {
  const [form, setForm] = useState({
    title: book.title || '', teacherId: book.teacherId ? String(book.teacherId) : '', description: book.description || '',
    grade: book.grade || '', price: book.price ?? '', releaseDate: book.releaseDate || cairoToday(),
    stock: book.stock ?? '', active: book.id ? book.active : true,
  })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    const body = {
      ...form, teacherId: form.teacherId ? Number(form.teacherId) : null, price: Number(form.price) || 0,
      stock: form.stock === '' ? null : Number(form.stock),
    }
    try {
      if (book.id) await api.put(`/center/books/${book.id}`, body)
      else await api.post('/center/books', body)
      onSaved(book.id ? 'اتحفظ الكتاب' : `اتضاف «${form.title}» — الطلاب يقدروا يحجزوه دلوقتي`)
    } catch (err) { setError(apiErrorMessage(err, 'تعذّر الحفظ')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={book.id ? `تعديل ${book.title}` : 'كتاب أو ملزمة جديدة'} wide>
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="b-title">اسم الكتاب</label><input id="b-title" className="input" value={form.title} onChange={set('title')} placeholder="ملزمة الكهربية — الترم الأول" required /></div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="b-teacher">المدرس</label>
            <select id="b-teacher" className="input" value={form.teacherId} onChange={set('teacherId')}>
              <option value="">لكل طلاب السنتر</option>
              {teachers.map((t) => <option key={t.id} value={t.id}>{t.name} · {t.subject}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="b-grade">السنة الدراسية</label>
            <select id="b-grade" className="input" value={form.grade} onChange={set('grade')}>
              <option value="">—</option>
              {SCHOOL_YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <div><label className="label" htmlFor="b-price">السعر (ج.م)</label><input id="b-price" type="number" min="0" className="input" value={form.price} onChange={set('price')} /></div>
          <div><label className="label" htmlFor="b-date">ميعاد النزول</label><input id="b-date" type="date" className="input" value={form.releaseDate} onChange={set('releaseDate')} required /></div>
          <div><label className="label" htmlFor="b-stock">عدد النسخ</label><input id="b-stock" type="number" min="0" className="input" value={form.stock} onChange={set('stock')} placeholder="من غير حد" /></div>
        </div>
        <div><label className="label" htmlFor="b-desc">وصف قصير</label><textarea id="b-desc" rows={3} className="input" value={form.description} onChange={set('description')} placeholder="شرح + تمارين + امتحانات على الباب الأول" /></div>
        {book.id && <label className="flex items-center gap-2 text-sm font-bold text-ink-600"><input type="checkbox" checked={form.active} onChange={set('active')} /> متاح للحجز</label>}
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}

function ReserveModal({ book, onClose, onDone }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try { const { data } = await api.post(`/center/books/${book.id}/reservations`, { studentCode: code.trim() }); onDone(data) }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر الحجز')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={`حجز «${book.title}»`}>
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="r-code">كود الطالب (اللي على الكارت)</label><input id="r-code" dir="ltr" className="input text-center font-mono tracking-widest" value={code} onChange={(e) => setCode(e.target.value)} placeholder="1001" required /></div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><Ticket size={16} /> احجز</>}</button>
      </form>
    </Modal>
  )
}
