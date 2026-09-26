package com.manarah.center;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Where a center student's QR lands. Public on purpose (the {@code /api/public/**} rule): the random token on the
 * card is the key, and PublicEndpointRateLimitFilter throttles guessing. Scanning for attendance is a separate,
 * signed-in call ({@code POST /api/center/scan}).
 */
@RestController
@RequestMapping("/api/public/center-pass")
@Tag(name = "Center student card")
public class CenterPassController {
    private final CenterPassService passes;

    public CenterPassController(CenterPassService passes) { this.passes = passes; }

    @GetMapping("/{token}")
    public ResponseEntity<CenterPassService.Pass> pass(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow").body(passes.pass(token));
    }

    @PostMapping("/{token}/books/{bookId}/reserve")
    public CenterPassService.PassReservation reserve(@PathVariable String token, @PathVariable Long bookId) {
        return passes.reserve(token, bookId);
    }

    @PostMapping("/{token}/reservations/{code}/cancel")
    public Map<String, Object> cancel(@PathVariable String token, @PathVariable String code) {
        passes.cancel(token, code);
        return Map.of("code", code, "status", CenterBookReservation.CANCELLED);
    }
}
