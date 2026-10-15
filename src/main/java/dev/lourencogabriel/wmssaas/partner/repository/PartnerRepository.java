package dev.lourencogabriel.wmssaas.partner.repository;

import dev.lourencogabriel.wmssaas.partner.model.Partner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface PartnerRepository extends JpaRepository<Partner, String>, JpaSpecificationExecutor<Partner> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Partner e where e.id = :id")
    Optional<Partner> findByIdForUpdate(@Param("id") String id);

    default Optional<Partner> lockById(String id) { return findByIdForUpdate(id); }

    Optional<Partner> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, String id);
}
