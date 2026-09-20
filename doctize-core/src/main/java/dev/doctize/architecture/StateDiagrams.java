package dev.doctize.architecture;

import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.architecture.TypeInfo.FieldInfo;
import dev.doctize.architecture.TypeInfo.MethodInfo;
import dev.doctize.config.ArchitectureSettings.Direction;
import dev.doctize.config.DiagramSettings.Kind;
import dev.doctize.config.DiagramSettings.StateDiagram;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes state diagrams of the enums marked with {@code @StateMachine}. The states are the constants of the enum. The transitions
 * are what {@code @Transition} says, on the constants or on the methods that make the change: nothing about them can be found in
 * a class file, because what a state change means is known to the author only. So problems in the annotations are reported.
 */
final class StateDiagrams {

    private final DiagramScope scope;
    private final StateDiagram settings;
    private final List<String> warnings = new ArrayList<>();

    /** One state machine while it is collected. */
    private static final class Machine {
        final TypeInfo type;
        final String name;
        final String initial;
        final Set<String> terminal;
        final String description;
        final List<String> states = new ArrayList<>();
        final Set<Transition> transitions = new LinkedHashSet<>();

        Machine(TypeInfo type, AnnotationInfo annotation) {
            this.type = type;
            String given = annotation.string("name", "");
            this.name = given.isBlank() ? type.displayName() : given;
            this.initial = annotation.string("initial", "");
            this.terminal = new LinkedHashSet<>(annotation.strings("terminal"));
            this.description = annotation.string("description", "");
        }
    }

    private record Transition(String from, String to, String event, String declaredAt) {}

    StateDiagrams(DiagramScope scope, StateDiagram settings) {
        this.scope = scope;
        this.settings = settings;
    }

    DiagramResult generate() {
        Map<String, Machine> machines = machines();
        collectFromConstants(machines);
        collectFromMethods(machines);
        List<Diagram> diagrams = new ArrayList<>();
        machines.values().forEach(machine -> diagrams.add(diagram(machine)));
        return new DiagramResult(diagrams, warnings);
    }

    // ---- what is a state machine

    private Map<String, Machine> machines() {
        Map<String, Machine> machines = new LinkedHashMap<>();
        for (TypeInfo type : scope.types()) {
            java.util.Optional<AnnotationInfo> annotation = type.annotation(Marks.STATE_MACHINE);
            if (annotation.isEmpty() || !shown(type)) {
                continue;
            }
            if (!type.isEnum()) {
                warnings.add(type.displayName() + " is marked with @StateMachine, but it is not an enum: the constants of an enum are the states.");
                continue;
            }
            Machine machine = new Machine(type, annotation.get());
            for (FieldInfo field : type.fields()) {
                if (field.isEnumConstant() && !Marks.of(field.annotations(), Kind.STATES).ignored()) {
                    machine.states.add(field.name());
                }
            }
            if (!machine.initial.isBlank() && !machine.states.contains(machine.initial)) {
                warnings.add("The initial state " + machine.initial + " of " + type.displayName() + " is no constant of the enum.");
            }
            for (String terminal : machine.terminal) {
                if (!machine.states.contains(terminal)) {
                    warnings.add("The terminal state " + terminal + " of " + type.displayName() + " is no constant of the enum.");
                }
            }
            machines.put(type.name(), machine);
        }
        return machines;
    }

    private boolean shown(TypeInfo type) {
        return scope.inScope(type, Kind.STATES)
                && (scope.mark(type, Kind.STATES).included() || DiagramScope.passesTypePatterns(type, settings.includeTypes(), settings.excludeTypes()));
    }

    // ---- transitions

    /** A transition on a constant starts in that constant. */
    private void collectFromConstants(Map<String, Machine> machines) {
        for (Machine machine : machines.values()) {
            for (FieldInfo field : machine.type.fields()) {
                if (!field.isEnumConstant() || !machine.states.contains(field.name())) {
                    continue;
                }
                for (AnnotationInfo annotation : field.annotations()) {
                    if (annotation.type().equals(Marks.TRANSITION)) {
                        add(machine, field.name(), annotation, machine.type.displayName() + "." + field.name());
                    }
                }
            }
        }
    }

    /** A transition on a method names the states it starts in, and the enum unless the method is in it. */
    private void collectFromMethods(Map<String, Machine> machines) {
        for (TypeInfo type : scope.types()) {
            for (MethodInfo method : type.methods()) {
                for (AnnotationInfo annotation : method.annotations()) {
                    if (!annotation.type().equals(Marks.TRANSITION) || Marks.of(method.annotations(), Kind.STATES).ignored()) {
                        continue;
                    }
                    String where = type.displayName() + "#" + method.name();
                    String target = annotation.classValue("machine");
                    if (target == null || target.equals("java.lang.Void")) {
                        target = type.name();
                    }
                    Machine machine = machines.get(target);
                    if (machine == null) {
                        if (scope.type(target).filter(candidate -> candidate.annotation(Marks.STATE_MACHINE).isPresent()).isEmpty()) {
                            warnings.add("@Transition on " + where + " needs machine = <an enum marked with @StateMachine>, but "
                                    + (target.equals(type.name()) ? "the class it is in is none" : TypeRef.displayName(target) + " is none") + ".");
                        }
                        continue;
                    }
                    List<String> from = annotation.strings("from");
                    if (from.isEmpty()) {
                        warnings.add("@Transition on " + where + " needs from = the states it can be called in.");
                        continue;
                    }
                    for (String state : from) {
                        add(machine, state, annotation, where);
                    }
                }
            }
        }
    }

    private void add(Machine machine, String from, AnnotationInfo annotation, String declaredAt) {
        String to = annotation.string("to", "");
        String event = annotation.string("on", "");
        if (!machine.states.contains(to)) {
            warnings.add("@Transition at " + declaredAt + " leads to " + to + ", which is no state of " + machine.type.displayName() + ".");
            return;
        }
        if (from.equals("*")) {
            for (String state : machine.states) {
                if (!state.equals(to) && !machine.terminal.contains(state)) {
                    machine.transitions.add(new Transition(state, to, event, declaredAt));
                }
            }
            return;
        }
        if (!machine.states.contains(from)) {
            warnings.add("@Transition at " + declaredAt + " starts in " + from + ", which is no state of " + machine.type.displayName() + ".");
            return;
        }
        machine.transitions.add(new Transition(from, to, event, declaredAt));
    }

    // ---- the diagram

    private Diagram diagram(Machine machine) {
        StringBuilder source = new StringBuilder("stateDiagram-v2\n");
        if (settings.direction() == Direction.LEFT_RIGHT) {
            source.append("  direction LR\n");
        }
        Set<String> declared = new HashSet<>();
        for (String state : machine.states) {
            String id = Mermaid.id(state);
            declared.add(id);
            if (!id.equals(state)) {
                source.append("  state \"").append(Mermaid.label(state)).append("\" as ").append(id).append('\n');
            }
        }
        if (!machine.initial.isBlank() && machine.states.contains(machine.initial)) {
            source.append("  [*] --> ").append(Mermaid.id(machine.initial)).append('\n');
        }
        // States with no transition are still part of the diagram.
        Set<String> mentioned = new HashSet<>();
        machine.transitions.forEach(transition -> {
            mentioned.add(transition.from());
            mentioned.add(transition.to());
        });
        machine.states.stream().filter(state -> !mentioned.contains(state) && !state.equals(machine.initial) && !machine.terminal.contains(state))
                .forEach(state -> source.append("  ").append(Mermaid.id(state)).append('\n'));
        // The same change can be declared twice, on the constant and on the method that makes it: it is drawn once.
        Set<String> drawn = new HashSet<>();
        for (Transition transition : machine.transitions) {
            String arrow = "  " + Mermaid.id(transition.from()) + " --> " + Mermaid.id(transition.to())
                    + (settings.events() && !transition.event().isBlank() ? " : " + Mermaid.member(transition.event()) : "");
            if (drawn.add(arrow)) {
                source.append(arrow).append('\n');
            }
        }
        for (String state : machine.states) {
            if (machine.terminal.contains(state)) {
                source.append("  ").append(Mermaid.id(state)).append(" --> [*]\n");
            }
        }

        List<String> items = new ArrayList<>();
        machine.states.forEach(state -> items.add("State `" + state + "`" + (state.equals(machine.initial) ? " (start)" : "") + (machine.terminal.contains(state) ? " (end)" : "")));
        machine.transitions.forEach(transition -> items.add("`" + transition.from() + "` → `" + transition.to() + "`"
                + (transition.event().isBlank() ? "" : " on " + transition.event()) + ", declared at `" + transition.declaredAt() + "`"));
        return new Diagram(machine.name, machine.description, source.toString().stripTrailing(), items, notes(machine));
    }

    private static List<String> notes(Machine machine) {
        List<String> notes = new ArrayList<>();
        if (machine.transitions.isEmpty()) {
            notes.add("No @Transition was found for this state machine, so the diagram only shows its states.");
            return notes;
        }
        Set<String> entered = new HashSet<>();
        Set<String> left = new HashSet<>();
        machine.transitions.forEach(transition -> {
            left.add(transition.from());
            entered.add(transition.to());
        });
        for (String state : machine.states) {
            if (!entered.contains(state) && !state.equals(machine.initial) && !machine.initial.isBlank()) {
                notes.add("`" + state + "` is never entered: no transition leads to it, and it is not the initial state.");
            }
            if (!left.contains(state) && !machine.terminal.contains(state)) {
                notes.add("`" + state + "` is never left, and it is not marked as terminal.");
            }
        }
        return notes;
    }
}
