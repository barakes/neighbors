package io.github.barakes.neighbors.presence;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Who is here right now.
 * <p>
 * Every open page keeps one WebSocket connection and hears the count. A page is only counted
 * after its person presses "I'm here" (the page sends "here"): opening the page just shows the building.
 * <p>
 * Kept only in memory, never in the database: nothing about who was here, or when, is stored.
 */
@Component
public class PresenceHandler extends TextWebSocketHandler {

    /** Every open page, whether or not its person is counted yet. */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** The pages whose person pressed "I'm here". */
    private final Set<String> present = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // A WebSocket session can't be written to from two threads at once; the decorator queues messages.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024);
        sessions.put(session.getId(), safe);
        // Only the newcomer needs the count: nobody else's count changed.
        send(safe, countMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("here".equals(message.getPayload().trim()) && present.add(session.getId())) {
            broadcastCount();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leave(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        leave(session);
    }

    /** How many pressed "I'm here" and are still connected. */
    public int count() {
        return present.size();
    }

    private void leave(WebSocketSession session) {
        sessions.remove(session.getId());
        if (present.remove(session.getId())) {
            broadcastCount();
        }
    }

    private TextMessage countMessage() {
        return new TextMessage(PresenceMessages.count(present.size()));
    }

    private void broadcastCount() {
        TextMessage message = countMessage();
        for (WebSocketSession session : sessions.values()) {
            send(session, message);
        }
    }

    private static void send(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            // That connection is broken; it's removed when its close arrives.
        }
    }
}