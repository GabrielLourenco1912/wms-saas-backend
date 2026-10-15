package dev.lourencogabriel.wmssaas.product.service;

import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.product.repository.*;
import dev.lourencogabriel.wmssaas.stock.model.Stock;
import dev.lourencogabriel.wmssaas.stock.repository.StockRepository;
import dev.lourencogabriel.wmssaas.product.dto.ProductDtos;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ProductService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductPackagingRepository packages;
    private final StockRepository stocks;
    private final EntityManager em;

    public Page<Category> categories(PageQuery query, Boolean active) {
        return categories.findAll(SearchSpecifications.<Category>text(query.getQ(), "id", "name", "description")
                .and(SearchSpecifications.equal("active", active)), query.toPageable());
    }

    public Page<Product> products(PageQuery query, String categoryId, String sku, String barcode,
                                  Boolean lotControlled, Boolean expiryControlled, Boolean active) {
        Specification<Product> text = SearchSpecifications.text(query.getQ(), "id", "sku", "name", "baseUnit");
        if (query.getQ() != null && !query.getQ().isBlank()) {
            text = text.or((root, criteria, builder) -> {
                var subquery = criteria.subquery(String.class);
                var packaging = subquery.from(ProductPackaging.class);
                subquery.select(packaging.get("productId")).where(SearchSpecifications.<ProductPackaging>text(query.getQ(), "barcode")
                        .toPredicate(packaging, criteria, builder));
                return root.get("id").in(subquery);
            });
        }
        var specification = text.and(SearchSpecifications.equal("categoryId", categoryId))
                .and(SearchSpecifications.equal("sku", sku))
                .and(SearchSpecifications.equal("lotControlled", lotControlled))
                .and(SearchSpecifications.equal("expiryControlled", expiryControlled))
                .and(SearchSpecifications.equal("active", active));
        if (barcode != null && !barcode.isBlank()) {
            specification = specification.and((root, criteria, builder) -> {
                var subquery = criteria.subquery(String.class);
                var packaging = subquery.from(ProductPackaging.class);
                subquery.select(packaging.get("productId")).where(builder.equal(builder.lower(packaging.get("barcode")), barcode.trim().toLowerCase(Locale.ROOT)));
                return root.get("id").in(subquery);
            });
        }
        return products.findAll(specification, query.toPageable());
    }

    public Page<ProductPackaging> packages(PageQuery query, String productId, String type, String barcode, Boolean active) {
        if (productId != null && !productId.isBlank()) get(Product.class, productId.trim());
        return packages.findAll(SearchSpecifications.<ProductPackaging>text(query.getQ(), "id", "productId", "type", "barcode")
                .and(SearchSpecifications.equal("productId", productId))
                .and(SearchSpecifications.equal("type", type))
                .and(SearchSpecifications.equal("barcode", barcode))
                .and(SearchSpecifications.equal("active", active)), query.toPageable());
    }

    public Page<LookupOption> packageLookup(LookupQuery query, String productId, String type, String barcode, Boolean active) {
        Page<ProductPackaging> page = packages(query, productId, type, barcode, active);
        Set<String> productIds = page.getContent().stream().map(ProductPackaging::getProductId).collect(Collectors.toSet());
        Map<String, String> skus = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Product::getSku));
        return page.map(packaging -> new LookupOption(packaging.getId(), skus.get(packaging.getProductId())
                + " — " + packaging.getType() + " × " + packaging.getConversionFactor().stripTrailingZeros().toPlainString()
                + (packaging.getBarcode() == null ? "" : " — " + packaging.getBarcode())));
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
        require(!products.existsSkuConflictIgnoreCase(r.sku().trim(),id),"SKU is already used");
        require(!packages.existsBarcodeForAnotherProductIgnoreCase(r.sku().trim(),id),"SKU conflicts with another product barcode");
        boolean hasStock=id!=null&&!stocks.findByProductId(id).isEmpty();
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
            require(!packages.existsBarcodeConflictIgnoreCase(barcode,id),"Barcode is already used");
            require(!products.existsSkuConflictIgnoreCase(barcode,productId),"Barcode conflicts with another SKU");
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

    private static void require(boolean ok,String message) { if(!ok)throw new BusinessRuleException(message); }

    private static String normalized(String s) { return s==null||s.isBlank()?null:s.trim(); }

    private void active(Boolean active,String what) { require(Boolean.TRUE.equals(active),what+" must be active"); }
}
