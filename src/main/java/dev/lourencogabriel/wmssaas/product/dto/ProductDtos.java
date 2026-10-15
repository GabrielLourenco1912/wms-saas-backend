package dev.lourencogabriel.wmssaas.product.dto;

import dev.lourencogabriel.wmssaas.product.model.Category;
import dev.lourencogabriel.wmssaas.product.model.Product;
import dev.lourencogabriel.wmssaas.product.model.ProductPackaging;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {
    private ProductDtos() {}

    public record CategoryRequest(@NotBlank @Size(max=100) String name,
            @Size(max=4000) String description, @NotNull Boolean active) {}

    public record ProductRequest(@NotBlank @Size(min=26,max=26) String categoryId,
            @NotBlank @Size(max=50) String sku, @NotBlank @Size(max=150) String name,
            @NotNull Boolean lotControlled, @NotNull Boolean expiryControlled,
            @Min(0) Integer shelfLifeDays, @NotBlank @Size(max=10) String baseUnit,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal unitWeightKg,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal unitVolumeM3,
            @NotNull Boolean active) {}

    public record PackagingRequest(@NotBlank @Size(max=20) String type,
            @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal conversionFactor,
            @Size(max=100) String barcode,
            @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal weightKg,
            @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal volumeM3,
            @NotNull Boolean active) {}

    public record CategoryResponse(String id, String name, String description, Boolean active) {
        public static CategoryResponse from(Category e) { return new CategoryResponse(e.getId(), e.getName(), e.getDescription(), e.getActive()); }
    }

    public record ProductResponse(String id, String categoryId, String sku, String name, Boolean lotControlled, Boolean expiryControlled, Integer shelfLifeDays, Instant createdAt, Boolean active, Instant updatedAt, String baseUnit, BigDecimal unitWeightKg, BigDecimal unitVolumeM3) {
        public static ProductResponse from(Product e) { return new ProductResponse(e.getId(), e.getCategoryId(), e.getSku(), e.getName(), e.getLotControlled(), e.getExpiryControlled(), e.getShelfLifeDays(), e.getCreatedAt(), e.getActive(), e.getUpdatedAt(), e.getBaseUnit(), e.getUnitWeightKg(), e.getUnitVolumeM3()); }
    }

    public record ProductPackagingResponse(String id, String productId, String type, BigDecimal conversionFactor, String barcode, BigDecimal weightKg, BigDecimal volumeM3, Boolean active) {
        public static ProductPackagingResponse from(ProductPackaging e) { return new ProductPackagingResponse(e.getId(), e.getProductId(), e.getType(), e.getConversionFactor(), e.getBarcode(), e.getWeightKg(), e.getVolumeM3(), e.getActive()); }
    }
}
