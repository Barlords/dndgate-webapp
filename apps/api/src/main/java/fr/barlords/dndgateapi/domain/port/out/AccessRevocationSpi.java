package fr.barlords.dndgateapi.domain.port.out;

import java.util.UUID;

/**
 * The live channels of an adventure (chat and WebRTC signals over WebSocket) are only checked when they are opened.
 * The domain calls this port each time an access ends, so that what is already open is cut too.
 * <p>
 * Inside a transaction, the adapter waits for the commit: a change that is rolled back cuts nobody.
 */
public interface AccessRevocationSpi {

    /** The account is no longer a participant of the adventure (it left, or lost the master seat without a seat of player). */
    void participationEnded(UUID accountId, UUID adventureId);

    /** The adventure no longer exists: nobody keeps listening to it. */
    void adventureDeleted(UUID adventureId);

    /** One session (one login) is over: what was opened with its tokens is closed. */
    void sessionRevoked(UUID sessionId);

    /** Every session of the account is over (logout everywhere, password changed, account deleted). */
    void allSessionsRevoked(UUID accountId);

}
