package com.manarah.exam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class ExamDtos {

    // ---- Question bank ----
    public record OptionInput(String text, boolean correct, Integer position) {
    }

    public record OptionView(Long id, String text, Boolean correct, int position) {
    }

    public record QuestionView(Long id, String subject, String chapter, String lesson, String difficulty,
                               String type, String stem, double points, String correctAnswer,
                               String learningObjective, String tags, List<OptionView> options,
                               String explanation, long usedInExams, String imageKey) {
    }

    public record CreateQuestionRequest(
            String subject, String chapter, String lesson,
            @NotBlank String difficulty, @NotBlank String type, @NotBlank String stem,
            Double points, String correctAnswer, String learningObjective, String tags,
            List<OptionInput> options, String explanation, String imageKey) {
        public CreateQuestionRequest(String subject, String chapter, String lesson, String difficulty, String type,
                                     String stem, Double points, String correctAnswer, String learningObjective,
                                     String tags, List<OptionInput> options) {
            this(subject, chapter, lesson, difficulty, type, stem, points, correctAnswer, learningObjective, tags, options, null, null);
        }
    }

    // ---- Exams ----
    public record ExamSummary(Long id, Long courseId, String courseTitle, String title, int durationMinutes,
                              double totalPoints, double passPercent, String status, long questionCount, String pdfKey,
                              Instant startAt, Instant endAt, long attempts, long pendingManual, String showResults) {
    }

    public record ExamResultRow(Long studentExamId, Long studentId, String studentName, String status,
                                double score, double maxScore, double percent, int tabSwitches, boolean needsManualGrade,
                                Instant startedAt, Instant submittedAt, Long durationSeconds, int fullscreenExits,
                                int integrityEvents) {
    }

    /** Item analysis: how the cohort did on one question (Moodle "quiz statistics" style). */
    public record QuestionStat(Long questionId, String stem, String type, double points, long answered,
                               long correct, double correctRate, double avgPoints) {
    }

    public record ExamAnalytics(long attempts, long submitted, double avgPercent, double highest, double lowest,
                                double medianPercent, double passRate, long pendingManual, long flagged,
                                List<Integer> distribution, List<QuestionStat> questionStats,
                                java.util.List<ExamResultRow> rows) {
    }

    public record ExamDetail(ExamSummary summary, String description, boolean shuffleQuestions, boolean shuffleOptions,
                             boolean fullscreen, boolean disableCopy, boolean detectTabSwitch,
                             Instant startAt, Instant endAt, List<QuestionView> questions,
                             String showResults, boolean showCorrectAnswers, List<Double> pointsOverrides) {
    }

    public record CreateExamRequest(
            Long courseId, @NotBlank String title, String description, Integer durationMinutes,
            Double passPercent, Boolean shuffleQuestions, Boolean shuffleOptions,
            Boolean fullscreen, Boolean disableCopy, Boolean detectTabSwitch,
            Instant startAt, Instant endAt, String pdfKey, String showResults, Boolean showCorrectAnswers) {
        public CreateExamRequest(Long courseId, String title, String description, Integer durationMinutes,
                                 Double passPercent, Boolean shuffleQuestions, Boolean shuffleOptions,
                                 Boolean fullscreen, Boolean disableCopy, Boolean detectTabSwitch,
                                 Instant startAt, Instant endAt, String pdfKey) {
            this(courseId, title, description, durationMinutes, passPercent, shuffleQuestions, shuffleOptions,
                    fullscreen, disableCopy, detectTabSwitch, startAt, endAt, pdfKey, null, null);
        }
    }

    /** Partial update of an exam; null fields are left untouched. */
    public record UpdateExamRequest(String title, String description, Integer durationMinutes, Double passPercent,
                                    Boolean shuffleQuestions, Boolean shuffleOptions, Boolean fullscreen,
                                    Boolean disableCopy, Boolean detectTabSwitch, Instant startAt, Instant endAt,
                                    String showResults, Boolean showCorrectAnswers, Boolean clearSchedule) {
    }

    public record AddQuestionRequest(@NotNull Long questionId, Double pointsOverride) {
    }

    public record ReorderRequest(@NotNull List<Long> questionIds) {
    }

    public record AutoGenerateRequest(Long courseId, @NotBlank String title, String subject,
                                      int easy, int medium, int hard, Integer durationMinutes,
                                      Double passPercent, Boolean shuffleQuestions, Boolean shuffleOptions, Boolean detectTabSwitch,
                                      Boolean fullscreen, Boolean disableCopy, Instant startAt, Instant endAt,
                                      String showResults, Boolean showCorrectAnswers, String description) {
    }

    public record AiGenerateQuestionsRequest(String subject, String topic, String difficulty,
                                             String type, Integer count) {
    }

    // ---- Taking an exam (no correctness leaked) ----
    public record TakeOption(Long id, String text) {
    }

    public record TakeQuestion(Long questionId, String type, String stem, double points, List<TakeOption> options,
                               String imageKey) {
    }

    public record AttemptView(Long studentExamId, Long examId, String title, int durationMinutes, String status,
                              boolean fullscreen, boolean disableCopy, boolean detectTabSwitch,
                              Instant startedAt, List<TakeQuestion> questions, Instant expiresAt,
                              Instant serverNow, List<SubmitAnswer> savedAnswers, Instant savedAt, String description) {
    }

    public record DraftView(Instant savedAt, Instant expiresAt) {}
    public record ReviewAnswer(Long questionId, String stem, String type, String answerText,
                               List<String> selectedOptions, double points, double awardedPoints,
                               Boolean correct, String feedback) {}

    public record SubmitAnswer(@NotNull Long questionId, String answerText, List<Long> selectedOptions) {
    }

    /** A client-side integrity signal: TAB_HIDDEN, TAB_VISIBLE, FULLSCREEN_EXIT, FULLSCREEN_ENTER, COPY_BLOCKED,
     *  PASTE_BLOCKED, RESUME. These are hints for the teacher, never proof of misconduct. */
    public record IntegrityEvent(String type, Instant at) {
    }

    public record SubmitRequest(List<SubmitAnswer> answers, Integer tabSwitches, List<IntegrityEvent> events) {
        public SubmitRequest(List<SubmitAnswer> answers, Integer tabSwitches) {
            this(answers, tabSwitches, null);
        }
    }

    public record QuestionResult(Long questionId, Boolean correct, double awarded, double points) {
    }

    public record AttemptResult(double score, double maxScore, double percent, boolean passed,
                                boolean needsManualGrade, int tabSwitches, List<QuestionResult> results,
                                boolean canReview) {
        public AttemptResult(double score, double maxScore, double percent, boolean passed,
                             boolean needsManualGrade, int tabSwitches, List<QuestionResult> results) {
            this(score, maxScore, percent, passed, needsManualGrade, tabSwitches, results, false);
        }
    }

    public record ManualGradeRequest(@NotNull Long questionId, @NotNull Double points, String feedback) {
    }

    /** One exam as it looks from a single student's perspective — for the student themselves, or
     *  a parent/staff member authorized to view that student (see StudentAccessPolicy). No other
     *  student's data is included. */
    public record StudentExamRow(Long examId, String examTitle, String courseTitle, String status,
                                 Double score, Double maxScore, Double percent) {
    }

    /** The student's own exam catalogue card: schedule window + their attempt state, no answer keys. */
    public record StudentExamCard(Long id, Long courseId, String courseTitle, String title, String description,
                                  int durationMinutes, double totalPoints, double passPercent, long questionCount,
                                  String pdfKey, Instant startAt, Instant endAt, String window, String myStatus,
                                  Double myScore, Double myMaxScore, Double myPercent, Boolean myPassed,
                                  Instant myStartedAt, Instant mySubmittedAt, boolean needsManualGrade, boolean canReview,
                                  boolean fullscreen, boolean detectTabSwitch, boolean disableCopy, Long studentExamId,
                                  Instant serverNow) {
    }

    // ---- Student's own post-exam review ----
    public record ReviewOption(Long id, String text, Boolean correct, boolean selected) {
    }

    public record StudentReviewAnswer(Long questionId, String stem, String type, double points, double awardedPoints,
                                      Boolean correct, String myAnswerText, List<ReviewOption> options,
                                      String correctAnswer, String explanation, String feedback, String imageKey) {
    }

    public record StudentReview(Long examId, String title, double score, double maxScore, double percent, boolean passed,
                                boolean needsManualGrade, boolean showCorrectAnswers, Instant submittedAt,
                                List<StudentReviewAnswer> answers) {
    }

    public record IntegrityReport(Long studentExamId, String studentName, Instant startedAt, Instant submittedAt,
                                  Long durationSeconds, int tabSwitches, int fullscreenExits,
                                  List<IntegrityEvent> events) {
    }
}
