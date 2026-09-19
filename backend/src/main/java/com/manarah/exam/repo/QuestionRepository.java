package com.manarah.exam.repo;

import com.manarah.exam.domain.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByTenantIdAndId(Long tenantId, Long id);

    Optional<Question> findByTenantIdAndImageKey(Long tenantId, String imageKey);

    long countByTenantId(Long tenantId);

    /**
     * {@code ownerId} narrows to one author ("my questions only"). {@code scoped}=1 limits a teacher to
     * their own questions plus the subjects they actually teach, so one teacher's bank does not spill
     * into another's; pass {@code scoped}=0 for admins who legitimately see the whole tenant bank.
     * {@code subjects} must never be empty — pass a placeholder when the teacher has no courses yet.
     */
    @Query("""
            SELECT q FROM Question q
            WHERE q.tenantId = :tenantId
              AND (:subject IS NULL OR q.subject = :subject)
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
              AND (:type IS NULL OR q.type = :type)
              AND (CAST(:q AS string) IS NULL OR LOWER(q.stem) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:ownerId IS NULL OR q.createdBy = :ownerId)
              AND (:scoped = 0 OR q.createdBy = :actorId OR q.subject IN :subjects)
            """)
    Page<Question> search(@Param("tenantId") Long tenantId,
                          @Param("subject") String subject,
                          @Param("difficulty") String difficulty,
                          @Param("type") String type,
                          @Param("q") String q,
                          @Param("ownerId") Long ownerId,
                          @Param("scoped") int scoped,
                          @Param("actorId") Long actorId,
                          @Param("subjects") List<String> subjects,
                          Pageable pageable);

    /** Auto-generation pool: bank questions matching subject+difficulty. */
    List<Question> findByTenantIdAndSubjectAndDifficulty(Long tenantId, String subject, String difficulty);
}
