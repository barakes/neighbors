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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import io.github.barakes.neighbors.limits.ClientAddress;

/**
 * Who is here right now, and who is doing something small.
 * <p>
 * Every open page keeps one WebSocket connection and hears the count. The page sends:
 * <ul>
 *   <li>"here" when its person presses "I'm here": they get a window of their own, chosen at random;</li>
 *   <li>"start" when they pick a small task (the server never learns which task);</li>
 *   <li>"done" when they finish it: their window glows for a moment for everyone;</li>
 *   <li>"ping" every 25 seconds, to say the page is still there.</li>
 * </ul>
 * A task that isn't marked done disappears quietly after 30 minutes. A page that stays silent for
 * 90 seconds (a phone that slept or lost signal without saying goodbye) is let go, so no window stays
 * lit for someone who left; the page reconnects by itself when it wakes.
 * <p>
 * Limits against abuse: one glow a minute per person, at most 10 lit windows and 20 open pages from one
 * address. Not lower: on mobile networks many unrelated people often share one address.
 * <p>
 * Kept only in memory, never in the database: nothing about who was here, or when, is stored.
 */
@Component
public class PresenceHandler extends TextWebSocketHandler {

    /** The building has 30 windows. More people than that are still counted, just without a window. */
    static final int SEATS = 30;

    static final Duration TASK_TIMEOUT = Duration.ofMinutes(30);
    static final Duration SILENCE_LIMIT = Duration.ofSeconds(90);
    static final Duration GLOW_COOLDOWN = Duration.ofMinutes(1);
    static final int MAX_WINDOWS_PER_ADDRESS = 10;
    static final int MAX_PAGES_PER_ADDRESS = 20;

    private final Clock clock;
    private final ClientAddress clientAddress;
    private final Random random = new Random();

    /** Every open page, whether or not its person pressed "I'm here". Changed only inside synchronized methods. */
    private final Map<String, Page> pages = new HashMap<>();

    /** The people who pressed "I'm here", by connection. Changed only inside synchronized methods. */
    private final Map<String, Neighbor> present = new HashMap<>();

    private static final class Page {
        final WebSocketSession session;
        final String address;
        Instant lastHeard;

        Page(WebSocketSession session, String address, Instant now) {
            this.session = session;
            this.address = address;
            this.lastHeard = now;
        }
    }

    private static final class Neighbor {
        final int seat;
        final String address;
        Instant busySince; // null when not doing a task
        Instant lastGlow;  // null until their first glow

        Neighbor(int seat, String address) {
            this.seat = seat;
            this.address = address;
        }
    }

    public PresenceHandler(Clock clock, ClientAddress clientAddress) {
        this.clock = clock;
        this.clientAddress = clientAddress;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) {
        String address = clientAddress.of(session);
        long pagesFromAddress = pages.values().stream().filter(p -> p.address.equals(address)).count();
        if (pagesFromAddress >= MAX_PAGES_PER_ADDRESS) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        // A WebSocket session can't be written to from two threads at once; the decorator queues messages.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024);
        pages.put(session.getId(), new Page(safe, address, clock.instant()));
        // Only the newcomer needs the picture: nothing changed for anyone else.
        send(safe, presenceMessage());
    }

    @Override
    protected synchronized void handleTextMessage(WebSocketSession session, TextMessage message) {
        Page page = pages.get(session.getId());
        if (page == null) {
            return;
        }
        page.lastHeard = clock.instant();
        switch (message.getPayload().trim()) {
            case "here" -> join(session.getId(), page);
            case "start" -> start(session.getId());
            case "done" -> done(session.getId());
            default -> {
                // "ping" and anything else: being heard was the point.
            }
        }
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leave(session.getId());
    }

    @Override
    public synchronized void handleTransportError(WebSocketSession session, Throwable exception) {
        leave(session.getId());
    }

    /** Every 30 seconds: let go of pages that have been silent for 90 seconds. */
    @Scheduled(fixedRate = 30_000)
    public synchronized void dropSilentPages() {
        Instant limit = clock.instant().minus(SILENCE_LIMIT);
        List<String> silent = pages.entrySet().stream()
                .filter(entry -> !entry.getValue().lastHeard.isAfter(limit))
                .map(Map.Entry::getKey)
                .toList();
        for (String id : silent) {
            WebSocketSession session = pages.get(id).session;
            leave(id);
            closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
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

    /** How many pages are open, counted or not. */
    public synchronized int pageCount() {
        return pages.size();
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

    private void join(String id, Page page) {
        if (present.containsKey(id)) {
            return;
        }
        long windowsFromAddress = present.values().stream().filter(n -> n.address.equals(page.address)).count();
        if (windowsFromAddress >= MAX_WINDOWS_PER_ADDRESS) {
            send(page.session, new TextMessage(PresenceMessages.tooMany()));
            return;
        }
        Neighbor neighbor = new Neighbor(freeSeat(), page.address);
        present.put(id, neighbor);
        send(page.session, new TextMessage(PresenceMessages.you(neighbor.seat)));
        broadcast(presenceMessage());
    }

    private void start(String id) {
        Neighbor neighbor = present.get(id);
        if (neighbor == null) {
            return;
        }
        boolean wasBusy = neighbor.busySince != null;
        neighbor.busySince = clock.instant();
        // Picking another task while already busy changes nothing anyone can see: no message for everyone.
        if (!wasBusy) {
            broadcast(presenceMessage());
        }
    }

    private void done(String id) {
        Neighbor neighbor = present.get(id);
        if (neighbor == null || neighbor.busySince == null) {
            return;
        }
        neighbor.busySince = null;
        Instant now = clock.instant();
        boolean mayGlow = neighbor.lastGlow == null || !now.isBefore(neighbor.lastGlow.plus(GLOW_COOLDOWN));
        if (mayGlow && neighbor.seat >= 0) {
            neighbor.lastGlow = now;
            broadcast(new TextMessage(PresenceMessages.done(neighbor.seat)));
        }
        broadcast(presenceMessage());
    }

    private void leave(String id) {
        pages.remove(id);
        if (present.remove(id) != null) {
            broadcast(presenceMessage());
        }
    }

    private TextMessage presenceMessage() {
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
        for (Page page : pages.values()) {
            send(page.session, message);
        }
    }

    private static void send(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            // That connection is broken; it's removed when its close arrives, or when it falls silent.
        }
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | IllegalStateException e) {
            // Already gone.
        }
    }
}
