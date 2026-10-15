package dev.lourencogabriel.wmssaas.warehouse.repository;

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

public interface PositionRepository extends JpaRepository<Position, String>, JpaSpecificationExecutor<Position> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Position e where e.id = :id")
    Optional<Position> findByIdForUpdate(@Param("id") String id);

    default Optional<Position> lockById(String id) { return findByIdForUpdate(id); }

    List<Position> findByZoneId(String zoneId);

    List<Position> findAllByZoneId(String zoneId);

    List<Position> findByZoneIdIn(Collection<String> zoneIds);

    boolean existsByZoneIdAndCode(String zoneId, String code);

    Optional<Position> findByZoneIdAndCode(String zoneId, String code);

    @Query("select p from Position p, Zone z where p.zoneId = z.id and z.warehouseId = :warehouseId order by p.code, p.id")
    List<Position> findByWarehouseId(@Param("warehouseId") String warehouseId);
}
