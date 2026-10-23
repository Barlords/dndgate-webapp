package fr.barlords.dndgateapi.client.dto.magicitem;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemRarity;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MagicItemCreationRequest(
        @NotBlank @Size(min = 1, max = 255) String name,
        @NotNull MagicItemType type,
        @NotNull MagicItemRarity rarity,
        @NotNull Boolean requiresAttunement,
        @NotBlank @Size(min = 1, max = 5000) String description
) {
}
