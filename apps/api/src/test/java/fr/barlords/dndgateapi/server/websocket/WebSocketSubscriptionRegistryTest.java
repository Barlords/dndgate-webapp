package fr.barlords.dndgateapi.server.websocket;

import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry.Subscription;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class WebSocketSubscriptionRegistryTest {

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry();

    private void open(String connectionId, UUID account, UUID session) {
        registry.connectionEstablished(connectionId);
        registry.authenticated(connectionId, account, session);
    }

    private final UUID alice = UUID.randomUUID();

    private final UUID bob = UUID.randomUUID();

    private final UUID aliceLaptop = UUID.randomUUID();

    private final UUID alicePhone = UUID.randomUUID();

    private final UUID bobSession = UUID.randomUUID();

    private final UUID quest = UUID.randomUUID();

    private final UUID otherQuest = UUID.randomUUID();

    @Test
    void a_subscription_is_live_once_recorded_on_an_open_connection() {
        open("c1", alice, aliceLaptop);

        Assertions.assertTrue(registry.isOpen("c1"));
        Assertions.assertFalse(registry.isLive("c1", "sub-0"));
        Assertions.assertTrue(registry.subscribe("c1", "sub-0", quest));
        Assertions.assertTrue(registry.isLive("c1", "sub-0"));
        Assertions.assertFalse(registry.isLive("c1", "sub-1"));
        Assertions.assertFalse(registry.isLive("c2", "sub-0"));
    }

    @Test
    void a_connection_cannot_subscribe_before_its_connect_frame_is_accepted() {
        registry.connectionEstablished("c1");

        Assertions.assertTrue(registry.isOpen("c1"));
        Assertions.assertFalse(registry.subscribe("c1", "sub-0", quest));
        Assertions.assertEquals(List.of(), registry.removeConnectionsOfAccount(alice), "not tied to any account yet");

        Assertions.assertTrue(registry.authenticated("c1", alice, aliceLaptop));
        Assertions.assertTrue(registry.subscribe("c1", "sub-0", quest));
    }

    /** The CONNECT frame is handled asynchronously: the socket may already be closed, it must not come back. */
    @Test
    void a_closed_connection_is_not_brought_back_by_a_late_connect_frame() {
        registry.connectionEstablished("c1");
        registry.connectionClosed("c1");

        Assertions.assertFalse(registry.authenticated("c1", alice, aliceLaptop));
        Assertions.assertFalse(registry.isOpen("c1"));
        Assertions.assertFalse(registry.authenticated("never-opened", alice, aliceLaptop));
    }

    @Test
    void an_unknown_connection_cannot_subscribe() {
        Assertions.assertFalse(registry.subscribe("never-opened", "sub-0", quest));
        Assertions.assertFalse(registry.isLive("never-opened", "sub-0"));
        Assertions.assertFalse(registry.isOpen("never-opened"));
    }

    @Test
    void unsubscribing_and_closing_forget_the_subscriptions() {
        open("c1", alice, aliceLaptop);
        registry.subscribe("c1", "sub-0", quest);
        registry.subscribe("c1", "sub-1", otherQuest);

        registry.unsubscribe("c1", "sub-0");
        Assertions.assertFalse(registry.isLive("c1", "sub-0"));
        Assertions.assertTrue(registry.isLive("c1", "sub-1"));

        registry.connectionClosed("c1");
        Assertions.assertFalse(registry.isLive("c1", "sub-1"));
        Assertions.assertFalse(registry.isOpen("c1"));
        Assertions.assertFalse(registry.subscribe("c1", "sub-2", quest), "a closed connection cannot subscribe again");

        Assertions.assertDoesNotThrow(() -> registry.unsubscribe("c1", "sub-1"), "unsubscribing from a closed connection is harmless");
    }

    @Test
    void the_end_of_a_participation_cuts_that_account_on_that_adventure_on_every_connection_and_nothing_else() {
        open("alice-laptop", alice, aliceLaptop);
        open("alice-phone", alice, alicePhone);
        open("bob", bob, bobSession);
        registry.subscribe("alice-laptop", "chat", quest);
        registry.subscribe("alice-laptop", "webrtc", quest);
        registry.subscribe("alice-laptop", "other", otherQuest);
        registry.subscribe("alice-phone", "chat", quest);
        registry.subscribe("bob", "chat", quest);

        List<Subscription> cut = registry.removeSubscriptions(alice, quest);

        Assertions.assertEquals(Set.of(new Subscription("alice-laptop", "chat"), new Subscription("alice-laptop", "webrtc"),
                new Subscription("alice-phone", "chat")), Set.copyOf(cut));
        Assertions.assertFalse(registry.isLive("alice-laptop", "chat"));
        Assertions.assertFalse(registry.isLive("alice-phone", "chat"));
        Assertions.assertTrue(registry.isLive("alice-laptop", "other"), "the other adventures are not concerned");
        Assertions.assertTrue(registry.isLive("bob", "chat"), "the other participants are not concerned");
        Assertions.assertTrue(registry.isOpen("alice-laptop"), "the connection itself stays");
        Assertions.assertEquals(List.of(), registry.removeSubscriptions(alice, quest), "nothing left to cut");
    }

    @Test
    void a_deleted_adventure_loses_every_subscriber() {
        open("alice", alice, aliceLaptop);
        open("bob", bob, bobSession);
        registry.subscribe("alice", "chat", quest);
        registry.subscribe("alice", "other", otherQuest);
        registry.subscribe("bob", "webrtc", quest);

        List<Subscription> cut = registry.removeSubscriptionsTo(quest);

        Assertions.assertEquals(Set.of(new Subscription("alice", "chat"), new Subscription("bob", "webrtc")), Set.copyOf(cut));
        Assertions.assertTrue(registry.isLive("alice", "other"));
    }

    @Test
    void a_revoked_session_removes_only_the_connections_opened_with_it() {
        open("alice-laptop-1", alice, aliceLaptop);
        open("alice-laptop-2", alice, aliceLaptop);
        open("alice-phone", alice, alicePhone);
        registry.subscribe("alice-laptop-1", "chat", quest);
        registry.subscribe("alice-phone", "chat", quest);

        List<String> removed = registry.removeConnectionsOfSession(aliceLaptop);

        Assertions.assertEquals(Set.of("alice-laptop-1", "alice-laptop-2"), Set.copyOf(removed));
        Assertions.assertFalse(registry.isLive("alice-laptop-1", "chat"));
        Assertions.assertFalse(registry.subscribe("alice-laptop-2", "chat", quest), "a revoked connection cannot subscribe");
        Assertions.assertTrue(registry.isLive("alice-phone", "chat"));
    }

    @Test
    void every_connection_of_an_account_is_removed_and_only_those() {
        open("alice-laptop", alice, aliceLaptop);
        open("alice-phone", alice, alicePhone);
        open("bob", bob, bobSession);
        registry.subscribe("bob", "chat", quest);

        List<String> removed = registry.removeConnectionsOfAccount(alice);

        Assertions.assertEquals(Set.of("alice-laptop", "alice-phone"), Set.copyOf(removed));
        Assertions.assertFalse(registry.isOpen("alice-laptop"));
        Assertions.assertFalse(registry.isOpen("alice-phone"));
        Assertions.assertTrue(registry.isLive("bob", "chat"));
    }

    @Test
    void subscriptions_and_revocations_running_at_the_same_time_neither_fail_nor_touch_the_other_accounts() throws Exception {
        int connections = 50;
        for (int i = 0; i < connections; i++) {
            open("alice-" + i, alice, aliceLaptop);
            open("bob-" + i, bob, bobSession);
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < connections; i++) {
                int n = i;
                tasks.add(pool.submit(() -> {
                    start.await();
                    registry.subscribe("alice-" + n, "chat", quest);
                    registry.subscribe("bob-" + n, "chat", quest);
                    return null;
                }));
                tasks.add(pool.submit(() -> {
                    start.await();
                    registry.removeSubscriptions(alice, quest);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        registry.removeSubscriptions(alice, quest);

        for (int i = 0; i < connections; i++) {
            Assertions.assertFalse(registry.isLive("alice-" + i, "chat"));
            Assertions.assertTrue(registry.isLive("bob-" + i, "chat"));
        }
    }
}
