package com.manarah.attendance;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class AttendanceDtos {

    public record SessionView(Long id, Long courseId, String courseTitle, String subject,
                              Long teacherId, String teacherName, Long roomId,
                              String title, Instant scheduledStart, Instant scheduledEnd, String status,
                              int present, int absent, int late, int total) {
    }

    public record CreateSessionRequest(@NotNull Long courseId, Long groupId, Long teacherId, Long roomId,
                                       String title, Instant scheduledStart, Instant scheduledEnd) {
    }

    public record RosterRow(Long studentId, String studentName, String code, String status,
                            int lateMinutes, String reason) {
    }

    public record MarkRow(@NotNull Long studentId, @NotNull String status, Integer lateMinutes, String reason) {
    }

    public record MarkRequest(@NotNull List<MarkRow> rows) {
    }

    public record QrToken(String token, Instant expiresAt, int ttlSeconds) {
    }

    /** studentId is only honoured for staff callers checking a student in manually; a STUDENT
     *  caller always checks themselves in regardless of this field — see AttendanceController. */
    public record QrCheckInRequest(@NotNull String token, Long studentId) {
    }

    /** A student's own attendance history row — what they see of themselves, nothing about classmates. */
    public record MyAttendanceRow(Long sessionId, String courseTitle, String sessionTitle,
                                  Instant scheduledStart, String status, int lateMinutes, String method) {
    }

    /** One of the student's enrolled courses with its sessions, for the self-service check-in view. */
    public record MyCourseSessions(Long courseId, String courseTitle, List<SessionView> sessions) {
    }
}
