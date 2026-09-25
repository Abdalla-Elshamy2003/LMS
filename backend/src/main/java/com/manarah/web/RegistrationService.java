package com.manarah.web;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.domain.Branch;
import com.manarah.org.domain.Tenant;
import com.manarah.org.repo.BranchRepository;
import com.manarah.org.repo.TenantRepository;
import com.manarah.payment.CourseCheckoutService;
import com.manarah.security.JwtService;
import com.manarah.security.PasswordPolicy;
import com.manarah.student.domain.Guardian;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGuardian;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Public self-registration (§register). A real account, not just a lead form: this creates an
 * actual STUDENT login (email + the password the visitor chose) alongside the trial student
 * profile, so the response can hand back a working access token — the visitor lands inside the
 * app immediately, the same way logging in does. Guardian details are optional here (staff can
 * always add one later from the student profile); the account and the student record are the
 * only things guaranteed to exist afterward.
 *
 * <p>Validates the requested institution and course membership first, then creates everything
 * atomically — a failure partway through rolls back the whole request instead of leaving an
 * orphaned account with no student record.
 */
@Service
public class RegistrationService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final TenantRepository tenants;
    private final BranchRepository branches;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final com.manarah.payment.CourseAccessCodeService accessCodes;
    private final JwtService jwtService;
    private final CourseCheckoutService checkout;
    private final com.manarah.academy.AcademyAccess academyAccess;
    private final ApplicationEventPublisher events;
    private final com.manarah.academy.LinkedStudentAccounts linked;
    private final com.manarah.security.auth.LoginAttemptLimiter loginAttempts;
    private final com.manarah.academy.BundleSubscriptionService packages;
    private final com.manarah.enrollment.CourseRequests requests;
    private final com.manarah.subscription.PlanService planService;

    public RegistrationService(TenantRepository tenants, BranchRepository branches, CourseRepository courses,
                               StudentRepository students, GuardianRepository guardians,
                               StudentGuardianRepository links,
                               UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                               CourseCheckoutService checkout, com.manarah.academy.AcademyAccess academyAccess,
                               com.manarah.payment.CourseAccessCodeService accessCodes, ApplicationEventPublisher events,
                               com.manarah.academy.LinkedStudentAccounts linked,
                               com.manarah.security.auth.LoginAttemptLimiter loginAttempts,
                               com.manarah.academy.BundleSubscriptionService packages,
                               com.manarah.enrollment.CourseRequests requests,
                               com.manarah.subscription.PlanService planService) {
        this.packages = packages;
        this.requests = requests;
        this.planService = planService;
        this.events = events;
        this.linked = linked;
        this.loginAttempts = loginAttempts;
        this.accessCodes = accessCodes;
        this.academyAccess = academyAccess;
        this.tenants = tenants;
        this.branches = branches;
        this.courses = courses;
        this.students = students;
        this.guardians = guardians;
        this.links = links;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.checkout = checkout;
    }

    private static final java.util.Set<String> EDUCATION_TYPES = java.util.Set.of("عادي", "لغات", "تجريبي");

    public record RegisterCommand(String fullName, String email, String password, String phone, String grade,
                                  String nationalId, String educationType,
                                  String guardianName, String guardianPhone, Long courseId, String tenantSlug) {
    }

    public record RegistrationResult(String accessToken, String tokenType, long expiresInMinutes,
                                     String studentCode, String message) {
    }

    /** A checkout is a registration tied to buying one specific paid course: the course is
     *  required, and a purchase order is opened for its price instead of a trial enrollment. */
    public record CheckoutCommand(String fullName, String email, String password, String phone, String grade,
                                  String nationalId, String educationType,
                                  String guardianName, String guardianPhone, Long courseId, String tenantSlug) {
    }

    /** No tenantSlug/courseId needed — the code itself identifies both. */
    public record RedeemCommand(String code, String fullName, String email, String password, String phone,
                                String grade, String nationalId, String educationType,
                                String guardianName, String guardianPhone) {
    }

    public record CheckoutResult(String accessToken, String tokenType, long expiresInMinutes, String studentCode,
                                 String reference, BigDecimal amount, String status, String checkoutUrl,
                                 boolean liveGateway, String courseTitle, String message) {
    }

    /**
     * Signing up with a teacher, usually from a course on their page. The course's year becomes the student's year
     * when they didn't pick one, so "تانية ثانوي" on the course is "تانية ثانوي" on the account; the course itself is
     * asked for right away (free: open; paid: on the dashboard waiting for payment — see CourseRequests).
     */
    @Transactional
    public RegistrationResult register(RegisterCommand cmd) {
        // Every student belongs to a teacher; without one the account would land in head office with nothing to study.
        if (cmd.tenantSlug() == null || cmd.tenantSlug().isBlank()) throw new BadRequestException("اختار المدرس اللي عايز تسجّل معاه");
        Tenant t = resolveTenant(cmd.tenantSlug());
        Long tenantId = t.getId();
        Course course = cmd.courseId() == null ? null : requireCourse(tenantId, cmd.courseId());
        String grade = (cmd.grade() == null || cmd.grade().isBlank()) && course != null && course.getGrade() != null
                && !course.getGrade().isBlank() ? course.getGrade().trim() : cmd.grade();

        Account acc = createAccount(tenantId, cmd.fullName(), cmd.email(), cmd.password(), cmd.phone(),
                grade, cmd.nationalId(), cmd.educationType(), cmd.guardianName(), cmd.guardianPhone());

        if (course != null) requests.request(tenantId, acc.student.getId(), course);

        String token = linked.tokenFor(acc.user);
        return new RegistrationResult(token, "Bearer", jwtService.getAccessTokenTtlMinutes(),
                acc.student.getCode(), "تم إنشاء حسابك بنجاح! أهلاً بك في دروس.");
    }

    /**
     * Public checkout from a teacher's profile / course page: create the account, then open the
     * very same purchase order a logged-in student would get from the courses page. This used to
     * raise a manual invoice instead, which meant a visitor buying from the public site never
     * reached the payment gateway at all — the one path where money was still collected by hand.
     *
     * <p>No enrollment is created here on purpose. Access to a paid course is granted only by
     * {@code CourseCheckoutService.complete()}, i.e. after the gateway webhook confirms payment
     * (or immediately, if the course is free) — creating one up front would hand out the course
     * before a piastre was collected.
     */
    @Transactional
    public CheckoutResult checkout(CheckoutCommand cmd) {
        if (cmd.courseId() == null) {
            throw new BadRequestException("اختر كورساً للاشتراك فيه");
        }
        Tenant t = resolveTenant(cmd.tenantSlug());
        Long tenantId = t.getId();
        Course course = requireCourse(tenantId, cmd.courseId());

        Account acc = createAccount(tenantId, cmd.fullName(), cmd.email(), cmd.password(), cmd.phone(),
                cmd.grade(), cmd.nationalId(), cmd.educationType(), cmd.guardianName(), cmd.guardianPhone());
        acc.student.setStatus("PENDING_PAYMENT");
        students.save(acc.student);

        var order = checkout.openOrder(tenantId, acc.user.getId(), acc.student.getId(),
                acc.student.getFullName(), cmd.email().trim(), cmd.phone(), course.getId());

        String token = linked.tokenFor(acc.user);
        return new CheckoutResult(token, "Bearer", jwtService.getAccessTokenTtlMinutes(), acc.student.getCode(),
                order.reference(), order.amount(), order.status(), order.checkoutUrl(), order.liveGateway(),
                course.getTitle(), checkoutMessage(order));
    }

    /** Creates the account inside the code's own tenant/course and redeems it in one transaction. */
    @Transactional
    public RegistrationResult redeem(RedeemCommand cmd) {
        if (com.manarah.academy.BundleSubscriptionService.isPackageCode(cmd.code())) return redeemPackage(cmd);
        if (com.manarah.subscription.PlanService.isPlanCode(cmd.code())) return redeemPlan(cmd);
        var code = accessCodes.findRedeemable(cmd.code());
        Course course = courses.findByTenantIdAndId(code.getTenantId(), code.getCourseId())
                .orElseThrow(() -> new BadRequestException("الكورس المرتبط بهذا الكود لم يعد متاحاً"));

        Account acc = createAccount(code.getTenantId(), cmd.fullName(), cmd.email(), cmd.password(), cmd.phone(),
                cmd.grade(), cmd.nationalId(), cmd.educationType(), cmd.guardianName(), cmd.guardianPhone());
        accessCodes.redeem(code, acc.student.getId());

        String token = linked.tokenFor(acc.user);
        return new RegistrationResult(token, "Bearer", jwtService.getAccessTokenTtlMinutes(),
                acc.student.getCode(), "تم تفعيل اشتراكك في \"" + course.getTitle() + "\" بنجاح!");
    }

    /**
     * A visitor signing up with a package code: the account is created with the package's first teacher (or linked,
     * if the email already has one — see createAccount), then the package joins them to every teacher in it.
     */
    private RegistrationResult redeemPackage(RedeemCommand cmd) {
        var code = packages.redeemable(cmd.code());
        Long tenantId = packages.homeTenantFor(code);
        Account acc = createAccount(tenantId, cmd.fullName(), cmd.email(), cmd.password(), cmd.phone(),
                cmd.grade(), cmd.nationalId(), cmd.educationType(), cmd.guardianName(), cmd.guardianPhone());
        packages.use(code, linked.owner(acc.user));
        String token = linked.tokenFor(acc.user);
        return new RegistrationResult(token, "Bearer", jwtService.getAccessTokenTtlMinutes(),
                acc.student.getCode(), "تم تفعيل الباقة! كل مدرسين الباقة وكورساتهم بقوا عندك في «باقتي».");
    }

    /** Signing up with a subscription code: the account is made with the plan's teacher, in the plan's year, and the period starts. */
    private RegistrationResult redeemPlan(RedeemCommand cmd) {
        var code = planService.findRedeemable(cmd.code());
        var plan = planService.planOf(code);
        String grade = cmd.grade() == null || cmd.grade().isBlank() ? plan.getYearLabel() : cmd.grade();
        Account acc = createAccount(plan.getTenantId(), cmd.fullName(), cmd.email(), cmd.password(), cmd.phone(),
                grade, cmd.nationalId(), cmd.educationType(), cmd.guardianName(), cmd.guardianPhone());
        var period = planService.use(code, acc.student.getId());
        String token = linked.tokenFor(acc.user);
        return new RegistrationResult(token, "Bearer", jwtService.getAccessTokenTtlMinutes(), acc.student.getCode(),
                "تم تفعيل اشتراك " + com.manarah.subscription.PlanAccess.label(plan) + " لحد يوم "
                        + com.manarah.subscription.PlanAccess.day(period.getEndsAt()) + ".");
    }

    private static String checkoutMessage(com.manarah.payment.CourseCheckoutService.OrderView order) {
        if ("PAID".equals(order.status()))
            return "تم إنشاء حسابك وتفعيل الكورس! تقدر تبدأ المذاكرة دلوقتي.";
        if (order.liveGateway())
            return "تم إنشاء حسابك! كمّل الدفع من بوابة الدفع الآمنة عشان يتفتحلك الكورس فوراً.";
        return "تم إنشاء حسابك! اختر طريقة الدفع لإتمام العملية وفتح الكورس.";
    }

    private record Account(User user, Student student) {
    }

    /** Shared by register() and checkout(): validates + creates the User/Student(+Guardian) that
     *  every self-service signup needs, regardless of whether it ends in a trial or a purchase. */
    private Account createAccount(Long tenantId, String fullName, String email, String password, String phone,
                                  String grade, String nationalId, String educationTypeIn,
                                  String guardianName, String guardianPhone) {
        if (fullName == null || fullName.isBlank()) {
            throw new BadRequestException("الاسم الكامل مطلوب");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new BadRequestException("البريد الإلكتروني غير صحيح");
        }
        PasswordPolicy.requireStrong(password);
        if (phone == null || phone.isBlank()) {
            throw new BadRequestException("رقم الهاتف مطلوب");
        }
        if (nationalId != null && !nationalId.isBlank() && !nationalId.matches("\\d{14}")) {
            throw new BadRequestException("الرقم القومي يجب أن يتكون من 14 رقماً");
        }
        String educationType = (educationTypeIn == null || educationTypeIn.isBlank()) ? "عادي" : educationTypeIn;
        if (!EDUCATION_TYPES.contains(educationType)) {
            throw new BadRequestException("نظام التعليم غير صحيح");
        }

        String trimmedEmail = email.trim();
        if (users.existsByTenantIdAndEmailIgnoreCase(tenantId, trimmedEmail)) {
            throw new ConflictException("هذا البريد الإلكتروني مسجَّل بالفعل، جرّب تسجيل الدخول بدلاً من ذلك");
        }
        // The address already signs in with another teacher. With that account's password this is the same
        // student joining one more teacher: link them instead of creating a second account they could never
        // sign in to. The password check counts against the same limit as sign-in, so this is no easier to guess.
        List<User> elsewhere = users.findAllByEmailIgnoreCase(trimmedEmail).stream()
                .filter(u -> u.getPrimaryUserId() == null).toList();
        if (!elsewhere.isEmpty()) {
            loginAttempts.assertAllowed(trimmedEmail);
            User owner = elsewhere.stream()
                    .filter(u -> "ACTIVE".equals(u.getStatus()) && u.getRole() == Role.STUDENT)
                    .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                    .findFirst().orElse(null);
            if (owner == null) {
                loginAttempts.failed(trimmedEmail);
                throw new ConflictException("الإيميل ده عنده حساب على دروس. سجّل دخولك بيه وانضم للمستر من صفحته، أو اكتب هنا نفس كلمة المرور بتاعته.");
            }
            loginAttempts.succeeded(trimmedEmail);
            Student joined = linked.linkAtRegistration(owner, tenantId, fullName, phone, grade, educationType);
            return new Account(users.findById(joined.getUserId()).orElseThrow(), joined);
        }

        Long branchId = branches.findByTenantIdOrderByName(tenantId).stream()
                .findFirst().map(Branch::getId).orElse(null);

        User user = new User();
        user.setTenantId(tenantId);
        user.setBranchId(branchId);
        user.setFullName(fullName);
        user.setEmail(trimmedEmail);
        user.setPhone(phone);
        user.setRole(Role.STUDENT);
        user.setPasswordHash(passwordEncoder.encode(password));
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("هذا البريد الإلكتروني مسجَّل بالفعل");
        }

        Student s = new Student();
        s.setTenantId(tenantId);
        s.setBranchId(branchId);
        s.setUserId(user.getId());
        // Placeholder for the insert; replaced with an id-derived code right after — race-free
        // by construction since the DB just assigned this row a unique auto-increment id.
        s.setCode("PENDING-" + UUID.randomUUID());
        s.setFullName(fullName);
        s.setGrade(grade);
        s.setNationalId(nationalId);
        s.setEducationType(educationType);
        s.setPhone(phone);
        s.setStatus("TRIAL");
        try {
            students.save(s);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("تعذّر إنشاء ملف الطالب، حاول مرة أخرى");
        }
        s.setCode(String.format("STD-%05d", s.getId()));
        students.save(s);

        // A guardian is optional at self-registration time — staff or the student can add one
        // later from the profile. We still link one automatically when contact info is given.
        if ((guardianName != null && !guardianName.isBlank()) || (guardianPhone != null && !guardianPhone.isBlank())) {
            Guardian g = new Guardian();
            g.setTenantId(tenantId);
            g.setFullName(nn(guardianName, "ولي أمر " + fullName));
            g.setPhone(nn(guardianPhone, phone));
            guardians.save(g);

            StudentGuardian link = new StudentGuardian();
            link.setTenantId(tenantId);
            link.setStudentId(s.getId());
            link.setGuardianId(g.getId());
            link.setRelation("ولي أمر");
            links.save(link);
        }

        // Consumed after the surrounding transaction commits (e.g. to email the student their QR pass).
        events.publishEvent(new com.manarah.common.events.DomainEvents.StudentRegistered(tenantId, s.getId()));

        return new Account(user, s);
    }

    private Course requireCourse(Long tenantId, Long courseId) {
        return courses.findByTenantIdAndId(tenantId, courseId).filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new BadRequestException("الكورس ده مش متاح عند المدرس دلوقتي. اختار كورس تاني."));
    }

    private Tenant resolveTenant(String slug) {
        var tenant = (slug != null ? tenants.findBySlug(slug) : tenants.findAll().stream().findFirst())
                .orElseThrow(() -> new NotFoundException("لا توجد مؤسسة"));
        academyAccess.rejectSelfEnrollment(tenant.getId());
        return tenant;
    }

    private static String nn(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
