package fr.barlords.dndgateapi.client.dto.adventure;

import fr.barlords.dndgateapi.client.validator.Patterns;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventurePatchRequest(
    @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String name,
    @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 5000) String description,
    @Min(1) @Max(100) Integer maxPlayer,
    AdventureVisibility visibility,
    @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String postalCode,
    @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String city
) {}
