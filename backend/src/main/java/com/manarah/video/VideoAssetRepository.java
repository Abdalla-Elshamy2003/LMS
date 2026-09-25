package com.manarah.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoAssetRepository extends JpaRepository<VideoAsset, Long> {
    Optional<VideoAsset> findFirstByStatusOrderByIdAsc(String status);
    List<VideoAsset> findByStatus(String status);
}
