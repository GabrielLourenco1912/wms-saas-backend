package dev.lourencogabriel.wmssaas.stock.controller;

import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.stock.dto.StockBlockDtos;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.*;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {
    private final StockService service;

    @GetMapping("/stock")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public ApiResponse<PageResponse<StockView>> stock(@RequestParam(required=false) String productId,
            @RequestParam(required=false) String positionId, @RequestParam(required=false) String lpnId,
            @RequestParam(required=false) String warehouseId, Pageable pageable) {
        return new ApiResponse<>(service.findStock(productId,positionId,lpnId,warehouseId,pageable),"Stock balances");
    }
    @GetMapping("/stock/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public ApiResponse<StockView> stock(@PathVariable String id) { return new ApiResponse<>(service.findStock(id),"Stock balance"); }

    @GetMapping("/stock-blocks")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
    public ApiResponse<PageResponse<StockBlockDtos.Response>> blocks(@RequestParam(required=false) String warehouseId,
            @RequestParam(required=false) Boolean active, Pageable pageable) {
        return new ApiResponse<>(service.blocks(warehouseId,active,pageable).map(StockBlockDtos.Response::from),"Stock blocks");
    }
    @PostMapping("/stock/{id}/block")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_BLOCK')")
    public ApiResponse<StockBlockDtos.Response> block(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(StockBlockDtos.Response.from(service.block(id,request,key)),"Stock blocked");
    }
    @PostMapping("/stock/{id}/unblock")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_UNBLOCK')")
    public ApiResponse<StockView> unblock(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) { return new ApiResponse<>(service.unblock(id,request,key),"Stock released"); }

    @PostMapping("/positions/{id}/block")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_BLOCK')")
    public ApiResponse<StockBlockDtos.Response> blockPosition(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(StockBlockDtos.Response.from(service.blockPosition(id,request,key)),"Position blocked");
    }
    @PostMapping("/positions/{id}/unblock")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_UNBLOCK')")
    public ApiResponse<MutationResult> unblockPosition(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) { return new ApiResponse<>(service.unblockPosition(id,request,key),"Position released"); }

    @PostMapping("/stock-blocks/lots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_BLOCK')")
    public ApiResponse<StockBlockDtos.Response> blockLot(@Valid @RequestBody LotBlockRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(StockBlockDtos.Response.from(service.blockLot(request,key)),"Lot blocked",HttpStatus.CREATED);
    }
    @PostMapping("/stock-blocks/{id}/release")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_UNBLOCK')")
    public ApiResponse<MutationResult> release(@PathVariable String id, @Valid @RequestBody ReasonRequest request,
            @RequestHeader("Idempotency-Key") String key) { return new ApiResponse<>(service.releaseBlock(id,request,key),"Block released"); }
}
