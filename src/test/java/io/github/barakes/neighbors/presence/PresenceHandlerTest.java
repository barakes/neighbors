package io.github.barakes.neighbors.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PresenceHandlerTest {

    private final PresenceHandler handler = new PresenceHandler();

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static TextMessage here(int count) {
        return new TextMessage(PresenceMessages.count(count));
    }

    @Test
    void whoeverComesHearsTheCount() throws Exception {
        WebSocketSession first = session("first");
        handler.afterConnectionEstablished(first);
        verify(first).sendMessage(here(1));
        assertEquals(1, handler.count());
    }

    @Test
    void everyoneHearsWhenSomeoneComes() throws Exception {
        WebSocketSession first = session("first");
        WebSocketSession second = session("second");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        verify(first).sendMessage(here(2));
        verify(second).sendMessage(here(2));
    }

    @Test
    void everyoneHearsWhenSomeoneLeaves() throws Exception {
        WebSocketSession first = session("first");
        WebSocketSession second = session("second");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        verify(second).sendMessage(here(1));
        assertEquals(1, handler.count());
    }

    @Test
    void whoLeftHearsNothingMore() throws Exception {
        WebSocketSession first = session("first");
        WebSocketSession second = session("second");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        handler.afterConnectionEstablished(second);
        verify(first, never()).sendMessage(here(2));
    }

    @Test
    void messageFormat() {
        assertEquals("{\"type\":\"presence\",\"here\":14}", PresenceMessages.count(14));
    }
}
