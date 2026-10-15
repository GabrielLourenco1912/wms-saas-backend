package dev.lourencogabriel.wmssaas.inbound.controller;

import dev.lourencogabriel.wmssaas.inbound.service.InboundService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.inbound.dto.InboundDtos.*;

@RestController
@RequestMapping("/api/pre-receipts")
@RequiredArgsConstructor
public class PreReceiptController {
    private final InboundService service;

    @GetMapping
    public ApiResponse<PageResponse<PreReceiptResponse>> list(@RequestParam(required=false) String warehouseId,
                                                              @RequestParam(required=false) String status, Pageable pageable) {
        return new ApiResponse<>(service.preReceipts(warehouseId,status,pageable),"Pré-recebimentos");
    }
    @GetMapping("/{id}")
    public ApiResponse<PreReceiptDetail> detail(@PathVariable String id) {
        return new ApiResponse<>(service.detail(id),"Pré-recebimento");
    }
    @PostMapping
    public ResponseEntity<ApiResponse<PreReceiptDetail>> create(@Valid @RequestBody PreReceiptRequest request,
                                                               @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(service.create(request,key),"Pré-recebimento criado",HttpStatus.CREATED));
    }
    @PutMapping("/{id}")
    public ApiResponse<PreReceiptDetail> update(@PathVariable String id, @Valid @RequestBody PreReceiptRequest request,
                                                @RequestHeader("Idempotency-Key") String key) {
        return new ApiResponse<>(service.update(id,request,key),"Pré-recebimento atualizado");
    }
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<PreReceiptDetail>> importInvoice(@Valid @RequestBody ImportRequest request,
                                                                      @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(service.importInvoice(request,key),"NF-e importada como expectativa de recebimento",HttpStatus.CREATED));
    }
}
