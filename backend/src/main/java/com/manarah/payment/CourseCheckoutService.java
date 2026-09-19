package com.manarah.payment;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.payment.domain.CoursePurchaseOrder;
import com.manarah.payment.gateway.PaymobGatewayService;
import com.manarah.payment.repo.CoursePurchaseOrderRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseCheckoutService {
    private static final Set<String> DEMO_METHODS = Set.of("CARD", "WALLET", "FAWRY");
    private final CoursePurchaseOrderRepository orders;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final PaymobGatewayService gateway;
    private final com.manarah.academy.AcademyAccess academyAccess;
    @org.springframework.beans.factory.annotation.Value("${manarah.payment.sandbox-enabled:false}")
    private boolean sandboxEnabled;

    public CourseCheckoutService(CoursePurchaseOrderRepository orders, CourseRepository courses,
                                 StudentRepository students, EnrollmentRepository enrollments,
                                 PaymobGatewayService gateway, com.manarah.academy.AcademyAccess academyAccess) {
        this.academyAccess = academyAccess;
        this.orders = orders; this.courses = courses; this.students = students; this.enrollments = enrollments;
        this.gateway = gateway;
    }
    public record CreateOrder(Long courseId) {}
    public record PayRequest(String method) {}
    public record OrderView(String reference, Long courseId, String courseTitle, String subject, BigDecimal amount,
                            String currency, String status, String paymentMethod, String checkoutUrl,
                            boolean liveGateway, Instant paidAt) {}

    @Transactional
    public OrderView create(UserPrincipal actor, CreateOrder req) {
        academyAccess.rejectManagedAcademySelfService(actor.getTenantId());
        if (actor.getRole() != Role.STUDENT) throw new ForbiddenException("شراء الكورسات متاح لحساب الطالب");
        var student = students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .orElseThrow(() -> new NotFoundException("لا يوجد ملف طالب مرتبط بالحساب"));
        if (enrollments.existsByTenantIdAndStudentIdAndCourseId(actor.getTenantId(), student.getId(), req.courseId()))
            throw new ConflictException("أنت ملتحق بهذا الكورس بالفعل");
        return openOrder(actor.getTenantId(), actor.getId(), student.getId(),
                student.getFullName(), actor.getUsername(), student.getPhone(), req.courseId());
    }

    /**
     * Opens (or reuses) a pending purchase order and, when a live gateway is configured, attaches
     * its hosted-checkout URL. Shared by the logged-in student flow above and by public
     * registration checkout, so a visitor buying from a teacher's page reaches the same gateway
     * instead of the manual invoice that path used to raise.
     *
     * <p>Takes ids rather than a principal because the registration caller has just created the
     * account and has no authenticated principal yet.
     */
    @Transactional
    public OrderView openOrder(Long tenantId, Long userId, Long studentId, String buyerName,
                               String buyerEmail, String buyerPhone, Long courseId) {
        var course = courses.findByTenantIdAndId(tenantId, courseId)
                .filter(c -> "ACTIVE".equals(c.getStatus())).orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        if (course.getFinalPrice() != null && course.getFinalPrice().signum() > 0 && !gateway.isEnabled() && !sandboxEnabled)
            throw new BadRequestException("الدفع الإلكتروني غير متاح حالياً. تواصل مع إدارة الأكاديمية للاشتراك.");
        var existing = orders.findFirstByTenantIdAndUserIdAndCourseIdAndStatusOrderByCreatedAtDesc(tenantId, userId, course.getId(), "PENDING");
        if (existing.isPresent()) return view(existing.get());

        CoursePurchaseOrder order = new CoursePurchaseOrder();
        order.setTenantId(tenantId);
        order.setUserId(userId);
        order.setStudentId(studentId);
        order.setCourseId(course.getId());
        order.setReference("MNR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        order.setAmount(course.getFinalPrice() == null ? BigDecimal.ZERO : course.getFinalPrice());
        orders.save(order);

        if (order.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            complete(order, "FREE", null);
            return view(order);
        }
        if (gateway.isEnabled()) {
            var billing = new PaymobGatewayService.BillingData(buyerName, buyerEmail, buyerPhone);
            gateway.createCheckout(order.getReference(), order.getAmount(), course.getTitle(), billing)
                    .ifPresent(session -> { order.setCheckoutUrl(session.checkoutUrl()); orders.save(order); });
        }
        return view(order);
    }

    public OrderView get(UserPrincipal actor, String reference) { return view(access(actor, reference)); }

    /** Demo/sandbox "mark as paid" — only usable while no live gateway is configured. Once a
     *  real gateway is enabled, an order can only be confirmed paid by {@link #confirmPaid},
     *  called from the HMAC-verified webhook — never by a client-reported "I paid" call. */
    @Transactional
    public OrderView pay(UserPrincipal actor, String reference, PayRequest req) {
        CoursePurchaseOrder order = access(actor, reference);
        if ("PAID".equals(order.getStatus())) return view(order);
        if (gateway.isEnabled())
            throw new BadRequestException("الدفع الحقيقي مفعّل لهذا الكورس — أكمل الدفع من رابط بوابة الدفع، ولا يمكن تأكيده يدوياً");
        if (!sandboxEnabled) throw new ForbiddenException("الدفع التجريبي غير متاح في هذه البيئة. تواصل مع الإدارة.");
        String method = req.method() == null ? "CARD" : req.method().toUpperCase(Locale.ROOT);
        if (!DEMO_METHODS.contains(method)) throw new BadRequestException("طريقة الدفع غير مدعومة");
        complete(order, method, "SANDBOX-" + order.getReference());
        return view(order);
    }

    /** Called only by {@code PaymobWebhookController} after HMAC verification — the sole path
     *  that can mark a real-gateway order paid. Idempotent: a retried webhook for an
     *  already-paid order is a silent no-op, and an unknown reference is ignored rather than
     *  thrown, since Paymob will still expect a 200 either way. */
    @Transactional
    public void confirmPaid(String reference, String providerTransactionId) {
        CoursePurchaseOrder order = orders.findByReference(reference).orElse(null);
        if (order == null || "PAID".equals(order.getStatus())) return;
        TenantContext.set(order.getTenantId());
        try {
            complete(order, "GATEWAY", providerTransactionId);
        } finally {
            TenantContext.clear();
        }
    }

    private void complete(CoursePurchaseOrder order, String method, String providerRef) {
        order.setPaymentMethod(method);
        order.setStatus("PAID");
        order.setPaidAt(Instant.now());
        order.setProviderReference(providerRef != null ? providerRef : "SANDBOX-" + order.getReference());
        orders.save(order);
        // Activate rather than only create: a student who came through public checkout may already
        // have a PENDING_PAYMENT enrollment row, and an existence check alone would leave it
        // un-activated — paid for, but still locked out of the course.
        Enrollment e = enrollments
                .findByTenantIdAndStudentIdAndCourseId(order.getTenantId(), order.getStudentId(), order.getCourseId())
                .orElseGet(() -> {
                    Enrollment fresh = new Enrollment();
                    fresh.setTenantId(order.getTenantId());
                    fresh.setStudentId(order.getStudentId());
                    fresh.setCourseId(order.getCourseId());
                    return fresh;
                });
        e.setStatus("ACTIVE");
        enrollments.save(e);
        // Same reasoning for the student record itself: left at PENDING_PAYMENT they'd be missing
        // from every "active students" count on the admin dashboard despite having paid.
        students.findByTenantIdAndId(order.getTenantId(), order.getStudentId())
                .filter(s -> "PENDING_PAYMENT".equals(s.getStatus()))
                .ifPresent(s -> { s.setStatus("ACTIVE"); students.save(s); });
    }

    private CoursePurchaseOrder access(UserPrincipal actor, String reference) {
        CoursePurchaseOrder order = orders.findByTenantIdAndReference(actor.getTenantId(), reference)
                .orElseThrow(() -> new NotFoundException("طلب الدفع غير موجود"));
        if (!actor.isAdmin() && !order.getUserId().equals(actor.getId())) throw new ForbiddenException("ليس لديك صلاحية لعرض عملية الدفع");
        return order;
    }
    private OrderView view(CoursePurchaseOrder order) {
        var c = courses.findByTenantIdAndId(order.getTenantId(), order.getCourseId()).orElseThrow(() -> NotFoundException.of("الكورس", order.getCourseId()));
        return new OrderView(order.getReference(), c.getId(), c.getTitle(), c.getSubject(), order.getAmount(), order.getCurrency(),
                order.getStatus(), order.getPaymentMethod(), order.getCheckoutUrl(), gateway.isEnabled(), order.getPaidAt());
    }
}
