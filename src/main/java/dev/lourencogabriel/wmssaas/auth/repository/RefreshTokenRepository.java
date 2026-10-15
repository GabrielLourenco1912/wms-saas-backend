package dev.lourencogabriel.wmssaas.auth.repository;

import dev.lourencogabriel.wmssaas.auth.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      SELECT rt
      FROM RefreshToken rt
      WHERE rt.tokenHash = :tokenHash
      """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.user.ulid = :userId
              and token.revokedAt is null
            """)
    int revokeAllActiveByUserId(
            @Param("userId") String userId,
            @Param("revokedAt") Instant revokedAt
    );
}
