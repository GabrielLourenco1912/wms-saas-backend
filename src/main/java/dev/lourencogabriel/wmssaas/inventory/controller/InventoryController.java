package dev.lourencogabriel.wmssaas.inventory.controller;

import dev.lourencogabriel.wmssaas.inventory.dto.InventoryDtos.*;
import dev.lourencogabriel.wmssaas.inventory.service.InventoryService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventories")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService service;

    @GetMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public ApiResponse<PageResponse<Summary>> list(@RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String positionId, @RequestParam(required = false) String status, Pageable pageable) {
        return new ApiResponse<>(service.list(warehouseId, positionId, status, pageable), "Inventories");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public ApiResponse<Detail> detail(@PathVariable String id) { return new ApiResponse<>(service.detail(id), "Inventory"); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_EXECUTE')")
    public ApiResponse<Detail> create(@Valid @RequestBody CreateRequest request, @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.create(request, key), "Inventory opened", HttpStatus.CREATED);
    }

    @PostMapping("/{id}/counts")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_EXECUTE')")
    public ApiResponse<Detail> count(@PathVariable String id, @Valid @RequestBody CountsRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.count(id, request, key), "Inventory count recorded");
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public ApiResponse<Detail> adjust(@PathVariable String id, @Valid @RequestBody AdjustRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.adjust(id, request, key), "Inventory adjustments applied");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_EXECUTE') or @wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public ApiResponse<Detail> cancel(@PathVariable String id, @Valid @RequestBody CancelRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.cancel(id, request, key), "Inventory cancelled");
    }
}
