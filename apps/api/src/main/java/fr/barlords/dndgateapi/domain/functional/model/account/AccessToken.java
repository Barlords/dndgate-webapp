package fr.barlords.dndgateapi.domain.functional.model.account;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {
}
