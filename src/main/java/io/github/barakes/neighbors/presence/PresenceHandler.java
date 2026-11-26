package io.github.barakes.neighbors.presence;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Who is here right now, and who is doing something small.
 * <p>
 * Every open page keeps one WebSocket connection and hears the count. The page sends:
 * <ul>
 *   <li>"here" when its person presses "I'm here": they get a window of their own, chosen at random;</li>
 *   <li>"start" when they pick a small task (the server never learns which task);</li>
 *   <li>"done" when they finish it: their window glows for a moment for everyone.</li>
 * </ul>
 * A task that isn't marked done disappears quietly after 30 minutes. That isn't a failure, and nothing is kept.
 * <p>
 * Kept only in memory, never in the database: nothing about who was here, or when, is stored.
 */
@Component
public class PresenceHandler extends TextWebSocketHandler {

    /** The building has 30 windows. More people than that are still counted, just without a window. */
    static final int SEATS = 30;

    static final Duration TASK_TIMEOUT = Duration.ofMinutes(30);

    private final Clock clock;
    private final Random random = new Random();

    /** Every open page, whether or not its person pressed "I'm here". */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** The people who pressed "I'm here", by connection. Changed only inside synchronized methods. */
    private final Map<String, Neighbor> present = new HashMap<>();

    private static final class Neighbor {
        final int seat;
        Instant busySince; // null when not doing a task

        Neighbor(int seat) {
            this.seat = seat;
        }
    }

    public PresenceHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // A WebSocket session can't be written to from two threads at once; the decorator queues messages.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024);
        sessions.put(session.getId(), safe);
        // Only the newcomer needs the picture: nothing changed for anyone else.
        send(safe, presenceMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        switch (message.getPayload().trim()) {
            case "here" -> join(session.getId());
            case "start" -> start(session.getId());
            case "done" -> done(session.getId());
            default -> {
                // Anything else is ignored.
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leave(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        leave(session.getId());
    }

    /** Every minute: tasks older than 30 minutes disappear quietly. */
    @Scheduled(fixedRate = 60_000)
    public synchronized void expireTasks() {
        Instant limit = clock.instant().minus(TASK_TIMEOUT);
        boolean changed = false;
        for (Neighbor neighbor : present.values()) {
            if (neighbor.busySince != null && !neighbor.busySince.isAfter(limit)) {
                neighbor.busySince = null;
                changed = true;
            }
        }
        if (changed) {
            broadcast(presenceMessage());
        }
    }

    /** How many pressed "I'm here" and are still connected. */
    public synchronized int count() {
        return present.size();
    }

    /** How many are doing a task right now. */
    public synchronized int busyCount() {
        return (int) present.values().stream().filter(n -> n.busySince != null).count();
    }

    /** The window of the person on this connection, or -1. */
    public synchronized int seatOf(String sessionId) {
        Neighbor neighbor = present.get(sessionId);
        return neighbor == null ? -1 : neighbor.seat;
    }

    private synchronized void join(String id) {
        if (present.containsKey(id)) {
            return;
        }
        Neighbor neighbor = new Neighbor(freeSeat());
        present.put(id, neighbor);
        WebSocketSession session = sessions.get(id);
        if (session != null) {
            send(session, new TextMessage(PresenceMessages.you(neighbor.seat)));
        }
        broadcast(presenceMessage());
    }

    private synchronized void start(String id) {
        Neighbor neighbor = present.get(id);
        if (neighbor == null) {
            return;
        }
        neighbor.busySince = clock.instant();
        broadcast(presenceMessage());
    }

    private synchronized void done(String id) {
        Neighbor neighbor = present.get(id);
        if (neighbor == null || neighbor.busySince == null) {
            return;
        }
        neighbor.busySince = null;
        if (neighbor.seat >= 0) {
            broadcast(new TextMessage(PresenceMessages.done(neighbor.seat)));
        }
        broadcast(presenceMessage());
    }

    private synchronized void leave(String id) {
        sessions.remove(id);
        if (present.remove(id) != null) {
            broadcast(presenceMessage());
        }
    }

    private synchronized TextMessage presenceMessage() {
        List<Integer> seats = present.values().stream()
                .map(n -> n.seat)
                .filter(seat -> seat >= 0)
                .toList();
        return new TextMessage(PresenceMessages.presence(present.size(), busyCount(), seats));
    }

    /** A random free window, or -1 when all 30 are taken. */
    private int freeSeat() {
        Set<Integer> taken = present.values().stream().map(n -> n.seat).collect(Collectors.toSet());
        List<Integer> free = IntStream.range(0, SEATS).filter(i -> !taken.contains(i)).boxed().toList();
        return free.isEmpty() ? -1 : free.get(random.nextInt(free.size()));
    }

    private void broadcast(TextMessage message) {
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