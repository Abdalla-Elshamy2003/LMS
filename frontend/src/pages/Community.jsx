import { useEffect, useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import { MessagesSquare, Plus, Send, Heart, Pin, MessageCircle, UserRound, ArrowRight } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, Spinner, fadeUp, stagger } from '../components/ui'
import { timeAgo } from '../lib/format'
import { apiErrorMessage } from '../lib/apiError'

export default function Community() {
  const { user } = useAuth()
  const [topics, setTopics] = useState(null)
  const [courses, setCourses] = useState([])
  const [courseFilter, setCourseFilter] = useState('')
  const [openId, setOpenId] = useState(null)
  const [createOpen, setCreateOpen] = useState(false)

  const load = (courseId = courseFilter) => {
    const params = courseId ? { courseId } : {}
    api.get('/forum/topics', { params }).then((r) => setTopics(r.data))
  }
  useEffect(() => { load() }, [courseFilter])
  useEffect(() => { api.get('/courses').then((r) => setCourses(r.data)).catch(() => {}) }, [])

  const toggleLike = async (id) => {
    const { data } = await api.post(`/forum/topics/${id}/like`)
    setTopics((prev) => prev.map((t) => (t.id === id ? data : t)))
  }

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.section variants={fadeUp} className="relative overflow-hidden rounded-[30px] bg-gradient-to-br from-brand-800 to-brand-950 p-6 text-white sm:p-8">
        <MessagesSquare className="absolute -bottom-12 left-4 h-56 w-56 text-white/5" strokeWidth={1} />
        <div className="relative flex flex-wrap items-end justify-between gap-5">
          <div>
            <span className="text-xs font-black text-cyan-300">مجتمع دروس التعليمي</span>
            <h2 className="mt-3 text-2xl font-black sm:text-3xl">اسأل، ناقش، وشارك زملاءك في التعلّم</h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300">منتدى مفتوح لكل الطلاب والمدرسين — اطرح سؤالاً عاماً أو دردش داخل مادة معينة.</p>
          </div>
          <button onClick={() => setCreateOpen(true)} className="rounded-xl bg-cyan-300 px-5 py-3 text-sm font-black text-slate-900 transition hover:bg-cyan-200">
            <Plus size={17} className="ml-2 inline" />موضوع جديد
          </button>
        </div>
      </motion.section>

      <div className="flex flex-wrap gap-2">
        <button onClick={() => setCourseFilter('')} className={`chip border ${!courseFilter ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>كل المواضيع</button>
        {courses.map((c) => (
          <button key={c.id} onClick={() => setCourseFilter(String(c.id))} className={`chip border ${courseFilter === String(c.id) ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>{c.title}</button>
        ))}
      </div>

      {!topics ? <PageLoader /> : topics.length === 0 ? (
        <div className="card"><EmptyState icon={MessagesSquare} title="لا توجد مواضيع بعد" hint="ابدأ أول نقاش في هذا القسم." /></div>
      ) : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
          {topics.map((t) => (
            <motion.div variants={fadeUp} key={t.id} className="card cursor-pointer p-5 transition hover:border-brand-200" onClick={() => setOpenId(t.id)}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    {t.pinned && <span className="chip bg-amber-50 text-amber-700"><Pin size={12} className="ml-1 inline" />مثبّت</span>}
                    {t.courseTitle && <span className="chip bg-brand-50 text-brand-700">{t.courseTitle}</span>}
                  </div>
                  <p className="mt-2 font-extrabold text-ink-800">{t.title}</p>
                  <p className="mt-1 line-clamp-2 text-sm text-ink-500">{t.body}</p>
                  <p className="mt-2 text-xs text-ink-400"><UserRound size={12} className="ml-1 inline" />{t.authorName} · {timeAgo(t.lastActivityAt)}</p>
                </div>
                <div className="flex shrink-0 flex-col items-center gap-2 text-ink-400">
                  <button onClick={(e) => { e.stopPropagation(); toggleLike(t.id) }} className={`flex items-center gap-1 rounded-xl px-2.5 py-1.5 text-xs font-bold transition ${t.likedByMe ? 'bg-rose-50 text-rose-600' : 'bg-ink-50 text-ink-500 hover:bg-rose-50 hover:text-rose-600'}`}>
                    <Heart size={14} fill={t.likedByMe ? 'currentColor' : 'none'} />{t.likeCount}
                  </button>
                  <span className="flex items-center gap-1 text-xs font-bold"><MessageCircle size={14} />{t.replyCount}</span>
                </div>
              </div>
            </motion.div>
          ))}
        </motion.div>
      )}

      {openId && <TopicModal id={openId} user={user} onClose={() => setOpenId(null)} onChanged={() => load()} />}
      {createOpen && <CreateTopic courses={courses} onClose={() => setCreateOpen(false)} onSaved={(t) => { setCreateOpen(false); load(); setOpenId(t.id) }} />}
    </motion.div>
  )
}

function TopicModal({ id, user, onClose, onChanged }) {
  const [detail, setDetail] = useState(null)
  const [reply, setReply] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = () => api.get(`/forum/topics/${id}`).then((r) => setDetail(r.data))
  useEffect(() => { load() }, [id])

  const send = async () => {
    if (!reply.trim()) return
    setSaving(true); setError('')
    try { await api.post(`/forum/topics/${id}/replies`, { body: reply }); setReply(''); await load(); onChanged() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر إرسال الرد')) }
    finally { setSaving(false) }
  }
  const toggleLike = async () => {
    const { data } = await api.post(`/forum/topics/${id}/like`)
    setDetail((d) => ({ ...d, topic: data }))
    onChanged()
  }

  if (!detail) return <Modal open onClose={onClose} title="جاري التحميل..." wide><PageLoader /></Modal>
  const { topic, replies } = detail
  return (
    <Modal open onClose={onClose} title={topic.title} wide>
      <div className="space-y-4">
        <div className="rounded-2xl bg-ink-50 p-4">
          <p className="text-xs font-bold text-ink-400"><UserRound size={12} className="ml-1 inline" />{topic.authorName} · {timeAgo(topic.createdAt)}{topic.courseTitle && ` · ${topic.courseTitle}`}</p>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-ink-700">{topic.body}</p>
          <button onClick={toggleLike} className={`mt-3 flex items-center gap-1 rounded-xl px-3 py-1.5 text-xs font-bold transition ${topic.likedByMe ? 'bg-rose-50 text-rose-600' : 'bg-white text-ink-500 hover:bg-rose-50 hover:text-rose-600'}`}>
            <Heart size={14} fill={topic.likedByMe ? 'currentColor' : 'none'} />{topic.likeCount} إعجاب
          </button>
        </div>

        <div className="max-h-[320px] space-y-3 overflow-y-auto">
          {replies.length === 0 && <p className="py-4 text-center text-sm text-ink-400">لا توجد ردود بعد — كن أول من يرد</p>}
          {replies.map((r) => (
            <div key={r.id} className={`flex ${r.authorUserId === user.id ? 'justify-start' : 'justify-end'}`}>
              <div className={`max-w-[86%] rounded-2xl px-4 py-3 ${r.authorUserId === user.id ? 'rounded-tr-sm bg-brand-600 text-white' : 'rounded-tl-sm border border-ink-100 bg-white text-ink-700'}`}>
                <div className={`mb-1 flex items-center gap-2 text-[10px] ${r.authorUserId === user.id ? 'text-brand-100' : 'text-ink-400'}`}><UserRound size={11} />{r.authorName} · {timeAgo(r.createdAt)}</div>
                <p className="whitespace-pre-wrap text-sm leading-7">{r.body}</p>
              </div>
            </div>
          ))}
        </div>

        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <div className="flex items-end gap-2 border-t border-ink-100 pt-4">
          <textarea aria-label="اكتب ردك" value={reply} onChange={(e) => setReply(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }} className="input min-h-[48px] flex-1 resize-none" rows={2} placeholder="اكتب ردك هنا..." />
          <button disabled={saving || !reply.trim()} onClick={send} className="btn-primary h-12 px-4">
            {saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Send size={17} />}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function CreateTopic({ courses, onClose, onSaved }) {
  const [form, setForm] = useState({ courseId: '', title: '', body: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const save = async () => {
    setSaving(true); setError('')
    try {
      const { data } = await api.post('/forum/topics', { ...form, courseId: form.courseId ? Number(form.courseId) : null })
      onSaved(data)
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر إنشاء الموضوع')) }
    finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title="موضوع نقاش جديد" wide>
      <div className="space-y-4">
        <div><label className="label">المادة (اختياري)</label><select className="input" value={form.courseId} onChange={(e) => setForm({ ...form, courseId: e.target.value })}><option value="">موضوع عام لكل المجتمع</option>{courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}</select></div>
        <div><label className="label">عنوان الموضوع</label><input className="input" maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="مثال: أفضل طريقة لمذاكرة الوحدة الثالثة؟" /></div>
        <div><label className="label">التفاصيل</label><textarea className="input" rows={5} maxLength={4000} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} placeholder="اشرح سؤالك أو موضوعك بالتفصيل..." /></div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || form.title.trim().length < 3 || form.body.trim().length < 3} className="btn-primary">
            {saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <ArrowRight size={16} />}نشر الموضوع
          </button>
        </div>
      </div>
    </Modal>
  )
}
