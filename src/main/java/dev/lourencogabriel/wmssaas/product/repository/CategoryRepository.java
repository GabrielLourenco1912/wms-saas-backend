package dev.lourencogabriel.wmssaas.product.repository;

import dev.lourencogabriel.wmssaas.product.model.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface CategoryRepository extends JpaRepository<Category, String>, JpaSpecificationExecutor<Category> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Category e where e.id = :id")
    Optional<Category> findByIdForUpdate(@Param("id") String id);

    default Optional<Category> lockById(String id) { return findByIdForUpdate(id); }

    boolean existsByNameIgnoreCase(String name);
}
