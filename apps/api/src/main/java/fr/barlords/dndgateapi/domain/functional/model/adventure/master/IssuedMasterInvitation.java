package fr.barlords.dndgateapi.domain.functional.model.adventure.master;

import java.time.Instant;

/** The only moment the code is known in clear. */
public record IssuedMasterInvitation(String code, Instant expiresAt) {
}
