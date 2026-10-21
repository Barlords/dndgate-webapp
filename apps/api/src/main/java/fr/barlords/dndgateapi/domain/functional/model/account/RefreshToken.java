package fr.barlords.dndgateapi.domain.functional.model.account;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.UUID;

/**
 * Stored form of a refresh token: only a hash of the secret is kept.
 * A refresh token can be used once; using it again means it was stolen.
 */
@Value
@Builder
public class RefreshToken {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID sessionId;

    @With
    String tokenHash;

    @With
    Instant expiresAt;

    @With
    Instant usedAt;

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

}
