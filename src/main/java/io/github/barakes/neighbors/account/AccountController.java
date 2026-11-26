package io.github.barakes.neighbors.account;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.barakes.neighbors.limits.AttemptLimiter;
import io.github.barakes.neighbors.limits.ClientAddress;
import io.github.barakes.neighbors.limits.TooManyAttemptsException;

/**
 * POST   /api/account/register  {"username","password"} -> 201 {"username","token"}, 400 bad details, 409 name taken
 * POST   /api/account/login     {"username","password"} -> 200 {"username","token"}, 401 wrong name or password
 * GET    /api/account/me        Authorization: Bearer token -> 200 {"username"}, 401
 * POST   /api/account/logout    Authorization: Bearer token -> 204
 * DELETE /api/account           Authorization: Bearer token -> 204, 401
 * Too many sign-ups or wrong passwords from one address -> 429.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    public record Credentials(String username, String password) {
    }

    public record Me(String username) {
    }

    private final AccountService service;
    private final ClientAddress clientAddress;
    private final AttemptLimiter loginFailures;
    private final AttemptLimiter signups;

    public AccountController(AccountService service, ClientAddress clientAddress,
                             @Qualifier("loginFailures") AttemptLimiter loginFailures,
                             @Qualifier("signups") AttemptLimiter signups) {
        this.service = service;
        this.clientAddress = clientAddress;
        this.loginFailures = loginFailures;
        this.signups = signups;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SignedIn register(@RequestBody Credentials credentials, HttpServletRequest request) {
        String address = clientAddress.of(request);
        if (signups.isBlocked(address)) {
            throw new TooManyAttemptsException();
        }
        signups.record(address);
        return service.register(credentials.username(), credentials.password());
    }

    @PostMapping("/login")
    public SignedIn login(@RequestBody Credentials credentials, HttpServletRequest request) {
        String address = clientAddress.of(request);
        if (loginFailures.isBlocked(address)) {
            throw new TooManyAttemptsException();
        }
        try {
            return service.login(credentials.username(), credentials.password());
        } catch (InvalidLoginException e) {
            // Only wrong attempts count; signing in correctly many times is fine.
            loginFailures.record(address);
            throw e;
        }
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
