package dev.lourencogabriel.wmssaas.outbound.controller;

import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.outbound.service.ShipmentService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentService service;

    @GetMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_VIEW')")
    public ApiResponse<PageResponse<ShipmentResponse>> list(Pageable pageable,
            @RequestParam(required = false) String orderId, @RequestParam(required = false) String status) {
        return new ApiResponse<>(service.shipments(pageable, orderId, status), "Expedições");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_VIEW')")
    public ApiResponse<ShipmentResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.shipment(id), "Expedição");
    }

    @PostMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_EXECUTE')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> create(@Valid @RequestBody ShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.prepare(request, OperationService.currentActorId()), "Carga preparada para conferência", HttpStatus.CREATED));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_EXECUTE')")
    public ApiResponse<ShipmentResponse> confirm(@PathVariable String id, @Valid @RequestBody ShipmentScanRequest request) {
        return new ApiResponse<>(service.confirm(id, request, OperationService.currentActorId()), "LPN conferido na carga");
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'SHIPMENT_EXECUTE')")
    public ApiResponse<ShipmentResponse> dispatch(@PathVariable String id, @Valid @RequestBody DispatchRequest request) {
        return new ApiResponse<>(service.dispatch(id, request, OperationService.currentActorId()), "Carga despachada");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CANCEL')")
    public ApiResponse<ShipmentResponse> cancel(@PathVariable String id, @Valid @RequestBody CancelRequest request) {
        return new ApiResponse<>(service.cancel(id, request, OperationService.currentActorId()), "Carga cancelada sem baixa de estoque");
    }
}
