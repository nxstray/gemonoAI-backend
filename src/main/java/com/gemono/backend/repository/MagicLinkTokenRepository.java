package com.gemono.backend.repository;

import com.gemono.backend.model.MagicLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

    Optional<MagicLinkToken> findByTokenAndConsumedFalse(String token);

    // Invalidate all unused tokens for an email before issuing a new one
    @Modifying
    @Transactional
    @Query("UPDATE MagicLinkToken m SET m.consumed = true WHERE m.email = :email AND m.consumed = false")
    void invalidateAllForEmail(String email);

    // Cleanup expired tokens — can be scheduled
    @Modifying
    @Transactional
    @Query("DELETE FROM MagicLinkToken m WHERE m.expiresAt < :now OR m.consumed = true")
    void deleteExpiredAndConsumed(LocalDateTime now);
}