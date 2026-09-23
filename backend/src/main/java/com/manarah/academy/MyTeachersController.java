package com.manarah.academy;

import com.manarah.security.FileSessionCookie;
import com.manarah.security.JwtService;
import com.manarah.security.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** A student's teachers: the "مدرسيني" switcher, moving between them, and joining a new one with the same account. */
@RestController @RequestMapping("/api/me/teachers")
public class MyTeachersController {
    private final LinkedStudentAccounts accounts;
    private final FileSessionCookie fileSessionCookie;
    private final JwtService jwt;

    public MyTeachersController(LinkedStudentAccounts accounts, FileSessionCookie fileSessionCookie, JwtService jwt) {
        this.accounts = accounts; this.fileSessionCookie = fileSessionCookie; this.jwt = jwt;
    }

    public record SwitchBody(Long userId) {}
    public record JoinBody(String slug, Long courseId) {}

    @GetMapping
    public List<LinkedStudentAccounts.Membership> list(@AuthenticationPrincipal UserPrincipal actor) {
        return accounts.memberships(actor);
    }

    @PostMapping("/switch") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> switchTo(@AuthenticationPrincipal UserPrincipal actor, @RequestBody SwitchBody body) {
        return session(accounts.switchTo(actor, body.userId()));
    }

    @PostMapping("/join") @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> join(@AuthenticationPrincipal UserPrincipal actor, @RequestBody JoinBody body) {
        return session(accounts.join(actor, body.slug(), body.courseId()));
    }

    /** The new session goes to the browser twice: as the bearer token, and as the cookie videos and files are fetched with. */
    private ResponseEntity<Map<String, Object>> session(LinkedStudentAccounts.Session s) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, fileSessionCookie.issue(s.accessToken()))
                .body(Map.of("accessToken", s.accessToken(), "tokenType", "Bearer", "expiresInMinutes", jwt.getAccessTokenTtlMinutes()));
    }
}
