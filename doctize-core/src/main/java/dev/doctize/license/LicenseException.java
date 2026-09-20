package dev.doctize.license;

import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;

/** A license could not be read, or is not valid. The message says what is wrong and is meant for end users. */
@Doc(group = "Licensing", order = 64)
public final class LicenseException extends DoctizeException {

    private static final long serialVersionUID = 1L;

    public LicenseException(String message) {
        super(message);
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
