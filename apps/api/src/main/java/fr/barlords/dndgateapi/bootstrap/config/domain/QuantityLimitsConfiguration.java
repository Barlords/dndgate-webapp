package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** The caps on what an account can create (review v2 point 40). The defaults apply when a key is absent. */
@Configuration
public class QuantityLimitsConfiguration {

    @Bean
    public QuantityLimits quantityLimits(
            @Value("${limits.characters-per-player-and-adventure:10}") int charactersPerPlayerAndAdventure,
            @Value("${limits.active-adventures-per-master:50}") int activeAdventuresPerMaster,
            @Value("${limits.spell-books-per-account:100}") int spellBooksPerAccount,
            @Value("${limits.spells-per-spell-book:1000}") int spellsPerSpellBook,
            @Value("${limits.adventure-notes-per-adventure:500}") int adventureNotesPerAdventure,
            @Value("${limits.historical-notes-per-adventure:500}") int historicalNotesPerAdventure,
            @Value("${limits.messages.max:30}") int messagesPerWindow,
            @Value("${limits.messages.window:PT1M}") Duration messageWindow
    ) {
        return new QuantityLimits(charactersPerPlayerAndAdventure, activeAdventuresPerMaster, spellBooksPerAccount,
                spellsPerSpellBook, adventureNotesPerAdventure, historicalNotesPerAdventure, messagesPerWindow, messageWindow);
    }

}
