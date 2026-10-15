package dev.lourencogabriel.wmssaas.task.repository;

import dev.lourencogabriel.wmssaas.task.model.WarehouseTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WarehouseTaskRepository extends JpaRepository<WarehouseTask, String>, JpaSpecificationExecutor<WarehouseTask> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from WarehouseTask e where e.id = :id")
    Optional<WarehouseTask> findByIdForUpdate(@Param("id") String id);

    default Optional<WarehouseTask> lockById(String id) { return findByIdForUpdate(id); }

    List<WarehouseTask> findByWaveId(String waveId);

    List<WarehouseTask> findByOrderId(String orderId);

    List<WarehouseTask> findByOrderItemId(String orderItemId);

    List<WarehouseTask> findByLpnId(String lpnId);

    List<WarehouseTask> findBySourceStockId(String sourceStockId);

    List<WarehouseTask> findByOriginTaskId(String originTaskId);

    List<WarehouseTask> findByStatus(String status);

    Page<WarehouseTask> findByType(String type, Pageable pageable);

    List<WarehouseTask> findByAssignedUserId(String assignedUserId);

    boolean existsByOriginPositionIdAndStatusIn(String positionId, Collection<String> statuses);

    boolean existsByDestinationPositionIdAndStatusIn(String positionId, Collection<String> statuses);
}
