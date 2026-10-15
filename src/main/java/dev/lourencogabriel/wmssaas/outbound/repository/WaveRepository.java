package dev.lourencogabriel.wmssaas.outbound.repository;

import dev.lourencogabriel.wmssaas.outbound.model.Wave;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WaveRepository extends JpaRepository<Wave, String>, JpaSpecificationExecutor<Wave> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Wave e where e.id = :id")
    Optional<Wave> findByIdForUpdate(@Param("id") String id);

    default Optional<Wave> lockById(String id) { return findByIdForUpdate(id); }

    Optional<Wave> findByCode(String code);

    List<Wave> findByWarehouseId(String warehouseId);
}
