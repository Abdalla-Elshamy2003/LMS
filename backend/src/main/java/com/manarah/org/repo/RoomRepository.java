package com.manarah.org.repo;

import com.manarah.org.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByTenantIdOrderByName(Long tenantId);
    List<Room> findByTenantIdAndBranchId(Long tenantId, Long branchId);
    Optional<Room> findByTenantIdAndId(Long tenantId, Long id);
}
