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
public class WaveController {
    private final OutboundService service;

    @GetMapping({"/waves", "/search/waves"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public ApiResponse<PageResponse<WaveResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute OutboundFilters.Waves filters) {
        return new ApiResponse<>(service.waves(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/waves")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute OutboundFilters.Waves filters) {
        return new ApiResponse<>(service.waveLookups(query, filters), "Opções de seleção");
    }

    @GetMapping("/waves/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_VIEW')")
    public ApiResponse<WaveResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.wave(id), "Onda de separação");
    }

    @PostMapping("/waves")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_CREATE')")
    public ResponseEntity<ApiResponse<WaveResponse>> create(@Valid @RequestBody WaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                service.createWave(request, OperationService.currentActorId()), "Onda criada", HttpStatus.CREATED));
    }

    @PostMapping("/waves/{id}/cancel")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'WAVE_CANCEL')")
    public ApiResponse<WaveResponse> cancel(@PathVariable String id, @Valid @RequestBody CancelRequest request) {
        return new ApiResponse<>(service.cancelWave(id, request, OperationService.currentActorId()), "Onda cancelada");
    }
}
