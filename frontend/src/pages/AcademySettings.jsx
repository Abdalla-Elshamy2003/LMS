import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  Save,
  ExternalLink,
  Plus,
  ImagePlus,
  Users,
  LockKeyhole,
  LayoutTemplate,
  CheckCircle2,
  ArrowUpLeft,
  PlayCircle,
  Trash2,
  Wallet,
  KeyRound,
  Copy,
  Ban,
} from "lucide-react";
import CoursePaymentCodes from "../features/payments/CoursePaymentCodes";
import api from "../lib/api";
import { useAuth } from "../lib/auth";
import { PageLoader, Spinner } from "../components/ui";
import ImageUpload from "../components/ImageUpload";
import { apiErrorMessage } from '../lib/apiError'

const blankAccount = {
  fullName: "",
  username: "",
  password: "",
  courseIds: [],
};
const blankVideo = {
  title: "",
  description: "",
  url: "",
  poster: "",
  category: "",
};
const fields = [
  ["name", "اسم المستر", 120],
  ["tagline", "اللقب والتخصص", 150],
  ["subject", "المادة", 100],
  ["headline", "العنوان الرئيسي", 220],
  ["description", "نص الترحيب", 1500],
  ["aboutText", "نبذة عن المستر", 3000],
  ["phone", "رقم التواصل (اختياري)", 25],
];
const paymentFields = [
  ["instapayNumber", "رقم إنستاباي (اختياري)", 40],
  ["vodafoneCashNumber", "رقم فودافون كاش (اختياري)", 40],
];
export default function AcademySettings() {
  const { user } = useAuth();
  const admin = ["SUPER_ADMIN", "BRANCH_ADMIN", "ACADEMIC_MANAGER"].includes(
    user.role,
  );
  const allowed = admin || user.role === "TEACHER";
  const [items, setItems] = useState(null),
    [selected, setSelected] = useState(""),
    [form, setForm] = useState(null);
  const [tab, setTab] = useState("content"),
    [courses, setCourses] = useState([]),
    [students, setStudents] = useState([]);
  const [video, setVideo] = useState(blankVideo);
  const [account, setAccount] = useState(blankAccount),
    [editing, setEditing] = useState(null);
  const [create, setCreate] = useState(false),
    [newForm, setNewForm] = useState({
      name: "",
      slug: "",
      username: "",
      password: "",
    });
  const [credentials, setCredentials] = useState({
    username: "",
    password: "",
  });
  const [teachers, setTeachers] = useState([]);
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState("");
  const load = async (preferred) => {
    const { data } = await api.get("/academies");
    setItems(data);
    setSelected((current) => String(preferred || current || data[0]?.id || ""));
  };
  useEffect(() => {
    if (allowed)
      load().catch((e) => {
        setError(apiErrorMessage(e, "تعذّر تحميل المساحات"));
        setItems([]);
      });
  }, []);
  useEffect(() => {
    if (admin) api.get("/users/teachers").then((r) => setTeachers(r.data)).catch(() => setTeachers([]));
  }, [admin]);
  useEffect(() => {
    const a = items?.find((x) => String(x.id) === selected);
    setForm(a ? { ...a } : null);
    setAccount(blankAccount);
    setEditing(null);
    setCredentials({ username: "", password: "" });
    if (a) {
      let live = true;
      Promise.all([
        api.get(`/academies/${a.id}/courses`),
        api.get(`/academies/${a.id}/students`),
      ])
        .then(([c, s]) => {
          if (live) {
            setCourses(c.data);
            setStudents(s.data);
          }
        })
        .catch(() => live && setError("تعذّر تحميل الكورسات أو حسابات الطلاب"));
      return () => {
        live = false;
      };
    }
  }, [selected, items]);
  const run = async (fn, success) => {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await fn();
      setMessage(success);
    } catch (e) {
      setError(
        apiErrorMessage(e, "تعذّر حفظ التغييرات. حاول مرة أخرى."),
      );
    } finally {
      setBusy(false);
    }
  };
  const save = (e) => {
    e.preventDefault();
    run(async () => {
      const { data } = await api.put(`/academies/${selected}`, form);
      setItems((v) => v.map((x) => (x.id === data.id ? data : x)));
    }, "تم حفظ محتوى الصفحة");
  };

  // Videos ride along with the rest of the page content, so adding or removing one saves
  // immediately rather than waiting for the content form's own save button.
  const saveVideos = (videos, message, after) =>
    run(async () => {
      const { data } = await api.put(`/academies/${selected}`, {
        ...form,
        videos,
      });
      setItems((v) => v.map((x) => (x.id === data.id ? data : x)));
      setForm((f) => ({ ...f, videos: data.videos || videos }));
      after?.(); // only on success — a failed save must not wipe what was typed
    }, message);
  const addVideo = (e) => {
    e.preventDefault();
    saveVideos(
      [...(form.videos || []), video],
      "تم نشر الدرس على صفحة المستر",
      () => setVideo(blankVideo),
    );
  };
  const removeVideo = (i) =>
    saveVideos(
      (form.videos || []).filter((_, x) => x !== i),
      "تم حذف الدرس",
    );
  const upload = (slot, file) => {
    if (!file) return;
    if (file.size > 3 * 1024 * 1024) {
      setError("اختر صورة أصغر من 3 ميجابايت");
      return;
    }
    run(async () => {
      const body = new FormData();
      body.append("file", file);
      const { data } = await api.post(
        `/academies/${selected}/images/${slot}`,
        body,
      );
      if (slot === "photo") setForm((f) => ({ ...f, photoUrl: data.url }));
    }, "تم رفع الصورة وحفظها");
  };
  const submitAccount = (e) => {
    e.preventDefault();
    run(
      async () => {
        if (editing)
          await api.put(`/academies/${selected}/students/${editing}`, account);
        else await api.post(`/academies/${selected}/students`, account);
        const { data } = await api.get(`/academies/${selected}/students`);
        setStudents(data);
        setAccount(blankAccount);
        setEditing(null);
      },
      editing
        ? "تم تعديل إتاحة الكورسات للطالب"
        : "تم إنشاء حساب الطالب وتحديد كورساته",
    );
  };
  // Removing a student archives the account rather than erasing it: attendance, grades and
  // submissions all reference the student, so a real delete would take their whole record with
  // them. The student disappears from this list and can no longer log in, and the username is
  // freed so the same one can be issued again.
  const removeStudent = (s) => {
    if (
      !confirm(
        `حذف حساب ${s.fullName}؟\n\nالطالب هيتشال من قائمة المستر ومش هيقدر يسجّل دخول تاني، وسجل حضوره ودرجاته هيفضل محفوظ في أرشيف المنصة.`,
      )
    )
      return;
    run(async () => {
      await api.delete(`/academies/${selected}/students/${s.id}`);
      const { data } = await api.get(`/academies/${selected}/students`);
      setStudents(data);
      if (editing === s.id) {
        setEditing(null);
        setAccount(blankAccount);
      }
    }, `تم حذف حساب ${s.fullName}`);
  };
  const enterWorkspace = () => {
    sessionStorage.setItem(
      "manarah_academy",
      JSON.stringify({ id: form.id, name: form.name, slug: form.slug }),
    );
    location.href = "/app/courses";
  };
  if (!allowed)
    return (
      <div className="card p-8">هذه الصفحة متاحة للمدرس والإدارة فقط.</div>
    );
  if (!items) return <PageLoader />;
  if (!items.length && !admin)
    return (
      <div className="card p-8 text-center">
        <LayoutTemplate size={40} className="mx-auto text-brand-500" />
        <h2 className="mt-4 text-xl font-black">لسه مفيش صفحة باسمك</h2>
        <p className="mx-auto mt-3 max-w-md text-sm leading-7 text-ink-500">
          صفحة المستر مساحة مستقلة بلينك خاص بيك وحسابات طلاب تخصّك وحدك. اطلب من
          الإدارة إنشاءها وربطها بحسابك، وهتلاقيها هنا على طول.
        </p>
      </div>
    );
  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      <div className="rounded-3xl bg-gradient-to-l from-[#173f53] to-[#086f98] text-white p-7 flex flex-wrap items-center justify-between gap-5">
        <div>
          <span className="text-xs text-cyan-100">
            مساحة باسم المستر.. بكل تفاصيله
          </span>
          <h2 className="text-2xl font-black mt-2">
            صفحة المدرس وحسابات الطلاب
          </h2>
          <p className="text-sm text-cyan-100/80 mt-2">
            عدّل النصوص والصور، وأنشئ حسابات الطلاب وحدد الكورسات المسموحة لكل
            طالب.
          </p>
        </div>
        <LayoutTemplate size={44} className="opacity-60" />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <label className="font-bold text-sm" htmlFor="academy-select">
          مساحة المدرس
        </label>
        <select
          id="academy-select"
          disabled={busy}
          className="input max-w-xs"
          value={selected}
          onChange={(e) => {
            setSelected(e.target.value);
            setCreate(false);
          }}
        >
          {items.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
              {a.defaultHome ? " · الصفحة الرئيسية" : ""}
            </option>
          ))}
        </select>
        {admin && (
          <button className="btn-secondary" onClick={() => setCreate(!create)}>
            <Plus size={17} /> مساحة مدرس جديدة
          </button>
        )}
        {admin && form && (
          <label className="flex items-center gap-2 text-sm">
            <span className="font-bold">يديرها المدرس</span>
            <select
              className="input max-w-[220px]"
              disabled={busy}
              value={form.ownerUserId || ""}
              onChange={(e) =>
                run(async () => {
                  const { data } = await api.put(`/academies/${selected}/owner`, {
                    userId: e.target.value ? Number(e.target.value) : null,
                  });
                  setItems((v) => v.map((x) => (x.id === data.id ? data : x)));
                }, "تم ربط الصفحة بحساب المدرس")
              }
            >
              <option value="">— غير مربوطة —</option>
              {teachers.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.fullName}
                </option>
              ))}
            </select>
          </label>
        )}
        {form && (
          <Link
            target="_blank"
            to={`/t/${form.slug}`}
            className="btn-secondary mr-auto"
          >
            معاينة الصفحة <ExternalLink size={15} />
          </Link>
        )}
      </div>
      {error && (
        <div
          role="alert"
          className="rounded-2xl bg-rose-50 border border-rose-100 p-4 text-sm text-rose-700"
        >
          {error}
        </div>
      )}
      {message && (
        <div
          role="status"
          className="flex gap-2 rounded-2xl bg-emerald-50 p-4 text-sm text-emerald-700"
        >
          <CheckCircle2 size={18} />
          {message}
        </div>
      )}
      {create && (
        <form
          className="card p-6 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            run(async () => {
              const { data } = await api.post("/academies", newForm);
              setCreate(false);
              setNewForm({ name: "", slug: "", username: "", password: "" });
              await load(data.id);
            }, "تم إنشاء مساحة مستقلة وحساب للمدرس");
          }}
        >
          <h3 className="font-extrabold">إنشاء مدرس بمساحة مستقلة</h3>
          <p className="text-xs text-ink-500">
            المساحة الجديدة تبدأ بدون طلاب أو كورسات. المدرس يضيف محتواه،
            والإدارة تقدر تدير مساحته.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            {[
              ["name", "اسم المدرس"],
              ["slug", "رابط الصفحة — مثال: ahmed-math"],
              ["username", "اسم مستخدم المدرس"],
              ["password", "كلمة المرور (10 أحرف على الأقل، تتضمن حرفًا ورقمًا)"],
            ].map(([key, label]) => (
              <label key={key} className="text-xs font-bold space-y-2 block">
                {label}
                <input
                  className="input mt-2"
                  required
                  autoComplete="off"
                  type={key === "password" ? "password" : "text"}
                  minLength={key === "password" ? 10 : undefined}
                  value={newForm[key]}
                  onChange={(e) =>
                    setNewForm((f) => ({ ...f, [key]: e.target.value }))
                  }
                />
              </label>
            ))}
          </div>
          <button disabled={busy} className="btn-primary">
            {busy ? <Spinner /> : <Plus size={16} />} إنشاء المساحة
          </button>
        </form>
      )}
      {form && (
        <>
          {admin && (
            <div className="card p-5 flex flex-wrap gap-4 items-center">
              <div className="flex-1">
                <h3 className="font-bold">إدارة محتوى {form.name}</h3>
                <p className="text-xs text-ink-500 mt-1">
                  افتح مساحة المدرس لإضافة الكورسات والدروس وإدارة الجدول
                  والمدفوعات الخاصة به.
                </p>
              </div>
              <button onClick={enterWorkspace} className="btn-primary">
                فتح مساحة المستر <ArrowUpLeft size={17} />
              </button>
              {!form.defaultHome && (
                <button
                  disabled={busy}
                  className="btn-secondary"
                  onClick={() =>
                    run(async () => {
                      await api.put(`/academies/${selected}/home`);
                      await load();
                    }, "تم تعيين الصفحة الرئيسية")
                  }
                >
                  تعيين كصفحة رئيسية
                </button>
              )}
            </div>
          )}
          <div className="flex gap-2 border-b border-ink-100 pb-3 flex-wrap">
            {[
              ["content", "محتوى الصفحة", LayoutTemplate],
              ["videos", "دروس الفيديو", PlayCircle],
              ["students", "حسابات الطلاب والكورسات", Users],
              ["payments", "الدفع وأكواد الاشتراك", Wallet],
              ...(admin ? [["credentials", "دخول المدرس", LockKeyhole]] : []),
            ].map(([key, label, Icon]) => (
              <button
                key={key}
                onClick={() => setTab(key)}
                className={tab === key ? "btn-primary" : "btn-secondary"}
              >
                <Icon size={16} />
                {label}
              </button>
            ))}
          </div>
          {tab === "content" && (
            <form
              onSubmit={save}
              className="grid gap-6 lg:grid-cols-[1fr_320px]"
            >
              <div className="card p-6 space-y-5">
                <div className="grid gap-5 sm:grid-cols-2">
                  {fields.map(([key, label, max]) => (
                    <label
                      key={key}
                      className={`block text-xs font-bold ${["description", "aboutText", "headline"].includes(key) ? "sm:col-span-2" : ""}`}
                    >
                      {label}
                      {["description", "aboutText"].includes(key) ? (
                        <textarea
                          rows={4}
                          maxLength={max}
                          required
                          className="input mt-2 resize-y"
                          value={form[key] || ""}
                          onChange={(e) =>
                            setForm((f) => ({ ...f, [key]: e.target.value }))
                          }
                        />
                      ) : (
                        <input
                          required={key !== "phone"}
                          maxLength={max}
                          className="input mt-2"
                          value={form[key] || ""}
                          onChange={(e) =>
                            setForm((f) => ({ ...f, [key]: e.target.value }))
                          }
                        />
                      )}
                    </label>
                  ))}
                </div>
                <label className="flex gap-3 items-start text-sm">
                  <input
                    type="checkbox"
                    checked={form.demoContent}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, demoContent: e.target.checked }))
                    }
                  />
                  <span>
                    عرض النماذج التجريبية
                    <small className="block mt-1 text-ink-400">
                      كروت كورسات وأمثلة شرح واضحة كعينات للعرض. يمكن إخفاؤها
                      عند إضافة المحتوى الحقيقي.
                    </small>
                  </span>
                </label>
                <label className="flex gap-3 items-center text-sm">
                  <input
                    type="checkbox"
                    checked={form.published}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, published: e.target.checked }))
                    }
                  />{" "}
                  إظهار صفحة المستر للزوار
                </label>
                <button disabled={busy} className="btn-primary">
                  {busy ? <Spinner /> : <Save size={17} />} حفظ التغييرات
                </button>
              </div>
              <aside className="space-y-5">
                <div className="card p-5">
                  <h3 className="font-extrabold text-sm mb-4">صورة المستر</h3>
                  <img
                    src={form.photoUrl}
                    alt={form.name}
                    className="rounded-2xl aspect-square w-full object-cover border border-ink-100"
                  />
                  <label className="btn-secondary w-full mt-4 cursor-pointer">
                    <ImagePlus size={18} /> رفع صورة المستر
                    <input
                      disabled={busy}
                      type="file"
                      accept="image/png,image/jpeg"
                      className="sr-only"
                      onChange={(e) => {
                        upload("photo", e.target.files[0]);
                        e.target.value = "";
                      }}
                    />
                  </label>
                  <p className="text-xs text-ink-400 mt-3">
                    PNG أو JPG · حتى 3 ميجابايت. تظهر في مقدمة الصفحة.
                  </p>
                </div>
                <div className="card p-5">
                  <h3 className="font-extrabold text-sm">
                    صورة قسم «عن المستر»
                  </h3>
                  <p className="text-xs text-ink-400 mt-2 leading-6">
                    أضف صورة ثانية للشرح أو للمستر. تستخدم الصورة الرئيسية إذا
                    لم ترفع صورة إضافية.
                  </p>
                  <label className="btn-secondary w-full mt-3 cursor-pointer">
                    <ImagePlus size={18} /> رفع صورة إضافية
                    <input
                      disabled={busy}
                      type="file"
                      accept="image/png,image/jpeg"
                      className="sr-only"
                      onChange={(e) => {
                        upload("cover", e.target.files[0]);
                        e.target.value = "";
                      }}
                    />
                  </label>
                </div>
                <p className="text-xs text-ink-400 leading-6 px-2">
                  الصور تُحفظ فور رفعها. باقي النصوص والإعدادات تُنشر عند الضغط
                  على حفظ التغييرات.
                </p>
              </aside>
            </form>
          )}
          {tab === "videos" && (
            <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
              <form
                onSubmit={addVideo}
                className="card p-6 space-y-4 self-start"
              >
                <h3 className="font-extrabold">درس فيديو جديد</h3>
                <p className="text-xs text-ink-500 leading-6">
                  الدرس بيظهر على صفحة المستر فوراً بعد الحفظ، جنب الكورسات في
                  نفس السلايدر.
                </p>
                <label className="block text-xs font-bold">
                  عنوان الدرس
                  <input
                    required
                    maxLength={140}
                    className="input mt-2"
                    value={video.title}
                    onChange={(e) =>
                      setVideo((f) => ({ ...f, title: e.target.value }))
                    }
                  />
                </label>
                <label className="block text-xs font-bold">
                  الصف / التصنيف
                  <input
                    maxLength={60}
                    placeholder="الصف الثالث الثانوي"
                    className="input mt-2"
                    value={video.category}
                    onChange={(e) =>
                      setVideo((f) => ({ ...f, category: e.target.value }))
                    }
                  />
                </label>
                <label className="block text-xs font-bold">
                  رابط الفيديو
                  <input
                    required
                    type="url"
                    dir="ltr"
                    placeholder="https://youtu.be/..."
                    className="input mt-2"
                    value={video.url}
                    onChange={(e) =>
                      setVideo((f) => ({ ...f, url: e.target.value }))
                    }
                  />
                </label>
                <label className="block text-xs font-bold">
                  وصف مختصر
                  <textarea
                    rows={3}
                    maxLength={400}
                    className="input mt-2 resize-y"
                    value={video.description}
                    onChange={(e) =>
                      setVideo((f) => ({ ...f, description: e.target.value }))
                    }
                  />
                </label>
                <div className="block text-xs font-bold">
                  صورة الغلاف (اختياري)
                  <ImageUpload
                    value={video.poster}
                    label="رفع صورة الغلاف"
                    onChange={(url) => setVideo((f) => ({ ...f, poster: url }))}
                  />
                </div>
                <button disabled={busy} className="btn-primary w-full">
                  {busy ? <Spinner /> : <Plus size={16} />} إضافة الدرس
                </button>
              </form>
              <div className="card p-6">
                <h3 className="font-extrabold mb-5">
                  دروس الفيديو{" "}
                  <span className="text-ink-400 text-sm">
                    ({(form.videos || []).length})
                  </span>
                </h3>
                {(form.videos || []).length === 0 ? (
                  <p className="text-sm text-ink-400 py-10 text-center">
                    لسه مفيش دروس فيديو. ابدأ بإضافة أول درس.
                  </p>
                ) : (
                  <ul className="space-y-3">
                    {(form.videos || []).map((v, i) => (
                      <li
                        key={i}
                        className="flex gap-4 items-center border border-ink-100 rounded-2xl p-4"
                      >
                        <img
                          src={v.poster || `/images/course-${(i % 6) + 1}.png`}
                          alt=""
                          className="w-16 h-16 rounded-xl object-cover shrink-0"
                        />
                        <div className="min-w-0 flex-1">
                          <p className="font-bold truncate">{v.title}</p>
                          <p className="text-xs text-ink-400 mt-1">
                            {v.category || "—"}
                          </p>
                          <p
                            className="text-[11px] text-ink-400 truncate"
                            dir="ltr"
                          >
                            {v.url}
                          </p>
                        </div>
                        <button
                          type="button"
                          aria-label={`حذف ${v.title}`}
                          className="text-rose-500 hover:text-rose-700 p-2"
                          onClick={() => removeVideo(i)}
                        >
                          <Trash2 size={17} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}
          {tab === "payments" && (
            <div className="space-y-6">
              <form onSubmit={save} className="card space-y-4 p-6">
                <h3 className="font-extrabold">طرق الدفع اليدوي</h3>
                <p className="text-sm leading-7 text-ink-500">
                  ديّ الأرقام اللي هتظهر للطالب في صفحة الدفع عشان يحوّل عليها. بعد ما تستلم التحويل، وّلد كوداً من قسم "أكواد الاشتراك" تحت وابعته للطالب — بمجرد ما يكتبه هيتفتحله الكورس فوراً من غير بوابة دفع إلكتروني.
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  {paymentFields.map(([key, label, max]) => (
                    <label key={key} className="block text-xs font-bold">
                      {label}
                      <input
                        dir="ltr"
                        maxLength={max}
                        className="input mt-2 text-right"
                        placeholder="01xxxxxxxxx"
                        value={form[key] || ""}
                        onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
                      />
                    </label>
                  ))}
                </div>
                <label className="block text-xs font-bold">
                  ملاحظة تظهر للطالب (اختياري)
                  <textarea
                    rows={2}
                    maxLength={500}
                    className="input mt-2 resize-y"
                    placeholder="مثال: اكتب اسمك في خانة الملاحظات عند التحويل"
                    value={form.paymentNote || ""}
                    onChange={(e) => setForm((f) => ({ ...f, paymentNote: e.target.value }))}
                  />
                </label>
                <button disabled={busy} className="btn-primary">
                  {busy ? <Spinner /> : <Save size={17} />} حفظ طرق الدفع
                </button>
              </form>
              <CoursePaymentCodes courses={courses} />
            </div>
          )}
          {tab === "credentials" && admin && (
            <form
              className="card p-6 max-w-2xl space-y-5"
              onSubmit={(e) => {
                e.preventDefault();
                run(async () => {
                  await api.put(
                    `/academies/${selected}/credentials`,
                    credentials,
                  );
                  setCredentials({ username: "", password: "" });
                }, "تم تفعيل حساب المدرس وحفظ بيانات الدخول الجديدة");
              }}
            >
              <h3 className="font-extrabold">
                تعيين اسم المستخدم وكلمة المرور للمستر
              </h3>
              <p className="text-sm text-ink-500 leading-7">
                عيّن بيانات الدخول لأول مرة لتفعيل حساب المستر، أو استخدم هذه
                الشاشة لتغييرها. بيانات الدخول القديمة تُستبدل بعد الحفظ.
              </p>
              <label className="block text-sm">
                اسم المستخدم
                <input
                  required
                  pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,49}"
                  autoComplete="off"
                  dir="ltr"
                  className="input mt-2"
                  value={credentials.username}
                  onChange={(e) =>
                    setCredentials((f) => ({ ...f, username: e.target.value }))
                  }
                />
              </label>
              <label className="block text-sm">
                كلمة مرور جديدة
                <input
                  required
                  minLength={10}
                  maxLength={72}
                  autoComplete="new-password"
                  type="password"
                  className="input mt-2"
                  value={credentials.password}
                  onChange={(e) =>
                    setCredentials((f) => ({ ...f, password: e.target.value }))
                  }
                />
              </label>
              <button disabled={busy} className="btn-primary">
                {busy ? <Spinner /> : <LockKeyhole size={17} />} حفظ وتفعيل حساب
                المدرس
              </button>
            </form>
          )}
          {tab === "students" && (
            <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
              <form
                onSubmit={submitAccount}
                className="card p-6 space-y-4 self-start"
              >
                <h3 className="font-extrabold">
                  {editing
                    ? `تعديل كورسات ${account.fullName}`
                    : "حساب طالب جديد"}
                </h3>
                <label className="block text-xs font-bold">
                  اسم الطالب
                  <input
                    required
                    className="input mt-2"
                    value={account.fullName}
                    onChange={(e) =>
                      setAccount((f) => ({ ...f, fullName: e.target.value }))
                    }
                  />
                </label>
                {!editing && (
                  <label className="block text-xs font-bold">
                    اسم المستخدم
                    <input
                      required
                      pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,49}"
                      autoComplete="off"
                      className="input mt-2"
                      dir="ltr"
                      value={account.username}
                      onChange={(e) =>
                        setAccount((f) => ({ ...f, username: e.target.value }))
                      }
                    />
                  </label>
                )}
                <label className="block text-xs font-bold">
                  {editing ? "كلمة مرور جديدة (اختياري)" : "كلمة المرور"}
                  <input
                    required={!editing}
                    minLength={10}
                    maxLength={72}
                    type="password"
                    autoComplete="new-password"
                    className="input mt-2"
                    value={account.password}
                    onChange={(e) =>
                      setAccount((f) => ({ ...f, password: e.target.value }))
                    }
                  />
                </label>
                <fieldset>
                  <legend className="text-sm font-bold mb-3">
                    الكورسات المسموح بها
                  </legend>
                  <div className="space-y-2 max-h-64 overflow-auto">
                    {courses.length === 0 && (
                      <p className="text-xs text-ink-400 leading-6">
                        لا توجد كورسات فعلية بعد. أضف كورساً من مساحة المستر، ثم
                        حدد إتاحته للطالب.
                      </p>
                    )}
                    {courses.map((c) => (
                      <label
                        key={c.id}
                        className="flex gap-3 p-3 rounded-xl bg-ink-50 text-xs items-center"
                      >
                        <input
                          type="checkbox"
                          checked={account.courseIds.includes(c.id)}
                          onChange={(e) =>
                            setAccount((f) => ({
                              ...f,
                              courseIds: e.target.checked
                                ? [...f.courseIds, c.id]
                                : f.courseIds.filter((id) => id !== c.id),
                            }))
                          }
                        />
                        {c.title}
                      </label>
                    ))}
                  </div>
                  <p className="text-[11px] text-ink-400 mt-3 leading-6">
                    الطالب يشوف الكورسات المختارة فقط. إلغاء الاختيار يوقف
                    الوصول للكورس مع الاحتفاظ بسجل تعلّمه.
                  </p>
                </fieldset>
                <button disabled={busy} className="btn-primary w-full">
                  {busy ? <Spinner /> : <Save size={16} />}{" "}
                  {editing ? "حفظ إتاحة الكورسات" : "إنشاء الحساب"}
                </button>
                {editing && (
                  <button
                    type="button"
                    className="btn-secondary w-full"
                    onClick={() => {
                      setEditing(null);
                      setAccount(blankAccount);
                    }}
                  >
                    إلغاء التعديل
                  </button>
                )}
              </form>
              <div className="card p-6">
                <h3 className="font-extrabold mb-5">
                  حسابات الطلاب{" "}
                  <span className="text-ink-400 text-sm">
                    ({students.length})
                  </span>
                </h3>
                {students.length === 0 ? (
                  <p className="text-sm text-ink-400 py-10 text-center">
                    ابدأ بإضافة أول طالب للمستر.
                  </p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm text-right">
                      <thead>
                        <tr className="border-b border-ink-100 text-ink-400 text-xs">
                          <th className="py-3">الطالب</th>
                          <th>اسم المستخدم</th>
                          <th>الكورسات</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {students.map((s) => (
                          <tr key={s.id} className="border-b border-ink-50">
                            <td className="py-4 font-bold">{s.fullName}</td>
                            <td dir="ltr" className="text-right text-xs">
                              {s.username}
                            </td>
                            <td>{s.courseIds.length}</td>
                            <td>
                              <div className="flex items-center justify-end gap-3">
                                <button
                                  className="text-brand-600 text-xs font-bold"
                                  onClick={() => {
                                    setEditing(s.id);
                                    setAccount({ ...s, password: "" });
                                  }}
                                >
                                  تعديل الإتاحة
                                </button>
                                <button
                                  disabled={busy}
                                  title={`حذف حساب ${s.fullName}`}
                                  aria-label={`حذف حساب ${s.fullName}`}
                                  className="text-rose-600 hover:text-rose-700 disabled:opacity-40"
                                  onClick={() => removeStudent(s)}
                                >
                                  <Trash2 size={15} />
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
