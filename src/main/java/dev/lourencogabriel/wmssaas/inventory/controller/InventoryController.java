package dev.lourencogabriel.wmssaas.inventory.controller;

import dev.lourencogabriel.wmssaas.inventory.dto.InventoryDtos.*;
import dev.lourencogabriel.wmssaas.inventory.service.InventoryService;
import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.inventory.dto.InventoryFilters;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService service;

    @GetMapping({"/inventories","/search/inventories"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public ApiResponse<PageResponse<Summary>> list(@Valid @ModelAttribute PageQuery query,
            @Valid @ModelAttribute InventoryFilters filters) {
        return new ApiResponse<>(service.search(query,filters),"Inventories");
    }
    @GetMapping("/lookups/inventories")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @Valid @ModelAttribute InventoryFilters filters) {
        return new ApiResponse<>(service.lookup(query,filters),"Inventory options");
    }
    @GetMapping("/inventories/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_VIEW')")
    public ApiResponse<Detail> detail(@PathVariable String id) { return new ApiResponse<>(service.detail(id), "Inventory"); }

    @PostMapping("/inventories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_EXECUTE')")
    public ApiResponse<Detail> create(@Valid @RequestBody CreateRequest request, @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.create(request, key), "Inventory opened", HttpStatus.CREATED);
    }

    @PostMapping("/inventories/{id}/counts")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_EXECUTE')")
    public ApiResponse<Detail> count(@PathVariable String id, @Valid @RequestBody CountsRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.count(id, request, key), "Inventory count recorded");
    }

    @PostMapping("/inventories/{id}/adjust")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public ApiResponse<Detail> adjust(@PathVariable String id, @Valid @RequestBody AdjustRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.adjust(id, request, key), "Inventory adjustments applied");
    }

    @PostMapping("/inventories/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_EXECUTE') or @wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public ApiResponse<Detail> cancel(@PathVariable String id, @Valid @RequestBody CancelRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.cancel(id, request, key), "Inventory cancelled");
    }
}
