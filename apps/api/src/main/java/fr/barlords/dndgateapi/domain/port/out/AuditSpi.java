package fr.barlords.dndgateapi.domain.port.out;

import java.util.UUID;

/**
 * The trace of what cannot be undone. Only identifiers are given to it, never a name, a pseudo or an address:
 * the traces must not keep a copy of the personal data that has just been deleted.
 */
public interface AuditSpi {

    /**
     * Records a deletion confirmed by {@code actorId}. To call once the deletion is done, never for a refused attempt.
     * The trace itself is only written when the deletion is really kept by the database (see the adapter).
     */
    void deletion(String subject, UUID actorId, UUID subjectId);

}
