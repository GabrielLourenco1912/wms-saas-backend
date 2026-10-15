package dev.lourencogabriel.wmssaas.order.repository;

import dev.lourencogabriel.wmssaas.order.model.OrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface OrderItemRepository extends JpaRepository<OrderItem, String>, JpaSpecificationExecutor<OrderItem> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderItem e where e.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") String id);

    default Optional<OrderItem> lockById(String id) { return findByIdForUpdate(id); }

    List<OrderItem> findByOrderId(String orderId);

    List<OrderItem> findByOrderIdIn(Collection<String> orderIds);

    Optional<OrderItem> findByOrderIdAndProductId(String orderId, String productId);
}
