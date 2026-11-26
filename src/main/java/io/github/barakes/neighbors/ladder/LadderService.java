package io.github.barakes.neighbors.ladder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.barakes.neighbors.account.Account;

/**
 * "סולם החוצה": each person's own ladder of small steps, from inside the room to going out.
 * People can change it as they like: add their own steps, edit, delete and reorder them.
 * Everything they write is encrypted before it's stored, and only they can read it.
 */
@Service
public class LadderService {

    /**
     * The ladder everyone starts with, from the spec. Waiting for the mental-health professional's
     * review (questions 14 and 15). Change it here only.
     */
    static final List<String> DEFAULT_STEPS = List.of(
            "לפתוח תריס",
            "להתקלח ולהחליף בגדים",
            "לצאת למרפסת או לעמוד בכניסה",
            "ללכת עד הפינה ולחזור",
            "לקנות דבר אחד במכולת",
            "לשבת 10 דקות בבית קפה");

    static final int MAX_STEPS = 20;
    static final int MAX_TEXT = 80;

    private final LadderStepRepository steps;
    private final TextCipher cipher;

    public LadderService(LadderStepRepository steps, TextCipher cipher) {
        this.steps = steps;
        this.cipher = cipher;
    }

    /** The person's ladder, in order. The first time, they get the default one. */
    @Transactional
    public List<StepView> ladder(Account account) {
        List<LadderStep> mine = steps.findByAccountIdOrderByStepOrderAsc(account.getId());
        if (mine.isEmpty()) {
            for (int i = 0; i < DEFAULT_STEPS.size(); i++) {
                steps.save(new LadderStep(account.getId(), i, cipher.encrypt(DEFAULT_STEPS.get(i), context(account))));
            }
            mine = steps.findByAccountIdOrderByStepOrderAsc(account.getId());
        }
        return mine.stream().map(step -> view(account, step)).toList();
    }

    /** Adds a step of their own at the top of the ladder. */
    @Transactional
    public StepView add(Account account, String text) {
        List<LadderStep> mine = steps.findByAccountIdOrderByStepOrderAsc(account.getId());
        if (mine.size() >= MAX_STEPS) {
            throw new InvalidStepException("A ladder has at most " + MAX_STEPS + " steps");
        }
        int order = mine.isEmpty() ? 0 : mine.getLast().getStepOrder() + 1;
        LadderStep step = steps.save(new LadderStep(account.getId(), order, cipher.encrypt(validText(text), context(account))));
        return view(account, step);
    }

    @Transactional
    public StepView edit(Account account, Long stepId, String text) {
        LadderStep step = mine(account, stepId);
        step.setTextEncrypted(cipher.encrypt(validText(text), context(account)));
        return view(account, step);
    }

    @Transactional
    public void delete(Account account, Long stepId) {
        steps.delete(mine(account, stepId));
    }

    /** "I did it". Stays reached for good. */
    @Transactional
    public StepView markReached(Account account, Long stepId) {
        LadderStep step = mine(account, stepId);
        step.markReached();
        return view(account, step);
    }

    /** A new order: the ids of all their steps, from the bottom of the ladder to the top. */
    @Transactional
    public List<StepView> reorder(Account account, List<Long> ids) {
        List<LadderStep> mine = steps.findByAccountIdOrderByStepOrderAsc(account.getId());
        if (ids == null || ids.size() != mine.size() || !new HashSet<>(ids).equals(
                mine.stream().map(LadderStep::getId).collect(Collectors.toSet()))) {
            throw new InvalidStepException("The new order must list each step exactly once");
        }
        Map<Long, LadderStep> byId = mine.stream().collect(Collectors.toMap(LadderStep::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setStepOrder(i);
        }
        return ids.stream().map(id -> view(account, byId.get(id))).toList();
    }

    private LadderStep mine(Account account, Long stepId) {
        return steps.findById(stepId == null ? -1L : stepId)
                .filter(step -> step.getAccountId().equals(account.getId()))
                .orElseThrow(StepNotFoundException::new);
    }

    private StepView view(Account account, LadderStep step) {
        return new StepView(step.getId(), cipher.decrypt(step.getTextEncrypted(), context(account)), step.isReached());
    }

    private static String validText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT) {
            throw new InvalidStepException("A step is 1 to " + MAX_TEXT + " characters");
        }
        return trimmed;
    }

    /** Binds each encrypted text to its owner: bytes moved to someone else's row won't decrypt. */
    private static String context(Account account) {
        return "ladder-step:" + account.getId();
    }
}
