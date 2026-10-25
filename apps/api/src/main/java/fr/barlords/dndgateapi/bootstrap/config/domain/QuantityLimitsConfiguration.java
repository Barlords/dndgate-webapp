package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** The technical ceilings for everybody (review v2 point 40). The defaults apply when a key is absent. */
@Configuration
public class QuantityLimitsConfiguration {

    @Bean
    public QuantityLimits quantityLimits(
            @Value("${limits.spells-per-spell-book:1000}") int spellsPerSpellBook,
            @Value("${limits.messages.max:30}") int messagesPerWindow,
            @Value("${limits.messages.window:PT1M}") Duration messageWindow
    ) {
        return new QuantityLimits(spellsPerSpellBook, messagesPerWindow, messageWindow);
    }

}
