package fr.barlords.dndgateapi.client.service;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The client of a request, for the lockout. It is the remote address of the connection, which the container
 * replaces by the one of the real client behind a trusted proxy (server.forward-headers-strategy=native);
 * the forwarded headers are never read here, so a client cannot choose its own identity.
 */
public final class ClientAddresses {

    private ClientAddresses() {
    }

    public static ClientAddress of(HttpServletRequest request) {
        return new ClientAddress(request.getRemoteAddr());
    }

}
