package com.manarah.config;

import com.manarah.attendance.domain.AttendanceRecord;
import com.manarah.attendance.domain.ClassSession;
import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.attendance.repo.ClassSessionRepository;
import com.manarah.calendar.domain.CalendarEvent;
import com.manarah.calendar.repo.CalendarEventRepository;
import com.manarah.calendar.domain.ScheduleSlot;
import com.manarah.calendar.repo.ScheduleSlotRepository;
import com.manarah.communication.domain.Announcement;
import com.manarah.communication.repo.AnnouncementRepository;
import com.manarah.course.domain.*;
import com.manarah.course.repo.*;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.domain.StudyGroup;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.enrollment.repo.StudyGroupRepository;
import com.manarah.exam.domain.*;
import com.manarah.exam.repo.*;
import com.manarah.gamification.GamificationService;
import com.manarah.gradebook.domain.GradeItem;
import com.manarah.gradebook.repo.GradeItemRepository;
import com.manarah.homework.domain.Assignment;
import com.manarah.homework.domain.Submission;
import com.manarah.homework.repo.AssignmentRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.NotificationRulesEngine;
import com.manarah.notification.domain.NotificationRule;
import com.manarah.notification.repo.NotificationRuleRepository;
import com.manarah.org.domain.Branch;
import com.manarah.org.domain.Room;
import com.manarah.org.domain.Tenant;
import com.manarah.org.repo.BranchRepository;
import com.manarah.org.repo.RoomRepository;
import com.manarah.org.repo.TenantRepository;
import com.manarah.payment.domain.Installment;
import com.manarah.payment.domain.Invoice;
import com.manarah.payment.repo.InstallmentRepository;
import com.manarah.payment.repo.InvoiceRepository;
import com.manarah.risk.RiskService;
import com.manarah.student.StudentMetricsService;
import com.manarah.student.domain.Guardian;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGuardian;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import com.manarah.timeline.TimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Seeds a realistic Arabic demo tenant on first run so every screen has meaningful data and the
 * derived metrics / risk / notifications pipelines are exercised end to end.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "manarah.demo.seed-enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_VIDEO_URL =
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";
    private final String demoPassword;

    private final Random rnd = new Random(42);

    private final TenantRepository tenants;
    private final BranchRepository branches;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository studentGuardians;
    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonMaterialRepository materials;
    private final StudyGroupRepository groups;
    private final EnrollmentRepository enrollments;
    private final ClassSessionRepository sessions;
    private final AttendanceRecordRepository attendance;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final GradeItemRepository grades;
    private final InvoiceRepository invoices;
    private final InstallmentRepository installments;
    private final NotificationRuleRepository rules;
    private final CalendarEventRepository calendar;
    private final ScheduleSlotRepository scheduleSlots;
    private final AnnouncementRepository announcements;
    private final PasswordEncoder encoder;
    private final StudentMetricsService metrics;
    private final RiskService risk;
    private final GamificationService gamification;
    private final NotificationRulesEngine rulesEngine;
    private final TimelineService timeline;

    public DataSeeder(TenantRepository tenants, BranchRepository branches, RoomRepository rooms, UserRepository users,
                      StudentRepository students, GuardianRepository guardians, StudentGuardianRepository studentGuardians,
                      CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
                      LessonMaterialRepository materials, StudyGroupRepository groups, EnrollmentRepository enrollments,
                      ClassSessionRepository sessions, AttendanceRecordRepository attendance, QuestionRepository questions,
                      QuestionOptionRepository options, ExamRepository exams, ExamQuestionRepository examQuestions,
                      AssignmentRepository assignments, SubmissionRepository submissions, GradeItemRepository grades,
                      InvoiceRepository invoices, InstallmentRepository installments, NotificationRuleRepository rules,
                      CalendarEventRepository calendar, ScheduleSlotRepository scheduleSlots, AnnouncementRepository announcements, PasswordEncoder encoder,
                      StudentMetricsService metrics, RiskService risk, GamificationService gamification,
                      NotificationRulesEngine rulesEngine, TimelineService timeline,
                      @Value("${manarah.demo.password}") String demoPassword) {
        this.tenants = tenants; this.branches = branches; this.rooms = rooms; this.users = users;
        this.students = students; this.guardians = guardians; this.studentGuardians = studentGuardians;
        this.courses = courses; this.modules = modules; this.lessons = lessons; this.materials = materials;
        this.groups = groups; this.enrollments = enrollments; this.sessions = sessions; this.attendance = attendance;
        this.questions = questions; this.options = options; this.exams = exams; this.examQuestions = examQuestions;
        this.assignments = assignments; this.submissions = submissions; this.grades = grades; this.invoices = invoices;
        this.installments = installments; this.rules = rules; this.calendar = calendar; this.scheduleSlots = scheduleSlots; this.announcements = announcements;
        this.encoder = encoder; this.metrics = metrics; this.risk = risk; this.gamification = gamification;
        this.rulesEngine = rulesEngine;
        this.timeline = timeline;
        this.demoPassword = com.manarah.security.PasswordPolicy.requireStrong(demoPassword);
    }

    @Override
    public void run(String... args) {
        if (tenants.count() > 0) {
            ensureFifthTeacherCatalog();
            ensurePlayableDemoVideos();
            log.info("Data already present — verified the five-teacher demo catalog and lesson previews.");
            return;
        }
        log.info("Seeding Manarah demo data...");
        Tenant tenant = seedTenant("أكاديمية النخبة التعليمية", "elite", "#4f46e5");
        seedTenant("مدارس المستقبل الدولية", "future", "#0ea5e9"); // second tenant proves isolation (§49)
        Long t = tenant.getId();

        Branch main = branch(t, "المقر الرئيسي — القاهرة", "وسط البلد، القاهرة", "0227000000");
        Branch nasr = branch(t, "فرع مدينة نصر", "شارع عباس العقاد", "0226000000");
        branch(t, "فرع الإسكندرية", "سموحة، الإسكندرية", "0345000000");
        Room room101 = room(t, main.getId(), "قاعة 101", 40, true, true);
        Room room102 = room(t, main.getId(), "قاعة 102", 30, true, false);
        Room roomA = room(t, nasr.getId(), "قاعة A", 25, true, true);

        // Staff logins
        user(t, main.getId(), "أ. خالد منصور", "admin@manarah.io", Role.SUPER_ADMIN);
        user(t, nasr.getId(), "أ. هبة السيد", "branch@manarah.io", Role.BRANCH_ADMIN);
        user(t, main.getId(), "أ. طارق فؤاد", "academic@manarah.io", Role.ACADEMIC_MANAGER);
        user(t, main.getId(), "أ. ياسمين نبيل", "accountant@manarah.io", Role.ACCOUNTANT);
        User tMath = user(t, main.getId(), "أ. محمد الجندي", "teacher@manarah.io", Role.TEACHER);
        User tPhys = user(t, main.getId(), "أ. سارة عبد الله", "sara@manarah.io", Role.TEACHER);
        User tChem = user(t, main.getId(), "أ. أحمد رشدي", "ahmed@manarah.io", Role.TEACHER);
        User tArab = user(t, nasr.getId(), "أ. منى صالح", "mona@manarah.io", Role.TEACHER);
        User tBio = user(t, main.getId(), "أ. ريم مصطفى", "reem@manarah.io", Role.TEACHER);
        teacherProfile(tMath, "خبير الرياضيات", "الجبر · التفاضل · الهندسة", "خبرة 15 عامًا في تدريس رياضيات الثانوية العامة بأسلوب مبسّط يركّز على الفهم لا الحفظ.", "الأحد والثلاثاء 6:00م", "https://i.pravatar.cc/400?img=13");
        teacherProfile(tPhys, "معلّمة الفيزياء", "الميكانيكا · الكهرباء · الحرارة", "شغوفة بتبسيط الفيزياء عبر التجارب والأمثلة الواقعية، ونتائج متميزة لطلابها.", "الاثنين والأربعاء 4:00م", "https://i.pravatar.cc/400?img=45");
        teacherProfile(tChem, "معلّم الكيمياء", "العضوية · غير العضوية · التحليلية", "يجعل الكيمياء ممتعة وسهلة بربطها بالحياة اليومية والتطبيقات العملية.", "الثلاثاء والخميس 5:00م", "https://i.pravatar.cc/400?img=33");
        teacherProfile(tArab, "معلّمة اللغة العربية", "النحو · البلاغة · الأدب", "متخصصة في قواعد اللغة والبلاغة مع تدريبات مكثّفة على الامتحانات.", "السبت والاثنين 3:00م", "https://i.pravatar.cc/400?img=48");
        teacherProfile(tBio, "معلّمة الأحياء", "الوراثة · الخلية · جسم الإنسان", "شرح بصري منظم يربط تفاصيل الأحياء بالرسومات والتجارب وأسئلة الامتحانات.", "الأحد والأربعاء 7:00م", "https://i.pravatar.cc/400?img=47");

        // Courses
        Course math = course(t, main.getId(), tMath.getId(), "الرياضيات — الصف الثالث الثانوي", "رياضيات", "ثانوي", "3200");
        Course phys = course(t, main.getId(), tPhys.getId(), "الفيزياء — الصف الثالث الثانوي", "فيزياء", "ثانوي", "3000");
        Course chem = course(t, main.getId(), tChem.getId(), "الكيمياء — الصف الثالث الثانوي", "كيمياء", "ثانوي", "2800");
        Course arab = course(t, nasr.getId(), tArab.getId(), "اللغة العربية — الصف الثالث الثانوي", "لغة عربية", "ثانوي", "2000");
        Course bio = course(t, main.getId(), tBio.getId(), "الأحياء — الصف الثالث الثانوي", "أحياء", "ثانوي", "2600");
        List<Course> allCourses = List.of(math, phys, chem, arab, bio);

        Room[] courseRooms = {room101, room102, roomA, null, room101};
        int[][] courseDays = {{0, 2}, {1, 3}, {2, 4}, {6, 1}, {0, 3}};
        LocalTime[] courseTimes = {LocalTime.of(18, 0), LocalTime.of(16, 0), LocalTime.of(17, 0), LocalTime.of(15, 0), LocalTime.of(19, 0)};
        String[] courseColors = {"#0f766e", "#2563eb", "#7c3aed", "#c2410c", "#059669"};
        for (int ci = 0; ci < allCourses.size(); ci++) {
            Course c = allCourses.get(ci);
            CourseModule m1 = module(t, c.getId(), "الفصل الأول", 1);
            Lesson l1 = lesson(t, m1.getId(), "المحاضرة الأولى", 1, 70);
            material(t, l1.getId(), "VIDEO", "شرح الدرس الأول", 4200);
            material(t, l1.getId(), "PDF", "ملخص الدرس الأول", null);
            lesson(t, m1.getId(), "المحاضرة الثانية", 2, 65);
            module(t, c.getId(), "الفصل الثاني", 2);
            StudyGroup studyGroup = groups.save(group(t, c.getId(), c.getTeacherId(), "المجموعة الرئيسية", 30));
            for (int day : courseDays[ci]) weeklySlot(t, c, studyGroup, courseRooms[ci], day, courseTimes[ci], courseColors[ci]);
        }

        // Question bank
        seedQuestions(t);
        seedExam(t, math, tMath.getId());

        // Assignments (2 per course)
        Map<Long, List<Assignment>> courseAssignments = new HashMap<>();
        for (Course c : allCourses) {
            List<Assignment> as = new ArrayList<>();
            as.add(assignment(t, c.getId(), "واجب 1 — " + c.getSubject(), tMath.getId(), 5));
            as.add(assignment(t, c.getId(), "واجب 2 — " + c.getSubject(), tMath.getId(), 12));
            courseAssignments.put(c.getId(), as);
        }

        // Notification rules FIRST so risk escalation during seeding fires parent alerts (§34)
        rule(t, "تنبيه الغياب لولي الأمر", "STUDENT_ABSENT", null, "IN_APP,WHATSAPP", true, false);
        rule(t, "تنبيه الدرجات المنخفضة", "LOW_SCORE", 50.0, "IN_APP,WHATSAPP", true, true);
        rule(t, "تنبيه الواجب غير المُسلَّم", "HOMEWORK_MISSED", 1.0, "IN_APP", true, false);
        rule(t, "تنبيه التراجع الدراسي", "RISK_ESCALATED", null, "IN_APP,WHATSAPP,SMS", true, true);
        rule(t, "تذكير الأقساط", "INSTALLMENT_DUE", null, "IN_APP,WHATSAPP", true, false);

        // Students + guardians + enrollment + attendance + grades + homework + invoices
        seedStudents(t, main.getId(), nasr.getId(), allCourses, courseAssignments);
        // Deterministically guarantee that every showcased teacher has real learners even if the
        // seeded random enrolment distribution changes in the future.
        Student firstStudent = students.findByTenantId(t).stream().findFirst().orElseThrow();
        for (Course c : allCourses) if (enrollments.countByTenantIdAndCourseId(t, c.getId()) == 0) {
            Enrollment e = new Enrollment(); e.setTenantId(t); e.setStudentId(firstStudent.getId()); e.setCourseId(c.getId());
            enrollments.save(e);
        }

        // Announcements & calendar
        announcement(t, "بدء الفصل الدراسي الثاني", "نودّ إعلامكم ببدء الفصل الدراسي الثاني يوم الأحد القادم. نتمنى لجميع الطلاب التوفيق.");
        announcement(t, "جدول الامتحانات الشهرية", "سيتم عقد الامتحانات الشهرية خلال الأسبوع الأخير من الشهر. يُرجى المتابعة مع المدرسين.");
        calendar(t, main.getId(), "EXAM", "امتحان الرياضيات الشهري", 3);
        calendar(t, main.getId(), "LECTURE", "محاضرة مراجعة الفيزياء", 1);
        calendar(t, main.getId(), "MEETING", "اجتماع أولياء الأمور", 7);

        log.info("Seeding complete: {} students, {} courses.", students.countByTenantId(t), courses.countByTenantId(t));
    }

    /** Safely tops up an existing local demo database created before the fifth teacher was added. */
    private void ensureFifthTeacherCatalog() {
        Tenant tenant = tenants.findBySlug("elite").orElse(null);
        if (tenant == null) return;
        Long tenantId = tenant.getId();
        User teacher = users.findByEmailIgnoreCase("reem@manarah.io")
                .filter(u -> tenantId.equals(u.getTenantId()) && u.getRole() == Role.TEACHER)
                .orElseGet(() -> {
                    var branch = branches.findByTenantIdOrderByName(tenantId).stream().findFirst().orElse(null);
                    User created = user(tenantId, branch == null ? null : branch.getId(), "أ. ريم مصطفى", "reem@manarah.io", Role.TEACHER);
                    teacherProfile(created, "معلّمة الأحياء", "الوراثة · الخلية · جسم الإنسان",
                            "شرح بصري منظم يربط تفاصيل الأحياء بالرسومات والتجارب وأسئلة الامتحانات.",
                            "الأحد والأربعاء 7:00م", "https://i.pravatar.cc/400?img=47");
                    return created;
                });
        if (!courses.findByTenantIdAndTeacherId(tenantId, teacher.getId()).isEmpty()) return;
        Long branchId = teacher.getBranchId();
        Course bio = course(tenantId, branchId, teacher.getId(), "الأحياء — الصف الثالث الثانوي", "أحياء", "ثانوي", "2600");
        CourseModule module = module(tenantId, bio.getId(), "الفصل الأول", 1);
        Lesson first = lesson(tenantId, module.getId(), "المحاضرة الأولى — الخلية ووظائفها", 1, 60);
        material(tenantId, first.getId(), "VIDEO", "شرح الخلية ووظائفها", 3600);
        material(tenantId, first.getId(), "PDF", "خريطة مفاهيم الخلية", null);
        lesson(tenantId, module.getId(), "المحاضرة الثانية — الانقسام الخلوي", 2, 55);
        module(tenantId, bio.getId(), "الوراثة", 2);
        groups.save(group(tenantId, bio.getId(), teacher.getId(), "المجموعة الرئيسية", 30));
        students.findByTenantId(tenantId).stream().limit(10).forEach(student -> {
            if (!enrollments.existsByTenantIdAndStudentIdAndCourseId(tenantId, student.getId(), bio.getId())) {
                Enrollment e = new Enrollment(); e.setTenantId(tenantId); e.setStudentId(student.getId()); e.setCourseId(bio.getId());
                enrollments.save(e);
            }
        });
        log.info("Added the fifth demo teacher with an enrolled biology course.");
    }

    /**
     * Older local demo volumes contained VIDEO rows without a URL or uploaded file. Keep the
     * migration idempotent and only fill those empty demo rows; real teacher uploads are never
     * overwritten. The public CC0 clip is deliberately labelled as a placeholder in the UI data.
     */
    private void ensurePlayableDemoVideos() {
        Tenant tenant = tenants.findBySlug("elite").orElse(null);
        if (tenant == null) return;
        List<LessonMaterial> changed = materials.findByTenantIdAndType(tenant.getId(), "VIDEO").stream()
                .filter(m -> (m.getUrl() == null || m.getUrl().isBlank())
                        && (m.getFileKey() == null || m.getFileKey().isBlank()))
                .peek(m -> {
                    m.setUrl(DEMO_VIDEO_URL);
                    m.setDescription("فيديو تجريبي لمعاينة رحلة المشاهدة. يستبدله المدرس بتسجيل المحاضرة الفعلي.");
                }).toList();
        if (!changed.isEmpty()) {
            materials.saveAll(changed);
            log.info("Added playable URLs to {} empty demo video materials.", changed.size());
        }
    }

    // ---------------- students pipeline ----------------

    private void seedStudents(Long t, Long mainBranch, Long nasrBranch, List<Course> allCourses,
                              Map<Long, List<Assignment>> courseAssignments) {
        String[] first = {"أحمد", "محمد", "يوسف", "عمر", "مالك", "خالد", "زياد", "كريم", "آدم", "مازن",
                "سلمى", "ملك", "حبيبة", "نور", "جنى", "ليان", "مريم", "فاطمة", "رقية", "هنا",
                "طه", "حمزة", "إبراهيم", "عبد الرحمن"};
        String[] last = {"إبراهيم", "السيد", "عبد العزيز", "منصور", "خليل", "شعبان", "الحسيني", "مصطفى",
                "رمضان", "فتحي", "سعيد", "جمال"};

        List<Student> created = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String name = first[i % first.length] + " " + last[rnd.nextInt(last.length)];
            double ability = 0.35 + rnd.nextDouble() * 0.6; // per-student skill 0.35..0.95
            Student s = new Student();
            s.setTenantId(t);
            s.setBranchId(i % 4 == 0 ? nasrBranch : mainBranch);
            s.setCode(String.format("STD-%05d", i + 1));
            s.setFullName(name);
            s.setNationalId("3040" + String.format("%09d", 100000 + i));
            s.setBirthDate(LocalDate.of(2007, 1 + (i % 12), 1 + (i % 27)));
            s.setGender(i % 3 == 0 ? "FEMALE" : "MALE");
            s.setGradeLevel("ثانوي");
            s.setGrade("الصف الثالث الثانوي");
            s.setSchool("مدرسة " + last[i % last.length] + " الثانوية");
            s.setPhone("0100" + String.format("%07d", 1000000 + i));
            s.setStatus("ACTIVE");
            students.save(s);
            created.add(s);

            // Guardian
            Guardian g = new Guardian();
            g.setTenantId(t);
            g.setFullName("والد/ة " + name);
            g.setPhone("0111" + String.format("%07d", 2000000 + i));
            g.setEmail(i == 0 ? "parent@manarah.io" : null);
            if (i == 0) {
                User parentUser = user(t, mainBranch, g.getFullName(), "parent@manarah.io", Role.PARENT);
                g.setUserId(parentUser.getId());
            }
            guardians.save(g);
            StudentGuardian link = new StudentGuardian();
            link.setTenantId(t);
            link.setStudentId(s.getId());
            link.setGuardianId(g.getId());
            link.setRelation("ولي أمر");
            studentGuardians.save(link);

            // Student login for the first student
            if (i == 0) {
                User su = user(t, mainBranch, name, "student@manarah.io", Role.STUDENT);
                s.setUserId(su.getId());
                students.save(s);
            }

            // Enroll in 2-3 courses
            List<Course> shuffled = new ArrayList<>(allCourses);
            Collections.shuffle(shuffled, rnd);
            int nCourses = 2 + rnd.nextInt(2);
            for (int ci = 0; ci < nCourses; ci++) {
                Course c = shuffled.get(ci);
                Enrollment e = new Enrollment();
                e.setTenantId(t);
                e.setStudentId(s.getId());
                e.setCourseId(c.getId());
                enrollments.save(e);
                timeline.record(t, s.getId(), "ENROLLMENT", "التحاق بكورس", c.getTitle(), "book-open",
                        Instant.now().minus(30, ChronoUnit.DAYS));

                // Grades: quiz + midterm influenced by ability
                grade(t, s.getId(), c.getId(), "اختبار قصير — " + c.getSubject(), "QUIZ", scoreOut(ability, 10), 10);
                double midterm = scoreOut(ability, 40);
                grade(t, s.getId(), c.getId(), "امتحان منتصف الفصل — " + c.getSubject(), "MIDTERM", midterm, 40);
                timeline.record(t, s.getId(), "EXAM", "امتحان منتصف الفصل — " + c.getSubject(),
                        "الدرجة: " + midterm + "/40", "clipboard-check", Instant.now().minus(12, ChronoUnit.DAYS));

                // Homework submissions
                for (Assignment a : courseAssignments.get(c.getId())) {
                    Submission sub = new Submission();
                    sub.setTenantId(t);
                    sub.setAssignmentId(a.getId());
                    sub.setStudentId(s.getId());
                    boolean missing = rnd.nextDouble() > (0.4 + ability * 0.5);
                    if (missing) {
                        sub.setStatus("MISSING");
                        timeline.record(t, s.getId(), "HOMEWORK", "واجب غير مُسلَّم", a.getTitle(), "file-x",
                                Instant.now().minus(rnd.nextInt(8) + 1, ChronoUnit.DAYS));
                    } else {
                        sub.setStatus("GRADED");
                        sub.setSubmittedAt(Instant.now().minus(rnd.nextInt(10) + 1, ChronoUnit.DAYS));
                        double sc = scoreOut(ability, a.getMaxScore());
                        sub.setScore(sc);
                        grade(t, s.getId(), c.getId(), a.getTitle(), "HOMEWORK", sc, a.getMaxScore());
                        timeline.record(t, s.getId(), "HOMEWORK", "تسليم واجب", a.getTitle() + " — " + sc + "/" + a.getMaxScore(),
                                "file-check", sub.getSubmittedAt());
                    }
                    submissions.save(sub);
                }
            }
        }

        // Attendance sessions per course (8 past sessions) + records for enrolled students
        for (Course c : allCourses) {
            var enrolled = enrollments.findByTenantIdAndCourseId(t, c.getId());
            for (int sIdx = 0; sIdx < 8; sIdx++) {
                ClassSession cs = new ClassSession();
                cs.setTenantId(t);
                cs.setCourseId(c.getId());
                cs.setTeacherId(c.getTeacherId());
                cs.setTitle("محاضرة " + (sIdx + 1) + " — " + c.getSubject());
                cs.setScheduledStart(Instant.now().minus((8 - sIdx) * 3L, ChronoUnit.DAYS));
                cs.setScheduledEnd(cs.getScheduledStart().plus(90, ChronoUnit.MINUTES));
                cs.setStatus("CLOSED");
                sessions.save(cs);
                for (var enr : enrolled) {
                    Student s = students.findByTenantIdAndId(t, enr.getStudentId()).orElseThrow();
                    double ability = 0.4 + (s.getId() % 10) / 16.0;
                    AttendanceRecord r = new AttendanceRecord();
                    r.setTenantId(t);
                    r.setSessionId(cs.getId());
                    r.setStudentId(s.getId());
                    double roll = rnd.nextDouble();
                    String status = roll < 0.75 * (0.6 + ability) ? "PRESENT" : (roll < 0.9 ? "LATE" : "ABSENT");
                    r.setStatus(status);
                    if ("LATE".equals(status)) r.setLateMinutes(5 + rnd.nextInt(20));
                    if (!"ABSENT".equals(status)) r.setArrivalTime(cs.getScheduledStart());
                    r.setMethod("MANUAL");
                    attendance.save(r);
                    if ("ABSENT".equals(status)) {
                        timeline.record(t, s.getId(), "ATTENDANCE", "غياب", "لم يحضر " + cs.getTitle(), "user-x", cs.getScheduledStart());
                    } else if ("LATE".equals(status)) {
                        timeline.record(t, s.getId(), "ATTENDANCE", "حضور متأخر", cs.getTitle(), "clock", cs.getScheduledStart());
                    }
                }
            }
        }

        // Invoices + installments per student
        for (Student s : created) {
            var enrs = enrollments.findByTenantIdAndStudentId(t, s.getId());
            BigDecimal total = BigDecimal.ZERO;
            for (var e : enrs) {
                total = total.add(courses.findByTenantIdAndId(t, e.getCourseId()).map(Course::getPrice).orElse(BigDecimal.ZERO));
            }
            if (total.signum() == 0) continue;
            Invoice inv = new Invoice();
            inv.setTenantId(t);
            inv.setStudentId(s.getId());
            inv.setTitle("رسوم الفصل الدراسي");
            inv.setTotalAmount(total);
            invoices.save(inv);
            BigDecimal per = total.divide(BigDecimal.valueOf(3), 0, java.math.RoundingMode.HALF_UP);
            int paidCount = rnd.nextInt(3); // 0..2 installments paid
            BigDecimal paid = BigDecimal.ZERO;
            for (int k = 0; k < 3; k++) {
                Installment inst = new Installment();
                inst.setTenantId(t);
                inst.setInvoiceId(inv.getId());
                inst.setSeq(k + 1);
                inst.setAmount(per);
                inst.setDueDate(LocalDate.now().plusDays((k - 1) * 30L));
                if (k < paidCount) {
                    inst.setStatus("PAID");
                    inst.setPaidAt(Instant.now().minus(20L * (paidCount - k), ChronoUnit.DAYS));
                    paid = paid.add(per);
                } else if (inst.getDueDate().isBefore(LocalDate.now())) {
                    inst.setStatus("OVERDUE");
                }
                installments.save(inst);
            }
            inv.setPaidAmount(paid);
            inv.setStatus(paid.signum() == 0 ? "PENDING" : (paid.compareTo(total) >= 0 ? "PAID" : "PARTIAL"));
            invoices.save(inv);
        }

        // Materialise metrics + assess risk (fires parent alerts on escalation)
        for (Student s : created) {
            metrics.recompute(t, s.getId());
            risk.assess(t, s.getId());
            gamification.award(t, s.getId(), 100 + rnd.nextInt(1400), "نشاط دراسي");
        }

        // Seed a few live-style parent notifications for the inbox (reload for fresh, materialised metrics)
        for (int i = 0; i < Math.min(4, created.size()); i++) {
            Student s = students.findByTenantIdAndId(t, created.get(i).getId()).orElseThrow();
            rulesEngine.onStudentAbsent(t, s.getId(), "الرياضيات");
            if (s.getAvgScore() < 60) rulesEngine.onLowScore(t, s.getId(), "امتحان منتصف الفصل", s.getAvgScore());
        }
    }

    private double scoreOut(double ability, double max) {
        double frac = Math.max(0.15, Math.min(1, ability + (rnd.nextDouble() - 0.5) * 0.3));
        return Math.round(frac * max * 10) / 10.0;
    }

    // ---------------- builders ----------------

    private Tenant seedTenant(String name, String slug, String color) {
        Tenant t = new Tenant();
        t.setName(name);
        t.setSlug(slug);
        t.setPrimaryColor(color);
        return tenants.save(t);
    }

    private Branch branch(Long t, String name, String address, String phone) {
        Branch b = new Branch();
        b.setTenantId(t); b.setName(name); b.setAddress(address); b.setPhone(phone);
        return branches.save(b);
    }

    private Room room(Long t, Long branchId, String name, int cap, boolean proj, boolean ac) {
        Room r = new Room();
        r.setTenantId(t); r.setBranchId(branchId); r.setName(name); r.setCapacity(cap);
        r.setHasProjector(proj); r.setHasAc(ac);
        return rooms.save(r);
    }

    private void weeklySlot(Long t, Course c, StudyGroup group, Room room, int day, LocalTime start, String color) {
        ScheduleSlot s = new ScheduleSlot();
        s.setTenantId(t); s.setCourseId(c.getId()); s.setGroupId(null); s.setTeacherId(c.getTeacherId());
        s.setRoomId(room == null ? null : room.getId()); s.setTitle("شرح وتطبيق — " + c.getSubject());
        s.setDayOfWeek(day); s.setStartTime(start); s.setEndTime(start.plusMinutes(90));
        s.setDeliveryMode(room == null ? "ONLINE" : "IN_PERSON");
        if (room == null) s.setMeetingUrl("https://meet.google.com/example-class");
        s.setColor(color); scheduleSlots.save(s);
    }

    private void teacherProfile(User u, String title, String subjects, String bio, String schedule, String photoUrl) {
        u.setTitle(title);
        u.setSubjects(subjects);
        u.setBio(bio);
        u.setSchedule(schedule);
        u.setPhotoUrl(photoUrl);
        users.save(u);
    }

    private User user(Long t, Long branchId, String name, String email, Role role) {
        User u = new User();
        u.setTenantId(t); u.setBranchId(branchId); u.setFullName(name); u.setEmail(email);
        u.setPhone("0100" + String.format("%07d", Math.abs(email.hashCode()) % 10000000));
        u.setRole(role); u.setPasswordHash(encoder.encode(demoPassword));
        return users.save(u);
    }

    private Course course(Long t, Long branchId, Long teacherId, String title, String subject, String level, String price) {
        Course c = new Course();
        c.setTenantId(t); c.setBranchId(branchId); c.setTeacherId(teacherId); c.setTitle(title);
        c.setSubject(subject); c.setGradeLevel(level); c.setPrice(new BigDecimal(price));
        c.setDescription("كورس متكامل في " + subject + " يشمل الشرح والامتحانات والواجبات.");
        return courses.save(c);
    }

    private CourseModule module(Long t, Long courseId, String title, int pos) {
        CourseModule m = new CourseModule();
        m.setTenantId(t); m.setCourseId(courseId); m.setTitle(title); m.setPosition(pos);
        return modules.save(m);
    }

    private Lesson lesson(Long t, Long moduleId, String title, int pos, int dur) {
        Lesson l = new Lesson();
        l.setTenantId(t); l.setModuleId(moduleId); l.setTitle(title); l.setPosition(pos); l.setDurationMin(dur);
        l.setContentText("قبل المشاهدة: تعرّف على الأفكار الأساسية في " + title + "، ثم دوّن ملاحظاتك وحل أسئلة التطبيق بعد الفيديو.");
        return lessons.save(l);
    }

    private void material(Long t, Long lessonId, String type, String title, Integer durationSec) {
        LessonMaterial m = new LessonMaterial();
        m.setTenantId(t); m.setLessonId(lessonId); m.setType(type); m.setTitle(title);
        m.setDurationSec(durationSec);
        if ("VIDEO".equals(type)) {
            m.setUrl(DEMO_VIDEO_URL);
            m.setDescription("فيديو تجريبي لمعاينة رحلة المشاهدة. يستبدله المدرس بتسجيل المحاضرة الفعلي.");
        }
        materials.save(m);
    }

    private StudyGroup group(Long t, Long courseId, Long teacherId, String name, int cap) {
        StudyGroup g = new StudyGroup();
        g.setTenantId(t); g.setCourseId(courseId); g.setTeacherId(teacherId); g.setName(name);
        g.setScheduleText(name); g.setCapacity(cap);
        return g;
    }

    private Assignment assignment(Long t, Long courseId, String title, Long by, int daysAgo) {
        Assignment a = new Assignment();
        a.setTenantId(t); a.setCourseId(courseId); a.setTitle(title); a.setCreatedBy(by);
        a.setDescription("قم بحل التمارين المرفقة وتسليمها قبل الموعد النهائي.");
        a.setStartAt(Instant.now().minus(daysAgo + 5L, ChronoUnit.DAYS));
        a.setDeadline(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        a.setMaxScore(20);
        return assignments.save(a);
    }

    private void grade(Long t, Long studentId, Long courseId, String title, String category, double score, double max) {
        GradeItem g = new GradeItem();
        g.setTenantId(t); g.setStudentId(studentId); g.setCourseId(courseId); g.setTitle(title);
        g.setCategory(category); g.setScore(score); g.setMaxScore(max); g.setSourceType("MANUAL");
        grades.save(g);
    }

    private void rule(Long t, String name, String trigger, Double threshold, String channels, boolean parent, boolean teacher) {
        NotificationRule r = new NotificationRule();
        r.setTenantId(t); r.setName(name); r.setTriggerType(trigger); r.setThreshold(threshold);
        r.setChannels(channels); r.setNotifyParent(parent); r.setNotifyTeacher(teacher);
        rules.save(r);
    }

    private void announcement(Long t, String title, String body) {
        Announcement a = new Announcement();
        a.setTenantId(t); a.setTitle(title); a.setBody(body); a.setAudience("ALL");
        announcements.save(a);
    }

    private void calendar(Long t, Long branchId, String type, String title, int inDays) {
        CalendarEvent e = new CalendarEvent();
        e.setTenantId(t); e.setBranchId(branchId); e.setType(type); e.setTitle(title);
        e.setStartAt(Instant.now().plus(inDays, ChronoUnit.DAYS));
        calendar.save(e);
    }

    // ---------------- question bank + exam ----------------

    private void seedQuestions(Long t) {
        mcq(t, "رياضيات", "الجبر", "EASY", "ما ناتج ٢ + ٣ × ٤؟", List.of("٢٠", "١٤", "٢٤", "١٠"), 1);
        mcq(t, "رياضيات", "الجبر", "MEDIUM", "حل المعادلة: س² − ٥س + ٦ = ٠", List.of("٢ و ٣", "١ و ٦", "−٢ و −٣", "٥ و ١"), 0);
        mcq(t, "رياضيات", "التفاضل", "HARD", "مشتقة الدالة س³ هي:", List.of("٣س²", "س²", "٣س", "س⁴/٤"), 0);
        tf(t, "رياضيات", "الهندسة", "EASY", "مجموع زوايا المثلث ١٨٠ درجة.", true);
        mcq(t, "فيزياء", "الكهرباء", "MEDIUM", "وحدة قياس المقاومة الكهربية هي:", List.of("أوم", "فولت", "أمبير", "وات"), 0);
        mcq(t, "فيزياء", "الميكانيكا", "HARD", "قانون نيوتن الثاني ينص على أن القوة تساوي:", List.of("الكتلة × التسارع", "الكتلة × السرعة", "الشغل ÷ الزمن", "الطاقة × الزمن"), 0);
        tf(t, "فيزياء", "الحرارة", "EASY", "تنتقل الحرارة من الجسم الأبرد إلى الأسخن تلقائياً.", false);
        mcq(t, "كيمياء", "الجدول الدوري", "EASY", "الرمز الكيميائي للأكسجين هو:", List.of("O", "Ox", "Og", "Om"), 0);
        mcq(t, "كيمياء", "التفاعلات", "MEDIUM", "الرقم الهيدروجيني للمحلول المتعادل هو:", List.of("٧", "٠", "١٤", "١"), 0);
        mcq(t, "لغة عربية", "النحو", "EASY", "علامة رفع الفاعل هي:", List.of("الضمة", "الفتحة", "الكسرة", "السكون"), 0);
        fill(t, "لغة عربية", "البلاغة", "MEDIUM", "التشبيه الذي حُذف منه وجه الشبه وأداة التشبيه يسمى التشبيه ______.", "البليغ");
        numeric(t, "رياضيات", "الحساب", "EASY", "كم يساوي جذر العدد ١٤٤؟", "12");
    }

    private void mcq(Long t, String subject, String chapter, String diff, String stem, List<String> opts, int correctIdx) {
        Question q = baseQ(t, subject, chapter, diff, "MCQ", stem, null);
        for (int i = 0; i < opts.size(); i++) option(t, q.getId(), opts.get(i), i == correctIdx, i);
    }

    private void tf(Long t, String subject, String chapter, String diff, String stem, boolean answer) {
        Question q = baseQ(t, subject, chapter, diff, "TRUE_FALSE", stem, null);
        option(t, q.getId(), "صح", answer, 0);
        option(t, q.getId(), "خطأ", !answer, 1);
    }

    private void fill(Long t, String subject, String chapter, String diff, String stem, String answer) {
        baseQ(t, subject, chapter, diff, "FILL_BLANK", stem, answer);
    }

    private void numeric(Long t, String subject, String chapter, String diff, String stem, String answer) {
        baseQ(t, subject, chapter, diff, "NUMERIC", stem, answer);
    }

    private Question baseQ(Long t, String subject, String chapter, String diff, String type, String stem, String answer) {
        Question q = new Question();
        q.setTenantId(t); q.setSubject(subject); q.setChapter(chapter); q.setDifficulty(diff);
        q.setType(type); q.setStem(stem); q.setPoints(1); q.setCorrectAnswer(answer);
        return questions.save(q);
    }

    private void option(Long t, Long qid, String text, boolean correct, int pos) {
        QuestionOption o = new QuestionOption();
        o.setTenantId(t); o.setQuestionId(qid); o.setText(text); o.setCorrect(correct); o.setPosition(pos);
        options.save(o);
    }

    private void seedExam(Long t, Course course, Long teacherId) {
        Exam e = new Exam();
        e.setTenantId(t); e.setCourseId(course.getId()); e.setTitle("امتحان الرياضيات الشهري");
        e.setDescription("امتحان شامل على الفصلين الأول والثاني.");
        e.setDurationMinutes(45); e.setPassPercent(50); e.setShuffleQuestions(true); e.setShuffleOptions(true);
        e.setDetectTabSwitch(true); e.setFullscreen(true); e.setStatus("PUBLISHED"); e.setCreatedBy(teacherId);
        exams.save(e);
        double total = 0;
        int pos = 0;
        for (Question q : questions.findByTenantIdAndSubjectAndDifficulty(t, "رياضيات", "EASY")) {
            addExamQuestion(t, e.getId(), q.getId(), pos++); total += q.getPoints();
        }
        for (Question q : questions.findByTenantIdAndSubjectAndDifficulty(t, "رياضيات", "MEDIUM")) {
            addExamQuestion(t, e.getId(), q.getId(), pos++); total += q.getPoints();
        }
        e.setTotalPoints(total);
        exams.save(e);
    }

    private void addExamQuestion(Long t, Long examId, Long qid, int pos) {
        ExamQuestion eq = new ExamQuestion();
        eq.setTenantId(t); eq.setExamId(examId); eq.setQuestionId(qid); eq.setPosition(pos);
        examQuestions.save(eq);
    }
}
