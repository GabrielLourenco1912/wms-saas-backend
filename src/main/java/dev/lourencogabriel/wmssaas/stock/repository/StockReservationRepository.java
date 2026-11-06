package dev.lourencogabriel.wmssaas.stock.repository;

import dev.lourencogabriel.wmssaas.stock.model.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.math.BigDecimal;

public interface StockReservationRepository extends JpaRepository<StockReservation, String>, JpaSpecificationExecutor<StockReservation> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StockReservation e where e.id = :id")
    Optional<StockReservation> findByIdForUpdate(@Param("id") String id);

    default Optional<StockReservation> lockById(String id) { return findByIdForUpdate(id); }

    Optional<StockReservation> findByTaskId(String taskId);

    List<StockReservation> findByStockId(String stockId);

    List<StockReservation> findByStockIdAndStatus(String stockId, String status);

    @Query("select coalesce(sum(r.reservedQuantity - r.consumedQuantity - r.releasedQuantity), 0) from StockReservation r where r.stockId = :stockId and r.status = 'ATIVA'")
    BigDecimal sumActiveRemainingByStockId(@Param("stockId") String stockId);

    List<StockReservation> findByOrderItemIdAndStatus(String orderItemId, String status);

    List<StockReservation> findByOrderItemId(String orderItemId);

    List<StockReservation> findByStatus(String status);
}
