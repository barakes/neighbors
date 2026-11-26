package io.github.barakes.neighbors.ladder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.barakes.neighbors.TestcontainersConfiguration;
import io.github.barakes.neighbors.account.Account;
import io.github.barakes.neighbors.account.AccountRepository;
import io.github.barakes.neighbors.account.AccountService;
import io.github.barakes.neighbors.account.LoginTokenRepository;

/** Runs against a real, temporary PostgreSQL (Testcontainers), with the real tables from Flyway. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LadderServiceTest {

    @Autowired
    private LadderService ladder;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @Autowired
    private LadderStepRepository steps;

    @Autowired
    private JdbcTemplate jdbc;

    private Account dana;
    private Account yossi;

    @BeforeEach
    void twoPeople() {
        steps.deleteAll();
        tokens.deleteAll();
        accounts.deleteAll();
        dana = accountService.accountFor(accountService.register("Dana", "a-good-password").token()).orElseThrow();
        yossi = accountService.accountFor(accountService.register("Yossi", "a-good-password").token()).orElseThrow();
    }

    private static List<String> texts(List<StepView> ladder) {
        return ladder.stream().map(StepView::text).toList();
    }

    @Test
    void everyoneStartsWithTheDefaultLadder() {
        assertEquals(LadderService.DEFAULT_STEPS, texts(ladder.ladder(dana)));
        assertFalse(ladder.ladder(dana).getFirst().reached());
    }

    @Test
    void theDefaultLadderIsCreatedOnlyOnce() {
        ladder.ladder(dana);
        ladder.ladder(dana);
        assertEquals(LadderService.DEFAULT_STEPS.size(), steps.count());
    }

    @Test
    void theDatabaseHoldsOnlyEncryptedBytes() {
        ladder.add(dana, "לצאת לגינה ליד הבית");
        List<byte[]> stored = jdbc.queryForList("select text_encrypted from ladder_steps", byte[].class);
        assertFalse(stored.isEmpty());
        for (byte[] bytes : stored) {
            assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("לגינה"));
            assertFalse(new String(bytes, StandardCharsets.UTF_8).contains("תריס"));
        }
    }

    @Test
    void addingAStepPutsItAtTheTop() {
        ladder.ladder(dana);
        StepView added = ladder.add(dana, "  לצאת לגינה ליד הבית  ");
        assertEquals("לצאת לגינה ליד הבית", added.text());
        assertEquals("לצאת לגינה ליד הבית", ladder.ladder(dana).getLast().text());
    }

    @Test
    void editingAStep() {
        StepView first = ladder.ladder(dana).getFirst();
        ladder.edit(dana, first.id(), "לפתוח את התריס בחדר");
        assertEquals("לפתוח את התריס בחדר", ladder.ladder(dana).getFirst().text());
    }

    @Test
    void deletingAStep() {
        StepView first = ladder.ladder(dana).getFirst();
        ladder.delete(dana, first.id());
        assertEquals(LadderService.DEFAULT_STEPS.size() - 1, ladder.ladder(dana).size());
    }

    @Test
    void aReachedStepStaysReached() {
        StepView first = ladder.ladder(dana).getFirst();
        assertTrue(ladder.markReached(dana, first.id()).reached());
        assertTrue(ladder.markReached(dana, first.id()).reached());
        assertTrue(ladder.ladder(dana).getFirst().reached());
    }

    @Test
    void reordering() {
        List<StepView> before = ladder.ladder(dana);
        List<Long> reversed = before.stream().map(StepView::id).toList().reversed();
        ladder.reorder(dana, reversed);
        assertEquals(texts(before).reversed(), texts(ladder.ladder(dana)));
    }

    @Test
    void aNewOrderMustListEveryStepOnce() {
        List<Long> ids = ladder.ladder(dana).stream().map(StepView::id).toList();
        assertThrows(InvalidStepException.class, () -> ladder.reorder(dana, ids.subList(1, ids.size())));
        assertThrows(InvalidStepException.class, () -> ladder.reorder(dana, null));
    }

    @Test
    void nobodyCanTouchSomeoneElsesSteps() {
        Long danasStep = ladder.ladder(dana).getFirst().id();
        assertThrows(StepNotFoundException.class, () -> ladder.edit(yossi, danasStep, "משהו אחר"));
        assertThrows(StepNotFoundException.class, () -> ladder.delete(yossi, danasStep));
        assertThrows(StepNotFoundException.class, () -> ladder.markReached(yossi, danasStep));
        assertEquals(LadderService.DEFAULT_STEPS.getFirst(), ladder.ladder(dana).getFirst().text());
    }

    @Test
    void stepTextFollowsTheRules() {
        assertThrows(InvalidStepException.class, () -> ladder.add(dana, "   "));
        assertThrows(InvalidStepException.class, () -> ladder.add(dana, null));
        assertThrows(InvalidStepException.class, () -> ladder.add(dana, "א".repeat(LadderService.MAX_TEXT + 1)));
    }

    @Test
    void aLadderHasAtMost20Steps() {
        ladder.ladder(dana);
        for (int i = LadderService.DEFAULT_STEPS.size(); i < LadderService.MAX_STEPS; i++) {
            ladder.add(dana, "צעד " + i);
        }
        assertThrows(InvalidStepException.class, () -> ladder.add(dana, "צעד אחד יותר מדי"));
    }

    @Test
    void deletingTheAccountDeletesTheLadder() {
        ladder.ladder(dana);
        String token = accountService.login("Dana", "a-good-password").token();
        accountService.deleteAccount(token);
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from ladder_steps where account_id = ?", Integer.class, dana.getId()));
    }
}
