package dev.lourencogabriel.wmssaas.auth.authorization;

import dev.lourencogabriel.wmssaas.auth.security.ActiveUserJwtValidator;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("wmsAuthorization")
@RequiredArgsConstructor
public class WmsAuthorization {
    private final UserRepository userRepository;

    public boolean can(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        WmsPermission required;
        try {
            required = WmsPermission.valueOf(permission);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return this.userRepository.findById(jwt.getSubject())
                .filter(user -> user.hasActiveAccess() && ActiveUserJwtValidator.matchesVersion(user, jwt))
                .map(user -> user.getProfile().getPermissions().contains(required))
                .orElse(false);
    }
}
