package dev.lourencogabriel.wmssaas.auth.repository;

import dev.lourencogabriel.wmssaas.auth.model.AuthChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, String> {

    @EntityGraph(attributePaths = "user")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthChallenge> findWithUserById(String id);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthChallenge challenge
            set challenge.consumedAt = :consumedAt
            where challenge.user.ulid = :userId
              and challenge.consumedAt is null
            """)
    int invalidateActiveForUser(
            @Param("userId") String userId,
            @Param("consumedAt") Instant consumedAt
    );
}
