package dev.lourencogabriel.wmssaas.user.controller;

import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.http.AuthRequestResolver;
import dev.lourencogabriel.wmssaas.auth.service.AuthService;
import dev.lourencogabriel.wmssaas.user.dto.UserDtos;
import dev.lourencogabriel.wmssaas.user.service.UserService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthService authService;
    private final AuthRequestResolver requestResolver;

    @GetMapping("/me")
    public ApiResponse<?> current() {
        return new ApiResponse<>(this.userService.current(), "Current user", HttpStatus.OK);
    }

    @GetMapping
    public ApiResponse<?> findAll(Pageable pageable) {
        return new ApiResponse<>(this.userService.findAll(pageable), "Users", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ApiResponse<?> findById(@PathVariable String id) {
        return new ApiResponse<>(this.userService.findById(id), "User", HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody RegisterRequest body,
                                                 HttpServletRequest request) {
        var context = this.requestResolver.resolve(null, request);
        var challenge = this.authService.register(body, context.userAgent(), context.ipAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
                challenge, "User created; email verification required", HttpStatus.CREATED));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @Valid @RequestBody UserDtos.UpdateRequest body) {
        return new ApiResponse<>(this.userService.update(id, body), "User updated", HttpStatus.OK);
    }
}
