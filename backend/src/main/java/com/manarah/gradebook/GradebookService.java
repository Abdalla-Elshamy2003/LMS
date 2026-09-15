package com.manarah.gradebook;

import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.gradebook.domain.GradeItem;
import com.manarah.gradebook.repo.GradeItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GradebookService {

    /** Source tag for a grade a teacher typed in by hand rather than one derived from a scored
     *  exam or homework submission — an oral test, a paper quiz, a participation mark. */
    private static final String MANUAL = "MANUAL";

    private final GradeItemRepository grades;
    private final ApplicationEventPublisher events;
    // Both read grade rows through the repository, never through this service, so there is no cycle.
    private final com.manarah.student.StudentMetricsService metrics;
    private final com.manarah.risk.RiskService risk;

    public GradebookService(GradeItemRepository grades, ApplicationEventPublisher events,
                            com.manarah.student.StudentMetricsService metrics,
                            com.manarah.risk.RiskService risk) {
        this.grades = grades;
        this.events = events;
        this.metrics = metrics;
        this.risk = risk;
    }

    public record ManualGrade(Long studentId, Long courseId, String title, String category,
                              Double score, Double maxScore) {
    }

    /**
     * Records a grade a teacher entered by hand.
     *
     * <p>Publishes {@code ScoreRecorded} rather than writing the row directly: that single event is
     * what drives {@link #upsertFromScore}, the student's average and risk level, and the parent
     * "low score" alert. Saving straight to the repository would produce a grade that shows in the
     * gradebook but never reaches the average, the ranking, or the guardian — the two views of the
     * same student would quietly disagree.
     *
     * <p>Each entry gets its own {@code sourceId} (the row's own id, assigned on the first save)
     * because {@code upsertFromScore} is keyed on it; sharing one would make every new manual grade
     * overwrite the previous one.
     */
    @Transactional
    public GradeItem record(Long tenantId, ManualGrade cmd) {
        if (cmd.studentId() == null) throw new BadRequestException("اختر الطالب");
        if (cmd.title() == null || cmd.title().isBlank()) throw new BadRequestException("اكتب اسم التقييم");
        double max = cmd.maxScore() == null ? 0 : cmd.maxScore();
        double score = cmd.score() == null ? 0 : cmd.score();
        if (max <= 0) throw new BadRequestException("الدرجة النهائية يجب أن تكون أكبر من صفر");
        if (score < 0 || score > max) throw new BadRequestException("الدرجة يجب أن تكون بين صفر و" + fmt(max));

        // Saved first with no sourceId so the database assigns an id we can key the event on.
        GradeItem item = new GradeItem();
        item.setTenantId(tenantId);
        item.setStudentId(cmd.studentId());
        item.setCourseId(cmd.courseId());
        item.setTitle(cmd.title().trim());
        item.setCategory(cmd.category() == null || cmd.category().isBlank() ? "OTHER" : cmd.category().trim());
        item.setScore(score);
        item.setMaxScore(max);
        item.setSourceType(MANUAL);
        item = grades.save(item);
        item.setSourceId(item.getId());
        grades.save(item);

        events.publishEvent(new DomainEvents.ScoreRecorded(tenantId, cmd.studentId(), cmd.courseId(),
                item.getTitle(), item.getCategory(), score, max, MANUAL, item.getId()));
        return item;
    }

    /**
     * Removes a hand-entered grade. Only manual entries can be deleted — a grade that came from a
     * scored exam belongs to that exam, and deleting it here would leave the two out of step.
     *
     * <p>Recomputes afterwards for the same reason {@link #record} publishes an event: the
     * student's average, overall percentage and risk level are all derived from the grade rows, so
     * deleting one without recomputing leaves every one of those figures — and the ranking built
     * on them — counting a grade that no longer exists.
     */
    @Transactional
    public void remove(Long tenantId, Long id) {
        GradeItem item = grades.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> NotFoundException.of("الدرجة", id));
        if (!MANUAL.equals(item.getSourceType()))
            throw new BadRequestException("هذه الدرجة ناتجة عن امتحان أو واجب — عدّلها من مكانها الأصلي");
        Long studentId = item.getStudentId();
        grades.delete(item);
        grades.flush();
        metrics.recompute(tenantId, studentId);
        risk.assess(tenantId, studentId);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Idempotently records a grade from a scored exam/homework (keyed by source), so replays don't double-count. */
    @Transactional
    public void upsertFromScore(DomainEvents.ScoreRecorded e) {
        GradeItem item = grades.findByTenantIdAndSourceTypeAndSourceIdAndStudentId(
                e.tenantId(), e.sourceType(), e.sourceId(), e.studentId()).orElseGet(GradeItem::new);
        item.setTenantId(e.tenantId());
        item.setStudentId(e.studentId());
        item.setCourseId(e.courseId());
        item.setTitle(e.title());
        item.setCategory(e.category());
        item.setScore(e.score());
        item.setMaxScore(e.maxScore());
        item.setSourceType(e.sourceType());
        item.setSourceId(e.sourceId());
        grades.save(item);
    }

    public Map<String, Object> forStudent(Long studentId) {
        Long tenantId = TenantContext.require();
        List<GradeItem> items = grades.findByTenantIdAndStudentId(tenantId, studentId);
        double overall = grades.averagePercent(tenantId, studentId);
        return Map.of("items", items, "overallPercent", Math.round(overall * 10) / 10.0);
    }
}
