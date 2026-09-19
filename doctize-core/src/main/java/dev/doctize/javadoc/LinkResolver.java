package dev.doctize.javadoc;

import java.util.Optional;

/** Turns the reference of a {@code {@link ...}} tag into a URL, if the target is documented. */
@FunctionalInterface
public interface LinkResolver {

    /**
     * @param reference the reference as written, e.g. {@code Cache#get(Object)} or {@code com.acme.Cache}
     * @return the URL to link to, empty if the reference cannot be resolved
     */
    Optional<String> resolve(String reference);

    static LinkResolver none() {
        return reference -> Optional.empty();
    }
}
