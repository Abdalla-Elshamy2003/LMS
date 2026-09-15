import ProtectedVideo from "../features/assessment/ProtectedVideo";
import { LessonQuizOverlay, LessonQuizManager } from "../features/assessment/LessonQuiz";
import { useEffect, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import {
  ArrowRight,
  Play,
  Video,
  BookOpen,
  Files,
  Plus,
  CheckCircle2,
  ChevronDown,
  Download,
  ExternalLink,
  Users,
  Clock,
  UploadCloud,
  Eye,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import api, { fileUrl } from "../lib/api";
import { useAuth } from "../lib/auth";
import { PageLoader, EmptyState, ProgressBar } from "../components/ui";
import { fmtDate } from "../lib/format";
import { AddModule, AddLesson, AddMaterials } from "./CourseDetail";

function mediaUrl(material) {
  if (material?.fileKey) return fileUrl(material.fileKey);
  try {
    const u = new URL(material?.url);
    return ["https:", "http:"].includes(u.protocol) ? u.href : "";
  } catch {
    return "";
  }
}
function youtubeUrl(url) {
  try {
    const u = new URL(url);
    const id =
      u.hostname === "youtu.be"
        ? u.pathname.slice(1)
        : ["youtube.com", "www.youtube.com", "m.youtube.com"].includes(
              u.hostname,
            )
          ? u.searchParams.get("v") || u.pathname.split("/embed/")[1]
          : null;
    return /^[\w-]{11}$/.test(id || "")
      ? `https://www.youtube-nocookie.com/embed/${id}`
      : null;
  } catch {
    return null;
  }
}

function LessonPreview({ lesson, course, videos, materials, onStart }) {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.985 }}
      animate={{ opacity: 1, scale: 1 }}
      className="absolute inset-0 overflow-y-auto bg-[radial-gradient(circle_at_top_right,_rgba(34,211,238,.2),_transparent_38%),linear-gradient(135deg,#0f172a,#172554)] p-6 sm:p-9"
    >
      <div className="mx-auto flex min-h-full max-w-3xl flex-col justify-center">
        <span className="mb-4 inline-flex w-fit items-center gap-2 rounded-full border border-cyan-300/20 bg-cyan-300/10 px-3 py-1.5 text-xs font-bold text-cyan-200">
          <Eye size={15} /> قبل ما تبدأ الفيديو
        </span>
        <p className="text-xs font-bold text-cyan-300">
          {lesson.moduleTitle} · {course.summary.subject}
        </p>
        <h3 className="mt-2 text-2xl font-black sm:text-3xl">{lesson.title}</h3>
        <p className="mt-4 max-w-2xl whitespace-pre-wrap text-sm leading-7 text-slate-300">
          {lesson.contentText ||
            course.description ||
            "راجع عنوان الدرس والمواد المرفقة، وجهّز ملاحظاتك قبل بدء الشرح."}
        </p>
        <div className="mt-6 grid gap-3 sm:grid-cols-3">
          <span className="rounded-2xl border border-white/10 bg-white/5 p-3 text-sm">
            <Clock className="mb-2 text-cyan-300" size={18} />
            <b className="block">{lesson.durationMin} دقيقة</b>
            <small className="text-slate-400">مدة الدرس المتوقعة</small>
          </span>
          <span className="rounded-2xl border border-white/10 bg-white/5 p-3 text-sm">
            <Video className="mb-2 text-violet-300" size={18} />
            <b className="block">{videos.length} فيديو</b>
            <small className="text-slate-400">شرح متاح للمشاهدة</small>
          </span>
          <span className="rounded-2xl border border-white/10 bg-white/5 p-3 text-sm">
            <Files className="mb-2 text-emerald-300" size={18} />
            <b className="block">
              {materials.filter((m) => m.type !== "VIDEO").length} ملف
            </b>
            <small className="text-slate-400">للمراجعة والتطبيق</small>
          </span>
        </div>
        <div className="mt-6 flex flex-wrap items-center gap-3">
          {videos.length > 0 && (
            <button onClick={onStart} className="btn-primary">
              <Play size={18} /> ابدأ المشاهدة
            </button>
          )}
          <span className="inline-flex items-center gap-2 text-xs text-slate-400">
            <ShieldCheck size={15} className="text-emerald-300" /> الدخول للدرس
            متاح للمشتركين في الكورس من حساباتهم فقط
          </span>
        </div>
      </div>
    </motion.div>
  );
}

export default function LessonWorkspace() {
  const { id } = useParams();
  const { user } = useAuth();
  const student = user.role === "STUDENT";
  const staff = [
    "SUPER_ADMIN",
    "BRANCH_ADMIN",
    "ACADEMIC_MANAGER",
    "TEACHER",
    "CONTENT_MANAGER",
  ].includes(user.role);
  const [params, setParams] = useSearchParams();
  const [course, setCourse] = useState(null);
  const [progress, setProgress] = useState([]);
  const [learners, setLearners] = useState([]);
  const [watchLog, setWatchLog] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [modal, setModal] = useState(null);
  const [tab, setTab] = useState("about");
  const [materialId, setMaterialId] = useState(null);
  const [collapsed, setCollapsed] = useState({});
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(student);
  const [videoEndedAt, setVideoEndedAt] = useState(null);
  const [summarizing, setSummarizing] = useState(false);
  const [summaryNotes, setSummaryNotes] = useState("");
  const [showNotes, setShowNotes] = useState(false);
  const lastSaved = useRef(0);
  const video = useRef(null);
  const saveQueue = useRef(Promise.resolve());
  const load = async () => {
    setError("");
    try {
      const requestedAcademy = params.get('academy');
      if (requestedAcademy && ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'].includes(user.role)) {
        const { data } = await api.get('/academies');
        const academy = data.find(a => String(a.id) === requestedAcademy);
        if (!academy) throw new Error('غير مسموح بفتح هذه المساحة');
        const current = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null');
        if (String(current?.id) !== requestedAcademy) {
          sessionStorage.setItem('manarah_academy', JSON.stringify({ id: academy.id, name: academy.name, slug: academy.slug }));
          const target = new URL(window.location.href);
          target.searchParams.delete('academy');
          window.location.replace(target.pathname + target.search);
          return;
        }
      }
      const [c, p] = await Promise.all([
        api.get(`/courses/${id}`),
        student || staff
          ? api.get(
              `/learning/courses/${id}/${student ? "progress" : "learners"}`,
            )
          : Promise.resolve({ data: [] }),
      ]);
      setCourse(c.data);
      student ? setProgress(p.data) : setLearners(p.data);
      if (staff) api.get(`/learning/courses/${id}/watch-log`).then((r) => setWatchLog(r.data)).catch(() => setWatchLog([]));
    } catch (e) {
      setError(
        e.response?.data?.message || "تعذّر تحميل الدروس. حاول مرة أخرى.",
      );
    }
  };
  useEffect(() => {
    setCourse(null);
    load();
  }, [id]);
  const lessons =
    course?.modules.flatMap((m) =>
      m.lessons.map((l) => ({ ...l, moduleTitle: m.title })),
    ) || [];
  const active =
    lessons.find((l) => String(l.id) === params.get("lesson")) || lessons[0];
  const saved = progress.find((p) => p.lessonId === active?.id);
  const materials = active?.materials || [];
  const videos = materials.filter((m) => m.type === "VIDEO" && mediaUrl(m));
  const selected = videos.find((m) => m.id === materialId) || videos[0];
  const src = mediaUrl(selected);
  const embed = youtubeUrl(src);
  const completed = progress.filter((p) => p.completed).length;
  useEffect(() => {
    setMaterialId(null);
    setPreviewing(student);
    lastSaved.current = 0;
    setVideoEndedAt(null);
    setNotice("");
  }, [active?.id, student]);
  const choose = (lesson) => {
    setParams({ lesson: lesson.id });
    setTab("about");
  };
  const save = async (position, done = false) => {
    if (!student || !active) return;
    const lessonId = active.id;
    saveQueue.current = saveQueue.current.then(async () => {
      try {
        const r = await api.put(`/learning/lessons/${lessonId}/progress`, {
          position: Math.min(86400, Math.max(0, Math.floor(position || 0))),
          completed: done,
        });
        setProgress((ps) => [
          ...ps.filter((p) => p.lessonId !== r.data.lessonId),
          r.data,
        ]);
        setNotice("");
      } catch {
        setNotice(
          "تعذّر حفظ التقدّم. اتأكد من الاتصال واضغط حفظ التقدّم للمحاولة مرة أخرى.",
        );
      }
    });
    return saveQueue.current;
  };
  const markDone = async () => {
    setSaving(true);
    await save(video.current?.currentTime ?? saved?.lastPosition ?? 0, true);
    setSaving(false);
  };
  if (error)
    return (
      <div className="card p-8">
        <p role="alert">{error}</p>
        <div className="mt-5 flex gap-3">
          <button onClick={load} className="btn-soft">
            إعادة المحاولة
          </button>
          <Link to="/app/courses" className="btn-ghost">
            العودة للكورسات
          </Link>
        </div>
      </div>
    );
  if (!course) return <PageLoader />;
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/app/learning"
          className="inline-flex items-center gap-2 text-sm text-ink-500"
        >
          <ArrowRight size={16} /> مكتبة التعلّم
        </Link>
        <span className="chip bg-emerald-50 text-emerald-700">
          {student ? "مساحة الطالب" : "استوديو المدرس والإدارة"}
        </span>
      </div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-bold text-brand-600">
            {course.summary.subject} / {course.summary.gradeLevel}
          </p>
          <h2 className="mt-2 text-2xl font-black">{course.summary.title}</h2>
          <p className="mt-2 text-sm text-ink-500">
            {course.summary.teacherName} · {lessons.length} درس ·{" "}
            {course.modules.length} فصل
          </p>
        </div>
        {staff && (
          <button
            className="btn-primary"
            onClick={() => setModal({ type: "module" })}
          >
            <Plus size={17} /> فصل جديد
          </button>
        )}
      </div>
      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-5">
          <div className="overflow-hidden rounded-3xl bg-slate-950 text-white shadow-xl">
            <div className="relative flex aspect-video items-center justify-center">
              {student && previewing && active ? (
                <LessonPreview
                  lesson={active}
                  course={course}
                  videos={videos}
                  materials={materials}
                  onStart={() => setPreviewing(false)}
                />
              ) : selected ? (
                <ProtectedVideo key={selected.id} material={selected} videoRef={video} position={videos.length === 1 ? saved?.lastPosition || 0 : 0}
                  onProgress={pos => { if (student && Math.abs(pos-lastSaved.current)>=15) { lastSaved.current=pos; save(pos); } }}
                  onPause={pos => save(pos)} onEnded={pos => { save(pos, videos.length === 1); setVideoEndedAt(Date.now()); }} />
              ) : (
                <div className="p-7 text-center">
                  <div className="mx-auto mb-5 grid h-20 w-20 place-items-center rounded-3xl bg-white/5 text-cyan-300">
                    <Video size={36} />
                  </div>
                  <h3 className="text-xl font-bold">
                    {active ? "كل المعرفة تبدأ من هنا" : "جهّز أول درس لطلابك"}
                  </h3>
                  <p className="mx-auto mt-3 max-w-sm text-sm leading-7 text-slate-400">
                    {active
                      ? staff
                        ? "أضف فيديو الشرح، أو ابدأ بنص الدرس والملفات المرفقة."
                        : "تابع شرح الدرس والملفات أسفل المساحة. سيظهر فيديو المدرس هنا عند إضافته."
                      : "أضف فصلاً ثم درساً وارفع فيديو الشرح والملفات."}
                  </p>
                  {staff && active && (
                    <button
                      onClick={() =>
                        setModal({ type: "material", lessonId: active.id })
                      }
                      className="mt-5 btn-primary"
                    >
                      <UploadCloud size={17} /> رفع فيديو أو ملفات
                    </button>
                  )}
                </div>
              )}
            </div>
            {selected && (!student || !previewing) && (
              <div className="flex items-center justify-between gap-3 border-t border-white/10 px-5 py-3">
                <span className="text-sm">{selected.title}</span>
                <span className="text-xs text-cyan-200">المشاهدة داخل المنصة</span>
              </div>
            )}
          </div>
          {student && active && !previewing && (
            <LessonQuizOverlay lessonId={active.id} videoRef={video} endedAt={videoEndedAt} />
          )}
          {staff && active && <LessonQuizManager lessonId={active.id} videoRef={video} />}
          {notice && (
            <div
              role="status"
              className="rounded-2xl bg-amber-50 p-4 text-sm text-amber-800"
            >
              {notice}
              {student && (
                <button
                  className="btn-soft mr-2"
                  onClick={() =>
                    save(video.current?.currentTime ?? saved?.lastPosition ?? 0)
                  }
                >
                  حفظ التقدّم
                </button>
              )}
            </div>
          )}
          {videos.length > 1 && (
            <div className="flex flex-wrap gap-2">
              {videos.map((m, i) => (
                <button
                  key={m.id}
                  className={
                    selected?.id === m.id ? "btn-primary" : "btn-ghost"
                  }
                  onClick={() => setMaterialId(m.id)}
                >
                  <Play size={14} /> {i + 1}. {m.title}
                </button>
              ))}
            </div>
          )}
          {active && (
            <div className="card p-5 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <p className="text-xs text-brand-600">{active.moduleTitle}</p>
                  <h3 className="mt-1 text-xl font-extrabold">
                    {active.title}
                  </h3>
                  <span className="mt-2 inline-flex flex-wrap items-center gap-3 text-xs text-ink-500">
                    <span className="inline-flex items-center gap-1.5">
                      <Clock size={13} /> {active.durationMin} دقيقة
                    </span>
                    {active.createdAt && (
                      <span>رُفع بتاريخ {fmtDate(active.createdAt)}</span>
                    )}
                  </span>
                </div>
                {student ? (
                  <button
                    className={saved?.completed ? "btn-soft" : "btn-primary"}
                    disabled={saving || saved?.completed}
                    onClick={markDone}
                  >
                    <CheckCircle2 size={17} />
                    {saved?.completed
                      ? "أكملت هذا الدرس"
                      : saving
                        ? "جارٍ الحفظ..."
                        : "تحديد الدرس كمكتمل"}
                  </button>
                ) : (
                  staff && (
                    <button
                      className="btn-primary"
                      onClick={() =>
                        setModal({ type: "material", lessonId: active.id })
                      }
                    >
                      <Plus size={17} /> فيديو / ملف جديد
                    </button>
                  )
                )}
              </div>
              <div className="mt-6 flex flex-wrap gap-2 border-b border-ink-100 pb-3">
                {[
                  ["about", "عن الدرس", BookOpen],
                  [
                    "files",
                    `ملفات الدرس (${materials.filter((m) => m.type !== "VIDEO").length})`,
                    Files,
                  ],
                  ...(staff
                    ? [
                        [
                          "learners",
                          `متابعة الطلاب (${learners.length})`,
                          Users,
                        ],
                      ]
                    : []),
                ].map(([key, label, Icon]) => (
                  <button
                    key={key}
                    onClick={() => setTab(key)}
                    className={tab === key ? "btn-soft" : "btn-ghost"}
                  >
                    <Icon size={16} />
                    {label}
                  </button>
                ))}
              </div>
              <AnimatePresence mode="wait">
                <motion.div
                  key={tab}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  className="pt-5"
                >
                  {tab === "about" ? (
                    <>
                      {active.aiSummary && (
                        <div className="mb-4 rounded-2xl border border-brand-100 bg-brand-50/60 p-4">
                          <p className="mb-2 flex items-center gap-2 text-xs font-black text-brand-700">
                            <Sparkles size={14} /> ملخص الدرس
                          </p>
                          <p className="whitespace-pre-wrap text-sm leading-8 text-ink-700">{active.aiSummary}</p>
                        </div>
                      )}
                      <p className="whitespace-pre-wrap text-sm leading-8 text-ink-600">
                        {active.contentText ||
                          course.description ||
                          "تابع فيديو الدرس، وراجع الملفات المرفقة لتثبيت المعلومة."}
                      </p>
                      {staff && (
                        <div className="mt-4 rounded-2xl border border-dashed border-ink-200 p-4">
                          <div className="flex flex-wrap items-center gap-2">
                            <button
                              className="btn-soft"
                              disabled={summarizing}
                              onClick={async () => {
                                setSummarizing(true); setNotice("");
                                try {
                                  await api.post(`/courses/lessons/${active.id}/summary`, { notes: summaryNotes });
                                  setShowNotes(false); setSummaryNotes("");
                                  await load();
                                  setNotice("تم توليد ملخص الدرس وحفظه — الطلاب هيشوفوه تحت الفيديو.");
                                } catch (e) {
                                  setNotice(e.response?.data?.message || "تعذّر توليد الملخص");
                                } finally { setSummarizing(false); }
                              }}
                            >
                              <Sparkles size={16} /> {summarizing ? "جارٍ التلخيص…" : active.aiSummary ? "أعد توليد الملخص" : "لخّص الدرس بالذكاء الاصطناعي"}
                            </button>
                            <button className="btn-ghost" onClick={() => setShowNotes(!showNotes)}>
                              {showNotes ? "إخفاء الملاحظات" : "أضف نص الشرح أو ملاحظاتك"}
                            </button>
                          </div>
                          {showNotes && (
                            <textarea
                              dir="auto"
                              className="input mt-3"
                              rows={5}
                              value={summaryNotes}
                              onChange={(e) => setSummaryNotes(e.target.value)}
                              placeholder="الصق هنا نص الشرح أو النقاط اللي اتشرحت في الفيديو — كل ما تدّي تفاصيل أكتر، الملخص يطلع أدق."
                            />
                          )}
                        </div>
                      )}
                      <div className="mt-5 flex flex-wrap gap-2">
                        <Link to="/app/homework" className="btn-ghost">
                          الواجبات
                        </Link>
                        <Link to="/app/exams" className="btn-ghost">
                          الاختبارات
                        </Link>
                        {student &&
                          lessons[
                            lessons.findIndex((l) => l.id === active.id) + 1
                          ] && (
                            <button
                              className="btn-soft"
                              onClick={() =>
                                choose(
                                  lessons[
                                    lessons.findIndex(
                                      (l) => l.id === active.id,
                                    ) + 1
                                  ],
                                )
                              }
                            >
                              الدرس التالي
                            </button>
                          )}
                      </div>
                    </>
                  ) : tab === "files" ? (
                    <div className="space-y-3">
                      {materials
                        .filter((m) => m.type !== "VIDEO")
                        .map((m) => (
                          <a
                            key={m.id}
                            href={mediaUrl(m) || undefined}
                            aria-disabled={!mediaUrl(m)}
                            target="_blank"
                            rel="noreferrer"
                            className="flex items-center gap-3 rounded-2xl border border-ink-100 p-4 hover:bg-brand-50 transition"
                          >
                            <span className="rounded-xl bg-brand-50 p-3 text-brand-600">
                              <Files size={21} />
                            </span>
                            <span className="min-w-0 flex-1">
                              <b className="block text-sm">{m.title}</b>
                              <span className="text-xs text-ink-500">
                                {mediaUrl(m)
                                  ? m.description || m.type
                                  : "بانتظار رفع الملف من المدرس"}
                                {m.createdAt &&
                                  ` · رُفع بتاريخ ${fmtDate(m.createdAt)}`}
                              </span>
                            </span>
                            <Download size={17} className="text-brand-600" />
                          </a>
                        ))}
                      {!materials.some((m) => m.type !== "VIDEO") && (
                        <EmptyState
                          icon={Files}
                          title="لا توجد ملفات لهذا الدرس بعد"
                          hint={
                            staff
                              ? "أضف مذكرة PDF أو عرضاً أو صوراً من زر فيديو / ملف جديد."
                              : "ستظهر مرفقات المدرس هنا عند إضافتها."
                          }
                        />
                      )}
                    </div>
                  ) : (
                    <div className="space-y-4">
                      <p className="text-xs text-ink-500">
                        التقدّم في الكورس بالكامل. الإكمال يشمل ما يحدده الطالب
                        يدوياً وما يُحفظ عند انتهاء فيديو الدرس.
                      </p>
                      {learners.map((l) => (
                        <div
                          key={l.studentId}
                          className="rounded-2xl border border-ink-100 p-4"
                        >
                          <div className="mb-2 flex justify-between gap-3">
                            <Link
                              to={`/app/students/${l.studentId}`}
                              className="text-sm font-bold"
                            >
                              {l.name}
                            </Link>
                            <b className="text-xs text-brand-600">
                              {l.completed} / {lessons.length} درس
                            </b>
                          </div>
                          <ProgressBar
                            value={
                              lessons.length
                                ? (l.completed / lessons.length) * 100
                                : 0
                            }
                          />
                          <p className="mt-2 text-xs text-ink-400">
                            {l.lastActivity
                              ? `آخر نشاط: ${new Date(l.lastActivity).toLocaleString("ar-EG")}`
                              : "لم يبدأ الدروس بعد"}
                          </p>
                        </div>
                      ))}
                      {!learners.length && (
                        <EmptyState
                          icon={Users}
                          title="لا يوجد طلاب مسجّلون بعد"
                        />
                      )}
                      {staff && (
                        <div className="rounded-2xl border border-ink-100 p-4">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <h4 className="inline-flex items-center gap-1.5 text-sm font-black"><ShieldCheck size={16} className="text-brand-600" /> سجل مشاهدة الفيديوهات المحمية</h4>
                            <span className="text-[11px] text-ink-400">جهاز واحد لكل حساب في نفس الوقت · يُرصد الحساب المشترك</span>
                          </div>
                          {!watchLog.length ? (
                            <p className="mt-3 text-xs text-ink-400">لم يشاهد أحد فيديوهات هذا الكورس بعد.</p>
                          ) : (
                            <div className="mt-3 overflow-x-auto">
                              <table className="w-full min-w-[560px] text-xs">
                                <thead><tr className="text-right text-ink-400"><th className="pb-2 font-semibold">الحساب</th><th className="pb-2 font-semibold">جلسات</th><th className="pb-2 font-semibold">عناوين IP</th><th className="pb-2 font-semibold">أجهزة</th><th className="pb-2 font-semibold">دقائق</th><th className="pb-2 font-semibold">آخر مشاهدة</th></tr></thead>
                                <tbody className="divide-y divide-ink-100">
                                  {watchLog.map((w) => (
                                    <tr key={w.userId} className={w.flagged ? "bg-rose-50/60" : ""}>
                                      <td className="py-2 font-bold">{w.name}{w.flagged && <span className="mr-2 rounded-md bg-rose-100 px-1.5 py-0.5 text-[10px] text-rose-700">مشاركة محتملة</span>}</td>
                                      <td className="py-2 tabular-nums">{w.sessions}{w.replaced > 0 && <span className="text-ink-400"> · استُبدل {w.replaced}</span>}</td>
                                      <td className="py-2 tabular-nums" title={w.ips.join(", ")}>{w.distinctIps}</td>
                                      <td className="py-2 tabular-nums">{w.distinctDevices}</td>
                                      <td className="py-2 tabular-nums">{w.watchedMinutes}</td>
                                      <td className="py-2 text-ink-500">{w.lastSeen ? new Date(w.lastSeen).toLocaleString("ar-EG") : "—"}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </motion.div>
              </AnimatePresence>
            </div>
          )}
        </div>
        <aside className="card overflow-hidden xl:sticky xl:top-24">
          <div className="border-b border-ink-100 p-5">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold">محتوى الكورس</h3>
              <BookOpen size={19} className="text-brand-600" />
            </div>
            {student && (
              <>
                <p className="my-3 text-xs text-ink-500">
                  {completed} من {lessons.length} درس مكتمل
                </p>
                <ProgressBar
                  value={
                    lessons.length ? (completed / lessons.length) * 100 : 0
                  }
                />
              </>
            )}
          </div>
          <div className="max-h-[70vh] overflow-y-auto">
            {course.modules.map((m, i) => (
              <div key={m.id} className="border-b border-ink-100 last:border-0">
                <button
                  onClick={() =>
                    setCollapsed((s) => ({ ...s, [m.id]: !s[m.id] }))
                  }
                  aria-expanded={!collapsed[m.id]}
                  className="flex w-full items-center gap-3 bg-ink-50/70 p-4 text-right"
                >
                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-white text-xs font-bold text-brand-600">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <span className="flex-1 text-sm font-bold">{m.title}</span>
                  <ChevronDown
                    size={15}
                    className={`transition-transform ${collapsed[m.id] ? "-rotate-90" : ""}`}
                  />
                </button>
                {!collapsed[m.id] && (
                  <div className="space-y-1 p-2">
                    {m.lessons.map((l, j) => {
                      const done = progress.some(
                        (p) => p.lessonId === l.id && p.completed,
                      );
                      return (
                        <button
                          key={l.id}
                          onClick={() => choose(l)}
                          className={`flex w-full items-start gap-3 rounded-xl p-3 text-right transition ${active?.id === l.id ? "bg-brand-50 text-brand-700 ring-1 ring-inset ring-brand-200" : "text-ink-600 hover:bg-ink-50"}`}
                        >
                          {done ? (
                            <CheckCircle2
                              size={18}
                              className="mt-1 shrink-0 text-emerald-500"
                            />
                          ) : (
                            <Play size={17} className="mt-1 shrink-0" />
                          )}
                          <span className="min-w-0 flex-1">
                            <span className="block text-sm font-semibold">
                              {j + 1}. {l.title}
                            </span>
                            <span className="mt-1 block text-[11px] text-ink-400">
                              {l.durationMin} دقيقة · {l.materials.length} مادة
                              {l.createdAt && ` · رُفع ${fmtDate(l.createdAt)}`}
                            </span>
                          </span>
                        </button>
                      );
                    })}
                    {staff && (
                      <button
                        onClick={() =>
                          setModal({ type: "lesson", moduleId: m.id })
                        }
                        className="btn-soft mt-2 w-full border border-dashed border-brand-200"
                      >
                        <Plus size={15} /> إضافة درس
                      </button>
                    )}
                    {!m.lessons.length && !staff && (
                      <p className="p-3 text-xs text-ink-400">
                        الدروس قيد التجهيز
                      </p>
                    )}
                  </div>
                )}
              </div>
            ))}
            {!course.modules.length && (
              <EmptyState icon={BookOpen} title="لم تُضف فصول بعد" />
            )}
          </div>
        </aside>
      </div>
      {modal?.type === "module" && (
        <AddModule
          courseId={id}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            load();
          }}
        />
      )}
      {modal?.type === "lesson" && (
        <AddLesson
          moduleId={modal.moduleId}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            load();
          }}
        />
      )}
      {modal?.type === "material" && (
        <AddMaterials
          lessonId={modal.lessonId}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            load();
          }}
        />
      )}
    </div>
  );
}
