package dev.lourencogabriel.wmssaas.outbound.controller;

import dev.lourencogabriel.wmssaas.outbound.service.PickingService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

@RestController
@RequestMapping("/api/divergences")
@RequiredArgsConstructor
@PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
public class DivergenceController {
    private final PickingService service;

    @GetMapping
    public ApiResponse<PageResponse<DivergenceResponse>> list(Pageable pageable, @RequestParam(required = false) String status) {
        return new ApiResponse<>(service.divergences(pageable, status), "Divergências operacionais");
    }

    @GetMapping("/{id}")
    public ApiResponse<DivergenceResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.divergence(id), "Divergência operacional");
    }
}
