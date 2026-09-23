package com.manarah.student;

import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.common.util.Json;
import com.manarah.common.web.PageResponse;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.risk.domain.RiskAssessment;
import com.manarah.risk.repo.RiskAssessmentRepository;
import com.manarah.student.StudentDtos.*;
import com.manarah.student.domain.Guardian;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGuardian;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import com.manarah.audit.AuditService;
import com.manarah.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final RiskAssessmentRepository riskAssessments;
    private final AuditService audit;
    private final com.manarah.identity.repo.UserRepository users;

    public StudentService(StudentRepository students, GuardianRepository guardians, StudentGuardianRepository links,
                          EnrollmentRepository enrollments, CourseRepository courses,
                          RiskAssessmentRepository riskAssessments, AuditService audit,
                          com.manarah.identity.repo.UserRepository users) {
        this.users = users;
        this.students = students;
        this.guardians = guardians;
        this.links = links;
        this.enrollments = enrollments;
        this.courses = courses;
        this.riskAssessments = riskAssessments;
        this.audit = audit;
    }

    public PageResponse<StudentSummary> search(String q, Long branchId, String status, String academicStatus,
                                               int page, int size) {
        Long tenantId = TenantContext.require();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(
                students.search(tenantId, branchId, blankToNull(status), blankToNull(academicStatus), blankToNull(q), pageable),
                this::toSummary);
    }

    public List<StudentSummary> all() {
        return students.findByTenantId(TenantContext.require()).stream().map(this::toSummary).toList();
    }

    /** The student record linked to the currently logged-in STUDENT user. */
    public StudentDetail me(UserPrincipal actor) {
        Student s = students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .orElseThrow(() -> new NotFoundException("لا يوجد ملف طالب مرتبط بحسابك"));
        return get(s.getId());
    }

    public Long myStudentId(UserPrincipal actor) {
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId()).map(Student::getId).orElse(null);
    }

    /** Students linked to the currently logged-in PARENT user (their children). */
    public List<StudentSummary> children(UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        return guardians.findByTenantIdAndUserId(tenantId, actor.getId())
                .map(g -> links.findByTenantIdAndGuardianId(tenantId, g.getId()).stream()
                        .map(l -> students.findByTenantIdAndId(tenantId, l.getStudentId()).orElse(null))
                        .filter(java.util.Objects::nonNull).map(this::toSummary).toList())
                .orElse(List.of());
    }

    public StudentDetail get(Long id) {
        Long tenantId = TenantContext.require();
        Student s = students.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الطالب", id));

        List<GuardianView> gv = links.findByTenantIdAndStudentId(tenantId, id).stream()
                .map(l -> guardians.findByTenantIdAndId(tenantId, l.getGuardianId())
                        .map(g -> new GuardianView(g.getId(), g.getFullName(), g.getPhone(), g.getEmail(), l.getRelation(), g.getUserId()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull).toList();

        List<EnrollmentView> ev = enrollments.findByTenantIdAndStudentId(tenantId, id).stream()
                .map(e -> new EnrollmentView(e.getId(), e.getCourseId(),
                        courses.findByTenantIdAndId(tenantId, e.getCourseId()).map(c -> c.getTitle()).orElse("—"),
                        e.getStatus()))
                .toList();

        RiskView rv = riskAssessments.findFirstByTenantIdAndStudentIdOrderByAssessedAtDesc(tenantId, id)
                .map(this::toRiskView).orElse(null);

        return new StudentDetail(toSummary(s), s.getNationalId(), s.getBirthDate(), s.getGender(),
                s.getSchool(), s.getNotes(), gv, ev, rv);
    }

    @Transactional
    public StudentDetail create(UserPrincipal actor, CreateStudentRequest req) {
        Long tenantId = TenantContext.require();
        boolean autoCode = req.code() == null || req.code().isBlank();
        Student s = new Student();
        s.setTenantId(tenantId);
        s.setFullName(req.fullName());
        s.setBranchId(req.branchId() != null ? req.branchId() : actor.getBranchId());
        // A temporary placeholder when auto-generating: satisfies the NOT NULL/UNIQUE(tenant_id, code)
        // constraint for the initial insert. Replaced below with an ID-derived code once the DB has
        // assigned one — see the comment there for why this avoids a count-based race entirely.
        s.setCode(autoCode ? "PENDING-" + java.util.UUID.randomUUID() : req.code());
        s.setNationalId(req.nationalId());
        s.setBirthDate(req.birthDate());
        s.setGender(req.gender());
        s.setGradeLevel(req.gradeLevel());
        s.setGrade(req.grade());
        s.setSchool(req.school());
        s.setPhone(req.phone());
        s.setNotes(req.notes());
        try {
            students.save(s);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("كود الطالب مستخدم بالفعل");
        }
        if (autoCode) {
            // Derive the code from the just-assigned, DB-atomic auto-increment ID instead of
            // counting existing rows — two concurrent requests can never race for the same ID,
            // so this is unique by construction with no retry loop needed.
            s.setCode(String.format("STD-%05d", s.getId()));
            students.save(s);
        }
        audit.record(actor, "CREATE_STUDENT", "Student", s.getId(), null, s.getFullName());
        return get(s.getId());
    }

    @Transactional
    public StudentDetail update(UserPrincipal actor, Long id, UpdateStudentRequest req) {
        Long tenantId = TenantContext.require();
        Student s = students.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الطالب", id));
        if (req.fullName() != null) s.setFullName(req.fullName());
        if (req.status() != null) s.setStatus(req.status());
        if (req.gradeLevel() != null) s.setGradeLevel(req.gradeLevel());
        if (req.grade() != null) s.setGrade(req.grade());
        if (req.school() != null) s.setSchool(req.school());
        if (req.phone() != null) s.setPhone(req.phone());
        if (req.notes() != null) s.setNotes(req.notes());
        if (req.branchId() != null) s.setBranchId(req.branchId());
        students.save(s);
        audit.record(actor, "UPDATE_STUDENT", "Student", s.getId(), null, s.getFullName());
        return get(id);
    }

    @Transactional
    public GuardianView addGuardian(UserPrincipal actor, Long studentId, CreateGuardianRequest req) {
        Long tenantId = TenantContext.require();
        students.findByTenantIdAndId(tenantId, studentId).orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        Guardian g = new Guardian();
        g.setTenantId(tenantId);
        g.setFullName(req.fullName());
        g.setPhone(req.phone());
        g.setEmail(req.email());
        guardians.save(g);
        StudentGuardian link = new StudentGuardian();
        link.setTenantId(tenantId);
        link.setStudentId(studentId);
        link.setGuardianId(g.getId());
        link.setRelation(req.relation());
        links.save(link);
        audit.record(actor, "ADD_GUARDIAN", "Student", studentId, null, g.getFullName());
        return new GuardianView(g.getId(), g.getFullName(), g.getPhone(), g.getEmail(), req.relation(), g.getUserId());
    }

    private StudentSummary toSummary(Student s) {
        return new StudentSummary(s.getId(), s.getCode(), s.getFullName(), s.getGrade(), s.getGradeLevel(),
                s.getStatus(), s.getAcademicStatus(), s.getAvgScore(), s.getAttendanceRate(),
                s.getHomeworkRate(), s.getOverallPercent(), s.getBranchId(), s.getPhone(), loginEmail(s));
    }

    /** The email the student signs in with — for a student who joined from another teacher, their own account's.
     *  Accounts a teacher creates by username get a placeholder address, which is not shown. */
    private String loginEmail(Student s) {
        if (s.getUserId() == null) return null;
        return users.findById(s.getUserId()).map(u -> u.getPrimaryUserId() == null ? u : users.findById(u.getPrimaryUserId()).orElse(u))
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.endsWith("@accounts.local")).orElse(null);
    }

    private RiskView toRiskView(RiskAssessment ra) {
        List<String> reasons = ra.getReasons() == null ? List.of()
                : Json.read(ra.getReasons(), new TypeReference<List<String>>() {});
        return new RiskView(ra.getLevel(), ra.getScore(), reasons, ra.getAssessedAt().toString());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
