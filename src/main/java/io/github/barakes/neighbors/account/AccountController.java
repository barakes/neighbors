package io.github.barakes.neighbors.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST   /api/account/register  {"username","password"} -> 201 {"username","token"}, 400 bad details, 409 name taken
 * POST   /api/account/login     {"username","password"} -> 200 {"username","token"}, 401 wrong name or password
 * GET    /api/account/me        Authorization: Bearer token -> 200 {"username"}, 401
 * POST   /api/account/logout    Authorization: Bearer token -> 204
 * DELETE /api/account           Authorization: Bearer token -> 204, 401
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    public record Credentials(String username, String password) {
    }

    public record Me(String username) {
    }

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SignedIn register(@RequestBody Credentials credentials) {
        return service.register(credentials.username(), credentials.password());
    }

    @PostMapping("/login")
    public SignedIn login(@RequestBody Credentials credentials) {
        return service.login(credentials.username(), credentials.password());
    }

    @GetMapping("/me")
    public Me me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Account account = service.accountFor(token(authorization)).orElseThrow(NotSignedInException::new);
        return new Me(account.getUsername());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logout(token(authorization));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.deleteAccount(token(authorization));
    }

    private static String token(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : null;
    }
}
