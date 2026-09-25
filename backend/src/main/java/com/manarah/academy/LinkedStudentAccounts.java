package com.manarah.academy;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.CourseRequests;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.domain.Branch;
import com.manarah.org.repo.BranchRepository;
import com.manarah.security.JwtService;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Guardian;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGuardian;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * One student, many teachers. A student signs in with one account (the "owner"); joining another teacher gives
 * them a row of their own inside that teacher's space — user + student, exactly like any other student there —
 * linked back to the owner through {@code primaryUserId}. Every space therefore stays isolated as before: a
 * teacher sees only their own students, and attendance, grades and payments stay with the teacher they belong to.
 * The student moves between their teachers with {@link #switchTo}, never with a second password.
 */
@Service
public class LinkedStudentAccounts {
    static final String ARCHIVED = "ARCHIVED";

    private final UserRepository users;
    private final StudentRepository students;
    private final TeacherAcademyRepository academies;
    private final BranchRepository branches;
    private final CourseRepository courses;
    private final CourseRequests requests;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository guardianLinks;
    private final JwtService jwt;
    private final PasswordEncoder passwords;
    private final com.manarah.audit.AuditService audit;

    public LinkedStudentAccounts(UserRepository users, StudentRepository students, TeacherAcademyRepository academies,
                                 BranchRepository branches, CourseRepository courses, CourseRequests requests,
                                 GuardianRepository guardians, StudentGuardianRepository guardianLinks, JwtService jwt,
                                 PasswordEncoder passwords, com.manarah.audit.AuditService audit) {
        this.users = users; this.students = students; this.academies = academies; this.branches = branches;
        this.courses = courses; this.requests = requests; this.guardians = guardians; this.guardianLinks = guardianLinks;
        this.jwt = jwt; this.passwords = passwords; this.audit = audit;
    }

    /** A teacher the student has, as shown in their "مدرسيني" switcher. */
    public record Membership(Long userId, Long academyId, String slug, String name, String subject, String photoUrl, boolean current) {}
    /** A session for one of the student's rows, ready to hand to the browser. */
    public record Session(User user, String accessToken) {}

    // ---- Who is who -------------------------------------------------------------------------------------------

    /** The account this row signs in with: itself, unless it is a linked row. */
    public User owner(User row) {
        if (row.getPrimaryUserId() == null) return row;
        return users.findById(row.getPrimaryUserId()).orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
    }

    /** Every row the person behind {@code row} has, in any space: the owner first, then the linked ones. */
    public List<User> rowsOf(User row) {
        User owner = owner(row);
        List<User> all = new ArrayList<>();
        all.add(owner);
        all.addAll(users.findByPrimaryUserId(owner.getId()));
        return all;
    }

    /** The same, from a student record (a scanned card, say). Rows without a login stand alone. */
    public List<Long> userIdsOfStudent(Student s) {
        if (s.getUserId() == null) return List.of();
        return users.findById(s.getUserId()).map(u -> rowsOf(u).stream().map(User::getId).toList()).orElse(List.of());
    }

    /** A session for {@code row}, valid only while its owner's password is unchanged. */
    public String tokenFor(User row) {
        return jwt.generateAccessToken(row, owner(row));
    }

    // ---- The switcher -----------------------------------------------------------------------------------------

    /** The teachers this student can switch between: rows that are active and still enrolled in their space. */
    public List<Membership> memberships(UserPrincipal actor) {
        User me = users.findById(actor.getId()).orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
        if (me.getRole() != Role.STUDENT) return List.of();
        List<Membership> out = new ArrayList<>();
        for (User row : rowsOf(me)) {
            if (!usable(row)) continue;
            academies.findByTenantId(row.getTenantId()).ifPresent(a -> out.add(new Membership(row.getId(), a.getId(), a.getSlug(),
                    a.getName(), Objects.toString(a.getSubject(), ""), Objects.toString(a.getPhotoUrl(), ""), row.getId().equals(me.getId()))));
        }
        return out;
    }

    /** Moves the student into another of their teacher spaces. Only ever to a row of the same person. */
    public Session switchTo(UserPrincipal actor, Long userId) {
        User me = users.findById(actor.getId()).orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
        User target = rowsOf(me).stream().filter(u -> u.getId().equals(userId)).findFirst()
                .filter(this::usable)
                .orElseThrow(() -> new ForbiddenException("المدرس ده مش ضمن مدرسينك"));
        return new Session(target, tokenFor(target));
    }

    /**
     * Where a student lands after signing in: their own row, unless that teacher removed them — then the first
     * teacher they still have, so one teacher removing a student never locks them out of the others.
     */
    public User landing(User owner) {
        if (owner.getRole() != Role.STUDENT || usable(owner)) return owner;
        return users.findByPrimaryUserId(owner.getId()).stream().filter(this::usable).findFirst().orElse(owner);
    }

    /**
     * The student's seat with the teacher in {@code tenantId} — for something that belongs to that teacher (their
     * class session's QR, say) done while the student happens to be looking at another teacher.
     */
    public Optional<Student> seatIn(UserPrincipal actor, Long tenantId) {
        User me = users.findById(actor.getId()).orElse(null);
        if (me == null || me.getRole() != Role.STUDENT) return Optional.empty();
        return rowsOf(me).stream().filter(u -> u.getTenantId().equals(tenantId) && usable(u)).findFirst()
                .flatMap(u -> students.findByTenantIdAndUserId(tenantId, u.getId()));
    }

    /** Every seat the student still has (one per teacher), for views that span all of their teachers. */
    public List<Student> seatsOf(UserPrincipal actor) {
        User me = users.findById(actor.getId()).orElse(null);
        if (me == null || me.getRole() != Role.STUDENT) return List.of();
        return rowsOf(me).stream().filter(this::usable)
                .map(u -> students.findByTenantIdAndUserId(u.getTenantId(), u.getId()).orElse(null))
                .filter(Objects::nonNull).toList();
    }

    /** Whether this seat is still one the student can use (active, and the teacher hasn't removed them). */
    public boolean isUsable(User row) {
        return usable(row);
    }

    /** Whether the student still has a teacher other than the one in {@code exceptTenantId}. */
    public boolean hasOtherTeachers(User owner, Long exceptTenantId) {
        return users.findByPrimaryUserId(owner.getId()).stream()
                .anyMatch(u -> !u.getTenantId().equals(exceptTenantId) && usable(u));
    }

    private boolean usable(User row) {
        if (!"ACTIVE".equals(row.getStatus())) return false;
        return students.findByTenantIdAndUserId(row.getTenantId(), row.getId()).map(s -> !ARCHIVED.equals(s.getStatus())).orElse(false);
    }

    // ---- Joining another teacher ------------------------------------------------------------------------------

    /**
     * The student joins a teacher from that teacher's page. A published page only — an unpublished one is still
     * invite-only. Idempotent: joining a teacher they already have just switches to them. A course picked on the
     * way in is asked for either way (see {@link CourseRequests}): a free one opens, a paid one waits for payment.
     */
    @Transactional
    public Session join(UserPrincipal actor, String slug, Long courseId) {
        if (actor.getRole() != Role.STUDENT) throw new ForbiddenException("الانضمام لمدرس متاح لحسابات الطلاب فقط");
        var academy = academies.findBySlug(Objects.toString(slug, "")).filter(TeacherAcademy::isPublished)
                .orElseThrow(() -> new NotFoundException("صفحة المدرس غير متاحة"));
        User me = users.findById(actor.getId()).orElseThrow(() -> new UnauthorizedException("الجلسة غير صالحة"));
        User owner = owner(me);
        Course course = courseId == null ? null : courses.findByTenantIdAndId(academy.getTenantId(), courseId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new BadRequestException("الكورس ده مش متاح عند المدرس"));
        var existing = rowIn(owner, academy.getTenantId());
        if (existing.isPresent()) {
            if (!usable(existing.get())) throw new ForbiddenException("المدرس ده وقّف حسابك عنده. تواصل معاه.");
            // Already studying with this teacher: the course they clicked is still what they came for.
            if (course != null) requests.request(academy.getTenantId(),
                    students.findByTenantIdAndUserId(academy.getTenantId(), existing.get().getId()).orElseThrow().getId(), course);
            return new Session(existing.get(), tokenFor(existing.get()));
        }
        var from = profileOf(owner);
        Student joined = createRow(owner, academy.getTenantId(), from.getFullName(), from.getPhone(), from.getGrade(), from.getEducationType());
        if (course != null) requests.request(academy.getTenantId(), joined.getId(), course);
        User row = users.findById(joined.getUserId()).orElseThrow();
        return new Session(row, tokenFor(row));
    }

    /**
     * The student's row in {@code tenantId}, creating it when they have none — used when a code from another
     * teacher is redeemed: the code is that teacher's own invitation, so the page need not be published.
     */
    @Transactional
    public Student rowForCode(User anyRow, Long tenantId) {
        User owner = owner(anyRow);
        var existing = rowIn(owner, tenantId);
        if (existing.isPresent()) {
            if (!usable(existing.get())) throw new ForbiddenException("المدرس ده وقّف حسابك عنده. تواصل معاه.");
            return students.findByTenantIdAndUserId(tenantId, existing.get().getId()).orElseThrow();
        }
        var from = profileOf(owner);
        return createRow(owner, tenantId, from.getFullName(), from.getPhone(), from.getGrade(), from.getEducationType());
    }

    /**
     * Registration on a second teacher's page with an email that already has an account (and its password —
     * checked by the caller): link instead of creating a duplicate the student could never sign in to.
     */
    @Transactional
    public Student linkAtRegistration(User owner, Long tenantId, String fullName, String phone, String grade, String educationType) {
        if (rowIn(owner, tenantId).isPresent())
            throw new ConflictException("إنت مشترك مع المدرس ده بالفعل. سجّل دخولك بنفس الإيميل.");
        return createRow(owner, tenantId, fullName, phone, grade, educationType);
    }

    private Optional<User> rowIn(User owner, Long tenantId) {
        return rowsOf(owner).stream().filter(u -> u.getTenantId().equals(tenantId)).findFirst();
    }

    /** The student details a new teacher gets: name, phone, grade and education type — nothing more. */
    private Student profileOf(User owner) {
        return students.findByTenantIdAndUserId(owner.getTenantId(), owner.getId()).orElseGet(() -> {
            Student s = new Student(); s.setFullName(owner.getFullName()); s.setPhone(owner.getPhone()); return s;
        });
    }

    private Student createRow(User owner, Long tenantId, String fullName, String phone, String grade, String educationType) {
        if (owner.getRole() != Role.STUDENT) throw new ForbiddenException("الانضمام لمدرس متاح لحسابات الطلاب فقط");
        var academy = academies.findByTenantId(tenantId).orElseThrow(() -> new NotFoundException("صفحة المدرس غير متاحة"));
        Long branchId = users.findById(academy.getTeacherId()).map(User::getBranchId)
                .orElseGet(() -> branches.findByTenantIdOrderByName(tenantId).stream().findFirst().map(Branch::getId).orElse(null));

        User row = new User();
        row.setTenantId(tenantId); row.setBranchId(branchId); row.setRole(Role.STUDENT);
        row.setFullName(fullName == null || fullName.isBlank() ? owner.getFullName() : fullName.trim());
        row.setPhone(phone);
        // No credentials of its own: the student signs in with the owner account and switches here.
        row.setEmail(UUID.randomUUID() + "@accounts.local");
        row.setPasswordHash(passwords.encode(UUID.randomUUID().toString()));
        row.setPrimaryUserId(owner.getId());
        users.save(row);

        Student s = new Student();
        s.setTenantId(tenantId); s.setBranchId(branchId); s.setUserId(row.getId());
        s.setCode("PENDING-" + UUID.randomUUID());
        s.setFullName(row.getFullName()); s.setPhone(phone); s.setGrade(grade);
        if (educationType != null && !educationType.isBlank()) s.setEducationType(educationType);
        s.setStatus("TRIAL");
        students.save(s);
        s.setCode(String.format("STD-%05d", s.getId()));
        students.save(s);
        copyGuardians(owner, tenantId, s);
        audit.record(UserPrincipal.from(row), "STUDENT_JOINED_TEACHER", "Student", s.getId(), null, "owner=" + owner.getId());
        return s;
    }

    /** The parent's contact details travel with the student, so the new teacher can reach them too. */
    private void copyGuardians(User owner, Long tenantId, Student joined) {
        students.findByTenantIdAndUserId(owner.getTenantId(), owner.getId()).ifPresent(home -> {
            for (StudentGuardian link : guardianLinks.findByTenantIdAndStudentId(home.getTenantId(), home.getId())) {
                guardians.findByTenantIdAndId(home.getTenantId(), link.getGuardianId()).ifPresent(g -> {
                    Guardian copy = new Guardian();
                    copy.setTenantId(tenantId); copy.setFullName(g.getFullName()); copy.setPhone(g.getPhone()); copy.setEmail(g.getEmail());
                    guardians.save(copy);
                    StudentGuardian l = new StudentGuardian();
                    l.setTenantId(tenantId); l.setStudentId(joined.getId()); l.setGuardianId(copy.getId()); l.setRelation(link.getRelation());
                    guardianLinks.save(l);
                });
            }
        });
    }
}
