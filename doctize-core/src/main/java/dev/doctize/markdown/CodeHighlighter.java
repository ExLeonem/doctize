package dev.doctize.markdown;

import dev.doctize.text.Html;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side syntax highlighting: wraps tokens in {@code <span class="tok-*">} so pages need no
 * JavaScript to look good. The colours come from the theme's CSS.
 *
 * <p>The tokenizer is deliberately simple (comments, strings, numbers, annotations, keywords,
 * type names, calls); languages it does not know are emitted escaped but otherwise untouched.
 */
public final class CodeHighlighter {

    private static final String C_LIKE_COMMENT = "//[^\\n]*|/\\*[\\s\\S]*?\\*/";
    private static final String C_LIKE_STRING =
            "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'";
    private static final String NUMBER =
            "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?)\\b";
    private static final String WORD = "[A-Za-z_$][\\w$]*";

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "sealed", "permits", "short", "static", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "var", "void",
            "volatile", "while", "record", "yield", "true", "false", "null", "non-sealed");
    private static final Set<String> KOTLIN_KEYWORDS = Set.of(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "val", "var", "when", "while", "by", "override", "private", "public", "internal", "data", "sealed",
            "companion", "import", "plugins", "dependencies");
    private static final Set<String> GROOVY_KEYWORDS = Set.of(
            "def", "class", "interface", "enum", "if", "else", "for", "while", "return", "new", "import",
            "package", "true", "false", "null", "in", "as", "this", "super", "try", "catch", "finally", "throw",
            "static", "final", "public", "private", "protected", "void", "implementation", "api", "plugins",
            "dependencies", "repositories", "id", "version", "task", "tasks");
    private static final Set<String> SCRIPT_KEYWORDS = Set.of(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "default", "else",
            "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof",
            "let", "new", "null", "return", "super", "switch", "this", "throw", "true", "try", "typeof",
            "undefined", "var", "while", "yield", "interface", "type", "of", "from");
    private static final Set<String> SHELL_KEYWORDS = Set.of(
            "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case", "esac", "function",
            "export", "cd", "echo", "exit", "return", "local", "sudo");
    private static final Set<String> LITERALS = Set.of("true", "false", "null", "yes", "no", "on", "off");

    private static final Language JAVA = cLike(JAVA_KEYWORDS, true);
    private static final Language KOTLIN = cLike(KOTLIN_KEYWORDS, true);
    private static final Language GROOVY = cLike(GROOVY_KEYWORDS, true);
    private static final Language SCRIPT = cLike(SCRIPT_KEYWORDS, true);
    private static final Language JSON = new Language(
            List.of(
                    new Rule("string", "\"(?:\\\\.|[^\"\\\\\\n])*\""),
                    new Rule("number", NUMBER),
                    new Rule("word", "[A-Za-z]+")),
            LITERALS,
            ":",
            false,
            false);
    private static final Language HASH_CONFIG = new Language(
            List.of(
                    new Rule("comment", "#[^\\n]*"),
                    new Rule("string", "\"(?:\\\\.|[^\"\\\\\\n])*\"|'[^'\\n]*'"),
                    new Rule("number", NUMBER),
                    new Rule("word", "[\\w.\\-/]+")),
            LITERALS,
            ":=",
            false,
            false);
    private static final Language SHELL = new Language(
            List.of(
                    new Rule("comment", "(?<![\\w$])#[^\\n]*"),
                    new Rule("string", "\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'"),
                    new Rule("annotation", "\\$\\{?\\w+}?"),
                    new Rule("number", NUMBER),
                    new Rule("word", "[A-Za-z_][\\w-]*")),
            SHELL_KEYWORDS,
            "=",
            false,
            false);
    private static final Language MARKUP = new Language(
            List.of(
                    new Rule("comment", "<!--[\\s\\S]*?-->"),
                    new Rule("keyword", "</?[A-Za-z][\\w:.-]*|/?>"),
                    new Rule("function", "[A-Za-z_:][\\w:.-]*(?=\\s*=)"),
                    new Rule("string", "\"[^\"]*\"|'[^']*'")),
            Set.of(),
            "",
            false,
            false);

    private static final Map<String, Language> LANGUAGES = Map.ofEntries(
            Map.entry("java", JAVA),
            Map.entry("kotlin", KOTLIN),
            Map.entry("kts", KOTLIN),
            Map.entry("groovy", GROOVY),
            Map.entry("gradle", GROOVY),
            Map.entry("javascript", SCRIPT),
            Map.entry("js", SCRIPT),
            Map.entry("typescript", SCRIPT),
            Map.entry("ts", SCRIPT),
            Map.entry("json", JSON),
            Map.entry("yaml", HASH_CONFIG),
            Map.entry("yml", HASH_CONFIG),
            Map.entry("properties", HASH_CONFIG),
            Map.entry("toml", HASH_CONFIG),
            Map.entry("bash", SHELL),
            Map.entry("sh", SHELL),
            Map.entry("shell", SHELL),
            Map.entry("zsh", SHELL),
            Map.entry("xml", MARKUP),
            Map.entry("html", MARKUP));

    /** Highlights code, leaving type names unlinked. */
    public String highlight(String code, String language) {
        return highlight(code, language, name -> Optional.empty());
    }

    /**
     * Highlights code; type names for which {@code typeLinks} yields a URL become links.
     *
     * @return HTML, safe to embed inside {@code <code>}
     */
    public String highlight(String code, String language, Function<String, Optional<String>> typeLinks) {
        Language grammar = LANGUAGES.get(language.toLowerCase());
        if (grammar == null) {
            return Html.escapeText(code);
        }
        StringBuilder out = new StringBuilder(code.length() * 2);
        Matcher matcher = grammar.pattern().matcher(code);
        int copied = 0;
        while (matcher.find()) {
            out.append(Html.escapeText(code.substring(copied, matcher.start())));
            Token token = grammar.classify(matcher, code);
            out.append(render(token, typeLinks));
            copied = matcher.end();
        }
        return out.append(Html.escapeText(code.substring(copied))).toString();
    }

    private static String render(Token token, Function<String, Optional<String>> typeLinks) {
        String text = Html.escapeText(token.text());
        if (token.cssClass().isEmpty()) {
            return text;
        }
        if (token.cssClass().equals("tok-t")) {
            Optional<String> href = typeLinks.apply(token.text());
            if (href.isPresent()) {
                return "<a class=\"tok-t\" href=\"" + Html.escape(href.get()) + "\">" + text + "</a>";
            }
        }
        return "<span class=\"" + token.cssClass() + "\">" + text + "</span>";
    }

    private static Language cLike(Set<String> keywords, boolean typeNames) {
        return new Language(
                List.of(
                        new Rule("comment", C_LIKE_COMMENT),
                        new Rule("string", C_LIKE_STRING),
                        new Rule("annotation", "@[A-Za-z_][\\w.]*"),
                        new Rule("number", NUMBER),
                        new Rule("word", WORD)),
                keywords,
                "",
                typeNames,
                true);
    }

    private record Rule(String name, String regex) {}

    private record Token(String text, String cssClass) {}

    /** A grammar: ordered token rules compiled into one alternation, plus word classification. */
    private static final class Language {
        private static final Map<String, String> CSS_CLASSES = Map.of(
                "comment", "tok-c",
                "string", "tok-s",
                "annotation", "tok-a",
                "number", "tok-n",
                "keyword", "tok-k",
                "function", "tok-f");

        private final List<Rule> rules;
        private final Pattern pattern;
        private final Set<String> keywords;
        private final String keySeparators;
        private final boolean typeNames;
        private final boolean calls;

        /**
         * @param keySeparators characters that, right after a word or string, mark it as a key
         * @param typeNames whether capitalized words are type names
         * @param calls whether a word directly followed by {@code (} is a call or declaration
         */
        Language(List<Rule> rules, Set<String> keywords, String keySeparators, boolean typeNames, boolean calls) {
            this.rules = List.copyOf(rules);
            this.keywords = keywords;
            this.keySeparators = keySeparators;
            this.typeNames = typeNames;
            this.calls = calls;
            List<String> alternatives = new ArrayList<>();
            for (Rule rule : rules) {
                alternatives.add("(?<" + rule.name() + ">" + rule.regex() + ")");
            }
            this.pattern = Pattern.compile(String.join("|", alternatives));
        }

        Pattern pattern() {
            return pattern;
        }

        Token classify(Matcher matcher, String code) {
            String text = matcher.group();
            for (Rule rule : rules) {
                if (matcher.group(rule.name()) == null) {
                    continue;
                }
                return switch (rule.name()) {
                    case "word" -> new Token(text, classifyWord(text, code, matcher.end()));
                    case "string" -> new Token(text, isPropertyKey(code, matcher.end()) ? "tok-p" : "tok-s");
                    default -> new Token(text, CSS_CLASSES.get(rule.name()));
                };
            }
            return new Token(text, "");
        }

        private String classifyWord(String word, String code, int end) {
            if (keywords.contains(word)) {
                return "tok-k";
            }
            if (isPropertyKey(code, end)) {
                return "tok-p";
            }
            if (calls && end < code.length() && code.charAt(end) == '(') {
                return "tok-f";
            }
            boolean capitalized = Character.isUpperCase(word.charAt(0)) && !word.equals(word.toUpperCase());
            return typeNames && capitalized ? "tok-t" : "";
        }

        private boolean isPropertyKey(String code, int end) {
            int i = end;
            while (i < code.length() && (code.charAt(i) == ' ' || code.charAt(i) == '\t')) {
                i++;
            }
            return i < code.length() && keySeparators.indexOf(code.charAt(i)) >= 0;
        }
    }
}
