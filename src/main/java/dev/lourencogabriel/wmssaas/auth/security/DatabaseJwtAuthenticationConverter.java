package dev.lourencogabriel.wmssaas.auth.security;

import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Read the current profile rather than trusting client headers or stale JWT role claims. */
@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var user = this.userRepository.findById(jwt.getSubject())
                .filter(u -> u.hasActiveAccess() && ActiveUserJwtValidator.matchesVersion(u, jwt))
                .orElseThrow(() -> new InvalidBearerTokenException("Inactive account or profile"));
        return new JwtAuthenticationToken(jwt, user.getAuthorities(), user.getUlid());
    }
}
