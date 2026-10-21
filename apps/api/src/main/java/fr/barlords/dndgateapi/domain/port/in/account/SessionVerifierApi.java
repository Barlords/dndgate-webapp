package fr.barlords.dndgateapi.domain.port.in.account;

import java.util.UUID;

public interface SessionVerifierApi {

    /** Whether the session exists, is not revoked and is not expired. */
    boolean isActive(UUID sessionId);

}
