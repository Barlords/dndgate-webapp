package fr.barlords.dndgateapi.domain.functional.model;

import java.time.Duration;

/**
 * How much one account (or one adventure) may hold, so that nobody can fill the storage or the database.
 * The message limit is a rate: at most {@code messagesPerWindow} messages per account in any {@code messageWindow}.
 */
public record QuantityLimits(
        int charactersPerPlayerAndAdventure,
        int activeAdventuresPerMaster,
        int spellBooksPerAccount,
        int spellsPerSpellBook,
        int adventureNotesPerAdventure,
        int historicalNotesPerAdventure,
        int messagesPerWindow,
        Duration messageWindow) {

    public QuantityLimits {
        if (charactersPerPlayerAndAdventure < 1 || activeAdventuresPerMaster < 1 || spellBooksPerAccount < 1
                || spellsPerSpellBook < 1 || adventureNotesPerAdventure < 1 || historicalNotesPerAdventure < 1
                || messagesPerWindow < 1) {
            throw new IllegalArgumentException("Every quantity limit must be at least 1");
        }
        if (messageWindow == null || messageWindow.isZero() || messageWindow.isNegative()) {
            throw new IllegalArgumentException("The message window must be positive");
        }
    }

    public static QuantityLimits defaults() {
        return new QuantityLimits(10, 50, 100, 1000, 500, 500, 30, Duration.ofMinutes(1));
    }

}
