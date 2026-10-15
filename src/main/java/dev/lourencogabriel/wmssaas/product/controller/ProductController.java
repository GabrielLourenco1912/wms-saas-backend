package dev.lourencogabriel.wmssaas.product.controller;

import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.product.dto.ProductDtos;
import dev.lourencogabriel.wmssaas.product.service.ProductService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @GetMapping({"/categories", "/search/categories"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductDtos.CategoryResponse>> categories(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.categories(query, active).map(ProductDtos.CategoryResponse::from)), "Category list");
    }

    @GetMapping("/lookups/categories")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> categoryLookup(@Valid @ModelAttribute LookupQuery query,
            @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.categories(query, active)
                .map(c -> new LookupOption(c.getId(), c.getName()))), "Category options");
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<ProductDtos.CategoryResponse> getCategory(@PathVariable String id) { return new ApiResponse<>(ProductDtos.CategoryResponse.from(service.get(Category.class,id)),"Category"); }

    @PostMapping("/categories")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<ProductDtos.CategoryResponse>> createCategory(@Valid @RequestBody ProductDtos.CategoryRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                ProductDtos.CategoryResponse.from(service.category(null,body)), "Category created", HttpStatus.CREATED));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_EDIT')")
    public ApiResponse<ProductDtos.CategoryResponse> updateCategory(@PathVariable String id,@Valid @RequestBody ProductDtos.CategoryRequest body) { return new ApiResponse<>(ProductDtos.CategoryResponse.from(service.category(id,body)),"Category updated"); }

    @GetMapping({"/products", "/search/products"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductDtos.ProductResponse>> products(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) String categoryId, @RequestParam(required = false) String sku,
            @RequestParam(required = false) String barcode, @RequestParam(required = false) Boolean lotControlled,
            @RequestParam(required = false) Boolean expiryControlled, @RequestParam(required = false) Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.products(query, categoryId, sku, barcode, lotControlled, expiryControlled, active)
                .map(ProductDtos.ProductResponse::from)), "Product list");
    }

    @GetMapping("/lookups/products")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> productLookup(@Valid @ModelAttribute LookupQuery query,
            @RequestParam(required = false) String categoryId, @RequestParam(required = false) String sku,
            @RequestParam(required = false) String barcode, @RequestParam(required = false) Boolean lotControlled,
            @RequestParam(required = false) Boolean expiryControlled, @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.products(query, categoryId, sku, barcode, lotControlled, expiryControlled, active)
                .map(p -> new LookupOption(p.getId(), p.getSku() + " — " + p.getName()))), "Product options");
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<ProductDtos.ProductResponse> getProduct(@PathVariable String id) { return new ApiResponse<>(ProductDtos.ProductResponse.from(service.get(Product.class,id)),"Product"); }

    @PostMapping("/products")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<ProductDtos.ProductResponse>> createProduct(@Valid @RequestBody ProductDtos.ProductRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                ProductDtos.ProductResponse.from(service.product(null,body)), "Product created", HttpStatus.CREATED));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_EDIT')")
    public ApiResponse<ProductDtos.ProductResponse> updateProduct(@PathVariable String id,@Valid @RequestBody ProductDtos.ProductRequest body) { return new ApiResponse<>(ProductDtos.ProductResponse.from(service.product(id,body)),"Product updated"); }

    @GetMapping("/products/{productId}/packages")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductDtos.ProductPackagingResponse>> packages(@PathVariable String productId,
            @Valid @ModelAttribute PageQuery query, @RequestParam(required = false) String type,
            @RequestParam(required = false) String barcode, @RequestParam(required = false) Boolean active) {
        return packagePage(query, productId, type, barcode, active);
    }

    @GetMapping("/search/product-packages")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductDtos.ProductPackagingResponse>> packageSearch(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) String productId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String barcode, @RequestParam(required = false) Boolean active) {
        return packagePage(query, productId, type, barcode, active);
    }

    @GetMapping("/lookups/product-packages")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> packageLookup(@Valid @ModelAttribute LookupQuery query,
            @RequestParam(required = false) String productId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String barcode, @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.packageLookup(query, productId, type, barcode, active)), "Packaging options");
    }

    private ApiResponse<PageResponse<ProductDtos.ProductPackagingResponse>> packagePage(PageQuery query, String productId,
            String type, String barcode, Boolean active) {
        return new ApiResponse<>(PageResponse.from(service.packages(query, productId, type, barcode, active)
                .map(ProductDtos.ProductPackagingResponse::from)), "Product packages");
    }

    @PostMapping("/products/{productId}/packages")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<ProductDtos.ProductPackagingResponse>> createPackaging(@PathVariable String productId,@Valid @RequestBody ProductDtos.PackagingRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                ProductDtos.ProductPackagingResponse.from(service.packaging(productId,null,body)), "Packaging created", HttpStatus.CREATED));
    }

    @PutMapping("/products/{productId}/packages/{packageId}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_EDIT')")
    public ApiResponse<ProductDtos.ProductPackagingResponse> updatePackaging(@PathVariable String productId,@PathVariable String packageId,@Valid @RequestBody ProductDtos.PackagingRequest body) { return new ApiResponse<>(ProductDtos.ProductPackagingResponse.from(service.packaging(productId,packageId,body)),"Packaging updated"); }
}
