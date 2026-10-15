package dev.lourencogabriel.wmssaas.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import dev.lourencogabriel.wmssaas.auth.authorization.WmsEndpointPolicy;
import dev.lourencogabriel.wmssaas.auth.http.ApiAccessDeniedHandler;
import dev.lourencogabriel.wmssaas.auth.http.ApiAuthenticationEntryPoint;
import dev.lourencogabriel.wmssaas.auth.security.ActiveUserJwtValidator;
import dev.lourencogabriel.wmssaas.auth.security.DatabaseJwtAuthenticationConverter;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SecretKey secretKey;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final String issuer;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${api.security.jwt.secret}") String jwtSecret,
            @Value("${api.security.jwt.issuer}") String issuer,
            @Value("${api.security.cors.allowed-origins}") List<String> allowedOrigins,
            ApiAuthenticationEntryPoint authenticationEntryPoint
    ) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        this.secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.issuer = issuer;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                    DatabaseJwtAuthenticationConverter converter,
                                    ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)

            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(CorsUtils::isPreFlightRequest).permitAll();
                auth.requestMatchers(HttpMethod.POST,
                        "/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password",
                        "/api/auth/challenge/verify", "/api/auth/refresh", "/api/auth/logout").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/users/me").authenticated();
                for (var rule : WmsEndpointPolicy.RULES) {
                    auth.requestMatchers(rule.method(), rule.pattern()).hasAuthority(rule.permission().authority());
                }
                auth.anyRequest().denyAll();
            })

            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(this.authenticationEntryPoint)
            )

            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(this.authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey secretKey() {
        return this.secretKey;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(this.allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(ActiveUserJwtValidator activeUserJwtValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(this.secretKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(this.issuer),
                activeUserJwtValidator
        ));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        ImmutableSecret<SecurityContext> secret = new ImmutableSecret<>(this.secretKey);

        return new NimbusJwtEncoder(secret);
    }
}
