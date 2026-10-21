package fr.barlords.dndgateapi.domain.port.in.historicalnote;

import java.util.UUID;

public interface HistoricalNoteDeleterApi {

    /** A note has no name to retype: the caller has to say explicitly that it must be deleted. */
    void deleteById(UUID accountId, UUID id, boolean confirmed);

}
