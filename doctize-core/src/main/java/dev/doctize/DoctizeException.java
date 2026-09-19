package dev.doctize;

import dev.doctize.annotations.Doc;

/** Signals that documentation could not be generated. The message is meant for end users. */
@Doc(group = "Generator", order = 13)
public class DoctizeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DoctizeException(String message) {
        super(message);
    }

    public DoctizeException(String message, Throwable cause) {
        super(message, cause);
    }
}
