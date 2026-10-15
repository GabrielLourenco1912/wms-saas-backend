package dev.lourencogabriel.wmssaas.audit.controller;

import dev.lourencogabriel.wmssaas.audit.dto.TransactionDtos;
import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.stock.dto.StockSearchFilters;
import dev.lourencogabriel.wmssaas.stock.service.StockQueryService;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.CompensationRequest;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuditController {
    private final StockService service;
    private final StockQueryService queries;

    @GetMapping({"/stock-transactions","/search/stock-transactions"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public ApiResponse<PageResponse<TransactionDtos.Response>> list(@Valid @ModelAttribute PageQuery query,
            @Valid @ModelAttribute StockSearchFilters.Ledger filters) {
        return new ApiResponse<>(queries.ledger(query,filters),"stock-transactions");
    }
    @GetMapping("/lookups/stock-transactions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @Valid @ModelAttribute StockSearchFilters.Ledger filters) {
        return new ApiResponse<>(queries.ledgerLookup(query,filters),"stock-transactions options");
    }
    @GetMapping("/stock-transactions/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public ApiResponse<TransactionDtos.Response> detail(@PathVariable String id) {
        return new ApiResponse<>(TransactionDtos.Response.from(service.transaction(id)),"Stock transaction");
    }
    @PostMapping("/stock-transactions/{id}/compensations")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public ApiResponse<TransactionDtos.Response> compensate(@PathVariable String id, @Valid @RequestBody CompensationRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TransactionDtos.Response.from(service.compensate(id,request,key)),"Compensating transaction appended");
    }
}
