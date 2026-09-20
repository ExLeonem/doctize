package dev.doctize.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import dev.doctize.config.ArchitectureSettings.Direction;
import dev.doctize.config.DiagramSettings.Format;
import dev.doctize.config.DiagramSettings.GroupBy;
import dev.doctize.config.DiagramSettings.Kind;
import dev.doctize.config.DiagramSettings.Visibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagramSettingsTest {

    @TempDir
    Path directory;

    private DiagramSettings load(String diagrams) throws IOException {
        Path file = directory.resolve("doctize.yml");
        Files.writeString(file, "architecture:\n  enabled: true\n  diagrams:\n" + diagrams.indent(4));
        return ConfigLoader.load(file).architecture().diagrams();
    }

    @Test
    void withoutASettingOnlyTheOverviewIsOn() {
        DiagramSettings diagrams = DoctizeConfig.defaults().architecture().diagrams();

        assertThat(diagrams.overview().enabled()).isTrue();
        assertThat(diagrams.overview().format()).isEqualTo(Format.MERMAID);
        assertThat(diagrams.classes().enabled()).isFalse();
        assertThat(diagrams.states().enabled()).isFalse();
        assertThat(diagrams.entities().enabled()).isFalse();
        assertThat(diagrams.flows().enabled()).isFalse();
    }

    @Test
    void aKindCanBeSwitchedOnAndOffWithAPlainBoolean() throws IOException {
        DiagramSettings diagrams = load("overview: false\nclasses: true\nstates: true\nentities: false\nflows: true");

        assertThat(diagrams.overview().enabled()).isFalse();
        assertThat(diagrams.classes().enabled()).isTrue();
        assertThat(diagrams.states().enabled()).isTrue();
        assertThat(diagrams.entities().enabled()).isFalse();
        assertThat(diagrams.flows().enabled()).isTrue();
        assertThat(diagrams.classes().groupBy()).as("a boolean keeps the defaults of the rest").isEqualTo(GroupBy.PACKAGE);
    }

    @Test
    void aMappingSwitchesTheKindOnUnlessItSaysOtherwise() throws IOException {
        DiagramSettings diagrams = load("classes:\n  groupBy: all\nstates:\n  enabled: false\n  events: false");

        assertThat(diagrams.classes().enabled()).as("mentioned, so on").isTrue();
        assertThat(diagrams.states().enabled()).isFalse();
        assertThat(diagrams.states().events()).isFalse();
    }

    @Test
    void theDefaultsOfClassDiagramsShowTheDataAndThePublicApiButNotTheNoise() {
        DiagramSettings.ClassDiagram classes = DiagramSettings.ClassDiagram.ofFlag(true);

        assertThat(classes.fields()).isTrue();
        assertThat(classes.fieldVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(classes.methods()).isTrue();
        assertThat(classes.methodVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(classes.constructors()).isFalse();
        assertThat(classes.accessors()).isFalse();
        assertThat(classes.stereotypes()).isTrue();
        assertThat(classes.inheritance()).isTrue();
        assertThat(classes.associations()).isTrue();
        assertThat(classes.dependencies()).isFalse();
        assertThat(classes.neighbours()).isFalse();
        assertThat(classes.groupBy()).isEqualTo(GroupBy.PACKAGE);
        assertThat(classes.maxClasses()).isEqualTo(30);
        assertThat(classes.onlyMarked()).isFalse();
        assertThat(classes.direction()).isEqualTo(Direction.TOP_DOWN);
        assertThat(classes.title()).isEqualTo("Class diagrams");
    }

    @Test
    void everyOptionOfClassDiagramsCanBeGiven() throws IOException {
        DiagramSettings.ClassDiagram classes = load("""
                classes:
                  title: Types
                  fields: false
                  fieldVisibility: package
                  methods: true
                  methodVisibility: protected
                  constructors: true
                  accessors: true
                  stereotypes: false
                  inheritance: false
                  associations: false
                  dependencies: true
                  neighbours: true
                  groupBy: component
                  maxClasses: 12
                  onlyMarked: true
                  includeTypes: ["com.acme.**.*Service"]
                  excludeTypes: ["**.*Test"]
                  direction: left-right
                """).classes();

        assertThat(classes.title()).isEqualTo("Types");
        assertThat(classes.fields()).isFalse();
        assertThat(classes.fieldVisibility()).isEqualTo(Visibility.PACKAGE);
        assertThat(classes.methodVisibility()).isEqualTo(Visibility.PROTECTED);
        assertThat(classes.constructors()).isTrue();
        assertThat(classes.accessors()).isTrue();
        assertThat(classes.stereotypes()).isFalse();
        assertThat(classes.inheritance()).isFalse();
        assertThat(classes.associations()).isFalse();
        assertThat(classes.dependencies()).isTrue();
        assertThat(classes.neighbours()).isTrue();
        assertThat(classes.groupBy()).isEqualTo(GroupBy.COMPONENT);
        assertThat(classes.maxClasses()).isEqualTo(12);
        assertThat(classes.onlyMarked()).isTrue();
        assertThat(classes.includeTypes()).containsExactly("com.acme.**.*Service");
        assertThat(classes.excludeTypes()).containsExactly("**.*Test");
        assertThat(classes.direction()).isEqualTo(Direction.LEFT_RIGHT);
    }

    @Test
    void entityDiagramsKnowJpaOutOfTheBoxAndCanBeToldAboutOtherAnnotations() throws IOException {
        DiagramSettings.EntityDiagram defaults = DiagramSettings.EntityDiagram.ofFlag(true);
        DiagramSettings.EntityDiagram custom = load("""
                entities:
                  columns: false
                  keys: false
                  relationships: false
                  tableNames: true
                  onlyMarked: true
                  entityAnnotations: [io.micronaut.data.annotation.MappedEntity]
                  idAnnotations: [io.micronaut.data.annotation.Id]
                  excludeTypes: ["**.Audit*"]
                """).entities();

        assertThat(defaults.entityAnnotations()).containsExactly("jakarta.persistence.Entity", "javax.persistence.Entity");
        assertThat(defaults.idAnnotations()).contains("jakarta.persistence.Id", "javax.persistence.EmbeddedId");
        assertThat(defaults.columns() && defaults.keys() && defaults.relationships()).isTrue();
        assertThat(defaults.tableNames()).isFalse();
        assertThat(custom.columns() || custom.keys() || custom.relationships()).isFalse();
        assertThat(custom.tableNames()).isTrue();
        assertThat(custom.onlyMarked()).isTrue();
        assertThat(custom.entityAnnotations()).containsExactly("io.micronaut.data.annotation.MappedEntity");
        assertThat(custom.idAnnotations()).containsExactly("io.micronaut.data.annotation.Id");
        assertThat(custom.excludeTypes()).containsExactly("**.Audit*");
    }

    @Test
    void flowDiagramsHaveSensibleLimitsAndEveryOptionCanBeGiven() throws IOException {
        DiagramSettings.FlowDiagram defaults = DiagramSettings.FlowDiagram.ofFlag(true);
        DiagramSettings.FlowDiagram custom = load("""
                flows:
                  title: Journeys
                  depth: 7
                  maxNodes: 80
                  implementations: false
                  constructors: true
                  accessors: true
                  numbered: false
                  direction: left-right
                  entryPoints: ["com.acme.web.*Controller#*", "com.acme.jobs.Nightly#run"]
                  includeTypes: ["com.acme.**"]
                  excludeTypes: ["**.internal.**"]
                """).flows();

        assertThat(defaults.depth()).isEqualTo(4);
        assertThat(defaults.maxNodes()).isEqualTo(40);
        assertThat(defaults.implementations()).isTrue();
        assertThat(defaults.constructors()).isFalse();
        assertThat(defaults.numbered()).isTrue();
        assertThat(custom.title()).isEqualTo("Journeys");
        assertThat(custom.depth()).isEqualTo(7);
        assertThat(custom.maxNodes()).isEqualTo(80);
        assertThat(custom.implementations()).isFalse();
        assertThat(custom.constructors()).isTrue();
        assertThat(custom.accessors()).isTrue();
        assertThat(custom.numbered()).isFalse();
        assertThat(custom.direction()).isEqualTo(Direction.LEFT_RIGHT);
        assertThat(custom.entryPoints()).extracting(DiagramSettings.EntryPoint::method).containsExactly("com.acme.web.*Controller#*", "com.acme.jobs.Nightly#run");
    }

    @Test
    void anEntryPointIsAStringOrAMappingWithATitleAndADescription() throws IOException {
        DiagramSettings.FlowDiagram flows = load("""
                flows:
                  entryPoints:
                    - com.acme.web.*Controller#*
                    - method: com.acme.Checkout#run
                      name: Check out
                      description: From the cart to the receipt.
                    - method: com.acme.Refund#run
                      depth: 2
                """).flows();

        assertThat(flows.entryPoints()).hasSize(3);
        assertThat(flows.entryPoints().get(0)).isEqualTo(new DiagramSettings.EntryPoint("com.acme.web.*Controller#*", "", ""));
        assertThat(flows.entryPoints().get(1).name()).isEqualTo("Check out");
        assertThat(flows.entryPoints().get(1).description()).isEqualTo("From the cart to the receipt.");
        assertThat(flows.entryPoints().get(1).typePattern()).isEqualTo("com.acme.Checkout");
        assertThat(flows.entryPoints().get(1).methodPattern()).isEqualTo("run");
        assertThat(flows.entryPoints().get(2).name()).isEmpty();
        assertThat(flows.entryPoints().get(2).depth()).as("its own depth").isEqualTo(2);
        assertThat(flows.entryPoints().get(1).depth()).as("the depth of the flows").isZero();
    }

    @Test
    void theOverviewCanLeaveOutPackagesThatTheOtherDiagramsKeep() throws IOException {
        DiagramSettings diagrams = load("overview:\n  exclude: [\"com.acme\", \"**.internal\"]\nclasses: true");

        assertThat(diagrams.overview().exclude()).containsExactly("com.acme", "**.internal");
        assertThat(diagrams.overview().enabled()).isTrue();
        assertThat(diagrams.classes().enabled()).isTrue();
        assertThat(DiagramSettings.defaults().overview().exclude()).isEmpty();
        assertThat(DiagramSettings.defaults().with(Kind.OVERVIEW).overview().exclude()).isEmpty();
    }

    @Test
    void stateDiagramsAreDrawnLeftToRightUnlessToldOtherwise() {
        assertThat(DiagramSettings.StateDiagram.ofFlag(true).direction()).isEqualTo(Direction.LEFT_RIGHT);
        assertThat(DiagramSettings.StateDiagram.ofFlag(true).events()).isTrue();
    }

    @Test
    void theOverviewCanBeAskedForAsSvgForSitesThatNeedNoScript() throws IOException {
        assertThat(load("overview:\n  format: svg").overview().format()).isEqualTo(Format.SVG);
        assertThat(load("overview:\n  format: Mermaid").overview().format()).isEqualTo(Format.MERMAID);
    }

    @Test
    void aKindCanBeSwitchedOnInCode() {
        DiagramSettings diagrams = DiagramSettings.defaults().with(Kind.CLASSES).with(Kind.FLOWS);

        assertThat(diagrams.classes().enabled()).isTrue();
        assertThat(diagrams.flows().enabled()).isTrue();
        assertThat(diagrams.states().enabled()).isFalse();
        assertThat(diagrams.anyEnabled()).isTrue();
        assertThat(ArchitectureSettings.defaults().withDiagram(Kind.STATES).enabled()).as("asking for a diagram is asking for the architecture").isTrue();
    }

    @Test
    void kindsAreReadInAnyCaseAndAreNamedLikeTheConfigurationKeys() {
        assertThat(Kind.of("Classes")).isEqualTo(Kind.CLASSES);
        assertThat(Kind.of("ENTITIES")).isEqualTo(Kind.ENTITIES);
        assertThat(Kind.FLOWS.id()).isEqualTo("flows");
        assertThatThrownBy(() -> Kind.of("sequence")).isInstanceOf(DoctizeException.class).hasMessageContaining("diagram type must be one of overview, classes, states, entities, flows");
    }

    @Test
    void aMisspelledOrInvalidSettingIsExplained() {
        assertThatThrownBy(() -> load("clases: true")).isInstanceOf(DoctizeException.class).hasMessageContaining("clases");
        assertThatThrownBy(() -> load("classes:\n  visibilty: public")).hasMessageContaining("visibilty");
        assertThatThrownBy(() -> load("classes:\n  methodVisibility: secret")).hasMessageContaining("methodVisibility must be one of private, package, protected, public");
        assertThatThrownBy(() -> load("classes:\n  groupBy: file")).hasMessageContaining("groupBy must be one of package, component, all");
        assertThatThrownBy(() -> load("classes:\n  maxClasses: 0")).hasMessageContaining("maxClasses must be at least 1");
        assertThatThrownBy(() -> load("flows:\n  depth: 0")).hasMessageContaining("depth must be at least 1");
        assertThatThrownBy(() -> load("flows:\n  maxNodes: 1")).hasMessageContaining("maxNodes must be at least 2");
        assertThatThrownBy(() -> load("flows:\n  entryPoints: [com.acme.Checkout]")).hasMessageContaining("Type#method");
        assertThatThrownBy(() -> load("flows:\n  entryPoints:\n    - name: No method\n")).hasMessageContaining("Type#method");
        assertThatThrownBy(() -> load("flows:\n  entryPoints:\n    - method: a.B#c\n      depth: -1\n")).hasMessageContaining("depth of the entry point a.B#c must be at least 1");
        assertThatThrownBy(() -> load("overview:\n  format: png")).hasMessageContaining("format must be one of mermaid, svg");
    }

    @Test
    void theOptionsOfTheMapKeepWorkingNextToTheDiagrams() throws IOException {
        Path file = directory.resolve("doctize.yml");
        Files.writeString(file, "architecture:\n  enabled: true\n  rootPackage: com.acme\n  cycles: false\n  diagrams:\n    classes: true\n");

        ArchitectureSettings settings = ConfigLoader.load(file).architecture();

        assertThat(settings.rootPackage()).isEqualTo("com.acme");
        assertThat(settings.cycles()).isFalse();
        assertThat(settings.diagrams().classes().enabled()).isTrue();
    }
}
