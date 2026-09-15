package com.manarah.academy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TeacherAcademyRepository extends JpaRepository<TeacherAcademy, Long> {
    Optional<TeacherAcademy> findByTenantId(Long tenantId);
    Optional<TeacherAcademy> findBySlug(String slug);
    Optional<TeacherAcademy> findByDefaultHomeTrue();
    List<TeacherAcademy> findByManagerTenantId(Long tenantId);
    List<TeacherAcademy> findByOwnerUserId(Long ownerUserId);
    List<TeacherAcademy> findByPublishedTrueOrderByNameAsc();

    /**
     * Every tenant whose people and courses should roll up into this tenant's own listings: its
     * own, plus one per academy it manages. Each academy lives in its own tenant, so without this
     * an admin who creates a teacher space would never see that teacher or their courses on the
     * staff / courses / dashboard pages — they'd surface only after entering the academy workspace.
     * A tenant that manages nothing gets back just itself, so nothing widens for students,
     * parents or teachers.
     */
    default List<Long> visibleTenantIds(Long tenantId) {
        List<Long> ids = new ArrayList<>();
        ids.add(tenantId);
        for (TeacherAcademy a : findByManagerTenantId(tenantId))
            if (!ids.contains(a.getTenantId())) ids.add(a.getTenantId());
        return ids;
    }
}
