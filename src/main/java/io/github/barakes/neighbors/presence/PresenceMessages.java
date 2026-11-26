package io.github.barakes.neighbors.presence;

/** The JSON messages the server sends to the page. Small enough to write by hand. */
public final class PresenceMessages {

    private PresenceMessages() {
    }

    /** How many are here right now, the reader included. */
    public static String count(int here) {
        return "{\"type\":\"presence\",\"here\":" + here + "}";
    }
}
