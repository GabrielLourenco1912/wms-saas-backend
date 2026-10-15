package dev.lourencogabriel.wmssaas.operation.repository;

import dev.lourencogabriel.wmssaas.operation.model.WmsOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WmsOperationRepository extends JpaRepository<WmsOperation, String>, JpaSpecificationExecutor<WmsOperation> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from WmsOperation e where e.id = :id")
    Optional<WmsOperation> findByIdForUpdate(@Param("id") String id);

    default Optional<WmsOperation> lockById(String id) { return findByIdForUpdate(id); }

    Optional<WmsOperation> findByIdempotencyKey(String idempotencyKey);
}
