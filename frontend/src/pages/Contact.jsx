import { useState } from 'react'
import { motion } from 'framer-motion'
import { MessageCircle, Phone, Mail, Send, CheckCircle2 } from 'lucide-react'
import api from '../lib/api'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import PageHero from '../components/marketing/PageHero'
import { Spinner } from '../components/ui'
import { apiErrorMessage } from '../lib/apiError'

const INSTITUTION_TYPES = ['مدرس مستقل', 'أكاديمية أونلاين', 'مركز تدريب', 'مدرسة', 'جامعة', 'أخرى']
const STUDENT_RANGES = ['1 - 50', '50 - 200', '200 - 500', '500 - 1,000', 'أكثر من 1,000']

const CHANNELS = [
  { icon: MessageCircle, title: 'واتساب', value: 'راسلنا على واتساب', href: 'https://wa.me/201000000000', tint: 'bg-emerald-50 text-emerald-600' },
  { icon: Phone, title: 'اتصل بنا', value: '+20 100 000 0000', href: 'tel:+201000000000', tint: 'bg-brand-50 text-brand-600' },
  { icon: Mail, title: 'البريد الإلكتروني', value: 'hello@madarik.com.co', href: 'mailto:hello@madarik.com.co', tint: 'bg-violet-50 text-violet-600' },
]

export default function Contact() {
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', institutionType: '', expectedStudents: '', message: '' })
  const [saving, setSaving] = useState(false)
  const [done, setDone] = useState(false)
  const [err, setErr] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true); setErr('')
    try {
      await api.post('/public/contact', form)
      setDone(true)
    } catch (e) {
      setErr(apiErrorMessage(e, 'تعذّر إرسال رسالتك، حاول مرة أخرى'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <PageHero badge="نحن هنا لمساعدتك" title="تواصل" highlight="معنا" subtitle="جاهزين نساعدك تبدأ أكاديميتك الرقمية — راسلنا وهنرد عليك في أسرع وقت." />

      <section className="mx-auto max-w-6xl px-5 pb-24">
        <div className="grid gap-8 lg:grid-cols-5">
          <div className="space-y-4 lg:col-span-2">
            {CHANNELS.map((c) => (
              <motion.a key={c.title} href={c.href} target="_blank" rel="noreferrer"
                initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
                className="card flex items-center gap-4 p-5 transition hover:shadow-glow">
                <span className={`grid h-12 w-12 shrink-0 place-items-center rounded-2xl ${c.tint}`}><c.icon size={22} /></span>
                <div><p className="font-bold text-ink-800">{c.title}</p><p className="text-sm text-ink-500" dir="ltr">{c.value}</p></div>
              </motion.a>
            ))}
            <div className="card p-5">
              <p className="font-bold text-ink-800">ساعات العمل</p>
              <p className="mt-1.5 text-sm text-ink-500">من الأحد للخميس، 9 صباحاً — 6 مساءً</p>
            </div>
          </div>

          <motion.div initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="card p-7 lg:col-span-3">
            {done ? (
              <div className="flex flex-col items-center py-10 text-center">
                <CheckCircle2 size={56} className="text-emerald-500" />
                <h3 className="mt-4 text-xl font-black text-ink-800">تم استلام رسالتك بنجاح!</h3>
                <p className="mt-2 text-ink-500">سيتواصل معك فريقنا في أقرب وقت ممكن.</p>
              </div>
            ) : (
              <form onSubmit={submit} className="space-y-4">
                <h3 className="text-lg font-extrabold text-ink-800">أرسل رسالتك</h3>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div><label className="label">الاسم الكامل</label><input required className="input" value={form.fullName} onChange={set('fullName')} placeholder="اسمك الكامل" /></div>
                  <div><label className="label">البريد الإلكتروني</label><input required type="email" className="input" value={form.email} onChange={set('email')} placeholder="you@example.com" /></div>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div><label className="label">رقم الموبايل</label><input className="input" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" /></div>
                  <div><label className="label">نوع المؤسسة</label>
                    <select className="input" value={form.institutionType} onChange={set('institutionType')}>
                      <option value="">اختر نوع المؤسسة...</option>
                      {INSTITUTION_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </div>
                </div>
                <div><label className="label">عدد الطلاب المتوقع</label>
                  <select className="input" value={form.expectedStudents} onChange={set('expectedStudents')}>
                    <option value="">اختر...</option>
                    {STUDENT_RANGES.map((r) => <option key={r} value={r}>{r}</option>)}
                  </select>
                </div>
                <div><label className="label">رسالتك</label><textarea className="input" rows={4} value={form.message} onChange={set('message')} placeholder="كيف يمكننا مساعدتك؟" /></div>
                {err && <p className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{err}</p>}
                <button type="submit" disabled={saving} className="btn-primary w-full py-3 text-base">
                  {saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>أرسل رسالتك <Send size={18} /></>}
                </button>
              </form>
            )}
          </motion.div>
        </div>
      </section>

      <MarketingFooter />
    </div>
  )
}
