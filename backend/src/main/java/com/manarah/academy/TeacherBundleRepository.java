package com.manarah.academy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TeacherBundleRepository extends JpaRepository<TeacherBundle, Long> {
    Optional<TeacherBundle> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<TeacherBundle> findByPublishedTrueOrderBySortOrderAscIdAsc();
    List<TeacherBundle> findAllByOrderBySortOrderAscIdAsc();
}
