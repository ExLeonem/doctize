package dev.doctize.javadoc;

import dev.doctize.markdown.CodeMarks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The markup comments of a snippet (JEP 413): {@code // @highlight}, {@code // @replace}, {@code // @link},
 * {@code // @start} and {@code // @end}, also written with {@code #} for languages such as properties or YAML.
 *
 * <p>A markup comment applies to its own line, to the next line if it ends with a colon, or, with a {@code region}
 * attribute, to every line up to the matching {@code @end}. The comments themselves are removed, and so is a line that
 * held nothing but markup. Highlights and links are written as {@link CodeMarks}.
 */
final class SnippetMarkup {

    private static final Pattern COMMENT = Pattern.compile("(?://|#)\\s*(@(?:start|end|highlight|replace|link)\\b.*)$");
    private static final Pattern TAG = Pattern.compile("@(start|end|highlight|replace|link)\\b");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|(\\S+)))?");

    /** One tag of a markup comment; an attribute without a value, e.g. an anonymous {@code region}, maps to "". */
    private record Markup(String name, Map<String, String> attributes) {

        Optional<String> region() {
            return Optional.ofNullable(attributes.get("region"));
        }

        Optional<String> get(String attribute) {
            return Optional.ofNullable(attributes.get(attribute));
        }
    }

    /** A line split into its code and its markup. */
    private record Line(String code, List<Markup> markup, boolean appliesToNext) {

        boolean onlyMarkup() {
            return !markup.isEmpty() && code.isBlank();
        }
    }

    private record Scoped(Markup markup, String region) {}

    private record Range(int start, int end, char mark, int link) {}

    /**
     * @param code the code with its markup applied and removed
     * @param links the URLs of the links in {@code code}, by index
     */
    record Result(String code, List<String> links) {}

    private SnippetMarkup() {}

    /**
     * The lines of the region {@code name}: from the line that starts it, with {@code @start} or any markup tag with
     * that region, up to its {@code @end}, still with their markup; empty if there is no such region.
     */
    static Optional<String> region(String text, String name) {
        List<String> lines = new ArrayList<>();
        Deque<String> open = new ArrayDeque<>();
        boolean inside = false;
        for (String raw : text.split("\n", -1)) {
            Line line = split(raw);
            for (Markup markup : line.markup()) {
                if (markup.name().equals("end")) {
                    close(open, markup.region().orElse(""));
                } else if (markup.region().isPresent()) {
                    open.push(markup.region().get());
                    inside |= markup.region().get().equals(name);
                }
            }
            if (inside) {
                lines.add(raw);
                if (!open.contains(name)) {
                    break;
                }
            }
        }
        return inside ? Optional.of(String.join("\n", lines)) : Optional.empty();
    }

    /**
     * Applies the markup of {@code text}.
     *
     * @param links resolves the target of a {@code @link} tag to a URL
     */
    static Result apply(String text, Function<String, Optional<String>> links) {
        List<String> out = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        List<Scoped> scoped = new ArrayList<>();
        Deque<String> open = new ArrayDeque<>();
        List<Markup> forNextLine = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            Line line = split(raw);
            List<Markup> active = new ArrayList<>(forNextLine);
            forNextLine.clear();
            List<String> ends = new ArrayList<>();
            for (Markup markup : line.markup()) {
                switch (markup.name()) {
                    case "end" -> ends.add(markup.region().orElse(""));
                    case "start" -> open.push(markup.region().orElse(""));
                    default -> {
                        if (markup.region().isPresent()) {
                            scoped.add(new Scoped(markup, markup.region().get()));
                            open.push(markup.region().get());
                        } else if (line.appliesToNext()) {
                            forNextLine.add(markup);
                        } else {
                            active.add(markup);
                        }
                    }
                }
            }
            if (!line.onlyMarkup()) {
                scoped.forEach(entry -> active.add(entry.markup()));
                out.add(render(line.code(), active, links, urls));
            }
            for (String region : ends) {
                String closed = close(open, region);
                scoped.removeIf(entry -> entry.region().equals(closed));
            }
        }
        return new Result(String.join("\n", out), List.copyOf(urls));
    }

    /** Closes the named region, or the innermost one for an empty name; returns the name of the closed region. */
    private static String close(Deque<String> open, String region) {
        if (region.isEmpty()) {
            return open.isEmpty() ? "" : open.pop();
        }
        open.removeFirstOccurrence(region);
        return region;
    }

    private static Line split(String raw) {
        Matcher comment = COMMENT.matcher(raw);
        if (!comment.find()) {
            return new Line(raw, List.of(), false);
        }
        String text = comment.group(1).strip();
        boolean appliesToNext = text.endsWith(":");
        if (appliesToNext) {
            text = text.substring(0, text.length() - 1);
        }
        List<Markup> markup = new ArrayList<>();
        Matcher tag = TAG.matcher(text);
        List<int[]> positions = new ArrayList<>();
        while (tag.find()) {
            positions.add(new int[] {tag.start(), tag.end()});
        }
        for (int i = 0; i < positions.size(); i++) {
            int end = i + 1 < positions.size() ? positions.get(i + 1)[0] : text.length();
            String name = text.substring(positions.get(i)[0] + 1, positions.get(i)[1]);
            markup.add(new Markup(name, attributes(text.substring(positions.get(i)[1], end))));
        }
        return new Line(raw.substring(0, comment.start()).stripTrailing(), markup, appliesToNext);
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3)
                    : matcher.group(4) != null ? matcher.group(4) : "";
            attributes.put(matcher.group(1), value);
        }
        return attributes;
    }

    /** Replacements first, then highlights and links on the replaced text. */
    private static String render(String code, List<Markup> markup, Function<String, Optional<String>> links, List<String> urls) {
        String line = code;
        for (Markup replace : markup) {
            if (replace.name().equals("replace")) {
                line = replace(line, replace);
            }
        }
        List<Range> ranges = new ArrayList<>();
        for (Markup entry : markup) {
            if (entry.name().equals("highlight")) {
                char mark = switch (entry.get("type").orElse("bold")) {
                    case "italic" -> CodeMarks.ITALIC;
                    case "highlighted" -> CodeMarks.HIGHLIGHTED;
                    default -> CodeMarks.BOLD;
                };
                matches(line, entry).forEach(match -> ranges.add(new Range(match[0], match[1], mark, -1)));
            } else if (entry.name().equals("link")) {
                Optional<String> url = entry.get("target").flatMap(links);
                if (url.isPresent()) {
                    int index = urls.size();
                    urls.add(url.get());
                    matches(line, entry).forEach(match -> ranges.add(new Range(match[0], match[1], CodeMarks.LINK, index)));
                }
            }
        }
        return ranges.isEmpty() ? line : marked(line, ranges);
    }

    private static String replace(String line, Markup markup) {
        String replacement = markup.get("replacement").orElse("");
        Optional<String> substring = markup.get("substring");
        if (substring.isPresent()) {
            return line.replace(substring.get(), replacement);
        }
        Optional<Pattern> regex = regex(markup);
        return regex.map(pattern -> pattern.matcher(line).replaceAll(replacement)).orElse(replacement);
    }

    /** Where the tag applies: every occurrence of {@code substring} or {@code regex}, else the line without its indentation. */
    private static List<int[]> matches(String line, Markup markup) {
        List<int[]> found = new ArrayList<>();
        Optional<String> substring = markup.get("substring");
        Optional<Pattern> regex = regex(markup);
        if (substring.isPresent() && !substring.get().isEmpty()) {
            for (int at = line.indexOf(substring.get()); at >= 0; at = line.indexOf(substring.get(), at + substring.get().length())) {
                found.add(new int[] {at, at + substring.get().length()});
            }
        } else if (regex.isPresent()) {
            Matcher matcher = regex.get().matcher(line);
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    found.add(new int[] {matcher.start(), matcher.end()});
                }
            }
        } else if (!line.isBlank()) {
            found.add(new int[] {line.length() - line.stripLeading().length(), line.length()});
        }
        return found;
    }

    private static Optional<Pattern> regex(Markup markup) {
        try {
            return markup.get("regex").map(Pattern::compile);
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }

    /** Writes the marks around their ranges; where ranges overlap, all marks are closed and the active ones opened again. */
    private static String marked(String line, List<Range> ranges) {
        StringBuilder out = new StringBuilder(line.length() + ranges.size() * 4);
        List<Range> previous = List.of();
        for (int i = 0; i <= line.length(); i++) {
            int position = i;
            List<Range> current = i == line.length()
                    ? List.of()
                    : ranges.stream().filter(range -> range.start() <= position && position < range.end()).toList();
            if (!current.equals(previous)) {
                previous.forEach(range -> out.append(CodeMarks.END));
                for (Range range : current) {
                    out.append(range.mark());
                    if (range.mark() == CodeMarks.LINK) {
                        out.append((char) (CodeMarks.LINK_BASE + range.link()));
                    }
                }
                previous = current;
            }
            if (i < line.length()) {
                out.append(line.charAt(i));
            }
        }
        return out.toString();
    }
}
