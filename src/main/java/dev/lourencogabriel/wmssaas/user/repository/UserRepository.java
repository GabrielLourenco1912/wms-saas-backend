package dev.lourencogabriel.wmssaas.user.repository;

import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    @EntityGraph(attributePaths = {"profile", "profile.permissions"})
    User findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = {"profile", "profile.permissions"})
    Optional<User> findById(String id);

    @Override
    @EntityGraph(attributePaths = "profile")
    Page<User> findAll(Pageable pageable);

    boolean existsByEmail(String email);
    boolean existsByEmailAndUlidNot(String email, String id);
    boolean existsByProfile_Code(String profileCode);
    @EntityGraph(attributePaths = {"profile", "profile.permissions"})
    List<User> findAllByProfile_Code(String profileCode);
    long countByProfile_CodeAndStatusAndEmailVerifiedAtIsNotNull(String profileCode, UserAccountStatus status);

    @EntityGraph(attributePaths = {"profile", "profile.permissions"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @EntityGraph(attributePaths = {"profile", "profile.permissions"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.ulid = :id")
    Optional<User> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findAllByStatusAndLockedUntilLessThanEqual(UserAccountStatus status, Instant lockedUntil);
}
