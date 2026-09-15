package com.manarah.gamification;

import com.manarah.common.tenant.TenantContext;
import com.manarah.gamification.domain.PointEvent;
import com.manarah.gamification.domain.StudentPoints;
import com.manarah.gamification.repo.*;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class GamificationService {

    private final StudentPointsRepository points;
    private final PointEventRepository pointEvents;
    private final StudentRepository students;

    public GamificationService(StudentPointsRepository points, PointEventRepository pointEvents,
                               StudentRepository students) {
        this.points = points;
        this.pointEvents = pointEvents;
        this.students = students;
    }

    @Transactional
    public void award(Long tenantId, Long studentId, int amount, String reason) {
        PointEvent ev = new PointEvent();
        ev.setTenantId(tenantId);
        ev.setStudentId(studentId);
        ev.setPoints(amount);
        ev.setReason(reason);
        pointEvents.save(ev);

        StudentPoints sp = points.findByTenantIdAndStudentId(tenantId, studentId).orElseGet(() -> {
            StudentPoints n = new StudentPoints();
            n.setTenantId(tenantId);
            n.setStudentId(studentId);
            return n;
        });
        sp.setPoints(Math.max(0, sp.getPoints() + amount));
        sp.setLevel(StudentPoints.levelFor(sp.getPoints()));
        sp.setUpdatedAt(Instant.now());
        points.save(sp);
    }

    public Map<String, Object> forStudent(Long studentId) {
        Long tenantId = TenantContext.require();
        StudentPoints sp = points.findByTenantIdAndStudentId(tenantId, studentId).orElse(null);
        return Map.of(
                "points", sp == null ? 0 : sp.getPoints(),
                "level", sp == null ? "BRONZE" : sp.getLevel(),
                "recent", pointEvents.findTop20ByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId));
    }

    public List<Map<String, Object>> leaderboard() {
        Long tenantId = TenantContext.require();
        return points.findTop20ByTenantIdOrderByPointsDesc(tenantId).stream()
                .map(sp -> {
                    String name = students.findByTenantIdAndId(tenantId, sp.getStudentId())
                            .map(s -> s.getFullName()).orElse("طالب");
                    return Map.<String, Object>of(
                            "studentId", sp.getStudentId(),
                            "name", name,
                            "points", sp.getPoints(),
                            "level", sp.getLevel());
                })
                .toList();
    }
}
