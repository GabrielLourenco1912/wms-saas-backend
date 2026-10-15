package dev.lourencogabriel.wmssaas.product.controller;

import dev.lourencogabriel.wmssaas.product.model.*;
import dev.lourencogabriel.wmssaas.product.dto.ProductDtos;
import dev.lourencogabriel.wmssaas.product.service.ProductService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @GetMapping("/categories")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductDtos.CategoryResponse>> categories(Pageable p) { return new ApiResponse<>(PageResponse.from(service.categories(p).map(ProductDtos.CategoryResponse::from)),"Category list"); }

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

    @GetMapping("/products")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductDtos.ProductResponse>> products(Pageable p) { return new ApiResponse<>(PageResponse.from(service.products(p).map(ProductDtos.ProductResponse::from)),"Product list"); }

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
    public ApiResponse<List<ProductDtos.ProductPackagingResponse>> packages(@PathVariable String productId) { return new ApiResponse<>(service.packages(productId).stream().map(ProductDtos.ProductPackagingResponse::from).toList(),"Product packages"); }

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
