package dev.doctize.javadoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits the raw text of a Javadoc comment into its description and block tags.
 *
 * <p>Lines starting with {@code @} inside fenced code blocks or {@code <pre>} elements are content,
 * not tags, so annotations in code samples survive.
 */
public final class JavadocParser {

    private static final Pattern BLOCK_TAG = Pattern.compile("^@([A-Za-z][\\w.-]*)(?:\\s+(.*))?$", Pattern.DOTALL);
    private static final Pattern PRE_OPEN = Pattern.compile("<pre[\\s>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRE_CLOSE = Pattern.compile("</pre\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TAGS_WITH_SUBJECT = Set.of("param", "throws", "exception");

    private JavadocParser() {}

    public static JavadocComment parse(String rawComment) {
        if (rawComment == null || rawComment.isBlank()) {
            return JavadocComment.EMPTY;
        }
        Collector collector = new Collector();
        CodeContext context = new CodeContext();
        for (String line : rawComment.stripTrailing().stripIndent().split("\n", -1)) {
            Matcher tag = BLOCK_TAG.matcher(line.stripLeading());
            if (!context.inCode() && tag.matches()) {
                collector.startTag(tag.group(1), tag.group(2) == null ? "" : tag.group(2));
            } else {
                collector.append(line);
            }
            context.update(line);
        }
        return collector.build();
    }

    /** Accumulates the description and the tags while lines are fed in. */
    private static final class Collector {
        private final StringBuilder description = new StringBuilder();
        private final List<BlockTag> tags = new ArrayList<>();
        private String currentName;
        private final StringBuilder currentText = new StringBuilder();

        void startTag(String name, String firstLine) {
            finishTag();
            currentName = name;
            currentText.append(firstLine);
        }

        void append(String line) {
            StringBuilder target = currentName == null ? description : currentText;
            if (!target.isEmpty()) {
                target.append('\n');
            }
            target.append(line);
        }

        JavadocComment build() {
            finishTag();
            return new JavadocComment(description.toString().strip(), tags);
        }

        private void finishTag() {
            if (currentName == null) {
                return;
            }
            String text = withoutHangingIndent(currentText.toString()).strip();
            String subject = "";
            if (TAGS_WITH_SUBJECT.contains(currentName)) {
                String[] parts = text.split("\\s+", 2);
                subject = parts[0];
                text = parts.length > 1 ? parts[1].strip() : "";
            }
            tags.add(new BlockTag(currentName, subject, text));
            currentName = null;
            currentText.setLength(0);
        }
    }

    /** Continuation lines of a tag are conventionally indented; that indentation is not content. */
    private static String withoutHangingIndent(String tagText) {
        int firstBreak = tagText.indexOf('\n');
        return firstBreak < 0 ? tagText : tagText.substring(0, firstBreak + 1) + tagText.substring(firstBreak + 1).stripIndent();
    }

    /** Tracks whether the current line lies inside a fenced code block or a {@code <pre>} element. */
    private static final class CodeContext {
        private char fence;
        private int preDepth;

        boolean inCode() {
            return fence != 0 || preDepth > 0;
        }

        void update(String line) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                char marker = trimmed.charAt(0);
                fence = fence == marker ? 0 : (fence == 0 ? marker : fence);
                return;
            }
            if (fence == 0) {
                preDepth = Math.max(0, preDepth + count(PRE_OPEN, line) - count(PRE_CLOSE, line));
            }
        }

        private static int count(Pattern pattern, String text) {
            int count = 0;
            for (Matcher matcher = pattern.matcher(text); matcher.find(); ) {
                count++;
            }
            return count;
        }
    }
}
