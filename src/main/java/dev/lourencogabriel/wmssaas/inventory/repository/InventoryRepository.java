package dev.lourencogabriel.wmssaas.inventory.repository;

import dev.lourencogabriel.wmssaas.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface InventoryRepository extends JpaRepository<Inventory, String>, JpaSpecificationExecutor<Inventory> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Inventory e where e.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") String id);

    default Optional<Inventory> lockById(String id) { return findByIdForUpdate(id); }

    List<Inventory> findByPositionId(String positionId);

    boolean existsByPositionIdAndStatusIn(String positionId, Collection<String> statuses);

    List<Inventory> findByWarehouseId(String warehouseId);
}
