package com.manarah.student.repo;

import com.manarah.student.domain.StudentGuardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, Long> {
    List<StudentGuardian> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    List<StudentGuardian> findByTenantIdAndGuardianId(Long tenantId, Long guardianId);
}
