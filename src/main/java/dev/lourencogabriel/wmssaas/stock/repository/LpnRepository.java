package dev.lourencogabriel.wmssaas.stock.repository;

import dev.lourencogabriel.wmssaas.stock.model.Lpn;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface LpnRepository extends JpaRepository<Lpn, String>, JpaSpecificationExecutor<Lpn> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Lpn e where e.id = :id")
    Optional<Lpn> findByIdForUpdate(@Param("id") String id);

    default Optional<Lpn> lockById(String id) { return findByIdForUpdate(id); }

    Optional<Lpn> findByTrackingCode(String trackingCode);

    boolean existsByTrackingCode(String trackingCode);

    List<Lpn> findByPositionId(String positionId);

    List<Lpn> findByOutboundOrderId(String outboundOrderId);
}
