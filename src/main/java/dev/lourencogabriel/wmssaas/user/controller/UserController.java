package dev.lourencogabriel.wmssaas.user.controller;

import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.http.AuthRequestResolver;
import dev.lourencogabriel.wmssaas.auth.service.AuthService;
import dev.lourencogabriel.wmssaas.user.dto.UserDtos;
import dev.lourencogabriel.wmssaas.user.dto.UserResponse;
import dev.lourencogabriel.wmssaas.user.service.UserService;
import dev.lourencogabriel.wmssaas.shared.api.PageQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupQuery;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthService authService;
    private final AuthRequestResolver requestResolver;

    @GetMapping("/api/users/me")
    public ApiResponse<?> current() {
        return new ApiResponse<>(this.userService.current(), "Current user", HttpStatus.OK);
    }

    @GetMapping({"/api/users", "/api/search/users"})
    public ApiResponse<PageResponse<UserResponse>> findAll(@Valid @ModelAttribute PageQuery query,
                                  @RequestParam(required = false) String profileCode,
                                  @RequestParam(required = false) UserAccountStatus status) {
        return new ApiResponse<>(this.userService.findAll(query.getQ(), profileCode, status,
                query.toPageable("ulid")), "Users", HttpStatus.OK);
    }

    @GetMapping("/api/lookups/users")
    public ApiResponse<PageResponse<LookupOption>> lookup(@Valid @ModelAttribute LookupQuery query,
                                 @RequestParam(required = false) String profileCode,
                                 @RequestParam(defaultValue = "ACTIVE") UserAccountStatus status) {
        return new ApiResponse<>(this.userService.lookup(query.getQ(), profileCode, status,
                query.toPageable("ulid")), "User options");
    }

    @GetMapping("/api/users/{id}")
    public ApiResponse<?> findById(@PathVariable String id) {
        return new ApiResponse<>(this.userService.findById(id), "User", HttpStatus.OK);
    }

    @PostMapping("/api/users")
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody RegisterRequest body,
                                                 HttpServletRequest request) {
        var context = this.requestResolver.resolve(null, request);
        var challenge = this.authService.register(body, context.userAgent(), context.ipAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                challenge, "User created; email verification required", HttpStatus.CREATED));
    }

    @PutMapping("/api/users/{id}")
    public ApiResponse<?> update(@PathVariable String id, @Valid @RequestBody UserDtos.UpdateRequest body) {
        return new ApiResponse<>(this.userService.update(id, body), "User updated", HttpStatus.OK);
    }
}
