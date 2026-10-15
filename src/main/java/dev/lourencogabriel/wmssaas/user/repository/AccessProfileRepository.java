package dev.lourencogabriel.wmssaas.user.repository;

import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccessProfileRepository extends JpaRepository<AccessProfile, String>, JpaSpecificationExecutor<AccessProfile> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AccessProfile p where p.code = :code")
    Optional<AccessProfile> findByCodeForUpdate(@Param("code") String code);
}
