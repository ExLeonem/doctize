package dev.doctize.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Builds the argument list of {@code doctize generate}: the one place that knows the command line of the tool. */
final class CliArguments {

    private final List<String> arguments = new ArrayList<>(List.of("generate"));

    static CliArguments generate() {
        return new CliArguments();
    }

    CliArguments option(String name, File value) {
        if (value != null) {
            arguments.add(name);
            arguments.add(value.getPath());
        }
        return this;
    }

    CliArguments option(String name, String value) {
        if (value != null && !value.isBlank()) {
            arguments.add(name);
            arguments.add(value);
        }
        return this;
    }

    CliArguments options(String name, Iterable<File> values) {
        values.forEach(value -> option(name, value));
        return this;
    }

    CliArguments flag(String name, boolean enabled) {
        if (enabled) {
            arguments.add(name);
        }
        return this;
    }

    CliArguments append(List<String> extra) {
        arguments.addAll(extra);
        return this;
    }

    List<String> build() {
        return List.copyOf(arguments);
    }
}
