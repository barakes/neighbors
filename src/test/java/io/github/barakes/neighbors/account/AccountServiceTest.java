package io.github.barakes.neighbors.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.barakes.neighbors.TestcontainersConfiguration;

/** Runs against a real, temporary PostgreSQL (Testcontainers), with the real tables from Flyway. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountServiceTest {

    @Autowired
    private AccountService service;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startEmpty() {
        tokens.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void signingUpGivesATokenThatSignsIn() {
        SignedIn signedIn = service.register("Dana", "a-good-password");
        assertEquals("Dana", signedIn.username());
        assertEquals("Dana", service.accountFor(signedIn.token()).orElseThrow().getUsername());
    }

    @Test
    void hebrewUsernamesWork() {
        SignedIn signedIn = service.register("דנה_כהן", "a-good-password");
        assertEquals("דנה_כהן", service.accountFor(signedIn.token()).orElseThrow().getUsername());
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        service.register("Dana", "a-good-password");
        assertThrows(UsernameTakenException.class, () -> service.register("dana", "another-password"));
    }

    @Test
    void signingInWithTheRightPassword() {
        service.register("Dana", "a-good-password");
        SignedIn signedIn = service.login("dana", "a-good-password");
        assertEquals("Dana", signedIn.username());
        assertTrue(service.accountFor(signedIn.token()).isPresent());
    }

    @Test
    void wrongPasswordAndUnknownNameGetTheSameAnswer() {
        service.register("Dana", "a-good-password");
        assertThrows(InvalidLoginException.class, () -> service.login("Dana", "wrong-password"));
        assertThrows(InvalidLoginException.class, () -> service.login("nobody", "a-good-password"));
        assertThrows(InvalidLoginException.class, () -> service.login(null, null));
    }

    @Test
    void thePasswordIsStoredOnlyAsABcryptHash() {
        service.register("Dana", "a-good-password");
        String stored = accounts.findByUsernameIgnoringCase("Dana").orElseThrow().getPasswordHash();
        assertNotEquals("a-good-password", stored);
        assertTrue(stored.startsWith("$2"), "BCrypt hashes start with $2");
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() {
        SignedIn signedIn = service.register("Dana", "a-good-password");
        assertFalse(tokens.findById(signedIn.token()).isPresent());
        assertTrue(tokens.findById(AccountService.hash(signedIn.token())).isPresent());
    }

    @Test
    void badUsernamesAreRefused() {
        for (String name : new String[] {"ab", "with space", "x".repeat(31), "", null, "<script>"}) {
            assertThrows(InvalidAccountDetailsException.class, () -> service.register(name, "a-good-password"),
                    "should refuse: " + name);
        }
    }

    @Test
    void shortOrTooLongPasswordsAreRefused() {
        assertThrows(InvalidAccountDetailsException.class, () -> service.register("Dana", "short"));
        assertThrows(InvalidAccountDetailsException.class, () -> service.register("Dana", null));
        // 40 Hebrew letters are 80 bytes, more than BCrypt reads.
        assertThrows(InvalidAccountDetailsException.class, () -> service.register("Dana", "א".repeat(40)));
    }

    @Test
    void unknownOrEmptyTokensSignNobodyIn() {
        assertTrue(service.accountFor("not-a-real-token").isEmpty());
        assertTrue(service.accountFor("").isEmpty());
        assertTrue(service.accountFor(null).isEmpty());
    }

    @Test
    void signingOutEndsOnlyThatToken() {
        service.register("Dana", "a-good-password");
        SignedIn phone = service.login("Dana", "a-good-password");
        SignedIn laptop = service.login("Dana", "a-good-password");
        service.logout(phone.token());
        assertTrue(service.accountFor(phone.token()).isEmpty());
        assertTrue(service.accountFor(laptop.token()).isPresent());
    }

    @Test
    void anExpiredTokenDoesNotSignIn() {
        SignedIn signedIn = service.register("Dana", "a-good-password");
        jdbc.update("update login_tokens set expires_at = now() - interval '1 minute'");
        assertTrue(service.accountFor(signedIn.token()).isEmpty());
    }

    @Test
    void expiredTokensAreCleanedUp() {
        SignedIn old = service.register("Dana", "a-good-password");
        jdbc.update("update login_tokens set expires_at = now() - interval '1 minute'");
        SignedIn fresh = service.login("Dana", "a-good-password");
        service.removeExpiredTokens();
        assertEquals(1, tokens.count());
        assertTrue(service.accountFor(fresh.token()).isPresent());
        assertTrue(service.accountFor(old.token()).isEmpty());
    }

    @Test
    void deletingTheAccountRemovesEverythingAtOnce() {
        SignedIn signedIn = service.register("Dana", "a-good-password");
        service.login("Dana", "a-good-password");
        service.deleteAccount(signedIn.token());
        assertEquals(0, accounts.count());
        assertEquals(0, tokens.count());
        // The name is free again.
        service.register("Dana", "a-good-password");
    }

    @Test
    void deletingNeedsToBeSignedIn() {
        assertThrows(NotSignedInException.class, () -> service.deleteAccount("not-a-real-token"));
    }
}
