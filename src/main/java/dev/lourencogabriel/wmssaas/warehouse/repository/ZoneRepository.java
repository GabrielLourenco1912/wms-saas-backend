package dev.lourencogabriel.wmssaas.warehouse.repository;

import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ZoneRepository extends JpaRepository<Zone, String>, JpaSpecificationExecutor<Zone> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Zone e where e.id = :id")
    Optional<Zone> findByIdForUpdate(@Param("id") String id);

    default Optional<Zone> lockById(String id) { return findByIdForUpdate(id); }

    List<Zone> findByWarehouseId(String warehouseId);

    List<Zone> findAllByWarehouseId(String warehouseId);

    boolean existsByWarehouseIdAndNameIgnoreCase(String warehouseId, String name);
}
