package io.github.barakes.neighbors.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class NotSignedInException extends RuntimeException {

    public NotSignedInException() {
        super("Not signed in");
    }
}
