package dev.lourencogabriel.wmssaas.outbound.controller;

import dev.lourencogabriel.wmssaas.outbound.service.PickingService;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.outbound.dto.OutboundFilters;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static dev.lourencogabriel.wmssaas.outbound.dto.OutboundDtos.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
public class DivergenceController {
    private final PickingService service;

    @GetMapping({"/divergences", "/search/divergences"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public ApiResponse<PageResponse<DivergenceResponse>> list(@Valid @ModelAttribute PageQuery query,
            @ModelAttribute OutboundFilters.Divergences filters) {
        return new ApiResponse<>(service.divergences(query, filters), "Consulta paginada");
    }

    @GetMapping("/lookups/divergences")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @ModelAttribute OutboundFilters.Divergences filters) {
        return new ApiResponse<>(service.divergenceLookups(query, filters), "Opções de seleção");
    }

    @GetMapping("/divergences/{id}")
    public ApiResponse<DivergenceResponse> detail(@PathVariable String id) {
        return new ApiResponse<>(service.divergence(id), "Divergência operacional");
    }
}
