package io.github.barakes.neighbors.account;

/** What the phone gets after signing up or in: the name to show, and the token to keep. */
public record SignedIn(String username, String token) {
}
