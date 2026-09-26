package com.manarah.center;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The center's own dashboard: settings, teachers, groups, students and cards, scanning, attendance, accounts, books. */
@RestController
@RequestMapping("/api/center")
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class CenterController {
    private final CenterService center;
    private final CenterAttendanceService attendance;
    private final CenterAccountsService accounts;
    private final CenterBookService books;

    public CenterController(CenterService center, CenterAttendanceService attendance, CenterAccountsService accounts, CenterBookService books) {
        this.center = center; this.attendance = attendance; this.accounts = accounts; this.books = books;
    }

    public record ScanBody(String code) {}
    public record PaidBody(Boolean paid) {}
    public record DeliverBody(String code) {}

    @GetMapping("/me")
    public CenterService.CenterView me(@AuthenticationPrincipal UserPrincipal actor) { return center.me(actor); }

    @PutMapping("/me")
    public CenterService.CenterView saveMe(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CenterService.CenterInput body) {
        return center.saveMe(actor, body);
    }

    @GetMapping("/overview")
    public CenterService.Overview overview(@AuthenticationPrincipal UserPrincipal actor) { return center.overview(actor); }

    // ---- Teachers and groups ----

    @GetMapping("/teachers")
    public List<CenterService.TeacherView> teachers(@AuthenticationPrincipal UserPrincipal actor) { return center.teachers(actor); }

    @PostMapping("/teachers")
    public CenterService.TeacherView addTeacher(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CenterService.TeacherInput body) {
        return center.saveTeacher(actor, null, body);
    }

    @PutMapping("/teachers/{id}")
    public CenterService.TeacherView saveTeacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                 @RequestBody CenterService.TeacherInput body) {
        return center.saveTeacher(actor, id, body);
    }

    @DeleteMapping("/teachers/{id}")
    public Map<String, Object> deleteTeacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        center.deleteTeacher(actor, id);
        return Map.of("id", id, "deleted", true);
    }

    @GetMapping("/groups")
    public List<CenterService.GroupView> groups(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) Long teacherId) {
        return center.groups(actor, teacherId);
    }

    @PostMapping("/groups")
    public CenterService.GroupView addGroup(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CenterService.GroupInput body) {
        return center.saveGroup(actor, null, body);
    }

    @PutMapping("/groups/{id}")
    public CenterService.GroupView saveGroup(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                             @RequestBody CenterService.GroupInput body) {
        return center.saveGroup(actor, id, body);
    }

    @DeleteMapping("/groups/{id}")
    public Map<String, Object> deleteGroup(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        center.deleteGroup(actor, id);
        return Map.of("id", id, "deleted", true);
    }

    // ---- Students and cards ----

    @GetMapping("/students")
    public List<CenterService.StudentView> students(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) Long groupId,
                                                    @RequestParam(required = false) Long teacherId, @RequestParam(required = false) String q) {
        return center.students(actor, groupId, teacherId, q);
    }

    @PostMapping("/students")
    public List<CenterService.StudentView> addStudents(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CenterService.BulkInput body) {
        return center.addStudents(actor, body);
    }

    @GetMapping("/students/{id}")
    public CenterAttendanceService.History student(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return attendance.history(actor, id);
    }

    @PutMapping("/students/{id}")
    public CenterService.StudentView saveStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                 @RequestBody CenterService.StudentUpdate body) {
        return center.updateStudent(actor, id, body);
    }

    @PostMapping("/students/{id}/new-card")
    public CenterService.StudentView newCard(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return center.newCard(actor, id);
    }

    @DeleteMapping("/students/{id}")
    public Map<String, Object> deleteStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        center.deleteStudent(actor, id);
        return Map.of("id", id, "deleted", true);
    }

    // ---- Scanning and attendance ----

    @PostMapping("/scan")
    public CenterAttendanceService.ScanResult scan(@AuthenticationPrincipal UserPrincipal actor, @RequestBody ScanBody body) {
        return attendance.scan(actor, body == null ? null : body.code());
    }

    @GetMapping("/attendance")
    public CenterAttendanceService.Sheet sheet(@AuthenticationPrincipal UserPrincipal actor, @RequestParam Long groupId,
                                               @RequestParam(required = false) String date) {
        return attendance.sheet(actor, groupId, date);
    }

    @PostMapping("/attendance")
    public CenterAttendanceService.ScanResult markByHand(@AuthenticationPrincipal UserPrincipal actor,
                                                         @RequestBody CenterAttendanceService.ManualMark body) {
        return attendance.markByHand(actor, body);
    }

    @PutMapping("/attendance/{id}/paid")
    public CenterAttendanceService.SheetRow paid(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody PaidBody body) {
        return attendance.setPaid(actor, id, body != null && Boolean.TRUE.equals(body.paid()));
    }

    @DeleteMapping("/attendance/{id}")
    public Map<String, Object> unmark(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        attendance.remove(actor, id);
        return Map.of("id", id, "deleted", true);
    }

    @GetMapping("/accounts")
    public CenterAccountsService.Accounts accounts(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to) {
        return accounts.accounts(actor, from, to);
    }

    // ---- Books ----

    @GetMapping("/books")
    public List<CenterBookService.BookView> books(@AuthenticationPrincipal UserPrincipal actor) { return books.books(actor); }

    @PostMapping("/books")
    public CenterBookService.BookView addBook(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CenterBookService.BookInput body) {
        return books.save(actor, null, body);
    }

    @PutMapping("/books/{id}")
    public CenterBookService.BookView saveBook(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                               @RequestBody CenterBookService.BookInput body) {
        return books.save(actor, id, body);
    }

    @DeleteMapping("/books/{id}")
    public Map<String, Object> deleteBook(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        books.delete(actor, id);
        return Map.of("id", id, "deleted", true);
    }

    @PostMapping("/books/{id}/reservations")
    public CenterBookService.ReservationView reserve(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                     @RequestBody CenterBookService.ReserveForStudent body) {
        return books.reserveAtDesk(actor, id, body);
    }

    @GetMapping("/reservations")
    public List<CenterBookService.ReservationView> reservations(@AuthenticationPrincipal UserPrincipal actor,
                                                                @RequestParam(required = false) Long bookId,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String q) {
        return books.reservations(actor, bookId, status, q);
    }

    @PostMapping("/reservations/deliver")
    public CenterBookService.ReservationView deliver(@AuthenticationPrincipal UserPrincipal actor, @RequestBody DeliverBody body) {
        return books.deliver(actor, body == null ? null : body.code());
    }

    @PostMapping("/reservations/{id}/deliver")
    public CenterBookService.ReservationView deliverById(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return books.deliverById(actor, id);
    }

    @PostMapping("/reservations/{id}/cancel")
    public CenterBookService.ReservationView cancel(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return books.cancel(actor, id);
    }
}
