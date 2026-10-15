package dev.lourencogabriel.wmssaas.auth.security;

import dev.lourencogabriel.wmssaas.auth.service.TokenService;
import dev.lourencogabriel.wmssaas.user.model.User;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ActiveUserJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCOUNT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The token no longer belongs to an active session",
            null
    );

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        Object tokenVersionClaim = token.getClaim(TokenService.TOKEN_VERSION_CLAIM);
        if (!StringUtils.hasText(subject) || !(tokenVersionClaim instanceof Number tokenVersion)) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
        }

        boolean valid = this.userRepository.findById(subject)
                .filter(User::hasActiveAccess)
                .map(user -> matchesVersion(user, token))
                .orElse(false);

        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
    }

    public static boolean matchesVersion(User user, Jwt token) {
        Object claim = token.getClaim(TokenService.TOKEN_VERSION_CLAIM);
        return claim instanceof Number version && tokenVersion(user) == version.longValue();
    }

    private static long tokenVersion(User user) {
        return user.getTokenVersion() == null ? 0L : user.getTokenVersion();
    }
}
