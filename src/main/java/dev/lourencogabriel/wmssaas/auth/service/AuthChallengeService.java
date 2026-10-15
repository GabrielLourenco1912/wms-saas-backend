package dev.lourencogabriel.wmssaas.auth.service;

import dev.lourencogabriel.wmssaas.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.wmssaas.auth.exception.InvalidAuthChallengeException;
import dev.lourencogabriel.wmssaas.auth.model.AuthChallenge;
import dev.lourencogabriel.wmssaas.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.wmssaas.auth.repository.AuthChallengeRepository;
import dev.lourencogabriel.wmssaas.mail.EmailMessage;
import dev.lourencogabriel.wmssaas.mail.EmailSender;
import dev.lourencogabriel.wmssaas.mail.EmailTemplateRenderer;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthChallengeService {

    private static final int CODE_BOUND = 1_000_000;
    private static final int NONCE_BYTES = 16;
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    private final AuthChallengeRepository challengeRepository;
    private final EmailSender emailSender;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final SecretKey secretKey;
    private final Clock clock;
    private final AccountSecurityService accountSecurityService;
    private final Duration challengeTtl;
    private final int maxAttempts;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthChallengeService(
            AuthChallengeRepository challengeRepository,
            EmailSender emailSender,
            EmailTemplateRenderer emailTemplateRenderer,
            SecretKey secretKey,
            Clock clock,
            AccountSecurityService accountSecurityService,
            @Value("${api.security.challenge.ttl:10m}") Duration challengeTtl,
            @Value("${api.security.challenge.max-attempts:5}") int maxAttempts
    ) {
        if (challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalArgumentException("Authentication challenge TTL must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Authentication challenge max attempts must be positive");
        }
        this.challengeRepository = challengeRepository;
        this.emailSender = emailSender;
        this.emailTemplateRenderer = emailTemplateRenderer;
        this.secretKey = secretKey;
        this.clock = clock;
        this.accountSecurityService = accountSecurityService;
        this.challengeTtl = challengeTtl;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public AuthChallengeResponse issue(
            User user,
            AuthChallengePurpose purpose,
            String userAgent,
            String ipAddress
    ) {
        Instant now = Instant.now(this.clock);
        this.challengeRepository.invalidateActiveForUser(user.getUlid(), now);

        String code = this.generateCode();
        String nonce = this.generateNonce();
        AuthChallenge challenge = new AuthChallenge();
        challenge.setUser(user);
        challenge.setPurpose(purpose);
        challenge.setCodeNonce(nonce);
        challenge.setCodeDigest(this.digest(nonce, code));
        challenge.setTargetEmail(user.getEmail());
        challenge.setExpiresAt(now.plus(this.challengeTtl));
        challenge.setMaxAttempts(this.maxAttempts);
        challenge.setRequestedUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        challenge.setRequestedIpAddress(truncate(ipAddress, MAX_IP_ADDRESS_LENGTH));

        AuthChallenge saved = this.challengeRepository.save(challenge);
        String subject = subject(purpose);
        String textBody = body(user, purpose, code);
        String htmlBody = this.emailTemplateRenderer.render(
                "auth-challenge",
                Map.of(
                        "subject", subject,
                        "preheader", preheader(purpose),
                        "heading", heading(purpose),
                        "recipientName", user.getName(),
                        "action", action(purpose),
                        "code", code,
                        "expirationMinutes", expirationMinutes()
                )
        );
        this.emailSender.send(new EmailMessage(
                challenge.getTargetEmail(),
                subject,
                textBody,
                htmlBody
        ));

        return toResponse(saved);
    }

    @Transactional(noRollbackFor = InvalidAuthChallengeException.class)
    public User verify(String challengeId, String code) {
        Instant now = Instant.now(this.clock);
        AuthChallenge challenge = this.verifyCode(challengeId, code, now);
        User user = challenge.getUser();
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw this.consumeAsInvalid(challenge, now);
        }

        if (challenge.getPurpose() == AuthChallengePurpose.EMAIL_VERIFICATION) {
            if (user.getStatus() != UserAccountStatus.INVITED
                    && user.getStatus() != UserAccountStatus.ACTIVE) {
                throw this.consumeAsInvalid(challenge, now);
            }
            user.setEmailVerifiedAt(now);
            if (user.getStatus() == UserAccountStatus.INVITED) {
                user.setStatus(UserAccountStatus.ACTIVE);
            }
        } else if (challenge.getPurpose() == AuthChallengePurpose.MFA_LOGIN) {
            if (user.getEmailVerifiedAt() == null
                    || user.getStatus() != UserAccountStatus.ACTIVE) {
                throw this.consumeAsInvalid(challenge, now);
            }
        } else {
            throw this.consumeAsInvalid(challenge, now);
        }

        return this.consumeSuccessfully(challenge, now);
    }

    @Transactional(noRollbackFor = InvalidAuthChallengeException.class)
    public User verifyPasswordReset(String challengeId, String code) {
        Instant now = Instant.now(this.clock);
        AuthChallenge challenge = this.verifyCode(challengeId, code, now);
        User user = challenge.getUser();
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw this.consumeAsInvalid(challenge, now);
        }
        if (challenge.getPurpose() != AuthChallengePurpose.PASSWORD_RESET
                || (user.getStatus() != UserAccountStatus.ACTIVE
                && user.getStatus() != UserAccountStatus.INVITED)) {
            throw this.consumeAsInvalid(challenge, now);
        }

        return this.consumeSuccessfully(challenge, now);
    }

    private AuthChallenge verifyCode(String challengeId, String code, Instant now) {
        AuthChallenge challenge = this.challengeRepository.findWithUserById(challengeId)
                .orElseThrow(InvalidAuthChallengeException::new);

        if (challenge.getConsumedAt() != null
                || !challenge.getExpiresAt().isAfter(now)
                || challenge.getFailedAttempts() >= challenge.getMaxAttempts()) {
            if (challenge.getConsumedAt() == null) {
                challenge.setConsumedAt(now);
                this.challengeRepository.save(challenge);
            }
            throw new InvalidAuthChallengeException();
        }

        if (!challenge.getTargetEmail().equalsIgnoreCase(challenge.getUser().getEmail())) {
            throw this.consumeAsInvalid(challenge, now);
        }

        byte[] expected = HexFormat.of().parseHex(challenge.getCodeDigest());
        byte[] actual = HexFormat.of().parseHex(this.digest(challenge.getCodeNonce(), code));
        if (!MessageDigest.isEqual(expected, actual)) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            boolean accountLocked = this.accountSecurityService
                    .recordFailedAuthentication(challenge.getUser());
            if (accountLocked || challenge.getFailedAttempts() >= challenge.getMaxAttempts()) {
                challenge.setConsumedAt(now);
            }
            this.challengeRepository.save(challenge);
            throw new InvalidAuthChallengeException();
        }

        return challenge;
    }

    private User consumeSuccessfully(AuthChallenge challenge, Instant now) {
        this.accountSecurityService.recordSuccessfulAuthentication(challenge.getUser());
        if (challenge.getPurpose() != AuthChallengePurpose.PASSWORD_RESET) {
            challenge.getUser().setLastLoginAt(now);
        }
        challenge.setConsumedAt(now);
        this.challengeRepository.save(challenge);
        return challenge.getUser();
    }

    private InvalidAuthChallengeException consumeAsInvalid(
            AuthChallenge challenge,
            Instant now
    ) {
        challenge.setConsumedAt(now);
        this.challengeRepository.save(challenge);
        return new InvalidAuthChallengeException();
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", this.secureRandom.nextInt(CODE_BOUND));
    }

    private String generateNonce() {
        byte[] nonce = new byte[NONCE_BYTES];
        this.secureRandom.nextBytes(nonce);
        return HexFormat.of().formatHex(nonce);
    }

    private String digest(String nonce, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(this.secretKey);
            byte[] value = (nonce + ':' + code).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is not available", exception);
        }
    }

    private String subject(AuthChallengePurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> "Confirme seu e-mail no WMS";
            case MFA_LOGIN -> "Seu código de acesso ao WMS";
            case PASSWORD_RESET -> "Redefina sua senha no WMS";
        };
    }

    private String body(User user, AuthChallengePurpose purpose, String code) {
        return """
                Olá, %s!

                Use o código abaixo para %s:

                %s

                O código expira em %d minutos e pode ser usado uma única vez.
                Se você não solicitou este código, ignore esta mensagem.
                """.formatted(
                user.getName(),
                action(purpose),
                code,
                expirationMinutes()
        );
    }

    private String action(AuthChallengePurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> "confirmar seu e-mail";
            case MFA_LOGIN -> "concluir seu login";
            case PASSWORD_RESET -> "redefinir sua senha";
        };
    }

    private String heading(AuthChallengePurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> "Confirme seu e-mail";
            case MFA_LOGIN -> "Confirme seu acesso";
            case PASSWORD_RESET -> "Redefina sua senha";
        };
    }

    private String preheader(AuthChallengePurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> "Código para confirmar seu e-mail no WMS.";
            case MFA_LOGIN -> "Código de uso único para acessar o WMS.";
            case PASSWORD_RESET -> "Código para redefinir sua senha no WMS.";
        };
    }

    private long expirationMinutes() {
        return Math.max(1, this.challengeTtl.toMinutes());
    }

    private AuthChallengeResponse toResponse(AuthChallenge challenge) {
        return new AuthChallengeResponse(
                challenge.getId(),
                challenge.getPurpose(),
                maskEmail(challenge.getTargetEmail()),
                challenge.getExpiresAt(),
                challenge.getMaxAttempts()
        );
    }

    private String maskEmail(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return "***";
        }
        String localPart = email.substring(0, separator);
        String visible = localPart.substring(0, 1);
        return visible + "***" + email.substring(separator);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
