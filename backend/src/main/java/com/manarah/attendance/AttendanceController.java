package com.manarah.attendance;

import com.manarah.attendance.AttendanceDtos.*;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService service;
    private final StudentService studentService;

    public AttendanceController(AttendanceService service, StudentService studentService) {
        this.service = service;
        this.studentService = studentService;
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal UserPrincipal actor) {
        return service.list(actor);
    }

    @GetMapping("/sessions/course/{courseId}")
    public List<SessionView> byCourse(@PathVariable Long courseId) {
        return service.byCourse(courseId);
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public SessionView create(@Valid @RequestBody CreateSessionRequest req) {
        return service.createSession(req);
    }

    @GetMapping("/sessions/{id}/roster")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public List<RosterRow> roster(@PathVariable Long id) {
        return service.roster(id);
    }

    @PostMapping("/sessions/{id}/mark")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public void mark(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @Valid @RequestBody MarkRequest req) {
        service.mark(actor, id, req);
    }

    @PostMapping("/sessions/{id}/qr")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public QrToken rotateQr(@PathVariable Long id) {
        return service.rotateQr(id);
    }

    /** A STUDENT caller always checks themselves in — the body's studentId is never trusted for
     *  them, so one student cannot mark another present. Staff may check a student in manually
     *  (e.g. a phone-less student reading the code aloud) by supplying studentId explicitly. */
    @PostMapping("/check-in")
    public RosterRow checkIn(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody QrCheckInRequest req) {
        Long studentId;
        if (actor.getRole() == Role.STUDENT) {
            studentId = requireOwnStudentId(actor);
        } else if (req.studentId() != null) {
            studentId = req.studentId();
        } else {
            throw new BadRequestException("لم يتم تحديد الطالب");
        }
        return service.checkInByQr(studentId, req.token());
    }

    /** The logged-in student's own attendance history. */
    @GetMapping("/my")
    public List<MyAttendanceRow> myAttendance(@AuthenticationPrincipal UserPrincipal actor) {
        return service.myAttendance(requireOwnStudentId(actor));
    }

    /** The logged-in student's enrolled courses with sessions — pick a course, then check in. */
    @GetMapping("/my-courses")
    public List<MyCourseSessions> myCourseSessions(@AuthenticationPrincipal UserPrincipal actor) {
        return service.myCourseSessions(requireOwnStudentId(actor));
    }

    private Long requireOwnStudentId(UserPrincipal actor) {
        Long id = studentService.myStudentId(actor);
        if (id == null) {
            throw new BadRequestException("لا يوجد ملف طالب مرتبط بحسابك");
        }
        return id;
    }
}
