package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.server.audit.AfterCommit;
import fr.barlords.dndgateapi.server.websocket.OpenWebSocketSessions;
import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Cuts the WebSocket accesses that the domain declares ended.
 * <p>
 * - The end of a participation cuts the subscriptions to that adventure only: the connection stays, for the other adventures.
 *   They are removed from the registry (nothing is delivered to them any more), then from the broker.
 * - The end of a session closes the connections opened with its tokens: the client has to log in again anyway.
 * <p>
 * Inside a transaction this waits for the commit: a change that is rolled back cuts nobody. A failure here is logged
 * and never thrown, since the change it follows is already committed.
 */
@Slf4j
@Component
public class WebSocketAccessRevocationAdapter implements AccessRevocationSpi {

    private final WebSocketSubscriptionRegistry registry;

    private final OpenWebSocketSessions openSessions;

    private final MessageChannel brokerChannel;

    public WebSocketAccessRevocationAdapter(
            WebSocketSubscriptionRegistry registry,
            OpenWebSocketSessions openSessions,
            @Qualifier("brokerChannel") MessageChannel brokerChannel
    ) {
        this.registry = registry;
        this.openSessions = openSessions;
        this.brokerChannel = brokerChannel;
    }

    @Override
    public void participationEnded(UUID accountId, UUID adventureId) {
        afterCommit(() -> {
            List<Subscription> cut = registry.removeSubscriptions(accountId, adventureId);
            forgetInBroker(cut);
            logIfAny(cut.size(), "Account {} is no longer a participant of the adventure {}: {} WebSocket subscription(s) cut",
                    accountId, adventureId);
        });
    }

    @Override
    public void adventureDeleted(UUID adventureId) {
        afterCommit(() -> {
            List<Subscription> cut = registry.removeSubscriptionsTo(adventureId);
            forgetInBroker(cut);
            logIfAny(cut.size(), "Adventure {} deleted: {} WebSocket subscription(s) cut", adventureId);
        });
    }

    @Override
    public void sessionRevoked(UUID sessionId) {
        afterCommit(() -> {
            List<String> closed = registry.removeConnectionsOfSession(sessionId);
            closed.forEach(openSessions::close);
            logIfAny(closed.size(), "Session {} revoked: {} WebSocket connection(s) closed", sessionId);
        });
    }

    @Override
    public void allSessionsRevoked(UUID accountId) {
        afterCommit(() -> {
            List<String> closed = registry.removeConnectionsOfAccount(accountId);
            closed.forEach(openSessions::close);
            logIfAny(closed.size(), "Every session of account {} revoked: {} WebSocket connection(s) closed", accountId);
        });
    }

    /** Most logouts and departures have nothing open: only an actual cut is worth a line. */
    private static void logIfAny(int count, String message, Object... subjects) {
        if (count > 0) {
            Object[] arguments = Arrays.copyOf(subjects, subjects.length + 1);
            arguments[subjects.length] = count;
            log.info(message, arguments);
        }
    }

    private static void afterCommit(Runnable cut) {
        AfterCommit.run(() -> {
            try {
                cut.run();
            } catch (RuntimeException e) {
                // the registry is updated first: nothing is delivered to what could not be cut further
                log.error("A WebSocket access could not be completely cut", e);
            }
        });
    }

    /** The same frame a client sends to unsubscribe: the broker stops routing to it and frees it. */
    private void forgetInBroker(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
            accessor.setSessionId(subscription.connectionId());
            accessor.setSubscriptionId(subscription.subscriptionId());
            brokerChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        }
    }
}
