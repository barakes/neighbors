package io.github.barakes.neighbors.limits;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Answered with 429 Too Many Requests. */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException() {
        super("Too many attempts, try again later");
    }
}
