package com.manarah.course;

import com.manarah.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final LearningService service;
    private final VideoWatchService watch;
    public LearningController(LearningService service, VideoWatchService watch) { this.service = service; this.watch = watch; }
    /** Who watched this course's protected videos, from where and on how many devices. */
    @GetMapping("/courses/{id}/watch-log")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public List<VideoWatchService.WatchLogRow> watchLog(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return watch.watchLog(actor, id); }
    @GetMapping
    public List<LearningService.LearningCourse> library(@AuthenticationPrincipal UserPrincipal actor) { return service.library(actor); }
    @GetMapping("/courses/{id}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public List<LearningService.ProgressView> progress(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.ownProgress(actor, id); }
    public record ProgressRequest(@Min(0) @Max(86400) int position, boolean completed) {}
    @PutMapping("/lessons/{id}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public LearningService.ProgressView save(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @Valid @RequestBody ProgressRequest req) {
        return service.save(actor, id, req.position(), req.completed());
    }
    @GetMapping("/courses/{id}/learners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public List<LearningService.LearnerView> learners(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.learners(actor, id); }
}
