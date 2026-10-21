package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.port.in.account.SessionVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The connections and subscriptions are recorded as they are allowed, so that they can be cut later. */
class WebSocketSecurityInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);

    private final AdventureAccessVerifierApi accessVerifier = mock(AdventureAccessVerifierApi.class);

    private final SessionVerifierApi sessionVerifier = mock(SessionVerifierApi.class);

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry(20);

    private final WebSocketSecurityInterceptor interceptor =
            new WebSocketSecurityInterceptor(jwtDecoder, accessVerifier, sessionVerifier, registry);

    private final UUID account = UUID.randomUUID();

    private final UUID session = UUID.randomUUID();

    private final UUID adventure = UUID.randomUUID();

    private Jwt token(String subject, String sessionClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("jwt").header("alg", "HS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (sessionClaim != null) {
            builder.claim("sid", sessionClaim);
        }
        return builder.build();
    }

    private Message<byte[]> connect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("c1");
        accessor.addNativeHeader("Authorization", "Bearer jwt");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("c1");
        accessor.setDestination(destination);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(account.toString(), null, List.of()));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> unsubscribe(String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId("c1");
        accessor.setSubscriptionId(subscriptionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void connected() {
        registry.connectionEstablished("c1");
        when(jwtDecoder.decode("jwt")).thenReturn(token(account.toString(), session.toString()));
        when(sessionVerifier.isActive(session)).thenReturn(true);
        interceptor.preSend(connect(), null);
    }

    @Test
    void a_connection_is_recorded_with_its_account_and_its_session() {
        connected();

        Assertions.assertTrue(registry.isOpen("c1"));
        Assertions.assertEquals(List.of("c1"), registry.removeConnectionsOfSession(session));
    }

    @Test
    void a_connect_frame_handled_after_the_socket_closed_is_refused_and_leaves_nothing_behind() {
        when(jwtDecoder.decode("jwt")).thenReturn(token(account.toString(), session.toString()));

        Assertions.assertThrows(AccessDeniedException.class, () -> interceptor.preSend(connect(), null));

        Assertions.assertFalse(registry.isOpen("c1"));
        verify(sessionVerifier, never()).isActive(any());
    }

    @Test
    void a_session_revoked_while_the_connection_was_being_recorded_is_refused_and_forgotten() {
        registry.connectionEstablished("c1");
        when(jwtDecoder.decode("jwt")).thenReturn(token(account.toString(), session.toString()));
        when(sessionVerifier.isActive(session)).thenReturn(false);

        Assertions.assertThrows(AccessDeniedException.class, () -> interceptor.preSend(connect(), null));

        Assertions.assertFalse(registry.isOpen("c1"));
    }

    @Test
    void a_token_without_a_valid_session_or_subject_is_refused() {
        registry.connectionEstablished("c1");
        when(jwtDecoder.decode("jwt")).thenReturn(token(account.toString(), null));
        Assertions.assertThrows(AccessDeniedException.class, () -> interceptor.preSend(connect(), null));

        when(jwtDecoder.decode("jwt")).thenReturn(token("not-a-uuid", session.toString()));
        Assertions.assertThrows(AccessDeniedException.class, () -> interceptor.preSend(connect(), null));

        Assertions.assertFalse(registry.subscribe("c1", "sub-0", adventure), "still not authenticated");
        verify(sessionVerifier, never()).isActive(any());
    }

    @Test
    void an_allowed_subscription_is_recorded_and_an_unsubscription_forgets_it() {
        connected();

        interceptor.preSend(subscribe("/topic/adventure/chat/" + adventure, "sub-0"), null);

        verify(accessVerifier).verifyParticipant(account, adventure);
        Assertions.assertTrue(registry.isLive("c1", "sub-0"));

        interceptor.preSend(unsubscribe("sub-0"), null);
        Assertions.assertFalse(registry.isLive("c1", "sub-0"));
    }

    @Test
    void an_unsubscribe_frame_without_id_is_ignored() {
        connected();
        interceptor.preSend(subscribe("/topic/adventure/chat/" + adventure, "sub-0"), null);

        Assertions.assertDoesNotThrow(() -> interceptor.preSend(unsubscribe(null), null));

        Assertions.assertTrue(registry.isLive("c1", "sub-0"));
    }

    @Test
    void a_second_connect_frame_on_the_same_connection_is_refused() {
        connected();

        Assertions.assertThrows(AccessDeniedException.class, () -> interceptor.preSend(connect(), null));

        Assertions.assertEquals(List.of("c1"), registry.removeConnectionsOfSession(session));
    }

    @Test
    void a_subscription_id_already_in_use_is_refused_without_reading_anything() {
        connected();
        interceptor.preSend(subscribe("/topic/adventure/chat/" + adventure, "sub-0"), null);
        UUID other = UUID.randomUUID();

        Assertions.assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(subscribe("/topic/adventure/chat/" + other, "sub-0"), null));

        verify(accessVerifier, never()).verifyParticipant(account, other);
        Assertions.assertEquals(java.util.Optional.of(adventure), registry.adventureOf("c1", "sub-0"),
                "the first subscription is untouched");
    }

    @Test
    void a_refused_participant_leaves_no_subscription_behind() {
        connected();
        doThrow(new AdventureAccessDeniedException()).when(accessVerifier).verifyParticipant(account, adventure);

        Assertions.assertThrows(AdventureAccessDeniedException.class,
                () -> interceptor.preSend(subscribe("/topic/adventure/chat/" + adventure, "sub-0"), null));

        Assertions.assertFalse(registry.isLive("c1", "sub-0"));
    }

    @Test
    void a_connection_the_registry_no_longer_knows_cannot_subscribe() {
        connected();
        registry.removeConnectionsOfAccount(account);

        Assertions.assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(subscribe("/topic/adventure/chat/" + adventure, "sub-0"), null));

        verify(accessVerifier, never()).verifyParticipant(any(), any());
    }

    @Test
    void a_malformed_destination_or_a_subscription_without_id_is_refused_before_anything_is_read() {
        connected();

        Assertions.assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(subscribe("/topic/adventure/x/" + adventure, "sub-0"), null));
        Assertions.assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(subscribe("/topic/adventure/chat/" + adventure, null), null));

        verify(accessVerifier, never()).verifyParticipant(any(), any());
        Assertions.assertFalse(registry.isLive("c1", "sub-0"));
    }
}
