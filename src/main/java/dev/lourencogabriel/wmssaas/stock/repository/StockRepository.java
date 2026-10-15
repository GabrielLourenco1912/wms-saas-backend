package dev.lourencogabriel.wmssaas.stock.repository;

import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.warehouse.model.Position;
import dev.lourencogabriel.wmssaas.warehouse.model.Zone;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface StockRepository extends JpaRepository<Stock, String>, JpaSpecificationExecutor<Stock> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Stock e where e.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") String id);

    default Optional<Stock> lockById(String id) { return findByIdForUpdate(id); }

    List<Stock> findByLpnId(String lpnId);

    List<Stock> findByProductId(String productId);

    List<Stock> findAllByProductId(String productId);

    List<Stock> findByPositionId(String positionId);

    List<Stock> findAllByPositionId(String positionId);

    List<Stock> findByPositionIdIn(Collection<String> positionIds);

    List<Stock> findByProductIdAndLot(String productId, String lot);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.lpnId = :lpnId order by s.id")
    List<Stock> findByLpnIdForUpdate(@Param("lpnId") String lpnId);

    @Query("select s from Stock s, Position p, Zone z where s.positionId = p.id and p.zoneId = z.id and z.warehouseId = :warehouseId and s.productId = :productId and s.quantity > 0 and s.blockStatus is null order by case when s.expiryDate is null then 1 else 0 end, s.expiryDate, s.entryAt, s.id")
    List<Stock> findAllocationCandidates(@Param("warehouseId") String warehouseId, @Param("productId") String productId);
}
