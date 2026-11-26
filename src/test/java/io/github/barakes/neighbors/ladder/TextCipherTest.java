package io.github.barakes.neighbors.ladder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TextCipherTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private final TextCipher cipher = new TextCipher(KEY);

    @Test
    void whatGoesInComesOut() {
        byte[] stored = cipher.encrypt("לצאת לגינה ליד הבית", "ladder-step:1");
        assertEquals("לצאת לגינה ליד הבית", cipher.decrypt(stored, "ladder-step:1"));
    }

    @Test
    void theStoredBytesDontContainTheText() {
        byte[] stored = cipher.encrypt("לצאת לגינה", "ladder-step:1");
        assertFalse(new String(stored, StandardCharsets.UTF_8).contains("לגינה"));
    }

    @Test
    void theSameTextLooksDifferentEveryTime() {
        byte[] first = cipher.encrypt("לפתוח תריס", "ladder-step:1");
        byte[] second = cipher.encrypt("לפתוח תריס", "ladder-step:1");
        assertFalse(Arrays.equals(first, second), "a new random IV every time");
    }

    @Test
    void changedBytesAreRefused() {
        byte[] stored = cipher.encrypt("לפתוח תריס", "ladder-step:1");
        stored[stored.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(stored, "ladder-step:1"));
    }

    @Test
    void someoneElsesRowWontDecrypt() {
        byte[] stored = cipher.encrypt("לפתוח תריס", "ladder-step:1");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(stored, "ladder-step:2"));
    }

    @Test
    void anotherKeyWontDecrypt() {
        byte[] stored = cipher.encrypt("לפתוח תריס", "ladder-step:1");
        TextCipher other = new TextCipher("HxwdGhsYGRYXFBUSExAREg8ODA0KCwgJBgcEBQIDAAE=");
        assertThrows(IllegalStateException.class, () -> other.decrypt(stored, "ladder-step:1"));
    }

    @Test
    void theServerRefusesToStartWithoutAValidKey() {
        assertThrows(IllegalStateException.class, () -> new TextCipher(""));
        assertThrows(IllegalStateException.class, () -> new TextCipher("not base64 !!"));
        assertThrows(IllegalStateException.class, () -> new TextCipher("AAECAwQ="));
    }
}
