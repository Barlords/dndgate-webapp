package fr.barlords.dndgateapi.domain.functional.model;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.UUID;

@Value
@Builder
public class GameCharacter {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    Account owner;

    /** Null for a character kept outside any adventure of the application. */
    @With
    Adventure adventure;

    @With
    String name;

    @With
    String race;

    @With
    String martialClass;

    @With
    String personality;

    @With
    String background;

    @With
    String picturePath;

    /** The character the owner currently plays in the adventure: at most one per player and adventure. */
    @With
    boolean active;

    /** Version read from the storage, null while the character has never been saved: it detects concurrent changes. */
    @With
    Long version;

    public boolean isMyOwner(Account account) {
        return account.getId().equals(this.owner.getId());
    }

    public boolean hasAdventure() {
        return adventure != null;
    }

}
