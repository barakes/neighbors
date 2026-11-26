package io.github.barakes.neighbors.ladder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No such step, or it belongs to someone else (the answer is the same, on purpose). Answered with 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class StepNotFoundException extends RuntimeException {

    public StepNotFoundException() {
        super("No such step");
    }
}
