package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.port.in.account.SessionVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.server.adapter.JwtAccessTokenAdapter;
import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Secures the STOMP channel:
 * - CONNECT requires a valid JWT in the Authorization header; the connection is recorded with its account and session,
 * - SUBSCRIBE is only allowed to exactly {@code /topic/adventure/(chat|webrtc)/{id}}, for a participant of that adventure;
 *   the subscription is recorded so that it can be cut when the access ends (see WebSocketSubscriptionRegistry),
 * - clients cannot publish: messages are only broadcast by the server.
 */
@Component
@RequiredArgsConstructor
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    private final SessionVerifierApi sessionVerifierApi;

    private final WebSocketSubscriptionRegistry registry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case UNSUBSCRIBE -> forgetSubscription(accessor);
            case SEND -> throw new AccessDeniedException("Clients are not allowed to publish messages");
            default -> {
                // other frames (DISCONNECT, heart-beats...) need no check; a closed connection is forgotten by OpenWebSocketSessions
            }
        }

        return message;
    }

    /** An UNSUBSCRIBE without its id names nothing: it is ignored. */
    private void forgetSubscription(StompHeaderAccessor accessor) {
        if (accessor.getSessionId() != null && accessor.getSubscriptionId() != null) {
            registry.unsubscribe(accessor.getSessionId(), accessor.getSubscriptionId());
        }
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Missing bearer token");
        }

        UUID accountId;
        UUID sessionId;
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
            accountId = UUID.fromString(jwt.getSubject());
            sessionId = UUID.fromString(jwt.getClaimAsString(JwtAccessTokenAdapter.SESSION_CLAIM));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new AccessDeniedException("Invalid bearer token");
        }

        // Recorded first, checked again after: a logout committed in between either finds the connection here,
        // or is seen by this check. A connection already closed (the frame is handled asynchronously) or already
        // authenticated (a second CONNECT) is refused.
        if (!registry.authenticated(accessor.getSessionId(), accountId, sessionId)) {
            throw new AccessDeniedException("Connection not allowed");
        }
        if (!sessionVerifierApi.isActive(sessionId)) {
            registry.connectionClosed(accessor.getSessionId());
            throw new AccessDeniedException("Invalid bearer token");
        }

        return new UsernamePasswordAuthenticationToken(accountId.toString(), null, List.of());
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal account = accessor.getUser();
        String connectionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        UUID adventureId = AdventureTopics.adventureIdOf(accessor.getDestination())
                .orElseThrow(WebSocketSecurityInterceptor::subscriptionDenied);

        if (account == null || subscriptionId == null) {
            throw subscriptionDenied();
        }

        // Recorded before the check, for the same reason as the connection: a departure committed in between
        // either removes it, or is seen by the check. Refused: an unknown (closed or revoked) connection, an id already
        // in use on the connection (the broker would keep routing its first destination), too many subscriptions.
        if (!registry.subscribe(connectionId, subscriptionId, adventureId)) {
            throw subscriptionDenied();
        }

        try {
            // the name is the account id written at CONNECT
            adventureAccessVerifierApi.verifyParticipant(UUID.fromString(account.getName()), adventureId);
        } catch (RuntimeException e) {
            registry.unsubscribe(connectionId, subscriptionId);
            throw e;
        }
    }

    private static AccessDeniedException subscriptionDenied() {
        return new AccessDeniedException("Subscription not allowed");
    }

}
