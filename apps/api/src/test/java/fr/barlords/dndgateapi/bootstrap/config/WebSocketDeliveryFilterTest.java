package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

/** A broadcast frame only leaves for a subscription the registry still knows. */
class WebSocketDeliveryFilterTest {

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry();

    private void open(String connectionId, UUID account, UUID session) {
        registry.connectionEstablished(connectionId);
        registry.authenticated(connectionId, account, session);
    }

    private final WebSocketDeliveryFilter filter = new WebSocketDeliveryFilter(registry);

    private static Message<byte[]> frame(SimpMessageType type, String connectionId, String subscriptionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionId(connectionId);
        accessor.setSubscriptionId(subscriptionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void a_message_for_a_live_subscription_is_delivered() {
        open("c1", UUID.randomUUID(), UUID.randomUUID());
        registry.subscribe("c1", "chat", UUID.randomUUID());
        Message<byte[]> message = frame(SimpMessageType.MESSAGE, "c1", "chat");

        Assertions.assertSame(message, filter.preSend(message, null));
    }

    @Test
    void a_message_for_a_cut_or_unknown_subscription_is_dropped() {
        UUID account = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        open("c1", account, UUID.randomUUID());
        registry.subscribe("c1", "chat", adventure);
        registry.removeSubscriptions(account, adventure);

        Assertions.assertNull(filter.preSend(frame(SimpMessageType.MESSAGE, "c1", "chat"), null));
        Assertions.assertNull(filter.preSend(frame(SimpMessageType.MESSAGE, "unknown", "chat"), null));
        Assertions.assertNull(filter.preSend(frame(SimpMessageType.MESSAGE, null, "chat"), null));
        Assertions.assertNull(filter.preSend(frame(SimpMessageType.MESSAGE, "c1", null), null));
    }

    @Test
    void the_frames_that_are_not_broadcasts_go_through() {
        Message<byte[]> connected = frame(SimpMessageType.CONNECT_ACK, "c1", null);
        Message<byte[]> heartbeat = frame(SimpMessageType.HEARTBEAT, "c1", null);

        Assertions.assertSame(connected, filter.preSend(connected, null));
        Assertions.assertSame(heartbeat, filter.preSend(heartbeat, null));
    }
}
