package dev.lourencogabriel.wmssaas.outbound.controller;

import dev.lourencogabriel.wmssaas.operation.service.OperationService;
import dev.lourencogabriel.wmssaas.outbound.service.OutboundService;
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
@RequestMapping("/api/waves")
@RequiredArgsConstructor
public class WaveController {
    private final OutboundService service;

    @GetMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public ApiResponse<PageResponse<WaveResponse>> list(Pageable pageable,
            @RequestParam(required = false) String warehouseId, @RequestParam(required = false) String status) {
        return new ApiResponse<>(service.waves(pageable, warehouseId, status), "Ondas de separação");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public ApiResponse<WaveResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.wave(id), "Onda de separação");
    }

    @PostMapping
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_CREATE')")
    public ResponseEntity<ApiResponse<WaveResponse>> create(@Valid @RequestBody WaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.createWave(request, OperationService.currentActorId()), "Onda criada", HttpStatus.CREATED));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_CANCEL')")
    public ApiResponse<WaveResponse> cancel(@PathVariable String id, @Valid @RequestBody CancelRequest request) {
        return new ApiResponse<>(service.cancelWave(id, request, OperationService.currentActorId()), "Onda cancelada");
    }
}
