package com.manarah.gradebook;

import com.manarah.security.UserPrincipal;
import com.manarah.gradebook.domain.GradeItem;
import com.manarah.student.StudentAccessPolicy;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gradebook")
@Tag(name = "Gradebook")
public class GradebookController {

    private final GradebookService service;
    private final StudentAccessPolicy accessPolicy;

    public GradebookController(GradebookService service, StudentAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/student/{studentId}")
    public Map<String, Object> forStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        accessPolicy.assertCanView(actor, studentId);
        return service.forStudent(studentId);
    }

    /** Hand-entered grade — an oral test, a paper quiz, a participation mark. Staff only: a student
     *  who could reach this could award themselves marks. */
    @PostMapping("/grades")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public GradeItem record(@AuthenticationPrincipal UserPrincipal actor,
                            @RequestBody GradebookService.ManualGrade body) {
        return service.record(actor.getTenantId(), body);
    }

    @DeleteMapping("/grades/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public void remove(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        service.remove(actor.getTenantId(), id);
    }
}
