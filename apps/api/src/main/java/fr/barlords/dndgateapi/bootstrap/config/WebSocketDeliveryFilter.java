package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.server.websocket.WebSocketSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Last check before a broadcast frame leaves the server: it is only delivered to a subscription the registry
 * still knows. Whatever the broker may still hold (a subscription cut while it was being registered, a cleanup
 * that failed), a revoked access receives nothing; when in doubt, nothing is delivered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDeliveryFilter implements ChannelInterceptor {

    private final WebSocketSubscriptionRegistry registry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            // CONNECTED, ERROR, heart-beats...: not a broadcast
            return message;
        }

        String connectionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());

        if (connectionId == null || subscriptionId == null || !registry.isLive(connectionId, subscriptionId)) {
            log.debug("Frame not delivered to the revoked or unknown subscription {} of connection {}", subscriptionId, connectionId);
            return null;
        }

        return message;
    }
}
