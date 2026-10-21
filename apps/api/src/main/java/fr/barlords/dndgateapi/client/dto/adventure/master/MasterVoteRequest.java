package fr.barlords.dndgateapi.client.dto.adventure.master;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MasterVoteRequest(
        @NotNull Boolean approve
) {
}
