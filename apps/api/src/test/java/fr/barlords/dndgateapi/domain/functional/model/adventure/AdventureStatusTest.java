package fr.barlords.dndgateapi.domain.functional.model.adventure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.ABORT;
import static fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.FINISH;
import static fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.NOT_STARTED;
import static fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.ONGOING;

class AdventureStatusTest {

    private static final Map<AdventureStatus, Set<AdventureStatus>> ALLOWED = Map.of(
            NOT_STARTED, Set.of(ONGOING, ABORT),
            ONGOING, Set.of(FINISH, ABORT),
            FINISH, Set.of(),
            ABORT, Set.of());

    @Test
    void only_the_forward_transitions_are_allowed() {
        for (AdventureStatus from : AdventureStatus.values()) {
            for (AdventureStatus to : AdventureStatus.values()) {
                Assertions.assertEquals(ALLOWED.get(from).contains(to), from.canMoveTo(to), from + " to " + to);
            }
        }
    }
}
