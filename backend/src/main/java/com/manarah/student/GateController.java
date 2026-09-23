package com.manarah.student;

import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Student entry/exit passes and the door log. See {@link GateService}. */
@RestController
@RequestMapping("/api/gate")
@Tag(name = "Gate pass")
public class GateController {

    private final GateService service;

    public GateController(GateService service) {
        this.service = service;
    }

    /** The signed-in student's own pass, for the QR on their profile. */
    @GetMapping("/my-pass")
    public GateService.PassView myPass(@AuthenticationPrincipal UserPrincipal actor) {
        return service.myPass(actor);
    }

    @GetMapping("/students/{studentId}/pass")
    public GateService.PassView studentPass(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        return service.passFor(actor, studentId);
    }

    /** Where a scanned pass lands — staff only; the direction is worked out server-side. */
    @PostMapping("/scan/{token}")
    public GateService.ScanResult scan(@AuthenticationPrincipal UserPrincipal actor, @PathVariable String token,
                                       @RequestParam(required = false) Long academyId) {
        return service.scan(actor, token, academyId);
    }

    /**
     * What the card-scanner page posts to. Identical to {@code /scan/{token}} except the code
     * travels in the body — a card reader can emit characters that are awkward in a path segment,
     * and an RFID UID is not something we chose the shape of.
     */
    @PostMapping("/scan")
    public GateService.ScanResult scanCard(@AuthenticationPrincipal UserPrincipal actor,
                                           @RequestBody ScanBody body) {
        return service.scan(actor, body.code(), body.academyId());
    }

    /** {@code academyId} is only sent back when a scan asked which teacher to record the attendance with. */
    public record ScanBody(String code, Long academyId) {}

    public record CardBody(String cardUid) {}

    /** Binds a physical RFID/NFC card to a student — staff tap a blank card and its UID is stored. */
    @PostMapping("/students/{studentId}/card")
    public GateService.PassView bindCard(@AuthenticationPrincipal UserPrincipal actor,
                                         @PathVariable Long studentId, @RequestBody CardBody body) {
        return service.bindCard(actor, studentId, body.cardUid());
    }

    /** Releases a lost or damaged card so a replacement can be issued. */
    @DeleteMapping("/students/{studentId}/card")
    public void unbindCard(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        service.unbindCard(actor, studentId);
    }

    @GetMapping("/log")
    public List<GateService.LogRow> day(@AuthenticationPrincipal UserPrincipal actor,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(actor, date);
    }

    @GetMapping("/students/{studentId}/log")
    public List<GateService.LogRow> forStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        return service.forStudent(actor, studentId);
    }
}
