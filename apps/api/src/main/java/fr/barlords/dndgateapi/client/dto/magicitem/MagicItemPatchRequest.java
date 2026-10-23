package fr.barlords.dndgateapi.client.dto.magicitem;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.client.validator.Patterns;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemRarity;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The fields left out keep their value. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MagicItemPatchRequest(
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String name,
        MagicItemType type,
        MagicItemRarity rarity,
        Boolean requiresAttunement,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 5000) String description
) {
}
