package com.manarah.course;

import com.manarah.course.CourseDtos.*;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses")
public class CourseController {

    private final CourseService service;
    private final LearningService learning;
    private final LessonCheckpointService checkpointService;
    private final LessonSummaryService summaryService;
    private final com.manarah.payment.CourseAccessCodeService accessCodeService;

    public CourseController(CourseService service, LearningService learning, LessonCheckpointService checkpointService,
                            LessonSummaryService summaryService, com.manarah.payment.CourseAccessCodeService accessCodeService) {
        this.service = service;
        this.learning = learning;
        this.checkpointService = checkpointService;
        this.summaryService = summaryService;
        this.accessCodeService = accessCodeService;
    }

    /** Generates (and stores) an AI recap of a lesson; the teacher may pass their own notes or a transcript. */
    @PostMapping("/lessons/{lessonId}/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public LessonSummaryService.SummaryView summarize(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long lessonId,
                                                      @RequestBody(required = false) LessonSummaryService.SummaryRequest req) {
        return summaryService.summarize(actor, lessonId, req);
    }

    @GetMapping
    public List<CourseSummary> list(@AuthenticationPrincipal UserPrincipal actor,
                                    @RequestParam(required = false) String gradeLevel) {
        // A teacher only ever manages their own courses, so their lists, dropdowns and filters start
        // there instead of the whole school's catalogue. Other staff already see everything their
        // tenant scope covers, and that scope now spans the academies they manage — running those
        // through the per-course check would reject them, since it resolves a course strictly within
        // the caller's own tenant. Everyone else (students, parents) still gets filtered down to what
        // they're actually enrolled in.
        List<CourseSummary> visible;
        if (actor.getRole() == com.manarah.identity.domain.Role.TEACHER) visible = service.forTeacher(actor.getId());
        else if (actor.isStaff()) visible = service.list();
        else visible = service.list().stream().filter(c -> {
            try { learning.access(actor, c.id(), false); return true; }
            catch (com.manarah.common.exception.ApiExceptions.ApiException e) { return false; }
        }).toList();
        if (gradeLevel == null || gradeLevel.isBlank()) return visible;
        return visible.stream().filter(c -> gradeLevel.equals(c.gradeLevel()) || gradeLevel.equals(c.grade())).toList();
    }

    @GetMapping("/{id}")
    public CourseDetail get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        learning.access(actor, id, false);
        return service.get(id, actor.isStaff());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public CourseDetail create(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateCourseRequest req) {
        return service.create(actor, req);
    }

    @PostMapping("/{id}/modules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public ModuleView addModule(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @Valid @RequestBody CreateModuleRequest req) {
        learning.access(actor, id, true);
        return service.addModule(id, req);
    }

    @PostMapping("/modules/{moduleId}/lessons")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public LessonView addLesson(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long moduleId, @Valid @RequestBody CreateLessonRequest req) {
        learning.access(actor, learning.moduleCourse(actor, moduleId), true);
        return service.addLesson(moduleId, req);
    }

    @PostMapping("/lessons/{lessonId}/materials")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public MaterialView addMaterial(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long lessonId, @Valid @RequestBody CreateMaterialRequest req) {
        learning.access(actor, learning.lessonCourse(actor, lessonId), true);
        return service.addMaterial(lessonId, req);
    }

    // ---- In-video checkpoint questions (the video pauses and asks; no timestamp = end-of-lesson test) ----

    @GetMapping("/lessons/{lessonId}/checkpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public List<LessonCheckpointService.CheckpointView> checkpoints(@AuthenticationPrincipal UserPrincipal actor,
                                                                    @PathVariable Long lessonId) {
        return checkpointService.list(actor, lessonId);
    }

    @PostMapping("/lessons/{lessonId}/checkpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public LessonCheckpointService.CheckpointView addCheckpoint(@AuthenticationPrincipal UserPrincipal actor,
                                                                @PathVariable Long lessonId,
                                                                @RequestBody LessonCheckpointService.CreateCheckpointRequest req) {
        return checkpointService.add(actor, lessonId, req);
    }

    @DeleteMapping("/checkpoints/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public void removeCheckpoint(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        checkpointService.remove(actor, id);
    }

    /** What the lesson player asks the student, without any answer key. */
    @GetMapping("/lessons/{lessonId}/quiz")
    public List<LessonCheckpointService.CheckpointPrompt> quiz(@AuthenticationPrincipal UserPrincipal actor,
                                                               @PathVariable Long lessonId) {
        return checkpointService.forStudent(actor, lessonId);
    }

    @PostMapping("/checkpoints/{id}/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public LessonCheckpointService.AnswerResult answerCheckpoint(@AuthenticationPrincipal UserPrincipal actor,
                                                                 @PathVariable Long id,
                                                                 @RequestBody LessonCheckpointService.AnswerRequest req) {
        return checkpointService.answer(actor, id, req);
    }

    // ---- Manual-payment access codes (InstaPay / Vodafone Cash confirmed outside the system) ----

    public record GenerateCodesRequest(Integer count) {}

    @PostMapping("/{id}/access-codes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public java.util.List<com.manarah.payment.CourseAccessCodeService.CodeView> generateCodes(
            @AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody GenerateCodesRequest req) {
        return accessCodeService.generate(actor, id, req.count() == null ? 1 : req.count());
    }

    @GetMapping("/{id}/access-codes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public java.util.List<com.manarah.payment.CourseAccessCodeService.CodeView> listCodes(
            @AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return accessCodeService.list(actor, id);
    }

    @DeleteMapping("/access-codes/{codeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public void revokeCode(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long codeId) {
        accessCodeService.revoke(actor, codeId);
    }

    public record RedeemCodeRequest(String code) {}

    /** A student who's already logged in unlocking another course from the same teacher. */
    @PostMapping("/redeem-code")
    @PreAuthorize("hasRole('STUDENT')")
    public void redeemCode(@AuthenticationPrincipal UserPrincipal actor, @RequestBody RedeemCodeRequest req) {
        accessCodeService.redeemForCurrentStudent(actor, req.code());
    }

    /** Sets (or clears, with 0/null) a course's promotional discount — used by the marketing
     *  campaigns page. The checkout price always follows this value, so a displayed discount can
     *  never drift from what a student is actually charged. */
    @PutMapping("/{id}/discount")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public CourseSummary setDiscount(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody SetDiscountRequest req) {
        learning.access(actor, id, true);
        return service.setDiscount(id, req);
    }
}
