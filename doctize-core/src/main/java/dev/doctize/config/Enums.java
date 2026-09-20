package dev.doctize.config;

import dev.doctize.DoctizeException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Reads the names of enum constants from configuration files: any case, {@code -} or {@code _} between words. */
final class Enums {

    private Enums() {}

    static <E extends Enum<E>> E parse(Class<E> type, String name, String setting) {
        String normalized = name.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return constant;
            }
        }
        String known = Arrays.stream(type.getEnumConstants()).map(constant -> constant.name().toLowerCase(Locale.ROOT).replace('_', '-')).collect(Collectors.joining(", "));
        throw new DoctizeException(setting + " must be one of " + known + ", but was " + name);
    }
}
