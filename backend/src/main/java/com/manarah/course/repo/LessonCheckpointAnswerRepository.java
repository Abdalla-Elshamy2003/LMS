package com.manarah.course.repo;

import com.manarah.course.domain.LessonCheckpointAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonCheckpointAnswerRepository extends JpaRepository<LessonCheckpointAnswer, Long> {

    Optional<LessonCheckpointAnswer> findByTenantIdAndCheckpointIdAndStudentId(Long tenantId, Long checkpointId, Long studentId);

    List<LessonCheckpointAnswer> findByTenantIdAndCheckpointIdIn(Long tenantId, List<Long> checkpointIds);

    List<LessonCheckpointAnswer> findByTenantIdAndStudentId(Long tenantId, Long studentId);
}
