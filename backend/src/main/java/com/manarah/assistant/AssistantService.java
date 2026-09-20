package com.manarah.assistant;

import com.manarah.academy.TeacherScope;
import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.attendance.repo.ClassSessionRepository;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.communication.repo.SupportCaseRepository;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.exam.repo.StudentExamRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * What a teacher hands to their assistant and what the assistant works through each day: tasks, private student
 * follow-up notes, and a "desk" that gathers everything waiting on a person into one screen. All of it lives in the
 * academy's own tenant, so an assistant only ever sees their own teacher's students and work.
 */
@Service
public class AssistantService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
    private static final Set<String> TASK_STATUSES = Set.of("TODO", "DOING", "DONE");
    private static final Set<String> NOTE_KINDS = Set.of("CALL_PARENT", "ABSENCE", "ACADEMIC", "BEHAVIOR", "GENERAL");
    private static final Set<String> NOTE_STATUSES = Set.of("OPEN", "RESOLVED");
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private final AssistantTaskRepository tasks;
    private final StudentNoteRepository notes;
    private final UserRepository users;
    private final StudentRepository students;
    private final CourseRepository courses;
    private final SubmissionRepository submissions;
    private final StudentExamRepository studentExams;
    private final SupportCaseRepository supportCases;
    private final ClassSessionRepository sessions;
    private final AttendanceRecordRepository attendance;
    private final TeacherScope teacherScope;

    public AssistantService(AssistantTaskRepository tasks, StudentNoteRepository notes, UserRepository users,
                            StudentRepository students, CourseRepository courses, SubmissionRepository submissions,
                            StudentExamRepository studentExams, SupportCaseRepository supportCases,
                            ClassSessionRepository sessions, AttendanceRecordRepository attendance,
                            TeacherScope teacherScope) {
        this.tasks = tasks; this.notes = notes; this.users = users; this.students = students; this.courses = courses;
        this.submissions = submissions; this.studentExams = studentExams; this.supportCases = supportCases;
        this.sessions = sessions; this.attendance = attendance; this.teacherScope = teacherScope;
    }

    public record TaskRequest(String title, String details, String priority, LocalDate dueDate, Long studentId,
                              Long courseId, Long assignedTo) {}
    public record StatusRequest(String status, String note) {}
    public record NoteRequest(Long studentId, String kind, String body, LocalDate followUpOn) {}

    public record TaskView(Long id, String title, String details, String priority, String status, LocalDate dueDate,
                           boolean overdue, Long studentId, String studentName, Long courseId, String courseTitle,
                           Long assignedTo, String assignedToName, Long createdBy, String createdByName,
                           Instant createdAt, Instant completedAt, String resultNote) {}
    public record NoteView(Long id, Long studentId, String studentName, Long authorId, String authorName, String kind,
                           String body, LocalDate followUpOn, boolean due, String status, Instant createdAt,
                           Instant resolvedAt) {}

    // ---- Tasks --------------------------------------------------------------------------------------------

    public List<TaskView> tasks(UserPrincipal actor) {
        Names names = new Names(actor.getTenantId());
        return tasks.findByTenantIdOrderByCreatedAtDesc(actor.getTenantId()).stream()
                .filter(t -> canSee(actor, t)).map(t -> view(t, names)).toList();
    }

    @Transactional
    public TaskView createTask(UserPrincipal actor, TaskRequest req) {
        // The teacher decides what the assistant works on; the assistant picks tasks up and closes them.
        if (actor.getRole() == Role.ASSISTANT) throw new ForbiddenException("المدرس هو من يكلّف المساعد بالمهام");
        Long tenantId = actor.getTenantId();
        String title = req.title() == null ? "" : req.title().trim();
        if (title.isEmpty() || title.length() > 150) throw new BadRequestException("اكتب عنوان المهمة (حتى 150 حرفاً)");
        String details = trimmed(req.details(), 1500, "تفاصيل المهمة طويلة جداً");
        String priority = req.priority() == null || req.priority().isBlank() ? "NORMAL" : req.priority();
        if (!PRIORITIES.contains(priority)) throw new BadRequestException("درجة الأولوية غير مدعومة");
        if (req.studentId() != null) student(tenantId, req.studentId());
        if (req.courseId() != null) courses.findByTenantIdAndId(tenantId, req.courseId())
                .orElseThrow(() -> new BadRequestException("الكورس غير موجود في هذه المساحة"));

        AssistantTask t = new AssistantTask();
        t.setTenantId(tenantId); t.setTitle(title); t.setDetails(details); t.setPriority(priority);
        t.setDueDate(req.dueDate()); t.setStudentId(req.studentId()); t.setCourseId(req.courseId());
        t.setCreatedBy(actor.getId());
        if (req.assignedTo() != null) {
            users.findByTenantIdAndId(tenantId, req.assignedTo())
                    .filter(u -> u.getRole() == Role.ASSISTANT && "ACTIVE".equals(u.getStatus()))
                    .orElseThrow(() -> new BadRequestException("اختر مساعداً نشطاً من هذه المساحة"));
            t.setAssignedTo(req.assignedTo());
        }
        tasks.save(t);
        return view(t, new Names(tenantId));
    }

    @Transactional
    public TaskView setTaskStatus(UserPrincipal actor, Long id, StatusRequest req) {
        AssistantTask t = tasks.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("المهمة", id));
        if (!canSee(actor, t)) throw new ForbiddenException("هذه المهمة مكلَّف بها مساعد آخر");
        String status = req.status();
        if (status == null || !TASK_STATUSES.contains(status)) throw new BadRequestException("حالة المهمة غير مدعومة");
        // Picking up an unassigned task claims it, so two assistants don't both think it is theirs.
        if (t.getAssignedTo() == null && actor.getRole() == Role.ASSISTANT && !"TODO".equals(status)) t.setAssignedTo(actor.getId());
        t.setStatus(status);
        if ("DONE".equals(status)) {
            t.setCompletedBy(actor.getId()); t.setCompletedAt(Instant.now());
            t.setResultNote(trimmed(req.note(), 1000, "الملاحظة طويلة جداً"));
        } else {
            t.setCompletedBy(null); t.setCompletedAt(null);
        }
        tasks.save(t);
        return view(t, new Names(actor.getTenantId()));
    }

    @Transactional
    public void deleteTask(UserPrincipal actor, Long id) {
        AssistantTask t = tasks.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("المهمة", id));
        if (actor.getRole() == Role.ASSISTANT) throw new ForbiddenException("حذف المهام للمدرس فقط");
        tasks.delete(t);
    }

    private boolean canSee(UserPrincipal actor, AssistantTask t) {
        return actor.getRole() != Role.ASSISTANT || t.getAssignedTo() == null || Objects.equals(t.getAssignedTo(), actor.getId());
    }

    private TaskView view(AssistantTask t, Names n) {
        boolean overdue = t.getDueDate() != null && !"DONE".equals(t.getStatus()) && t.getDueDate().isBefore(today());
        return new TaskView(t.getId(), t.getTitle(), t.getDetails(), t.getPriority(), t.getStatus(), t.getDueDate(), overdue,
                t.getStudentId(), n.student(t.getStudentId()), t.getCourseId(), n.course(t.getCourseId()),
                t.getAssignedTo(), n.user(t.getAssignedTo()), t.getCreatedBy(), n.user(t.getCreatedBy()),
                t.getCreatedAt(), t.getCompletedAt(), t.getResultNote());
    }

    // ---- Student follow-up notes ---------------------------------------------------------------------------

    public List<NoteView> notes(UserPrincipal actor, Long studentId, boolean openOnly) {
        Long tenantId = actor.getTenantId();
        List<StudentNote> found;
        if (studentId != null) {
            student(tenantId, studentId);
            found = notes.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId);
        } else {
            found = notes.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "OPEN");
        }
        Names names = new Names(tenantId);
        return found.stream().filter(n -> !openOnly || "OPEN".equals(n.getStatus())).map(n -> view(n, names)).toList();
    }

    @Transactional
    public NoteView addNote(UserPrincipal actor, NoteRequest req) {
        if (req.studentId() == null) throw new BadRequestException("اختر الطالب");
        student(actor.getTenantId(), req.studentId());
        String body = req.body() == null ? "" : req.body().trim();
        if (body.isEmpty() || body.length() > 2000) throw new BadRequestException("اكتب الملاحظة (حتى 2000 حرف)");
        String kind = req.kind() == null || req.kind().isBlank() ? "GENERAL" : req.kind();
        if (!NOTE_KINDS.contains(kind)) throw new BadRequestException("نوع الملاحظة غير مدعوم");
        StudentNote n = new StudentNote();
        n.setTenantId(actor.getTenantId()); n.setStudentId(req.studentId()); n.setAuthorId(actor.getId());
        n.setKind(kind); n.setBody(body); n.setFollowUpOn(req.followUpOn());
        notes.save(n);
        return view(n, new Names(actor.getTenantId()));
    }

    @Transactional
    public NoteView setNoteStatus(UserPrincipal actor, Long id, StatusRequest req) {
        StudentNote n = notes.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("الملاحظة", id));
        if (req.status() == null || !NOTE_STATUSES.contains(req.status())) throw new BadRequestException("حالة الملاحظة غير مدعومة");
        n.setStatus(req.status());
        n.setResolvedAt("RESOLVED".equals(req.status()) ? Instant.now() : null);
        notes.save(n);
        return view(n, new Names(actor.getTenantId()));
    }

    @Transactional
    public void deleteNote(UserPrincipal actor, Long id) {
        StudentNote n = notes.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("الملاحظة", id));
        if (actor.getRole() == Role.ASSISTANT && !Objects.equals(n.getAuthorId(), actor.getId()))
            throw new ForbiddenException("يحذف الملاحظة المدرس أو كاتبها");
        notes.delete(n);
    }

    private NoteView view(StudentNote n, Names names) {
        boolean due = "OPEN".equals(n.getStatus()) && n.getFollowUpOn() != null && !n.getFollowUpOn().isAfter(today());
        return new NoteView(n.getId(), n.getStudentId(), names.student(n.getStudentId()), n.getAuthorId(), names.user(n.getAuthorId()),
                n.getKind(), n.getBody(), n.getFollowUpOn(), due, n.getStatus(), n.getCreatedAt(), n.getResolvedAt());
    }

    // ---- The desk: everything waiting on a person today -----------------------------------------------------

    public Map<String, Object> desk(UserPrincipal actor) {
        Long tenantId = actor.getTenantId();
        Names names = new Names(tenantId);
        LocalDate today = today();

        long homework = submissions.countByTenantIdAndStatusIn(tenantId, List.of("SUBMITTED", "LATE"));
        long exams = studentExams.countByTenantIdAndStatus(tenantId, "SUBMITTED");
        Long teacherId = teacherScope.teacherIdFor(actor);
        long support = supportCases.findByTenantIdOrderByLastMessageAtDesc(tenantId).stream()
                .filter(c -> teacherId != null && Objects.equals(c.getAssignedTeacherId(), teacherId))
                .filter(c -> Set.of("OPEN", "IN_PROGRESS").contains(c.getStatus())).count();

        List<AssistantTask> open = tasks.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(t -> canSee(actor, t)).filter(t -> !"DONE".equals(t.getStatus())).toList();
        List<TaskView> topTasks = open.stream()
                .sorted(Comparator.comparing((AssistantTask t) -> !"HIGH".equals(t.getPriority()))
                        .thenComparing(t -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate()))
                .limit(6).map(t -> view(t, names)).toList();
        long overdue = open.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count();

        List<NoteView> followUps = notes.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "OPEN").stream()
                .filter(n -> n.getFollowUpOn() != null && !n.getFollowUpOn().isAfter(today))
                .sorted(Comparator.comparing(StudentNote::getFollowUpOn)).limit(8).map(n -> view(n, names)).toList();
        long followUpCount = notes.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "OPEN").stream()
                .filter(n -> n.getFollowUpOn() != null && !n.getFollowUpOn().isAfter(today)).count();

        List<Map<String, Object>> absent = absentToday(tenantId, today, names);

        List<Map<String, Object>> atRisk = students.findByTenantId(tenantId).stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) && Set.of("AT_RISK", "NEEDS_ATTENTION").contains(s.getAcademicStatus()))
                .sorted(Comparator.comparingDouble(Student::getOverallPercent)).limit(6)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId()); m.put("name", s.getFullName()); m.put("code", s.getCode());
                    m.put("academicStatus", s.getAcademicStatus()); m.put("overallPercent", s.getOverallPercent());
                    m.put("attendanceRate", s.getAttendanceRate());
                    return m;
                }).toList();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("homeworkToGrade", homework); counts.put("examsToReview", exams); counts.put("openSupport", support);
        counts.put("openTasks", open.size()); counts.put("overdueTasks", overdue);
        counts.put("followUpsDue", followUpCount); counts.put("absentToday", absent.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts); out.put("tasks", topTasks); out.put("followUps", followUps);
        out.put("absentToday", absent); out.put("atRisk", atRisk);
        return out;
    }

    private List<Map<String, Object>> absentToday(Long tenantId, LocalDate today, Names names) {
        Instant from = today.atStartOfDay(ZONE).toInstant(), to = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var session : sessions.findByTenantIdOrderByScheduledStartDesc(tenantId)) {
            var start = session.getScheduledStart();
            if (start == null || start.isBefore(from) || !start.isBefore(to)) continue;
            for (var rec : attendance.findByTenantIdAndSessionId(tenantId, session.getId())) {
                if (!"ABSENT".equals(rec.getStatus())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("studentId", rec.getStudentId()); m.put("studentName", names.student(rec.getStudentId()));
                m.put("session", session.getTitle()); m.put("at", start);
                rows.add(m);
            }
        }
        return rows.size() > 12 ? rows.subList(0, 12) : rows;
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private static LocalDate today() { return LocalDate.now(ZONE); }

    private static String trimmed(String s, int max, String message) {
        if (s == null || s.isBlank()) return null;
        if (s.trim().length() > max) throw new BadRequestException(message);
        return s.trim();
    }

    private Student student(Long tenantId, Long id) {
        return students.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new BadRequestException("الطالب غير موجود في هذه المساحة"));
    }

    /** Lazy id-to-name lookups within one tenant, cached for the length of one request. */
    private final class Names {
        private final Long tenantId;
        private final Map<Long, String> userNames = new HashMap<>(), studentNames = new HashMap<>(), courseTitles = new HashMap<>();
        Names(Long tenantId) { this.tenantId = tenantId; }
        String user(Long id) {
            return id == null ? null : userNames.computeIfAbsent(id, k -> users.findByTenantIdAndId(tenantId, k).map(User::getFullName).orElse(""));
        }
        String student(Long id) {
            return id == null ? null : studentNames.computeIfAbsent(id, k -> students.findByTenantIdAndId(tenantId, k).map(Student::getFullName).orElse(""));
        }
        String course(Long id) {
            return id == null ? null : courseTitles.computeIfAbsent(id, k -> courses.findByTenantIdAndId(tenantId, k).map(Course::getTitle).orElse(""));
        }
    }
}
