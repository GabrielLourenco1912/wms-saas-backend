package dev.lourencogabriel.wmssaas.task.repository;

import dev.lourencogabriel.wmssaas.task.model.OperationalDivergence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface OperationalDivergenceRepository extends JpaRepository<OperationalDivergence, String>, JpaSpecificationExecutor<OperationalDivergence> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OperationalDivergence e where e.id = :id")
    Optional<OperationalDivergence> findByIdForUpdate(@Param("id") String id);

    default Optional<OperationalDivergence> lockById(String id) { return findByIdForUpdate(id); }

    List<OperationalDivergence> findByTaskId(String taskId);

    Page<OperationalDivergence> findByStatus(String status, Pageable pageable);
}
