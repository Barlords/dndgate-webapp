package fr.barlords.dndgateapi.domain.port.in.adventure;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;

import java.util.UUID;

public interface AdventureCreatorApi {
    Adventure create(UUID accountId, Adventure adventure);
}
