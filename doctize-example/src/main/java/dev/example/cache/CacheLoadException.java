package dev.example.cache;

import dev.doctize.annotations.Doc;

/** Thrown by {@link Cache#getOrLoad(Object, java.util.function.Function)} when a value cannot be loaded. */
@Doc(order = 5)
public class CacheLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param cause the failure of the loader, may be {@code null}
     */
    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
