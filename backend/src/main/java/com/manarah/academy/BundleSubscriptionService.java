package com.manarah.academy;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * A whole package at one price. The student pays head office (manually, like a course) and gets a one-time package
 * code — or head office grants the package directly. Subscribing joins the student to every published teacher in
 * the package (a seat in each teacher's space, see {@link LinkedStudentAccounts}) and opens all of their active
 * courses. Each enrollment the package opened is remembered, so cancelling closes exactly those and never a course
 * the student bought on their own. Courses a teacher adds later open the next time the student looks at the package.
 */
@Service
public class BundleSubscriptionService {
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String PREFIX = "PKG-";

    private final TeacherBundleRepository bundles;
    private final TeacherBundleMemberRepository members;
    private final TeacherAcademyRepository academies;
    private final BundleAccessCodeRepository codes;
    private final BundleSubscriptionRepository subscriptions;
    private final BundleEnrollmentRepository opened;
    private final BundleService bundleService;
    private final LinkedStudentAccounts linked;
    private final UserRepository users;
    private final StudentRepository students;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final com.manarah.audit.AuditService audit;

    public BundleSubscriptionService(TeacherBundleRepository bundles, TeacherBundleMemberRepository members, TeacherAcademyRepository academies,
                                     BundleAccessCodeRepository codes, BundleSubscriptionRepository subscriptions, BundleEnrollmentRepository opened,
                                     BundleService bundleService, LinkedStudentAccounts linked, UserRepository users, StudentRepository students,
                                     CourseRepository courses, EnrollmentRepository enrollments, com.manarah.audit.AuditService audit) {
        this.bundles = bundles; this.members = members; this.academies = academies; this.codes = codes; this.subscriptions = subscriptions;
        this.opened = opened; this.bundleService = bundleService; this.linked = linked; this.users = users; this.students = students;
        this.courses = courses; this.enrollments = enrollments; this.audit = audit;
    }

    public static boolean isPackageCode(String raw) {
        return raw != null && raw.trim().toUpperCase(Locale.ROOT).startsWith(PREFIX);
    }

    // ---- Head office: codes, subscribers, grants ---------------------------------------------------------------

    public record CodeView(Long id, String code, String status, Instant createdAt, Instant usedAt, String usedBy) {}
    public record SubscriberView(Long id, Long userId, String fullName, String login, String status, String source, Instant createdAt) {}

    @Transactional
    public List<CodeView> generate(UserPrincipal actor, Long bundleId, int count) {
        var b = bundleService.manage(actor, bundleId);
        if (count < 1 || count > 100) throw new BadRequestException("العدد من 1 إلى 100");
        List<CodeView> made = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var c = new BundleAccessCode();
            c.setBundleId(b.getId()); c.setCode(freshCode()); c.setCreatedBy(actor.getId()); c.setCreatedAt(Instant.now());
            codes.save(c);
            made.add(view(c));
        }
        audit.record(actor, "BUNDLE_CODES_CREATED", "TeacherBundle", b.getId(), null, "count=" + count);
        return made;
    }

    public List<CodeView> codes(UserPrincipal actor, Long bundleId) {
        var b = bundleService.manage(actor, bundleId);
        return codes.findByBundleIdOrderByCreatedAtDesc(b.getId()).stream().map(this::view).toList();
    }

    @Transactional
    public void revokeCode(UserPrincipal actor, Long codeId) {
        var c = codes.findById(codeId).orElseThrow(() -> NotFoundException.of("الكود", codeId));
        bundleService.manage(actor, c.getBundleId());
        if (!"UNUSED".equals(c.getStatus())) throw new BadRequestException("الكود مستخدم بالفعل ولا يمكن إلغاؤه");
        c.setStatus("REVOKED"); codes.save(c);
        audit.record(actor, "BUNDLE_CODE_REVOKED", "TeacherBundle", c.getBundleId(), null, c.getCode());
    }

    public List<SubscriberView> subscribers(UserPrincipal actor, Long bundleId) {
        var b = bundleService.manage(actor, bundleId);
        return subscriptions.findByBundleIdOrderByCreatedAtDesc(b.getId()).stream().map(s -> {
            var u = users.findById(s.getUserId()).orElse(null);
            return new SubscriberView(s.getId(), s.getUserId(), u == null ? "—" : u.getFullName(), u == null ? "" : login(u),
                    s.getStatus(), s.getSource(), s.getCreatedAt());
        }).toList();
    }

    /** Head office confirmed the transfer itself: subscribe the student by the email or username they sign in with. */
    @Transactional
    public SubscriberView grant(UserPrincipal actor, Long bundleId, String studentLogin) {
        var b = bundleService.manage(actor, bundleId);
        String id = studentLogin == null ? "" : studentLogin.trim();
        if (id.isEmpty()) throw new BadRequestException("اكتب إيميل الطالب أو اسم المستخدم");
        User owner = (id.contains("@") ? users.findAllByEmailIgnoreCase(id) : users.findByUsernameIgnoreCase(id).map(List::of).orElse(List.<User>of()))
                .stream().filter(u -> u.getPrimaryUserId() == null && u.getRole() == Role.STUDENT && "ACTIVE".equals(u.getStatus()))
                .findFirst().orElseThrow(() -> new NotFoundException("مفيش طالب بالبيانات دي"));
        var s = subscribe(b, owner, "ADMIN", null, actor.getId());
        audit.record(actor, "BUNDLE_GRANTED", "TeacherBundle", b.getId(), null, "user=" + owner.getId());
        return new SubscriberView(s.getId(), owner.getId(), owner.getFullName(), login(owner), s.getStatus(), s.getSource(), s.getCreatedAt());
    }

    /** Ends a subscription and closes the courses it opened (not ones the student bought separately). */
    @Transactional
    public void cancel(UserPrincipal actor, Long subscriptionId) {
        var s = subscriptions.findById(subscriptionId).orElseThrow(() -> NotFoundException.of("الاشتراك", subscriptionId));
        bundleService.manage(actor, s.getBundleId());
        if (!"ACTIVE".equals(s.getStatus())) return;
        s.setStatus("CANCELLED"); s.setCancelledAt(Instant.now()); subscriptions.save(s);
        for (var be : opened.findBySubscriptionId(s.getId()))
            enrollments.findById(be.getEnrollmentId()).ifPresent(e -> { e.setStatus("INACTIVE"); enrollments.save(e); });
        audit.record(actor, "BUNDLE_CANCELLED", "TeacherBundle", s.getBundleId(), null, "user=" + s.getUserId());
    }

    // ---- The student ------------------------------------------------------------------------------------------

    /** A signed-in student typing a package code. */
    @Transactional
    public Long redeem(UserPrincipal actor, String rawCode) {
        var code = redeemable(rawCode);
        User me = users.findById(actor.getId()).orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
        if (me.getRole() != Role.STUDENT) throw new ForbiddenException("الباقات للطلاب فقط");
        User owner = linked.owner(me);
        use(code, owner);
        return code.getBundleId();
    }

    /** The code for a visitor who is creating their account on the package page (see RegistrationService). */
    public BundleAccessCode redeemable(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        if (code.isEmpty()) throw new BadRequestException("اكتب كود الباقة");
        var c = codes.findByCodeIgnoreCase(code).orElseThrow(() -> new BadRequestException("كود الباقة غير صحيح"));
        if ("USED".equals(c.getStatus())) throw new ConflictException("كود الباقة ده مستخدم بالفعل");
        if (!"UNUSED".equals(c.getStatus())) throw new BadRequestException("كود الباقة ده لم يعد صالحاً");
        return c;
    }

    /** The teacher space a visitor's new account is created in when they sign up with a package code. */
    public Long homeTenantFor(BundleAccessCode code) {
        return members.findByBundleIdOrderByPositionAsc(code.getBundleId()).stream()
                .map(m -> m.getAcademyId() == null ? null : academies.findById(m.getAcademyId()).filter(TeacherAcademy::isPublished).orElse(null))
                .filter(Objects::nonNull).findFirst().map(TeacherAcademy::getTenantId)
                .orElseThrow(() -> new BadRequestException("الباقة لسه مفيهاش مدرسين متاحين"));
    }

    @Transactional
    public void use(BundleAccessCode code, User owner) {
        var b = bundles.findById(code.getBundleId()).orElseThrow(() -> new BadRequestException("الباقة غير متاحة"));
        code.setStatus("USED"); code.setUsedByUserId(owner.getId()); code.setUsedAt(Instant.now()); codes.save(code);
        subscribe(b, owner, "CODE", code.getId(), null);
    }

    /** The student's packages — "باقتي": every teacher with their courses, in one place, opening new courses as they come. */
    @Transactional
    public List<Map<String, Object>> mine(UserPrincipal actor) {
        User me = users.findById(actor.getId()).orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
        if (me.getRole() != Role.STUDENT) return List.of();
        User owner = linked.owner(me);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var s : subscriptions.findByUserIdAndStatus(owner.getId(), "ACTIVE")) {
            var b = bundles.findById(s.getBundleId()).orElse(null);
            if (b == null) continue;
            sync(s, b, owner);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("slug", b.getSlug()); view.put("name", b.getName()); view.put("tagline", b.getTagline()); view.put("since", s.getCreatedAt());
            List<Map<String, Object>> teachers = new ArrayList<>();
            for (var m : members.findByBundleIdOrderByPositionAsc(b.getId())) {
                var a = m.getAcademyId() == null ? null : academies.findById(m.getAcademyId()).filter(TeacherAcademy::isPublished).orElse(null);
                if (a == null) continue;
                var seat = linked.seatIn(actor, a.getTenantId());
                if (seat.isEmpty()) continue;
                Student st = seat.get();
                Set<Long> open = new HashSet<>();
                for (var e : enrollments.findByTenantIdAndStudentId(a.getTenantId(), st.getId()))
                    if (Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())) open.add(e.getCourseId());
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("seatUserId", st.getUserId()); t.put("current", st.getUserId().equals(actor.getId()));
                t.put("name", !m.getDisplayName().isBlank() ? m.getDisplayName() : a.getName()); t.put("subject", m.getSubject());
                t.put("photoUrl", !m.getPhotoUrl().isBlank() ? m.getPhotoUrl() : Objects.toString(a.getPhotoUrl(), ""));
                t.put("slug", a.getSlug());
                t.put("courses", courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream()
                        .filter(c -> "ACTIVE".equals(c.getStatus()) && open.contains(c.getId()))
                        .map(c -> Map.<String, Object>of("id", c.getId(), "title", c.getTitle(), "year", Objects.toString(c.getGrade(), ""),
                                "description", Objects.toString(c.getDescription(), ""), "coverUrl", Objects.toString(c.getCoverUrl(), "")))
                        .toList());
                teachers.add(t);
            }
            view.put("teachers", teachers);
            out.add(view);
        }
        return out;
    }

    // ---- Internals --------------------------------------------------------------------------------------------

    BundleSubscription subscribe(TeacherBundle b, User owner, String source, Long codeId, Long grantedBy) {
        if (owner.getRole() != Role.STUDENT) throw new BadRequestException("الباقات للطلاب فقط");
        var s = subscriptions.findByBundleIdAndUserId(b.getId(), owner.getId()).orElseGet(BundleSubscription::new);
        if (s.getId() == null || !"ACTIVE".equals(s.getStatus())) {
            s.setBundleId(b.getId()); s.setUserId(owner.getId()); s.setStatus("ACTIVE"); s.setSource(source);
            s.setCodeId(codeId); s.setGrantedBy(grantedBy); s.setCreatedAt(Instant.now()); s.setCancelledAt(null);
            subscriptions.save(s);
        }
        sync(s, b, owner);
        return s;
    }

    /** Joins every published member teacher and opens each active course the student doesn't already have. */
    private void sync(BundleSubscription s, TeacherBundle b, User owner) {
        for (var m : members.findByBundleIdOrderByPositionAsc(b.getId())) {
            var a = m.getAcademyId() == null ? null : academies.findById(m.getAcademyId()).filter(TeacherAcademy::isPublished).orElse(null);
            if (a == null) continue;
            Student seat;
            try { seat = linked.rowForCode(owner, a.getTenantId()); }
            catch (ForbiddenException removed) { continue; } // that teacher removed the student; the package doesn't override them
            for (var c : courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId())) {
                if (!"ACTIVE".equals(c.getStatus())) continue;
                var existing = enrollments.findByTenantIdAndStudentIdAndCourseId(a.getTenantId(), seat.getId(), c.getId());
                Enrollment e;
                if (existing.isPresent()) {
                    e = existing.get();
                    boolean mine = opened.existsBySubscriptionIdAndEnrollmentId(s.getId(), e.getId());
                    if (Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())) continue; // bought separately, or already open
                    e.setStatus("ACTIVE"); enrollments.save(e);
                    if (mine) continue;
                } else {
                    e = new Enrollment(); e.setTenantId(a.getTenantId()); e.setStudentId(seat.getId()); e.setCourseId(c.getId()); e.setStatus("ACTIVE");
                    enrollments.save(e);
                }
                var be = new BundleEnrollment(); be.setSubscriptionId(s.getId()); be.setEnrollmentId(e.getId()); opened.save(be);
            }
        }
    }

    private CodeView view(BundleAccessCode c) {
        String by = c.getUsedByUserId() == null ? null : users.findById(c.getUsedByUserId()).map(User::getFullName).orElse(null);
        return new CodeView(c.getId(), c.getCode(), c.getStatus(), c.getCreatedAt(), c.getUsedAt(), by);
    }

    private static String login(User u) {
        return u.getUsername() != null ? u.getUsername() : u.getEmail();
    }

    private String freshCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(PREFIX);
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            if (!codes.existsByCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Could not mint a unique package code");
    }
}
