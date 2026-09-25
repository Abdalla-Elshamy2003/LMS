package com.manarah.billing;

import com.manarah.academy.BundleService;
import com.manarah.academy.LinkedStudentAccounts;
import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.notification.NotificationService;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import com.manarah.subscription.PlanAccess;
import com.manarah.subscription.PlanSubscription;
import com.manarah.subscription.PlanSubscriptionRepository;
import com.manarah.subscription.SubscriptionPlan;
import com.manarah.subscription.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The platform takes the money. Head office sets up the ways to pay (Vodafone Cash, InstaPay, Fawry — {@link PaymentMethod})
 * and every student pays through them: they pick a method, transfer, and send the transfer's reference and a receipt
 * photo for their subscription request. Head office checks it and approves — which starts the subscription — or
 * sends it back with a reason. Teachers can still open a subscription themselves for money taken at the desk.
 */
@Service
public class BillingService {
    private static final long MAX_RECEIPT_BYTES = 3L * 1024 * 1024;
    private static final long MAX_RECEIPT_PIXELS = 20_000_000L;

    private final PaymentMethodRepository methods;
    private final PaymentSubmissionRepository submissions;
    private final PlanSubscriptionRepository subs;
    private final SubscriptionPlanRepository plans;
    private final PlanAccess access;
    private final LinkedStudentAccounts linked;
    private final StudentRepository students;
    private final UserRepository users;
    private final TeacherAcademyRepository academies;
    private final BundleService bundles;
    private final NotificationService notifications;
    private final com.manarah.audit.AuditService audit;

    public BillingService(PaymentMethodRepository methods, PaymentSubmissionRepository submissions, PlanSubscriptionRepository subs,
                          SubscriptionPlanRepository plans, PlanAccess access, LinkedStudentAccounts linked, StudentRepository students,
                          UserRepository users, TeacherAcademyRepository academies, BundleService bundles,
                          NotificationService notifications, com.manarah.audit.AuditService audit) {
        this.methods = methods; this.submissions = submissions; this.subs = subs; this.plans = plans; this.access = access;
        this.linked = linked; this.students = students; this.users = users; this.academies = academies; this.bundles = bundles;
        this.notifications = notifications; this.audit = audit;
    }

    // ---- Ways to pay -----------------------------------------------------------------------------------------------

    public record MethodView(String code, String name, boolean enabled, String account, String accountName, String instructions) {}
    public record MethodInput(Boolean enabled, String account, String accountName, String instructions) {}

    /** What a student can pay with right now: the methods head office turned on and filled in. */
    public List<MethodView> enabledMethods() {
        return methods.findAllByOrderBySortOrderAsc().stream().filter(m -> m.isEnabled() && !m.getAccount().isBlank()).map(this::view).toList();
    }

    public List<MethodView> allMethods(UserPrincipal actor) {
        bundles.requireHeadOffice(actor);
        return methods.findAllByOrderBySortOrderAsc().stream().map(this::view).toList();
    }

    @Transactional
    public MethodView saveMethod(UserPrincipal actor, String code, MethodInput in) {
        bundles.requireHeadOffice(actor);
        PaymentMethod m = methods.findByCode(code).orElseThrow(() -> new NotFoundException("طريقة الدفع غير موجودة"));
        String account = trim(in.account(), 120, "رقم الحساب"), accountName = trim(in.accountName(), 120, "اسم الحساب");
        String instructions = trim(in.instructions(), 600, "التعليمات");
        boolean enabled = in.enabled() != null ? in.enabled() : m.isEnabled();
        if (enabled && account.isBlank()) throw new BadRequestException("اكتب رقم " + m.getName() + " الأول عشان تفعّلها");
        String before = m.isEnabled() + "/" + m.getAccount();
        m.setAccount(account); m.setAccountName(accountName); m.setInstructions(instructions); m.setEnabled(enabled);
        m.setUpdatedAt(Instant.now());
        methods.save(m);
        audit.record(actor, "PAYMENT_METHOD_SAVED", "PaymentMethod", m.getId(), before, m.isEnabled() + "/" + m.getAccount());
        return view(m);
    }

    private MethodView view(PaymentMethod m) {
        return new MethodView(m.getCode(), m.getName(), m.isEnabled(), m.getAccount(), m.getAccountName(), m.getInstructions());
    }

    // ---- The student sends a payment -----------------------------------------------------------------------------

    public record SubmissionView(Long id, String status, String method, BigDecimal amount, String reference, String sender,
                                 boolean hasReceipt, String note, Instant createdAt, Instant reviewedAt, Long subscriptionId,
                                 Long studentId, String studentName, String email, String phone, String teacher, String plan,
                                 int months, String subscriptionStatus) {}

    @Transactional
    public SubmissionView submit(UserPrincipal actor, Long subscriptionId, String methodCode, String reference, String sender,
                                 MultipartFile receipt) {
        if (actor.getRole() != Role.STUDENT) throw new ForbiddenException("الدفع من حساب الطالب");
        PlanSubscription s = subs.findById(subscriptionId).orElseThrow(() -> NotFoundException.of("طلب الاشتراك", subscriptionId));
        Student seat = linked.seatIn(actor, s.getTenantId()).filter(st -> st.getId().equals(s.getStudentId()))
                .orElseThrow(() -> new ForbiddenException("طلب الاشتراك ده مش بتاعك"));
        if (!PlanSubscription.PENDING.equals(s.getStatus())) throw new ConflictException("الاشتراك ده مش مستني دفع");
        PaymentMethod m = methods.findByCode(Objects.toString(methodCode, "")).filter(x -> x.isEnabled() && !x.getAccount().isBlank())
                .orElseThrow(() -> new BadRequestException("اختار طريقة دفع متاحة"));
        String ref = trim(reference, 80, "رقم العملية"), from = trim(sender, 80, "الرقم اللي حوّلت منه");
        boolean hasFile = receipt != null && !receipt.isEmpty();
        if (ref.isBlank() && !hasFile) throw new BadRequestException("اكتب رقم العملية أو ارفع صورة الإيصال");

        // A second send for the same request replaces the one still waiting, so head office checks one thing.
        PaymentSubmission p = submissions.findFirstByPlanSubscriptionIdOrderByIdDesc(s.getId())
                .filter(x -> PaymentSubmission.SUBMITTED.equals(x.getStatus())).orElseGet(PaymentSubmission::new);
        p.setTenantId(s.getTenantId()); p.setStudentId(seat.getId()); p.setPlanSubscriptionId(s.getId());
        p.setMethodCode(m.getCode()); p.setReference(ref); p.setSender(from); p.setStatus(PaymentSubmission.SUBMITTED);
        p.setNote(""); p.setCreatedAt(Instant.now());
        SubscriptionPlan plan = plans.findById(s.getPlanId()).orElseThrow();
        p.setAmount(s.getPrice() != null ? s.getPrice() : plan.finalPrice());
        if (hasFile) { p.setReceiptType(imageType(receipt)); p.setReceiptData(base64(receipt)); }
        submissions.save(p);
        tellHeadOffice(s.getTenantId(), "دفع جديد مستني المراجعة",
                seat.getFullName() + " حوّل " + (p.getAmount() == null ? "" : p.getAmount().toPlainString() + " ج.م ") + "بـ" + m.getName()
                        + " لاشتراك " + PlanAccess.label(plan) + ". راجعه من «المدفوعات».");
        return view(p);
    }

    /** The latest payment sent for a subscription request, for the student's own screens. */
    public Optional<PaymentSubmission> latestFor(Long subscriptionId) {
        return submissions.findFirstByPlanSubscriptionIdOrderByIdDesc(subscriptionId);
    }

    // ---- Head office reviews -------------------------------------------------------------------------------------

    public List<SubmissionView> list(UserPrincipal actor, String status) {
        List<Long> tenants = managedTenants(actor);
        if (tenants.isEmpty()) return List.of();
        return submissions.findByTenantIdInOrderByIdDesc(tenants).stream()
                .filter(p -> status == null || status.isBlank() || status.equalsIgnoreCase(p.getStatus()))
                .limit(300).map(this::view).toList();
    }

    public record Receipt(String type, byte[] data) {}

    /** The receipt photo: head office, or the student who sent it. */
    public Receipt receipt(UserPrincipal actor, Long id) {
        PaymentSubmission p = submissions.findById(id).orElseThrow(() -> NotFoundException.of("الدفع", id));
        boolean own = actor.getRole() == Role.STUDENT && linked.seatsOf(actor).stream().anyMatch(s -> s.getId().equals(p.getStudentId()));
        if (!own) {
            if (!managedTenants(actor).contains(p.getTenantId())) throw new ForbiddenException("مش مسموح");
        }
        if (p.getReceiptData() == null) throw new NotFoundException("مفيش صورة إيصال للدفع ده");
        return new Receipt(p.getReceiptType(), Base64.getDecoder().decode(p.getReceiptData()));
    }

    @Transactional
    public SubmissionView approve(UserPrincipal actor, Long id) {
        PaymentSubmission p = reviewable(actor, id);
        PlanSubscription s = subs.findById(p.getPlanSubscriptionId()).orElseThrow();
        // Still waiting: the payment starts it. Already opened (the teacher took cash meanwhile): just record the payment.
        if (PlanSubscription.PENDING.equals(s.getStatus())) access.start(s, plans.findById(s.getPlanId()).orElseThrow(), actor.getId(), "PAYMENT");
        p.setStatus(PaymentSubmission.APPROVED); p.setReviewedBy(actor.getId()); p.setReviewedAt(Instant.now());
        submissions.save(p);
        audit.record(actor, "PAYMENT_APPROVED", "PaymentSubmission", p.getId(), PaymentSubmission.SUBMITTED, PaymentSubmission.APPROVED);
        return view(p);
    }

    @Transactional
    public SubmissionView reject(UserPrincipal actor, Long id, String reason) {
        PaymentSubmission p = reviewable(actor, id);
        String why = trim(reason, 300, "السبب");
        if (why.isBlank()) throw new BadRequestException("اكتب سبب الرفض عشان الطالب يعرف يصلّح إيه");
        p.setStatus(PaymentSubmission.REJECTED); p.setNote(why); p.setReviewedBy(actor.getId()); p.setReviewedAt(Instant.now());
        submissions.save(p);
        students.findById(p.getStudentId()).filter(st -> st.getUserId() != null).ifPresent(st ->
                notifications.notify(p.getTenantId(), new NotifyCommand(st.getUserId(), null, "الدفع ما اتأكدش",
                        why + " — راجع البيانات وابعته تاني من «كورساتي».", "PAYMENT", "PaymentSubmission", p.getId(), List.of("IN_APP"))));
        audit.record(actor, "PAYMENT_REJECTED", "PaymentSubmission", p.getId(), PaymentSubmission.SUBMITTED, why);
        return view(p);
    }

    public record TeacherTotal(String teacher, long payments, BigDecimal total, BigDecimal thisMonth) {}
    public record Summary(long waiting, BigDecimal total, BigDecimal thisMonth, List<TeacherTotal> teachers) {}

    /** What the platform collected, per teacher: approved payments, all time and this month (Cairo time). */
    public Summary summary(UserPrincipal actor) {
        List<Long> tenants = managedTenants(actor);
        if (tenants.isEmpty()) return new Summary(0, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        Instant monthStart = ZonedDateTime.now(PlanAccess.CAIRO).withDayOfMonth(1).toLocalDate().atStartOfDay(PlanAccess.CAIRO).toInstant();
        var all = submissions.findByTenantIdInOrderByIdDesc(tenants);
        long waiting = all.stream().filter(p -> PaymentSubmission.SUBMITTED.equals(p.getStatus())).count();
        var approved = all.stream().filter(p -> PaymentSubmission.APPROVED.equals(p.getStatus()) && p.getAmount() != null).toList();
        Map<Long, List<PaymentSubmission>> byTenant = approved.stream().collect(Collectors.groupingBy(PaymentSubmission::getTenantId));
        List<TeacherTotal> teachers = byTenant.entrySet().stream().map(e -> new TeacherTotal(
                academies.findByTenantId(e.getKey()).map(TeacherAcademy::getName).orElse("—"), e.getValue().size(),
                sum(e.getValue(), null), sum(e.getValue(), monthStart)))
                .sorted(Comparator.comparing(TeacherTotal::total).reversed()).toList();
        return new Summary(waiting, sum(approved, null), sum(approved, monthStart), teachers);
    }

    private static BigDecimal sum(List<PaymentSubmission> list, Instant since) {
        return list.stream().filter(p -> since == null || (p.getReviewedAt() != null && !p.getReviewedAt().isBefore(since)))
                .map(PaymentSubmission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------------

    private PaymentSubmission reviewable(UserPrincipal actor, Long id) {
        PaymentSubmission p = submissions.findById(id).orElseThrow(() -> NotFoundException.of("الدفع", id));
        if (!managedTenants(actor).contains(p.getTenantId())) throw new ForbiddenException("الدفع ده تابع لإدارة تانية");
        if (!PaymentSubmission.SUBMITTED.equals(p.getStatus())) throw new ConflictException("الدفع ده اتراجع قبل كده");
        return p;
    }

    /** The teacher spaces head office runs — payments to any of them are head office's to review. */
    private List<Long> managedTenants(UserPrincipal actor) {
        bundles.requireHeadOffice(actor);
        return academies.findByManagerTenantId(actor.getTenantId()).stream().map(TeacherAcademy::getTenantId).toList();
    }

    private void tellHeadOffice(Long teacherTenantId, String title, String body) {
        Long headOffice = academies.findByTenantId(teacherTenantId).map(TeacherAcademy::getManagerTenantId).orElse(null);
        if (headOffice == null) return;
        for (Role role : List.of(Role.SUPER_ADMIN, Role.BRANCH_ADMIN))
            users.findByTenantIdAndRole(headOffice, role).forEach(u -> notifications.notify(headOffice,
                    new NotifyCommand(u.getId(), null, title, body, "PAYMENT", "PaymentSubmission", null, List.of("IN_APP"))));
    }

    private SubmissionView view(PaymentSubmission p) {
        PlanSubscription s = subs.findById(p.getPlanSubscriptionId()).orElse(null);
        SubscriptionPlan plan = s == null ? null : plans.findById(s.getPlanId()).orElse(null);
        Student st = students.findById(p.getStudentId()).orElse(null);
        String email = st == null || st.getUserId() == null ? "" : users.findById(st.getUserId())
                .map(u -> Objects.toString(linked.owner(u).getEmail(), "")).orElse("");
        return new SubmissionView(p.getId(), p.getStatus(), p.getMethodCode(), p.getAmount(), p.getReference(), p.getSender(),
                p.getReceiptType() != null, p.getNote(), p.getCreatedAt(), p.getReviewedAt(), p.getPlanSubscriptionId(), p.getStudentId(),
                st == null ? "طالب" : st.getFullName(), email, st == null ? "" : Objects.toString(st.getPhone(), ""),
                academies.findByTenantId(p.getTenantId()).map(TeacherAcademy::getName).orElse(""),
                plan == null ? "" : PlanAccess.label(plan), plan == null ? 0 : plan.getMonths(), s == null ? "" : s.getStatus());
    }

    private static String trim(String v, int max, String what) {
        String s = v == null ? "" : v.trim();
        if (s.length() > max) throw new BadRequestException(what + " طويل جداً");
        return s;
    }

    /** Only a real PNG/JPG under 3 MB is kept: the bytes are decoded, not trusted by name. */
    private static String imageType(MultipartFile file) {
        if (file.getSize() > MAX_RECEIPT_BYTES) throw new BadRequestException("صورة الإيصال لازم تكون أصغر من ٣ ميجا");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("صورة الإيصال مش صورة صالحة");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_RECEIPT_PIXELS) throw new BadRequestException("أبعاد الصورة كبيرة جداً");
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new BadRequestException("الصورة لازم تكون PNG أو JPG");
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally {
                reader.dispose();
            }
        } catch (java.io.IOException e) {
            throw new BadRequestException("تعذّر قراءة صورة الإيصال");
        }
    }

    private static String base64(MultipartFile file) {
        try { return Base64.getEncoder().encodeToString(file.getBytes()); }
        catch (java.io.IOException e) { throw new BadRequestException("تعذّر قراءة صورة الإيصال"); }
    }
}
