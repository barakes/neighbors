package io.github.barakes.neighbors.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing up, in and out, and deleting the account.
 * <p>
 * Only a username and a BCrypt hash of the password are stored. Signing in gives the phone a random
 * token, and only the token's SHA-256 hash is stored, so the tables alone can't sign anyone in.
 */
@Service
public class AccountService {

    static final Duration TOKEN_LIFETIME = Duration.ofDays(30);

    /** 3 to 30 letters (Hebrew or English), digits, dot, dash or underscore. No spaces. */
    private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}_.-]{3,30}");
    private static final int MIN_PASSWORD = 8;
    /** BCrypt reads only the first 72 bytes; a Hebrew letter takes 2. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AccountRepository accounts;
    private final LoginTokenRepository tokens;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    /** Checked when the username doesn't exist, so a wrong name takes as long as a wrong password. */
    private final String dummyHash = encoder.encode("not-a-real-password");

    public AccountService(AccountRepository accounts, LoginTokenRepository tokens, Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public SignedIn register(String username, String password) {
        String name = validUsername(username);
        checkPassword(password);
        if (accounts.findByUsernameIgnoringCase(name).isPresent()) {
            throw new UsernameTakenException();
        }
        Account account;
        try {
            account = accounts.saveAndFlush(new Account(name, encoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // Someone took the same name a moment ago; the unique index caught it.
            throw new UsernameTakenException();
        }
        return new SignedIn(account.getUsername(), newToken(account));
    }

    @Transactional
    public SignedIn login(String username, String password) {
        Account account = username == null ? null
                : accounts.findByUsernameIgnoringCase(username.trim()).orElse(null);
        String hash = account == null ? dummyHash : account.getPasswordHash();
        boolean passwordMatches = password != null && encoder.matches(password, hash);
        if (account == null || !passwordMatches) {
            // The same answer for an unknown name and a wrong password: it doesn't reveal who has an account.
            throw new InvalidLoginException();
        }
        return new SignedIn(account.getUsername(), newToken(account));
    }

    /** The account a token belongs to, if the token is known and hasn't expired. */
    @Transactional(readOnly = true)
    public Optional<Account> accountFor(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findById(hash(token))
                .filter(t -> t.getExpiresAt().isAfter(clock.instant()))
                .flatMap(t -> accounts.findById(t.getAccountId()));
    }

    /** Signs this phone out. Other phones of the same account stay signed in. */
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokens.findById(hash(token)).ifPresent(tokens::delete);
        }
    }

    /** Deletes the account and everything that belongs to it, right away. */
    @Transactional
    public void deleteAccount(String token) {
        Account account = accountFor(token).orElseThrow(NotSignedInException::new);
        // The database deletes its tokens too (ON DELETE CASCADE).
        accounts.delete(account);
    }

    /** Every 6 hours: expired tokens are deleted instead of piling up. */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    @Transactional
    public void removeExpiredTokens() {
        tokens.deleteByExpiresAtBefore(clock.instant());
    }

    private String newToken(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new LoginToken(hash(token), account.getId(), clock.instant().plus(TOKEN_LIFETIME)));
        return token;
    }

    private static String validUsername(String username) {
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) {
            throw new InvalidAccountDetailsException(
                    "Username: 3 to 30 letters or digits, no spaces");
        }
        return name;
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new InvalidAccountDetailsException("Password: at least " + MIN_PASSWORD + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidAccountDetailsException("Password: too long");
        }
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
