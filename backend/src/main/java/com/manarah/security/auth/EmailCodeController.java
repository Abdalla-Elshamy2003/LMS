package com.manarah.security.auth;

import com.manarah.security.FileSessionCookie;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth/email-code")
public class EmailCodeController {
    private final EmailCodeService codes;
    private final AuthService auth;
    private final FileSessionCookie cookies;
    public EmailCodeController(EmailCodeService codes, AuthService auth, FileSessionCookie cookies) {
        this.codes = codes; this.auth = auth; this.cookies = cookies;
    }
    public record RequestCode(String email, String purpose) {}
    public record VerifyCode(String email, String purpose, String challengeId, String code) {}
    @GetMapping("/status")
    public Object status() { return Map.of("available", codes.available(), "registrationVerificationRequired", codes.registrationVerificationRequired()); }
    @PostMapping("/request")
    public Object request(@RequestBody RequestCode body) { return codes.request(body.email(), body.purpose()); }
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyCode body) {
        var verified = codes.verify(body.challengeId(), body.email(), body.purpose(), body.code());
        if ("REGISTER".equals(body.purpose())) return ResponseEntity.ok(Map.of("verificationToken", verified.verificationToken()));
        var session = auth.emailLogin(verified.userId(), EmailCodeService.normalize(body.email()));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(session.accessToken())).body(session);
    }
}
