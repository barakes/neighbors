package io.github.barakes.neighbors.ladder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The step's text or the new order doesn't follow the rules. Answered with 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidStepException extends RuntimeException {

    public InvalidStepException(String message) {
        super(message);
    }
}
