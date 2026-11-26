package io.github.barakes.neighbors.presence;

import java.util.Collection;
import java.util.stream.Collectors;

/** The JSON messages the server sends to the page. Small enough to write by hand. */
public final class PresenceMessages {

    private PresenceMessages() {
    }

    /**
     * To everyone: how many are here, how many are doing something small right now,
     * and which windows are lit (each person here has one window, chosen at random).
     */
    public static String presence(int here, int busy, Collection<Integer> seats) {
        String list = seats.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "{\"type\":\"presence\",\"here\":" + here + ",\"busy\":" + busy + ",\"seats\":[" + list + "]}";
    }

    /** To one person only: which window is theirs. */
    public static String you(int seat) {
        return "{\"type\":\"you\",\"seat\":" + seat + "}";
    }

    /** To everyone: the person in this window just finished something small. Their window glows for a moment. */
    public static String done(int seat) {
        return "{\"type\":\"done\",\"seat\":" + seat + "}";
    }

    /** To one person only: too many windows are already lit from their address; try again later. */
    public static String tooMany() {
        return "{\"type\":\"tooMany\"}";
    }
}
