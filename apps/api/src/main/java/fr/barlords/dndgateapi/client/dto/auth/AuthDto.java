package fr.barlords.dndgateapi.client.dto.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthDto(
        UUID accountId,
        String token,
        Instant expiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) { }
