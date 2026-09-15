package com.manarah.security.auth;

import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import com.manarah.security.FileSessionCookie;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordReset;
    private final FileSessionCookie fileSessionCookie;

    public AuthController(AuthService authService, PasswordResetService passwordReset, FileSessionCookie fileSessionCookie) {
        this.authService = authService;
        this.passwordReset = passwordReset;
        this.fileSessionCookie = fileSessionCookie;
    }

    @PostMapping("/login")
    @Operation(summary = "تسجيل الدخول والحصول على رمز الوصول")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        var result = authService.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, fileSessionCookie.issue(result.accessToken())).body(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, fileSessionCookie.clear()).build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "طلب رابط إعادة تعيين كلمة المرور")
    public PasswordResetService.GenericResult forgot(@RequestBody PasswordResetService.ForgotRequest request) {
        return passwordReset.request(request);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "تعيين كلمة مرور جديدة برابط الاستعادة")
    public PasswordResetService.GenericResult reset(@RequestBody PasswordResetService.ResetRequest request) {
        return passwordReset.reset(request);
    }

    @GetMapping("/me")
    @Operation(summary = "بيانات المستخدم الحالي")
    public AuthDtos.UserProfile me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal);
    }
}
