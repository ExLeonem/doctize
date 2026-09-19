package dev.doctize.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs external programs for black-box tests. Output goes to files, so a chatty process can never block. */
public final class Processes {

    /** The outcome of a finished process. */
    public record Result(int exitCode, String out, String err) {

        @Override
        public String toString() {
            return "exit " + exitCode + "\n--- stdout ---\n" + out + "\n--- stderr ---\n" + err;
        }
    }

    private Processes() {}

    public static Result run(List<String> command, Map<String, String> environment, Path workingDirectory, Duration timeout) {
        try {
            Process process = start(command, environment, workingDirectory);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Timed out after " + timeout + ": " + command);
            }
            return new Result(process.exitValue(), read(workingDirectory, "stdout"), read(workingDirectory, "stderr"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        }
    }

    /** Starts a process that keeps running; the caller must destroy it. Output is written to {@code stdout}/{@code stderr} files in the directory. */
    public static Process start(List<String> command, Map<String, String> environment, Path workingDirectory) throws IOException {
        Files.createDirectories(workingDirectory);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectOutput(workingDirectory.resolve("stdout").toFile())
                .redirectError(workingDirectory.resolve("stderr").toFile());
        builder.environment().putAll(environment);
        return builder.start();
    }

    public static String read(Path workingDirectory, String stream) throws IOException {
        Path file = workingDirectory.resolve(stream);
        return Files.exists(file) ? Files.readString(file) : "";
    }
}
