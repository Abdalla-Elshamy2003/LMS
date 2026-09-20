package com.manarah.communication;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.communication.domain.SupportCase;
import com.manarah.communication.domain.SupportMessage;
import com.manarah.communication.repo.SupportCaseRepository;
import com.manarah.communication.repo.SupportMessageRepository;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.notification.NotificationService;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class SupportService {
    private static final Set<String> CATEGORIES = Set.of("ACADEMIC", "TEACHER", "COMPLAINT", "TECHNICAL", "PAYMENT", "GENERAL");
    private static final Set<String> PRIORITIES = Set.of("NORMAL", "HIGH", "URGENT");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "WAITING_REPLY", "RESOLVED", "CLOSED");

    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final NotificationService notifications;
    private final com.manarah.academy.TeacherScope teacherScope;

    public SupportService(SupportCaseRepository cases, SupportMessageRepository messages, UserRepository users,
                          StudentRepository students, GuardianRepository guardians, StudentGuardianRepository links,
                          CourseRepository courses, EnrollmentRepository enrollments, NotificationService notifications,
                          com.manarah.academy.TeacherScope teacherScope) {
        this.cases = cases; this.messages = messages; this.users = users; this.students = students;
        this.guardians = guardians; this.links = links; this.courses = courses; this.enrollments = enrollments;
        this.notifications = notifications; this.teacherScope = teacherScope;
    }

    public record PersonOption(Long id, String name, String code) {}
    public record CourseOption(Long studentId, Long id, String title, String subject, Long teacherId, String teacherName) {}
    public record ContextView(List<PersonOption> students, List<CourseOption> courses) {}
    public record MessageView(Long id, Long authorUserId, String authorName, String authorRole, String body, Instant createdAt) {}
    public record CaseView(Long id, Long createdByUserId, String createdByName, String createdByRole,
                           Long studentId, String studentName, Long courseId, String courseTitle,
                           Long assignedTeacherId, String assignedTeacherName, String category, String priority,
                           String subject, String status, Instant lastMessageAt, Instant createdAt,
                           List<MessageView> messages) {}
    public record CreateRequest(Long studentId, Long courseId, String destination, String category,
                                String priority, String subject, String message) {}
    public record ReplyRequest(String message) {}
    public record StatusRequest(String status) {}

    public ContextView context(UserPrincipal actor) {
        List<Student> linked = linkedStudents(actor);
        List<PersonOption> people = linked.stream().map(s -> new PersonOption(s.getId(), s.getFullName(), s.getCode())).toList();
        List<CourseOption> options = new ArrayList<>();
        for (Student student : linked) {
            for (Enrollment enrollment : enrollments.findByTenantIdAndStudentId(actor.getTenantId(), student.getId())) {
                if (!Set.of("ACTIVE", "COMPLETED").contains(enrollment.getStatus())) continue;
                courses.findByTenantIdAndId(actor.getTenantId(), enrollment.getCourseId()).ifPresent(course ->
                        options.add(new CourseOption(student.getId(), course.getId(), course.getTitle(), course.getSubject(),
                                course.getTeacherId(), userName(actor.getTenantId(), course.getTeacherId()))));
            }
        }
        return new ContextView(people, options);
    }

    public List<CaseView> list(UserPrincipal actor) {
        return cases.findByTenantIdOrderByLastMessageAtDesc(actor.getTenantId()).stream()
                .filter(c -> canView(actor, c))
                .map(this::toView).toList();
    }

    @Transactional
    public CaseView create(UserPrincipal actor, CreateRequest request) {
        if (actor.getRole() != Role.STUDENT && actor.getRole() != Role.PARENT)
            throw new ForbiddenException("إنشاء الاستفسار متاح للطالب وولي الأمر");
        validateText(request.subject(), request.message());
        String category = normalized(request.category(), "GENERAL");
        String priority = normalized(request.priority(), "NORMAL");
        if (!CATEGORIES.contains(category)) throw new BadRequestException("نوع الطلب غير مدعوم");
        if (!PRIORITIES.contains(priority)) throw new BadRequestException("درجة الأولوية غير مدعومة");

        List<Student> linked = linkedStudents(actor);
        Long studentId = resolveStudent(actor, request.studentId(), linked);
        Course course = null;
        if (request.courseId() != null) {
            course = courses.findByTenantIdAndId(actor.getTenantId(), request.courseId())
                    .orElseThrow(() -> NotFoundException.of("الكورس", request.courseId()));
            if (studentId == null || !enrollments.existsByTenantIdAndStudentIdAndCourseId(actor.getTenantId(), studentId, course.getId()))
                throw new ForbiddenException("الطالب غير مسجّل في هذا الكورس");
        }
        String destination = normalized(request.destination(), "ADMIN");
        if (!Set.of("ADMIN", "TEACHER").contains(destination)) throw new BadRequestException("جهة الاستفسار غير مدعومة");
        if ("TEACHER".equals(destination) && (course == null || course.getTeacherId() == null))
            throw new BadRequestException("اختر مادة لها مدرس للتواصل معه");

        Instant now = Instant.now();
        SupportCase c = new SupportCase();
        c.setTenantId(actor.getTenantId()); c.setCreatedByUserId(actor.getId()); c.setStudentId(studentId);
        c.setCourseId(course == null ? null : course.getId());
        c.setAssignedTeacherId("TEACHER".equals(destination) ? course.getTeacherId() : null);
        c.setCategory(category); c.setPriority(priority); c.setSubject(request.subject().trim());
        c.setStatus("OPEN"); c.setCreatedAt(now); c.setUpdatedAt(now); c.setLastMessageAt(now);
        cases.save(c);
        saveMessage(c, actor.getId(), request.message());
        notifyHandlers(c, "طلب جديد: " + c.getSubject(), actor.getFullName() + " أرسل طلباً جديداً");
        return toView(c);
    }

    @Transactional
    public CaseView reply(UserPrincipal actor, Long id, ReplyRequest request) {
        if (request.message() == null || request.message().trim().length() < 2 || request.message().trim().length() > 4000)
            throw new BadRequestException("اكتب رسالة واضحة لا تتجاوز 4000 حرف");
        SupportCase c = findVisible(actor, id);
        if ("CLOSED".equals(c.getStatus())) throw new BadRequestException("المحادثة مغلقة");
        saveMessage(c, actor.getId(), request.message());
        c.setLastMessageAt(Instant.now()); c.setUpdatedAt(Instant.now());
        boolean requester = Objects.equals(c.getCreatedByUserId(), actor.getId());
        c.setStatus(requester ? "OPEN" : "WAITING_REPLY"); c.setResolvedAt(null);
        cases.save(c);
        if (requester) notifyHandlers(c, "رد جديد: " + c.getSubject(), actor.getFullName() + " أضاف رسالة");
        else notifyUser(c.getTenantId(), c.getCreatedByUserId(), "رد على طلبك: " + c.getSubject(), "وصل رد جديد ويمكنك متابعته من مركز التواصل", c.getId());
        return toView(c);
    }

    @Transactional
    public CaseView changeStatus(UserPrincipal actor, Long id, StatusRequest request) {
        SupportCase c = findVisible(actor, id);
        if (!isHandler(actor, c)) throw new ForbiddenException("تغيير حالة الطلب متاح للإدارة أو المدرس المسؤول");
        String status = normalized(request.status(), "");
        if (!STATUSES.contains(status)) throw new BadRequestException("حالة الطلب غير مدعومة");
        c.setStatus(status); c.setUpdatedAt(Instant.now());
        c.setResolvedAt(Set.of("RESOLVED", "CLOSED").contains(status) ? Instant.now() : null);
        cases.save(c);
        notifyUser(c.getTenantId(), c.getCreatedByUserId(), "تحديث طلبك: " + c.getSubject(), "تم تحديث الحالة إلى " + statusArabic(status), c.getId());
        return toView(c);
    }

    private void validateText(String subject, String message) {
        if (subject == null || subject.trim().length() < 3 || subject.trim().length() > 160)
            throw new BadRequestException("عنوان الطلب يجب أن يكون بين 3 و160 حرفاً");
        if (message == null || message.trim().length() < 3 || message.trim().length() > 4000)
            throw new BadRequestException("تفاصيل الطلب يجب أن تكون بين 3 و4000 حرف");
    }

    private Long resolveStudent(UserPrincipal actor, Long requested, List<Student> linked) {
        if (actor.getRole() == Role.STUDENT) return linked.stream().findFirst().map(Student::getId)
                .orElseThrow(() -> new NotFoundException("لا يوجد ملف طالب مرتبط بالحساب"));
        if (requested == null && linked.size() == 1) return linked.get(0).getId();
        if (requested == null) return null;
        return linked.stream().filter(s -> Objects.equals(s.getId(), requested)).findFirst().map(Student::getId)
                .orElseThrow(() -> new ForbiddenException("يمكنك اختيار أحد أبنائك المرتبطين فقط"));
    }

    private List<Student> linkedStudents(UserPrincipal actor) {
        if (actor.getRole() == Role.STUDENT) return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId()).map(List::of).orElse(List.of());
        if (actor.getRole() == Role.PARENT) return guardians.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .map(g -> links.findByTenantIdAndGuardianId(actor.getTenantId(), g.getId()).stream()
                        .map(l -> students.findByTenantIdAndId(actor.getTenantId(), l.getStudentId()).orElse(null))
                        .filter(Objects::nonNull).toList()).orElse(List.of());
        return List.of();
    }

    private SupportCase findVisible(UserPrincipal actor, Long id) {
        SupportCase c = cases.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("الطلب", id));
        if (!canView(actor, c)) throw new ForbiddenException("ليس لديك صلاحية لعرض هذا الطلب");
        return c;
    }

    private boolean canView(UserPrincipal actor, SupportCase c) {
        return isManager(actor) || Objects.equals(c.getCreatedByUserId(), actor.getId()) || handlesForTeacher(actor, c);
    }

    private boolean isHandler(UserPrincipal actor, SupportCase c) {
        return isManager(actor) || handlesForTeacher(actor, c);
    }

    /** A teacher handles the cases addressed to them; their assistant handles the same ones on their behalf. */
    private boolean handlesForTeacher(UserPrincipal actor, SupportCase c) {
        Long teacherId = teacherScope.teacherIdFor(actor);
        return teacherId != null && Objects.equals(c.getAssignedTeacherId(), teacherId);
    }

    private boolean isManager(UserPrincipal actor) {
        return actor.isAdmin() || actor.getRole() == Role.SUPPORT;
    }

    private void saveMessage(SupportCase c, Long authorId, String body) {
        SupportMessage m = new SupportMessage(); m.setTenantId(c.getTenantId()); m.setCaseId(c.getId());
        m.setAuthorUserId(authorId); m.setBody(body.trim()); messages.save(m);
    }

    private void notifyHandlers(SupportCase c, String title, String body) {
        if (c.getAssignedTeacherId() != null) {
            notifyUser(c.getTenantId(), c.getAssignedTeacherId(), title, body, c.getId());
            return;
        }
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        for (Role role : List.of(Role.SUPER_ADMIN, Role.BRANCH_ADMIN, Role.ACADEMIC_MANAGER, Role.SUPPORT))
            users.findByTenantIdAndRole(c.getTenantId(), role).forEach(u -> recipients.add(u.getId()));
        recipients.forEach(id -> notifyUser(c.getTenantId(), id, title, body, c.getId()));
    }

    private void notifyUser(Long tenantId, Long userId, String title, String body, Long caseId) {
        if (userId == null) return;
        notifications.notify(tenantId, new NotifyCommand(userId, null, title, body, "SUPPORT", "SupportCase", caseId, List.of("IN_APP")));
    }

    private CaseView toView(SupportCase c) {
        User creator = users.findByTenantIdAndId(c.getTenantId(), c.getCreatedByUserId()).orElse(null);
        List<MessageView> thread = messages.findByTenantIdAndCaseIdOrderByCreatedAtAsc(c.getTenantId(), c.getId()).stream()
                .map(m -> { User author = users.findByTenantIdAndId(c.getTenantId(), m.getAuthorUserId()).orElse(null);
                    return new MessageView(m.getId(), m.getAuthorUserId(), author == null ? "مستخدم" : author.getFullName(),
                            author == null ? "" : author.getRole().name(), m.getBody(), m.getCreatedAt()); }).toList();
        return new CaseView(c.getId(), c.getCreatedByUserId(), creator == null ? "مستخدم" : creator.getFullName(),
                creator == null ? "" : creator.getRole().name(), c.getStudentId(), studentName(c.getTenantId(), c.getStudentId()),
                c.getCourseId(), courseTitle(c.getTenantId(), c.getCourseId()), c.getAssignedTeacherId(),
                userName(c.getTenantId(), c.getAssignedTeacherId()), c.getCategory(), c.getPriority(), c.getSubject(),
                c.getStatus(), c.getLastMessageAt(), c.getCreatedAt(), thread);
    }

    private String userName(Long tenant, Long id) { return id == null ? null : users.findByTenantIdAndId(tenant, id).map(User::getFullName).orElse(null); }
    private String studentName(Long tenant, Long id) { return id == null ? null : students.findByTenantIdAndId(tenant, id).map(Student::getFullName).orElse(null); }
    private String courseTitle(Long tenant, Long id) { return id == null ? null : courses.findByTenantIdAndId(tenant, id).map(Course::getTitle).orElse(null); }
    private static String normalized(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT); }
    private static String statusArabic(String status) { return Map.of("OPEN", "مفتوح", "IN_PROGRESS", "قيد المتابعة", "WAITING_REPLY", "بانتظار ردك", "RESOLVED", "تم الحل", "CLOSED", "مغلق").getOrDefault(status, status); }
}
