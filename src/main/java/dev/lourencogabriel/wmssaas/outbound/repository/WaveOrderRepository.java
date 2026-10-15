package dev.lourencogabriel.wmssaas.outbound.repository;

import dev.lourencogabriel.wmssaas.outbound.model.WaveOrder;
import dev.lourencogabriel.wmssaas.outbound.model.WaveOrderId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WaveOrderRepository extends JpaRepository<WaveOrder, WaveOrderId>, JpaSpecificationExecutor<WaveOrder> {

    List<WaveOrder> findByWaveId(String waveId);

    List<WaveOrder> findByOrderId(String orderId);

    boolean existsByWaveIdAndOrderId(String waveId, String orderId);
}
