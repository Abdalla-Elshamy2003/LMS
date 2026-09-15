package com.manarah.exam;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.web.PageResponse;
import com.manarah.exam.ExamDtos.*;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentAccessPolicy;
import com.manarah.student.repo.StudentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
@Tag(name = "Exams")
public class ExamController {

    private static final String STAFF = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')";
    private static final String AUTHORS = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','CONTENT_MANAGER')";
    private static final String GRADERS = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')";

    private final ExamService service;
    private final StudentRepository students;
    private final StudentAccessPolicy accessPolicy;
    private final com.manarah.academy.AcademyAccess courseAccess;
    private final com.manarah.course.LearningService learning;
    private final com.manarah.audit.AuditService audit;
    private final QuestionImportService questionImport;

    public ExamController(ExamService service, StudentRepository students, StudentAccessPolicy accessPolicy,
                          com.manarah.academy.AcademyAccess courseAccess, com.manarah.course.LearningService learning,
                          com.manarah.audit.AuditService audit, QuestionImportService questionImport) {
        this.questionImport = questionImport;
        this.service = service;
        this.students = students;
        this.accessPolicy = accessPolicy; this.courseAccess = courseAccess;
        this.learning = learning;
        this.audit = audit;
    }

    /** A student's own exam list (each tagged with their own attempt status/score) — usable by the
     *  student themselves, or by a parent/staff member authorized to view that student. */
    @GetMapping("/student/{studentId}")
    public List<StudentExamRow> forStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        accessPolicy.assertCanView(actor, studentId);
        return service.forStudent(studentId);
    }

    /** The logged-in student's full catalogue: upcoming, open and closed exams with their own attempt. */
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudentExamCard> my(@AuthenticationPrincipal UserPrincipal actor) {
        return service.catalogue(ownStudentId(actor));
    }

    @GetMapping("/{id}/my-review")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentReview myReview(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return service.studentReview(id, ownStudentId(actor));
    }

    // ---- Question bank ----
    @GetMapping("/questions")
    @PreAuthorize(STAFF)
    public PageResponse<QuestionView> searchQuestions(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.searchQuestions(actor, subject, difficulty, type, q, mine, page, size);
    }

    /** Bulk import from a CSV sheet — see {@link QuestionImportService} for the accepted columns. */
    @PostMapping(value = "/questions/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AUTHORS)
    public QuestionImportService.ImportResult importQuestions(@AuthenticationPrincipal UserPrincipal actor,
                                                              @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return questionImport.importCsv(actor, file);
    }

    @GetMapping("/questions/{id}")
    @PreAuthorize(STAFF)
    public QuestionView getQuestion(@PathVariable Long id) {
        return service.getQuestion(id);
    }

    @PostMapping("/questions")
    @PreAuthorize(AUTHORS)
    public QuestionView createQuestion(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateQuestionRequest req) {
        return service.createQuestion(actor, req);
    }

    @PutMapping("/questions/{id}")
    @PreAuthorize(AUTHORS)
    public QuestionView updateQuestion(@PathVariable Long id, @Valid @RequestBody CreateQuestionRequest req) {
        return service.updateQuestion(id, req);
    }

    @DeleteMapping("/questions/{id}")
    @PreAuthorize(AUTHORS)
    public void deleteQuestion(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        service.deleteQuestion(id);
        audit.record(actor, "QUESTION_DELETED", "Question", id, null, null);
    }

    /** Returns AI-drafted question suggestions for review — nothing is saved until the caller
     *  posts an accepted draft back through {@link #createQuestion}. */
    @PostMapping("/questions/ai-generate")
    @PreAuthorize(AUTHORS)
    public List<CreateQuestionRequest> aiGenerateQuestions(@RequestBody AiGenerateQuestionsRequest req) {
        return service.generateQuestions(req);
    }

    /** Drafts grounded in an uploaded PDF / page photo instead of a free-text topic. */
    @PostMapping(value = "/questions/ai-generate-from-file", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AUTHORS)
    public List<CreateQuestionRequest> aiGenerateFromFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                          @RequestParam(required = false) String subject,
                                                          @RequestParam(required = false) String focus,
                                                          @RequestParam(required = false) String difficulty,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(defaultValue = "5") int count) {
        return service.generateQuestionsFromFile(file, subject, focus, difficulty, type, count);
    }

    // ---- Exam authoring ----
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER','STUDENT')")
    public List<ExamSummary> list(@AuthenticationPrincipal UserPrincipal actor) {
        if (actor.getRole() == Role.STUDENT) return service.availableForStudent(ownStudentId(actor));
        // A course-less exam has no ownership to check against, so only admins see it — otherwise it
        // would sit in every teacher's list and stay editable by all of them.
        return service.list().stream()
                .filter(e -> e.courseId() != null ? courseAccess.visible(e.courseId()) : actor.isAdmin())
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF)
    public ExamDetail get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return authorized(actor, id);
    }

    private ExamDetail authorized(UserPrincipal actor, Long id) {
        ExamDetail exam = service.get(id);
        if (exam.summary().courseId() != null) learning.access(actor, exam.summary().courseId(), true);
        else if (!actor.isAdmin())
            throw new com.manarah.common.exception.ApiExceptions.ForbiddenException("هذا الامتحان غير مرتبط بكورس؛ إدارته من الإدارة فقط");
        return exam;
    }

    @PostMapping
    @PreAuthorize(AUTHORS)
    public ExamDetail create(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateExamRequest req) {
        if (req.courseId() != null) learning.access(actor, req.courseId(), true);
        return service.create(actor, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AUTHORS)
    public ExamDetail update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody UpdateExamRequest req) {
        authorized(actor, id);
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AUTHORS)
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        ExamDetail exam = authorized(actor, id);
        service.delete(id);
        audit.record(actor, "EXAM_DELETED", "Exam", id, exam.summary().title(), null);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(AUTHORS)
    public ExamDetail close(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        authorized(actor, id);
        return service.setStatus(id, "CLOSED");
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(AUTHORS)
    public ExamDetail reopen(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        authorized(actor, id);
        return service.setStatus(id, "PUBLISHED");
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize(AUTHORS)
    public ExamDetail addQuestion(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @Valid @RequestBody AddQuestionRequest req) {
        authorized(actor, id);
        return service.addQuestion(id, req);
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    @PreAuthorize(AUTHORS)
    public ExamDetail removeQuestion(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable Long questionId) {
        authorized(actor, id);
        return service.removeQuestion(id, questionId);
    }

    @PatchMapping("/{id}/questions/{questionId}")
    @PreAuthorize(AUTHORS)
    public ExamDetail setPoints(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable Long questionId,
                                @RequestBody Map<String, Double> body) {
        authorized(actor, id);
        return service.setQuestionPoints(id, questionId, body.get("pointsOverride"));
    }

    @PutMapping("/{id}/questions/order")
    @PreAuthorize(AUTHORS)
    public ExamDetail reorder(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @Valid @RequestBody ReorderRequest req) {
        authorized(actor, id);
        return service.reorder(id, req.questionIds());
    }

    @PostMapping("/auto-generate")
    @PreAuthorize(AUTHORS)
    public ExamDetail autoGenerate(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody AutoGenerateRequest req) {
        if (req.courseId() != null) learning.access(actor, req.courseId(), true);
        return service.autoGenerate(actor, req);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(AUTHORS)
    public void publish(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        authorized(actor, id);
        service.publish(id);
        audit.record(actor, "EXAM_PUBLISHED", "Exam", id, null, null);
    }

    @GetMapping("/{id}/results")
    @PreAuthorize(GRADERS)
    public ExamAnalytics results(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        authorized(actor, id);
        return service.analytics(id);
    }

    // ---- Taking (student) ----
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView start(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                             @RequestParam(required = false) Long studentId) {
        courseAccess.requireCourse(service.get(id).summary().courseId());
        return service.startAttempt(id, ownStudentId(actor));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptResult submit(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long attemptId, @Valid @RequestBody SubmitRequest req) {
        return service.submit(attemptId, ownStudentId(actor), req);
    }

    @PostMapping("/attempts/{attemptId}/grade")
    @PreAuthorize(GRADERS)
    public void manualGrade(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long attemptId, @Valid @RequestBody ManualGradeRequest req) {
        Long courseId = service.courseIdForAttempt(attemptId);
        if (courseId != null) learning.access(actor, courseId, true);
        service.manualGrade(attemptId, req);
    }

    @PutMapping("/attempts/{attemptId}/draft")
    @PreAuthorize("hasRole('STUDENT')")
    public DraftView draft(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long attemptId, @RequestBody SubmitRequest req) {
        return service.saveDraft(attemptId, ownStudentId(actor), req);
    }

    @GetMapping("/attempts/{attemptId}/answers")
    @PreAuthorize(GRADERS)
    public List<ReviewAnswer> answers(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long attemptId) {
        Long courseId = service.courseIdForAttempt(attemptId);
        if (courseId == null) throw new BadRequestException("المحاولة غير مرتبطة بكورس");
        learning.access(actor, courseId, true);
        return service.reviewAnswers(attemptId);
    }

    @GetMapping("/attempts/{attemptId}/integrity")
    @PreAuthorize(GRADERS)
    public IntegrityReport integrity(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long attemptId) {
        Long courseId = service.courseIdForAttempt(attemptId);
        if (courseId == null) throw new BadRequestException("المحاولة غير مرتبطة بكورس");
        learning.access(actor, courseId, true);
        return service.integrity(attemptId);
    }

    private Long ownStudentId(UserPrincipal actor) {
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .map(s -> s.getId())
                .orElseThrow(() -> new BadRequestException("لم يتم تحديد الطالب"));
    }
}
