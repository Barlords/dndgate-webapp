package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Arrays;
import java.util.Optional;

/** A quantity that a plan caps, either per account or per adventure. */
public enum PlanLimit {
    ACTIVE_ADVENTURES_AS_MASTER("activeAdventuresAsMaster", Scope.ACCOUNT),
    CHARACTERS_WITHOUT_ADVENTURE("charactersWithoutAdventure", Scope.ACCOUNT),
    SPELL_BOOKS("spellBooks", Scope.ACCOUNT),
    MAGIC_ITEMS("magicItems", Scope.ACCOUNT),
    MASTER_NOTES_PER_ADVENTURE("masterNotesPerAdventure", Scope.ADVENTURE),
    HISTORICAL_NOTES_PER_ADVENTURE("historicalNotesPerAdventure", Scope.ADVENTURE),
    CHARACTERS_PER_PLAYER_AND_ADVENTURE("charactersPerPlayerAndAdventure", Scope.ADVENTURE),
    NPCS_PER_ADVENTURE("npcsPerAdventure", Scope.ADVENTURE),
    MESSAGES_PER_ADVENTURE("messagesPerAdventure", Scope.ADVENTURE);

    public enum Scope { ACCOUNT, ADVENTURE }

    private final String key;
    private final Scope scope;

    PlanLimit(String key, Scope scope) {
        this.key = key;
        this.scope = scope;
    }

    public String key() {
        return key;
    }

    public Scope scope() {
        return scope;
    }

    public static Optional<PlanLimit> fromKey(String key) {
        return Arrays.stream(values()).filter(limit -> limit.key.equals(key)).findFirst();
    }
}
