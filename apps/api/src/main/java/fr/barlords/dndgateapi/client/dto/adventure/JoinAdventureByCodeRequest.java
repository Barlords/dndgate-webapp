package fr.barlords.dndgateapi.client.dto.adventure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record JoinAdventureByCodeRequest(
        @NotBlank @Size(max = 50) String code
) {
}

