package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class WebSocketSecurityIntegrationTest extends ApiIntegrationTestSupport {

    private static final long TIMEOUT_SECONDS = 5;

    /** One STOMP connection attempt: completes either `connected` or `rejected`. */
    private static class Probe extends StompSessionHandlerAdapter {

        final CompletableFuture<StompSession> connected = new CompletableFuture<>();

        final CompletableFuture<String> rejected = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            connected.complete(session);
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            // the only frame delivered to the session handler itself is an ERROR frame
            rejected.complete(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            rejected.complete("transport: " + exception.getMessage());
        }

        @Override
        public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            rejected.complete("exception: " + exception.getMessage());
        }
    }

    private Probe connect(String token) {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        Probe probe = new Probe();
        stompClient.connectAsync("ws://localhost:" + port + "/dndgate-api/ws/websocket",
                new WebSocketHttpHeaders(), connectHeaders, probe);
        return probe;
    }

    private StompSession connectAuthenticated(String token) throws Exception {
        return connect(token).connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private CompletableFuture<String> subscribe(StompSession session, String destination) {
        CompletableFuture<String> received = new CompletableFuture<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete(String.valueOf(payload));
            }
        });
        return received;
    }

    /**
     * What the client sees is the same whether the transport drops the frame or the security interceptor refuses it
     * (the connection closes either way): the size limit itself is checked by WebSocketConfigTest.
     */
    @Test
    void a_big_frame_never_gets_an_answer_and_ends_the_connection() throws Exception {
        TestAccount account = registerAndLogin();
        Probe probe = connect(account.token());
        StompSession session = probe.connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try {
            session.send("/app/anything", Map.of("padding", "a".repeat(40_000)));
        } catch (org.springframework.messaging.MessageDeliveryException closedWhileSending) {
            // the server may drop the connection before the client has finished writing
        }

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertFalse(session.isConnected());
    }

    @Test
    void connection_without_token_is_refused() throws Exception {
        Probe probe = connect(null);

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertFalse(probe.connected.isDone());
    }

    @Test
    void connection_with_an_invalid_token_is_refused() throws Exception {
        Probe probe = connect("not-a-jwt");

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertFalse(probe.connected.isDone());
    }

    @Test
    void connection_with_the_token_of_a_logged_out_session_is_refused() throws Exception {
        TestAccount account = registerAndLogin();
        call(HttpMethod.POST, "/accounts/logout", account.token(), null);

        Probe probe = connect(account.token());

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertFalse(probe.connected.isDone());
    }

    @Test
    void a_participant_receives_the_chat_messages_of_the_adventure() throws Exception {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        StompSession session = connectAuthenticated(owner.token());
        CompletableFuture<String> received = subscribe(session, "/topic/adventure/chat/" + adventureId);
        // let the subscription be registered on the broker before publishing
        TimeUnit.MILLISECONDS.sleep(500);

        var sent = call(HttpMethod.POST, "/messages", owner.token(), Map.of("adventureId", adventureId.toString(),
                "content", "hello table"));
        Assertions.assertEquals(201, sent.getStatusCode().value(), sent.getBody());

        String payload = received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assertions.assertTrue(payload.contains("hello table"), payload);
    }

    @Test
    void a_webrtc_signal_answers_no_content_and_is_still_broadcast_to_the_participants() throws Exception {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        StompSession session = connectAuthenticated(owner.token());
        CompletableFuture<String> received = subscribe(session, "/topic/adventure/webrtc/" + adventureId);
        TimeUnit.MILLISECONDS.sleep(500);

        var sent = call(HttpMethod.POST, "/webrtc/offer", owner.token(),
                Map.of("type", "offer", "content", "sdp-body", "adventureId", adventureId.toString()));
        Assertions.assertEquals(204, sent.getStatusCode().value(), sent.getBody());
        Assertions.assertNull(sent.getBody());

        String payload = received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assertions.assertTrue(payload.contains("sdp-body"), payload);
        Assertions.assertTrue(payload.contains(owner.id().toString()), payload);
    }

    @Test
    void a_stranger_cannot_subscribe_to_the_adventure_topics() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        Probe probe = connect(stranger.token());
        StompSession session = probe.connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        CompletableFuture<String> received = subscribe(session, "/topic/adventure/chat/" + adventureId);

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertThrows(TimeoutException.class, () -> received.get(1, TimeUnit.SECONDS));
    }

    /** Only the exact topics of an adventure exist: anything else is refused, even to a participant. */
    private void assertSubscriptionRefused(TestAccount participant, String destination) throws Exception {
        Probe probe = connect(participant.token());
        StompSession session = probe.connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscribe(session, destination);

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), destination);
    }

    @Test
    void a_participant_cannot_subscribe_to_a_destination_that_is_not_exactly_an_adventure_topic() throws Exception {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        assertSubscriptionRefused(owner, "/topic/adventure/x/" + adventureId);
        assertSubscriptionRefused(owner, "/topic/adventure/" + adventureId);
        assertSubscriptionRefused(owner, "/topic/adventure/chat/" + adventureId + "/extra");
        assertSubscriptionRefused(owner, "/topic/adventure/chat/" + adventureId + "/");
        assertSubscriptionRefused(owner, "/topic/adventure/chat/x/" + adventureId);
        assertSubscriptionRefused(owner, "/topic/adventure/chat/" + adventureId.toString().replace("-", ""));
        assertSubscriptionRefused(owner, "/topic/adventure/chat/*");
        assertSubscriptionRefused(owner, "/topic/adventure/webrtc/**");
        assertSubscriptionRefused(owner, "/topic/other/chat/" + adventureId);
    }

    /** The server publishes to the lowercase form only: another spelling would never receive anything, it is refused. */
    @Test
    void an_uppercase_adventure_id_is_refused() throws Exception {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        assertSubscriptionRefused(owner, "/topic/adventure/chat/" + adventureId.toString().toUpperCase());
    }

    /** limits.websocket.subscriptions-per-connection, 20 by default. */
    @Test
    void a_connection_cannot_open_more_than_twenty_subscriptions() throws Exception {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        Probe probe = connect(owner.token());
        StompSession session = probe.connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (int i = 0; i < 20; i++) {
            subscribe(session, (i % 2 == 0 ? "/topic/adventure/chat/" : "/topic/adventure/webrtc/") + adventureId);
        }
        Assertions.assertThrows(TimeoutException.class, () -> probe.rejected.get(1, TimeUnit.SECONDS),
                "twenty subscriptions are allowed");

        subscribe(session, "/topic/adventure/chat/" + adventureId);

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the twenty-first is refused");
    }

    @Test
    void clients_cannot_publish_messages_themselves() throws Exception {
        TestAccount owner = registerAndLogin();

        Probe probe = connect(owner.token());
        StompSession session = probe.connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        session.send("/app/anything", "payload");

        Assertions.assertNotNull(probe.rejected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}
