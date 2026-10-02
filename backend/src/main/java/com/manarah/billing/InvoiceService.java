package com.manarah.billing;

import com.manarah.academy.LinkedStudentAccounts;
import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.billing.gateway.FawryGateway;
import com.manarah.common.SchoolYears;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.Course;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import com.manarah.subscription.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Every student payment goes through an invoice («مدفوعاتي»): the student picks a teacher's year-and-subject
 * subscription, how long (one of the lengths the teacher priced), and a way to pay. The server fixes the price, the
 * method's fee on top (Vodafone Cash +10%) and the total, and keeps one open invoice per subscription request.
 * <ul>
 *   <li><b>InstaPay / Vodafone Cash</b> — the student transfers the total to the platform's number, sends the screenshot
 *       on WhatsApp and here; head office approves it from «المدفوعات».</li>
 *   <li><b>Fawry</b> — a reference code from the gateway ({@link FawryGateway}) the student pays at any Fawry outlet;
 *       the gateway's signed webhook (or a status check) confirms it.</li>
 * </ul>
 * Either way {@link #markPaid} settles it, exactly once: the invoice is locked, the subscription starts for the
 * chosen length (all its courses open, and any published during it), and the payment counts in head office's totals.
 */
@Service
public class InvoiceService {
    public static final String FAWRY = "FAWRY";
    public static final String GATEWAY = "FAWATERAK";
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentInvoiceRepository invoices;
    private final PaymentMethodRepository methods;
    private final PaymentSubmissionRepository submissions;
    private final PlanSubscriptionRepository subs;
    private final SubscriptionPlanRepository plans;
    private final PlanAccess access;
    private final LinkedStudentAccounts linked;
    private final StudentRepository students;
    private final UserRepository users;
    private final TeacherAcademyRepository academies;
    private final FawryGateway gateway;
    private final PaymentNotices notices;
    private final com.manarah.audit.AuditService audit;
    private final String appUrl;

    public InvoiceService(PaymentInvoiceRepository invoices, PaymentMethodRepository methods, PaymentSubmissionRepository submissions,
                          PlanSubscriptionRepository subs, SubscriptionPlanRepository plans, PlanAccess access,
                          LinkedStudentAccounts linked, StudentRepository students, UserRepository users,
                          TeacherAcademyRepository academies, FawryGateway gateway, PaymentNotices notices,
                          com.manarah.audit.AuditService audit, @Value("${manarah.public-app-url:}") String appUrl) {
        this.invoices = invoices; this.methods = methods; this.submissions = submissions; this.subs = subs; this.plans = plans;
        this.access = access; this.linked = linked; this.students = students; this.users = users; this.academies = academies;
        this.gateway = gateway; this.notices = notices; this.audit = audit;
        this.appUrl = appUrl == null ? "" : appUrl.trim().replaceAll("/+$", "");
    }

    public record MethodOption(String code, String name, BigDecimal feePercent, boolean gateway, String account, String accountName,
                               String whatsapp, String instructions) {}
    public record PlanChoice(Long planId, String label, String year, String subject, boolean forYear, List<String> courses,
                             List<SubscriptionPlan.Option> options, String state, Instant endsAt) {}
    public record PaymentOptions(String teacher, List<PlanChoice> plans, List<MethodOption> methods) {}
    public record InvoiceView(Long id, String number, String status, String description, String teacher, int months,
                              String method, String methodName, boolean gateway, BigDecimal baseAmount, BigDecimal feePercent,
                              BigDecimal feeAmount, BigDecimal total, Instant createdAt, Instant expiresAt, Instant paidAt,
                              String fawryCode, String account, String accountName, String whatsapp, String instructions, String note,
                              String studentName, String studentCode, String email, String phone, Instant periodEndsAt, Long planId) {}

    // ---- What a student can pay for, and with what ------------------------------------------------------------

    /** The ways to pay right now: on, filled in, and — for Fawry — with the gateway's keys on the server. */
    public List<MethodOption> availableMethods() {
        return methods.findAllByOrderBySortOrderAsc().stream().filter(this::available).map(this::option).toList();
    }

    boolean available(PaymentMethod m) {
        if (!m.isEnabled()) return false;
        // Fawry also needs the site's public address: the gateway calls back to it when the code is paid.
        return FAWRY.equals(m.getCode()) ? gateway.configured() && !appUrl.isBlank() : !m.getAccount().isBlank();
    }

    /** The current teacher's priced subscriptions (the student's own year first) with their lengths, and the methods. */
    public PaymentOptions options(UserPrincipal actor) {
        Student seat = seat(actor);
        String yearKey = SchoolYears.key(seat.getGrade());
        Instant now = Instant.now();
        List<PlanChoice> list = new ArrayList<>();
        for (SubscriptionPlan p : plans.findByTenantId(seat.getTenantId())) {
            if (!p.isActive() || p.options().isEmpty()) continue;
            List<String> titles = access.coursesOf(p).stream().map(Course::getTitle).toList();
            if (titles.isEmpty()) continue;
            var periods = subs.findByPlanIdAndStudentIdOrderByIdDesc(p.getId(), seat.getId());
            Instant endsAt = periods.stream().filter(s -> PlanSubscription.ACTIVE.equals(s.getStatus()) && s.getEndsAt() != null && s.getEndsAt().isAfter(now))
                    .map(PlanSubscription::getEndsAt).max(Comparator.naturalOrder()).orElse(null);
            boolean waiting = periods.stream().anyMatch(s -> PlanSubscription.PENDING.equals(s.getStatus()));
            String state = endsAt != null ? "ACTIVE" : waiting ? "PENDING" : "NONE";
            list.add(new PlanChoice(p.getId(), PlanAccess.label(p), p.getYearLabel(), Objects.toString(p.getSubject(), ""),
                    !yearKey.isEmpty() && yearKey.equals(p.getYearKey()), titles, p.options(), state, endsAt));
        }
        list.sort(Comparator.comparing((PlanChoice c) -> !c.forYear()).thenComparing(PlanChoice::label));
        return new PaymentOptions(teacherName(seat.getTenantId()), list, availableMethods());
    }

    // ---- Making an invoice ---------------------------------------------------------------------------------------

    /**
     * An invoice for {@code months} of a plan, paid by {@code methodCode}. Pressing twice gives the same invoice back
     * (no second Fawry code); choosing another length or method replaces the open one.
     */
    @Transactional
    public InvoiceView create(UserPrincipal actor, Long planId, Integer months, String methodCode) {
        Student seat = seat(actor);
        SubscriptionPlan plan = plans.findById(planId == null ? -1L : planId).filter(p -> p.getTenantId().equals(seat.getTenantId()))
                .orElseThrow(() -> new NotFoundException("الاشتراك ده مش موجود عند مدرسك"));
        SubscriptionPlan.Option option = plan.option(months).orElseThrow(() -> new BadRequestException("اختار مدة من المدد اللي المدرس حددها"));
        PaymentMethod method = methods.findByCode(Objects.toString(methodCode, "")).filter(this::available)
                .orElseThrow(() -> new BadRequestException("اختار طريقة دفع متاحة"));
        PlanSubscription sub = access.request(plan, seat.getId(), option.months());
        return view(open(sub, plan, seat, option, method));
    }

    /** The open invoice for a subscription request, made (or replaced) for this length and method. */
    PaymentInvoice open(PlanSubscription sub, SubscriptionPlan plan, Student seat, SubscriptionPlan.Option option, PaymentMethod method) {
        Instant now = Instant.now();
        for (PaymentInvoice existing : invoices.findByPlanSubscriptionIdOrderByIdDesc(sub.getId())) {
            expireIfDue(existing, now);
            if (PaymentInvoice.AWAITING_REVIEW.equals(existing.getStatus()))
                throw new ConflictException("بعت إيصال للاشتراك ده ومستني المراجعة (" + existing.getNumber() + ") — استنى رد الإدارة");
            if (!PaymentInvoice.UNPAID.equals(existing.getStatus())) continue;
            if (existing.getMethodCode().equals(method.getCode()) && existing.getMonths() == option.months()) return existing;
            existing.setStatus(PaymentInvoice.CANCELLED);
            existing.setUpdatedAt(now);
            invoices.save(existing);
        }
        PaymentInvoice inv = new PaymentInvoice();
        inv.setNumber(newNumber());
        inv.setTenantId(sub.getTenantId());
        inv.setStudentId(seat.getId());
        inv.setPlanSubscriptionId(sub.getId());
        inv.setMonths(option.months());
        inv.setDescription("اشتراك " + PlanAccess.label(plan) + " — " + teacherName(sub.getTenantId()) + " · " + months(option.months()));
        inv.setMethodCode(method.getCode());
        BigDecimal base = option.price().setScale(2, RoundingMode.HALF_UP);
        BigDecimal pct = method.getFeePercent() == null ? BigDecimal.ZERO : method.getFeePercent();
        BigDecimal fee = base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        inv.setBaseAmount(base);
        inv.setFeePercent(pct);
        inv.setFeeAmount(fee);
        inv.setTotal(base.add(fee));
        invoices.save(inv);
        if (FAWRY.equals(method.getCode())) issueFawryCode(inv, seat);
        return inv;
    }

    private void issueFawryCode(PaymentInvoice inv, Student seat) {
        if (appUrl.isBlank()) throw new BadRequestException("الدفع بفوري مش متاح دلوقتي — اختار طريقة تانية");
        User owner = seat.getUserId() == null ? null : users.findById(seat.getUserId()).map(linked::owner).orElse(null);
        List<FawryGateway.Item> items = new ArrayList<>();
        items.add(new FawryGateway.Item(inv.getDescription(), inv.getBaseAmount()));
        if (inv.getFeeAmount().signum() > 0) items.add(new FawryGateway.Item("رسوم الدفع", inv.getFeeAmount()));
        String latin = owner != null && owner.getUsername() != null ? owner.getUsername().replaceAll("[^A-Za-z0-9]", "") : "";
        String email = owner != null && owner.getEmail() != null && owner.getEmail().contains("@") && !owner.getEmail().endsWith("@accounts.local")
                ? owner.getEmail() : "no-reply@droos.com.co";
        String phone = Objects.toString(seat.getPhone(), "").replaceAll("[^0-9]", "");
        var customer = new FawryGateway.Customer(latin.isEmpty() ? "Droos" : latin, "Student" + seat.getId(), email,
                phone.matches("01[0-9]{9}") ? phone : "01000000000");
        var code = gateway.createFawryCode(inv.getNumber(), inv.getTotal(), customer, items,
                appUrl + "/api/public/payments/fawaterak/webhook_json", appUrl + "/app/my-payments/" + inv.getId());
        inv.setGateway(GATEWAY);
        inv.setGatewayInvoiceId(code.gatewayInvoiceId());
        inv.setGatewayInvoiceKey(code.gatewayInvoiceKey());
        inv.setFawryCode(code.code());
        inv.setExpiresAt(code.expiresAt());
        inv.setStatus(PaymentInvoice.UNPAID);
        inv.setUpdatedAt(Instant.now());
        invoices.save(inv);
    }

    // ---- The student's own invoices ------------------------------------------------------------------------------

    /** Every invoice of the student, with all of their teachers, newest first. */
    @Transactional
    public List<InvoiceView> mine(UserPrincipal actor) {
        List<Long> seatIds = seatIds(actor);
        if (seatIds.isEmpty()) return List.of();
        Instant now = Instant.now();
        return invoices.findByStudentIdInOrderByIdDesc(seatIds).stream().peek(i -> expireIfDue(i, now)).map(this::view).toList();
    }

    @Transactional
    public InvoiceView get(UserPrincipal actor, Long id) {
        PaymentInvoice inv = owned(actor, id);
        expireIfDue(inv, Instant.now());
        return view(inv);
    }

    /** "I paid": asks the gateway whether a Fawry invoice is paid, in case its webhook hasn't arrived. */
    @Transactional
    public InvoiceView check(UserPrincipal actor, Long id) {
        PaymentInvoice inv = owned(actor, id);
        if (FAWRY.equals(inv.getMethodCode()) && inv.open() && inv.getGatewayInvoiceId() != null
                && gateway.isPaid(inv.getGatewayInvoiceId()).orElse(false))
            markPaid(inv.getId(), null, "FAWRY_CHECK", inv.getFawryCode());
        return view(invoices.findById(id).orElseThrow());
    }

    /** A new Fawry code for an invoice whose code ran out. */
    @Transactional
    public InvoiceView renewCode(UserPrincipal actor, Long id) {
        PaymentInvoice inv = owned(actor, id);
        expireIfDue(inv, Instant.now());
        if (!FAWRY.equals(inv.getMethodCode())) throw new BadRequestException("الفاتورة دي مش بفوري");
        if (!PaymentInvoice.EXPIRED.equals(inv.getStatus())) throw new BadRequestException("كود فوري لسه شغال");
        PlanSubscription sub = subs.findById(inv.getPlanSubscriptionId()).orElseThrow();
        if (!PlanSubscription.PENDING.equals(sub.getStatus())) throw new ConflictException("طلب الاشتراك ده مبقاش مستني دفع — اعمل فاتورة جديدة");
        issueFawryCode(inv, students.findById(inv.getStudentId()).orElseThrow());
        return view(inv);
    }

    /** Cancels an unpaid invoice (a manual one the student no longer means to pay). */
    @Transactional
    public InvoiceView cancel(UserPrincipal actor, Long id) {
        PaymentInvoice inv = owned(actor, id);
        if (!PaymentInvoice.UNPAID.equals(inv.getStatus()) && !PaymentInvoice.EXPIRED.equals(inv.getStatus()))
            throw new ConflictException("الفاتورة دي مينفعش تتلغي دلوقتي");
        inv.setStatus(PaymentInvoice.CANCELLED);
        inv.setUpdatedAt(Instant.now());
        invoices.save(inv);
        return view(inv);
    }

    /**
     * A manual payment's proof: the transfer's reference and/or the screenshot. It waits for head office, who approves
     * it from «المدفوعات» (which pays the invoice) or sends it back with a reason (the invoice opens again).
     */
    @Transactional
    public InvoiceView sendReceipt(UserPrincipal actor, Long id, String reference, String sender, MultipartFile receipt) {
        PaymentInvoice inv = owned(actor, id);
        if (FAWRY.equals(inv.getMethodCode())) throw new BadRequestException("فوري بيتأكد لوحده — مش محتاج إيصال");
        if (!inv.open()) throw new ConflictException("الفاتورة دي مش مستنية دفع");
        String ref = BillingService.trim(reference, 80, "رقم العملية"), from = BillingService.trim(sender, 80, "الرقم اللي حوّلت منه");
        boolean hasFile = receipt != null && !receipt.isEmpty();
        if (ref.isBlank() && !hasFile) throw new BadRequestException("اكتب رقم العملية أو ارفع صورة التحويل");
        PaymentSubmission p = submissions.findFirstByInvoiceIdOrderByIdDesc(inv.getId())
                .filter(x -> PaymentSubmission.SUBMITTED.equals(x.getStatus())).orElseGet(PaymentSubmission::new);
        p.setTenantId(inv.getTenantId()); p.setStudentId(inv.getStudentId()); p.setPlanSubscriptionId(inv.getPlanSubscriptionId());
        p.setInvoiceId(inv.getId()); p.setMethodCode(inv.getMethodCode()); p.setAmount(inv.getTotal());
        p.setReference(ref); p.setSender(from); p.setStatus(PaymentSubmission.SUBMITTED); p.setNote(""); p.setCreatedAt(Instant.now());
        if (hasFile) { p.setReceiptType(BillingService.imageType(receipt)); p.setReceiptData(BillingService.base64(receipt)); }
        submissions.save(p);
        inv.setStatus(PaymentInvoice.AWAITING_REVIEW);
        inv.setNote("");
        inv.setUpdatedAt(Instant.now());
        invoices.save(inv);
        String name = students.findById(inv.getStudentId()).map(Student::getFullName).orElse("طالب");
        notices.headOffice(inv.getTenantId(), "دفع جديد مستني المراجعة",
                name + " حوّل " + inv.getTotal().toPlainString() + " ج.م بـ" + methodName(inv.getMethodCode()) + " — فاتورة " + inv.getNumber()
                        + ". راجعه من «المدفوعات».");
        return view(inv);
    }

    // ---- Settling ------------------------------------------------------------------------------------------------

    /**
     * The one way an invoice gets paid — head office approving a receipt, or the gateway. Locks the invoice, so a
     * repeated webhook can't pay it twice; starts the subscription for the chosen length if it's still waiting; and
     * records a Fawry payment as an approved payment so it counts in head office's totals. Returns false if already paid.
     */
    @Transactional
    public boolean markPaid(Long invoiceId, Long by, String source, String reference) {
        PaymentInvoice inv = invoices.lockById(invoiceId).orElseThrow(() -> NotFoundException.of("الفاتورة", invoiceId));
        if (PaymentInvoice.PAID.equals(inv.getStatus())) return false;
        Instant now = Instant.now();
        inv.setStatus(PaymentInvoice.PAID);
        inv.setPaidAt(now);
        inv.setNote("");
        inv.setUpdatedAt(now);
        invoices.save(inv);
        PlanSubscription sub = subs.findById(inv.getPlanSubscriptionId()).orElseThrow();
        SubscriptionPlan plan = plans.findById(sub.getPlanId()).orElseThrow();
        if (PlanSubscription.PENDING.equals(sub.getStatus())) {
            sub.setMonths(inv.getMonths());
            sub.setPrice(inv.getBaseAmount());
            access.start(sub, plan, by, "PAYMENT");
        } else {
            // Money arrived for a request that no longer waits (opened by the teacher, or paid twice): head office decides.
            notices.headOffice(inv.getTenantId(), "دفع لطلب اتقفل قبل كده",
                    "فاتورة " + inv.getNumber() + " اتدفعت (" + inv.getTotal().toPlainString() + " ج.م) لطلب اشتراك مبقاش مستني دفع. راجع الطالب.");
        }
        // Every paid invoice counts once in head office's totals: as the receipt it was approved by, or as a record made here.
        PaymentSubmission p = submissions.findFirstByInvoiceIdOrderByIdDesc(inv.getId())
                .filter(x -> !PaymentSubmission.REJECTED.equals(x.getStatus())).orElse(null);
        if (p == null) {
            p = new PaymentSubmission();
            p.setTenantId(inv.getTenantId()); p.setStudentId(inv.getStudentId()); p.setPlanSubscriptionId(inv.getPlanSubscriptionId());
            p.setInvoiceId(inv.getId()); p.setMethodCode(inv.getMethodCode()); p.setSender(""); p.setCreatedAt(now);
            p.setReference(Objects.toString(reference, ""));
            p.setNote(FAWRY.equals(inv.getMethodCode()) ? "اتأكد من فوري تلقائياً" : "اتأكد من الإدارة برقم الفاتورة");
        }
        if (!PaymentSubmission.APPROVED.equals(p.getStatus())) {
            p.setStatus(PaymentSubmission.APPROVED); p.setAmount(inv.getTotal()); p.setReviewedBy(by); p.setReviewedAt(now);
            submissions.save(p);
        }
        PlanSubscription started = subs.findById(sub.getId()).orElseThrow();
        notices.student(inv.getTenantId(), inv.getStudentId(), "تم تأكيد الدفع ✓",
                "فاتورة " + inv.getNumber() + " اتدفعت. اشتراك " + PlanAccess.label(plan)
                        + (started.getEndsAt() != null ? " شغال لحد " + PlanAccess.day(started.getEndsAt()) : "") + ".", inv.getId());
        // The gateway acts with no signed-in user: its entry goes to the teacher's space under its own name.
        audit.record(new UserPrincipal(by, inv.getTenantId(), null, by == null ? "بوابة الدفع" : "الإدارة", null, Role.SUPER_ADMIN),
                "INVOICE_PAID", "PaymentInvoice", inv.getId(), null, source + " " + inv.getTotal().toPlainString());
        return true;
    }

    /** An invoice by the number the student sends with their screenshot (on WhatsApp, say); "dr-abc" and "ABC" work too. */
    @Transactional
    public InvoiceView byNumber(String number) {
        String n = Objects.toString(number, "").trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!n.startsWith("DR-")) n = "DR-" + n;
        PaymentInvoice inv = invoices.findByNumber(n).orElseThrow(() -> new NotFoundException("مفيش فاتورة بالرقم ده"));
        expireIfDue(inv, Instant.now());
        return view(inv);
    }

    /**
     * Head office saw the transfer outside the app (the screenshot on WhatsApp) and confirms a wallet invoice by hand.
     * Fawry invoices are left to the gateway, so a code can't be paid once here and again at the kiosk.
     */
    @Transactional
    public InvoiceView confirm(Long invoiceId, Long by, String reference) {
        PaymentInvoice inv = invoices.findById(invoiceId).orElseThrow(() -> NotFoundException.of("الفاتورة", invoiceId));
        if (FAWRY.equals(inv.getMethodCode())) throw new BadRequestException("فاتورة فوري بتتأكد لوحدها من بوابة الدفع");
        if (PaymentInvoice.PAID.equals(inv.getStatus())) throw new ConflictException("الفاتورة دي اتدفعت قبل كده");
        if (!inv.open()) throw new ConflictException("الفاتورة دي اتلغت — الطالب يعمل فاتورة جديدة");
        markPaid(invoiceId, by, "MANUAL", reference);
        return view(invoices.findById(invoiceId).orElseThrow());
    }

    /** Head office sent the receipt back: the invoice waits for payment again, with the reason. */
    @Transactional
    public void receiptRejected(Long invoiceId, String reason) {
        invoices.findById(invoiceId).filter(i -> PaymentInvoice.AWAITING_REVIEW.equals(i.getStatus())).ifPresent(inv -> {
            inv.setStatus(PaymentInvoice.UNPAID);
            inv.setNote(reason);
            inv.setUpdatedAt(Instant.now());
            invoices.save(inv);
        });
    }

    // ---- The gateway's webhook -----------------------------------------------------------------------------------

    public enum WebhookResult { PAID, ALREADY_PAID, CLOSED, IGNORED, BAD_SIGNATURE, UNKNOWN_INVOICE }

    /**
     * A Fawaterak webhook. "Paid" names the invoice by its id and key (both secret per invoice) and is signed over them;
     * "cancelled"/"expired" is signed over its referenceId. Nothing changes unless the signature checks out.
     */
    @Transactional
    public WebhookResult webhook(Map<String, String> f) {
        String invoiceId = f.getOrDefault("invoice_id", ""), invoiceKey = f.getOrDefault("invoice_key", "");
        String hash = f.getOrDefault("hashKey", "");
        if (!invoiceId.isBlank() && !invoiceKey.isBlank()) {
            if (!gateway.validPaidSignature(invoiceId, invoiceKey, f.getOrDefault("payment_method", ""), hash)) return WebhookResult.BAD_SIGNATURE;
            PaymentInvoice inv = invoices.findByGatewayInvoiceId(invoiceId).filter(i -> invoiceKey.equals(i.getGatewayInvoiceKey())).orElse(null);
            if (inv == null) return WebhookResult.UNKNOWN_INVOICE;
            String status = f.getOrDefault("invoice_status", "").toLowerCase(Locale.ROOT);
            if (!status.isBlank() && !status.equals("paid")) return WebhookResult.IGNORED;  // a failed attempt: the code stays usable
            return markPaid(inv.getId(), null, "FAWRY_WEBHOOK", f.getOrDefault("referenceNumber", inv.getFawryCode()))
                    ? WebhookResult.PAID : WebhookResult.ALREADY_PAID;
        }
        String referenceId = f.getOrDefault("referenceId", "");
        if (!referenceId.isBlank()) {
            if (!gateway.validCancelSignature(referenceId, f.getOrDefault("paymentMethod", ""), hash)) return WebhookResult.BAD_SIGNATURE;
            PaymentInvoice inv = invoices.findByGatewayInvoiceKey(referenceId).or(() -> invoices.findByGatewayInvoiceId(referenceId)).orElse(null);
            if (inv == null) return WebhookResult.UNKNOWN_INVOICE;
            if (PaymentInvoice.UNPAID.equals(inv.getStatus())) {
                inv.setStatus(PaymentInvoice.EXPIRED);
                inv.setUpdatedAt(Instant.now());
                invoices.save(inv);
            }
            return WebhookResult.CLOSED;
        }
        return WebhookResult.IGNORED;
    }

    // ---- Helpers -------------------------------------------------------------------------------------------------

    private void expireIfDue(PaymentInvoice inv, Instant now) {
        if (PaymentInvoice.UNPAID.equals(inv.getStatus()) && inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(now)) {
            inv.setStatus(PaymentInvoice.EXPIRED);
            inv.setUpdatedAt(now);
            invoices.save(inv);
        }
    }

    /** The student's seat with the teacher they're looking at now. */
    private Student seat(UserPrincipal actor) {
        if (actor.getRole() != Role.STUDENT) throw new ForbiddenException("الدفع من حساب الطالب");
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .orElseThrow(() -> new ForbiddenException("الحساب ده مش مربوط بطالب"));
    }

    private List<Long> seatIds(UserPrincipal actor) {
        if (actor.getRole() != Role.STUDENT) throw new ForbiddenException("الفواتير لحساب الطالب");
        return linked.seatsOf(actor).stream().map(Student::getId).toList();
    }

    private PaymentInvoice owned(UserPrincipal actor, Long id) {
        PaymentInvoice inv = invoices.findById(id).orElseThrow(() -> NotFoundException.of("الفاتورة", id));
        if (!seatIds(actor).contains(inv.getStudentId())) throw new NotFoundException("الفاتورة مش موجودة");
        return inv;
    }

    private String newNumber() {
        String number;
        do {
            StringBuilder s = new StringBuilder("DR-");
            for (int i = 0; i < 7; i++) s.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            number = s.toString();
        } while (invoices.existsByNumber(number));
        return number;
    }

    private String teacherName(Long tenantId) {
        return academies.findByTenantId(tenantId).map(TeacherAcademy::getName).orElse("المدرس");
    }

    private String methodName(String code) {
        return methods.findByCode(code).map(PaymentMethod::getName).orElse(code);
    }

    private static String months(int m) {
        return m == 1 ? "شهر" : m == 2 ? "شهرين" : m + " شهور";
    }

    private MethodOption option(PaymentMethod m) {
        String whatsapp = m.getWhatsapp() == null || m.getWhatsapp().isBlank() ? m.getAccount() : m.getWhatsapp();
        boolean viaGateway = FAWRY.equals(m.getCode());
        return new MethodOption(m.getCode(), m.getName(), m.getFeePercent() == null ? BigDecimal.ZERO : m.getFeePercent(), viaGateway,
                viaGateway ? "" : m.getAccount(), viaGateway ? "" : m.getAccountName(), viaGateway ? "" : whatsapp, m.getInstructions());
    }

    InvoiceView view(PaymentInvoice inv) {
        PaymentMethod m = methods.findByCode(inv.getMethodCode()).orElse(null);
        MethodOption how = m == null ? null : option(m);
        Student st = students.findById(inv.getStudentId()).orElse(null);
        User owner = st == null || st.getUserId() == null ? null : users.findById(st.getUserId()).map(linked::owner).orElse(null);
        String email = owner == null || owner.getEmail() == null || owner.getEmail().endsWith("@accounts.local") ? "" : owner.getEmail();
        PlanSubscription sub = subs.findById(inv.getPlanSubscriptionId()).orElse(null);
        Instant periodEnds = sub == null ? null : sub.getEndsAt();
        return new InvoiceView(inv.getId(), inv.getNumber(), inv.getStatus(), inv.getDescription(), teacherName(inv.getTenantId()),
                inv.getMonths(), inv.getMethodCode(), m == null ? inv.getMethodCode() : m.getName(), FAWRY.equals(inv.getMethodCode()),
                inv.getBaseAmount(), inv.getFeePercent(), inv.getFeeAmount(), inv.getTotal(), inv.getCreatedAt(), inv.getExpiresAt(),
                inv.getPaidAt(), inv.getFawryCode(), how == null ? "" : how.account(), how == null ? "" : how.accountName(),
                how == null ? "" : how.whatsapp(), how == null ? "" : how.instructions(), inv.getNote(),
                st == null ? "" : st.getFullName(), st == null ? "" : Objects.toString(st.getCode(), ""), email,
                st == null ? "" : Objects.toString(st.getPhone(), ""), PaymentInvoice.PAID.equals(inv.getStatus()) ? periodEnds : null,
                sub == null ? null : sub.getPlanId());
    }
}
