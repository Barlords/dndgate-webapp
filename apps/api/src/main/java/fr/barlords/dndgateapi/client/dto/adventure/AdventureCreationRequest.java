package fr.barlords.dndgateapi.client.dto.adventure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventureCreationRequest(
        @NotBlank @Size(min = 1, max = 255) String name,
        @NotBlank @Size(min = 1, max = 5000) String description,
        @NotNull @Min(1) @Max(100) Integer maxPlayer,
        @NotNull AdventureVisibility visibility,
        @NotBlank @Size(min = 1, max = 255) String postalCode,
        @NotBlank @Size(min = 1, max = 255) String city
) {
}

