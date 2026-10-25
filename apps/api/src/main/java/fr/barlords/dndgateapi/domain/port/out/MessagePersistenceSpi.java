package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessagePersistenceSpi extends PersistenceSpi<Message, UUID> {

    /** Newest first. */
    Page<Message> findByAdventureId(UUID adventureId, PageRequest request);

    /** How many messages the adventure holds. */
    long countByAdventureId(UUID adventureId);

    /** Deletes the oldest messages of the adventure, keeping the {@code keep} newest; returns how many were deleted. */
    int deleteOldestBeyond(UUID adventureId, int keep);

    List<Message> findBySenderId(UUID senderId);

    void deleteByAdventureId(UUID adventureId);

    /** How many messages the account sent after the given instant. */
    long countBySenderIdSince(UUID senderId, Instant since);

    /** The send date of the oldest message the account sent after the given instant, if any. */
    Optional<Instant> findOldestSendDateBySenderIdSince(UUID senderId, Instant since);
}
