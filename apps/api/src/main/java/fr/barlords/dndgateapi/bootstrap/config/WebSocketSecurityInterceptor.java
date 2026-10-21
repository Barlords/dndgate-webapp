package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
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
 * - CONNECT requires a valid JWT in the Authorization header,
 * - SUBSCRIBE to an adventure topic requires being a participant of that adventure,
 * - clients cannot publish: messages are only broadcast by the server.
 */
@Component
@RequiredArgsConstructor
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String ADVENTURE_TOPIC_PREFIX = "/topic/adventure/";

    private final JwtDecoder jwtDecoder;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new AccessDeniedException("Clients are not allowed to publish messages");
            default -> {
                // other frames (DISCONNECT, UNSUBSCRIBE, heart-beats...) need no check
            }
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Missing bearer token");
        }

        try {
            Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
            return new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of());
        } catch (JwtException e) {
            throw new AccessDeniedException("Invalid bearer token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal account = accessor.getUser();
        String destination = accessor.getDestination();

        if (account == null || destination == null || !destination.startsWith(ADVENTURE_TOPIC_PREFIX)) {
            throw new AccessDeniedException("Subscription not allowed");
        }

        try {
            UUID accountId = UUID.fromString(account.getName());
            UUID adventureId = UUID.fromString(destination.substring(destination.lastIndexOf('/') + 1));
            adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Subscription not allowed");
        }
    }

}
