package dev.lourencogabriel.wmssaas.stock.repository;

import dev.lourencogabriel.wmssaas.stock.model.StockBlock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface StockBlockRepository extends JpaRepository<StockBlock, String>, JpaSpecificationExecutor<StockBlock> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StockBlock e where e.id = :id")
    Optional<StockBlock> findByIdForUpdate(@Param("id") String id);

    default Optional<StockBlock> lockById(String id) { return findByIdForUpdate(id); }

    List<StockBlock> findByWarehouseIdAndActive(String warehouseId, Boolean active);

    List<StockBlock> findByStockIdAndActive(String stockId, Boolean active);

    List<StockBlock> findByPositionIdAndActive(String positionId, Boolean active);

    List<StockBlock> findByProductIdAndLotAndActive(String productId, String lot, Boolean active);

    List<StockBlock> findByActive(Boolean active);
}
