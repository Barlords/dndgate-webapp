package fr.barlords.dndgateapi.client.dto.adventure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;

import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventureDto(
    UUID id,
    UUID creatorId,
    String name,
    String description,
    Integer maxPlayer,
    List<UUID> players,
    AdventureStatus status,
    AdventureVisibility visibility,
    String postalCode,
    String city,
    /** True while the adventure has no game master: the players can invite one or vote for one among them. */
    boolean masterVacant
) {}
