package dev.lourencogabriel.wmssaas.partner.controller;

import dev.lourencogabriel.wmssaas.partner.model.*;
import dev.lourencogabriel.wmssaas.partner.dto.PartnerDtos;
import dev.lourencogabriel.wmssaas.partner.service.PartnerService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PartnerController {
    private final PartnerService service;

    @GetMapping("/partners")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public ApiResponse<?> partners(Pageable p) { return new ApiResponse<>(PageResponse.from(service.partners(p).map(PartnerDtos.PartnerResponse::from)),"Partner list"); }

    @GetMapping("/partners/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_VIEW')")
    public ApiResponse<?> getPartner(@PathVariable String id) { return new ApiResponse<>(PartnerDtos.PartnerResponse.from(service.get(Partner.class,id)),"Partner"); }

    @PostMapping("/partners")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_CREATE')")
    public ApiResponse<?> createPartner(@Valid @RequestBody PartnerDtos.PartnerRequest body) { return new ApiResponse<>(PartnerDtos.PartnerResponse.from(service.partner(null,body)),"Partner created",HttpStatus.CREATED); }

    @PutMapping("/partners/{id}")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'ORDER_EDIT')")
    public ApiResponse<?> updatePartner(@PathVariable String id,@Valid @RequestBody PartnerDtos.PartnerRequest body) { return new ApiResponse<>(PartnerDtos.PartnerResponse.from(service.partner(id,body)),"Partner updated"); }
}
