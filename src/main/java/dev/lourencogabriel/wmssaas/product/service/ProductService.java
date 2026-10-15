package dev.lourencogabriel.wmssaas.product.service;

import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.product.repository.*;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.product.dto.ProductDtos;
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
public class ProductService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductPackagingRepository packages;
    private final StockRepository stocks;
    private final EntityManager em;

    public Page<Category> categories(Pageable p) { return categories.findAll(page(p)); }

    public Page<Product> products(Pageable p) { return products.findAll(page(p)); }

    public List<ProductPackaging> packages(String productId) {
        get(Product.class,productId);return packages.findAll().stream().filter(x->productId.equals(x.getProductId())).toList();
    }

    @Transactional
    public Category category(String id,ProductDtos.CategoryRequest r) {
        Category e=id==null?new Category():lock(Category.class,id);e.setName(r.name().trim());e.setDescription(normalized(r.description()));e.setActive(r.active());
        return categories.saveAndFlush(e);
    }

    @Transactional
    public Product product(String id,ProductDtos.ProductRequest r) {
        Category category=get(Category.class,r.categoryId());active(category.getActive(),"Category");
        Product e=id==null?new Product():lock(Product.class,id);
        require(products.findAll().stream().noneMatch(x->!Objects.equals(x.getId(),id)&&r.sku().trim().equalsIgnoreCase(x.getSku())),"SKU is already used");
        require(packages.findAll().stream().noneMatch(x->r.sku().trim().equalsIgnoreCase(x.getBarcode())&&!Objects.equals(x.getProductId(),id)),"SKU conflicts with another product barcode");
        boolean hasStock=id!=null&&stocks.findAll().stream().anyMatch(x->id.equals(x.getProductId()));
        if(hasStock) {
            require(r.baseUnit().trim().equals(e.getBaseUnit())&&r.unitWeightKg().compareTo(e.getUnitWeightKg())==0&&r.unitVolumeM3().compareTo(e.getUnitVolumeM3())==0
                    &&r.lotControlled().equals(e.getLotControlled())&&r.expiryControlled().equals(e.getExpiryControlled()),"Referenced product unit, physical parameters and traceability cannot change");
        }
        e.setCategoryId(category.getId());e.setSku(r.sku().trim());e.setName(r.name().trim());e.setLotControlled(r.lotControlled());e.setExpiryControlled(r.expiryControlled());e.setShelfLifeDays(r.shelfLifeDays());e.setBaseUnit(r.baseUnit().trim());e.setUnitWeightKg(r.unitWeightKg());e.setUnitVolumeM3(r.unitVolumeM3());e.setActive(r.active());
        return products.saveAndFlush(e);
    }

    @Transactional
    public ProductPackaging packaging(String productId,String id,ProductDtos.PackagingRequest r) {
        Product p=lock(Product.class,productId);active(p.getActive(),"Product");
        ProductPackaging e=id==null?new ProductPackaging():lock(ProductPackaging.class,id);
        require(id==null||productId.equals(e.getProductId()),"Packaging does not belong to this product");
        String barcode=normalized(r.barcode());
        if(barcode!=null){
            require(packages.findAll().stream().noneMatch(x->!Objects.equals(x.getId(),id)&&barcode.equalsIgnoreCase(x.getBarcode())),"Barcode is already used");
            require(products.findAll().stream().noneMatch(x->!productId.equals(x.getId())&&barcode.equalsIgnoreCase(x.getSku())),"Barcode conflicts with another SKU");
        }
        if(id!=null)require(r.conversionFactor().compareTo(e.getConversionFactor())==0,"Create another packaging instead of changing an existing conversion factor");
        e.setProductId(productId);e.setType(r.type().trim());e.setConversionFactor(r.conversionFactor());e.setBarcode(barcode);e.setWeightKg(r.weightKg());e.setVolumeM3(r.volumeM3());e.setActive(r.active());
        return packages.saveAndFlush(e);
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
