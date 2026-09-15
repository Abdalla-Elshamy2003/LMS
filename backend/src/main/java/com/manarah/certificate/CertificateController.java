package com.manarah.certificate;

import com.manarah.certificate.CertificateService.CertificateView;
import com.manarah.certificate.CertificateService.VerifyResult;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentAccessPolicy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Certificates")
public class CertificateController {

    private final CertificateService service;
    private final StudentAccessPolicy accessPolicy;

    public CertificateController(CertificateService service, StudentAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    public record IssueRequest(@NotNull Long studentId, Long courseId, String grade) {
    }

    @PostMapping("/api/certificates/issue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public CertificateView issue(@RequestBody IssueRequest req) {
        return service.issue(req.studentId(), req.courseId(), req.grade());
    }

    @GetMapping("/api/certificates/student/{studentId}")
    public List<CertificateView> forStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        accessPolicy.assertCanView(actor, studentId);
        return service.forStudent(studentId);
    }

    /** Public verification endpoint — what a QR-code scan on a printed/PDF certificate resolves to. */
    @GetMapping("/api/public/verify-certificate/{code}")
    public VerifyResult verify(@PathVariable String code) {
        return service.verify(code);
    }
}
