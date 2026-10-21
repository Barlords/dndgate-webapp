package fr.barlords.dndgateapi.domain.functional.model.account;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.UUID;

/**
 * One login of an account on one device. Every access token carries the id of its session,
 * and is rejected as soon as the session is revoked or expired.
 */
@Value
@Builder
public class Session {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID accountId;

    @With
    Instant createdAt;

    @With
    Instant expiresAt;

    @With
    Instant revokedAt;

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

}
