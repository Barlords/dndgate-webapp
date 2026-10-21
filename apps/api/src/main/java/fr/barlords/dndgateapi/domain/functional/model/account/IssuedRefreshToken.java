package fr.barlords.dndgateapi.domain.functional.model.account;

import java.time.Instant;

/**
 * A refresh token as handed to the client. The value is only known at this moment.
 */
public record IssuedRefreshToken(String value, Instant expiresAt) {
}
