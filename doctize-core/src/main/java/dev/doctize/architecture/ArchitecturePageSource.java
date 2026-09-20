package dev.doctize.architecture;

import dev.doctize.annotations.Doc;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.DiagramSettings;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.PageSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Contributes the pages of the architecture diagrams: the overview of the packages and how they depend on each other, class
 * diagrams, state diagrams, entity-relationship diagrams and flow charts, each as far as the settings switch it on. All are made from
 * the compiled classes, and the diagrams are Mermaid diagrams that the browser draws.
 *
 * <p>Problems found while analyzing, such as dependency cycles, broken rules, annotations that do not fit together, and a diagram
 * that was asked for but has nothing to show, are available as {@link #warnings()} once {@link #pages()} was called, so that a
 * build can fail on them.
 */
@Doc(group = "Architecture", order = 72)
public final class ArchitecturePageSource implements PageSource {

    private final ArchitectureSettings settings;
    private final List<Path> classRoots;
    private List<Page> pages;
    private final List<String> warnings = new ArrayList<>();

    /**
     * @param settings how to generate the diagrams
     * @param classRoots directories and jars with compiled classes
     */
    public ArchitecturePageSource(ArchitectureSettings settings, List<Path> classRoots) {
        this.settings = settings;
        this.classRoots = List.copyOf(classRoots);
    }

    @Override
    public List<Page> pages() {
        if (pages == null) {
            pages = build();
        }
        return pages;
    }

    /** Problems found in the architecture; complete after {@link #pages()}. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private List<Page> build() {
        ClassScanner.Scan scan = ClassScanner.scanTypes(classRoots);
        if (scan.types().isEmpty()) {
            warnings.add("The architecture diagrams are empty: no classes were found in " + (classRoots.isEmpty() ? "any location (pass the compiled classes with --classes)" : classRoots) + ".");
            return List.of();
        }
        DiagramSettings diagrams = settings.diagrams();
        List<Page> result = new ArrayList<>();
        if (diagrams.overview().enabled()) {
            overview(scan).ifPresent(result::add);
        }
        DiagramScope scope = new DiagramScope(settings, scan);
        if (diagrams.classes().enabled()) {
            DiagramResult classes = new ClassDiagrams(scope, diagrams.classes()).generate();
            add(result, classes, 1, "class-diagrams", diagrams.classes().title(), "Class diagrams of the code, one for each package or component.",
                    List.of("class diagrams", "uml", "types", "fields", "methods", "inheritance"),
                    "Class diagrams of the code: the types, what they contain and how they relate. `extends` and `implements` are drawn as hollow arrowheads,"
                            + " and a field that holds another type as an arrow with the name of the field. `+` is public, `#` protected, `~` package"
                            + " and `-` private.",
                    "Class diagrams are switched on, but there is no type to draw. Check rootPackage, include, exclude, the include and exclude types of the class diagrams, and the marks.");
        }
        if (diagrams.states().enabled()) {
            DiagramResult states = new StateDiagrams(scope, diagrams.states()).generate();
            add(result, states, 2, "state-diagrams", diagrams.states().title(), "The state machines of the code, from the enums marked with @StateMachine.",
                    List.of("state machine", "states", "transitions", "lifecycle"),
                    "State diagrams of the enums marked with `@StateMachine`. The constants are the states, and `@Transition` says what leads from one to another.",
                    "State diagrams are switched on, but no enum is marked with @StateMachine.");
        }
        if (diagrams.entities().enabled()) {
            DiagramResult entities = new EntityDiagrams(scope, diagrams.entities()).generate();
            add(result, entities, 3, "data-model", diagrams.entities().title(), "The database model: entities, columns and relations.",
                    List.of("entity", "database", "er diagram", "tables", "columns", "relations"),
                    "The database model: the entities, their columns and the relations between them, as the persistence annotations of the code say."
                            + " `PK` marks a primary key and `FK` a foreign key.",
                    "Entity diagrams are switched on, but no entity was found. Entities are classes annotated with " + String.join(" or ", diagrams.entities().entityAnnotations())
                            + ", or marked with @DiagramInclude(types = DiagramTypes.ENTITIES).");
        }
        if (diagrams.flows().enabled()) {
            DiagramResult flows = new FlowDiagrams(scope, diagrams.flows()).generate();
            add(result, flows, 4, "flows", diagrams.flows().title(), "What happens when a method is called, as flow charts.",
                    List.of("flow", "flow chart", "calls", "sequence"),
                    "Flow charts of what happens when a method is called: the methods it calls, and the ones they call, in the order the calls are in the code."
                            + " A chart shows what can be called, not under which condition.",
                    "Flow charts are switched on, but there is no method to start from. Mark one with @Flow, or name entry points in flows.entryPoints.");
        }
        return result;
    }

    private java.util.Optional<Page> overview(ClassScanner.Scan scan) {
        ArchitectureModel model = new ArchitectureAnalyzer(settings).analyze(scan);
        if (model.isEmpty()) {
            warnings.add("The architecture overview is empty: no package is left after rootPackage, include and exclude.");
            return java.util.Optional.empty();
        }
        model.cycles().forEach(cycle -> warnings.add("Architecture: dependency cycle between " + String.join(", ", cycle) + "."));
        model.violations().forEach(violation -> warnings.add("Architecture: " + sentence(violation.message())));
        return java.util.Optional.of(page(
                "architecture", settings.title(), 0, new ArchitecturePage(model, settings).markdown(),
                "How the packages depend on each other: the map, cycles, layers and rules.",
                List.of("architecture", "dependencies", "packages", "layers", "cycles", "modules")));
    }

    private void add(List<Page> pages, DiagramResult result, int offset, String slug, String title, String description, List<String> keywords,
            String introduction, String emptyWarning) {
        warnings.addAll(result.warnings());
        if (result.isEmpty()) {
            warnings.add(emptyWarning);
            return;
        }
        pages.add(page(slug, title, offset, DiagramPage.markdown(introduction, result.diagrams()), description, keywords));
    }

    private Page page(String slug, String title, int orderOffset, String markdown, String description, List<String> keywords) {
        return new Page(slug, title, settings.section(), "", settings.order() + orderOffset, description, keywords, new PageContent.Guide(markdown, ""));
    }

    /** A reason from the configuration may or may not end in a full stop already. */
    private static String sentence(String text) {
        return text.endsWith(".") || text.endsWith("!") || text.endsWith("?") ? text : text + ".";
    }
}
