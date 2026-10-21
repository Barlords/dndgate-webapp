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

import java.util.Optional;
import java.util.UUID;

/**
 * Last check before a broadcast frame leaves the server: it is only delivered to a subscription the registry
 * still knows, and only when the frame is about the adventure recorded for that subscription.
 * Whatever the broker may still hold (a subscription cut while it was being registered, a cleanup that failed,
 * an id it routes elsewhere), a revoked access receives nothing; when in doubt, nothing is delivered.
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
        Optional<UUID> adventureOfTheFrame =
                AdventureTopics.adventureIdOf(SimpMessageHeaderAccessor.getDestination(message.getHeaders()));

        // The frame must be about the very adventure this subscription was allowed for
        boolean allowed = connectionId != null && subscriptionId != null && adventureOfTheFrame.isPresent()
                && adventureOfTheFrame.equals(registry.adventureOf(connectionId, subscriptionId));

        if (!allowed) {
            log.debug("Frame not delivered to the revoked, unknown or mismatched subscription {} of connection {}",
                    subscriptionId, connectionId);
            return null;
        }

        return message;
    }
}
