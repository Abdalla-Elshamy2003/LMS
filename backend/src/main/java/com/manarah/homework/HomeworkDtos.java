package com.manarah.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class HomeworkDtos {

    /** One rubric criterion. {@code levels} is optional; when present a teacher clicks a level instead of typing points. */
    public record RubricLevel(String label, double points, String description) {
    }

    public record RubricCriterion(String id, String title, String description, double maxPoints, List<RubricLevel> levels) {
    }

    public record AttachmentInput(@NotBlank String fileKey, String name, Long size) {
    }

    public record AttachmentView(String fileKey, String name, long size) {
    }

    public record AssignmentView(Long id, Long courseId, String courseTitle, String title, String description,
                                 String fileKey, Instant startAt, Instant deadline, double maxScore,
                                 long submitted, long graded, long missing, long returned, long enrolled,
                                 boolean allowLate, double latePenaltyPercent, List<RubricCriterion> rubric,
                                 Double avgPercent, Instant createdAt, Instant updatedAt) {
    }

    public record CreateAssignmentRequest(@NotNull Long courseId, @NotBlank String title, String description,
                                          String fileKey, Instant startAt, Instant deadline, Double maxScore,
                                          Boolean allowLate, Double latePenaltyPercent, List<RubricCriterion> rubric) {
    }

    /** Partial update; null fields are untouched. {@code clearFile}/{@code clearDeadline} remove existing values. */
    public record UpdateAssignmentRequest(String title, String description, String fileKey, Instant startAt, Instant deadline,
                                          Double maxScore, Boolean allowLate, Double latePenaltyPercent,
                                          List<RubricCriterion> rubric, Boolean clearFile, Boolean clearDeadline,
                                          Boolean clearRubric) {
    }

    public record SubmissionView(Long id, Long assignmentId, Long studentId, String studentName, String text,
                                 String fileKey, String status, Instant submittedAt, Double score, String feedback,
                                 List<AttachmentView> files, Double rawScore, double penaltyPercent,
                                 Map<String, Double> rubricScores, int resubmissions, Instant gradedAt, Instant returnedAt,
                                 Long lateSeconds) {
    }

    /** A student's own view of an assignment, with their submission state embedded (no classmates' data). */
    public record MyAssignmentView(Long id, Long courseId, String courseTitle, String title, String description,
                                   String fileKey, Instant startAt, Instant deadline, double maxScore,
                                   String myStatus, Double myScore, String myFeedback, String myText,
                                   String myFileKey, Instant mySubmittedAt, List<AttachmentView> myFiles,
                                   boolean allowLate, double latePenaltyPercent, List<RubricCriterion> rubric,
                                   Map<String, Double> myRubricScores, Double myRawScore, double myPenaltyPercent,
                                   Instant myGradedAt, Instant myReturnedAt, int myResubmissions, Instant serverNow) {
    }

    /** studentId is only honoured for staff callers (recording a paper submission on a student's behalf);
     *  a STUDENT-role caller always submits as themselves regardless of this field. */
    public record SubmitRequest(@NotNull Long assignmentId, Long studentId, String text, String fileKey,
                                List<AttachmentInput> files) {
    }

    /** {@code score} is the raw score (before penalty). With a rubric, {@code rubricScores} is authoritative
     *  and {@code score} is derived server-side. {@code waivePenalty} skips the late deduction for this student. */
    public record GradeRequest(Double score, String feedback, Map<String, Double> rubricScores, Boolean waivePenalty) {
    }

    public record CommentView(Long id, String text, int uses) {
    }

    public record CommentRequest(@NotBlank String text) {
    }
}
