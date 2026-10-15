package dev.lourencogabriel.wmssaas.inventory.repository;

import dev.lourencogabriel.wmssaas.inventory.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, String>, JpaSpecificationExecutor<InventoryItem> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from InventoryItem e where e.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") String id);

    default Optional<InventoryItem> lockById(String id) { return findByIdForUpdate(id); }

    List<InventoryItem> findByInventoryId(String inventoryId);

    Optional<InventoryItem> findByInventoryIdAndStockId(String inventoryId, String stockId);
}
