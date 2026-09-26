package dev.doctize;

import dev.doctize.model.SourceLocation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Links from API pages to the lines in the repository where types and members are declared. */
final class SourceLinks implements Function<SourceLocation, Optional<String>> {

    private final String template;

    private SourceLinks(String template) {
        this.template = template;
    }

    /** No links at all. */
    static SourceLinks none() {
        return new SourceLinks("");
    }

    /**
     * @param sourceUrl the configured address with {@code {path}} and {@code {line}}; wins if set
     * @param repository the web address of the repository
     * @param revision the branch or commit to link to, if known
     */
    static SourceLinks of(String sourceUrl, String repository, Optional<String> revision) {
        if (!sourceUrl.isBlank()) {
            return new SourceLinks(sourceUrl);
        }
        return new SourceLinks(revision.map(ref -> forgeTemplate(repository, encode(ref))).orElse(""));
    }

    /** The address of a line in a file on the forges whose layout is known. */
    private static String forgeTemplate(String repository, String ref) {
        String base = repository.replaceAll("/+$", "");
        String host = host(base);
        return switch (host) {
            case "github.com" -> base + "/blob/" + ref + "/{path}#L{line}";
            case "gitlab.com" -> base + "/-/blob/" + ref + "/{path}#L{line}";
            case "bitbucket.org" -> base + "/src/" + ref + "/{path}#lines-{line}";
            case "codeberg.org" -> base + "/src/commit/" + ref + "/{path}#L{line}";
            default -> "";
        };
    }

    @Override
    public Optional<String> apply(SourceLocation location) {
        if (template.isEmpty() || location == null || location.path().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(template.replace("{path}", encode(location.path())).replace("{line}", String.valueOf(location.line())));
    }

    private static String host(String address) {
        try {
            String host = URI.create(address).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Percent-encodes each segment of a path; the slashes stay. */
    private static String encode(String path) {
        return Stream.of(path.split("/", -1)).map(SourceLinks::encodeSegment).collect(Collectors.joining("/"));
    }

    private static String encodeSegment(String segment) {
        StringBuilder out = new StringBuilder(segment.length());
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c < 128 && (Character.isLetterOrDigit(c) || "-._~".indexOf(c) >= 0)) {
                out.append((char) c);
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }
}
