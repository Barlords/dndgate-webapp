package fr.barlords.dndgateapi.domain.port.in.adventure;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdventureFinderApi {

    Page<Adventure> findVisibleTo(UUID accountId, PageRequest request);

    Optional<Adventure> findById(UUID id);

    Optional<Adventure> findByIdVisibleTo(UUID accountId, UUID id);

    Optional<Adventure> findByInvitationCode(String code);

    Page<Adventure> findWhereImMaster(UUID accountId, PageRequest request);

    Page<Adventure> findWhereImPlayer(UUID accountId, PageRequest request);

    Page<Adventure> findAvailableAdventures(UUID accountId, PageRequest request);

}
