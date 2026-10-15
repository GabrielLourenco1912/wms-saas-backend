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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @GetMapping("/categories")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<?> categories(Pageable p) { return new ApiResponse<>(PageResponse.from(service.categories(p).map(ProductDtos.CategoryResponse::from)),"Category list"); }

    @GetMapping("/categories/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<?> getCategory(@PathVariable String id) { return new ApiResponse<>(ProductDtos.CategoryResponse.from(service.get(Category.class,id)),"Category"); }

    @PostMapping("/categories")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_CREATE')")
    public ApiResponse<?> createCategory(@Valid @RequestBody ProductDtos.CategoryRequest body) { return new ApiResponse<>(ProductDtos.CategoryResponse.from(service.category(null,body)),"Category created",HttpStatus.CREATED); }

    @PutMapping("/categories/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_EDIT')")
    public ApiResponse<?> updateCategory(@PathVariable String id,@Valid @RequestBody ProductDtos.CategoryRequest body) { return new ApiResponse<>(ProductDtos.CategoryResponse.from(service.category(id,body)),"Category updated"); }

    @GetMapping("/products")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<?> products(Pageable p) { return new ApiResponse<>(PageResponse.from(service.products(p).map(ProductDtos.ProductResponse::from)),"Product list"); }

    @GetMapping("/products/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<?> getProduct(@PathVariable String id) { return new ApiResponse<>(ProductDtos.ProductResponse.from(service.get(Product.class,id)),"Product"); }

    @PostMapping("/products")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_CREATE')")
    public ApiResponse<?> createProduct(@Valid @RequestBody ProductDtos.ProductRequest body) { return new ApiResponse<>(ProductDtos.ProductResponse.from(service.product(null,body)),"Product created",HttpStatus.CREATED); }

    @PutMapping("/products/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_EDIT')")
    public ApiResponse<?> updateProduct(@PathVariable String id,@Valid @RequestBody ProductDtos.ProductRequest body) { return new ApiResponse<>(ProductDtos.ProductResponse.from(service.product(id,body)),"Product updated"); }

    @GetMapping("/products/{productId}/packages")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_VIEW')")
    public ApiResponse<?> packages(@PathVariable String productId) { return new ApiResponse<>(service.packages(productId).stream().map(ProductDtos.ProductPackagingResponse::from).toList(),"Product packages"); }

    @PostMapping("/products/{productId}/packages")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_CREATE')")
    public ApiResponse<?> createPackaging(@PathVariable String productId,@Valid @RequestBody ProductDtos.PackagingRequest body) { return new ApiResponse<>(ProductDtos.ProductPackagingResponse.from(service.packaging(productId,null,body)),"Packaging created",HttpStatus.CREATED); }

    @PutMapping("/products/{productId}/packages/{packageId}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRODUCT_EDIT')")
    public ApiResponse<?> updatePackaging(@PathVariable String productId,@PathVariable String packageId,@Valid @RequestBody ProductDtos.PackagingRequest body) { return new ApiResponse<>(ProductDtos.ProductPackagingResponse.from(service.packaging(productId,packageId,body)),"Packaging updated"); }
}
