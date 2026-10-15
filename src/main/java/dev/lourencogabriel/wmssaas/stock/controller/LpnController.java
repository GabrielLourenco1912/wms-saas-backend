package dev.lourencogabriel.wmssaas.stock.controller;

import dev.lourencogabriel.wmssaas.shared.api.*;
import dev.lourencogabriel.wmssaas.stock.dto.LpnResponse;
import dev.lourencogabriel.wmssaas.stock.dto.StockSearchFilters;
import dev.lourencogabriel.wmssaas.stock.service.StockQueryService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("@wmsAuthorization.can(authentication, 'STOCK_VIEW')")
public class LpnController {
    private final StockQueryService queries;
    @GetMapping({"/lpns","/search/lpns"})
    public ApiResponse<PageResponse<LpnResponse>> list(@Valid @ModelAttribute PageQuery query,
            @Valid @ModelAttribute StockSearchFilters.Lpns filters) {
        return new ApiResponse<>(queries.lpns(query,filters),"LPNs");
    }
    @GetMapping("/lpns/{id}")
    public ApiResponse<LpnResponse> detail(@PathVariable String id) { return new ApiResponse<>(queries.lpn(id),"LPN"); }
    @GetMapping("/lookups/lpns")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
            @Valid @ModelAttribute StockSearchFilters.Lpns filters) {
        return new ApiResponse<>(queries.lpnLookup(query,filters),"LPN options");
    }
}
