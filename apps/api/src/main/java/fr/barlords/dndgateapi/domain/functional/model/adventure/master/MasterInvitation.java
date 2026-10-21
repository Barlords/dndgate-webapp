package fr.barlords.dndgateapi.domain.functional.model.adventure.master;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/** The code that lets someone take over an adventure from its game master. Only a hash of the code is kept. */
@Value
@Builder
public class MasterInvitation {

    @Builder.Default
    UUID id = UUID.randomUUID();

    UUID adventureId;

    String codeHash;

    UUID createdBy;

    Instant createdAt;

    Instant expiresAt;

    public boolean isValidAt(Instant now) {
        return expiresAt.isAfter(now);
    }
}
