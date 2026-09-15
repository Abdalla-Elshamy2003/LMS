# منارة (Manarah) — نظام إدارة التعليم المتكامل

منصّة LMS متكاملة للمراكز التعليمية والمدارس والأكاديميات، مبنية بـ **Java Spring Boot** (باك-إند) و **React** (فرونت-إند) بواجهة عربية RTL احترافية مع حركات (animations).

> نظام واحد يجمع: LMS + إدارة الطلاب + الامتحانات + الحضور + أولياء الأمور + التواصل + المدفوعات + التحليلات — متعدّد المستأجرين (Multi-tenant).

---

## 🎯 نظرة سريعة

| | |
|---|---|
| **الباك-إند** | Java 21 · Spring Boot 3.3 · Spring Security (JWT) · Spring Data JPA · Flyway |
| **قاعدة البيانات** | SQLite (Hibernate community dialect · WAL) |
| **الفرونت-إند** | React 18 · Vite · Tailwind CSS · Framer Motion · Recharts · RTL عربي |
| **المعمارية** | Clean layered + Domain Events + RBAC + Multi-tenant |
| **المنفذ** | الباك-إند: `8091` · الفرونت-إند (dev): `5174` |

---

## 🚀 التشغيل

الجهاز لا يحتوي على Java مثبّتة على النظام، لذلك تم وضع **JDK 21 + Maven محمولين** داخل `D:\LMS\.tooling` (بدون صلاحيات مدير). المسارات محفوظة في `.tooling\paths.json`.

### 1) الباك-إند

من **PowerShell**:

```powershell
$p = Get-Content 'D:\LMS\.tooling\paths.json' | ConvertFrom-Json
$env:JAVA_HOME = $p.jdk
& "$($p.mvn)\bin\mvn.cmd" -f 'D:\LMS\backend\pom.xml' -DskipTests package
& "$($p.jdk)\bin\java.exe" -jar 'D:\LMS\backend\target\manarah-lms.jar'
```

- يعمل على `http://localhost:8091`
- توثيق الـ API (Swagger): `http://localhost:8091/swagger-ui.html`
- عند أول تشغيل يتم إنشاء قاعدة البيانات وتعبئتها ببيانات تجريبية عربية غنية تلقائياً.

### 2) الفرونت-إند

```bash
cd D:\LMS\frontend
npm install
npm run dev
```

ثم افتح `http://localhost:5174` (يوجّه طلبات `/api` تلقائياً إلى الباك-إند).

### 3) عن طريق Docker (الطريقة الموصى بها للتشغيل الموحّد)

لا يحتاج Docker إلى تثبيت Java أو Node على الجهاز — كل حاجة جوّه الكونتينرات.

```bash
# من مجلد D:\LMS
docker compose up --build -d
```

- الموقع الكامل (فرونت + باك عبر Nginx reverse proxy): **http://localhost**
- الباك-إند مباشرة (Swagger): **http://localhost:8091/swagger-ui.html**
- البيانات (قاعدة SQLite + الملفات المرفوعة) محفوظة في Docker volume باسم `manarah-data` وتفضل موجودة حتى لو اتعمل `docker compose down` (استخدم `down -v` لمسحها فعليًا).
- لتفعيل واتساب حقيقي أو تغيير السيكريت، انسخ `.env.example` إلى `.env` وعدّل القيم قبل التشغيل.

أوامر مفيدة:

```bash
docker compose logs -f            # متابعة اللوج للاتنين
docker compose ps                 # حالة الكونتينرات والـ healthcheck
docker compose down                # إيقاف (البيانات تفضل محفوظة)
docker compose up --build -d       # إعادة البناء بعد أي تعديل في الكود
```

بنية الـ Docker: `backend/Dockerfile` (multi-stage: Maven build ثم JRE-only runtime، يشتغل بمستخدم غير root، وفيه healthcheck على `/actuator/health`)، و`frontend/Dockerfile` (multi-stage: Vite build ثم Nginx بيعمل serve للـ SPA وreverse proxy لـ `/api/*` على الباك-إند بالاسم الداخلي `backend`).

---

## 🔑 حسابات تجريبية

كلمة المرور للجميع: **`manarah123`**

| الدور | البريد |
|---|---|
| مدير النظام (Super Admin) | `admin@manarah.io` |
| مدير الفرع | `branch@manarah.io` |
| المدير الأكاديمي | `academic@manarah.io` |
| مدرس | `teacher@manarah.io` |
| محاسب | `accountant@manarah.io` |
| ولي أمر | `parent@manarah.io` |
| طالب | `student@manarah.io` |

---

## ✅ الميزات المنفّذة (مقابل المتطلبات)

| القسم | الحالة |
|---|---|
| 1. الأدوار والصلاحيات (RBAC) — 10 أدوار | ✅ JWT + Method Security |
| 2. إدارة الطلاب (ملف كامل + حالات + حالة أكاديمية مُحتسبة) | ✅ |
| 3. نظام أولياء الأمور (حساب + متابعة الأبناء) | ✅ |
| 4. تنبيهات ذكية لأولياء الأمور (WhatsApp/SMS/Email/Push/In-App) | ✅ (In-App فعلي · الخارجية Stub) |
| 5. الحضور (يدوي + QR ديناميكي متغيّر) | ✅ |
| 6. الكورسات (كورس › فصول › دروس › مواد) | ✅ |
| 7. رفع المحاضرات والملفات + تتبّع المشاهدة | ✅ (رفع الملفات + جدول lesson_progress) |
| 9-13. الامتحانات (أنواع متعددة · بنك أسئلة · عشوائية · أمان · تصحيح آلي) | ✅ قاعة امتحان بملء الشاشة وحفظ تلقائي · نافذة زمنية · سياسة عرض النتائج · تقرير نزاهة · تحليل الأسئلة · محرر أسئلة يدوي |
| 14. الواجبات (تسليم · تصحيح · رصد الغياب) | ✅ ملفات متعددة وتصوير الكراسة · معايير تصحيح (Rubric) · سياسة تأخير بخصم · إعادة للتعديل · استوديو تصحيح ببنك تعليقات · تصدير CSV |
| 7ب. حماية الفيديو | ✅ جلسة مشاهدة لجهاز واحد · تذكرة تشغيل موقّعة · رفض التحميل من خارج المتصفح · علامة مائية للحساب · سجل مشاهدة (IP/أجهزة) · DRM عبر VdoCipher اختياري — التفاصيل في `output/ASSESSMENT_PRO_AND_VIDEO_SECURITY_2026-09-14.md` |
| 15. سجل الدرجات (Gradebook مُغذّى بالأحداث) | ✅ |
| 16. تحليلات الأداء (لوحات معلومات) | ✅ |
| 17. كشف الطلاب المتعثرين (At-Risk مُحتسب + تنبيه عند التراجع) | ✅ |
| 18. الخط الزمني للطالب (Timeline) | ✅ |
| 19. تقييم المدرسين | ✅ (نموذج + API) |
| 20. لوحة تحكم المدرس | ✅ |
| 21. التقويم / الجدول | ✅ |
| 22-23. الفروع والقاعات | ✅ |
| 24. التسجيل والمجموعات | ✅ |
| 25-27. المدفوعات والأقساط والتذكيرات | ✅ |
| 28. الشهادات (برمز تحقق) | ✅ (نموذج + API) |
| 30-31. التلعيب (نقاط · مستويات · لوحة شرف · شارات) | ✅ |
| 32-33. مركز التواصل والبث الجماعي | ✅ |
| 34. محرّك التنبيهات (قواعد IF/THEN قابلة للتخصيص) | ✅ |
| 46-47. التقارير (بيانات جاهزة عبر الـ API) | ✅ بيانات · (تصدير PDF: نقطة توسعة) |
| 48. سجل التدقيق (Audit Log) | ✅ |
| 49. تعدد المستأجرين (Multi-tenant) | ✅ `tenant_id` في كل جدول |

### ملاحظات صريحة
- **قنوات WhatsApp/SMS/Email/Push**: منفّذة كـ **منافذ (ports)** مع مُرسِلات وهمية تسجّل النية فقط (لا توجد مفاتيح مزوّد خدمة). القناة **In-App** فعلية بالكامل. لتفعيل الإرسال الحقيقي: استبدل `StubSenders` بمُحوّلات Twilio / Meta WhatsApp / SMTP / FCM.
- **Live Classes (§8)**: نقطة توسعة — يُدمج Zoom/Meet عبر إضافة رابط للجلسة.
- **تصدير التقارير PDF (§47)**: البيانات متاحة عبر الـ API؛ التصدير كملف PDF نقطة توسعة.

---

## 🏛️ المعمارية

```
backend/src/main/java/com/manarah/
├── common/         (النواة المشتركة: BaseEntity, TenantContext, exceptions, storage, events)
├── security/       (JWT, RBAC, AuthController)
├── config/         (Security, OpenAPI, DataSeeder)
├── org/            (Tenant, Branch, Room)
├── identity/       (User, Role, UserService)
├── student/        (Student, Guardian, StudentMetricsService)
├── course/         (Course, Module, Lesson, Material, Progress)
├── enrollment/     (Enrollment, StudyGroup)
├── attendance/     (ClassSession, AttendanceRecord + QR)
├── exam/           (Question bank, Exam, StudentExam, auto-grading)
├── homework/       (Assignment, Submission)
├── gradebook/      (GradeItem ledger)
├── payment/        (Invoice, Installment, Payment)
├── notification/   (Notification, Rules engine, channels)
├── risk/           (RiskService — كشف التعثّر)
├── timeline/       (خط زمني للطالب)
├── audit/          (سجل التدقيق)
├── gamification/   (نقاط · مستويات · لوحة شرف)
├── communication/  (رسائل · إعلانات · بث)
├── calendar/       (تقويم)
├── analytics/      (لوحات المعلومات)
└── integration/    (مستمعو الأحداث: metrics/risk/timeline/notifications/gamification)
```

**نمط الأحداث (Domain Events):** الخدمات الأساسية تُطلق أحداثاً (`StudentAbsent`, `ScoreRecorded`, `HomeworkMissed`, ...) وتستهلكها المهام العرضية (التنبيهات، كشف التعثّر، الخط الزمني، سجل الدرجات، التلعيب) كمستمعين `@TransactionalEventListener` — فصل نظيف يسمح بإضافة قواعد §34 دون تعديل الخدمات.

**الحالة المُشتقّة مُجسّدة (Materialized):** معدل الطالب ونسبة الحضور والحالة الأكاديمية ومستوى الخطر تُحسب وتُخزّن عند وقوع الأحداث — مما يجعل «أصبح الطالب متعثراً» قابلاً للرصد وإطلاق تنبيه.

---

## 🔎 أهم مسارات الـ API

- `POST /api/auth/login` · `GET /api/auth/me`
- `GET/POST /api/students` · `GET /api/students/{id}` · `/timeline`
- `GET/POST /api/courses` · `/modules` · `/lessons` · `/materials`
- `POST /api/attendance/sessions/{id}/mark` · `/qr` · `POST /api/attendance/check-in`
- `POST /api/exams/{id}/start` · `POST /api/exams/attempts/{id}/submit`
- `POST /api/homework/submit` · `/grade` · `/mark-missing`
- `POST /api/payments/invoices` · `/record` · `/run-reminders`
- `GET /api/dashboard/admin` · `/teacher` · `/leaderboard`
- `GET/POST /api/notifications/rules`

التوثيق الكامل التفاعلي: **Swagger UI** على `/swagger-ui.html`.
