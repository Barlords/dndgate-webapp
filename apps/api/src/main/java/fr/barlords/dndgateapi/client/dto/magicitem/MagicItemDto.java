package fr.barlords.dndgateapi.client.dto.magicitem;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemRarity;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemType;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MagicItemDto(
        UUID id,
        String name,
        MagicItemType type,
        MagicItemRarity rarity,
        boolean requiresAttunement,
        String description
) {
}
