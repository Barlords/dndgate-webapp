package fr.barlords.dndgateapi.client.dto.adventure.master;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MasterClaimDto(
        UUID id,
        UUID adventureId,
        UUID candidateId,
        MasterClaimStatus status,
        Instant expiresAt,
        /** The players who have to approve, and what each of them answered (null: not yet). */
        List<Voter> voters
) {

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public record Voter(UUID accountId, Boolean approved) {
    }
}
