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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReceiptController {
    private final InboundService service;

    @GetMapping({"/receipts", "/search/receipts"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'RECEIPT_VIEW')")
    public ApiResponse<PageResponse<PreReceiptResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute InboundFilters.Receipts filters) {
        return new ApiResponse<>(service.receipts(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/receipts")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'RECEIPT_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute InboundFilters.Receipts filters) {
        return new ApiResponse<>(service.receiptLookups(query, filters), "Opções de seleção");
    }
    @GetMapping("/receipts/{id}")
    public ApiResponse<PreReceiptDetail> detail(@PathVariable String id) {
        return new ApiResponse<>(service.detail(id),"Recebimento e conferências");
    }
    @PostMapping("/receipts/{id}/confirm")
    public ApiResponse<ReceiptResult> confirm(@PathVariable String id, @Valid @RequestBody ConfirmReceiptRequest request,
                                              @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.confirm(id,request,key),"Recebimento conferido");
    }
}
