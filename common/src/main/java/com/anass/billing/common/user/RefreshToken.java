package com.anass.billing.common.user;


import com.anass.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * One issued refresh token. The token string itself is never stored, only its id.
 */

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder

public class RefreshToken extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID jti;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Keeps the first revocation time if called twice.
     */
    public void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }
}
