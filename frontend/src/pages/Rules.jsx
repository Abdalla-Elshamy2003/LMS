import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { SlidersHorizontal, Plus, Zap, Pencil } from 'lucide-react'
import api from '../lib/api'
import { Modal, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'

const TRIGGERS = {
  STUDENT_ABSENT: 'عند غياب الطالب',
  LOW_SCORE: 'عند انخفاض الدرجة',
  HOMEWORK_MISSED: 'عند عدم تسليم الواجب',
  RISK_ESCALATED: 'عند التراجع الدراسي',
  INSTALLMENT_DUE: 'عند استحقاق قسط',
}

const PLACEHOLDERS = {
  STUDENT_ABSENT: '{{name}}, {{course}}',
  LOW_SCORE: '{{name}}, {{title}}, {{percent}}',
  HOMEWORK_MISSED: '{{name}}, {{title}}, {{count}}',
  RISK_ESCALATED: '{{name}}, {{level}}, {{reasons}}',
  INSTALLMENT_DUE: '{{name}}, {{amount}}, {{dueDate}}',
}

export default function Rules() {
  const [rules, setRules] = useState(null)
  const [showNew, setShowNew] = useState(false)
  const [editing, setEditing] = useState(null)
  const load = () => api.get('/notifications/rules').then((r) => setRules(r.data))
  useEffect(() => { load() }, [])

  const toggle = async (id) => { await api.post(`/notifications/rules/${id}/toggle`); load() }
  if (!rules) return <PageLoader />

  return (
    <div className="space-y-5">
      <div className="card bg-gradient-to-l from-brand-50 to-white p-5">
        <div className="flex items-center gap-3">
          <div className="grid h-11 w-11 place-items-center rounded-2xl bg-brand-100 text-brand-600"><Zap size={20} /></div>
          <div className="flex-1"><p className="font-extrabold text-ink-800">محرّك التنبيهات الذكي</p><p className="text-sm text-ink-500">إذا حدث شرط معيّن → أرسل تنبيهاً تلقائياً لولي الأمر أو المدرس</p></div>
          <button onClick={() => setShowNew(true)} className="btn-primary"><Plus size={16} /> قاعدة جديدة</button>
        </div>
      </div>

      {rules.length === 0 ? <div className="card"><EmptyState icon={SlidersHorizontal} title="لا توجد قواعد" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
          {rules.map((r) => (
            <motion.div variants={fadeUp} key={r.id} className="card flex items-center gap-4 p-5">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="font-bold text-ink-800">{r.name}</p>
                  <button onClick={() => setEditing(r)} className="text-ink-300 hover:text-brand-600"><Pencil size={14} /></button>
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs">
                  <span className="chip bg-amber-50 text-amber-700">{TRIGGERS[r.triggerType] || r.triggerType}</span>
                  {r.threshold != null && <span className="chip bg-ink-100 text-ink-600">الحد: {r.threshold}</span>}
                  {r.channels.split(',').map((c) => <span key={c} className="chip bg-brand-50 text-brand-700">{c}</span>)}
                  {r.notifyParent && <span className="chip bg-emerald-50 text-emerald-700">ولي الأمر</span>}
                  {r.notifyTeacher && <span className="chip bg-sky-50 text-sky-700">المدرس</span>}
                </div>
                {r.template && <p className="mt-2 truncate rounded-xl bg-ink-50 px-3 py-1.5 text-xs text-ink-500">{r.template}</p>}
              </div>
              <button onClick={() => toggle(r.id)} className={`relative h-7 w-12 shrink-0 rounded-full transition ${r.active ? 'bg-brand-500' : 'bg-ink-200'}`}>
                <motion.span layout className="absolute top-1 h-5 w-5 rounded-full bg-white shadow" style={{ [r.active ? 'left' : 'right']: 4 }} />
              </button>
            </motion.div>
          ))}
        </motion.div>
      )}

      {showNew && <RuleModal onClose={() => setShowNew(false)} onSaved={() => { setShowNew(false); load() }} />}
      {editing && <RuleModal rule={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load() }} />}
    </div>
  )
}

function RuleModal({ rule, onClose, onSaved }) {
  const isEdit = !!rule
  const [form, setForm] = useState(rule
    ? { name: rule.name, triggerType: rule.triggerType, threshold: rule.threshold ?? '', channels: rule.channels, notifyParent: rule.notifyParent, notifyTeacher: rule.notifyTeacher, active: rule.active, template: rule.template || '' }
    : { name: '', triggerType: 'STUDENT_ABSENT', threshold: '', channels: 'IN_APP,WHATSAPP', notifyParent: true, notifyTeacher: false, active: true, template: '' })
  const [saving, setSaving] = useState(false)
  const save = async () => {
    setSaving(true)
    const payload = { ...form, threshold: form.threshold === '' ? null : Number(form.threshold), template: form.template || null }
    try {
      if (isEdit) await api.put(`/notifications/rules/${rule.id}`, payload)
      else await api.post('/notifications/rules', payload)
      onSaved()
    } finally { setSaving(false) }
  }
  return (
    <Modal open onClose={onClose} title={isEdit ? 'تعديل القاعدة' : 'قاعدة تنبيه جديدة'} wide>
      <div className="space-y-4">
        <div><label className="label">اسم القاعدة</label><input className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="مثال: تنبيه الغياب" /></div>
        <div><label className="label">الشرط (المُشغّل)</label>
          <select className="input" value={form.triggerType} onChange={(e) => setForm({ ...form, triggerType: e.target.value })}>
            {Object.entries(TRIGGERS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">الحد (اختياري)</label><input type="number" className="input" value={form.threshold} onChange={(e) => setForm({ ...form, threshold: e.target.value })} placeholder="مثال: 50" /></div>
          <div><label className="label">القنوات</label><input className="input" value={form.channels} onChange={(e) => setForm({ ...form, channels: e.target.value })} /></div>
        </div>
        <div className="flex gap-4">
          <label className="flex items-center gap-2 text-sm font-semibold text-ink-600"><input type="checkbox" checked={form.notifyParent} onChange={(e) => setForm({ ...form, notifyParent: e.target.checked })} /> إخطار ولي الأمر</label>
          <label className="flex items-center gap-2 text-sm font-semibold text-ink-600"><input type="checkbox" checked={form.notifyTeacher} onChange={(e) => setForm({ ...form, notifyTeacher: e.target.checked })} /> إخطار المدرس</label>
        </div>
        <div>
          <label className="label">نص الرسالة المخصّص (اختياري)</label>
          <textarea className="input" rows={3} value={form.template} onChange={(e) => setForm({ ...form, template: e.target.value })}
            placeholder={`اتركه فارغاً لاستخدام النص الافتراضي. متغيرات متاحة: ${PLACEHOLDERS[form.triggerType] || '{{name}}'}`} />
          <p className="mt-1.5 text-[11px] text-ink-400">المتغيرات المتاحة لهذا الشرط: <span className="font-mono">{PLACEHOLDERS[form.triggerType] || '{{name}}'}</span></p>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || !form.name} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ القاعدة'}</button>
        </div>
      </div>
    </Modal>
  )
}
