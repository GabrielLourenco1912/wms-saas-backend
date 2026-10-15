package dev.lourencogabriel.wmssaas.warehouse.service;

import dev.lourencogabriel.wmssaas.warehouse.model.*;
import dev.lourencogabriel.wmssaas.warehouse.repository.*;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.warehouse.dto.WarehouseDtos;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
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

    public Page<Warehouse> warehouses(Pageable p) { return warehouses.findAll(page(p)); }

    public Page<Zone> zones(String warehouseId, Pageable p) {
        return zones.findAll((r,q,b)->warehouseId==null?b.conjunction():b.equal(r.get("warehouseId"),warehouseId),page(p));
    }

    public Page<Position> positions(String zoneId, Pageable p) {
        return positions.findAll((r,q,b)->zoneId==null?b.conjunction():b.equal(r.get("zoneId"),zoneId),page(p));
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

    private static Pageable page(Pageable p) {
        return PageRequest.of(Math.max(0,p.getPageNumber()),Math.min(200,p.getPageSize()),Sort.by("id"));
    }

    private static void require(boolean ok,String message) { if(!ok)throw new BusinessRuleException(message); }

    private static String normalized(String s) { return s==null||s.isBlank()?null:s.trim(); }

    private void active(Boolean active,String what) { require(Boolean.TRUE.equals(active),what+" must be active"); }
}
