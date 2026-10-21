package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.Message;

import java.util.List;
import java.util.UUID;

public interface MessagePersistenceSpi extends PersistenceSpi<Message, UUID> {

    /** Newest first. */
    Page<Message> findByAdventureId(UUID adventureId, PageRequest request);

    List<Message> findBySenderId(UUID senderId);

    void deleteByAdventureId(UUID adventureId);

}
