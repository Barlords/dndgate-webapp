package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.server.websocket.OpenWebSocketSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final int MAX_FRAME_BYTES = 16 * 1024;

    private final WebSocketSecurityInterceptor webSocketSecurityInterceptor;

    private final WebSocketDeliveryFilter webSocketDeliveryFilter;

    private final OpenWebSocketSessions openWebSocketSessions;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    /**
     * Clients only ever send CONNECT (a JWT), SUBSCRIBE and heart-beats: a frame this big is not one of those.
     * It is dropped by the transport, before anything parses it.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_FRAME_BYTES);
        // keeps a handle on each connection, to close it when its session is revoked
        registration.addDecoratorFactory(openWebSocketSessions);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketSecurityInterceptor);
    }

    /** A broadcast frame only leaves for a subscription whose access has not been revoked since. */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketDeliveryFilter);
    }

}
