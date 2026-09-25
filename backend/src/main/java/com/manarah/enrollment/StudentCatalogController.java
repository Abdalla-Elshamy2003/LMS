package com.manarah.enrollment;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The student's own course list with their current teacher — see {@link StudentCatalogService}. */
@RestController
@RequestMapping("/api/me/catalog")
public class StudentCatalogController {
    private final StudentCatalogService catalog;

    public StudentCatalogController(StudentCatalogService catalog) { this.catalog = catalog; }

    public record GradeBody(String grade) {}

    @GetMapping @PreAuthorize("hasRole('STUDENT')")
    public StudentCatalogService.Catalog mine(@AuthenticationPrincipal UserPrincipal actor) {
        return catalog.forStudent(actor);
    }

    @PostMapping("/{courseId}/request") @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> request(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long courseId) {
        return Map.of("courseId", courseId, "state", catalog.request(actor, courseId));
    }

    @PutMapping("/grade") @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> grade(@AuthenticationPrincipal UserPrincipal actor, @RequestBody GradeBody body) {
        catalog.setGrade(actor, body.grade());
        return Map.of("grade", body.grade().trim());
    }
}
