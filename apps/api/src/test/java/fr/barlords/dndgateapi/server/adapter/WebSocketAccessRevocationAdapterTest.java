package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.server.websocket.OpenWebSocketSessions;
import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The accesses are cut once the change that ends them is committed, never for a change that is rolled back. */
class WebSocketAccessRevocationAdapterTest {

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry(20);

    private void open(String connectionId, UUID account, UUID session) {
        registry.connectionEstablished(connectionId);
        registry.authenticated(connectionId, account, session);
    }

    private final OpenWebSocketSessions openSessions = mock(OpenWebSocketSessions.class);

    private final List<Message<?>> sentToTheBroker = new ArrayList<>();

    private final MessageChannel brokerChannel = (message, timeout) -> sentToTheBroker.add(message);

    private final WebSocketAccessRevocationAdapter adapter =
            new WebSocketAccessRevocationAdapter(registry, openSessions, brokerChannel);

    private final UUID account = UUID.randomUUID();

    private final UUID session = UUID.randomUUID();

    private final UUID adventure = UUID.randomUUID();

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void listening() {
        open("c1", account, session);
        registry.subscribe("c1", "chat", adventure);
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test
    void outside_a_transaction_the_subscription_is_cut_at_once_and_the_broker_forgets_it() {
        listening();

        adapter.participationEnded(account, adventure);

        Assertions.assertFalse(registry.isLive("c1", "chat"));
        Assertions.assertTrue(registry.isOpen("c1"), "the connection stays, for the other adventures");
        Assertions.assertEquals(1, sentToTheBroker.size());
        Message<?> unsubscribe = sentToTheBroker.getFirst();
        Assertions.assertEquals(SimpMessageType.UNSUBSCRIBE, SimpMessageHeaderAccessor.getMessageType(unsubscribe.getHeaders()));
        Assertions.assertEquals("c1", SimpMessageHeaderAccessor.getSessionId(unsubscribe.getHeaders()));
        Assertions.assertEquals("chat", SimpMessageHeaderAccessor.getSubscriptionId(unsubscribe.getHeaders()));
    }

    @Test
    void inside_a_transaction_a_departure_waits_for_the_commit() {
        listening();
        TransactionSynchronizationManager.initSynchronization();

        adapter.participationEnded(account, adventure);

        Assertions.assertTrue(registry.isLive("c1", "chat"), "nothing is cut before the commit");
        Assertions.assertTrue(sentToTheBroker.isEmpty());

        commit();

        Assertions.assertFalse(registry.isLive("c1", "chat"));
        Assertions.assertEquals(1, sentToTheBroker.size());
    }

    @Test
    void a_departure_that_is_rolled_back_cuts_nothing() {
        listening();
        TransactionSynchronizationManager.initSynchronization();

        adapter.participationEnded(account, adventure);
        rollback();

        Assertions.assertTrue(registry.isLive("c1", "chat"));
        Assertions.assertTrue(sentToTheBroker.isEmpty());
    }

    @Test
    void a_deleted_adventure_loses_its_subscriptions_after_the_commit() {
        listening();
        TransactionSynchronizationManager.initSynchronization();

        adapter.adventureDeleted(adventure);
        Assertions.assertTrue(registry.isLive("c1", "chat"));

        commit();
        Assertions.assertFalse(registry.isLive("c1", "chat"));
        Assertions.assertEquals(1, sentToTheBroker.size());
    }

    @Test
    void a_revoked_session_closes_its_connections_after_the_commit() {
        listening();
        TransactionSynchronizationManager.initSynchronization();

        adapter.sessionRevoked(session);
        verify(openSessions, never()).close(anyString());

        commit();
        verify(openSessions).close("c1");
        Assertions.assertFalse(registry.isOpen("c1"));
    }

    @Test
    void every_session_of_an_account_closes_every_connection_after_the_commit_and_not_on_rollback() {
        listening();
        TransactionSynchronizationManager.initSynchronization();

        adapter.allSessionsRevoked(account);
        rollback();
        verify(openSessions, never()).close(anyString());
        Assertions.assertTrue(registry.isOpen("c1"));

        TransactionSynchronizationManager.clearSynchronization();
        adapter.allSessionsRevoked(account);
        verify(openSessions).close("c1");
        Assertions.assertFalse(registry.isOpen("c1"));
    }

    @Test
    void a_failure_while_cutting_never_reaches_the_change_that_is_already_committed() {
        listening();
        MessageChannel failingBroker = mock(MessageChannel.class);
        doThrow(new IllegalStateException("broker stopped")).when(failingBroker).send(any());
        WebSocketAccessRevocationAdapter failing = new WebSocketAccessRevocationAdapter(registry, openSessions, failingBroker);

        Assertions.assertDoesNotThrow(() -> failing.participationEnded(account, adventure));

        Assertions.assertFalse(registry.isLive("c1", "chat"), "removed from the registry first: nothing is delivered to it");
    }
}
