package dev.lourencogabriel.wmssaas.user.controller;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class PermissionController {
    public record PermissionResponse(String code, String functionalRequirement) {}

    @GetMapping("/api/permissions")
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public ApiResponse<?> findAll() {
        return new ApiResponse<>(Arrays.stream(WmsPermission.values())
                .map(p -> new PermissionResponse(p.name(), p.functionalRequirement())).toList(),
                "WMS permissions", HttpStatus.OK);
    }
}
