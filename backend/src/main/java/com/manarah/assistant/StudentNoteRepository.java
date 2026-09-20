package com.manarah.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentNoteRepository extends JpaRepository<StudentNote, Long> {
    List<StudentNote> findByTenantIdAndStudentIdOrderByCreatedAtDesc(Long tenantId, Long studentId);
    List<StudentNote> findByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, String status);
    Optional<StudentNote> findByTenantIdAndId(Long tenantId, Long id);
}
