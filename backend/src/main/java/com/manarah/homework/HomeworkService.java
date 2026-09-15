package com.manarah.homework;

import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.common.util.Json;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.homework.HomeworkDtos.*;
import com.manarah.homework.domain.Assignment;
import com.manarah.homework.domain.GradingComment;
import com.manarah.homework.domain.Submission;
import com.manarah.homework.domain.SubmissionFile;
import com.manarah.homework.repo.AssignmentRepository;
import com.manarah.homework.repo.GradingCommentRepository;
import com.manarah.homework.repo.SubmissionFileRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.StudentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HomeworkService {

    private static final Set<String> ACTIVE = Set.of("ACTIVE", "COMPLETED");
    private static final int MAX_FILES = 5;

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final SubmissionFileRepository submissionFiles;
    private final GradingCommentRepository comments;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;
    private final com.manarah.course.LearningService learning;
    private final com.manarah.common.storage.UploadOwnership uploadOwnership;

    public HomeworkService(AssignmentRepository assignments, SubmissionRepository submissions,
                           SubmissionFileRepository submissionFiles, GradingCommentRepository comments,
                           CourseRepository courses, EnrollmentRepository enrollments, StudentRepository students,
                           ApplicationEventPublisher events, com.manarah.course.LearningService learning,
                           com.manarah.common.storage.UploadOwnership uploadOwnership) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.submissionFiles = submissionFiles;
        this.comments = comments;
        this.courses = courses;
        this.enrollments = enrollments;
        this.students = students;
        this.events = events;
        this.learning = learning;
        this.uploadOwnership = uploadOwnership;
    }

    public List<AssignmentView> byCourse(Long courseId) {
        Long tenantId = TenantContext.require();
        return assignments.findByTenantIdAndCourseId(tenantId, courseId).stream().map(this::toView).toList();
    }

    public List<AssignmentView> all() {
        Long tenantId = TenantContext.require();
        return assignments.findByTenantId(tenantId).stream().map(this::toView).toList();
    }

    public AssignmentView get(UserPrincipal actor, Long id) {
        Long tenantId = TenantContext.require();
        Assignment a = assignments.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الواجب", id));
        learning.access(actor, a.getCourseId(), false);
        return toView(a);
    }

    // ================= Rubric =================

    static List<RubricCriterion> parseRubric(String json) {
        if (json == null || json.isBlank()) return null;
        return Json.read(json, new TypeReference<List<RubricCriterion>>() {});
    }

    private static Map<String, Double> parseScores(String json) {
        if (json == null || json.isBlank()) return null;
        return Json.read(json, new TypeReference<Map<String, Double>>() {});
    }

    /** Validates a rubric and returns a normalised copy with stable ids; total must equal maxScore so the
     *  gradebook never receives a score out of range. */
    private static List<RubricCriterion> normaliseRubric(List<RubricCriterion> rubric, double maxScore) {
        if (rubric == null || rubric.isEmpty()) return null;
        if (rubric.size() > 30) throw new BadRequestException("الحد الأقصى 30 معياراً");
        List<RubricCriterion> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        double total = 0;
        int i = 0;
        for (RubricCriterion c : rubric) {
            if (c == null || c.title() == null || c.title().isBlank()) throw new BadRequestException("كل معيار يحتاج عنواناً");
            if (!Double.isFinite(c.maxPoints()) || c.maxPoints() <= 0) throw new BadRequestException("درجة المعيار «" + c.title() + "» يجب أن تكون أكبر من صفر");
            String id = c.id() == null || c.id().isBlank() || !ids.add(c.id()) ? "c" + (++i) + "-" + UUID.randomUUID().toString().substring(0, 6) : c.id();
            ids.add(id);
            List<RubricLevel> levels = null;
            if (c.levels() != null && !c.levels().isEmpty()) {
                levels = new ArrayList<>();
                for (RubricLevel l : c.levels()) {
                    if (l == null || l.label() == null || l.label().isBlank()) throw new BadRequestException("كل مستوى يحتاج اسماً");
                    if (!Double.isFinite(l.points()) || l.points() < 0 || l.points() > c.maxPoints()) throw new BadRequestException("درجة المستوى «" + l.label() + "» خارج نطاق المعيار");
                    levels.add(new RubricLevel(l.label().trim(), l.points(), l.description()));
                }
            }
            out.add(new RubricCriterion(id, c.title().trim(), c.description(), c.maxPoints(), levels));
            total += c.maxPoints();
        }
        if (Math.abs(total - maxScore) > 0.001)
            throw new BadRequestException("مجموع درجات المعايير (" + trim(total) + ") يجب أن يساوي الدرجة العظمى (" + trim(maxScore) + ")");
        return out;
    }

    private static String trim(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v); }

    // ================= Assignment authoring =================

    private void validatePolicy(Double maxScore, Double latePenaltyPercent, Instant startAt, Instant deadline) {
        if (maxScore != null && (!Double.isFinite(maxScore) || maxScore <= 0 || maxScore > 10000))
            throw new BadRequestException("الدرجة العظمى يجب أن تكون أكبر من صفر وحتى 10000");
        if (latePenaltyPercent != null && (!Double.isFinite(latePenaltyPercent) || latePenaltyPercent < 0 || latePenaltyPercent > 100))
            throw new BadRequestException("غرامة التأخير من صفر إلى 100٪ لكل يوم");
        if (startAt != null && deadline != null && !deadline.isAfter(startAt))
            throw new BadRequestException("موعد التسليم يجب أن يأتي بعد فتح الواجب");
    }

    @Transactional
    public AssignmentView create(UserPrincipal actor, CreateAssignmentRequest req) {
        Long tenantId = TenantContext.require();
        learning.access(actor, req.courseId(), true);
        courses.findByTenantIdAndId(tenantId, req.courseId()).orElseThrow(() -> NotFoundException.of("الكورس", req.courseId()));
        requireFileKey(req.fileKey(), tenantId, "assignments");
        uploadOwnership.requireOwned(actor, req.fileKey(), null);
        if (req.title().length() > 200) throw new BadRequestException("العنوان أطول من الحد المسموح");
        validatePolicy(req.maxScore(), req.latePenaltyPercent(), req.startAt(), req.deadline());
        double max = req.maxScore() != null ? req.maxScore() : 100;
        List<RubricCriterion> rubric = normaliseRubric(req.rubric(), max);
        Assignment a = new Assignment();
        a.setTenantId(tenantId);
        a.setCourseId(req.courseId());
        a.setTitle(req.title().trim());
        a.setDescription(req.description());
        a.setFileKey(req.fileKey());
        a.setStartAt(req.startAt());
        a.setDeadline(req.deadline());
        a.setMaxScore(max);
        a.setAllowLate(req.allowLate() == null || req.allowLate());
        a.setLatePenaltyPercent(req.latePenaltyPercent() != null ? req.latePenaltyPercent() : 0);
        a.setRubric(rubric == null ? null : Json.write(rubric));
        a.setCreatedBy(actor.getId());
        assignments.save(a);
        return toView(a);
    }

    /** Max score and rubric are frozen once a submission has been graded, so recorded grades stay consistent. */
    @Transactional
    public AssignmentView update(UserPrincipal actor, Long id, UpdateAssignmentRequest req) {
        Long tenantId = TenantContext.require();
        Assignment a = assignments.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الواجب", id));
        learning.access(actor, a.getCourseId(), true);
        boolean graded = submissions.findByTenantIdAndAssignmentId(tenantId, id).stream().anyMatch(s -> "GRADED".equals(s.getStatus()));
        if (req.title() != null) { if (req.title().isBlank() || req.title().length() > 200) throw new BadRequestException("عنوان غير صالح"); a.setTitle(req.title().trim()); }
        if (req.description() != null) a.setDescription(req.description());
        if (Boolean.TRUE.equals(req.clearFile())) a.setFileKey(null);
        if (req.fileKey() != null && !req.fileKey().isBlank()) {
            requireFileKey(req.fileKey(), tenantId, "assignments");
            uploadOwnership.requireOwned(actor, req.fileKey(), a.getFileKey());
            a.setFileKey(req.fileKey());
        }
        if (Boolean.TRUE.equals(req.clearDeadline())) a.setDeadline(null);
        if (req.startAt() != null) a.setStartAt(req.startAt());
        if (req.deadline() != null) a.setDeadline(req.deadline());
        if (req.allowLate() != null) a.setAllowLate(req.allowLate());
        if (req.latePenaltyPercent() != null) a.setLatePenaltyPercent(req.latePenaltyPercent());
        validatePolicy(req.maxScore(), req.latePenaltyPercent(), a.getStartAt(), a.getDeadline());
        if ((req.maxScore() != null || req.rubric() != null || Boolean.TRUE.equals(req.clearRubric())) && graded)
            throw new BadRequestException("الدرجة العظمى والمعايير تُجمَّد بعد اعتماد أول درجة");
        if (req.maxScore() != null) a.setMaxScore(req.maxScore());
        if (Boolean.TRUE.equals(req.clearRubric())) a.setRubric(null);
        List<RubricCriterion> rubric = req.rubric() != null ? req.rubric() : parseRubric(a.getRubric());
        rubric = normaliseRubric(rubric, a.getMaxScore());
        a.setRubric(rubric == null ? null : Json.write(rubric));
        a.setUpdatedAt(Instant.now());
        assignments.save(a);
        return toView(a);
    }

    @Transactional
    public void delete(UserPrincipal actor, Long id) {
        Long tenantId = TenantContext.require();
        Assignment a = assignments.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الواجب", id));
        learning.access(actor, a.getCourseId(), true);
        var subs = submissions.findByTenantIdAndAssignmentId(tenantId, id);
        if (subs.stream().anyMatch(s -> "GRADED".equals(s.getStatus())))
            throw new BadRequestException("لا يمكن حذف واجب له درجات معتمدة");
        for (Submission s : subs) { submissionFiles.deleteByTenantIdAndSubmissionId(tenantId, s.getId()); submissions.delete(s); }
        assignments.delete(a);
    }

    public List<SubmissionView> submissions(UserPrincipal actor, Long assignmentId) {
        Long tenantId = TenantContext.require();
        Assignment a = assignments.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> NotFoundException.of("الواجب", assignmentId));
        learning.access(actor, a.getCourseId(), true);
        return submissions.findByTenantIdAndAssignmentId(tenantId, assignmentId).stream().map(s -> toSubmissionView(s, a)).toList();
    }

    /** The logged-in student's own assignments (scoped to their enrolled courses) with their submission state. */
    public List<MyAssignmentView> myAssignments(Long studentId) {
        Long tenantId = TenantContext.require();
        Set<Long> courseIds = enrollments.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(e -> ACTIVE.contains(e.getStatus())).map(e -> e.getCourseId()).collect(Collectors.toSet());
        Instant now = Instant.now();
        return assignments.findByTenantId(tenantId).stream()
                .filter(a -> courseIds.contains(a.getCourseId()))
                .filter(a -> a.getStartAt() == null || !now.isBefore(a.getStartAt()))
                .map(a -> {
                    Submission sub = submissions.findByTenantIdAndAssignmentIdAndStudentId(tenantId, a.getId(), studentId).orElse(null);
                    String courseTitle = courses.findByTenantIdAndId(tenantId, a.getCourseId()).map(c -> c.getTitle()).orElse("—");
                    return new MyAssignmentView(a.getId(), a.getCourseId(), courseTitle, a.getTitle(), a.getDescription(),
                            a.getFileKey(), a.getStartAt(), a.getDeadline(), a.getMaxScore(),
                            sub == null ? "PENDING" : sub.getStatus(),
                            sub == null ? null : sub.getScore(),
                            sub == null ? null : sub.getFeedback(),
                            sub == null ? null : sub.getText(),
                            sub == null ? null : sub.getFileKey(),
                            sub == null ? null : sub.getSubmittedAt(),
                            sub == null ? List.of() : files(sub),
                            a.isAllowLate(), a.getLatePenaltyPercent(), parseRubric(a.getRubric()),
                            sub == null ? null : parseScores(sub.getRubricScores()),
                            sub == null ? null : sub.getRawScore(), sub == null ? 0 : sub.getPenaltyPercent(),
                            sub == null ? null : sub.getGradedAt(), sub == null ? null : sub.getReturnedAt(),
                            sub == null ? 0 : sub.getResubmissions(), now);
                })
                .sorted((x, y) -> {
                    if (x.deadline() == null && y.deadline() == null) return 0;
                    if (x.deadline() == null) return 1;
                    if (y.deadline() == null) return -1;
                    return y.deadline().compareTo(x.deadline());
                })
                .toList();
    }

    // ================= Submitting =================

    /** @param studentId the resolved owner of this submission — the controller decides this
     *  (a student's own id for STUDENT callers, never trusted from the request body for them). */
    @Transactional
    public SubmissionView submit(UserPrincipal actor, Long studentId, SubmitRequest req) {
        Long tenantId = TenantContext.require();
        Assignment a = assignments.findByTenantIdAndId(tenantId, req.assignmentId())
                .orElseThrow(() -> NotFoundException.of("الواجب", req.assignmentId()));
        if (enrollments.findByTenantIdAndStudentIdAndCourseId(tenantId, studentId, a.getCourseId()).filter(e -> ACTIVE.contains(e.getStatus())).isEmpty())
            throw new ForbiddenException("هذا الواجب خارج الكورسات المتاحة للطالب");
        if (actor.getRole() != com.manarah.identity.domain.Role.STUDENT) learning.access(actor, a.getCourseId(), true);
        Submission s = submissions.findByTenantIdAndAssignmentIdAndStudentId(tenantId, req.assignmentId(), studentId)
                .orElseGet(Submission::new);
        Instant now = Instant.now();
        if (a.getStartAt() != null && now.isBefore(a.getStartAt()))
            throw new BadRequestException("لم يفتح الواجب بعد");
        if ("GRADED".equals(s.getStatus()))
            throw new BadRequestException("تم تصحيح الواجب ولا يمكن تغيير التسليم");
        boolean late = a.getDeadline() != null && now.isAfter(a.getDeadline());
        if (late && !a.isAllowLate() && actor.getRole() == com.manarah.identity.domain.Role.STUDENT)
            throw new BadRequestException("انتهى موعد التسليم ولا يقبل هذا الواجب التسليم المتأخر");

        // Merge legacy single fileKey with the attachment list; keep existing attachments the client re-sent.
        List<AttachmentInput> wanted = new ArrayList<>();
        if (req.files() != null) wanted.addAll(req.files().stream().filter(Objects::nonNull).toList());
        if (req.fileKey() != null && !req.fileKey().isBlank() && wanted.stream().noneMatch(f -> req.fileKey().equals(f.fileKey())))
            wanted.add(0, new AttachmentInput(req.fileKey(), null, null));
        if (wanted.size() > MAX_FILES) throw new BadRequestException("الحد الأقصى " + MAX_FILES + " ملفات لكل تسليم");
        if ((req.text() == null || req.text().isBlank()) && wanted.isEmpty())
            throw new BadRequestException("أضف إجابة أو ملفاً قبل التسليم");
        if (req.text() != null && req.text().length() > 50000)
            throw new BadRequestException("الإجابة أطول من الحد المسموح");
        Set<String> existing = s.getId() == null ? Set.of()
                : submissionFiles.findByTenantIdAndSubmissionIdOrderByUploadedAt(tenantId, s.getId()).stream().map(SubmissionFile::getFileKey).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (AttachmentInput f : wanted) {
            requireFileKey(f.fileKey(), tenantId, "submissions");
            if (!seen.add(f.fileKey())) throw new BadRequestException("ملف مكرر في التسليم");
            if (!existing.contains(f.fileKey())) uploadOwnership.requireOwned(actor, f.fileKey(), null);
        }

        boolean resubmission = s.getId() != null && s.getSubmittedAt() != null;
        s.setTenantId(tenantId);
        s.setAssignmentId(req.assignmentId());
        s.setStudentId(studentId);
        s.setText(req.text() == null ? null : req.text().trim());
        s.setFileKey(wanted.isEmpty() ? null : wanted.get(0).fileKey());
        s.setStatus(late ? "LATE" : "SUBMITTED");
        s.setSubmittedAt(now);
        if (resubmission) s.setResubmissions(s.getResubmissions() + 1);
        submissions.save(s);
        // Hibernate queues inserts before deletes; flush so a kept file key does not collide with itself.
        submissionFiles.deleteByTenantIdAndSubmissionId(tenantId, s.getId());
        submissionFiles.flush();
        for (AttachmentInput f : wanted) {
            SubmissionFile sf = new SubmissionFile();
            sf.setTenantId(tenantId);
            sf.setSubmissionId(s.getId());
            sf.setFileKey(f.fileKey());
            sf.setName(cleanName(f.name(), f.fileKey()));
            sf.setSizeBytes(f.size() == null || f.size() < 0 ? 0 : f.size());
            submissionFiles.save(sf);
        }
        events.publishEvent(new DomainEvents.HomeworkSubmitted(tenantId, studentId, a.getId(), a.getTitle(), late));
        events.publishEvent(new DomainEvents.StudentMetricsDirty(tenantId, studentId));
        return toSubmissionView(s, a);
    }

    private static String cleanName(String name, String key) {
        String n = name == null || name.isBlank() ? key.substring(key.lastIndexOf('/') + 1).replaceFirst("^[0-9a-f-]{36}_", "") : name.trim();
        return n.length() > 160 ? n.substring(0, 160) : n;
    }

    // ================= Grading =================

    /** Days late rounded up (Canvas-style): 1 second after the deadline is one day late. */
    static long daysLate(Assignment a, Submission s) {
        if (a.getDeadline() == null || s.getSubmittedAt() == null || !s.getSubmittedAt().isAfter(a.getDeadline())) return 0;
        long seconds = Duration.between(a.getDeadline(), s.getSubmittedAt()).getSeconds();
        return (seconds + 86399) / 86400;
    }

    @Transactional
    public SubmissionView grade(Long submissionId, GradeRequest req, UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        Submission s = submissions.findById(submissionId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> NotFoundException.of("التسليم", submissionId));
        Assignment a = assignments.findByTenantIdAndId(tenantId, s.getAssignmentId()).orElseThrow();
        learning.access(actor, a.getCourseId(), true);
        if ("MISSING".equals(s.getStatus()) || "RETURNED".equals(s.getStatus()))
            throw new BadRequestException("انتظر تسليم الطالب قبل التصحيح");
        if (req.feedback() != null && req.feedback().length() > 5000) throw new BadRequestException("الملاحظة أطول من الحد المسموح");

        List<RubricCriterion> rubric = parseRubric(a.getRubric());
        double raw;
        Map<String, Double> rubricScores = null;
        if (rubric != null && req.rubricScores() != null && !req.rubricScores().isEmpty()) {
            rubricScores = new LinkedHashMap<>();
            raw = 0;
            for (RubricCriterion c : rubric) {
                Double p = req.rubricScores().get(c.id());
                if (p == null || !Double.isFinite(p) || p < 0 || p > c.maxPoints())
                    throw new BadRequestException("درجة المعيار «" + c.title() + "» يجب أن تكون بين صفر و" + trim(c.maxPoints()));
                rubricScores.put(c.id(), p);
                raw += p;
            }
        } else {
            if (req.score() == null || !Double.isFinite(req.score()) || req.score() < 0 || req.score() > a.getMaxScore())
                throw new BadRequestException("الدرجة يجب أن تكون بين صفر والدرجة العظمى");
            raw = req.score();
        }
        double penalty = 0;
        if (!Boolean.TRUE.equals(req.waivePenalty()) && a.getLatePenaltyPercent() > 0) {
            penalty = Math.min(100, a.getLatePenaltyPercent() * daysLate(a, s));
        }
        double finalScore = round1(raw - raw * penalty / 100);
        s.setRawScore(round1(raw));
        s.setPenaltyPercent(penalty);
        s.setRubricScores(rubricScores == null ? null : Json.write(rubricScores));
        s.setScore(finalScore);
        s.setFeedback(req.feedback());
        s.setStatus("GRADED");
        s.setGradedBy(actor.getId());
        s.setGradedAt(Instant.now());
        submissions.save(s);
        // ScoreRecorded drives the gradebook + metrics + risk chain (ScoreListener), in that order.
        events.publishEvent(new DomainEvents.ScoreRecorded(tenantId, s.getStudentId(), a.getCourseId(),
                a.getTitle(), "HOMEWORK", finalScore, a.getMaxScore(), "HOMEWORK", a.getId()));
        return toSubmissionView(s, a);
    }

    /** Flags enrolled students who never submitted as MISSING (§14), driving risk & notifications. */
    @Transactional
    public int markMissing(UserPrincipal actor, Long assignmentId) {
        Long tenantId = TenantContext.require();
        Assignment a = assignments.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> NotFoundException.of("الواجب", assignmentId));
        learning.access(actor, a.getCourseId(), true);
        if (a.getDeadline() == null || !Instant.now().isAfter(a.getDeadline()))
            throw new BadRequestException("رصد غير المسلّمين متاح بعد الموعد النهائي فقط");
        int count = 0;
        for (var enr : enrollments.findByTenantIdAndCourseId(tenantId, a.getCourseId())) {
            if (!ACTIVE.contains(enr.getStatus())) continue;
            Long studentId = enr.getStudentId();
            if (submissions.findByTenantIdAndAssignmentIdAndStudentId(tenantId, assignmentId, studentId).isEmpty()) {
                Submission s = new Submission();
                s.setTenantId(tenantId);
                s.setAssignmentId(assignmentId);
                s.setStudentId(studentId);
                s.setStatus("MISSING");
                submissions.save(s);
                long missedCount = submissions.countByTenantIdAndStudentIdAndStatus(tenantId, studentId, "MISSING");
                events.publishEvent(new DomainEvents.HomeworkMissed(tenantId, studentId, assignmentId, a.getTitle(), (int) missedCount));
                events.publishEvent(new DomainEvents.StudentMetricsDirty(tenantId, studentId));
                count++;
            }
        }
        return count;
    }

    @Transactional
    public SubmissionView returnForRevision(UserPrincipal actor, Long id, String feedback) {
        Long tenantId = TenantContext.require();
        Submission s = submissions.findById(id).filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("التسليم", id));
        Assignment a = assignments.findByTenantIdAndId(tenantId, s.getAssignmentId()).orElseThrow();
        learning.access(actor, a.getCourseId(), true);
        if (!Set.of("SUBMITTED", "LATE", "RETURNED").contains(s.getStatus()))
            throw new BadRequestException("الإعادة للتعديل متاحة قبل اعتماد الدرجة فقط");
        if (feedback == null || feedback.isBlank())
            throw new BadRequestException("وضّح للطالب التعديلات المطلوبة");
        s.setFeedback(feedback); s.setStatus("RETURNED"); s.setReturnedAt(Instant.now());
        return toSubmissionView(submissions.save(s), a);
    }

    // ================= Comment bank =================

    public List<CommentView> myComments(UserPrincipal actor) {
        return comments.findByTenantIdAndTeacherIdOrderByUsesDescCreatedAtDesc(TenantContext.require(), actor.getId()).stream()
                .map(c -> new CommentView(c.getId(), c.getText(), c.getUses())).toList();
    }

    @Transactional
    public CommentView addComment(UserPrincipal actor, String text) {
        Long tenantId = TenantContext.require();
        if (text == null || text.isBlank() || text.length() > 1000) throw new BadRequestException("اكتب تعليقاً حتى 1000 حرف");
        if (comments.countByTenantIdAndTeacherId(tenantId, actor.getId()) >= 200) throw new BadRequestException("وصلت للحد الأقصى من التعليقات المحفوظة");
        GradingComment c = new GradingComment();
        c.setTenantId(tenantId); c.setTeacherId(actor.getId()); c.setText(text.trim());
        comments.save(c);
        return new CommentView(c.getId(), c.getText(), c.getUses());
    }

    @Transactional
    public CommentView useComment(UserPrincipal actor, Long id) {
        GradingComment c = comments.findByTenantIdAndIdAndTeacherId(TenantContext.require(), id, actor.getId())
                .orElseThrow(() -> NotFoundException.of("التعليق", id));
        c.setUses(c.getUses() + 1);
        comments.save(c);
        return new CommentView(c.getId(), c.getText(), c.getUses());
    }

    @Transactional
    public void deleteComment(UserPrincipal actor, Long id) {
        GradingComment c = comments.findByTenantIdAndIdAndTeacherId(TenantContext.require(), id, actor.getId())
                .orElseThrow(() -> NotFoundException.of("التعليق", id));
        comments.delete(c);
    }

    // ================= Views =================

    private List<AttachmentView> files(Submission s) {
        return submissionFiles.findByTenantIdAndSubmissionIdOrderByUploadedAt(s.getTenantId(), s.getId()).stream()
                .map(f -> new AttachmentView(f.getFileKey(), f.getName(), f.getSizeBytes())).toList();
    }

    private AssignmentView toView(Assignment a) {
        Long tenantId = a.getTenantId();
        var subs = submissions.findByTenantIdAndAssignmentId(tenantId, a.getId());
        long submitted = subs.stream().filter(s -> Set.of("SUBMITTED", "LATE", "GRADED").contains(s.getStatus())).count();
        long graded = subs.stream().filter(s -> "GRADED".equals(s.getStatus())).count();
        long missing = subs.stream().filter(s -> "MISSING".equals(s.getStatus())).count();
        long returned = subs.stream().filter(s -> "RETURNED".equals(s.getStatus())).count();
        long enrolled = enrollments.findByTenantIdAndCourseId(tenantId, a.getCourseId()).stream().filter(e -> ACTIVE.contains(e.getStatus())).count();
        var gradedScores = subs.stream().filter(s -> "GRADED".equals(s.getStatus()) && s.getScore() != null).mapToDouble(Submission::getScore);
        Double avgPercent = graded == 0 || a.getMaxScore() <= 0 ? null : round1(gradedScores.average().orElse(0) / a.getMaxScore() * 100);
        String courseTitle = courses.findByTenantIdAndId(tenantId, a.getCourseId()).map(c -> c.getTitle()).orElse("—");
        return new AssignmentView(a.getId(), a.getCourseId(), courseTitle, a.getTitle(), a.getDescription(),
                a.getFileKey(), a.getStartAt(), a.getDeadline(), a.getMaxScore(), submitted, graded, missing, returned, enrolled,
                a.isAllowLate(), a.getLatePenaltyPercent(), parseRubric(a.getRubric()), avgPercent, a.getCreatedAt(), a.getUpdatedAt());
    }

    private SubmissionView toSubmissionView(Submission s, Assignment a) {
        String name = students.findByTenantIdAndId(s.getTenantId(), s.getStudentId()).map(x -> x.getFullName()).orElse("—");
        Long lateSeconds = a.getDeadline() != null && s.getSubmittedAt() != null && s.getSubmittedAt().isAfter(a.getDeadline())
                ? Duration.between(a.getDeadline(), s.getSubmittedAt()).getSeconds() : null;
        return new SubmissionView(s.getId(), s.getAssignmentId(), s.getStudentId(), name, s.getText(),
                s.getFileKey(), s.getStatus(), s.getSubmittedAt(), s.getScore(), s.getFeedback(),
                s.getId() == null ? List.of() : files(s), s.getRawScore(), s.getPenaltyPercent(), parseScores(s.getRubricScores()),
                s.getResubmissions(), s.getGradedAt(), s.getReturnedAt(), lateSeconds);
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private static void requireFileKey(String key, Long tenantId, String folder) {
        if (key == null || key.isBlank()) return;
        String prefix = "t" + tenantId + "/" + folder + "/";
        if (!key.startsWith(prefix) || key.contains("..")) {
            throw new BadRequestException("ملف غير صالح");
        }
    }
}
