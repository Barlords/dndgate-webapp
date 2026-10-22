package fr.barlords.dndgateapi.domain.functional.exception.adventure;

import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(AdventureStatus current, AdventureStatus requested) {
        super("An adventure cannot move from " + current + " to " + requested);
    }

}
