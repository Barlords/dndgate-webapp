package fr.barlords.dndgateapi.server.websocket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenWebSocketSessionsTest {

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry(20);

    private void open(String connectionId, UUID account, UUID session) {
        registry.connectionEstablished(connectionId);
        registry.authenticated(connectionId, account, session);
    }

    private final OpenWebSocketSessions openSessions = new OpenWebSocketSessions(registry);

    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    private final WebSocketHandler decorated = openSessions.decorate(handler);

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    @Test
    void an_open_connection_can_be_closed_by_the_server_with_a_policy_status() throws Exception {
        WebSocketSession session = session("c1");
        decorated.afterConnectionEstablished(session);
        verify(handler).afterConnectionEstablished(session);
        Assertions.assertTrue(registry.isOpen("c1"), "known as soon as the socket is open, before its CONNECT frame");

        openSessions.close("c1");

        verify(session).close(OpenWebSocketSessions.REVOKED);
        Assertions.assertEquals(CloseStatus.POLICY_VIOLATION.getCode(), OpenWebSocketSessions.REVOKED.getCode());
    }

    @Test
    void a_closed_connection_is_forgotten_with_its_subscriptions_even_when_the_handler_fails() throws Exception {
        WebSocketSession session = session("c1");
        decorated.afterConnectionEstablished(session);
        open("c1", UUID.randomUUID(), UUID.randomUUID());
        registry.subscribe("c1", "chat", UUID.randomUUID());
        doThrow(new IllegalStateException("handler failure")).when(handler).afterConnectionClosed(session, CloseStatus.NORMAL);

        Assertions.assertThrows(IllegalStateException.class, () -> decorated.afterConnectionClosed(session, CloseStatus.NORMAL));

        Assertions.assertFalse(registry.isOpen("c1"));
        openSessions.close("c1");
        verify(session, never()).close(OpenWebSocketSessions.REVOKED);
    }

    @Test
    void closing_an_unknown_connection_does_nothing() {
        Assertions.assertDoesNotThrow(() -> openSessions.close("unknown"));
    }

    @Test
    void a_connection_that_fails_to_close_does_not_fail_the_revocation() throws Exception {
        WebSocketSession session = session("c1");
        decorated.afterConnectionEstablished(session);
        doThrow(new IOException("broken pipe")).when(session).close(OpenWebSocketSessions.REVOKED);

        Assertions.assertDoesNotThrow(() -> openSessions.close("c1"));
    }
}
