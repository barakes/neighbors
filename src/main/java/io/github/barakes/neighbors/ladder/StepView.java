package io.github.barakes.neighbors.ladder;

/** A step as the app sees it: decrypted, for its own person only. */
public record StepView(Long id, String text, boolean reached) {
}
