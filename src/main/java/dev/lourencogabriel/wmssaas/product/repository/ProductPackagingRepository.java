package dev.lourencogabriel.wmssaas.product.repository;

import dev.lourencogabriel.wmssaas.product.model.ProductPackaging;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ProductPackagingRepository extends JpaRepository<ProductPackaging, String>, JpaSpecificationExecutor<ProductPackaging> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProductPackaging e where e.id = :id")
    Optional<ProductPackaging> findByIdForUpdate(@Param("id") String id);

    default Optional<ProductPackaging> lockById(String id) { return findByIdForUpdate(id); }

    List<ProductPackaging> findByProductId(String productId);

    Optional<ProductPackaging> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    @Query("select (count(p) > 0) from ProductPackaging p where lower(p.barcode) = lower(:barcode) and (:excludedId is null or p.id <> :excludedId)")
    boolean existsBarcodeConflictIgnoreCase(@Param("barcode") String barcode, @Param("excludedId") String excludedId);

    @Query("select (count(p) > 0) from ProductPackaging p where lower(p.barcode) = lower(:barcode) and (:productId is null or p.productId <> :productId)")
    boolean existsBarcodeForAnotherProductIgnoreCase(@Param("barcode") String barcode, @Param("productId") String productId);
}
