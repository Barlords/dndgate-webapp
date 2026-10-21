package fr.barlords.dndgateapi.server.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a handle on every open WebSocket connection, so that the server can close one whose session is revoked.
 * The id is the one STOMP gives to the connection ({@code simpSessionId}).
 * <p>
 * It also forgets the subscriptions of a connection when it ends, however it ends (DISCONNECT frame, network loss, close).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenWebSocketSessions implements WebSocketHandlerDecoratorFactory {

    /** 1008: the server ends the connection because of its policy (here: the access was revoked). */
    static final CloseStatus REVOKED = CloseStatus.POLICY_VIOLATION.withReason("Access revoked");

    private final WebSocketSubscriptionRegistry registry;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                registry.connectionEstablished(session.getId());
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    sessions.remove(session.getId());
                    registry.connectionClosed(session.getId());
                }
            }
        };
    }

    /** Closes the connection if it is still open. Never throws: the access is already removed from the registry. */
    public void close(String connectionId) {
        WebSocketSession session = sessions.get(connectionId);
        if (session == null) {
            return;
        }
        try {
            session.close(REVOKED);
        } catch (IOException | RuntimeException e) {
            log.warn("The WebSocket connection {} could not be closed; nothing is delivered to it any more", connectionId, e);
        }
    }

}
