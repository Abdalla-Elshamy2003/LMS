package com.manarah.security.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface EmailLoginCodeRepository extends JpaRepository<EmailLoginCode, String> {
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:key)", nativeQuery = true)
    int lockEmail(@Param("key") long key);
    long countByEmailAndCreatedAtAfter(String email, Instant after);
    Optional<EmailLoginCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailLoginCode c where c.id = :id")
    Optional<EmailLoginCode> locked(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailLoginCode c where c.proofHash = :hash")
    Optional<EmailLoginCode> proof(@Param("hash") String hash);
}
