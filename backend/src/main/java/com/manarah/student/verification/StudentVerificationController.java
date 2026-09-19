package com.manarah.student.verification;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public student verification - where the QR on a student's card or phone lands.
 *
 * <p>Intentionally unauthenticated: it falls under the {@code /api/public/**} permit rule in
 * SecurityConfig and is throttled per IP by PublicEndpointRateLimitFilter. Every private student
 * endpoint ({@code /api/students/**}, {@code /api/gate/**}) stays protected.
 */
@RestController
@RequestMapping("/api/public/students")
@Tag(name = "Student verification")
public class StudentVerificationController {

    private final StudentVerificationService service;

    public StudentVerificationController(StudentVerificationService service) {
        this.service = service;
    }

    @GetMapping("/verify/{token}")
    public ResponseEntity<PublicStudentProfileDto> verify(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(service.verify(token));
    }
}
