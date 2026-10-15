package dev.lourencogabriel.wmssaas.audit.repository;

import dev.lourencogabriel.wmssaas.audit.model.StockTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface StockTransactionRepository extends Repository<StockTransaction, String> {

    StockTransaction save(StockTransaction entry);
    Optional<StockTransaction> findById(String id);
    List<StockTransaction> findAll();
    Page<StockTransaction> findAll(Pageable pageable);
    Page<StockTransaction> findAll(Specification<StockTransaction> specification, Pageable pageable);
    long count();

    List<StockTransaction> findByOperationIdOrderBySequenceAsc(String operationId);

    List<StockTransaction> findByStockIdOrderByOccurredAtAsc(String stockId);

    List<StockTransaction> findByCompensatedTransactionId(String compensatedTransactionId);

    List<StockTransaction> findByTaskId(String taskId);

    List<StockTransaction> findByInventoryItemId(String inventoryItemId);

    List<StockTransaction> findByShipmentItemId(String shipmentItemId);
}
