import { ArrowDown, ArrowRight, ArrowUp, BookOpen, CheckCircle2, ChevronLeft, Clock, Files, Layers, Pencil, Play, Plus, Trash2 } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { fmtDate } from '../../lib/format'
import ActionMenu from '../../components/ActionMenu'
import { EmptyState, ProgressBar } from '../../components/ui'

/**
 * A course, one step at a time: its units (e.g. «الجبر», «الهندسة»), then a unit's lessons, then a lesson (shown by
 * LessonWorkspace). Staff build it here too: add, rename, delete and reorder units and the lessons inside them.
 */

const ordinal = (n) => n.toLocaleString('ar-EG')
const minutes = (lessons) => lessons.reduce((sum, l) => sum + (Number(l.durationMin) || 0), 0)
const doneIn = (lessons, progress) => lessons.filter(l => progress.some(p => p.lessonId === l.id && p.completed)).length
const upcoming = (l) => l.releaseAt && new Date(l.releaseAt) > new Date()

/** Swaps an item with its neighbour and saves the new order. */
async function move(url, items, index, step, onDone, onError) {
  const target = index + step
  if (target < 0 || target >= items.length) return
  const ids = items.map(i => i.id)
  ;[ids[index], ids[target]] = [ids[target], ids[index]]
  try { await api.put(url, { ids }); onDone() }
  catch (e) { onError(apiErrorMessage(e, 'تعذّر تغيير الترتيب')) }
}

export function UnitsOverview({ course, progress, staff, student, resume, onOpenUnit, onOpenLesson, onModal, onChanged, onError }) {
  const units = course.modules
  if (!units.length) return (
    <div className="card">
      <EmptyState icon={Layers} title="لسه مفيش وحدات" hint={staff ? 'ابدأ بإضافة أول وحدة — مثلاً «الوحدة الأولى: الجبر» — وبعدين ضيف دروسها جواها.' : 'المدرس لسه بيجهّز محتوى الكورس.'} />
    </div>
  )
  return (
    <div className="space-y-4">
      {student && resume && (
        <button type="button" onClick={() => onOpenLesson(resume)}
          className="card flex w-full items-center gap-4 p-4 text-right transition hover:shadow-glow">
          <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-brand-600 text-white"><Play size={20} /></span>
          <span className="min-w-0 flex-1"><small className="block text-xs text-ink-400">كمّل من حيث وقفت</small><b className="block truncate text-ink-800">{resume.title}</b><small className="text-xs text-ink-400">{resume.moduleTitle}</small></span>
          <ChevronLeft size={20} className="text-brand-600" />
        </button>
      )}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {units.map((m, i) => {
          const done = doneIn(m.lessons, progress)
          return (
            <div key={m.id} className="card relative overflow-hidden transition hover:shadow-glow">
              <button type="button" onClick={() => onOpenUnit(m.id)} className="block w-full p-5 text-right">
                <span className="chip bg-brand-50 text-brand-700">الوحدة {ordinal(i + 1)}</span>
                <b className="mt-3 block text-lg font-black leading-snug text-ink-800">{m.title}</b>
                <span className="mt-2 flex flex-wrap items-center gap-3 text-xs text-ink-500">
                  <span className="inline-flex items-center gap-1"><BookOpen size={13} /> {ordinal(m.lessons.length)} درس</span>
                  {minutes(m.lessons) > 0 && <span className="inline-flex items-center gap-1"><Clock size={13} /> {ordinal(minutes(m.lessons))} دقيقة</span>}
                </span>
                {student && m.lessons.length > 0 && (
                  <span className="mt-4 block">
                    <ProgressBar value={(done / m.lessons.length) * 100} />
                    <small className="mt-1 block text-xs text-ink-400">{ordinal(done)} من {ordinal(m.lessons.length)} خلصتهم</small>
                  </span>
                )}
                <span className="mt-4 inline-flex items-center gap-1 text-sm font-bold text-brand-700">ادخل الوحدة <ChevronLeft size={16} /></span>
              </button>
              {staff && (
                <span className="absolute left-3 top-3">
                  <ActionMenu label={`خيارات ${m.title}`} items={[
                    { label: 'تعديل الاسم', icon: Pencil, onClick: () => onModal({ type: 'editModule', module: m }) },
                    { label: 'تقديمها', icon: ArrowUp, hidden: i === 0, onClick: () => move(`/courses/${course.summary.id}/modules/order`, units, i, -1, onChanged, onError) },
                    { label: 'تأخيرها', icon: ArrowDown, hidden: i === units.length - 1, onClick: () => move(`/courses/${course.summary.id}/modules/order`, units, i, 1, onChanged, onError) },
                    { label: 'حذف الوحدة', icon: Trash2, danger: true, onClick: () => onModal({ type: 'deleteModule', module: m }) },
                  ]} />
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export function UnitLessons({ unit, index, progress, staff, student, onBack, onOpenLesson, onModal, onChanged, onError }) {
  const lessons = unit.lessons
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <button type="button" onClick={onBack} className="inline-flex items-center gap-1.5 text-sm font-bold text-ink-500 hover:text-brand-700"><ArrowRight size={16} /> كل الوحدات</button>
          <h3 className="mt-2 text-xl font-black text-ink-800"><span className="text-brand-600">الوحدة {ordinal(index + 1)}:</span> {unit.title}</h3>
          <p className="mt-1 text-sm text-ink-500">{ordinal(lessons.length)} درس{minutes(lessons) > 0 ? ` · ${ordinal(minutes(lessons))} دقيقة` : ''}{student && lessons.length ? ` · خلصت ${ordinal(doneIn(lessons, progress))}` : ''}</p>
        </div>
        {staff && <button type="button" className="btn-primary" onClick={() => onModal({ type: 'lesson', moduleId: unit.id })}><Plus size={17} /> درس جديد في الوحدة</button>}
      </div>
      {!lessons.length ? (
        <div className="card"><EmptyState icon={BookOpen} title="لسه مفيش دروس في الوحدة دي" hint={staff ? 'ضيف أول درس، وبعدين ارفع فيديوهاته وملفاته من جوه الدرس.' : 'الدروس قيد التجهيز.'} /></div>
      ) : (
        <div className="card divide-y divide-ink-100 p-0">
          {lessons.map((l, j) => {
            const done = progress.some(p => p.lessonId === l.id && p.completed)
            return (
              <div key={l.id} className="flex items-center gap-2 pl-3">
                <button type="button" onClick={() => onOpenLesson(l)} className="flex min-w-0 flex-1 items-center gap-4 p-4 text-right transition hover:bg-ink-50/60">
                  <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl text-sm font-black ${done ? 'bg-emerald-50 text-emerald-600' : 'bg-brand-50 text-brand-700'}`}>
                    {done ? <CheckCircle2 size={19} /> : ordinal(j + 1)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <b className="block truncate text-ink-800">{l.title}</b>
                    <small className="mt-0.5 flex flex-wrap gap-x-3 text-xs text-ink-400">
                      {l.durationMin > 0 && <span className="inline-flex items-center gap-1"><Clock size={12} /> {ordinal(l.durationMin)} دقيقة</span>}
                      <span className="inline-flex items-center gap-1"><Files size={12} /> {ordinal(l.materials.length)} ملف</span>
                      {staff && upcoming(l) && <span className="font-bold text-amber-600">هيتفتح {fmtDate(l.releaseAt)}</span>}
                    </small>
                  </span>
                  <ChevronLeft size={18} className="shrink-0 text-ink-300" />
                </button>
                {staff && (
                  <ActionMenu label={`خيارات ${l.title}`} items={[
                    { label: 'تعديل الدرس', icon: Pencil, onClick: () => onModal({ type: 'editLesson', lesson: l }) },
                    { label: 'تقديمه', icon: ArrowUp, hidden: j === 0, onClick: () => move(`/courses/modules/${unit.id}/lessons/order`, lessons, j, -1, onChanged, onError) },
                    { label: 'تأخيره', icon: ArrowDown, hidden: j === lessons.length - 1, onClick: () => move(`/courses/modules/${unit.id}/lessons/order`, lessons, j, 1, onChanged, onError) },
                    { label: 'حذف الدرس', icon: Trash2, danger: true, onClick: () => onModal({ type: 'deleteLesson', lesson: l }) },
                  ]} />
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
