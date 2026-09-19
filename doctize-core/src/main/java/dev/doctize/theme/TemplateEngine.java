package dev.doctize.theme;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import dev.doctize.DoctizeException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the theme's Mustache templates. Templates are logic-less: everything they show is
 * prepared by Java view objects, and partials are referenced as {@code {{> partials/footer}}}.
 */
public final class TemplateEngine {

    private final Mustache.Compiler compiler;
    private final Map<String, Template> compiled = new HashMap<>();
    private final Theme theme;

    public TemplateEngine(Theme theme) {
        this.theme = theme;
        this.compiler = Mustache.compiler()
                .withLoader(name -> new StringReader(theme.template(name)))
                .emptyStringIsFalse(true)
                .defaultValue("");
    }

    public String render(String templateName, Object context) {
        try {
            return compiled.computeIfAbsent(templateName, this::compile).execute(context);
        } catch (MustacheException e) {
            throw new DoctizeException("Cannot render template '" + templateName + "': " + e.getMessage(), e);
        }
    }

    private Template compile(String templateName) {
        try {
            return compiler.compile(theme.template(templateName));
        } catch (MustacheException e) {
            throw new DoctizeException("Invalid template '" + templateName + "': " + e.getMessage(), e);
        }
    }
}
