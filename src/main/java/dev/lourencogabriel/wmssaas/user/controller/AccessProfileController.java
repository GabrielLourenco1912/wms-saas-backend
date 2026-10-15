package dev.lourencogabriel.wmssaas.user.controller;

import dev.lourencogabriel.wmssaas.user.dto.ProfileDtos;
import dev.lourencogabriel.wmssaas.user.service.AccessProfileService;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AccessProfileController {
    private final AccessProfileService profileService;

    @GetMapping({"/api/profiles", "/api/search/profiles"})
    public ApiResponse<PageResponse<ProfileDtos.Response>> findAll(@Valid @ModelAttribute PageQuery query,
                                  @RequestParam(required = false) Boolean active) {
        return new ApiResponse<>(this.profileService.findAll(query.getQ(), active, query.toPageable("code")),
                "Access profiles", HttpStatus.OK);
    }

    @GetMapping("/api/lookups/profiles")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
                                 @RequestParam(defaultValue = "true") Boolean active) {
        return new ApiResponse<>(this.profileService.lookup(query.getQ(), active, query.toPageable("code")),
                "Access profile options");
    }

    @GetMapping("/api/profiles/{code}")
    public ApiResponse<?> findById(@PathVariable String code) {
        return new ApiResponse<>(this.profileService.findById(code), "Access profile", HttpStatus.OK);
    }

    @PutMapping("/api/profiles/{code}")
    public ApiResponse<?> update(@PathVariable String code, @Valid @RequestBody ProfileDtos.UpdateRequest body) {
        return new ApiResponse<>(this.profileService.update(code, body), "Access profile updated", HttpStatus.OK);
    }
}
