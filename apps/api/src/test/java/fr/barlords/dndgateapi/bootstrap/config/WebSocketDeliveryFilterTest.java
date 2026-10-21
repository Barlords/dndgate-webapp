package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

/** A broadcast frame only leaves for a subscription the registry still knows, about the adventure it was allowed for. */
class WebSocketDeliveryFilterTest {

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry(20);

    private final WebSocketDeliveryFilter filter = new WebSocketDeliveryFilter(registry);

    private final UUID account = UUID.randomUUID();

    private final UUID adventure = UUID.randomUUID();

    private void open(String connectionId, UUID account, UUID session) {
        registry.connectionEstablished(connectionId);
        registry.authenticated(connectionId, account, session);
    }

    private static Message<byte[]> frame(SimpMessageType type, String connectionId, String subscriptionId, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionId(connectionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> chatOf(UUID adventure, String connectionId, String subscriptionId) {
        return frame(SimpMessageType.MESSAGE, connectionId, subscriptionId, "/topic/adventure/chat/" + adventure);
    }

    @Test
    void a_message_for_a_live_subscription_is_delivered() {
        open("c1", account, UUID.randomUUID());
        registry.subscribe("c1", "chat", adventure);
        Message<byte[]> chat = chatOf(adventure, "c1", "chat");
        Message<byte[]> signal = frame(SimpMessageType.MESSAGE, "c1", "chat", "/topic/adventure/webrtc/" + adventure);

        Assertions.assertSame(chat, filter.preSend(chat, null));
        Assertions.assertSame(signal, filter.preSend(signal, null));
    }

    @Test
    void a_message_for_a_cut_or_unknown_subscription_is_dropped() {
        open("c1", account, UUID.randomUUID());
        registry.subscribe("c1", "chat", adventure);
        registry.removeSubscriptions(account, adventure);

        Assertions.assertNull(filter.preSend(chatOf(adventure, "c1", "chat"), null));
        Assertions.assertNull(filter.preSend(chatOf(adventure, "unknown", "chat"), null));
        Assertions.assertNull(filter.preSend(chatOf(adventure, null, "chat"), null));
        Assertions.assertNull(filter.preSend(chatOf(adventure, "c1", null), null));
    }

    /** The broker may route an id to another destination than the one recorded (an id reused by the client). */
    @Test
    void a_message_about_another_adventure_than_the_one_of_the_subscription_is_dropped() {
        open("c1", account, UUID.randomUUID());
        registry.subscribe("c1", "chat", adventure);

        Assertions.assertNull(filter.preSend(chatOf(UUID.randomUUID(), "c1", "chat"), null));
    }

    @Test
    void a_message_whose_destination_is_not_an_adventure_topic_is_dropped() {
        open("c1", account, UUID.randomUUID());
        registry.subscribe("c1", "chat", adventure);

        Assertions.assertNull(filter.preSend(frame(SimpMessageType.MESSAGE, "c1", "chat", null), null));
        Assertions.assertNull(filter.preSend(frame(SimpMessageType.MESSAGE, "c1", "chat", "/topic/other/" + adventure), null));
    }

    @Test
    void the_frames_that_are_not_broadcasts_go_through() {
        Message<byte[]> connected = frame(SimpMessageType.CONNECT_ACK, "c1", null, null);
        Message<byte[]> heartbeat = frame(SimpMessageType.HEARTBEAT, "c1", null, null);

        Assertions.assertSame(connected, filter.preSend(connected, null));
        Assertions.assertSame(heartbeat, filter.preSend(heartbeat, null));
    }
}
