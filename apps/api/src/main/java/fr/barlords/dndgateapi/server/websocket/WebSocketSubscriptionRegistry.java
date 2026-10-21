package fr.barlords.dndgateapi.server.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The live WebSocket connections and their subscriptions to the topics of an adventure, with who opened them:
 * the account, and the session (login) whose token was presented at {@code CONNECT}.
 * <p>
 * It is the reference of what may still be delivered: a frame for a subscription it does not know is dropped.
 * When an access ends, the matching entries are removed here first, then the connection or the broker is told.
 * <p>
 * Thread-safe: changes are serialized (they are rare: connect, subscribe, revoke), lookups are lock-free
 * (one per frame delivered).
 */
@Component
public class WebSocketSubscriptionRegistry {

    /** One subscription of one connection, as the broker names it. */
    public record Subscription(String connectionId, String subscriptionId) {
    }

    /** Account and session stay null until the CONNECT frame is accepted. */
    private record Connection(UUID accountId, UUID sessionId, Map<String, UUID> adventureBySubscription) {

        boolean isAuthenticated() {
            return accountId != null;
        }
    }

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    /** A client opens one subscription per topic it shows: beyond this, a connection is refused new ones. */
    private final int maxSubscriptionsPerConnection;

    public WebSocketSubscriptionRegistry(
            @Value("${limits.websocket.subscriptions-per-connection:20}") int maxSubscriptionsPerConnection
    ) {
        this.maxSubscriptionsPerConnection = maxSubscriptionsPerConnection;
    }

    /** A WebSocket connection is open; nothing can be subscribed on it before its CONNECT frame is accepted. */
    public synchronized void connectionEstablished(String connectionId) {
        connections.putIfAbsent(connectionId, new Connection(null, null, new ConcurrentHashMap<>()));
    }

    /**
     * Ties the connection to the account and the session of the token presented at CONNECT.
     * The frames of a connection are handled after it may already be closed: a closed connection is not brought back.
     *
     * @return false when the connection is no longer open, or already authenticated (a second CONNECT):
     *         the frame must be refused
     */
    public synchronized boolean authenticated(String connectionId, UUID accountId, UUID sessionId) {
        Connection connection = connections.get(connectionId);
        if (connection == null || connection.isAuthenticated()) {
            return false;
        }
        connections.put(connectionId, new Connection(accountId, sessionId, connection.adventureBySubscription()));
        return true;
    }

    public synchronized void connectionClosed(String connectionId) {
        connections.remove(connectionId);
    }

    public boolean isOpen(String connectionId) {
        return connections.containsKey(connectionId);
    }

    /**
     * Records the subscription before the access is checked, so that a revocation committed in the meantime
     * either finds it here, or is already visible to the check.
     *
     * The broker keeps the first destination of a subscription id: an id already in use is refused, never
     * overwritten, so that what is recorded here is always what the broker routes.
     *
     * @return false when the connection is unknown (closed or revoked) or not authenticated, when the id is already
     *         in use on the connection, or when the connection has reached its number of subscriptions:
     *         the subscription must be refused
     */
    public synchronized boolean subscribe(String connectionId, String subscriptionId, UUID adventureId) {
        Connection connection = connections.get(connectionId);
        if (connection == null || !connection.isAuthenticated()) {
            return false;
        }
        Map<String, UUID> subscriptions = connection.adventureBySubscription();
        if (subscriptions.containsKey(subscriptionId) || subscriptions.size() >= maxSubscriptionsPerConnection) {
            return false;
        }
        subscriptions.put(subscriptionId, adventureId);
        return true;
    }

    public synchronized void unsubscribe(String connectionId, String subscriptionId) {
        Connection connection = connections.get(connectionId);
        if (connection != null) {
            connection.adventureBySubscription().remove(subscriptionId);
        }
    }

    /** The adventure a live subscription listens to, or empty when nothing may be delivered to it any more. */
    public Optional<UUID> adventureOf(String connectionId, String subscriptionId) {
        Connection connection = connections.get(connectionId);
        return connection == null
                ? Optional.empty()
                : Optional.ofNullable(connection.adventureBySubscription().get(subscriptionId));
    }

    /** Whether a frame may still be delivered to this subscription. */
    public boolean isLive(String connectionId, String subscriptionId) {
        return adventureOf(connectionId, subscriptionId).isPresent();
    }

    /** Removes the subscriptions of the account to the adventure, on every connection of the account. */
    public synchronized List<Subscription> removeSubscriptions(UUID accountId, UUID adventureId) {
        return removeSubscriptionsWhere(connection -> accountId.equals(connection.accountId()), adventureId);
    }

    /** Removes every subscription to the adventure, whoever opened it. */
    public synchronized List<Subscription> removeSubscriptionsTo(UUID adventureId) {
        return removeSubscriptionsWhere(connection -> true, adventureId);
    }

    /** Forgets the connections opened with a token of this session; returns them, to be closed. */
    public synchronized List<String> removeConnectionsOfSession(UUID sessionId) {
        return removeConnectionsWhere(connection -> sessionId.equals(connection.sessionId()));
    }

    /** Forgets every connection of the account; returns them, to be closed. */
    public synchronized List<String> removeConnectionsOfAccount(UUID accountId) {
        return removeConnectionsWhere(connection -> accountId.equals(connection.accountId()));
    }

    private List<Subscription> removeSubscriptionsWhere(Predicate<Connection> concerned, UUID adventureId) {
        List<Subscription> removed = new ArrayList<>();
        connections.forEach((connectionId, connection) -> {
            if (concerned.test(connection)) {
                connection.adventureBySubscription().entrySet().removeIf(entry -> {
                    boolean matches = entry.getValue().equals(adventureId);
                    if (matches) {
                        removed.add(new Subscription(connectionId, entry.getKey()));
                    }
                    return matches;
                });
            }
        });
        return removed;
    }

    private List<String> removeConnectionsWhere(Predicate<Connection> concerned) {
        List<String> removed = new ArrayList<>();
        connections.entrySet().removeIf(entry -> {
            boolean matches = concerned.test(entry.getValue());
            if (matches) {
                removed.add(entry.getKey());
            }
            return matches;
        });
        return removed;
    }
}
