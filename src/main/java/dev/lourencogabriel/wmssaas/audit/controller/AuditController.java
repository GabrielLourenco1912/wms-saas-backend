package dev.lourencogabriel.wmssaas.audit.controller;

import dev.lourencogabriel.wmssaas.audit.dto.TransactionDtos;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.stock.dto.StockDtos.CompensationRequest;
import dev.lourencogabriel.wmssaas.stock.service.StockService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-transactions")
@RequiredArgsConstructor
public class AuditController {
    private final StockService service;

    @GetMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public ApiResponse<PageResponse<TransactionDtos.Response>> list(@RequestParam(required=false) String stockId,
            @RequestParam(required=false) String productId,@RequestParam(required=false) String operationId,Pageable pageable) {
        return new ApiResponse<>(service.audit(stockId,productId,operationId,pageable).map(TransactionDtos.Response::from),"Stock ledger");
    }
    @GetMapping("/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'AUDIT_VIEW')")
    public ApiResponse<TransactionDtos.Response> detail(@PathVariable String id) {
        return new ApiResponse<>(TransactionDtos.Response.from(service.transaction(id)),"Stock transaction");
    }
    @PostMapping("/{id}/compensations")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'INVENTORY_ADJUST')")
    public ApiResponse<TransactionDtos.Response> compensate(@PathVariable String id, @Valid @RequestBody CompensationRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(TransactionDtos.Response.from(service.compensate(id,request,key)),"Compensating transaction appended");
    }
}
