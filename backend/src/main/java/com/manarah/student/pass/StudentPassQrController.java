package com.manarah.student.pass;

import com.manarah.student.repo.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * A student's pass QR as an image, for the email that sends the pass: Brevo's API can't embed images in a message, so
 * the email points here. It answers only for a pass that exists, and shows nothing the email's own link doesn't —
 * the QR is that same link.
 */
@RestController
@RequestMapping("/api/public/students/pass")
public class StudentPassQrController {
    private final StudentRepository students;
    private final String appUrl;

    public StudentPassQrController(StudentRepository students, @Value("${manarah.public-app-url:}") String appUrl) {
        this.students = students;
        this.appUrl = appUrl == null ? "" : appUrl.trim().replaceAll("/+$", "");
    }

    @GetMapping(value = "/{token}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String token) {
        if (appUrl.isEmpty() || students.findByPassToken(token).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(QrCodeImages.png(appUrl + "/student/verify/" + token, 360));
    }

    /** Where the email finds the image for {@code token}. */
    static String url(String appUrl, String token) {
        return appUrl + "/api/public/students/pass/" + token + "/qr.png";
    }
}
