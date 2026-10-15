package dev.lourencogabriel.wmssaas.warehouse.repository;

import dev.lourencogabriel.wmssaas.warehouse.model.Warehouse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WarehouseRepository extends JpaRepository<Warehouse, String>, JpaSpecificationExecutor<Warehouse> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Warehouse e where e.id = :id")
    Optional<Warehouse> findByIdForUpdate(@Param("id") String id);

    default Optional<Warehouse> lockById(String id) { return findByIdForUpdate(id); }

    boolean existsByNameIgnoreCase(String name);
}
