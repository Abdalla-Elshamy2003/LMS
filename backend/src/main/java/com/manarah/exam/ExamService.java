package com.manarah.exam;

import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.common.util.Json;
import com.manarah.common.web.PageResponse;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.exam.ExamDtos.*;
import com.manarah.exam.domain.*;
import com.manarah.exam.repo.*;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ExamService {

    private static final Set<String> QUESTION_TYPES = Set.of("MCQ", "MULTI_SELECT", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER", "ESSAY", "NUMERIC");
    private static final Set<String> RESULT_POLICIES = Set.of("NEVER", "AFTER_SUBMIT", "AFTER_CLOSE");
    private static final Set<String> EVENT_TYPES = Set.of("TAB_HIDDEN", "TAB_VISIBLE", "FULLSCREEN_EXIT", "FULLSCREEN_ENTER",
            "COPY_BLOCKED", "PASTE_BLOCKED", "RESUME", "WINDOW_BLUR");
    private static final int MAX_EVENTS = 300;

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final StudentExamRepository studentExams;
    private final StudentAnswerRepository studentAnswers;
    private final CourseRepository courses;
    private final com.manarah.student.repo.StudentRepository students;
    private final com.manarah.enrollment.repo.EnrollmentRepository enrollments;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final AiQuestionGenerator aiQuestionGenerator;
    private final com.manarah.academy.TeacherAcademyRepository academies;

    public ExamService(QuestionRepository questions, QuestionOptionRepository options, ExamRepository exams,
                       ExamQuestionRepository examQuestions, StudentExamRepository studentExams,
                       StudentAnswerRepository studentAnswers, CourseRepository courses,
                       com.manarah.student.repo.StudentRepository students,
                       com.manarah.enrollment.repo.EnrollmentRepository enrollments,
                       org.springframework.context.ApplicationEventPublisher events,
                       AiQuestionGenerator aiQuestionGenerator,
                       com.manarah.academy.TeacherAcademyRepository academies) {
        this.academies = academies;
        this.questions = questions;
        this.options = options;
        this.exams = exams;
        this.examQuestions = examQuestions;
        this.studentExams = studentExams;
        this.studentAnswers = studentAnswers;
        this.courses = courses;
        this.students = students;
        this.enrollments = enrollments;
        this.events = events;
        this.aiQuestionGenerator = aiQuestionGenerator;
    }

    /** All exams for the courses this student is enrolled in, each tagged with that student's own
     *  attempt status/score — used for a student's own view and for parent/staff viewers who are
     *  authorized to see this particular student (see StudentAccessPolicy). */
    public List<StudentExamRow> forStudent(Long studentId) {
        Long tenantId = TenantContext.require();
        Set<Long> courseIds = enrollments.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).map(e -> e.getCourseId()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<StudentExamRow> rows = new ArrayList<>();
        for (Long courseId : courseIds) {
            String courseTitle = courses.findByTenantIdAndId(tenantId, courseId).map(c -> c.getTitle()).orElse("—");
            for (Exam e : exams.findByTenantIdAndCourseId(tenantId, courseId)) {
                if (!"PUBLISHED".equals(e.getStatus()) && !"CLOSED".equals(e.getStatus())) continue;
                StudentExam attempt = studentExams.findByTenantIdAndExamIdAndStudentId(tenantId, e.getId(), studentId).orElse(null);
                boolean done = attempt != null && ("SUBMITTED".equals(attempt.getStatus()) || "GRADED".equals(attempt.getStatus()));
                Double score = done ? attempt.getScore() : null;
                Double maxScore = done ? attempt.getMaxScore() : null;
                Double percent = (done && maxScore != null && maxScore > 0) ? round1(score / maxScore * 100) : null;
                rows.add(new StudentExamRow(e.getId(), e.getTitle(), courseTitle,
                        attempt == null ? "NOT_STARTED" : attempt.getStatus(), score, maxScore, percent));
            }
        }
        return rows;
    }

    /** Drafts (not yet saved) via AI — the caller reviews/edits then saves each via createQuestion. */
    public List<CreateQuestionRequest> generateQuestions(AiGenerateQuestionsRequest req) {
        return aiQuestionGenerator.generate(req.subject(), req.topic(), req.difficulty(), req.type(),
                req.count() != null ? req.count() : 5);
    }

    public List<CreateQuestionRequest> generateQuestionsFromFile(org.springframework.web.multipart.MultipartFile file, String subject,
                                                                 String focus, String difficulty, String type, int count) {
        return aiQuestionGenerator.generateFromFile(file, subject, focus, difficulty, type, count);
    }

    /** Per-exam analytics for the teacher: attempts, averages, distribution, item analysis and each student's result. */
    public ExamAnalytics analytics(Long examId) {
        Long tenantId = TenantContext.require();
        Exam exam = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        var attempts = studentExams.findByTenantIdAndExamId(tenantId, examId);
        List<ExamResultRow> rows = new ArrayList<>();
        List<Double> percents = new ArrayList<>();
        long submitted = 0, pendingManual = 0, flagged = 0;
        int[] distribution = new int[5];
        for (StudentExam se : attempts) {
            String name = students.findByTenantIdAndId(tenantId, se.getStudentId()).map(s -> s.getFullName()).orElse("طالب");
            double pct = se.getMaxScore() > 0 ? round1(se.getScore() / se.getMaxScore() * 100) : 0;
            boolean done = "SUBMITTED".equals(se.getStatus()) || "GRADED".equals(se.getStatus());
            if (done) { submitted++; percents.add(pct); distribution[Math.min(4, (int) Math.floor(pct / 20))]++; }
            if (se.isNeedsManualGrade()) pendingManual++;
            int eventCount = integrityEvents(se).size();
            if (se.getTabSwitches() >= 3 || se.getFullscreenExits() >= 2) flagged++;
            Long duration = se.getStartedAt() == null ? null
                    : ChronoUnit.SECONDS.between(se.getStartedAt(), se.getSubmittedAt() != null ? se.getSubmittedAt() : Instant.now());
            rows.add(new ExamResultRow(se.getId(), se.getStudentId(), name, se.getStatus(),
                    se.getScore(), se.getMaxScore(), pct, se.getTabSwitches(), se.isNeedsManualGrade(),
                    se.getStartedAt(), se.getSubmittedAt(), duration, se.getFullscreenExits(), eventCount));
        }
        double avg = percents.isEmpty() ? 0 : round1(percents.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        double hi = percents.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double lo = percents.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double median = 0;
        if (!percents.isEmpty()) {
            List<Double> sorted = new ArrayList<>(percents); Collections.sort(sorted);
            int n = sorted.size();
            median = round1(n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2);
        }
        long passed = percents.stream().filter(p -> p >= exam.getPassPercent()).count();
        double passRate = percents.isEmpty() ? 0 : round1(passed * 100.0 / percents.size());
        List<Integer> dist = new ArrayList<>(); for (int d : distribution) dist.add(d);
        return new ExamAnalytics(attempts.size(), submitted, avg, hi, lo, median, passRate, pendingManual, flagged,
                dist, questionStats(exam, attempts), rows);
    }

    private List<QuestionStat> questionStats(Exam exam, List<StudentExam> attempts) {
        Long tenantId = exam.getTenantId();
        List<Long> doneIds = attempts.stream().filter(a -> !"IN_PROGRESS".equals(a.getStatus()) && !"NOT_STARTED".equals(a.getStatus()))
                .map(StudentExam::getId).toList();
        Map<Long, List<StudentAnswer>> byQuestion = new HashMap<>();
        if (!doneIds.isEmpty())
            for (StudentAnswer a : studentAnswers.findByTenantIdAndStudentExamIdIn(tenantId, doneIds))
                byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        Map<Long, Double> points = pointsMap(exam);
        List<QuestionStat> stats = new ArrayList<>();
        for (ExamQuestion eq : examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, exam.getId())) {
            Question q = questions.findByTenantIdAndId(tenantId, eq.getQuestionId()).orElse(null);
            if (q == null) continue;
            List<StudentAnswer> answers = byQuestion.getOrDefault(q.getId(), List.of());
            long answered = answers.stream().filter(a -> (a.getAnswerText() != null && !a.getAnswerText().isBlank())
                    || (a.getSelectedOptions() != null && !Json.readLongList(a.getSelectedOptions()).isEmpty())).count();
            long correct = answers.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
            double max = points.getOrDefault(q.getId(), q.getPoints());
            double avgPts = answers.isEmpty() ? 0 : round1(answers.stream().mapToDouble(StudentAnswer::getAwardedPoints).average().orElse(0));
            stats.add(new QuestionStat(q.getId(), q.getStem(), q.getType(), max, answered, correct,
                    answers.isEmpty() ? 0 : round1(correct * 100.0 / answers.size()), avgPts));
        }
        return stats;
    }

    // ================= Question bank =================

    private void validateQuestion(CreateQuestionRequest req) {
        if (!QUESTION_TYPES.contains(req.type()) || !Set.of("EASY", "MEDIUM", "HARD").contains(req.difficulty()))
            throw new BadRequestException("نوع السؤال أو الصعوبة غير صالح");
        if (req.stem() == null || req.stem().isBlank() || req.stem().length() > 10000) throw new BadRequestException("اكتب نص السؤال (حتى 10000 حرف)");
        if (req.points() != null && (!Double.isFinite(req.points()) || req.points() <= 0 || req.points() > 10000)) throw new BadRequestException("درجة السؤال يجب أن تكون موجبة وحتى 10000");
        if (needsOptions(req.type())) {
            if (req.options() == null || req.options().size() < 2 || req.options().size() > 20 || req.options().stream().anyMatch(o -> o == null || o.text() == null || o.text().isBlank())) throw new BadRequestException("أضف اختيارين صالحين على الأقل");
            long correct = req.options().stream().filter(OptionInput::correct).count();
            if (correct == 0 || (!"MULTI_SELECT".equals(req.type()) && correct != 1)) throw new BadRequestException("حدّد الإجابة الصحيحة للسؤال");
            if ("TRUE_FALSE".equals(req.type()) && req.options().size() != 2) throw new BadRequestException("سؤال صح/خطأ يحتوي على اختيارين فقط");
        } else if (!"ESSAY".equals(req.type())) {
            if (req.correctAnswer() == null || req.correctAnswer().isBlank()) throw new BadRequestException("اكتب الإجابة النموذجية ليتم التصحيح تلقائياً");
            if ("NUMERIC".equals(req.type())) {
                try { Double.parseDouble(req.correctAnswer().trim()); } catch (NumberFormatException e) { throw new BadRequestException("الإجابة الرقمية يجب أن تكون رقماً"); }
            }
        }
        if (req.explanation() != null && req.explanation().length() > 5000) throw new BadRequestException("الشرح أطول من الحد المسموح");
    }

    @Transactional
    public QuestionView createQuestion(UserPrincipal actor, CreateQuestionRequest req) {
        Long tenantId = TenantContext.require();
        validateQuestion(req);
        Question q = new Question();
        q.setTenantId(tenantId);
        apply(q, req);
        q.setCreatedBy(actor.getId());
        questions.save(q);
        saveOptions(tenantId, q, req);
        return toQuestionView(q, true);
    }

    private void apply(Question q, CreateQuestionRequest req) {
        q.setSubject(nn(req.subject()));
        q.setChapter(nn(req.chapter()));
        q.setLesson(nn(req.lesson()));
        q.setDifficulty(req.difficulty());
        q.setType(req.type());
        q.setStem(req.stem().trim());
        q.setPoints(req.points() != null ? req.points() : 1);
        q.setCorrectAnswer(needsOptions(req.type()) || "ESSAY".equals(req.type()) ? null : req.correctAnswer().trim());
        q.setLearningObjective(nn(req.learningObjective()));
        q.setTags(nn(req.tags()));
        q.setExplanation(nn(req.explanation()));
        q.setImageKey(nn(req.imageKey()));
    }

    private void saveOptions(Long tenantId, Question q, CreateQuestionRequest req) {
        if (!needsOptions(req.type()) || req.options() == null) return;
        int pos = 0;
        for (OptionInput oi : req.options()) {
            QuestionOption o = new QuestionOption();
            o.setTenantId(tenantId);
            o.setQuestionId(q.getId());
            o.setText(oi.text().trim());
            o.setCorrect(oi.correct());
            o.setPosition(pos++);
            options.save(o);
        }
    }

    /** Editing is blocked once any exam containing this question has student attempts: changing the key
     *  after students answered would silently rewrite their results. */
    @Transactional
    public QuestionView updateQuestion(Long id, CreateQuestionRequest req) {
        Long tenantId = TenantContext.require();
        Question q = questions.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("السؤال", id));
        validateQuestion(req);
        for (ExamQuestion eq : examQuestions.findByTenantIdAndQuestionId(tenantId, id))
            if (studentExams.countByTenantIdAndExamId(tenantId, eq.getExamId()) > 0)
                throw new BadRequestException("لا يمكن تعديل سؤال أجاب عنه طلاب بالفعل؛ أنشئ نسخة جديدة منه");
        apply(q, req);
        questions.save(q);
        options.deleteByQuestionId(q.getId());
        saveOptions(tenantId, q, req);
        // Keep exam totals in sync when the base points changed and no override exists.
        for (ExamQuestion eq : examQuestions.findByTenantIdAndQuestionId(tenantId, id)) recomputeTotal(tenantId, eq.getExamId());
        return toQuestionView(q, true);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        Long tenantId = TenantContext.require();
        Question q = questions.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("السؤال", id));
        if (!examQuestions.findByTenantIdAndQuestionId(tenantId, id).isEmpty())
            throw new BadRequestException("السؤال مستخدم في امتحان؛ أزله من الامتحان أولاً");
        options.deleteByQuestionId(id);
        questions.delete(q);
    }

    public PageResponse<QuestionView> searchQuestions(UserPrincipal actor, String subject, String difficulty, String type,
                                                      String q, boolean mine, int page, int size) {
        Long tenantId = TenantContext.require();
        // Inside a teacher's own academy the whole bank is theirs, so only school tenants are scoped.
        boolean teacher = actor != null && actor.getRole() == Role.TEACHER && academies.findByTenantId(tenantId).isEmpty();
        Long ownerId = mine && actor != null ? actor.getId() : null;
        var p = questions.search(tenantId, nn(subject), nn(difficulty), nn(type), nn(q), ownerId,
                teacher ? 1 : 0, teacher ? actor.getId() : -1L, teacher ? mySubjects(tenantId, actor.getId()) : NO_SUBJECTS,
                PageRequest.of(page, Math.max(1, Math.min(200, size))));
        return PageResponse.of(p, question -> toQuestionView(question, true));
    }

    /** Placeholder so the {@code IN} clause is never handed an empty list; it matches no real subject (and, unlike NUL, PostgreSQL accepts it). */
    private static final List<String> NO_SUBJECTS = List.of("\uE000");

    /** The subjects a teacher actually teaches, taken from their own courses. */
    private List<String> mySubjects(Long tenantId, Long teacherId) {
        List<String> subjects = courses.findByTenantIdAndTeacherId(tenantId, teacherId).stream()
                .map(Course::getSubject)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        return subjects.isEmpty() ? NO_SUBJECTS : subjects;
    }

    public QuestionView getQuestion(Long id) {
        Long tenantId = TenantContext.require();
        return toQuestionView(questions.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("السؤال", id)), true);
    }

    // ================= Exam authoring =================

    public List<ExamSummary> list() {
        Long tenantId = TenantContext.require();
        return exams.findByTenantId(tenantId).stream().map(this::toExamSummary).toList();
    }

    public ExamDetail get(Long examId) {
        Long tenantId = TenantContext.require();
        Exam exam = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        List<QuestionView> qs = new ArrayList<>();
        List<Double> overrides = new ArrayList<>();
        for (ExamQuestion eq : examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, examId)) {
            Question q = questions.findByTenantIdAndId(tenantId, eq.getQuestionId()).orElse(null);
            if (q == null) continue;
            qs.add(toQuestionView(q, true));
            overrides.add(eq.getPointsOverride());
        }
        return new ExamDetail(toExamSummary(exam), exam.getDescription(), exam.isShuffleQuestions(), exam.isShuffleOptions(),
                exam.isFullscreen(), exam.isDisableCopy(), exam.isDetectTabSwitch(), exam.getStartAt(), exam.getEndAt(), qs,
                exam.getShowResults(), exam.isShowCorrectAnswers(), overrides);
    }

    @Transactional
    public ExamDetail create(UserPrincipal actor, CreateExamRequest req) {
        Long tenantId = TenantContext.require();
        if (req.courseId() != null) courses.findByTenantIdAndId(tenantId, req.courseId())
                .orElseThrow(() -> NotFoundException.of("الكورس", req.courseId()));
        requireFileKey(req.pdfKey(), tenantId, "exams");
        if (req.title() == null || req.title().isBlank() || req.title().length() > 200) throw new BadRequestException("اكتب عنواناً للامتحان (حتى 200 حرف)");
        if (req.durationMinutes() != null && (req.durationMinutes() < 1 || req.durationMinutes() > 600)) throw new BadRequestException("مدة الامتحان من دقيقة إلى 600 دقيقة");
        if (req.passPercent() != null && (!Double.isFinite(req.passPercent()) || req.passPercent() < 0 || req.passPercent() > 100)) throw new BadRequestException("نسبة النجاح من صفر إلى 100");
        if (req.startAt() != null && req.endAt() != null && !req.endAt().isAfter(req.startAt())) throw new BadRequestException("موعد النهاية يجب أن يأتي بعد البداية");
        if (req.showResults() != null && !RESULT_POLICIES.contains(req.showResults())) throw new BadRequestException("سياسة عرض النتائج غير صالحة");
        Exam e = new Exam();
        e.setTenantId(tenantId);
        e.setCourseId(req.courseId());
        e.setTitle(req.title().trim());
        e.setDescription(nn(req.description()));
        e.setDurationMinutes(req.durationMinutes() != null ? req.durationMinutes() : 60);
        e.setPassPercent(req.passPercent() != null ? req.passPercent() : 50);
        e.setShuffleQuestions(req.shuffleQuestions() == null || req.shuffleQuestions());
        e.setShuffleOptions(req.shuffleOptions() == null || req.shuffleOptions());
        e.setFullscreen(Boolean.TRUE.equals(req.fullscreen()));
        e.setDisableCopy(Boolean.TRUE.equals(req.disableCopy()));
        e.setDetectTabSwitch(Boolean.TRUE.equals(req.detectTabSwitch()));
        e.setStartAt(req.startAt());
        e.setEndAt(req.endAt());
        e.setPdfKey(req.pdfKey());
        e.setShowResults(req.showResults() != null ? req.showResults() : "AFTER_SUBMIT");
        e.setShowCorrectAnswers(req.showCorrectAnswers() == null || req.showCorrectAnswers());
        e.setCreatedBy(actor.getId());
        exams.save(e);
        return get(e.getId());
    }

    /** Settings that only affect *future* visibility (schedule end, review policy, title) may change at any
     *  time; anything that changes what a student sees while answering is frozen once attempts exist. */
    @Transactional
    public ExamDetail update(Long examId, UpdateExamRequest req) {
        Long tenantId = TenantContext.require();
        Exam e = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        boolean hasAttempts = studentExams.countByTenantIdAndExamId(tenantId, examId) > 0;
        if (req.title() != null) { if (req.title().isBlank() || req.title().length() > 200) throw new BadRequestException("عنوان غير صالح"); e.setTitle(req.title().trim()); }
        if (req.description() != null) e.setDescription(nn(req.description()));
        if (req.showResults() != null) { if (!RESULT_POLICIES.contains(req.showResults())) throw new BadRequestException("سياسة عرض النتائج غير صالحة"); e.setShowResults(req.showResults()); }
        if (req.showCorrectAnswers() != null) e.setShowCorrectAnswers(req.showCorrectAnswers());
        if (Boolean.TRUE.equals(req.clearSchedule())) { e.setStartAt(null); e.setEndAt(null); }
        if (req.startAt() != null) { if (hasAttempts) throw new BadRequestException("لا يمكن تغيير موعد البداية بعد بدء المحاولات"); e.setStartAt(req.startAt()); }
        if (req.endAt() != null) e.setEndAt(req.endAt());
        if (e.getStartAt() != null && e.getEndAt() != null && !e.getEndAt().isAfter(e.getStartAt())) throw new BadRequestException("موعد النهاية يجب أن يأتي بعد البداية");
        if (req.passPercent() != null) { if (!Double.isFinite(req.passPercent()) || req.passPercent() < 0 || req.passPercent() > 100) throw new BadRequestException("نسبة النجاح من صفر إلى 100"); e.setPassPercent(req.passPercent()); }
        boolean frozen = req.durationMinutes() != null || req.shuffleQuestions() != null || req.shuffleOptions() != null
                || req.fullscreen() != null || req.disableCopy() != null || req.detectTabSwitch() != null;
        if (frozen && hasAttempts) throw new BadRequestException("إعدادات المحاولة (المدة والترتيب والحماية) تُجمَّد بعد بدء الطلاب");
        if (req.durationMinutes() != null) { if (req.durationMinutes() < 1 || req.durationMinutes() > 600) throw new BadRequestException("مدة الامتحان من دقيقة إلى 600 دقيقة"); e.setDurationMinutes(req.durationMinutes()); }
        if (req.shuffleQuestions() != null) e.setShuffleQuestions(req.shuffleQuestions());
        if (req.shuffleOptions() != null) e.setShuffleOptions(req.shuffleOptions());
        if (req.fullscreen() != null) e.setFullscreen(req.fullscreen());
        if (req.disableCopy() != null) e.setDisableCopy(req.disableCopy());
        if (req.detectTabSwitch() != null) e.setDetectTabSwitch(req.detectTabSwitch());
        e.setUpdatedAt(Instant.now());
        exams.save(e);
        return get(examId);
    }

    @Transactional
    public void delete(Long examId) {
        Long tenantId = TenantContext.require();
        Exam e = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (studentExams.countByTenantIdAndExamId(tenantId, examId) > 0)
            throw new BadRequestException("لا يمكن حذف امتحان له محاولات؛ أغلقه بدلاً من ذلك");
        examQuestions.deleteAll(examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, examId));
        exams.delete(e);
    }

    @Transactional
    public ExamDetail setStatus(Long examId, String status) {
        Long tenantId = TenantContext.require();
        Exam e = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (!Set.of("PUBLISHED", "CLOSED").contains(status)) throw new BadRequestException("حالة غير صالحة");
        if ("DRAFT".equals(e.getStatus())) throw new BadRequestException("انشر الامتحان أولاً");
        e.setStatus(status);
        e.setUpdatedAt(Instant.now());
        exams.save(e);
        return get(examId);
    }

    @Transactional
    public ExamDetail addQuestion(Long examId, AddQuestionRequest req) {
        Long tenantId = TenantContext.require();
        Exam exam = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (!studentExams.findByTenantIdAndExamId(tenantId, examId).isEmpty()) throw new BadRequestException("لا يمكن تغيير الأسئلة بعد بدء محاولات الطلاب");
        if (req.pointsOverride() != null && (!Double.isFinite(req.pointsOverride()) || req.pointsOverride() <= 0)) throw new BadRequestException("درجة السؤال يجب أن تكون أكبر من صفر");
        Question q = questions.findByTenantIdAndId(tenantId, req.questionId())
                .orElseThrow(() -> NotFoundException.of("السؤال", req.questionId()));
        if (!examQuestions.existsByTenantIdAndExamIdAndQuestionId(tenantId, examId, req.questionId())) {
            ExamQuestion eq = new ExamQuestion();
            eq.setTenantId(tenantId);
            eq.setExamId(examId);
            eq.setQuestionId(req.questionId());
            eq.setPosition((int) examQuestions.countByTenantIdAndExamId(tenantId, examId));
            eq.setPointsOverride(req.pointsOverride());
            examQuestions.save(eq);
            exam.setTotalPoints(exam.getTotalPoints() + (req.pointsOverride() != null ? req.pointsOverride() : q.getPoints()));
            exams.save(exam);
        }
        return get(examId);
    }

    @Transactional
    public ExamDetail removeQuestion(Long examId, Long questionId) {
        Long tenantId = TenantContext.require();
        exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (studentExams.countByTenantIdAndExamId(tenantId, examId) > 0) throw new BadRequestException("لا يمكن تغيير الأسئلة بعد بدء محاولات الطلاب");
        ExamQuestion eq = examQuestions.findByTenantIdAndExamIdAndQuestionId(tenantId, examId, questionId)
                .orElseThrow(() -> NotFoundException.of("السؤال", questionId));
        examQuestions.delete(eq);
        int pos = 0;
        for (ExamQuestion rest : examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, examId)) { rest.setPosition(pos++); examQuestions.save(rest); }
        recomputeTotal(tenantId, examId);
        return get(examId);
    }

    @Transactional
    public ExamDetail setQuestionPoints(Long examId, Long questionId, Double pointsOverride) {
        Long tenantId = TenantContext.require();
        exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (studentExams.countByTenantIdAndExamId(tenantId, examId) > 0) throw new BadRequestException("لا يمكن تغيير الدرجات بعد بدء محاولات الطلاب");
        if (pointsOverride != null && (!Double.isFinite(pointsOverride) || pointsOverride <= 0 || pointsOverride > 10000)) throw new BadRequestException("درجة السؤال يجب أن تكون أكبر من صفر");
        ExamQuestion eq = examQuestions.findByTenantIdAndExamIdAndQuestionId(tenantId, examId, questionId)
                .orElseThrow(() -> NotFoundException.of("السؤال", questionId));
        eq.setPointsOverride(pointsOverride);
        examQuestions.save(eq);
        recomputeTotal(tenantId, examId);
        return get(examId);
    }

    @Transactional
    public ExamDetail reorder(Long examId, List<Long> questionIds) {
        Long tenantId = TenantContext.require();
        exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (studentExams.countByTenantIdAndExamId(tenantId, examId) > 0) throw new BadRequestException("لا يمكن تغيير الترتيب بعد بدء محاولات الطلاب");
        var current = examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, examId);
        if (questionIds == null || new HashSet<>(questionIds).size() != current.size()
                || !new HashSet<>(questionIds).equals(current.stream().map(ExamQuestion::getQuestionId).collect(java.util.stream.Collectors.toSet())))
            throw new BadRequestException("قائمة الترتيب لا تطابق أسئلة الامتحان");
        for (ExamQuestion eq : current) { eq.setPosition(questionIds.indexOf(eq.getQuestionId())); examQuestions.save(eq); }
        return get(examId);
    }

    private void recomputeTotal(Long tenantId, Long examId) {
        Exam exam = exams.findByTenantIdAndId(tenantId, examId).orElseThrow();
        double total = 0;
        for (ExamQuestion eq : examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, examId)) {
            Question q = questions.findByTenantIdAndId(tenantId, eq.getQuestionId()).orElse(null);
            if (q == null) continue;
            total += eq.getPointsOverride() != null ? eq.getPointsOverride() : q.getPoints();
        }
        exam.setTotalPoints(round1(total));
        exams.save(exam);
    }

    /** Auto-assemble an exam from the bank by difficulty mix (§10). */
    @Transactional
    public ExamDetail autoGenerate(UserPrincipal actor, AutoGenerateRequest req) {
        if (req.easy() < 0 || req.medium() < 0 || req.hard() < 0 || (long)req.easy()+req.medium()+req.hard() < 1
                || (long)req.easy()+req.medium()+req.hard() > 200) throw new BadRequestException("اختر من سؤال إلى 200 سؤال وبأعداد غير سالبة");
        ExamDetail exam = create(actor, new CreateExamRequest(req.courseId(), req.title(),
                req.description() != null ? req.description() : "تم توليده تلقائياً من بنك الأسئلة",
                req.durationMinutes(), req.passPercent(), req.shuffleQuestions(), req.shuffleOptions(), req.fullscreen(), req.disableCopy(),
                req.detectTabSwitch(), req.startAt(), req.endAt(), null, req.showResults(), req.showCorrectAnswers()));
        Long examId = exam.summary().id();
        pick(actor, req.subject(), "EASY", req.easy(), examId);
        pick(actor, req.subject(), "MEDIUM", req.medium(), examId);
        pick(actor, req.subject(), "HARD", req.hard(), examId);
        return get(examId);
    }

    private void pick(UserPrincipal actor, String subject, String difficulty, int count, Long examId) {
        if (count <= 0) return;
        Long tenantId = TenantContext.require();
        // Inside a teacher's own academy the whole bank is theirs, so only school tenants are scoped.
        boolean teacher = actor != null && actor.getRole() == Role.TEACHER && academies.findByTenantId(tenantId).isEmpty();
        List<Question> pool = questions.search(tenantId, nn(subject), difficulty, null, null, null,
                teacher ? 1 : 0, teacher ? actor.getId() : -1L, teacher ? mySubjects(tenantId, actor.getId()) : NO_SUBJECTS,
                PageRequest.of(0, 500)).getContent();
        pool = new ArrayList<>(pool);
        Collections.shuffle(pool);
        if (pool.size() < count) throw new BadRequestException("بنك الأسئلة لا يحتوي على العدد المطلوب للصعوبة " + difficulty + "؛ المتاح " + pool.size());
        pool.stream().limit(count).forEach(q -> addQuestion(examId, new AddQuestionRequest(q.getId(), null)));
    }

    @Transactional
    public void publish(Long examId) {
        Long tenantId = TenantContext.require();
        Exam exam = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        if (exam.getPdfKey() == null && examQuestions.countByTenantIdAndExamId(tenantId, examId) == 0)
            throw new BadRequestException("أضف سؤالاً واحداً على الأقل قبل النشر");
        exam.setStatus("PUBLISHED");
        exam.setUpdatedAt(Instant.now());
        exams.save(exam);
    }

    // ================= Student catalogue =================

    private String window(Exam e, Instant now) {
        if ("CLOSED".equals(e.getStatus())) return "CLOSED";
        if (e.getStartAt() != null && now.isBefore(e.getStartAt())) return "UPCOMING";
        if (e.getEndAt() != null && now.isAfter(e.getEndAt())) return "CLOSED";
        return "OPEN";
    }

    private boolean canReview(Exam e, StudentExam attempt, Instant now) {
        if (attempt == null || "IN_PROGRESS".equals(attempt.getStatus()) || "NOT_STARTED".equals(attempt.getStatus())) return false;
        return switch (e.getShowResults()) {
            case "AFTER_SUBMIT" -> true;
            case "AFTER_CLOSE" -> "CLOSED".equals(window(e, now));
            default -> false;
        };
    }

    /** Every published exam of the student's courses — upcoming, open and closed — with their own attempt. */
    public List<StudentExamCard> catalogue(Long studentId) {
        Long tenantId = TenantContext.require();
        Set<Long> enrolled = enrollments.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(en -> Set.of("ACTIVE", "COMPLETED").contains(en.getStatus()))
                .map(en -> en.getCourseId()).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now();
        List<StudentExamCard> cards = new ArrayList<>();
        for (Exam e : exams.findByTenantId(tenantId)) {
            if (!("PUBLISHED".equals(e.getStatus()) || "CLOSED".equals(e.getStatus()))) continue;
            if (e.getCourseId() == null || !enrolled.contains(e.getCourseId())) continue;
            StudentExam a = studentExams.findByTenantIdAndExamIdAndStudentId(tenantId, e.getId(), studentId).orElse(null);
            boolean done = a != null && ("SUBMITTED".equals(a.getStatus()) || "GRADED".equals(a.getStatus()));
            Double pct = done && a.getMaxScore() > 0 ? round1(a.getScore() / a.getMaxScore() * 100) : null;
            String courseTitle = courses.findByTenantIdAndId(tenantId, e.getCourseId()).map(c -> c.getTitle()).orElse("—");
            cards.add(new StudentExamCard(e.getId(), e.getCourseId(), courseTitle, e.getTitle(), e.getDescription(),
                    e.getDurationMinutes(), e.getTotalPoints(), e.getPassPercent(), examQuestions.countByTenantIdAndExamId(tenantId, e.getId()),
                    e.getPdfKey(), e.getStartAt(), e.getEndAt(), window(e, now), a == null ? "NOT_STARTED" : a.getStatus(),
                    done ? a.getScore() : null, done ? a.getMaxScore() : null, pct, pct == null ? null : pct >= e.getPassPercent(),
                    a == null ? null : a.getStartedAt(), a == null ? null : a.getSubmittedAt(), a != null && a.isNeedsManualGrade(),
                    canReview(e, a, now), e.isFullscreen(), e.isDetectTabSwitch(), e.isDisableCopy(), a == null ? null : a.getId(), now));
        }
        cards.sort(Comparator.comparing((StudentExamCard c) -> switch (c.window()) { case "OPEN" -> 0; case "UPCOMING" -> 1; default -> 2; })
                .thenComparing(c -> c.startAt() == null ? Instant.EPOCH : c.startAt(), Comparator.reverseOrder()));
        return cards;
    }

    // ================= Taking an exam =================

    @Transactional
    public AttemptView startAttempt(Long examId, Long studentId) {
        Long tenantId = TenantContext.require();
        Exam exam = exams.findByTenantIdAndId(tenantId, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        Instant now = Instant.now();
        if (!"PUBLISHED".equals(exam.getStatus())) throw new BadRequestException("CLOSED".equals(exam.getStatus()) ? "أُغلق هذا الامتحان" : "الامتحان غير منشور");
        if (exam.getStartAt() != null && now.isBefore(exam.getStartAt())) throw new BadRequestException("لم يبدأ موعد الامتحان بعد");
        if (exam.getEndAt() != null && now.isAfter(exam.getEndAt())) throw new BadRequestException("انتهى موعد الامتحان");
        if (exam.getPdfKey() != null && examQuestions.countByTenantIdAndExamId(tenantId, examId) == 0) throw new BadRequestException("هذا الامتحان ورقي؛ حمّل الملف من بطاقة الامتحان");
        if (exam.getCourseId() != null && enrollments.findByTenantIdAndStudentIdAndCourseId(tenantId, studentId, exam.getCourseId())
                .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).isEmpty()) {
            throw new ForbiddenException("الطالب غير مسجل في كورس الامتحان");
        }
        StudentExam attempt = studentExams.findByTenantIdAndExamIdAndStudentId(tenantId, examId, studentId)
                .orElseGet(() -> {
                    StudentExam se = new StudentExam();
                    se.setTenantId(tenantId);
                    se.setExamId(examId);
                    se.setStudentId(studentId);
                    List<Long> order = new ArrayList<>(
                            examQuestions.findByTenantIdAndExamIdOrderByPosition(tenantId, examId).stream()
                                    .map(ExamQuestion::getQuestionId).toList());
                    if (exam.isShuffleQuestions()) Collections.shuffle(order);
                    se.setQuestionOrder(Json.write(order));
                    se.setStatus("IN_PROGRESS");
                    se.setStartedAt(java.time.Instant.now());
                    se.setMaxScore(exam.getTotalPoints());
                    return studentExams.save(se);
                });
        if ("GRADED".equals(attempt.getStatus()) || "SUBMITTED".equals(attempt.getStatus())) {
            throw new BadRequestException("لقد قمت بتسليم هذا الامتحان بالفعل");
        }
        return buildAttemptView(exam, attempt);
    }

    private AttemptView buildAttemptView(Exam exam, StudentExam attempt) {
        Long tenantId = exam.getTenantId();
        Map<Long, Double> points = pointsMap(exam);
        List<Long> order = Json.readLongList(attempt.getQuestionOrder());
        List<TakeQuestion> tqs = new ArrayList<>();
        for (Long qid : order) {
            Question q = questions.findByTenantIdAndId(tenantId, qid).orElse(null);
            if (q == null) continue;
            List<TakeOption> opts = new ArrayList<>();
            if (needsOptions(q.getType())) {
                var ol = new ArrayList<>(options.findByTenantIdAndQuestionIdOrderByPosition(tenantId, qid));
                if (exam.isShuffleOptions() && !"TRUE_FALSE".equals(q.getType())) Collections.shuffle(ol);
                for (var o : ol) opts.add(new TakeOption(o.getId(), o.getText()));
            }
            tqs.add(new TakeQuestion(qid, q.getType(), q.getStem(), points.getOrDefault(qid, q.getPoints()), opts, q.getImageKey()));
        }
        return new AttemptView(attempt.getId(), exam.getId(), exam.getTitle(), exam.getDurationMinutes(),
                attempt.getStatus(), exam.isFullscreen(), exam.isDisableCopy(), exam.isDetectTabSwitch(),
                attempt.getStartedAt(), tqs, expiresAt(exam, attempt), Instant.now(), draftAnswers(attempt), attempt.getDraftSavedAt(),
                exam.getDescription());
    }

    private Instant expiresAt(Exam exam, StudentExam attempt) {
        Instant limit = attempt.getStartedAt().plus(exam.getDurationMinutes(), ChronoUnit.MINUTES);
        return exam.getEndAt() != null && exam.getEndAt().isBefore(limit) ? exam.getEndAt() : limit;
    }

    private List<SubmitAnswer> draftAnswers(StudentExam attempt) {
        return attempt.getDraftAnswers() == null ? List.of() : Json.read(attempt.getDraftAnswers(),
                new com.fasterxml.jackson.core.type.TypeReference<List<SubmitAnswer>>() {});
    }

    private List<IntegrityEvent> integrityEvents(StudentExam attempt) {
        return attempt.getIntegrityEvents() == null ? List.of() : Json.read(attempt.getIntegrityEvents(),
                new com.fasterxml.jackson.core.type.TypeReference<List<IntegrityEvent>>() {});
    }

    /** Merges client-reported events into the attempt, ignoring unknown types and duplicates, capped. */
    private void mergeEvents(StudentExam attempt, List<IntegrityEvent> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        List<IntegrityEvent> merged = new ArrayList<>(integrityEvents(attempt));
        Set<String> seen = new HashSet<>();
        for (IntegrityEvent e : merged) seen.add(e.type() + "@" + e.at());
        for (IntegrityEvent e : incoming) {
            if (e == null || e.type() == null || e.at() == null || !EVENT_TYPES.contains(e.type())) continue;
            if (merged.size() >= MAX_EVENTS) break;
            if (seen.add(e.type() + "@" + e.at())) merged.add(e);
        }
        merged.sort(Comparator.comparing(IntegrityEvent::at));
        attempt.setIntegrityEvents(Json.write(merged));
        attempt.setFullscreenExits((int) merged.stream().filter(e -> "FULLSCREEN_EXIT".equals(e.type())).count());
    }

    private void validateAnswers(StudentExam attempt, SubmitRequest req) {
        Set<Long> allowed = new HashSet<>(Json.readLongList(attempt.getQuestionOrder()));
        Set<Long> seen = new HashSet<>();
        if (req.answers() == null) return;
        if (req.answers().size() > allowed.size()) throw new BadRequestException("عدد إجابات غير صالح");
        for (SubmitAnswer a : req.answers()) {
            if (a == null || !allowed.contains(a.questionId()) || !seen.add(a.questionId()))
                throw new BadRequestException("الإجابة لا تنتمي لهذه المحاولة أو مكررة");
            if (a.answerText() != null && a.answerText().length() > 20000) throw new BadRequestException("الإجابة طويلة جداً");
            if (a.selectedOptions() != null && !a.selectedOptions().isEmpty()) {
                Set<Long> valid = options.findByTenantIdAndQuestionIdOrderByPosition(attempt.getTenantId(), a.questionId())
                        .stream().map(o -> o.getId()).collect(java.util.stream.Collectors.toSet());
                Question q = questions.findByTenantIdAndId(attempt.getTenantId(), a.questionId()).orElseThrow();
                if (!valid.containsAll(a.selectedOptions()) || new HashSet<>(a.selectedOptions()).size() != a.selectedOptions().size()
                        || (!"MULTI_SELECT".equals(q.getType()) && a.selectedOptions().size() > 1))
                    throw new BadRequestException("اختيارات غير صالحة للسؤال");
            }
        }
    }

    @Transactional
    public DraftView saveDraft(Long id, Long studentId, SubmitRequest req) {
        StudentExam a = studentExams.findByTenantIdAndId(TenantContext.require(), id)
                .orElseThrow(() -> NotFoundException.of("المحاولة", id));
        if (!a.getStudentId().equals(studentId)) throw new ForbiddenException("محاولة غير متاحة");
        Exam exam = exams.findByTenantIdAndId(a.getTenantId(), a.getExamId()).orElseThrow();
        if (!"IN_PROGRESS".equals(a.getStatus()) || !Instant.now().isBefore(expiresAt(exam, a)))
            throw new BadRequestException("انتهى وقت تعديل الإجابات");
        validateAnswers(a, req);
        a.setDraftAnswers(Json.write(req.answers() == null ? List.of() : req.answers()));
        a.setDraftSavedAt(Instant.now());
        a.setTabSwitches(Math.max(a.getTabSwitches(), Math.min(10000, Math.max(0, req.tabSwitches() == null ? 0 : req.tabSwitches()))));
        mergeEvents(a, req.events());
        studentExams.save(a);
        return new DraftView(a.getDraftSavedAt(), expiresAt(exam, a));
    }

    public List<ReviewAnswer> reviewAnswers(Long id) {
        Long tenant = TenantContext.require();
        StudentExam a = studentExams.findByTenantIdAndId(tenant, id).orElseThrow(() -> NotFoundException.of("المحاولة", id));
        if ("IN_PROGRESS".equals(a.getStatus())) throw new BadRequestException("لم يسلم الطالب بعد");
        Exam exam = exams.findByTenantIdAndId(tenant, a.getExamId()).orElseThrow();
        Map<Long, Double> points = pointsMap(exam);
        List<ReviewAnswer> out = new ArrayList<>();
        Map<Long, StudentAnswer> byQ = new HashMap<>();
        studentAnswers.findByTenantIdAndStudentExamId(tenant, id).forEach(sa -> byQ.put(sa.getQuestionId(), sa));
        for (Long qid : Json.readLongList(a.getQuestionOrder())) {
            StudentAnswer sa = byQ.get(qid);
            Question q = questions.findByTenantIdAndId(tenant, qid).orElse(null);
            if (q == null || sa == null) continue;
            List<Long> selected = Json.readLongList(sa.getSelectedOptions());
            var labels = options.findByTenantIdAndQuestionIdOrderByPosition(tenant, q.getId()).stream()
                    .filter(o -> selected.contains(o.getId())).map(o -> o.getText()).toList();
            out.add(new ReviewAnswer(q.getId(), q.getStem(), q.getType(), sa.getAnswerText(), labels,
                    points.getOrDefault(q.getId(), q.getPoints()), sa.getAwardedPoints(), sa.getCorrect(), sa.getFeedback()));
        }
        return out;
    }

    public IntegrityReport integrity(Long id) {
        Long tenant = TenantContext.require();
        StudentExam a = studentExams.findByTenantIdAndId(tenant, id).orElseThrow(() -> NotFoundException.of("المحاولة", id));
        String name = students.findByTenantIdAndId(tenant, a.getStudentId()).map(s -> s.getFullName()).orElse("طالب");
        Long duration = a.getStartedAt() == null ? null
                : ChronoUnit.SECONDS.between(a.getStartedAt(), a.getSubmittedAt() != null ? a.getSubmittedAt() : Instant.now());
        return new IntegrityReport(a.getId(), name, a.getStartedAt(), a.getSubmittedAt(), duration, a.getTabSwitches(),
                a.getFullscreenExits(), integrityEvents(a));
    }

    /** The student's own answer review, gated by the exam's review policy (Moodle "review options"). */
    public StudentReview studentReview(Long examId, Long studentId) {
        Long tenant = TenantContext.require();
        Exam exam = exams.findByTenantIdAndId(tenant, examId).orElseThrow(() -> NotFoundException.of("الامتحان", examId));
        StudentExam a = studentExams.findByTenantIdAndExamIdAndStudentId(tenant, examId, studentId)
                .orElseThrow(() -> new BadRequestException("لم تبدأ هذا الامتحان بعد"));
        if ("IN_PROGRESS".equals(a.getStatus())) throw new BadRequestException("سلّم الامتحان أولاً");
        if (!canReview(exam, a, Instant.now()))
            throw new ForbiddenException("AFTER_CLOSE".equals(exam.getShowResults()) ? "تظهر مراجعة الإجابات بعد إغلاق الامتحان لكل الطلاب" : "المدرس لم يتح مراجعة الإجابات لهذا الامتحان");
        boolean showKey = exam.isShowCorrectAnswers();
        Map<Long, Double> points = pointsMap(exam);
        Map<Long, StudentAnswer> byQ = new HashMap<>();
        studentAnswers.findByTenantIdAndStudentExamId(tenant, a.getId()).forEach(sa -> byQ.put(sa.getQuestionId(), sa));
        List<StudentReviewAnswer> answers = new ArrayList<>();
        for (Long qid : Json.readLongList(a.getQuestionOrder())) {
            Question q = questions.findByTenantIdAndId(tenant, qid).orElse(null);
            StudentAnswer sa = byQ.get(qid);
            if (q == null) continue;
            List<Long> selected = sa == null ? List.of() : Json.readLongList(sa.getSelectedOptions());
            List<ReviewOption> opts = options.findByTenantIdAndQuestionIdOrderByPosition(tenant, qid).stream()
                    .map(o -> new ReviewOption(o.getId(), o.getText(), showKey ? o.isCorrect() : null, selected.contains(o.getId()))).toList();
            answers.add(new StudentReviewAnswer(qid, q.getStem(), q.getType(), points.getOrDefault(qid, q.getPoints()),
                    sa == null ? 0 : sa.getAwardedPoints(), sa == null ? Boolean.FALSE : sa.getCorrect(),
                    sa == null ? null : sa.getAnswerText(), opts,
                    showKey && !needsOptions(q.getType()) ? q.getCorrectAnswer() : null,
                    showKey ? q.getExplanation() : null, sa == null ? null : sa.getFeedback(), q.getImageKey()));
        }
        double pct = a.getMaxScore() > 0 ? round1(a.getScore() / a.getMaxScore() * 100) : 0;
        return new StudentReview(examId, exam.getTitle(), a.getScore(), a.getMaxScore(), pct, pct >= exam.getPassPercent(),
                a.isNeedsManualGrade(), showKey, a.getSubmittedAt(), answers);
    }

    @Transactional
    public AttemptResult submit(Long studentExamId, Long studentId, SubmitRequest req) {
        Long tenantId = TenantContext.require();
        StudentExam attempt = studentExams.findByTenantIdAndId(tenantId, studentExamId)
                .orElseThrow(() -> NotFoundException.of("المحاولة", studentExamId));
        if (!attempt.getStudentId().equals(studentId)) {
            throw new ForbiddenException("لا يمكن تسليم محاولة طالب آخر");
        }
        if ("GRADED".equals(attempt.getStatus()) || "SUBMITTED".equals(attempt.getStatus())) {
            Exam completedExam = exams.findByTenantIdAndId(tenantId, attempt.getExamId()).orElseThrow();
            Map<Long, Double> p = pointsMap(completedExam);
            List<QuestionResult> saved = studentAnswers.findByTenantIdAndStudentExamId(tenantId, studentExamId).stream()
                    .map(a -> new QuestionResult(a.getQuestionId(), a.getCorrect(), a.getAwardedPoints(),
                            p.getOrDefault(a.getQuestionId(), questions.findByTenantIdAndId(tenantId, a.getQuestionId()).orElseThrow().getPoints()))).toList();
            double percent = attempt.getMaxScore() > 0 ? round1(attempt.getScore()/attempt.getMaxScore()*100) : 0;
            return new AttemptResult(attempt.getScore(),attempt.getMaxScore(),percent,percent>=completedExam.getPassPercent(),attempt.isNeedsManualGrade(),attempt.getTabSwitches(),saved,
                    canReview(completedExam, attempt, Instant.now()));
        }
        Exam exam = exams.findByTenantIdAndId(tenantId, attempt.getExamId()).orElseThrow();
        Instant now = Instant.now();
        mergeEvents(attempt, req.events());
        // Late finalization uses only the last server-accepted draft, never late answers.
        if (!now.isBefore(expiresAt(exam, attempt))) req = new SubmitRequest(draftAnswers(attempt), attempt.getTabSwitches());
        validateAnswers(attempt, req);
        if (req.tabSwitches() != null && (req.tabSwitches() < 0 || req.tabSwitches() > 10000)) {
            throw new BadRequestException("عدد مرات مغادرة صفحة الامتحان غير صالح");
        }
        Map<Long, Double> pointsByQ = pointsMap(exam);
        double total = 0;
        double maxTotal = 0;
        boolean needsManual = false;
        List<QuestionResult> results = new ArrayList<>();
        Map<Long, SubmitAnswer> byQ = new HashMap<>();
        if (req.answers() != null) for (SubmitAnswer a : req.answers()) byQ.put(a.questionId(), a);

        for (Long qid : Json.readLongList(attempt.getQuestionOrder())) {
            Question q = questions.findByTenantIdAndId(tenantId, qid).orElse(null);
            if (q == null) continue;
            double points = pointsByQ.getOrDefault(qid, q.getPoints());
            maxTotal += points;
            SubmitAnswer ans = byQ.get(qid);
            Grade g = grade(tenantId, q, ans, points);
            total += g.awarded;
            if (g.correct == null) needsManual = true;

            StudentAnswer sa = studentAnswers.findByTenantIdAndStudentExamIdAndQuestionId(tenantId, studentExamId, qid)
                    .orElseGet(StudentAnswer::new);
            sa.setTenantId(tenantId);
            sa.setStudentExamId(studentExamId);
            sa.setQuestionId(qid);
            sa.setAnswerText(ans == null ? null : ans.answerText());
            sa.setSelectedOptions(ans == null || ans.selectedOptions() == null ? null : Json.write(ans.selectedOptions()));
            sa.setCorrect(g.correct);
            sa.setAwardedPoints(g.awarded);
            studentAnswers.save(sa);
            results.add(new QuestionResult(qid, g.correct, g.awarded, points));
        }

        attempt.setScore(round1(total));
        // Authoritative max = sum of points for the questions this student actually saw, so a
        // mid-attempt exam edit can't desync the stored score from the reported percentage.
        attempt.setMaxScore(round1(maxTotal));
        attempt.setTabSwitches(Math.max(attempt.getTabSwitches(), req.tabSwitches() != null ? req.tabSwitches() : 0));
        attempt.setNeedsManualGrade(needsManual);
        attempt.setStatus(needsManual ? "SUBMITTED" : "GRADED");
        attempt.setSubmittedAt(java.time.Instant.now());
        studentExams.save(attempt);

        publishScore(exam, attempt);

        double percent = maxTotal > 0 ? round1(total / maxTotal * 100) : 0;
        return new AttemptResult(round1(total), round1(maxTotal), percent,
                percent >= exam.getPassPercent(), needsManual, attempt.getTabSwitches(), results, canReview(exam, attempt, Instant.now()));
    }

    @Transactional
    public void manualGrade(Long studentExamId, ManualGradeRequest req) {
        Long tenantId = TenantContext.require();
        StudentExam attempt = studentExams.findByTenantIdAndId(tenantId, studentExamId)
                .orElseThrow(() -> NotFoundException.of("المحاولة", studentExamId));
        if ("IN_PROGRESS".equals(attempt.getStatus())) throw new BadRequestException("لم يسلم الطالب بعد");
        StudentAnswer sa = studentAnswers.findByTenantIdAndStudentExamIdAndQuestionId(tenantId, studentExamId, req.questionId())
                .orElseThrow(() -> NotFoundException.of("الإجابة", req.questionId()));
        Exam examForPoints = exams.findByTenantIdAndId(tenantId, attempt.getExamId()).orElseThrow();
        double maxPoints = pointsMap(examForPoints).getOrDefault(req.questionId(),
                questions.findByTenantIdAndId(tenantId, req.questionId()).orElseThrow().getPoints());
        if (!Double.isFinite(req.points()) || req.points() < 0 || req.points() > maxPoints) throw new BadRequestException("الدرجة خارج النطاق المسموح");
        if (req.feedback() != null && req.feedback().length() > 5000) throw new BadRequestException("الملاحظة أطول من الحد المسموح");
        sa.setAwardedPoints(req.points());
        sa.setCorrect(req.points() > 0);
        sa.setFeedback(nn(req.feedback()));
        studentAnswers.save(sa);

        double total = studentAnswers.findByTenantIdAndStudentExamId(tenantId, studentExamId).stream()
                .mapToDouble(StudentAnswer::getAwardedPoints).sum();
        attempt.setScore(round1(total));
        boolean pending = studentAnswers.findByTenantIdAndStudentExamId(tenantId, studentExamId).stream().anyMatch(answer -> answer.getCorrect() == null);
        attempt.setNeedsManualGrade(pending);
        attempt.setStatus(pending ? "SUBMITTED" : "GRADED");
        studentExams.save(attempt);
        Exam exam = exams.findByTenantIdAndId(tenantId, attempt.getExamId()).orElseThrow();
        publishScore(exam, attempt);
    }

    /** Student catalog contains metadata only, never questions or answer keys. */
    public List<ExamSummary> availableForStudent(Long studentId) {
        Long tenantId = TenantContext.require();
        Set<Long> enrolled = enrollments.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus()))
                .map(e -> e.getCourseId()).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now();
        return exams.findByTenantId(tenantId).stream()
                .filter(e -> "PUBLISHED".equals(e.getStatus()))
                .filter(e -> e.getCourseId() != null && enrolled.contains(e.getCourseId()))
                .filter(e -> e.getStartAt() == null || !now.isBefore(e.getStartAt()))
                .filter(e -> e.getEndAt() == null || !now.isAfter(e.getEndAt()))
                .map(this::toExamSummary).toList();
    }

    public Long courseIdForAttempt(Long studentExamId) {
        Long tenantId = TenantContext.require();
        StudentExam attempt = studentExams.findByTenantIdAndId(tenantId, studentExamId)
                .orElseThrow(() -> NotFoundException.of("المحاولة", studentExamId));
        return exams.findByTenantIdAndId(tenantId, attempt.getExamId())
                .orElseThrow(() -> NotFoundException.of("الامتحان", attempt.getExamId())).getCourseId();
    }

    private void publishScore(Exam exam, StudentExam attempt) {
        Long tenantId = exam.getTenantId();
        events.publishEvent(new DomainEvents.ExamSubmitted(tenantId, attempt.getStudentId(), exam.getId(),
                exam.getTitle(), attempt.getScore(), attempt.getMaxScore()));
        // ScoreRecorded drives the gradebook + metrics + risk chain (ScoreListener), in that order.
        events.publishEvent(new DomainEvents.ScoreRecorded(tenantId, attempt.getStudentId(), exam.getCourseId(),
                exam.getTitle(), "EXAM", attempt.getScore(), attempt.getMaxScore(), "EXAM", exam.getId()));
    }

    // ================= Grading =================

    private record Grade(Boolean correct, double awarded) {
    }

    private Grade grade(Long tenantId, Question q, SubmitAnswer ans, double points) {
        if (ans == null) return new Grade(false, 0);
        switch (q.getType()) {
            case "MCQ", "TRUE_FALSE" -> {
                Long sel = ans.selectedOptions() == null || ans.selectedOptions().isEmpty() ? null : ans.selectedOptions().get(0);
                if (sel == null) return new Grade(false, 0);
                boolean ok = options.findByTenantIdAndQuestionIdOrderByPosition(tenantId, q.getId()).stream()
                        .anyMatch(o -> o.getId().equals(sel) && o.isCorrect());
                return new Grade(ok, ok ? points : 0);
            }
            case "MULTI_SELECT" -> {
                Set<Long> correct = new HashSet<>();
                options.findByTenantIdAndQuestionIdOrderByPosition(tenantId, q.getId())
                        .forEach(o -> { if (o.isCorrect()) correct.add(o.getId()); });
                Set<Long> chosen = new HashSet<>(ans.selectedOptions() == null ? List.of() : ans.selectedOptions());
                boolean ok = !correct.isEmpty() && correct.equals(chosen);
                return new Grade(ok, ok ? points : 0);
            }
            case "FILL_BLANK", "SHORT_ANSWER" -> {
                boolean ok = q.getCorrectAnswer() != null && ans.answerText() != null
                        && acceptable(q.getCorrectAnswer()).contains(normalize(ans.answerText()));
                return new Grade(ok, ok ? points : 0);
            }
            case "NUMERIC" -> {
                try {
                    double expected = Double.parseDouble(normalizeDigits(q.getCorrectAnswer()).trim());
                    double got = Double.parseDouble(normalizeDigits(ans.answerText()).trim());
                    boolean ok = Math.abs(expected - got) < 1e-6;
                    return new Grade(ok, ok ? points : 0);
                } catch (Exception e) {
                    return new Grade(false, 0);
                }
            }
            default -> {
                return new Grade(null, 0); // ESSAY -> manual
            }
        }
    }

    /** A model answer may list alternatives separated by "|" — any one of them earns the points. */
    private static Set<String> acceptable(String correctAnswer) {
        Set<String> set = new HashSet<>();
        for (String part : correctAnswer.split("\\|")) if (!part.isBlank()) set.add(normalize(part));
        return set;
    }

    private static boolean needsOptions(String type) {
        return "MCQ".equals(type) || "TRUE_FALSE".equals(type) || "MULTI_SELECT".equals(type);
    }

    /** Single-query map of question id -> points override (only questions that override); callers
     *  fall back to the question's own points via getOrDefault. Avoids the previous O(n²) lookup. */
    private Map<Long, Double> pointsMap(Exam exam) {
        Map<Long, Double> map = new HashMap<>();
        for (ExamQuestion eq : examQuestions.findByTenantIdAndExamIdOrderByPosition(exam.getTenantId(), exam.getId())) {
            if (eq.getPointsOverride() != null) {
                map.put(eq.getQuestionId(), eq.getPointsOverride());
            }
        }
        return map;
    }

    private ExamSummary toExamSummary(Exam e) {
        String courseTitle = e.getCourseId() == null ? null
                : courses.findByTenantIdAndId(e.getTenantId(), e.getCourseId()).map(c -> c.getTitle()).orElse(null);
        long qc = examQuestions.countByTenantIdAndExamId(e.getTenantId(), e.getId());
        var attempts = studentExams.findByTenantIdAndExamId(e.getTenantId(), e.getId());
        return new ExamSummary(e.getId(), e.getCourseId(), courseTitle, e.getTitle(), e.getDurationMinutes(),
                e.getTotalPoints(), e.getPassPercent(), e.getStatus(), qc, e.getPdfKey(), e.getStartAt(), e.getEndAt(),
                attempts.size(), attempts.stream().filter(StudentExam::isNeedsManualGrade).count(), e.getShowResults());
    }

    private QuestionView toQuestionView(Question q, boolean includeAnswers) {
        List<OptionView> ovs = options.findByTenantIdAndQuestionIdOrderByPosition(q.getTenantId(), q.getId()).stream()
                .map(o -> new OptionView(o.getId(), o.getText(), includeAnswers ? o.isCorrect() : null, o.getPosition()))
                .toList();
        return new QuestionView(q.getId(), q.getSubject(), q.getChapter(), q.getLesson(), q.getDifficulty(),
                q.getType(), q.getStem(), q.getPoints(), includeAnswers ? q.getCorrectAnswer() : null,
                q.getLearningObjective(), q.getTags(), ovs, includeAnswers ? q.getExplanation() : null,
                examQuestions.findByTenantIdAndQuestionId(q.getTenantId(), q.getId()).size(), q.getImageKey());
    }

    /** Case, whitespace, Arabic diacritics/hamza variants and Arabic-Indic digits are ignored when comparing. */
    private static String normalize(String s) {
        if (s == null) return "";
        String n = normalizeDigits(s).trim().toLowerCase().replaceAll("\\s+", " ");
        n = n.replaceAll("[\\u064B-\\u0652\\u0640]", "");
        n = n.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي');
        return n;
    }

    private static String normalizeDigits(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '٠' && c <= '٩') b.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') b.append((char) ('0' + (c - '۰')));
            else if (c == '٫') b.append('.');
            else b.append(c);
        }
        return b.toString();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String nn(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static void requireFileKey(String key, Long tenantId, String folder) {
        if (key == null || key.isBlank()) return;
        if (!key.startsWith("t" + tenantId + "/" + folder + "/") || key.contains("..")) {
            throw new BadRequestException("ملف غير صالح");
        }
    }
}
