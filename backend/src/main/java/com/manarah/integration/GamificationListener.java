package com.manarah.integration;

import com.manarah.common.events.DomainEvents;
import com.manarah.gamification.GamificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Awards gamification points (§31) as students engage. */
@Component
public class GamificationListener {

    private final GamificationService gamification;

    public GamificationListener(GamificationService gamification) {
        this.gamification = gamification;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScore(DomainEvents.ScoreRecorded e) {
        int pts = (int) Math.round(e.percent() / 5.0); // up to +20 for a perfect score
        gamification.award(e.tenantId(), e.studentId(), pts, "درجة في " + e.title());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHomeworkSubmitted(DomainEvents.HomeworkSubmitted e) {
        gamification.award(e.tenantId(), e.studentId(), e.late() ? 5 : 15, "تسليم واجب");
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExamSubmitted(DomainEvents.ExamSubmitted e) {
        boolean passed = e.maxScore() > 0 && (e.score() / e.maxScore()) >= 0.5;
        gamification.award(e.tenantId(), e.studentId(), passed ? 30 : 5, "أداء امتحان");
    }
}
