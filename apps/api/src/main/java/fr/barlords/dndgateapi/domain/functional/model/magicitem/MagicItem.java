package fr.barlords.dndgateapi.domain.functional.model.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.UUID;

/** A magic item of an account: personal, like a spell book. */
@Value
@Builder
public class MagicItem {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    Account owner;

    @With
    String name;

    @With
    MagicItemType type;

    @With
    MagicItemRarity rarity;

    /** Null in a patch that does not change it. */
    @With
    Boolean requiresAttunement;

    @With
    String description;

    /** Version read from the storage, null while the item has never been saved: it detects concurrent changes. */
    @With
    Long version;

}
