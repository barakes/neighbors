package io.github.barakes.neighbors.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The username or password doesn't follow the rules. Answered with 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAccountDetailsException extends RuntimeException {

    public InvalidAccountDetailsException(String message) {
        super(message);
    }
}
