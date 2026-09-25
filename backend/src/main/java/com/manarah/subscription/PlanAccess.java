package com.manarah.subscription;

import com.manarah.academy.BundleEnrollmentRepository;
import com.manarah.academy.BundleSubscriptionRepository;
import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.common.SchoolYears;
import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.CourseRequests;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.notification.NotificationService;
import com.manarah.student.repo.StudentRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.manarah.subscription.PlanSubscription.*;

/**
 * What a subscription to "a year and a subject with this teacher" opens, and for how long.
 *
 * <p>A plan covers every published course of its teacher whose year and subject match — a course with no year
 * belongs to every year of its subject — including courses the teacher adds later. While a period runs, the student
 * has an ACTIVE enrollment in each of those courses, so exams, homework, the schedule and every count keep working
 * off enrollments as they always have. The enrollments a plan opened are recorded ({@link PlanEnrollment}); when no
 * period runs any more they are locked ({@link #LOCKED}) and reopened on renewal. A plan never touches an enrollment
 * something else granted (a package, a course code, a free course) and never reopens one the teacher closed.
 */
@Service
public class PlanAccess {
    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    /** Enrollment status of a plan course while none of its plans runs for the student. */
    public static final String LOCKED = "EXPIRED";
    private static final Duration REMIND_BEFORE = Duration.ofDays(3);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ar-EG")).withZone(CAIRO);

    private final SubscriptionPlanRepository plans;
    private final PlanSubscriptionRepository subs;
    private final PlanEnrollmentRepository planEnrollments;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final TeacherAcademyRepository academies;
    private final StudentRepository students;
    private final BundleEnrollmentRepository bundleEnrollments;
    private final BundleSubscriptionRepository bundleSubs;
    private final NotificationService notifications;

    public PlanAccess(SubscriptionPlanRepository plans, PlanSubscriptionRepository subs, PlanEnrollmentRepository planEnrollments,
                      EnrollmentRepository enrollments, CourseRepository courses, TeacherAcademyRepository academies,
                      StudentRepository students, BundleEnrollmentRepository bundleEnrollments,
                      BundleSubscriptionRepository bundleSubs, NotificationService notifications) {
        this.plans = plans; this.subs = subs; this.planEnrollments = planEnrollments; this.enrollments = enrollments;
        this.courses = courses; this.academies = academies; this.students = students;
        this.bundleEnrollments = bundleEnrollments; this.bundleSubs = bundleSubs; this.notifications = notifications;
    }

    // ---- Which plan a course belongs to ------------------------------------------------------------------------

    /** A course's subject, or its teacher's subject when the course doesn't name one. */
    public String subjectOf(Course c) {
        if (!blank(c.getSubject())) return c.getSubject().trim();
        return academies.findByTenantId(c.getTenantId()).map(a -> Objects.toString(a.getSubject(), "")).orElse("").trim();
    }

    public boolean covers(SubscriptionPlan p, Course c) {
        return p.getTenantId().equals(c.getTenantId()) && "ACTIVE".equals(c.getStatus())
                && p.getSubjectKey().equals(SchoolYears.subjectKey(subjectOf(c)))
                && (blank(c.getGrade()) || p.getYearKey().equals(SchoolYears.key(c.getGrade())));
    }

    public List<Course> coursesOf(SubscriptionPlan p) {
        return courses.findByTenantId(p.getTenantId()).stream().filter(c -> covers(p, c)).toList();
    }

    /** The plan a course is sold under for this student: the course's year, or the student's own year for a course with none. */
    public Optional<SubscriptionPlan> findPlanFor(Course c, String studentGrade) {
        String year = !blank(c.getGrade()) ? c.getGrade() : studentGrade;
        if (blank(year)) return Optional.empty();
        return plans.findByTenantIdAndYearKeyAndSubjectKey(c.getTenantId(), SchoolYears.key(year), SchoolYears.subjectKey(subjectOf(c)));
    }

    /** Same, creating the plan (with no price yet) the first time a student asks for it. */
    @Transactional
    public SubscriptionPlan planFor(Course c, String studentGrade) {
        String year = !blank(c.getGrade()) ? c.getGrade().trim() : Objects.toString(studentGrade, "").trim();
        if (year.isEmpty()) throw new BadRequestException("اختار سنتك الدراسية الأول عشان تشترك في كورسات سنتك");
        return planOrCreate(c.getTenantId(), year, subjectOf(c));
    }

    @Transactional
    public SubscriptionPlan planOrCreate(Long tenantId, String yearLabel, String subject) {
        String yearKey = SchoolYears.key(yearLabel), subjectKey = SchoolYears.subjectKey(subject);
        return plans.findByTenantIdAndYearKeyAndSubjectKey(tenantId, yearKey, subjectKey).orElseGet(() -> {
            SubscriptionPlan p = new SubscriptionPlan();
            p.setTenantId(tenantId); p.setYearKey(yearKey); p.setYearLabel(yearLabel.trim());
            p.setSubjectKey(subjectKey); p.setSubject(Objects.toString(subject, "").trim());
            return plans.save(p);
        });
    }

    // ---- Periods -------------------------------------------------------------------------------------------------

    /** Whether a period of this plan runs for the student — now, or paid ahead. */
    public boolean running(Long planId, Long studentId, Instant now) {
        return subs.findByPlanIdAndStudentIdOrderByIdDesc(planId, studentId).stream().anyMatch(s -> runs(s, now));
    }

    static boolean runs(PlanSubscription s, Instant now) {
        return ACTIVE.equals(s.getStatus()) && s.getEndsAt() != null && s.getEndsAt().isAfter(now);
    }

    public Optional<PlanSubscription> pending(Long planId, Long studentId) {
        return subs.findByPlanIdAndStudentIdOrderByIdDesc(planId, studentId).stream().filter(s -> PENDING.equals(s.getStatus())).findFirst();
    }

    /** The student asks to subscribe (or renew): one waiting request per plan, and the teacher hears about it. */
    @Transactional
    public PlanSubscription request(SubscriptionPlan p, Long studentId) {
        if (!p.isActive()) throw new BadRequestException("الاشتراك ده مش متاح دلوقتي. تواصل مع المدرس.");
        var waiting = pending(p.getId(), studentId);
        if (waiting.isPresent()) return waiting.get();
        PlanSubscription s = fresh(p, studentId, "REQUEST");
        subs.save(s);
        tellTeacher(p, studentId);
        return s;
    }

    /** A period with no request before it — the teacher renewing at the desk, or a code. Answers a waiting request if there is one. */
    @Transactional
    public PlanSubscription grant(SubscriptionPlan p, Long studentId, Long by, String source, Long codeId) {
        PlanSubscription s = pending(p.getId(), studentId).orElseGet(() -> fresh(p, studentId, source));
        s.setCodeId(codeId);
        return start(s, p, by, source);
    }

    /** Starts a period: now, or when the running one ends if the student renewed early. Months and price are fixed here. */
    @Transactional
    public PlanSubscription start(PlanSubscription s, SubscriptionPlan p, Long by, String source) {
        Instant now = Instant.now();
        Instant from = subs.findByPlanIdAndStudentIdOrderByIdDesc(p.getId(), s.getStudentId()).stream()
                .filter(x -> runs(x, now) && !Objects.equals(x.getId(), s.getId()))
                .map(PlanSubscription::getEndsAt).max(Comparator.naturalOrder()).orElse(now);
        s.setMonths(p.getMonths());
        s.setPrice(p.finalPrice());
        s.setStartsAt(from);
        s.setEndsAt(from.atZone(CAIRO).plusMonths(p.getMonths()).toInstant());
        s.setStatus(ACTIVE);
        s.setActivatedAt(now);
        s.setActivatedBy(by);
        if (source != null) s.setSource(source);
        subs.save(s);
        open(p, s.getStudentId());
        tellStudent(s, "اشتراكك اتفعّل: " + label(p),
                "من " + DAY.format(s.getStartsAt()) + " لحد " + DAY.format(s.getEndsAt()) + ". كل كورسات " + label(p) + " مفتوحة ليك.");
        return s;
    }

    /** The teacher stops a period early: what it opened is locked unless another period still runs. */
    @Transactional
    public void cancel(PlanSubscription s) {
        Instant now = Instant.now();
        s.setStatus(CANCELLED);
        s.setEndedAt(now);
        subs.save(s);
        plans.findById(s.getPlanId()).ifPresent(p -> close(p, s.getStudentId(), now));
    }

    private PlanSubscription fresh(SubscriptionPlan p, Long studentId, String source) {
        PlanSubscription s = new PlanSubscription();
        s.setTenantId(p.getTenantId()); s.setPlanId(p.getId()); s.setStudentId(studentId);
        s.setStatus(PENDING); s.setMonths(p.getMonths()); s.setPrice(p.finalPrice()); s.setSource(source);
        return s;
    }

    // ---- Opening and locking the plan's courses ------------------------------------------------------------------

    public void open(SubscriptionPlan p, Long studentId) {
        for (Course c : coursesOf(p)) openCourse(p, studentId, c);
    }

    void openCourse(SubscriptionPlan p, Long studentId, Course c) {
        Enrollment e = enrollments.findByTenantIdAndStudentIdAndCourseId(p.getTenantId(), studentId, c.getId()).orElse(null);
        if (e == null) {
            e = new Enrollment();
            e.setTenantId(p.getTenantId()); e.setStudentId(studentId); e.setCourseId(c.getId()); e.setStatus("ACTIVE");
            enrollments.save(e);
            record(p, studentId, e);
            return;
        }
        String status = e.getStatus();
        if (LOCKED.equals(status) || CourseRequests.WAITING.equals(status)) {
            e.setStatus("ACTIVE");
            enrollments.save(e);
            record(p, studentId, e);
        } else if ("ACTIVE".equals(status) && !planEnrollments.findByEnrollmentId(e.getId()).isEmpty()) {
            // Already open through another plan: this one shares it, so either plan keeps it open.
            record(p, studentId, e);
        }
        // Open through something else (package, code, free) — not ours to track. Closed by the teacher — stays closed.
    }

    private void record(SubscriptionPlan p, Long studentId, Enrollment e) {
        if (planEnrollments.existsByPlanIdAndEnrollmentId(p.getId(), e.getId())) return;
        PlanEnrollment pe = new PlanEnrollment();
        pe.setPlanId(p.getId()); pe.setStudentId(studentId); pe.setEnrollmentId(e.getId());
        planEnrollments.save(pe);
    }

    /** No period of the plan runs for the student any more: lock what it opened, unless something else still grants it. */
    void close(SubscriptionPlan p, Long studentId, Instant now) {
        if (running(p.getId(), studentId, now)) return;
        for (PlanEnrollment pe : planEnrollments.findByPlanIdAndStudentId(p.getId(), studentId)) {
            Enrollment e = enrollments.findById(pe.getEnrollmentId()).orElse(null);
            if (e == null || !"ACTIVE".equals(e.getStatus())) continue;
            Course c = courses.findById(e.getCourseId()).orElse(null);
            if (c != null && CourseRequests.isFree(c)) continue;
            if (grantedElsewhere(e, p, studentId, now)) continue;
            e.setStatus(LOCKED);
            enrollments.save(e);
        }
    }

    private boolean grantedElsewhere(Enrollment e, SubscriptionPlan p, Long studentId, Instant now) {
        for (PlanEnrollment other : planEnrollments.findByEnrollmentId(e.getId()))
            if (!other.getPlanId().equals(p.getId()) && running(other.getPlanId(), studentId, now)) return true;
        for (var be : bundleEnrollments.findByEnrollmentId(e.getId()))
            if (bundleSubs.findById(be.getSubscriptionId()).map(b -> "ACTIVE".equals(b.getStatus())).orElse(false)) return true;
        return false;
    }

    /** A course the teacher just published opens at once for everyone whose plan covers it. */
    @EventListener
    public void onCourseOffered(DomainEvents.CourseOffered e) {
        Course c = courses.findByTenantIdAndId(e.tenantId(), e.courseId()).orElse(null);
        if (c == null || !"ACTIVE".equals(c.getStatus())) return;
        Instant now = Instant.now();
        for (SubscriptionPlan p : plans.findByTenantId(e.tenantId())) {
            if (!covers(p, c)) continue;
            subs.findByPlanIdAndStatus(p.getId(), ACTIVE).stream().filter(s -> runs(s, now))
                    .map(PlanSubscription::getStudentId).collect(Collectors.toSet())
                    .forEach(studentId -> openCourse(p, studentId, c));
        }
    }

    // ---- Periods running out (see PlanExpiryJob) -----------------------------------------------------------------

    /** Ends every period that ran out by {@code now} and locks what it opened. Returns how many ended. */
    @Transactional
    public int expire(Instant now) {
        int ended = 0;
        for (PlanSubscription s : subs.findByStatus(ACTIVE)) {
            if (s.getEndsAt() == null || s.getEndsAt().isAfter(now)) continue;
            s.setStatus(ENDED);
            s.setEndedAt(now);
            subs.save(s);
            ended++;
            var plan = plans.findById(s.getPlanId()).orElse(null);
            if (plan == null) continue;
            close(plan, s.getStudentId(), now);
            if (!running(plan.getId(), s.getStudentId(), now))
                tellStudent(s, "اشتراك " + label(plan) + " خلص", "خلص يوم " + DAY.format(s.getEndsAt())
                        + ". جدّده من «كورساتي» أو من ملفك الشخصي عشان الكورسات تتفتح تاني.");
        }
        return ended;
    }

    /** Tells students once, a few days ahead, that a period ends — unless they already renewed. */
    @Transactional
    public int remind(Instant now) {
        int reminded = 0;
        for (PlanSubscription s : subs.findByStatus(ACTIVE)) {
            if (s.getRemindedAt() != null || s.getEndsAt() == null || !s.getEndsAt().isAfter(now)
                    || s.getEndsAt().isAfter(now.plus(REMIND_BEFORE))) continue;
            s.setRemindedAt(now);
            subs.save(s);
            boolean renewed = subs.findByPlanIdAndStudentIdOrderByIdDesc(s.getPlanId(), s.getStudentId()).stream()
                    .anyMatch(x -> !x.getId().equals(s.getId()) && runs(x, now) && x.getStartsAt() != null && !x.getStartsAt().isBefore(s.getEndsAt()));
            if (renewed) continue;
            plans.findById(s.getPlanId()).ifPresent(p -> tellStudent(s, "اشتراكك هيخلص قريب",
                    "اشتراك " + label(p) + " هيخلص يوم " + DAY.format(s.getEndsAt()) + ". جدّده قبلها عشان الكورسات ما تتقفلش."));
            reminded++;
        }
        return reminded;
    }

    // ---- Messages ------------------------------------------------------------------------------------------------

    public static String label(SubscriptionPlan p) {
        return p.getYearLabel() + (blank(p.getSubject()) ? "" : " — " + p.getSubject());
    }

    public static String day(Instant at) { return at == null ? "" : DAY.format(at); }

    private void tellTeacher(SubscriptionPlan p, Long studentId) {
        Long teacher = academies.findByTenantId(p.getTenantId()).map(TeacherAcademy::getTeacherId).orElse(null);
        if (teacher == null) return;
        String name = students.findById(studentId).map(s -> s.getFullName()).orElse("طالب");
        notifications.notify(p.getTenantId(), new NotifyCommand(teacher, null, "طلب اشتراك جديد",
                name + " عايز يشترك في " + label(p) + " ومستني يدفع. أول ما تستلم الفلوس فعّله من «طلبات الاشتراك» في لوحتك أو ابعتله كود.",
                "SUBSCRIPTION", "SubscriptionPlan", p.getId(), List.of("IN_APP")));
    }

    private void tellStudent(PlanSubscription s, String title, String body) {
        students.findById(s.getStudentId()).filter(st -> st.getUserId() != null).ifPresent(st ->
                notifications.notify(s.getTenantId(), new NotifyCommand(st.getUserId(), null, title, body,
                        "SUBSCRIPTION", "SubscriptionPlan", s.getPlanId(), List.of("IN_APP"))));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
