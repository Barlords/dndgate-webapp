package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The participant rule is checked when a subscription is opened; these tests check that it keeps holding afterwards:
 * whoever loses the access (leaves, loses the master seat, logs out, changes the password, deletes the account)
 * stops receiving the chat and the WebRTC signals at once, while the other participants keep receiving them.
 */
class WebSocketAccessRevocationIntegrationTest extends ApiIntegrationTestSupport {

    private static final long TIMEOUT_SECONDS = 5;

    /** How long a frame that must not come is waited for, once the same frame has reached another participant. */
    private static final long SILENCE_MILLIS = 700;

    @Autowired
    SimpleBrokerMessageHandler broker;

    @Autowired
    AdventureUpdaterApi adventureUpdater;

    @Autowired
    AccountSessionRevokerApi sessionRevoker;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final List<StompSession> opened = new ArrayList<>();

    @AfterEach
    void closeConnections() {
        opened.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    /** One STOMP connection: `closed` completes when the server ends it. */
    private static final class Connection extends StompSessionHandlerAdapter {

        final CompletableFuture<StompSession> connected = new CompletableFuture<>();

        final CompletableFuture<String> closed = new CompletableFuture<>();

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
            closed.complete("error frame: " + headers.getFirst("message"));
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            closed.complete("transport: " + exception.getMessage());
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
                                    Throwable exception) {
            closed.complete("exception: " + exception.getMessage());
        }

        StompSession session() throws Exception {
            return connected.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** What one subscription has received. */
    private record Inbox(BlockingQueue<String> frames) {

        boolean receives(String text) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                String frame = frames.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null && frame.contains(text)) {
                    return true;
                }
            }
            return false;
        }

        /** To call once another participant has received the frame: it was broadcast, it just did not come here. */
        boolean staysSilentAbout(String text) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SILENCE_MILLIS);
            while (System.nanoTime() < deadline) {
                String frame = frames.poll(50, TimeUnit.MILLISECONDS);
                if (frame != null && frame.contains(text)) {
                    return false;
                }
            }
            return true;
        }
    }

    private Connection connect(TestAccount account) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + account.token());

        Connection connection = new Connection();
        stompClient.connectAsync("ws://localhost:" + port + "/dndgate-api/ws/websocket",
                new WebSocketHttpHeaders(), connectHeaders, connection);
        opened.add(connection.session());
        return connection;
    }

    private Inbox subscribe(Connection connection, String destination) throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        connection.session().subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add(String.valueOf(payload));
            }
        });
        return new Inbox(frames);
    }

    private Inbox chat(Connection connection, UUID adventureId) throws Exception {
        return subscribe(connection, "/topic/adventure/chat/" + adventureId);
    }

    private Inbox webrtc(Connection connection, UUID adventureId) throws Exception {
        return subscribe(connection, "/topic/adventure/webrtc/" + adventureId);
    }

    /** The subscriptions are registered by the broker asynchronously. */
    private static void letTheSubscriptionsSettle() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(500);
    }

    private String say(TestAccount author, UUID adventureId) {
        String text = "line-" + UUID.randomUUID();
        ResponseEntity<String> sent = call(HttpMethod.POST, "/messages", author.token(),
                Map.of("adventureId", adventureId.toString(), "content", text));
        Assertions.assertEquals(200, sent.getStatusCode().value(), sent.getBody());
        return text;
    }

    private String signal(TestAccount author, UUID adventureId) {
        String sdp = "sdp-" + UUID.randomUUID();
        ResponseEntity<String> sent = call(HttpMethod.POST, "/webrtc/offer", author.token(),
                Map.of("type", "offer", "content", sdp, "adventureId", adventureId.toString()));
        Assertions.assertEquals(204, sent.getStatusCode().value(), sent.getBody());
        return sdp;
    }

    private void join(TestAccount account, UUID adventureId) {
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", account.token(), null)
                .getStatusCode().value());
    }

    private void leave(TestAccount account, UUID adventureId) {
        Assertions.assertEquals(200, call(HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", account.token(), null)
                .getStatusCode().value());
    }

    /** The sessions the broker still delivers the chat of the adventure to. */
    private List<String> brokerSubscribersOfTheChat(UUID adventureId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination("/topic/adventure/chat/" + adventureId);
        return new ArrayList<>(broker.getSubscriptionRegistry()
                .findSubscriptions(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()))
                .keySet());
    }

    private static void assertClosedByTheServer(Connection connection) throws Exception {
        Assertions.assertNotNull(connection.closed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertFalse(connection.session().isConnected());
    }

    // ---------------------------------------------------------------- leaving the adventure

    @Test
    void a_player_who_leaves_stops_receiving_the_chat_and_the_signals_while_the_others_keep_receiving() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount leaver = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(leaver, adventureId);

        Connection ownerConnection = connect(owner);
        Inbox ownerChat = chat(ownerConnection, adventureId);
        Inbox ownerSignals = webrtc(ownerConnection, adventureId);
        Connection leaverConnection = connect(leaver);
        Inbox leaverChat = chat(leaverConnection, adventureId);
        Inbox leaverSignals = webrtc(leaverConnection, adventureId);
        letTheSubscriptionsSettle();

        String before = say(owner, adventureId);
        Assertions.assertTrue(leaverChat.receives(before), "a participant receives the chat");

        leave(leaver, adventureId);

        String line = say(owner, adventureId);
        String sdp = signal(owner, adventureId);
        Assertions.assertTrue(ownerChat.receives(line));
        Assertions.assertTrue(ownerSignals.receives(sdp));
        Assertions.assertTrue(leaverChat.staysSilentAbout(line), "the chat is no longer delivered to the former player");
        Assertions.assertTrue(leaverSignals.staysSilentAbout(sdp), "the signals are no longer delivered to the former player");
        Assertions.assertEquals(1, brokerSubscribersOfTheChat(adventureId).size(),
                "the broker itself has forgotten the subscription of the former player");
        Assertions.assertTrue(leaverConnection.session().isConnected(), "the connection stays usable for the other adventures");
    }

    @Test
    void a_player_who_leaves_one_adventure_keeps_receiving_the_others() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID left = createAdventure(owner, "PUBLIC");
        UUID kept = createAdventure(owner, "PUBLIC");
        join(player, left);
        join(player, kept);

        Connection connection = connect(player);
        chat(connection, left);
        Inbox keptChat = chat(connection, kept);
        letTheSubscriptionsSettle();

        leave(player, left);

        Assertions.assertTrue(keptChat.receives(say(owner, kept)));
    }

    @Test
    void a_departure_that_is_rolled_back_cuts_nobody() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(player, adventureId);

        Inbox playerChat = chat(connect(player), adventureId);
        letTheSubscriptionsSettle();

        Assertions.assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adventureUpdater.quitAdventure(adventureId, player.id());
            throw new IllegalStateException("something fails before the commit");
        }));

        Assertions.assertTrue(playerChat.receives(say(owner, adventureId)), "the player is still in the adventure");
    }

    // ---------------------------------------------------------------- master seat

    @Test
    void a_former_master_left_without_a_seat_stops_receiving_and_the_new_master_can_listen() throws Exception {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        TestAccount newMaster = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
        jdbc.update("update adventure set max_player = 1, status = 'ONGOING' where id = ?", adventureId);

        Inbox masterChat = chat(connect(master), adventureId);
        Inbox playerChat = chat(connect(player), adventureId);
        letTheSubscriptionsSettle();

        ResponseEntity<String> invitation = call(HttpMethod.POST, "/adventures/" + adventureId + "/master/invitation", master.token(), null);
        Assertions.assertEquals(201, invitation.getStatusCode().value(), invitation.getBody());
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/master/accept", newMaster.token(),
                Map.of("code", body(invitation).get("code").asString())).getStatusCode().value());

        Inbox newMasterChat = chat(connect(newMaster), adventureId);
        letTheSubscriptionsSettle();

        String line = say(player, adventureId);
        Assertions.assertTrue(playerChat.receives(line));
        Assertions.assertTrue(newMasterChat.receives(line));
        Assertions.assertTrue(masterChat.staysSilentAbout(line), "every seat is taken: the former master has left");
    }

    @Test
    void a_former_master_who_gets_a_seat_keeps_receiving() throws Exception {
        TestAccount master = registerAndLogin();
        TestAccount newMaster = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        jdbc.update("update adventure set status = 'ONGOING' where id = ?", adventureId);

        Inbox masterChat = chat(connect(master), adventureId);
        letTheSubscriptionsSettle();

        ResponseEntity<String> invitation = call(HttpMethod.POST, "/adventures/" + adventureId + "/master/invitation", master.token(), null);
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/master/accept", newMaster.token(),
                Map.of("code", body(invitation).get("code").asString())).getStatusCode().value());

        Assertions.assertTrue(masterChat.receives(say(newMaster, adventureId)), "the former master is now a player");
    }

    // ---------------------------------------------------------------- sessions

    @Test
    void a_logged_out_session_is_disconnected_and_the_other_sessions_of_the_account_stay() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount laptop = registerAndLogin();
        TestAccount phone = login(laptop);
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(laptop, adventureId);

        Connection laptopConnection = connect(laptop);
        Inbox laptopChat = chat(laptopConnection, adventureId);
        Inbox phoneChat = chat(connect(phone), adventureId);
        letTheSubscriptionsSettle();

        Assertions.assertEquals(204, call(HttpMethod.POST, "/accounts/logout", laptop.token(), null).getStatusCode().value());

        assertClosedByTheServer(laptopConnection);
        String line = say(owner, adventureId);
        Assertions.assertTrue(phoneChat.receives(line), "another session of the same account is not concerned");
        Assertions.assertTrue(laptopChat.staysSilentAbout(line));
    }

    @Test
    void logging_out_everywhere_disconnects_every_connection_of_the_account_and_only_those() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount laptop = registerAndLogin();
        TestAccount phone = login(laptop);
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(laptop, adventureId);

        Connection laptopConnection = connect(laptop);
        Connection phoneConnection = connect(phone);
        Inbox ownerChat = chat(connect(owner), adventureId);
        letTheSubscriptionsSettle();

        Assertions.assertEquals(204, call(HttpMethod.POST, "/accounts/logout-all", phone.token(), null).getStatusCode().value());

        assertClosedByTheServer(laptopConnection);
        assertClosedByTheServer(phoneConnection);
        Assertions.assertTrue(ownerChat.receives(say(owner, adventureId)));
    }

    @Test
    void changing_the_password_disconnects_the_connections_opened_with_the_old_one() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(player, adventureId);

        Connection playerConnection = connect(player);
        Inbox playerChat = chat(playerConnection, adventureId);
        Inbox ownerChat = chat(connect(owner), adventureId);
        letTheSubscriptionsSettle();

        Assertions.assertEquals(200, call(HttpMethod.PATCH, "/accounts", player.token(),
                Map.of("currentPassword", player.password(), "newPassword", "a-brand-new-password")).getStatusCode().value());

        assertClosedByTheServer(playerConnection);
        String line = say(owner, adventureId);
        Assertions.assertTrue(ownerChat.receives(line));
        Assertions.assertTrue(playerChat.staysSilentAbout(line));
    }

    @Test
    void reusing_a_refresh_token_disconnects_the_session_it_belongs_to() throws Exception {
        TestAccount player = registerAndLogin();
        Connection connection = connect(player);

        Assertions.assertEquals(200, call(HttpMethod.POST, "/accounts/refresh", null,
                Map.of("refreshToken", player.refreshToken())).getStatusCode().value());
        Assertions.assertEquals(401, call(HttpMethod.POST, "/accounts/refresh", null,
                Map.of("refreshToken", player.refreshToken())).getStatusCode().value());

        assertClosedByTheServer(connection);
    }

    @Test
    void deleting_the_account_disconnects_it() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(player, adventureId);

        Connection playerConnection = connect(player);
        Inbox playerChat = chat(playerConnection, adventureId);
        Inbox ownerChat = chat(connect(owner), adventureId);
        letTheSubscriptionsSettle();

        Assertions.assertEquals(204, deleteAccount(player));

        assertClosedByTheServer(playerConnection);
        String line = say(owner, adventureId);
        Assertions.assertTrue(ownerChat.receives(line));
        Assertions.assertTrue(playerChat.staysSilentAbout(line));
    }

    @Test
    void a_logout_everywhere_that_is_rolled_back_disconnects_nobody() throws Exception {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        Connection connection = connect(owner);
        Inbox ownerChat = chat(connection, adventureId);
        letTheSubscriptionsSettle();

        Assertions.assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sessionRevoker.logoutAll(owner.id());
            throw new IllegalStateException("something fails before the commit");
        }));

        Assertions.assertTrue(ownerChat.receives(say(owner, adventureId)), "the sessions are still active");
        Assertions.assertTrue(connection.session().isConnected());
    }

    // ---------------------------------------------------------------- adventure deletion

    @Test
    void nobody_keeps_listening_to_a_deleted_adventure() throws Exception {
        TestAccount owner = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        join(player, adventureId);

        chat(connect(owner), adventureId);
        chat(connect(player), adventureId);
        letTheSubscriptionsSettle();
        Assertions.assertEquals(2, brokerSubscribersOfTheChat(adventureId).size());

        Assertions.assertEquals(204, call(HttpMethod.DELETE, "/adventures/" + adventureId, owner.token(),
                Map.of("confirmation", "Quest")).getStatusCode().value());

        Assertions.assertEquals(List.of(), brokerSubscribersOfTheChat(adventureId));
    }
}
