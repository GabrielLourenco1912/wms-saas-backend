package dev.lourencogabriel.wmssaas.outbound.repository;

import dev.lourencogabriel.wmssaas.outbound.model.Shipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ShipmentRepository extends JpaRepository<Shipment, String>, JpaSpecificationExecutor<Shipment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Shipment e where e.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") String id);

    default Optional<Shipment> lockById(String id) { return findByIdForUpdate(id); }

    List<Shipment> findByOrderId(String orderId);
}
