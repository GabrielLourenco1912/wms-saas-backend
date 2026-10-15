package dev.lourencogabriel.wmssaas.warehouse.service;

import dev.lourencogabriel.wmssaas.warehouse.model.*;
import dev.lourencogabriel.wmssaas.warehouse.repository.*;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.warehouse.dto.WarehouseDtos;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class WarehouseService {
    private final WarehouseRepository warehouses;
    private final ZoneRepository zones;
    private final PositionRepository positions;
    private final StockRepository stocks;
    private final EntityManager em;

    public Page<Warehouse> warehouses(PageQuery query, Boolean active) {
        return warehouses.findAll(SearchSpecifications.<Warehouse>text(query.getQ(), "id", "name", "taxId", "address")
                .and(SearchSpecifications.equal("active", active)), query.toPageable());
    }

    public Page<Zone> zones(PageQuery query, String warehouseId, Boolean active) {
        return zones.findAll(SearchSpecifications.<Zone>text(query.getQ(), "id", "name", "controlType", "warehouseId")
                .and(SearchSpecifications.equal("warehouseId", warehouseId))
                .and(SearchSpecifications.equal("active", active)), query.toPageable());
    }

    public Page<Position> positions(PageQuery query, String zoneId, String warehouseId, String status,
                                    String type, String purpose, Boolean active) {
        var specification = SearchSpecifications.<Position>text(query.getQ(), "id", "code", "type", "status", "purpose", "zoneId")
                .and(SearchSpecifications.equal("zoneId", zoneId))
                .and(SearchSpecifications.equal("status", status))
                .and(SearchSpecifications.equal("type", type))
                .and(SearchSpecifications.equal("purpose", purpose))
                .and(SearchSpecifications.equal("active", active));
        if (warehouseId != null && !warehouseId.isBlank()) {
            specification = specification.and((root, criteria, builder) -> {
                var subquery = criteria.subquery(String.class);
                var zone = subquery.from(Zone.class);
                subquery.select(zone.get("id")).where(builder.equal(zone.get("warehouseId"), warehouseId.trim()));
                return root.get("zoneId").in(subquery);
            });
        }
        return positions.findAll(specification, query.toPageable());
    }

    @Transactional
    public Warehouse warehouse(String id,WarehouseDtos.WarehouseRequest r) {
        Warehouse e=id==null?new Warehouse():lock(Warehouse.class,id);
        e.setName(r.name().trim());e.setTaxId(normalized(r.taxId()));e.setAddress(normalized(r.address()));e.setActive(r.active());
        return warehouses.saveAndFlush(e);
    }

    @Transactional
    public Zone zone(String id,WarehouseDtos.ZoneRequest r) {
        Warehouse w=lock(Warehouse.class,r.warehouseId());active(w.getActive(),"Warehouse");
        Zone e=id==null?new Zone():lock(Zone.class,id);
        if(id!=null&&!r.warehouseId().equals(e.getWarehouseId())) {
            require(!positions.existsByZoneId(id),"A zone with positions cannot change warehouse");
        }
        e.setWarehouseId(w.getId());e.setName(r.name().trim());e.setControlType(normalized(r.controlType()));e.setActive(r.active());
        return zones.saveAndFlush(e);
    }

    @Transactional
    public Position position(String id,WarehouseDtos.PositionRequest r) {
        Zone z=get(Zone.class,r.zoneId());Warehouse w=lock(Warehouse.class,z.getWarehouseId());
        active(z.getActive(),"Zone");active(w.getActive(),"Warehouse");
        Position e=id==null?new Position():lock(Position.class,id);
        boolean duplicateCode = id == null
                ? positions.existsByCodeIgnoreCase(r.code().trim())
                : positions.existsByCodeIgnoreCaseAndIdNot(r.code().trim(), id);
        require(!duplicateCode,"Position code is already used");
        List<Stock> balances=id==null?List.of():stocks.findByPositionId(id);
        if(!balances.isEmpty())require(r.zoneId().equals(e.getZoneId()),"A referenced position cannot change zone");
        if(balances.stream().anyMatch(s->s.getQuantity().signum()>0))require(r.purpose().equals(e.getPurpose()),"An occupied position cannot change purpose");
        BigDecimal weight=BigDecimal.ZERO,volume=BigDecimal.ZERO;
        for(Stock s:balances){Product p=get(Product.class,s.getProductId());weight=weight.add(s.getQuantity().multiply(p.getUnitWeightKg()));volume=volume.add(s.getQuantity().multiply(p.getUnitVolumeM3()));}
        require(r.weightCapacityKg()==null||weight.compareTo(r.weightCapacityKg())<=0,"Capacity is below current physical weight");
        require(r.volumeCapacityM3()==null||volume.compareTo(r.volumeCapacityM3())<=0,"Capacity is below current physical volume");
        e.setZoneId(z.getId());e.setCode(r.code().trim());e.setType(r.type().trim());e.setWeightCapacityKg(r.weightCapacityKg());e.setVolumeCapacityM3(r.volumeCapacityM3());e.setPurpose(r.purpose());e.setActive(r.active());
        if(id==null)e.setStatus("LIVRE");
        return positions.saveAndFlush(e);
    }

    public <T> T get(Class<T> type,String id) {
        T found=em.find(type,id);
        if(found==null)throw new ResourceNotFoundException(type.getSimpleName(),id);
        return found;
    }

    private <T> T lock(Class<T> type,String id) {
        T found=em.find(type,id,LockModeType.PESSIMISTIC_WRITE);
        if(found==null)throw new ResourceNotFoundException(type.getSimpleName(),id);
        return found;
    }

    private static void require(boolean ok,String message) { if(!ok)throw new BusinessRuleException(message); }

    private static String normalized(String s) { return s==null||s.isBlank()?null:s.trim(); }

    private void active(Boolean active,String what) { require(Boolean.TRUE.equals(active),what+" must be active"); }
}
