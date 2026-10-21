package fr.barlords.dndgateapi.domain.port.in.message;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageFinderApi {

    Optional<Message> findById(UUID id);

    /** Newest first. */
    Page<Message> findByAdventureId(UUID accountId, UUID adventureId, PageRequest request);

    List<Message> findBySenderId(UUID senderId);

}
