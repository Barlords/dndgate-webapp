package fr.barlords.dndgateapi.domain.functional.model;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Value
@Builder
public class SpellBook {

    /** Ruleset of the spell books created before rulesets existed, and of imported files that name none. */
    public static final String LEGACY_RULESET = "dnd5e-2014";

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    Account owner;

    @With
    String name;

    @With
    String description;

    /** Identifier of the ruleset the spell indexes belong to; the API only checks its format. */
    @With
    String ruleset;

    @Builder.Default
    @With
    Set<String> spellList = new HashSet<>();

    /** Version read from the storage, null while the spell book has never been saved: it detects concurrent changes. */
    @With
    Long version;

    public boolean isMyOwner(Account account) {
        return account.getId().equals(this.owner.getId());
    }

}

