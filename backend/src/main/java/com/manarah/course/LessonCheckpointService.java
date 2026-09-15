package com.manarah.course;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.domain.LessonCheckpoint;
import com.manarah.course.domain.LessonCheckpointAnswer;
import com.manarah.course.repo.LessonCheckpointAnswerRepository;
import com.manarah.course.repo.LessonCheckpointRepository;
import com.manarah.exam.domain.Question;
import com.manarah.exam.domain.QuestionOption;
import com.manarah.exam.repo.QuestionOptionRepository;
import com.manarah.exam.repo.QuestionRepository;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * In-video checkpoint questions (§7/§9). A lesson's video pauses at each checkpoint's timestamp and
 * asks its question; checkpoints with no timestamp form the short end-of-lesson test. These are
 * formative — answers are recorded so the teacher can see who understood what, but they never feed
 * the gradebook, which stays driven by exams and homework.
 */
@Service
public class LessonCheckpointService {

    public record CheckpointOption(Long id, String text, Boolean correct) {}

    /** Teacher view: carries the answer key. */
    public record CheckpointView(Long id, Long questionId, Integer atSeconds, int position, String type, String stem,
                                 String imageKey, List<CheckpointOption> options, String correctAnswer,
                                 String explanation, long answered, long correctCount) {}

    /** Student view: same question, no key. */
    public record CheckpointPrompt(Long id, Integer atSeconds, int position, String type, String stem, String imageKey,
                                   List<CheckpointOption> options, Boolean myCorrect) {}

    public record CreateCheckpointRequest(Long questionId, Integer atSeconds, Integer position) {}

    public record AnswerRequest(List<Long> selectedOptions, String answerText) {}

    public record AnswerResult(boolean correct, String correctAnswer, String explanation) {}

    private final LessonCheckpointRepository checkpoints;
    private final LessonCheckpointAnswerRepository answers;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final StudentRepository students;
    private final LearningService learning;

    public LessonCheckpointService(LessonCheckpointRepository checkpoints, LessonCheckpointAnswerRepository answers,
                                   QuestionRepository questions, QuestionOptionRepository options,
                                   StudentRepository students, LearningService learning) {
        this.checkpoints = checkpoints;
        this.answers = answers;
        this.questions = questions;
        this.options = options;
        this.students = students;
        this.learning = learning;
    }

    // ---------- Teacher side ----------

    public List<CheckpointView> list(UserPrincipal actor, Long lessonId) {
        Long tenantId = TenantContext.require();
        learning.access(actor, learning.lessonCourse(actor, lessonId), true);
        List<LessonCheckpoint> rows = checkpoints.findByTenantIdAndLessonIdOrderByPositionAsc(tenantId, lessonId);
        if (rows.isEmpty()) return List.of();
        Map<Long, List<LessonCheckpointAnswer>> byCheckpoint = new HashMap<>();
        for (var a : answers.findByTenantIdAndCheckpointIdIn(tenantId, rows.stream().map(LessonCheckpoint::getId).toList()))
            byCheckpoint.computeIfAbsent(a.getCheckpointId(), k -> new ArrayList<>()).add(a);
        List<CheckpointView> out = new ArrayList<>();
        for (LessonCheckpoint c : rows) {
            Question q = questions.findByTenantIdAndId(tenantId, c.getQuestionId()).orElse(null);
            if (q == null) continue;
            var given = byCheckpoint.getOrDefault(c.getId(), List.of());
            out.add(new CheckpointView(c.getId(), q.getId(), c.getAtSeconds(), c.getPosition(), q.getType(), q.getStem(),
                    q.getImageKey(), optionsOf(tenantId, q.getId(), true), q.getCorrectAnswer(), q.getExplanation(),
                    given.size(), given.stream().filter(LessonCheckpointAnswer::isCorrect).count()));
        }
        return out;
    }

    @Transactional
    public CheckpointView add(UserPrincipal actor, Long lessonId, CreateCheckpointRequest req) {
        Long tenantId = TenantContext.require();
        learning.access(actor, learning.lessonCourse(actor, lessonId), true);
        if (req.questionId() == null) throw new BadRequestException("اختر سؤالاً من بنك الأسئلة");
        questions.findByTenantIdAndId(tenantId, req.questionId())
                .orElseThrow(() -> NotFoundException.of("السؤال", req.questionId()));
        if (req.atSeconds() != null && req.atSeconds() < 0) throw new BadRequestException("التوقيت لا يمكن أن يكون سالباً");
        if (checkpoints.countByTenantIdAndLessonId(tenantId, lessonId) >= 50)
            throw new BadRequestException("الحد الأقصى 50 سؤالاً للدرس الواحد");
        LessonCheckpoint c = new LessonCheckpoint();
        c.setTenantId(tenantId);
        c.setLessonId(lessonId);
        c.setQuestionId(req.questionId());
        c.setAtSeconds(req.atSeconds());
        c.setPosition(req.position() != null ? req.position()
                : (int) checkpoints.countByTenantIdAndLessonId(tenantId, lessonId));
        c.setCreatedAt(Instant.now());
        checkpoints.save(c);
        Question q = questions.findByTenantIdAndId(tenantId, c.getQuestionId()).orElseThrow();
        return new CheckpointView(c.getId(), q.getId(), c.getAtSeconds(), c.getPosition(), q.getType(), q.getStem(),
                q.getImageKey(), optionsOf(tenantId, q.getId(), true), q.getCorrectAnswer(), q.getExplanation(), 0, 0);
    }

    @Transactional
    public void remove(UserPrincipal actor, Long checkpointId) {
        Long tenantId = TenantContext.require();
        LessonCheckpoint c = checkpoints.findByTenantIdAndId(tenantId, checkpointId)
                .orElseThrow(() -> NotFoundException.of("السؤال", checkpointId));
        learning.access(actor, learning.lessonCourse(actor, c.getLessonId()), true);
        checkpoints.delete(c);
    }

    // ---------- Student side ----------

    public List<CheckpointPrompt> forStudent(UserPrincipal actor, Long lessonId) {
        Long tenantId = TenantContext.require();
        learning.access(actor, learning.lessonCourse(actor, lessonId), false);
        learning.requireReleasedLesson(actor, lessonId);
        Long studentId = actor.getRole() == Role.STUDENT ? ownStudentId(actor) : null;
        List<CheckpointPrompt> out = new ArrayList<>();
        for (LessonCheckpoint c : checkpoints.findByTenantIdAndLessonIdOrderByPositionAsc(tenantId, lessonId)) {
            Question q = questions.findByTenantIdAndId(tenantId, c.getQuestionId()).orElse(null);
            if (q == null) continue;
            Boolean mine = studentId == null ? null : answers
                    .findByTenantIdAndCheckpointIdAndStudentId(tenantId, c.getId(), studentId)
                    .map(LessonCheckpointAnswer::isCorrect).orElse(null);
            out.add(new CheckpointPrompt(c.getId(), c.getAtSeconds(), c.getPosition(), q.getType(), q.getStem(),
                    q.getImageKey(), optionsOf(tenantId, q.getId(), false), mine));
        }
        return out;
    }

    @Transactional
    public AnswerResult answer(UserPrincipal actor, Long checkpointId, AnswerRequest req) {
        Long tenantId = TenantContext.require();
        LessonCheckpoint c = checkpoints.findByTenantIdAndId(tenantId, checkpointId)
                .orElseThrow(() -> NotFoundException.of("السؤال", checkpointId));
        learning.access(actor, learning.lessonCourse(actor, c.getLessonId()), false);
        Long studentId = ownStudentId(actor);
        Question q = questions.findByTenantIdAndId(tenantId, c.getQuestionId())
                .orElseThrow(() -> NotFoundException.of("السؤال", c.getQuestionId()));

        boolean correct = grade(tenantId, q, req);
        var row = answers.findByTenantIdAndCheckpointIdAndStudentId(tenantId, checkpointId, studentId)
                .orElseGet(() -> {
                    var fresh = new LessonCheckpointAnswer();
                    fresh.setTenantId(tenantId);
                    fresh.setCheckpointId(checkpointId);
                    fresh.setStudentId(studentId);
                    return fresh;
                });
        row.setCorrect(correct);
        row.setAnswerText(req.answerText());
        row.setAnsweredAt(Instant.now());
        answers.save(row);
        return new AnswerResult(correct, correct ? null : keyOf(tenantId, q), q.getExplanation());
    }

    // ---------- helpers ----------

    private boolean grade(Long tenantId, Question q, AnswerRequest req) {
        if (usesOptions(q.getType())) {
            Set<Long> picked = new HashSet<>(req.selectedOptions() == null ? List.of() : req.selectedOptions());
            Set<Long> key = options.findByTenantIdAndQuestionIdOrderByPosition(tenantId, q.getId()).stream()
                    .filter(QuestionOption::isCorrect).map(QuestionOption::getId).collect(java.util.stream.Collectors.toSet());
            return !key.isEmpty() && key.equals(picked);
        }
        if ("ESSAY".equals(q.getType())) return false; // needs a human; recorded but never auto-marked correct
        return normalize(q.getCorrectAnswer()).equals(normalize(req.answerText()));
    }

    private String keyOf(Long tenantId, Question q) {
        if (!usesOptions(q.getType())) return q.getCorrectAnswer();
        return options.findByTenantIdAndQuestionIdOrderByPosition(tenantId, q.getId()).stream()
                .filter(QuestionOption::isCorrect).map(QuestionOption::getText)
                .reduce((a, b) -> a + "، " + b).orElse(null);
    }

    private List<CheckpointOption> optionsOf(Long tenantId, Long questionId, boolean withKey) {
        return options.findByTenantIdAndQuestionIdOrderByPosition(tenantId, questionId).stream()
                .map(o -> new CheckpointOption(o.getId(), o.getText(), withKey ? o.isCorrect() : null))
                .toList();
    }

    private static boolean usesOptions(String type) {
        return "MCQ".equals(type) || "MULTI_SELECT".equals(type) || "TRUE_FALSE".equals(type);
    }

    /** Mirrors the exam grader: case, spacing, Arabic diacritics and Arabic-Indic digits are ignored. */
    private static String normalize(String s) {
        if (s == null) return "";
        StringBuilder digits = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '٠' && ch <= '٩') digits.append((char) ('0' + ch - '٠'));
            else if (ch >= '۰' && ch <= '۹') digits.append((char) ('0' + ch - '۰'));
            else digits.append(ch);
        }
        String n = digits.toString().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        n = n.replaceAll("[\\u064B-\\u0652\\u0640]", "");
        return n.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي');
    }

    private Long ownStudentId(UserPrincipal actor) {
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .map(s -> s.getId())
                .orElseThrow(() -> new BadRequestException("هذا الحساب غير مرتبط بطالب"));
    }
}
