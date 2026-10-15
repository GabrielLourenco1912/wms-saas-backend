package dev.lourencogabriel.wmssaas.product.repository;

import dev.lourencogabriel.wmssaas.product.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Product e where e.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") String id);

    default Optional<Product> lockById(String id) { return findByIdForUpdate(id); }

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Product> findByCategoryId(String categoryId);
}
