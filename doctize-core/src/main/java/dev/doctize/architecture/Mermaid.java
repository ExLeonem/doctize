package dev.doctize.architecture;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** What is needed to write Mermaid diagrams whatever the names in the code are. */
final class Mermaid {

    /** Words that Mermaid reads as syntax when they are an identifier. */
    private static final Set<String> RESERVED = Set.of(
            "end", "graph", "subgraph", "flowchart", "class", "classdiagram", "state", "default", "style", "linkstyle", "classdef", "click",
            "direction", "note", "as", "erdiagram", "call", "href", "callback", "namespace", "interface");

    private Mermaid() {}

    /** An identifier made of letters, digits and underscores that is no reserved word. */
    static String id(String name) {
        StringBuilder id = new StringBuilder();
        for (char c : name.toCharArray()) {
            id.append(Character.isLetterOrDigit(c) && c < 128 || c == '_' ? c : '_');
        }
        if (id.isEmpty() || Character.isDigit(id.charAt(0))) {
            id.insert(0, '_');
        }
        String text = id.toString();
        return RESERVED.contains(text.toLowerCase(Locale.ROOT)) ? text + "_" : text;
    }

    /**
     * A name for an entity or a column: letters, digits and underscores, with nothing else changed. Unlike {@link #id(String)} it does
     * not avoid reserved words, because in an entity-relationship diagram a column may be called {@code state} or {@code end}.
     */
    static String name(String name) {
        StringBuilder out = new StringBuilder();
        for (char c : name.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) && c < 128 || c == '_' ? c : '_');
        }
        if (out.isEmpty() || Character.isDigit(out.charAt(0))) {
            out.insert(0, '_');
        }
        return out.toString();
    }

    /**
     * Text for a label in quotes. Mermaid takes quotes and some other characters in labels as syntax, but reads entity codes
     * such as {@code #quot;}. HTML characters are made harmless as well.
     */
    static String label(String text) {
        return text.replace("&", "#amp;").replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;").replace("\n", " ");
    }

    /** Text as a member of a class or entity, where {@code < > { } " ,} would be read as syntax. */
    static String member(String text) {
        return text.replace('<', '~').replace('>', '~').replace("\"", "'").replace("{", "(").replace("}", ")");
    }
}
