package io.github.barakes.neighbors.ladder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts what people write before it reaches the database, with AES-GCM.
 * <p>
 * Every text gets its own random 12-byte IV, stored in front of the encrypted bytes. GCM also checks
 * that the bytes weren't changed. The "context" (whose step this is) is bound to the encryption:
 * bytes copied from one person's row to another's won't decrypt.
 * <p>
 * The key comes from ladder.key (the LADDER_KEY environment variable). Without a valid key the server
 * doesn't start, rather than run and lose data.
 */
@Component
public class TextCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TextCipher(@Value("${ladder.key:}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            // The message never includes the key itself.
            throw new IllegalStateException("LADDER_KEY is not valid Base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("LADDER_KEY must be Base64 of exactly 32 random bytes");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public byte[] encrypt(String text, String context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt", e);
        }
    }

    public String decrypt(byte[] stored, String context) {
        if (stored == null || stored.length <= IV_BYTES) {
            throw new IllegalStateException("Could not decrypt: too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] text = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
            return new String(text, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Changed bytes, another person's row, or another key.
            throw new IllegalStateException("Could not decrypt", e);
        }
    }
}
