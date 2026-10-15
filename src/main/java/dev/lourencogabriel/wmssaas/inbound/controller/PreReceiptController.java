package dev.lourencogabriel.wmssaas.inbound.controller;

import dev.lourencogabriel.wmssaas.inbound.service.InboundService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.inbound.dto.InboundFilters;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PreReceiptController {
    private final InboundService service;

    @GetMapping({"/pre-receipts", "/search/pre-receipts"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_VIEW')")
    public ApiResponse<PageResponse<PreReceiptResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute InboundFilters.Receipts filters) {
        return new ApiResponse<>(service.preReceipts(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/pre-receipts")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PRE_RECEIPT_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute InboundFilters.Receipts filters) {
        return new ApiResponse<>(service.preReceiptLookups(query, filters), "Opções de seleção");
    }
    @GetMapping("/pre-receipts/{id}")
    public ApiResponse<PreReceiptDetail> detail(@PathVariable String id) {
        return new ApiResponse<>(service.detail(id),"Pré-recebimento");
    }
    @PostMapping("/pre-receipts")
    public ResponseEntity<ApiResponse<PreReceiptDetail>> create(@Valid @RequestBody PreReceiptRequest request,
                                                               @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(service.create(request,key),"Pré-recebimento criado",HttpStatus.CREATED));
    }
    @PutMapping("/pre-receipts/{id}")
    public ApiResponse<PreReceiptDetail> update(@PathVariable String id, @Valid @RequestBody PreReceiptRequest request,
                                                @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.update(id,request,key),"Pré-recebimento atualizado");
    }
    @PostMapping("/pre-receipts/import")
    public ResponseEntity<ApiResponse<PreReceiptDetail>> importInvoice(@Valid @RequestBody ImportRequest request,
                                                                      @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(service.importInvoice(request,key),"NF-e importada como expectativa de recebimento",HttpStatus.CREATED));
    }
}
