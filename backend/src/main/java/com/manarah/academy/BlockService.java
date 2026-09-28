package com.manarah.academy;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Blocking and unblocking, with the reason the blocked person is shown (enforced by
 * {@link com.manarah.identity.AccountBlocks}).
 * <ul>
 *   <li>Head office blocks a teacher (and so their assistants) or a student on the whole platform, and can lift any
 *       block inside the teacher spaces it manages — a teacher's own block on a student included.</li>
 *   <li>A teacher blocks a student in their own space only; they see, but cannot lift, a block head office set.
 *       Assistants do neither.</li>
 * </ul>
 * Every change goes to the audit log with its reason.
 */
@Service
public class BlockService {
    private static final String ARCHIVED = "ARCHIVED";
    private static final int MAX_REASON = 500;

    private final BundleService bundleService;
    private final AcademyService academyService;
    private final TeacherAcademyRepository academies;
    private final UserRepository users;
    private final StudentRepository students;
    private final LinkedStudentAccounts linked;
    private final com.manarah.audit.AuditService audit;

    public BlockService(BundleService bundleService, AcademyService academyService, TeacherAcademyRepository academies,
                        UserRepository users, StudentRepository students, LinkedStudentAccounts linked,
                        com.manarah.audit.AuditService audit) {
        this.bundleService = bundleService; this.academyService = academyService; this.academies = academies;
        this.users = users; this.students = students; this.linked = linked; this.audit = audit;
    }

    public record TeacherRow(Long academyId, String name, String subject, String photoUrl, String username,
                             boolean blocked, String reason, Instant blockedAt, String blockedBy) {}
    /** One block in force: {@code TEACHER} and {@code STUDENT} are head office's, {@code SEAT} is a teacher's. */
    public record BlockedRow(String kind, Long academyId, Long userId, Long studentId, String name, String login,
                             String teacher, String reason, Instant blockedAt, String blockedBy) {}
    /** A student in one teacher's space: the teacher's own block, and head office's if there is one. */
    public record SeatRow(Long studentId, String fullName, String login, String phone, boolean blocked, String reason,
                          Instant blockedAt, String blockedBy, boolean platformBlocked, String platformReason) {}

    // ---- Head office ------------------------------------------------------------------------------------------

    /** The teachers with their block status, and every block in force across the managed spaces. */
    public Map<String, Object> overview(UserPrincipal actor) {
        var managed = managed(actor);
        Map<Long, TeacherAcademy> byTenant = new HashMap<>();
        managed.forEach(a -> byTenant.put(a.getTenantId(), a));
        List<TeacherRow> teachers = new ArrayList<>();
        List<BlockedRow> blocked = new ArrayList<>();
        for (var a : managed) {
            User t = users.findById(a.getTeacherId()).orElse(null);
            if (t == null) continue;
            teachers.add(new TeacherRow(a.getId(), a.getName(), Objects.toString(a.getSubject(), ""), Objects.toString(a.getPhotoUrl(), ""),
                    Objects.toString(t.getUsername(), ""), t.getBlockedAt() != null, t.getBlockedReason(), t.getBlockedAt(), t.getBlockedBy()));
            if (t.getBlockedAt() != null)
                blocked.add(new BlockedRow("TEACHER", a.getId(), t.getId(), null, a.getName(), Objects.toString(t.getUsername(), ""),
                        a.getName(), t.getBlockedReason(), t.getBlockedAt(), t.getBlockedBy()));
        }
        var tenantIds = new ArrayList<>(byTenant.keySet());
        for (User u : users.findByBlockedAtIsNotNull()) {
            if (u.getRole() != Role.STUDENT || !inScope(u, tenantIds)) continue;
            blocked.add(new BlockedRow("STUDENT", null, u.getId(), null, u.getFullName(), login(u), null,
                    u.getBlockedReason(), u.getBlockedAt(), u.getBlockedBy()));
        }
        if (!tenantIds.isEmpty())
            for (Student s : students.findByTenantIdInAndBlockedAtIsNotNull(tenantIds)) {
                User row = s.getUserId() == null ? null : users.findById(s.getUserId()).orElse(null);
                blocked.add(new BlockedRow("SEAT", byTenant.get(s.getTenantId()).getId(), row == null ? null : linked.owner(row).getId(), s.getId(),
                        s.getFullName(), row == null ? "" : login(linked.owner(row)), byTenant.get(s.getTenantId()).getName(),
                        s.getBlockedReason(), s.getBlockedAt(), s.getBlockedBy()));
            }
        blocked.sort(Comparator.comparing(BlockedRow::blockedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return Map.of("teachers", teachers, "blocked", blocked);
    }

    @Transactional
    public void blockTeacher(UserPrincipal actor, Long academyId, String reason) {
        var a = managedAcademy(actor, academyId);
        User t = users.findById(a.getTeacherId()).orElseThrow();
        apply(t, reason(reason), actor);
        users.save(t);
        audit.record(actor, "TEACHER_BLOCKED", "TeacherAcademy", a.getId(), null, t.getBlockedReason());
    }

    @Transactional
    public void unblockTeacher(UserPrincipal actor, Long academyId) {
        var a = managedAcademy(actor, academyId);
        User t = users.findById(a.getTeacherId()).orElseThrow();
        String before = t.getBlockedReason();
        clear(t);
        users.save(t);
        audit.record(actor, "TEACHER_UNBLOCKED", "TeacherAcademy", a.getId(), before, null);
    }

    /** Blocks the account the student signs in with, so none of their teachers opens. */
    @Transactional
    public void blockStudent(UserPrincipal actor, Long userId, String reason) {
        User owner = managedStudent(actor, userId);
        apply(owner, reason(reason), actor);
        users.save(owner);
        audit.record(actor, "STUDENT_BLOCKED", "User", owner.getId(), null, owner.getBlockedReason());
    }

    @Transactional
    public void unblockStudent(UserPrincipal actor, Long userId) {
        User owner = managedStudent(actor, userId);
        String before = owner.getBlockedReason();
        clear(owner);
        users.save(owner);
        audit.record(actor, "STUDENT_UNBLOCKED", "User", owner.getId(), before, null);
    }

    /** Head office lifting a teacher's block on one of their students. */
    @Transactional
    public void liftSeat(UserPrincipal actor, Long studentId) {
        var tenantIds = managed(actor).stream().map(TeacherAcademy::getTenantId).toList();
        Student s = students.findById(studentId).filter(x -> tenantIds.contains(x.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        String before = s.getBlockedReason();
        clearSeat(s);
        students.save(s);
        audit.record(actor, "STUDENT_SEAT_UNBLOCKED", "Student", s.getId(), before, "by head office");
    }

    // ---- A teacher, in their own space ------------------------------------------------------------------------

    public List<SeatRow> seats(UserPrincipal actor, Long academyId) {
        var a = academyService.manageAsOwner(actor, academyId);
        return students.findByTenantId(a.getTenantId()).stream()
                .filter(s -> s.getUserId() != null && !ARCHIVED.equals(s.getStatus()))
                .sorted(Comparator.comparing((Student s) -> s.getBlockedAt() == null).thenComparing(Student::getFullName))
                .map(s -> {
                    User row = users.findById(s.getUserId()).orElse(null);
                    User owner = row == null ? null : linked.owner(row);
                    return new SeatRow(s.getId(), s.getFullName(), owner == null ? "" : login(owner), Objects.toString(s.getPhone(), ""),
                            s.getBlockedAt() != null, s.getBlockedReason(), s.getBlockedAt(), s.getBlockedBy(),
                            owner != null && owner.getBlockedAt() != null, owner == null ? null : owner.getBlockedReason());
                }).toList();
    }

    @Transactional
    public void blockSeat(UserPrincipal actor, Long academyId, Long studentId, String reason) {
        var a = academyService.manageAsOwner(actor, academyId);
        Student s = seat(a, studentId);
        s.setBlockedAt(Instant.now());
        s.setBlockedReason(reason(reason));
        s.setBlockedBy(actor.getFullName());
        students.save(s);
        audit.record(actor, "STUDENT_SEAT_BLOCKED", "Student", s.getId(), null, s.getBlockedReason());
    }

    @Transactional
    public void unblockSeat(UserPrincipal actor, Long academyId, Long studentId) {
        var a = academyService.manageAsOwner(actor, academyId);
        Student s = seat(a, studentId);
        String before = s.getBlockedReason();
        clearSeat(s);
        students.save(s);
        audit.record(actor, "STUDENT_SEAT_UNBLOCKED", "Student", s.getId(), before, null);
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------

    private Student seat(TeacherAcademy a, Long studentId) {
        return students.findByTenantIdAndId(a.getTenantId(), studentId)
                .filter(s -> s.getUserId() != null && !ARCHIVED.equals(s.getStatus()))
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
    }

    private static void apply(User u, String reason, UserPrincipal actor) {
        u.setBlockedAt(Instant.now());
        u.setBlockedReason(reason);
        u.setBlockedBy(actor.getFullName());
    }

    private static void clear(User u) { u.setBlockedAt(null); u.setBlockedReason(null); u.setBlockedBy(null); }

    private static void clearSeat(Student s) { s.setBlockedAt(null); s.setBlockedReason(null); s.setBlockedBy(null); }

    private static String reason(String raw) {
        if (raw == null || raw.isBlank()) throw new BadRequestException("اكتب سبب الحظر — هيظهر للشخص لما يحاول يدخل");
        String r = raw.trim();
        if (r.length() > MAX_REASON) throw new BadRequestException("السبب طويل — " + MAX_REASON + " حرف بحد أقصى");
        return r;
    }

    private static String login(User u) {
        if (u.getUsername() != null && !u.getUsername().isBlank()) return u.getUsername();
        return u.getEmail() != null && !u.getEmail().endsWith("@accounts.local") ? u.getEmail() : "";
    }

    private List<TeacherAcademy> managed(UserPrincipal actor) {
        bundleService.requireHeadOffice(actor);
        return academies.managedBy(actor.getTenantId()).stream().sorted(Comparator.comparing(TeacherAcademy::getName)).toList();
    }

    private TeacherAcademy managedAcademy(UserPrincipal actor, Long academyId) {
        return managed(actor).stream().filter(a -> a.getId().equals(academyId)).findFirst()
                .orElseThrow(() -> NotFoundException.of("المدرس", academyId));
    }

    /** The account a student signs in with, provided they study with a teacher this head office manages. */
    private User managedStudent(UserPrincipal actor, Long userId) {
        var tenantIds = managed(actor).stream().map(TeacherAcademy::getTenantId).toList();
        User u = users.findById(userId).orElseThrow(() -> NotFoundException.of("الطالب", userId));
        if (u.getRole() != Role.STUDENT) throw new BadRequestException("الحساب ده مش حساب طالب");
        User owner = linked.owner(u);
        if (!inScope(owner, tenantIds)) throw new ForbiddenException("الطالب ده مش تابع لمدرسين إدارتك");
        return owner;
    }

    private boolean inScope(User owner, List<Long> tenantIds) {
        return tenantIds.contains(owner.getTenantId())
                || users.findByPrimaryUserId(owner.getId()).stream().anyMatch(x -> tenantIds.contains(x.getTenantId()));
    }
}
