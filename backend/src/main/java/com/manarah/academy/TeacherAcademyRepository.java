package com.manarah.academy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TeacherAcademyRepository extends JpaRepository<TeacherAcademy, Long> {
    Optional<TeacherAcademy> findByTenantId(Long tenantId);
    Optional<TeacherAcademy> findBySlug(String slug);
    Optional<TeacherAcademy> findByDefaultHomeTrue();
    /** Every space this head office ever made, deleted ones included — for money and history, not for listings. */
    List<TeacherAcademy> findByManagerTenantId(Long tenantId);
    List<TeacherAcademy> findByManagerTenantIdAndArchivedAtIsNull(Long tenantId);
    List<TeacherAcademy> findByOwnerUserIdAndArchivedAtIsNull(Long ownerUserId);
    List<TeacherAcademy> findByPublishedTrueOrderByNameAsc();

    /** The teacher spaces this head office runs today (a deleted teacher is gone from here). */
    default List<TeacherAcademy> managedBy(Long tenantId) {
        return findByManagerTenantIdAndArchivedAtIsNull(tenantId);
    }

    /**
     * Every tenant whose people and courses should roll up into this tenant's own listings: its
     * own, plus one per academy it manages. Each academy lives in its own tenant, so without this
     * an admin who creates a teacher space would never see that teacher or their courses on the
     * staff / courses / dashboard pages — they'd surface only after entering the academy workspace.
     * A tenant that manages nothing gets back just itself, so nothing widens for students,
     * parents or teachers. A deleted teacher's space no longer rolls up.
     */
    default List<Long> visibleTenantIds(Long tenantId) {
        List<Long> ids = new ArrayList<>();
        ids.add(tenantId);
        for (TeacherAcademy a : managedBy(tenantId))
            if (!ids.contains(a.getTenantId())) ids.add(a.getTenantId());
        return ids;
    }
}
