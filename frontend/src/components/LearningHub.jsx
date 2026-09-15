import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { BookOpen, Play, Video, Files, ArrowLeft, Search, UploadCloud, CheckCircle2, Layers, Sparkles } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { courseHref } from '../lib/courseNavigation'
import { EmptyState, PageLoader, ProgressBar, fadeUp } from './ui'

export default function LearningHub({ compact = false }) {
  const { user } = useAuth()
  const student = user.role === 'STUDENT'
  const [courses, setCourses] = useState(null)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('all')
  const load = () => { setError(''); api.get('/learning').then(r => setCourses(r.data)).catch(() => setError('تعذّر تحميل مكتبتك. حاول مرة أخرى.')) }
  useEffect(load, [])
  if (error) return <div className="card p-6" role="alert">{error}<button className="btn-soft mr-3" onClick={load}>إعادة المحاولة</button></div>
  if (!courses) return <PageLoader />
  const filtered = courses.filter(c => `${c.summary.title} ${c.summary.teacherName || ''}`.includes(query) && (filter === 'all' || (filter === 'videos' ? c.videoCount > 0 : c.fileCount > 0)))
  const totals = courses.reduce((a, c) => ({ lessons: a.lessons + c.lessonCount, videos: a.videos + c.videoCount, files: a.files + c.fileCount, done: a.done + c.completedCount }), { lessons: 0, videos: 0, files: 0, done: 0 })
  const next = courses.find(c => c.nextLessonId)
  return <motion.section variants={fadeUp} className="space-y-6">
    <div className="learning-banner relative overflow-hidden rounded-[28px] p-6 text-white sm:p-8">
      <div className="relative z-10 max-w-2xl">
        <span className="inline-flex items-center gap-2 text-xs text-cyan-200"><Sparkles size={15} /> {student ? 'خطوة جديدة نحو مستقبلك' : 'مساحة واحدة لكل رحلة التعلّم'}</span>
        <h2 className="mt-3 text-2xl font-black sm:text-3xl">{student ? 'كل درس، يقرّبك من حلمك.' : user.role === 'TEACHER' ? 'ألهم طلابك. واصنع درسهم القادم.' : 'المحتوى، المدرسون، والطلاب. صورة كاملة.'}</h2>
        <p className="mt-3 max-w-xl text-sm leading-7 text-slate-300">{student ? 'شاهد شرح مدرسك، حمّل ملفات الدروس، وكمّل رحلتك من المكان اللي وقفت عنده.' : 'نظّم الفصول، أضف الفيديوهات وملفات الشرح، وتابع الدروس التي أكملها كل طالب.'}</p>
        <Link to={student && next ? `/app/courses/${next.summary.id}?lesson=${next.nextLessonId}` : '/app/courses'} className="mt-5 inline-flex items-center gap-2 rounded-xl bg-lime-300 px-5 py-3 text-sm font-extrabold text-slate-900 hover:bg-lime-200 transition">{student ? <Play size={16} /> : <UploadCloud size={18} />}{student ? next ? 'كمّل تعلّمك' : 'استكشف الكورسات' : 'إدارة الكورسات'}<ArrowLeft size={16} /></Link>
      </div>
      <BookOpen className="banner-book absolute -bottom-8 left-4 h-52 w-52 rotate-12 text-white/[0.06]" strokeWidth={1} />
    </div>
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {[[BookOpen, courses.length, student ? 'كورساتي' : 'الكورسات'], [Video, totals.videos, 'فيديو تعليمي'], [Files, totals.files, 'ملف ومصدر'], [student ? CheckCircle2 : Layers, student ? totals.done : totals.lessons, student ? 'درس مكتمل' : 'درس منظّم']].map(([Icon, n, label]) => <div key={label} className="card flex items-center gap-3 p-4"><span className="rounded-xl bg-brand-50 p-2.5 text-brand-600"><Icon size={20} /></span><div><p className="text-xl font-black text-ink-800">{n}</p><p className="text-xs text-ink-500">{label}</p></div></div>)}
    </div>
    <div className="flex flex-wrap items-center justify-between gap-4">
      <div><h3 className="text-xl font-extrabold">{student ? 'مكتبة تعلّمك' : 'استوديو المحتوى'}</h3><p className="mt-1 text-xs text-ink-500">{student ? 'الشرح والملفات، مرتّبة كورس بكورس' : 'اختر كورساً لإدارة دروسه ومتابعة طلابه'}</p></div>
      {compact ? <Link to="/app/learning" className="btn-soft">المكتبة كاملة <ArrowLeft size={15} /></Link> : <div className="relative w-full sm:w-72"><Search size={17} className="absolute right-3 top-3 text-ink-400" /><input aria-label="ابحث في مكتبتك" className="input pr-10" placeholder="ابحث عن كورس أو مدرس..." value={query} onChange={e => setQuery(e.target.value)} /></div>}
    </div>
    {!compact && <div className="flex gap-2">{[['all', 'كل المحتوى'], ['videos', 'فيه فيديوهات'], ['files', 'فيه ملفات']].map(([key, label]) => <button key={key} onClick={() => setFilter(key)} className={filter === key ? 'btn-primary' : 'btn-ghost'}>{label}</button>)}</div>}
    {filtered.length === 0 ? <div className="card"><EmptyState icon={BookOpen} title={courses.length ? 'لا توجد نتائج مطابقة' : student ? 'رحلتك لسه بتبدأ' : 'ابدأ بأول كورس'} hint={student ? 'الكورسات المسجّل فيها هتظهر هنا مع دروس وملفات مدرسك.' : 'أضف كورساً ثم أنشئ فصوله ودروسه من صفحة الكورسات.'} /><div className="pb-6 text-center"><Link to="/app/courses" className="btn-soft">تصفّح الكورسات <ArrowLeft size={15} /></Link></div></div> : <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
      {(compact ? filtered.slice(0, 3) : filtered).map((c, i) => <motion.div key={c.summary.id} initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * .06 }} className="course-tile card overflow-hidden">
        <Link to={courseHref(c.summary, user.role, student ? c.nextLessonId : null)} className="block h-full">
          <div className={`course-cover cover-${i % 4} relative flex h-40 flex-col justify-between overflow-hidden p-5 text-white`}>
            <div className="flex items-center justify-between"><span className="chip bg-white/15">{c.summary.subject || 'مسار تعليمي'}</span><span className="rounded-full bg-white/15 p-2"><Play size={16} fill="currentColor" /></span></div>
            <BookOpen className="absolute -bottom-8 -left-2 h-36 w-36 -rotate-12 text-white/10" />
            <p className="relative text-lg font-extrabold">{c.summary.title}</p>
          </div>
          <div className="p-5"><p className="text-xs text-ink-500">{c.summary.teacherName || 'فريق منارة'} · {c.summary.gradeLevel || 'كل المستويات'}</p><div className="my-4 flex flex-wrap gap-4 text-xs text-ink-600"><span className="inline-flex items-center gap-1"><Video size={14} /> {c.videoCount} فيديو</span><span className="inline-flex items-center gap-1"><Files size={14} /> {c.fileCount} ملف</span><span>{c.lessonCount} درس</span></div>
            {student ? <><div className="mb-2 flex justify-between text-xs"><span>تقدّمك في الكورس</span><b>{c.lessonCount ? Math.round(c.completedCount / c.lessonCount * 100) : 0}٪</b></div><ProgressBar value={c.lessonCount ? c.completedCount / c.lessonCount * 100 : 0} /><p className="mt-4 truncate text-xs text-ink-500">{c.nextLessonTitle ? `التالي: ${c.nextLessonTitle}` : c.lessonCount ? 'أكملت كل دروس الكورس 🎉' : 'المدرس بيجهّز محتوى الكورس'}</p></> : <p className="text-xs text-ink-500">{c.summary.studentCount} طالب مسجّل · {c.lessonCount ? 'المحتوى متاح للطلاب' : 'بانتظار إضافة الدروس'}</p>}
            <div className="mt-4 flex items-center justify-between border-t border-ink-100 pt-4 text-sm font-bold text-brand-700"><span>{student ? 'افتح مساحة الدرس' : 'إدارة المحتوى والمتابعة'}</span><ArrowLeft size={17} /></div>
          </div>
        </Link>
      </motion.div>)}
    </div>}
  </motion.section>
}
