package dev.lourencogabriel.wmssaas.inbound.controller;

import dev.lourencogabriel.wmssaas.inbound.service.InboundService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {
    private final InboundService service;

    @GetMapping
    public ApiResponse<PageResponse<PreReceiptResponse>> list(@RequestParam(required=false) String warehouseId,
                                                              @RequestParam(required=false) String status, Pageable pageable) {
        return new ApiResponse<>(service.receipts(warehouseId,status,pageable),"Recebimentos");
    }
    @GetMapping("/{id}")
    public ApiResponse<PreReceiptDetail> detail(@PathVariable String id) {
        return new ApiResponse<>(service.detail(id),"Recebimento e conferências");
    }
    @PostMapping("/{id}/confirm")
    public ApiResponse<ReceiptResult> confirm(@PathVariable String id, @Valid @RequestBody ConfirmReceiptRequest request,
                                              @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.confirm(id,request,key),"Recebimento conferido");
    }
}
