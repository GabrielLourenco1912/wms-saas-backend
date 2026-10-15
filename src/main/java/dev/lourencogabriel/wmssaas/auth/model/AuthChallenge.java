package dev.lourencogabriel.wmssaas.auth.model;

import dev.lourencogabriel.wmssaas.shared.persistence.AuditedEntity;
import dev.lourencogabriel.wmssaas.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "auth_challenges",
        indexes = {
                @Index(
                        name = "idx_auth_challenges_user_active",
                        columnList = "user_id,consumed_at,expires_at"
                ),
                @Index(
                        name = "idx_auth_challenges_expiration",
                        columnList = "expires_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AuthChallenge extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private AuthChallengePurpose purpose;

    @Column(name = "code_digest", length = 64, nullable = false)
    private String codeDigest;

    @Column(name = "code_nonce", length = 32, nullable = false)
    private String codeNonce;

    @Column(name = "target_email", length = 254, nullable = false)
    private String targetEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "requested_user_agent", length = 255)
    private String requestedUserAgent;

    @Column(name = "requested_ip_address", length = 45)
    private String requestedIpAddress;

    @Version
    private long version;
}
