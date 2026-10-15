package dev.lourencogabriel.wmssaas.order.repository;

import dev.lourencogabriel.wmssaas.order.model.WarehouseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WarehouseOrderRepository extends JpaRepository<WarehouseOrder, String>, JpaSpecificationExecutor<WarehouseOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from WarehouseOrder e where e.id = :id")
    Optional<WarehouseOrder> findByIdForUpdate(@Param("id") String id);

    default Optional<WarehouseOrder> lockById(String id) { return findByIdForUpdate(id); }

    List<WarehouseOrder> findByWarehouseId(String warehouseId);

    List<WarehouseOrder> findByTypeAndStatus(String type, String status);

    Page<WarehouseOrder> findByType(String type, Pageable pageable);
}
