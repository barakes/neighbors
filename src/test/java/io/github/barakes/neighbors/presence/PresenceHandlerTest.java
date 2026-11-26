package io.github.barakes.neighbors.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    private void pressHere(WebSocketSession session) throws Exception {
        handler.handleMessage(session, new TextMessage("here"));
    }

    @Test
    void openingThePageShowsTheCountButIsNotCounted() throws Exception {
        WebSocketSession viewer = session("viewer");
        handler.afterConnectionEstablished(viewer);
        verify(viewer).sendMessage(here(0));
        assertEquals(0, handler.count());
    }

    @Test
    void pressingHereIsCountedAndEveryoneHears() throws Exception {
        WebSocketSession viewer = session("viewer");
        WebSocketSession person = session("person");
        handler.afterConnectionEstablished(viewer);
        handler.afterConnectionEstablished(person);
        pressHere(person);
        verify(viewer).sendMessage(here(1));
        verify(person).sendMessage(here(1));
        assertEquals(1, handler.count());
    }

    @Test
    void pressingTwiceCountsOnce() throws Exception {
        WebSocketSession person = session("person");
        handler.afterConnectionEstablished(person);
        pressHere(person);
        pressHere(person);
        verify(person, times(1)).sendMessage(here(1));
        assertEquals(1, handler.count());
    }

    @Test
    void whenSomeoneCountedLeavesEveryoneHears() throws Exception {
        WebSocketSession first = session("first");
        WebSocketSession second = session("second");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        pressHere(first);   // everyone hears 1
        pressHere(second);  // everyone hears 2
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);  // second hears 1 again
        verify(second, times(2)).sendMessage(here(1));
        assertEquals(1, handler.count());
    }

    @Test
    void whenAViewerLeavesNobodyIsDisturbed() throws Exception {
        WebSocketSession person = session("person");
        WebSocketSession viewer = session("viewer");
        handler.afterConnectionEstablished(person);  // person hears 0
        pressHere(person);                           // person hears 1
        handler.afterConnectionEstablished(viewer);
        handler.afterConnectionClosed(viewer, CloseStatus.NORMAL);
        // Those two messages and nothing more: the viewer coming and going changed nothing for the person.
        verify(person, times(2)).sendMessage(any());
    }

    @Test
    void otherTextIsIgnored() throws Exception {
        WebSocketSession person = session("person");
        handler.afterConnectionEstablished(person);
        handler.handleMessage(person, new TextMessage("something else"));
        assertEquals(0, handler.count());
    }

    @Test
    void messageFormat() {
        assertEquals("{\"type\":\"presence\",\"here\":14}", PresenceMessages.count(14));
    }
}