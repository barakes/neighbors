package io.github.barakes.neighbors.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import io.github.barakes.neighbors.limits.ClientAddress;

class PresenceHandlerTest {

    /** A clock the test can move forward, so "30 minutes later" takes no time. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-26T20:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final PresenceHandler handler = new PresenceHandler(clock, new ClientAddress("CF-Connecting-IP"));
    private int nextAddress = 1;

    /** Each page gets its own address unless the test says otherwise. */
    private WebSocketSession session(String id, String address) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        HttpHeaders headers = new HttpHeaders();
        headers.add("CF-Connecting-IP", address);
        when(session.getHandshakeHeaders()).thenReturn(headers);
        return session;
    }

    /** Opens the page: connects without pressing anything. */
    private WebSocketSession open(String id) throws Exception {
        return open(id, "10.0.0." + nextAddress++);
    }

    private WebSocketSession open(String id, String address) throws Exception {
        WebSocketSession session = session(id, address);
        handler.afterConnectionEstablished(session);
        return session;
    }

    private void say(WebSocketSession session, String word) throws Exception {
        handler.handleMessage(session, new TextMessage(word));
    }

    private static TextMessage presence(int here, int busy, List<Integer> seats) {
        return new TextMessage(PresenceMessages.presence(here, busy, seats));
    }

    private TextMessage glow(String id) {
        return new TextMessage(PresenceMessages.done(handler.seatOf(id)));
    }

    // --- Being here ---

    @Test
    void openingThePageShowsTheBuildingButIsNotCounted() throws Exception {
        WebSocketSession viewer = open("viewer");
        verify(viewer).sendMessage(presence(0, 0, List.of()));
        assertEquals(0, handler.count());
    }

    @Test
    void pressingHereGivesYouAWindowOfYourOwn() throws Exception {
        WebSocketSession person = open("person");
        say(person, "here");
        int seat = handler.seatOf("person");
        assertTrue(seat >= 0 && seat < PresenceHandler.SEATS);
        verify(person).sendMessage(new TextMessage(PresenceMessages.you(seat)));
        assertEquals(1, handler.count());
    }

    @Test
    void everyoneSeesTheSameWindowLit() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        verify(viewer).sendMessage(presence(1, 0, List.of(handler.seatOf("person"))));
    }

    @Test
    void everyoneGetsADifferentWindowUntilTheBuildingIsFull() throws Exception {
        Set<Integer> seats = new HashSet<>();
        for (int i = 0; i < PresenceHandler.SEATS; i++) {
            say(open("p" + i), "here");
            seats.add(handler.seatOf("p" + i));
        }
        assertEquals(PresenceHandler.SEATS, seats.size());

        // One more: still counted, just without a window.
        say(open("extra"), "here");
        assertEquals(-1, handler.seatOf("extra"));
        assertEquals(PresenceHandler.SEATS + 1, handler.count());
    }

    @Test
    void pressingTwiceKeepsTheSameWindow() throws Exception {
        WebSocketSession person = open("person");
        say(person, "here");
        int seat = handler.seatOf("person");
        say(person, "here");
        assertEquals(seat, handler.seatOf("person"));
        assertEquals(1, handler.count());
    }

    @Test
    void leavingFreesTheWindowAndEveryoneSeesIt() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        handler.afterConnectionClosed(person, CloseStatus.NORMAL);
        assertEquals(0, handler.count());
        verify(viewer, times(2)).sendMessage(presence(0, 0, List.of()));
    }

    @Test
    void whenAViewerComesAndGoesNobodyIsDisturbed() throws Exception {
        WebSocketSession person = open("person");  // 1: the empty building
        say(person, "here");                       // 2: "your window", 3: the building with it
        WebSocketSession viewer = open("viewer");
        handler.afterConnectionClosed(viewer, CloseStatus.NORMAL);
        verify(person, times(3)).sendMessage(any());
    }

    // --- Doing something small ---

    @Test
    void startingATaskIsSeenByEveryone() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        say(person, "start");
        assertEquals(1, handler.busyCount());
        verify(viewer).sendMessage(presence(1, 1, List.of(handler.seatOf("person"))));
    }

    @Test
    void pickingAnotherTaskWhileBusyDisturbsNobody() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        say(person, "start");
        say(person, "start");
        say(person, "start");
        verify(viewer, times(1)).sendMessage(presence(1, 1, List.of(handler.seatOf("person"))));
    }

    @Test
    void startingBeforeBeingHereIsIgnored() throws Exception {
        WebSocketSession person = open("person");
        say(person, "start");
        assertEquals(0, handler.busyCount());
    }

    @Test
    void doneMakesYourWindowGlowForEveryone() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        say(person, "start");
        say(person, "done");
        verify(viewer).sendMessage(glow("person"));
        verify(person).sendMessage(glow("person"));
        assertEquals(0, handler.busyCount());
    }

    @Test
    void doneWithoutATaskDoesNothing() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        say(person, "done");
        verify(viewer, never()).sendMessage(glow("person"));
    }

    @Test
    void aWindowGlowsAtMostOnceAMinute() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");
        for (int i = 0; i < 5; i++) {
            say(person, "start");
            say(person, "done");
        }
        verify(viewer, times(1)).sendMessage(glow("person"));
        // The task still counts as done: only the glow waits.
        assertEquals(0, handler.busyCount());

        clock.advance(Duration.ofMinutes(1));
        say(person, "start");
        say(person, "done");
        verify(viewer, times(2)).sendMessage(glow("person"));
    }

    @Test
    void aTaskDisappearsQuietlyAfter30Minutes() throws Exception {
        WebSocketSession person = open("person");
        say(person, "here");
        say(person, "start");

        clock.advance(Duration.ofMinutes(29));
        say(person, "ping");
        handler.expireTasks();
        assertEquals(1, handler.busyCount());

        clock.advance(Duration.ofMinutes(1));
        say(person, "ping");
        handler.expireTasks();
        assertEquals(0, handler.busyCount());
        assertEquals(1, handler.count());
    }

    @Test
    void doneAfterTheTaskDisappearedDoesNotGlow() throws Exception {
        WebSocketSession person = open("person");
        say(person, "here");
        say(person, "start");
        clock.advance(Duration.ofMinutes(31));
        handler.expireTasks();
        say(person, "done");
        verify(person, never()).sendMessage(glow("person"));
    }

    // --- Pages that fall silent ---

    @Test
    void aSilentPageIsLetGoAfter90Seconds() throws Exception {
        WebSocketSession viewer = open("viewer");
        WebSocketSession person = open("person");
        say(person, "here");

        clock.advance(Duration.ofSeconds(60));
        say(viewer, "ping");
        handler.dropSilentPages();
        assertEquals(1, handler.count());

        clock.advance(Duration.ofSeconds(30));
        say(viewer, "ping");
        handler.dropSilentPages();
        assertEquals(0, handler.count());
        verify(person).close(CloseStatus.SESSION_NOT_RELIABLE);
        // The one still here hears that the window went dark.
        verify(viewer, times(2)).sendMessage(presence(0, 0, List.of()));
    }

    @Test
    void pingingKeepsAPageAlive() throws Exception {
        WebSocketSession person = open("person");
        say(person, "here");
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofSeconds(25));
            say(person, "ping");
            handler.dropSilentPages();
        }
        assertEquals(1, handler.count());
        verify(person, never()).close(any());
    }

    // --- Limits per address ---

    @Test
    void atMostTenWindowsFromOneAddress() throws Exception {
        for (int i = 0; i < PresenceHandler.MAX_WINDOWS_PER_ADDRESS; i++) {
            say(open("p" + i, "203.0.113.7"), "here");
        }
        WebSocketSession eleventh = open("eleventh", "203.0.113.7");
        say(eleventh, "here");
        verify(eleventh).sendMessage(new TextMessage(PresenceMessages.tooMany()));
        assertEquals(PresenceHandler.MAX_WINDOWS_PER_ADDRESS, handler.count());

        // Someone from another address is not affected.
        say(open("other", "198.51.100.1"), "here");
        assertEquals(PresenceHandler.MAX_WINDOWS_PER_ADDRESS + 1, handler.count());
    }

    @Test
    void atMostTwentyPagesFromOneAddress() throws Exception {
        for (int i = 0; i < PresenceHandler.MAX_PAGES_PER_ADDRESS; i++) {
            open("p" + i, "203.0.113.7");
        }
        WebSocketSession extra = open("extra", "203.0.113.7");
        verify(extra).close(CloseStatus.POLICY_VIOLATION);
        assertEquals(PresenceHandler.MAX_PAGES_PER_ADDRESS, handler.pageCount());
    }

    @Test
    void otherTextIsIgnored() throws Exception {
        WebSocketSession person = open("person");
        say(person, "something else");
        assertEquals(0, handler.count());
    }

    // --- Message formats ---

    @Test
    void messageFormats() {
        assertEquals("{\"type\":\"presence\",\"here\":2,\"busy\":1,\"seats\":[3,17]}",
                PresenceMessages.presence(2, 1, List.of(17, 3)));
        assertEquals("{\"type\":\"you\",\"seat\":17}", PresenceMessages.you(17));
        assertEquals("{\"type\":\"done\",\"seat\":17}", PresenceMessages.done(17));
        assertEquals("{\"type\":\"tooMany\"}", PresenceMessages.tooMany());
    }
}
