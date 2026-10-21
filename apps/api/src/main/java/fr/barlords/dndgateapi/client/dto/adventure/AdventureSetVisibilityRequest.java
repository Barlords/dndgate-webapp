package fr.barlords.dndgateapi.client.dto.adventure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import jakarta.validation.constraints.NotNull;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventureSetVisibilityRequest(
        @NotNull AdventureVisibility visibility
) {
}

