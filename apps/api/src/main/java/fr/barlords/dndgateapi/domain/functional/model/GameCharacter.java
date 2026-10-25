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

    /** A character of the game master of the adventure: never played, never moved, owned by whoever created it. */
    @Builder.Default
    @With
    boolean npc = false;

    /** Only meaningful for a non-player character: a private one is read by the current game master alone. */
    @Builder.Default
    @With
    CharacterVisibility visibility = CharacterVisibility.PUBLIC;

    /** Version read from the storage, null while the character has never been saved: it detects concurrent changes. */
    @With
    Long version;

    public boolean isMyOwner(Account account) {
        return account.getId().equals(this.owner.getId());
    }

    public boolean isPrivateNpc() {
        return npc && visibility == CharacterVisibility.PRIVATE;
    }

    /** A non-player character is managed by the current game master of its adventure, whoever owns it. */
    public boolean isManagedBy(UUID accountId) {
        return hasAdventure() && adventure.isMaster(accountId);
    }

    public boolean hasAdventure() {
        return adventure != null;
    }

}
