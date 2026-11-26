package io.github.barakes.neighbors.presence;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Who is here right now. Every open page keeps one WebSocket connection, and everyone hears
 * how many are here whenever someone comes or goes.
 * <p>
 * Kept only in memory, never in the database: nothing about who was here, or when, is stored.
 */
@Component
public class PresenceHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // A WebSocket session can't be written to from two threads at once; the decorator queues messages.
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
        broadcastCount();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        broadcastCount();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        broadcastCount();
    }

    public int count() {
        return sessions.size();
    }

    private void broadcastCount() {
        TextMessage message = new TextMessage(PresenceMessages.count(sessions.size()));
        for (WebSocketSession session : sessions.values()) {
            try {
                session.sendMessage(message);
            } catch (IOException | IllegalStateException e) {
                // That connection is broken; it's removed when its close arrives.
            }
        }
    }
}
