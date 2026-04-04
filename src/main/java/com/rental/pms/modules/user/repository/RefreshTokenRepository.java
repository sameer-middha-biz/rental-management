package com.rental.pms.modules.user.repository;

import com.rental.pms.modules.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Atomically revokes a refresh token if it is not already revoked.
     * Returns the number of rows updated (0 = already revoked or not found, 1 = successfully revoked).
     * This prevents TOCTOU race conditions during token rotation.
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.tokenHash = :hash AND rt.revoked = false")
    int revokeByTokenHashIfNotRevoked(@Param("hash") String hash);
}
