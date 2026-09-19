package dev.doctize.javadoc;

import dev.doctize.text.Html;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces Javadoc inline tags ({@code {@code}}, {@code {@link}}, {@code {@literal}} ...) by their
 * Markdown equivalent. Code spans and fenced code blocks are left untouched.
 */
public final class InlineTagProcessor {

    /** {@code [label]} or {@code [text][label]}, but not {@code [text](url)} or {@code [id]: url}. */
    private static final Pattern REFERENCE_LINK = Pattern.compile("\\[([^\\[\\]\\n]+)](?:\\[([^\\[\\]\\n]+)])?(?![(:])");

    private static final Pattern PRE_CODE_START = Pattern.compile("<pre>\\s*\\{@code(?=\\s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRE_END = Pattern.compile("\\s*</pre>", Pattern.CASE_INSENSITIVE);

    private final LinkResolver links;
    private final Consumer<String> unresolvedLinks;

    public InlineTagProcessor(LinkResolver links) {
        this(links, reference -> {});
    }

    /**
     * @param links resolves the targets of link tags
     * @param unresolvedLinks told about the reference of every {@code {@link}} tag that {@code links} could not resolve
     */
    public InlineTagProcessor(LinkResolver links, Consumer<String> unresolvedLinks) {
        this.links = links;
        this.unresolvedLinks = unresolvedLinks;
    }

    public String process(String text) {
        StringBuilder out = new StringBuilder(text.length());
        StringBuilder segment = new StringBuilder();
        char fence = 0;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.stripLeading();
            boolean fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            if (fence != 0 || fenceLine) {
                flush(segment, out);
                out.append(line).append('\n');
                if (fenceLine) {
                    fence = fence == trimmed.charAt(0) ? 0 : (fence == 0 ? trimmed.charAt(0) : fence);
                }
            } else {
                segment.append(line).append('\n');
            }
        }
        flush(segment, out);
        out.setLength(Math.max(0, out.length() - 1));
        return out.toString();
    }

    private void flush(StringBuilder segment, StringBuilder out) {
        if (!segment.isEmpty()) {
            out.append(new Scanner(segment.toString()).run());
            segment.setLength(0);
        }
    }

    /** Walks one run of prose, copying code spans verbatim and translating tags. */
    private final class Scanner {
        private final String text;
        private final StringBuilder out = new StringBuilder();
        private int position;
        private int preDepth;

        Scanner(String text) {
            this.text = text;
        }

        String run() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == '`') {
                    copyCodeSpan();
                } else if (c == '<') {
                    if (!translatePreCode()) {
                        trackPreElement();
                    }
                } else if (!(text.startsWith("{@", position) && translateTag())
                        && !(c == '[' && translateReferenceLink())) {
                    out.append(c);
                    position++;
                }
            }
            return out.toString();
        }

        private void copyCodeSpan() {
            int runLength = backtickRun(position);
            int close = findClosingRun(position + runLength, runLength);
            int end = close < 0 ? position + runLength : close + runLength;
            out.append(text, position, end);
            position = end;
        }

        private int findClosingRun(int from, int runLength) {
            for (int i = from; i < text.length(); ) {
                if (text.charAt(i) != '`') {
                    i++;
                    continue;
                }
                int length = backtickRun(i);
                if (length == runLength) {
                    return i;
                }
                i += length;
            }
            return -1;
        }

        private int backtickRun(int from) {
            int end = from;
            while (end < text.length() && text.charAt(end) == '`') {
                end++;
            }
            return end - from;
        }

        /** Turns {@code <pre>{@code ...}</pre>} into a fenced code block, which gets highlighted and a copy button. */
        private boolean translatePreCode() {
            Matcher start = PRE_CODE_START.matcher(text).region(position, text.length());
            if (!start.lookingAt()) {
                return false;
            }
            int close = findClosingBrace(start.end());
            Matcher end = PRE_END.matcher(text).region(Math.max(close, 0) + 1, text.length());
            if (close < 0 || !end.lookingAt()) {
                return false;
            }
            String code = text.substring(start.end(), close).stripIndent().strip();
            out.append("\n\n```java\n").append(code).append("\n```\n\n");
            position = end.end();
            return true;
        }

        private void trackPreElement() {
            if (text.regionMatches(true, position, "<pre", 0, 4)) {
                preDepth++;
            } else if (text.regionMatches(true, position, "</pre", 0, 5)) {
                preDepth = Math.max(0, preDepth - 1);
            }
            out.append('<');
            position++;
        }

        /** Translates the tag at the current position; returns false if it is not a well-formed tag. */
        private boolean translateTag() {
            int nameStart = position + 2;
            int nameEnd = nameStart;
            while (nameEnd < text.length() && Character.isLetter(text.charAt(nameEnd))) {
                nameEnd++;
            }
            int close = findClosingBrace(nameEnd);
            if (nameEnd == nameStart || close < 0) {
                return false;
            }
            String name = text.substring(nameStart, nameEnd);
            String body = text.substring(nameEnd, close).strip();
            out.append(render(name, body));
            position = close + 1;
            return true;
        }

        /**
         * Translates JEP 467 reference links: {@code [Type#member]} and {@code [text][Type#member]}.
         * Brackets that do not name a documented element are left alone, as plain Markdown.
         */
        private boolean translateReferenceLink() {
            Matcher matcher = REFERENCE_LINK.matcher(text).region(position, text.length());
            if (!matcher.lookingAt()) {
                return false;
            }
            boolean hasText = matcher.group(2) != null;
            String reference = hasText ? matcher.group(2) : matcher.group(1);
            Optional<String> href = links.resolve(reference.strip());
            if (href.isEmpty() || preDepth > 0) {
                return false;
            }
            String label = hasText ? matcher.group(1) : Markdown.code(defaultLabel(reference.strip()));
            out.append(anchor(href.get(), label));
            position = matcher.end();
            return true;
        }

        private int findClosingBrace(int from) {
            int depth = 1;
            for (int i = from; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    return i;
                }
            }
            return -1;
        }

        private String render(String name, String body) {
            return switch (name) {
                case "code" -> Markdown.code(body);
                case "literal" -> Markdown.entities(body);
                case "link" -> link(body, true);
                case "linkplain" -> link(body, false);
                case "value" -> Markdown.code(body);
                case "summary" -> new InlineTagProcessor(links, unresolvedLinks).process(body);
                case "index" -> Markdown.entities(unquote(body.split("\\s+", 2)[0]));
                case "inheritDoc" -> "";
                case "snippet" -> Snippet.toMarkdown(body);
                default -> Markdown.entities("{@" + name + " " + body + "}");
            };
        }

        private String link(String body, boolean asCode) {
            String reference = leadingToken(body);
            String label = body.substring(reference.length()).strip();
            Optional<String> href = links.resolve(reference);
            if (href.isEmpty()) {
                unresolvedLinks.accept(reference);
            }
            String shown = label.isEmpty() ? defaultLabel(reference) : label;
            String formatted = asCode ? Markdown.code(shown) : Markdown.entities(shown);
            return href.map(url -> anchor(url, formatted)).orElse(formatted);
        }

        private static String anchor(String url, String content) {
            return "<a href=\"" + Html.escape(url) + "\">" + content + "</a>";
        }

        /** The reference ends at the first whitespace outside of parentheses, e.g. {@code get(K, V)}. */
        private static String leadingToken(String body) {
            int depth = 0;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (Character.isWhitespace(c) && depth <= 0) {
                    return body.substring(0, i);
                }
            }
            return body;
        }

        private static String defaultLabel(String reference) {
            return reference.startsWith("#") ? reference.substring(1) : reference.replace('#', '.');
        }

        private static String unquote(String term) {
            return term.length() > 1 && term.startsWith("\"") && term.endsWith("\"")
                    ? term.substring(1, term.length() - 1)
                    : term;
        }
    }
}
