package dev.lourencogabriel.wmssaas.outbound.controller;

import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.outbound.service.OutboundService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.outbound.dto.OutboundFilters;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {
    private final OutboundService service;

    @GetMapping({"/orders", "/search/orders"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public ApiResponse<PageResponse<OrderResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute OutboundFilters.Orders filters) {
        return new ApiResponse<>(service.orders(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/orders")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute OutboundFilters.Orders filters) {
        return new ApiResponse<>(service.orderLookups(query, filters), "Opções de seleção");
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public ApiResponse<OrderResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.order(id), "Pedido de saída");
    }

    @PostMapping("/orders")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CREATE')")
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.createOrder(request, OperationService.currentActorId()), "Pedido criado", HttpStatus.CREATED));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_EDIT')")
    public ApiResponse<OrderResponse> update(@PathVariable String id, @Valid @RequestBody OrderRequest request) {
        return new ApiResponse<>(service.updateOrder(id, request), "Pedido atualizado");
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CANCEL')")
    public ApiResponse<OrderResponse> cancel(@PathVariable String id, @Valid @RequestBody CancelRequest request) {
        return new ApiResponse<>(service.cancelOrder(id, request, OperationService.currentActorId()), "Pedido cancelado");
    }
}
