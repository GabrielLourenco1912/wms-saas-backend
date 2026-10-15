package dev.lourencogabriel.wmssaas.outbound.repository;

import dev.lourencogabriel.wmssaas.outbound.model.ShipmentItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, String>, JpaSpecificationExecutor<ShipmentItem> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ShipmentItem e where e.id = :id")
    Optional<ShipmentItem> findByIdForUpdate(@Param("id") String id);

    default Optional<ShipmentItem> lockById(String id) { return findByIdForUpdate(id); }

    List<ShipmentItem> findByShipmentId(String shipmentId);

    List<ShipmentItem> findByLpnId(String lpnId);

    List<ShipmentItem> findByStockId(String stockId);

    boolean existsByShipmentIdAndStockId(String shipmentId, String stockId);
}
