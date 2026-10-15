package dev.lourencogabriel.wmssaas.user.controller;

import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.user.dto.PermissionDtos;
import dev.lourencogabriel.wmssaas.user.service.PermissionService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService service;

    @GetMapping({"/api/permissions", "/api/search/permissions"})
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public ApiResponse<PageResponse<PermissionDtos.Response>> findAll(@Valid @ModelAttribute PageQuery query,
                                  @RequestParam(required = false) String resource,
                                  @RequestParam(required = false) String functionalRequirement) {
        return new ApiResponse<>(service.findAll(query.getQ(), resource, functionalRequirement,
                query.toPageable("code")), "WMS permissions");
    }

    @GetMapping("/api/lookups/permissions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
                                 @RequestParam(required = false) String resource,
                                 @RequestParam(required = false) String functionalRequirement) {
        return new ApiResponse<>(service.lookup(query.getQ(), resource, functionalRequirement,
                query.toPageable("code")), "WMS permission options");
    }
}
