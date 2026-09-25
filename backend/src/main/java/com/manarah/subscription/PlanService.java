package com.manarah.subscription;

import com.manarah.academy.LinkedStudentAccounts;
import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.academy.TeacherScope;
import com.manarah.common.SchoolYears;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.manarah.subscription.PlanSubscription.*;

/**
 * The teacher's side of year-and-subject subscriptions (prices, requests, subscribers, codes) and the student's own
 * list of subscriptions across all of their teachers. The rules of what a subscription opens live in {@link PlanAccess}.
 */
@Service
public class PlanService {
    public static final String CODE_PREFIX = "SUB-";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> STAGES = List.of("kg", "primary", "preparatory", "secondary");

    private final SubscriptionPlanRepository plans;
    private final PlanSubscriptionRepository subs;
    private final PlanAccessCodeRepository codes;
    private final PlanAccess access;
    private final TeacherAcademyRepository academies;
    private final TeacherScope teacherScope;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final UserRepository users;
    private final LinkedStudentAccounts linked;
    private final com.manarah.audit.AuditService audit;
    private final com.manarah.billing.PaymentSubmissionRepository payments;

    public PlanService(SubscriptionPlanRepository plans, PlanSubscriptionRepository subs, PlanAccessCodeRepository codes,
                       PlanAccess access, TeacherAcademyRepository academies, TeacherScope teacherScope, CourseRepository courses,
                       StudentRepository students, UserRepository users, LinkedStudentAccounts linked,
                       com.manarah.audit.AuditService audit, com.manarah.billing.PaymentSubmissionRepository payments) {
        this.payments = payments;
        this.plans = plans; this.subs = subs; this.codes = codes; this.access = access; this.academies = academies;
        this.teacherScope = teacherScope; this.courses = courses; this.students = students; this.users = users;
        this.linked = linked; this.audit = audit;
    }

    // ---- Teacher: plans and prices -------------------------------------------------------------------------------

    public record PlanRow(Long id, String year, String subject, BigDecimal price, int discountPercent, BigDecimal finalPrice,
                          int months, boolean active, long running, long pending, List<String> courses) {}
    public record PlanInput(Long id, String year, String subject, BigDecimal price, Integer discountPercent, Integer months, Boolean active) {}

    /** Every plan, plus a row (with no id yet) for each year and subject the teacher's courses cover that isn't priced. */
    public List<PlanRow> plans(UserPrincipal actor) {
        Long tenantId = manage(actor);
        Map<String, SubscriptionPlan> byKey = new LinkedHashMap<>();
        plans.findByTenantId(tenantId).forEach(p -> byKey.put(p.getYearKey() + "|" + p.getSubjectKey(), p));
        Map<String, SubscriptionPlan> suggested = new LinkedHashMap<>();
        for (Course c : courses.findByTenantId(tenantId)) {
            if (!"ACTIVE".equals(c.getStatus()) || c.getGrade() == null || c.getGrade().isBlank()) continue;
            String subject = access.subjectOf(c), key = SchoolYears.key(c.getGrade()) + "|" + SchoolYears.subjectKey(subject);
            if (byKey.containsKey(key) || suggested.containsKey(key)) continue;
            SubscriptionPlan v = new SubscriptionPlan();
            v.setTenantId(tenantId); v.setYearKey(SchoolYears.key(c.getGrade())); v.setYearLabel(c.getGrade().trim());
            v.setSubjectKey(SchoolYears.subjectKey(subject)); v.setSubject(subject);
            suggested.put(key, v);
        }
        List<SubscriptionPlan> all = new ArrayList<>(byKey.values());
        all.addAll(suggested.values());
        all.sort(Comparator.comparing((SubscriptionPlan p) -> yearRank(p.getYearKey())).thenComparing(SubscriptionPlan::getSubject));
        return all.stream().map(this::row).toList();
    }

    @Transactional
    public PlanRow save(UserPrincipal actor, PlanInput in) {
        Long tenantId = manage(actor);
        int months = in.months() == null ? 2 : in.months();
        int discount = in.discountPercent() == null ? 0 : in.discountPercent();
        if (months < 1 || months > 24) throw new BadRequestException("مدة الاشتراك من شهر لحد ٢٤ شهر");
        if (discount < 0 || discount > 90) throw new BadRequestException("الخصم من ٠ لحد ٩٠٪");
        if (in.price() != null && in.price().signum() <= 0) throw new BadRequestException("اكتب سعر أكبر من صفر، أو سيبه فاضي لو لسه ما حددتوش");
        SubscriptionPlan p;
        if (in.id() != null) {
            p = plans.findById(in.id()).filter(x -> x.getTenantId().equals(tenantId)).orElseThrow(() -> NotFoundException.of("الاشتراك", in.id()));
        } else {
            if (in.year() == null || in.year().isBlank()) throw new BadRequestException("اختار السنة الدراسية");
            String subject = in.subject() == null || in.subject().isBlank()
                    ? academies.findByTenantId(tenantId).map(a -> Objects.toString(a.getSubject(), "")).orElse("") : in.subject();
            p = access.planOrCreate(tenantId, in.year(), subject);
        }
        String before = p.getPrice() + "/" + p.getDiscountPercent() + "%/" + p.getMonths() + "m/" + p.isActive();
        p.setPrice(in.price());
        p.setDiscountPercent(discount);
        p.setMonths(months);
        if (in.active() != null) p.setActive(in.active());
        p.setUpdatedAt(Instant.now());
        plans.save(p);
        audit.record(actor, "SUBSCRIPTION_PLAN_SAVED", "SubscriptionPlan", p.getId(), before,
                p.getPrice() + "/" + p.getDiscountPercent() + "%/" + p.getMonths() + "m/" + p.isActive());
        return row(p);
    }

    private PlanRow row(SubscriptionPlan p) {
        Instant now = Instant.now();
        long running = 0, pending = 0;
        List<String> titles;
        if (p.getId() != null) {
            var all = subs.findByPlanIdOrderByIdDesc(p.getId());
            running = all.stream().filter(s -> PlanAccess.runs(s, now)).map(PlanSubscription::getStudentId).distinct().count();
            pending = all.stream().filter(s -> PENDING.equals(s.getStatus())).count();
            titles = access.coursesOf(p).stream().map(Course::getTitle).toList();
        } else {
            titles = courses.findByTenantId(p.getTenantId()).stream().filter(c -> access.covers(p, c)).map(Course::getTitle).toList();
        }
        return new PlanRow(p.getId(), p.getYearLabel(), p.getSubject(), p.getPrice(), p.getDiscountPercent(), p.finalPrice(),
                p.getMonths(), p.isActive(), running, pending, titles);
    }

    // ---- Teacher: requests and subscribers -----------------------------------------------------------------------

    public record RequestRow(Long id, Long planId, String plan, BigDecimal price, int months, Long studentId, String studentName,
                             String email, String phone, String grade, Instant requestedAt, boolean renewal,
                             String paymentStatus, String paymentMethod) {}
    public record SubscriberRow(Long subscriptionId, Long studentId, String studentName, String email, String phone,
                                String status, Instant startsAt, Instant endsAt, long daysLeft) {}

    public List<RequestRow> requests(UserPrincipal actor) {
        Long tenantId = manage(actor);
        return subs.findByTenantIdAndStatus(tenantId, PENDING).stream()
                .sorted(Comparator.comparing(PlanSubscription::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> {
                    SubscriptionPlan p = plans.findById(s.getPlanId()).orElseThrow();
                    Student st = students.findById(s.getStudentId()).orElse(null);
                    boolean renewal = subs.findByPlanIdAndStudentIdOrderByIdDesc(p.getId(), s.getStudentId()).stream()
                            .anyMatch(x -> !PENDING.equals(x.getStatus()));
                    var paid = payments.findFirstByPlanSubscriptionIdOrderByIdDesc(s.getId()).orElse(null);
                    return new RequestRow(s.getId(), p.getId(), PlanAccess.label(p), p.finalPrice(), p.getMonths(), s.getStudentId(),
                            st == null ? "طالب" : st.getFullName(), email(st), st == null ? "" : Objects.toString(st.getPhone(), ""),
                            st == null ? "" : Objects.toString(st.getGrade(), ""), s.getRequestedAt(), renewal,
                            paid == null ? null : paid.getStatus(), paid == null ? null : paid.getMethodCode());
                }).toList();
    }

    /** One row per student: their running period (to its latest end, renewals included) or else their latest one. */
    public List<SubscriberRow> subscribers(UserPrincipal actor, Long planId) {
        SubscriptionPlan p = plan(actor, planId);
        Instant now = Instant.now();
        Map<Long, List<PlanSubscription>> byStudent = subs.findByPlanIdOrderByIdDesc(p.getId()).stream()
                .filter(s -> !PENDING.equals(s.getStatus()))
                .collect(Collectors.groupingBy(PlanSubscription::getStudentId, LinkedHashMap::new, Collectors.toList()));
        List<SubscriberRow> out = new ArrayList<>();
        byStudent.forEach((studentId, list) -> {
            Student st = students.findById(studentId).orElse(null);
            var running = list.stream().filter(s -> PlanAccess.runs(s, now)).toList();
            PlanSubscription shown = running.isEmpty() ? list.get(0)
                    : running.stream().min(Comparator.comparing(PlanSubscription::getStartsAt)).orElseThrow();
            Instant ends = running.isEmpty() ? shown.getEndsAt()
                    : running.stream().map(PlanSubscription::getEndsAt).max(Comparator.naturalOrder()).orElseThrow();
            out.add(new SubscriberRow(shown.getId(), studentId, st == null ? "طالب" : st.getFullName(), email(st),
                    st == null ? "" : Objects.toString(st.getPhone(), ""), running.isEmpty() ? shown.getStatus() : ACTIVE,
                    shown.getStartsAt(), ends, running.isEmpty() ? 0 : daysLeft(ends, now)));
        });
        out.sort(Comparator.comparing((SubscriberRow r) -> !ACTIVE.equals(r.status())).thenComparing(SubscriberRow::endsAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    @Transactional
    public void activate(UserPrincipal actor, Long subscriptionId) {
        PlanSubscription s = subscription(actor, subscriptionId);
        if (!PENDING.equals(s.getStatus())) throw new ConflictException("الطلب ده اتعامل معاه قبل كده");
        SubscriptionPlan p = plans.findById(s.getPlanId()).orElseThrow();
        access.start(s, p, actor.getId(), null);
        audit.record(actor, "SUBSCRIPTION_ACTIVATED", "PlanSubscription", s.getId(), PENDING, ACTIVE + " until " + s.getEndsAt());
    }

    @Transactional
    public void reject(UserPrincipal actor, Long subscriptionId) {
        PlanSubscription s = subscription(actor, subscriptionId);
        if (!PENDING.equals(s.getStatus())) throw new ConflictException("الطلب ده اتعامل معاه قبل كده");
        subs.delete(s);
        audit.record(actor, "SUBSCRIPTION_REQUEST_REJECTED", "PlanSubscription", s.getId(), PENDING, null);
    }

    @Transactional
    public void cancel(UserPrincipal actor, Long subscriptionId) {
        PlanSubscription s = subscription(actor, subscriptionId);
        if (!ACTIVE.equals(s.getStatus())) throw new ConflictException("الاشتراك ده مش شغال");
        // Stopping a student stops every period they have of this plan, renewals paid ahead included.
        for (PlanSubscription x : subs.findByPlanIdAndStudentIdOrderByIdDesc(s.getPlanId(), s.getStudentId()))
            if (ACTIVE.equals(x.getStatus())) access.cancel(x);
        audit.record(actor, "SUBSCRIPTION_CANCELLED", "PlanSubscription", s.getId(), ACTIVE, CANCELLED);
    }

    /** The teacher took the money at the desk: a new period for this student, after the running one if any. */
    @Transactional
    public void renew(UserPrincipal actor, Long planId, Long studentId) {
        SubscriptionPlan p = plan(actor, planId);
        students.findByTenantIdAndId(p.getTenantId(), studentId).orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        PlanSubscription s = access.grant(p, studentId, actor.getId(), "TEACHER", null);
        audit.record(actor, "SUBSCRIPTION_RENEWED", "PlanSubscription", s.getId(), null, ACTIVE + " until " + s.getEndsAt());
    }

    // ---- Codes -----------------------------------------------------------------------------------------------------

    public record CodeRow(Long id, String code, String status, Instant createdAt, String usedBy, Instant usedAt) {}

    @Transactional
    public List<CodeRow> generate(UserPrincipal actor, Long planId, int count) {
        SubscriptionPlan p = plan(actor, planId);
        if (count < 1 || count > 50) throw new BadRequestException("من كود واحد لحد ٥٠ كود في المرة");
        List<CodeRow> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PlanAccessCode c = new PlanAccessCode();
            c.setTenantId(p.getTenantId()); c.setPlanId(p.getId()); c.setCode(freshCode()); c.setCreatedBy(actor.getId());
            codes.save(c);
            out.add(codeRow(c));
        }
        audit.record(actor, "SUBSCRIPTION_CODES_CREATED", "SubscriptionPlan", p.getId(), null, "count=" + count);
        return out;
    }

    public List<CodeRow> codes(UserPrincipal actor, Long planId) {
        return codes.findByPlanIdOrderByIdDesc(plan(actor, planId).getId()).stream().map(this::codeRow).toList();
    }

    public static boolean isPlanCode(String raw) {
        return raw != null && raw.trim().toUpperCase(Locale.ROOT).startsWith(CODE_PREFIX);
    }

    public PlanAccessCode findRedeemable(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        PlanAccessCode c = codes.findByCodeIgnoreCase(code).orElseThrow(() -> new BadRequestException("الكود غير صحيح"));
        if ("USED".equals(c.getStatus())) throw new ConflictException("هذا الكود مُستخدم بالفعل");
        if (!"UNUSED".equals(c.getStatus())) throw new BadRequestException("هذا الكود لم يعد صالحاً");
        return c;
    }

    public SubscriptionPlan planOf(PlanAccessCode c) { return plans.findById(c.getPlanId()).orElseThrow(); }

    /** Uses the code for this student: one period of its plan starts. */
    @Transactional
    public PlanSubscription use(PlanAccessCode c, Long studentId) {
        c.setStatus("USED"); c.setUsedByStudentId(studentId); c.setUsedAt(Instant.now());
        codes.save(c);
        return access.grant(planOf(c), studentId, null, "CODE", c.getId());
    }

    /** A signed-in student's code; a code from another teacher joins them to that teacher. Returns the seat to switch to, if any. */
    @Transactional
    public Long redeem(UserPrincipal actor, String raw) {
        if (actor.getRole() != com.manarah.identity.domain.Role.STUDENT) throw new ForbiddenException("الأكواد للطلاب");
        PlanAccessCode c = findRedeemable(raw);
        SubscriptionPlan p = planOf(c);
        if (p.getTenantId().equals(actor.getTenantId())) {
            use(c, me(actor).getId());
            return null;
        }
        Student seat = linked.rowForCode(users.findById(actor.getId()).orElseThrow(), p.getTenantId());
        use(c, seat.getId());
        return seat.getUserId();
    }

    private CodeRow codeRow(PlanAccessCode c) {
        String usedBy = c.getUsedByStudentId() == null ? null : students.findById(c.getUsedByStudentId()).map(Student::getFullName).orElse(null);
        return new CodeRow(c.getId(), c.getCode(), c.getStatus(), c.getCreatedAt(), usedBy, c.getUsedAt());
    }

    private String freshCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_PREFIX);
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            if (!codes.existsByCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("could not generate a unique code");
    }

    // ---- The student's own subscriptions -----------------------------------------------------------------------

    public record Period(Long id, String status, Instant startsAt, Instant endsAt, int months, BigDecimal price) {}
    public record MySubscription(Long planId, String teacher, String teacherPhoto, String slug, String year, String subject,
                                 String status, Instant startsAt, Instant endsAt, long daysLeft, int months,
                                 BigDecimal price, boolean renewalPending, Long seatUserId, boolean current, List<Period> periods) {}

    /** Every plan the student has asked for, with any teacher, newest first: when it started, when it ends, how long is left. */
    public List<MySubscription> mine(UserPrincipal actor) {
        Map<Long, Student> seats = linked.seatsOf(actor).stream().collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
        if (seats.isEmpty()) return List.of();
        Instant now = Instant.now();
        Map<Long, List<PlanSubscription>> byPlan = subs.findByStudentIdInOrderByIdDesc(seats.keySet()).stream()
                .collect(Collectors.groupingBy(PlanSubscription::getPlanId, LinkedHashMap::new, Collectors.toList()));
        List<MySubscription> out = new ArrayList<>();
        byPlan.forEach((planId, list) -> {
            SubscriptionPlan p = plans.findById(planId).orElse(null);
            if (p == null) return;
            Student seat = seats.get(list.get(0).getStudentId());
            TeacherAcademy a = academies.findByTenantId(p.getTenantId()).orElse(null);
            out.add(summary(p, list, now, a, seat, seat != null && seat.getTenantId().equals(actor.getTenantId())));
        });
        return out;
    }

    /** One plan as the student sees it (also used by the dashboard header). */
    public MySubscription summary(SubscriptionPlan p, List<PlanSubscription> list, Instant now, TeacherAcademy a, Student seat, boolean current) {
        var running = list.stream().filter(s -> PlanAccess.runs(s, now)).toList();
        boolean waiting = list.stream().anyMatch(s -> PENDING.equals(s.getStatus()));
        var done = list.stream().filter(s -> !PENDING.equals(s.getStatus())).toList();
        String status; Instant starts = null, ends = null;
        if (!running.isEmpty()) {
            status = ACTIVE;
            starts = running.stream().map(PlanSubscription::getStartsAt).min(Comparator.naturalOrder()).orElse(null);
            ends = running.stream().map(PlanSubscription::getEndsAt).max(Comparator.naturalOrder()).orElse(null);
        } else if (waiting && done.isEmpty()) {
            status = PENDING;
        } else if (!done.isEmpty()) {
            PlanSubscription last = done.get(0);
            status = CANCELLED.equals(last.getStatus()) ? CANCELLED : ENDED;
            starts = last.getStartsAt(); ends = last.getEndedAt() != null && last.getEndedAt().isBefore(last.getEndsAt()) ? last.getEndedAt() : last.getEndsAt();
        } else status = "NONE";
        List<Period> periods = done.stream().map(s -> new Period(s.getId(), s.getStatus(), s.getStartsAt(), s.getEndsAt(), s.getMonths(), s.getPrice())).toList();
        return new MySubscription(p.getId(), a == null ? "" : a.getName(), a == null ? "" : Objects.toString(a.getPhotoUrl(), ""),
                a == null ? "" : a.getSlug(), p.getYearLabel(), p.getSubject(), status, starts, ends,
                ACTIVE.equals(status) ? daysLeft(ends, now) : 0, p.getMonths(), p.finalPrice(), waiting && !PENDING.equals(status),
                seat == null ? null : seat.getUserId(), current, periods);
    }

    /** Subscribe to (or renew) a plan of a teacher the student has. */
    @Transactional
    public String request(UserPrincipal actor, Long planId) {
        SubscriptionPlan p = plans.findById(planId).orElseThrow(() -> NotFoundException.of("الاشتراك", planId));
        Student seat = linked.seatIn(actor, p.getTenantId())
                .orElseThrow(() -> new ForbiddenException("إنت مش مشترك مع المدرس ده. انضم له من صفحته الأول."));
        access.request(p, seat.getId());
        return PENDING;
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------------

    /** Plans are the teacher's own: the teacher, their assistant, or head office working inside the teacher's space. */
    private Long manage(UserPrincipal actor) {
        Long tenantId = actor.getTenantId();
        TeacherAcademy a = academies.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("اشتراكات السنين بتتظبط من جوه مساحة المدرس"));
        if (!actor.isAdmin() && !Objects.equals(teacherScope.teacherIdFor(actor), a.getTeacherId()))
            throw new ForbiddenException("الاشتراكات دي بيظبطها المدرس نفسه");
        return tenantId;
    }

    private SubscriptionPlan plan(UserPrincipal actor, Long planId) {
        Long tenantId = manage(actor);
        return plans.findById(planId).filter(p -> p.getTenantId().equals(tenantId)).orElseThrow(() -> NotFoundException.of("الاشتراك", planId));
    }

    private PlanSubscription subscription(UserPrincipal actor, Long id) {
        Long tenantId = manage(actor);
        return subs.findById(id).filter(s -> s.getTenantId().equals(tenantId)).orElseThrow(() -> NotFoundException.of("الاشتراك", id));
    }

    private Student me(UserPrincipal actor) {
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .orElseThrow(() -> new ForbiddenException("لا يوجد ملف طالب مرتبط بالحساب"));
    }

    private String email(Student st) {
        if (st == null || st.getUserId() == null) return "";
        return users.findById(st.getUserId()).map(u -> Objects.toString(linked.owner(u).getEmail(), "")).orElse("");
    }

    static long daysLeft(Instant ends, Instant now) {
        if (ends == null || !ends.isAfter(now)) return 0;
        return (Duration.between(now, ends).toHours() + 23) / 24;
    }

    private static int yearRank(String yearKey) {
        int dash = yearKey.lastIndexOf('-');
        int stage = dash < 0 ? -1 : STAGES.indexOf(yearKey.substring(0, dash));
        if (stage < 0) return 1000;
        try { return stage * 10 + Integer.parseInt(yearKey.substring(dash + 1)); } catch (NumberFormatException e) { return 1000; }
    }
}
