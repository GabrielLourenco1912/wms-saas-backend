package dev.lourencogabriel.wmssaas.user.controller;

import dev.lourencogabriel.wmssaas.user.dto.ProfileDtos;
import dev.lourencogabriel.wmssaas.user.service.AccessProfileService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class AccessProfileController {
    private final AccessProfileService profileService;

    @GetMapping
    public ApiResponse<?> findAll() {
        return new ApiResponse<>(this.profileService.findAll(), "Access profiles", HttpStatus.OK);
    }

    @GetMapping("/{code}")
    public ApiResponse<?> findById(@PathVariable String code) {
        return new ApiResponse<>(this.profileService.findById(code), "Access profile", HttpStatus.OK);
    }

    @PutMapping("/{code}")
    public ApiResponse<?> update(@PathVariable String code, @Valid @RequestBody ProfileDtos.UpdateRequest body) {
        return new ApiResponse<>(this.profileService.update(code, body), "Access profile updated", HttpStatus.OK);
    }
}
