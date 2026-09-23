package com.manarah.academy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TeacherBundleMemberRepository extends JpaRepository<TeacherBundleMember, Long> {
    List<TeacherBundleMember> findByBundleIdOrderByPositionAsc(Long bundleId);
    void deleteByBundleId(Long bundleId);
}
