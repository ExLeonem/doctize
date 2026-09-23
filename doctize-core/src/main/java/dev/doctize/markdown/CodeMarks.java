package dev.doctize.markdown;

import dev.doctize.text.Html;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Emphasis and links inside a code block, as asked for by the markup of a {@code {@snippet}}. They travel inside the code
 * as private-use characters. The code is highlighted without them, so that marked words keep their colours, and the
 * marks are then woven into the highlighted HTML.
 */
public final class CodeMarks {

    public static final char BOLD = '\uE000';
    public static final char ITALIC = '\uE001';
    public static final char HIGHLIGHTED = '\uE002';
    /** Followed by one character, {@link #LINK_BASE} plus the index of the link's URL. */
    public static final char LINK = '\uE003';
    public static final char END = '\uE00F';
    public static final char LINK_BASE = '\uE100';
    private static final char LAST = '\uEFFF';
    /** Elements left empty where a mark was closed and opened again right away. */
    private static final Pattern EMPTY = Pattern.compile("<(b|i|mark)></\\1>|<a href=\"[^\"]*\"></a>");

    private record Mark(String open, String close) {}

    private CodeMarks() {}

    /** Whether {@code code} contains marks. */
    public static boolean present(String code) {
        return code.chars().anyMatch(c -> c >= BOLD && c <= LAST);
    }

    /** The code without its marks, for plain text. */
    public static String strip(String code) {
        return present(code) ? code.replaceAll("[\uE000-\uEFFF]", "") : code;
    }

    /**
     * Highlights code that may contain marks.
     *
     * @param links the URLs that link marks refer to by index
     * @param highlighter turns code without marks into HTML
     */
    public static String render(String code, List<String> links, UnaryOperator<String> highlighter) {
        if (!present(code)) {
            return highlighter.apply(code);
        }
        StringBuilder plain = new StringBuilder(code.length());
        Map<Integer, StringBuilder> closes = new HashMap<>();
        Map<Integer, StringBuilder> opens = new HashMap<>();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c < BOLD || c > LAST) {
                plain.append(c);
            } else if (c == END) {
                closes.computeIfAbsent(plain.length(), at -> new StringBuilder()).append(c);
            } else {
                StringBuilder open = opens.computeIfAbsent(plain.length(), at -> new StringBuilder()).append(c);
                if (c == LINK && i + 1 < code.length()) {
                    open.append(code.charAt(++i));
                }
            }
        }
        String html = highlighter.apply(plain.toString());
        // Closing marks go right after the text they end, opening marks right before the text they start, so that
        // they end up inside the elements of the highlighting where possible.
        StringBuilder woven = new StringBuilder(html.length() + 32);
        int position = 0;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = html.indexOf('>', i);
                if (end > 0) {
                    woven.append(html, i, end + 1);
                    i = end;
                    continue;
                }
            }
            append(woven, opens.get(position));
            int end = c == '&' ? html.indexOf(';', i) : -1;
            if (end > 0 && end - i < 10) {
                woven.append(html, i, end + 1);
                i = end;
            } else {
                woven.append(c);
            }
            position++;
            append(woven, closes.get(position));
        }
        append(woven, opens.get(position));
        return toHtml(woven.toString(), links);
    }

    private static void append(StringBuilder out, CharSequence marks) {
        if (marks != null) {
            out.append(marks);
        }
    }

    /**
     * Turns the marks in highlighted code into HTML elements. Where a mark crosses an element of the highlighting, it is
     * closed before that element and opened again after it, so that the result is well nested.
     *
     * @param links the URLs that link marks refer to by index
     */
    public static String toHtml(String html, List<String> links) {
        if (!present(html)) {
            return html;
        }
        StringBuilder out = new StringBuilder(html.length() + 64);
        Deque<Mark> open = new ArrayDeque<>();
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            int end = c == '<' && !open.isEmpty() ? html.indexOf('>', i) : -1;
            if (end > 0) {
                open.forEach(mark -> out.append(mark.close()));
                out.append(html, i, end + 1);
                open.descendingIterator().forEachRemaining(mark -> out.append(mark.open()));
                i = end;
                continue;
            }
            Mark mark = switch (c) {
                case BOLD -> new Mark("<b>", "</b>");
                case ITALIC -> new Mark("<i>", "</i>");
                case HIGHLIGHTED -> new Mark("<mark>", "</mark>");
                case LINK -> {
                    int index = html.charAt(++i) - LINK_BASE;
                    String url = index < links.size() ? links.get(index) : "";
                    yield new Mark("<a href=\"" + Html.escape(url) + "\">", "</a>");
                }
                default -> null;
            };
            if (mark != null) {
                open.push(mark);
                out.append(mark.open());
            } else if (c == END) {
                if (!open.isEmpty()) {
                    out.append(open.pop().close());
                }
            } else if (c < BOLD || c > LAST) {
                out.append(c);
            }
        }
        open.forEach(mark -> out.append(mark.close()));
        return EMPTY.matcher(out).replaceAll("");
    }
}
