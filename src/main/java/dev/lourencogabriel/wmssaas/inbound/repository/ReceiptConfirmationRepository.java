package dev.lourencogabriel.wmssaas.inbound.repository;

import dev.lourencogabriel.wmssaas.inbound.model.ReceiptConfirmation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ReceiptConfirmationRepository extends JpaRepository<ReceiptConfirmation, String>, JpaSpecificationExecutor<ReceiptConfirmation> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ReceiptConfirmation e where e.id = :id")
    Optional<ReceiptConfirmation> findByIdForUpdate(@Param("id") String id);

    default Optional<ReceiptConfirmation> lockById(String id) { return findByIdForUpdate(id); }

    List<ReceiptConfirmation> findByOrderItemId(String orderItemId);

    List<ReceiptConfirmation> findByOperationId(String operationId);

    List<ReceiptConfirmation> findByLpnId(String lpnId);
}
