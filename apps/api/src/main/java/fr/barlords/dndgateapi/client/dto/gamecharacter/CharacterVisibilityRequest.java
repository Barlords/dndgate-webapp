package fr.barlords.dndgateapi.client.dto.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.CharacterVisibility;
import jakarta.validation.constraints.NotNull;

public record CharacterVisibilityRequest(@NotNull CharacterVisibility visibility) {
}
