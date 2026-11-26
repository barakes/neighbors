package io.github.barakes.neighbors.ladder;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.barakes.neighbors.account.Account;
import io.github.barakes.neighbors.account.AccountService;
import io.github.barakes.neighbors.account.NotSignedInException;

/**
 * Every call needs "Authorization: Bearer token"; without it -> 401.
 * GET    /api/ladder                -> [{"id","text","reached"}], bottom of the ladder first
 * POST   /api/ladder        {"text"} -> 201 the new step (at the top)
 * PUT    /api/ladder/{id}   {"text"} -> the step
 * DELETE /api/ladder/{id}            -> 204
 * POST   /api/ladder/{id}/reached    -> the step, reached
 * PUT    /api/ladder/order  {"ids"}  -> the ladder in its new order
 * Someone else's step, or one that doesn't exist -> 404. Text or order against the rules -> 400.
 */
@RestController
@RequestMapping("/api/ladder")
public class LadderController {

    public record StepText(String text) {
    }

    public record Order(List<Long> ids) {
    }

    private final LadderService ladder;
    private final AccountService accounts;

    public LadderController(LadderService ladder, AccountService accounts) {
        this.ladder = ladder;
        this.accounts = accounts;
    }

    @GetMapping
    public List<StepView> ladder(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ladder.ladder(signedIn(authorization));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StepView add(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @RequestBody StepText body) {
        return ladder.add(signedIn(authorization), body.text());
    }

    @PutMapping("/order")
    public List<StepView> reorder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @RequestBody Order body) {
        return ladder.reorder(signedIn(authorization), body.ids());
    }

    @PutMapping("/{id}")
    public StepView edit(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @PathVariable Long id, @RequestBody StepText body) {
        return ladder.edit(signedIn(authorization), id, body.text());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        ladder.delete(signedIn(authorization), id);
    }

    @PostMapping("/{id}/reached")
    public StepView reached(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @PathVariable Long id) {
        return ladder.markReached(signedIn(authorization), id);
    }

    private Account signedIn(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : null;
        return accounts.accountFor(token).orElseThrow(NotSignedInException::new);
    }
}
