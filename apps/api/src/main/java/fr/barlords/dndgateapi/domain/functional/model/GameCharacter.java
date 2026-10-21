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

    public boolean isMyOwner(Account account) {
        return account.getId().equals(this.owner.getId());
    }

}
