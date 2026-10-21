package fr.barlords.dndgateapi.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Memory of the invitation e-mails sent, which the invitation limits count. */
public interface InvitationLogSpi {

    void record(UUID senderId, UUID recipientId, UUID adventureId, Instant sentAt);

    /** When the sender sent invitations since the given instant, oldest first. */
    List<Instant> findSentAtBySenderSince(UUID senderId, Instant since);

    /** When the recipient was last invited to the adventure, if it happened since the given instant. */
    Optional<Instant> findLastSentToRecipientSince(UUID recipientId, UUID adventureId, Instant since);

    /** Forgets the invitations sent before the given instant. */
    void deleteSentBefore(Instant before);

}
