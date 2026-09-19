package dev.doctize.processor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.ModelLocator;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocProcessorTest {

    @TempDir
    Path output;

    private static JavaFileObject source(String path, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + path), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    private SourceModel compile(JavaFileObject... sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = List.of(
                "-d", output.toString(), "-classpath", System.getProperty("java.class.path"),
                "-processor", DocProcessor.class.getName());
        Boolean success = compiler.getTask(new StringWriter(), null, diagnostics, options, null, List.of(sources)).call();
        assertThat(success).as(diagnostics.getDiagnostics().toString()).isTrue();
        assertThat(diagnostics.getDiagnostics()).noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
        return ModelLocator.load(output);
    }

    private static SourceElement element(SourceModel model, String qualifiedName) {
        return model.elements().stream()
                .filter(e -> e.declaration().qualifiedName().equals(qualifiedName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no element " + qualifiedName + " in " + model.elements()));
    }

    private static Declaration member(SourceElement type, String name) {
        return type.members().stream().map(SourceElement::declaration).filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void extractsTypesCommentsPlacementAndMembers() throws IOException {
        SourceModel model = compile(source("demo/Store.java", """
                package demo;

                import dev.doctize.annotations.Doc;
                import dev.doctize.annotations.DocIgnore;
                import java.io.IOException;
                import java.util.List;

                /** A store.
                 *
                 * @since 2
                 */
                @Doc(section = "Data", title = "The Store", order = 3, group = "core", keywords = {"db", "kv"})
                public class Store<T extends Comparable<T>> implements AutoCloseable {

                    /** Maximum size. */
                    public static final int MAX = 10;

                    private int hidden;

                    /** Creates a store. */
                    public Store() {}

                    /**
                     * Finds things.
                     * @param names the names
                     * @return the matches
                     * @throws IOException if reading fails
                     */
                    @Deprecated(since = "1")
                    public <R> List<R> find(String... names) throws IOException { return null; }

                    @DocIgnore
                    public void ignored() {}

                    void packagePrivate() {}

                    protected void guarded() {}

                    @Override
                    public void close() {}
                }
                """));

        SourceElement store = element(model, "demo.Store");

        assertThat(store.role()).isEqualTo(Role.API);
        assertThat(store.declaration().kind()).isEqualTo(ElementKind.CLASS);
        assertThat(store.declaration().signature()).isEqualTo("public class Store<T extends Comparable<T>> implements AutoCloseable");
        assertThat(store.comment()).contains("A store.").contains("@since 2");
        assertThat(store.placement().section()).isEqualTo("Data");
        assertThat(store.placement().title()).isEqualTo("The Store");
        assertThat(store.placement().order()).isEqualTo(3);
        assertThat(store.placement().group()).isEqualTo("core");
        assertThat(store.keywords()).containsExactly("db", "kv");

        assertThat(store.members()).extracting(m -> m.declaration().name())
                .containsExactly("MAX", "Store", "find", "guarded", "close");
        assertThat(member(store, "MAX").signature()).isEqualTo("public static final int MAX = 10");

        Declaration find = member(store, "find");
        assertThat(find.signature()).isEqualTo("public <R> List<R> find(String... names) throws IOException");
        assertThat(find.parameters()).extracting(p -> p.name() + ":" + p.type()).containsExactly("names:String[]");
        assertThat(find.returnType()).isEqualTo("List<R>");
        assertThat(find.thrownTypes()).containsExactly("IOException");
        assertThat(find.annotations()).containsExactly("@Deprecated(since = \"1\")");
        assertThat(find.isDeprecated()).isTrue();
        assertThat(member(store, "Store").kind()).isEqualTo(ElementKind.CONSTRUCTOR);
    }

    @Test
    void readsMarkdownDocComments() throws IOException {
        SourceModel model = compile(source("demo/Md.java", """
                package demo;

                import dev.doctize.annotations.Doc;

                /// # Heading
                ///
                /// Some `code` and a list:
                ///
                /// - one
                /// - two
                ///
                /// @param <T> the type
                @Doc
                public interface Md<T> {
                    /// Does it.
                    void run();
                }
                """));

        SourceElement md = element(model, "demo.Md");

        assertThat(md.comment()).contains("# Heading").contains("Some `code` and a list:").contains("- one").contains("@param <T> the type");
        assertThat(md.members().getFirst().comment()).contains("Does it.");
        assertThat(md.members().getFirst().declaration().signature()).isEqualTo("void run()");
    }

    @Test
    void hidesMembersTheCompilerGeneratesForEnumsAndRecords() throws IOException {
        SourceModel model = compile(
                source("demo/Color.java", """
                        package demo;
                        import dev.doctize.annotations.Doc;
                        /** Colors. */
                        @Doc
                        public enum Color {
                            /** Warm. */ RED,
                            BLUE;
                            /** Mixes. */
                            public Color mix(Color other) { return this; }
                        }
                        """),
                source("demo/Point.java", """
                        package demo;
                        import dev.doctize.annotations.Doc;
                        /**
                         * A point.
                         * @param x the x
                         * @param y the y
                         */
                        @Doc
                        public record Point(int x, int y) {
                            /** Distance to the origin. */
                            public double norm() { return 0; }
                        }
                        """));

        SourceElement color = element(model, "demo.Color");
        SourceElement point = element(model, "demo.Point");

        assertThat(color.members()).extracting(m -> m.declaration().name()).containsExactly("RED", "BLUE", "mix");
        assertThat(color.members().getFirst().declaration().kind()).isEqualTo(ElementKind.ENUM_CONSTANT);
        assertThat(color.declaration().signature()).isEqualTo("public enum Color");
        assertThat(point.declaration().signature()).isEqualTo("public record Point(int x, int y)");
        assertThat(point.members()).extracting(m -> m.declaration().name()).containsExactly("norm");
    }

    @Test
    void interfacesHideImplicitModifiersAndAnnotationTypesShowDefaults() throws IOException {
        SourceModel model = compile(
                source("demo/Api.java", """
                        package demo;
                        import dev.doctize.annotations.Doc;
                        /** Api. */
                        @Doc
                        public interface Api {
                            int LIMIT = 5;
                            /** Calls. */
                            String call(int n);
                            default void other() {}
                            static Api of() { return null; }
                        }
                        """),
                source("demo/Marker.java", """
                        package demo;
                        import dev.doctize.annotations.Doc;
                        /** Marker. */
                        @Doc
                        public @interface Marker {
                            /** The name. */
                            String name() default "x";
                        }
                        """));

        SourceElement api = element(model, "demo.Api");

        assertThat(member(api, "call").signature()).isEqualTo("String call(int n)");
        assertThat(member(api, "other").signature()).isEqualTo("default void other()");
        assertThat(member(api, "of").signature()).isEqualTo("static Api of()");
        assertThat(member(api, "LIMIT").signature()).isEqualTo("int LIMIT = 5");
        assertThat(element(model, "demo.Marker").declaration().signature()).isEqualTo("public @interface Marker");
        assertThat(member(element(model, "demo.Marker"), "name").signature()).isEqualTo("String name() default \"x\"");
    }

    @Test
    void docPageOnPackagesAndTypesBecomesAProsePage() throws IOException {
        SourceModel model = compile(
                source("demo/package-info.java", """
                        /// # Concepts
                        ///
                        /// Explains things.
                        @DocPage(section = "Guides", order = 2)
                        package demo;

                        import dev.doctize.annotations.DocPage;
                        """),
                source("demo/Holder.java", """
                        package demo;
                        import dev.doctize.annotations.DocPage;
                        /** Prose. */
                        @DocPage(title = "Holder page")
                        final class Holder {}
                        """));

        SourceElement pkg = element(model, "demo");
        SourceElement holder = element(model, "demo.Holder");

        assertThat(pkg.role()).isEqualTo(Role.PAGE);
        assertThat(pkg.declaration().kind()).isEqualTo(ElementKind.PACKAGE);
        assertThat(pkg.comment()).contains("# Concepts").contains("Explains things.");
        assertThat(pkg.placement().section()).isEqualTo("Guides");
        assertThat(holder.role()).isEqualTo(Role.PAGE);
        assertThat(holder.placement().title()).isEqualTo("Holder page");
        assertThat(holder.members()).isEmpty();
    }

    @Test
    void writesNoModelWhenNothingIsAnnotated() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.getTask(new StringWriter(), null, null,
                List.of("-d", output.toString(), "-classpath", System.getProperty("java.class.path"), "-processor", DocProcessor.class.getName()),
                null, List.of(source("demo/Plain.java", "package demo; public class Plain {}"))).call();

        assertThat(Files.exists(output.resolve("META-INF/doctize/model.json"))).isFalse();
    }
}
