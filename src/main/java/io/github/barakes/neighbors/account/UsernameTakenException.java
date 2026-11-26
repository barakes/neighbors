package io.github.barakes.neighbors.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException() {
        super("This username is taken");
    }
}
