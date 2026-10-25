package fr.barlords.dndgateapi.domain.functional.model;

import java.time.Duration;

/**
 * The technical ceilings that apply to everybody, so that nobody can fill the storage or the database.
 * What an account may hold, by plan, is read from the plan catalog instead.
 * The message limit is a rate: at most {@code messagesPerWindow} messages per account in any {@code messageWindow}.
 */
public record QuantityLimits(
        int spellsPerSpellBook,
        int messagesPerWindow,
        Duration messageWindow) {

    public QuantityLimits {
        if (spellsPerSpellBook < 1 || messagesPerWindow < 1) {
            throw new IllegalArgumentException("Every quantity limit must be at least 1");
        }
        if (messageWindow == null || messageWindow.isZero() || messageWindow.isNegative()) {
            throw new IllegalArgumentException("The message window must be positive");
        }
    }

    public static QuantityLimits defaults() {
        return new QuantityLimits(1000, 30, Duration.ofMinutes(1));
    }

}
