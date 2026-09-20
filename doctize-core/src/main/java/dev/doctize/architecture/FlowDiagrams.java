package dev.doctize.architecture;

import dev.doctize.architecture.Marks.Mark;
import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.architecture.TypeInfo.Call;
import dev.doctize.architecture.TypeInfo.MethodInfo;
import dev.doctize.config.ArchitectureSettings.Direction;
import dev.doctize.config.DiagramSettings.FlowDiagram;
import dev.doctize.config.DiagramSettings.Kind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Makes flow charts: from a method that starts a flow, the methods it calls, and the ones they call, in the order the calls are in
 * the code, down to the configured depth.
 *
 * <p>What a class file has is calls, not decisions. A chart therefore shows what a method <em>can</em> call, in the order the code
 * says so, and not under which condition. A call of an interface method continues in the classes that implement it, if asked for.
 */
final class FlowDiagrams {

    private record MethodRef(TypeInfo type, MethodInfo method) {

        String id() {
            return type.name() + "#" + method.key();
        }
    }

    private record Edge(String from, String to, int order, boolean implementation) {}

    /** A flow while it is being followed. */
    private static final class Flow {
        final Map<String, MethodRef> nodes = new LinkedHashMap<>();
        final Map<String, Integer> depths = new HashMap<>();
        final Set<String> cut = new LinkedHashSet<>();
        final List<Edge> edges = new ArrayList<>();
        boolean truncated;
    }

    private final DiagramScope scope;
    private final FlowDiagram settings;
    private final List<String> warnings = new ArrayList<>();

    FlowDiagrams(DiagramScope scope, FlowDiagram settings) {
        this.scope = scope;
        this.settings = settings;
    }

    DiagramResult generate() {
        List<Diagram> diagrams = new ArrayList<>();
        for (Map.Entry<MethodRef, Optional<AnnotationInfo>> entry : entryPoints().entrySet()) {
            diagrams.add(diagram(entry.getKey(), entry.getValue()));
        }
        return new DiagramResult(diagrams, warnings);
    }

    // ---- where a flow starts

    private Map<MethodRef, Optional<AnnotationInfo>> entryPoints() {
        Map<MethodRef, Optional<AnnotationInfo>> entries = new LinkedHashMap<>();
        for (TypeInfo type : scope.types()) {
            if (!scope.inScope(type, Kind.FLOWS)) {
                continue;
            }
            for (MethodInfo method : type.methods()) {
                Optional<AnnotationInfo> flow = method.annotations().stream().filter(annotation -> annotation.type().equals(Marks.FLOW)).findFirst();
                if (flow.isPresent() && !Marks.of(method.annotations(), Kind.FLOWS).ignored()) {
                    entries.put(new MethodRef(type, method), flow);
                }
            }
        }
        for (String pattern : settings.entryPoints()) {
            int hash = pattern.lastIndexOf('#');
            String typePattern = pattern.substring(0, hash);
            String methodPattern = pattern.substring(hash + 1);
            int matches = 0;
            for (TypeInfo type : scope.types()) {
                if (!scope.inScope(type, Kind.FLOWS) || !PackagePattern.matches(typePattern, DiagramScope.patternName(type))) {
                    continue;
                }
                for (MethodInfo method : type.methods()) {
                    if (!method.isSynthetic() && !method.isStaticInitializer() && !method.isConstructor() && !method.isAbstract()
                            && !Marks.of(method.annotations(), Kind.FLOWS).ignored() && PackagePattern.matches(methodPattern, method.name())) {
                        entries.putIfAbsent(new MethodRef(type, method), Optional.empty());
                        matches++;
                    }
                }
            }
            if (matches == 0) {
                warnings.add("The entry point " + pattern + " of flows.entryPoints matches no method.");
            }
        }
        return entries;
    }

    // ---- following the calls

    private Diagram diagram(MethodRef entry, Optional<AnnotationInfo> annotation) {
        Flow flow = follow(entry);
        String defaultName = entry.type().displayName() + "#" + entry.method().name();
        String name = annotation.map(a -> a.string("name", "")).filter(text -> !text.isBlank()).orElse(defaultName);
        String description = annotation.map(a -> a.string("description", "")).orElse("");

        Map<String, String> ids = new LinkedHashMap<>();
        flow.nodes.keySet().forEach(key -> ids.put(key, "n" + ids.size()));
        Map<String, String> labels = labels(flow);

        StringBuilder source = new StringBuilder("flowchart ").append(settings.direction() == Direction.LEFT_RIGHT ? "LR" : "TD").append('\n');
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, MethodRef> node : flow.nodes.entrySet()) {
            MethodRef ref = node.getValue();
            String label = Mermaid.label(labels.get(node.getKey()) + (flow.cut.contains(node.getKey()) ? " …" : ""));
            boolean isEntry = node.getKey().equals(entry.id());
            boolean abstractMethod = ref.method().isAbstract();
            String open = isEntry ? "([\"" : abstractMethod ? "{{\"" : "[\"";
            String close = isEntry ? "\"])" : abstractMethod ? "\"}}" : "\"]";
            String style = isEntry ? ":::entry" : abstractMethod ? ":::abstract" : flow.cut.contains(node.getKey()) ? ":::cut" : "";
            source.append("  ").append(ids.get(node.getKey())).append(open).append(label).append(close).append(style).append('\n');
            items.add("`" + ref.type().displayName() + "#" + ref.method().name() + "`" + (isEntry ? " (start)" : ""));
        }
        for (Edge edge : flow.edges) {
            String arrow = edge.implementation() ? "-.->|implemented by|" : settings.numbered() ? "-->|" + edge.order() + "|" : "-->";
            source.append("  ").append(ids.get(edge.from())).append(' ').append(arrow).append(' ').append(ids.get(edge.to())).append('\n');
        }
        source.append("  classDef entry fill:#4f46e5,stroke:#4f46e5,color:#ffffff\n")
                .append("  classDef abstract stroke-dasharray:5 3\n")
                .append("  classDef cut stroke-dasharray:2 3");

        List<String> notes = new ArrayList<>();
        if (flow.truncated) {
            notes.add("The flow has more than " + settings.maxNodes() + " methods, so it is cut off. Raise flows.maxNodes, lower flows.depth, or leave types out with excludeTypes.");
        }
        if (!flow.cut.isEmpty()) {
            notes.add("Calls deeper than " + settings.depth() + " levels are not followed; " + flow.cut.size() + " method" + (flow.cut.size() == 1 ? "" : "s")
                    + " marked with … call further methods. Raise flows.depth to see them.");
        }
        if (flow.nodes.size() == 1) {
            notes.add("The method calls nothing that is part of the analyzed code.");
        }
        return new Diagram(name, description, source.toString(), items, notes);
    }

    private Flow follow(MethodRef entry) {
        Flow flow = new Flow();
        flow.nodes.put(entry.id(), entry);
        flow.depths.put(entry.id(), 0);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entry.id());
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            MethodRef current = flow.nodes.get(currentId);
            int depth = flow.depths.get(currentId);
            List<MethodRef> callees = calleesOf(current);
            if (depth >= settings.depth()) {
                if (!callees.isEmpty()) {
                    flow.cut.add(currentId);
                }
                continue;
            }
            int order = 0;
            Set<String> drawn = new HashSet<>();
            for (MethodRef callee : callees) {
                if (!drawn.add(callee.id())) {
                    continue;
                }
                if (!flow.nodes.containsKey(callee.id())) {
                    if (flow.nodes.size() >= settings.maxNodes()) {
                        flow.truncated = true;
                        continue;
                    }
                    flow.nodes.put(callee.id(), callee);
                    flow.depths.put(callee.id(), depth + 1);
                    queue.add(callee.id());
                }
                boolean implementation = current.method().isAbstract();
                flow.edges.add(new Edge(currentId, callee.id(), implementation ? 0 : ++order, implementation));
            }
        }
        return flow;
    }

    /** The methods that a method leads to, in the order of the calls; for an abstract method, the methods that implement it. */
    private List<MethodRef> calleesOf(MethodRef method) {
        List<MethodRef> callees = new ArrayList<>();
        if (method.method().isAbstract()) {
            if (settings.implementations()) {
                for (TypeInfo sub : scope.subtypes(method.type())) {
                    findIn(sub, method.method().key()).map(found -> realMethod(sub, found)).filter(found -> !found.isAbstract()).ifPresent(found -> {
                        MethodRef ref = new MethodRef(sub, found);
                        if (followable(ref)) {
                            callees.add(ref);
                        }
                    });
                }
            }
            return callees;
        }
        for (Call call : effectiveCalls(method.type(), method.method(), new HashSet<>())) {
            resolve(call).stream().filter(this::followable).forEach(callees::add);
        }
        return callees;
    }

    /**
     * A class that implements a generic interface, {@code Store<Order>}, gets a bridge method from the compiler with the erased
     * signature, {@code put(Object)}, which only calls the method the author wrote, {@code put(Order)}. That is the one to follow.
     */
    private MethodInfo realMethod(TypeInfo type, MethodInfo method) {
        if ((method.flags() & TypeInfo.VOLATILE_OR_BRIDGE) == 0) {
            return method;
        }
        return method.calls().stream()
                .filter(call -> call.owner().equals(type.name()) && call.name().equals(method.name()))
                .findFirst()
                .flatMap(call -> findIn(type, call.key()))
                .orElse(method);
    }

    /** The calls of a method with the body of every lambda in it counted as part of it. */
    private List<Call> effectiveCalls(TypeInfo type, MethodInfo method, Set<String> seen) {
        List<Call> calls = new ArrayList<>();
        for (Call call : method.calls()) {
            if (call.owner().equals(type.name()) && call.name().startsWith("lambda$")) {
                Optional<MethodInfo> body = findIn(type, call.key());
                if (body.isPresent() && seen.add(call.key())) {
                    calls.addAll(effectiveCalls(type, body.get(), seen));
                }
            } else {
                calls.add(call);
            }
        }
        return calls;
    }

    private static Optional<MethodInfo> findIn(TypeInfo type, String key) {
        return type.methods().stream().filter(method -> method.key().equals(key)).findFirst();
    }

    /** The method a call means: the one declared in the class it names, or in a super type of it. */
    private List<MethodRef> resolve(Call call) {
        Optional<TypeInfo> owner = scope.type(call.owner());
        if (owner.isEmpty()) {
            return List.of();
        }
        return declaration(owner.get(), call.key(), new HashSet<>()).map(List::of).orElse(List.of());
    }

    private Optional<MethodRef> declaration(TypeInfo type, String key, Set<String> seen) {
        if (!seen.add(type.name())) {
            return Optional.empty();
        }
        Optional<MethodInfo> own = findIn(type, key);
        if (own.isPresent()) {
            return Optional.of(new MethodRef(type, own.get()));
        }
        for (TypeInfo supertype : scope.supertypes(type)) {
            Optional<MethodRef> inherited = declaration(supertype, key, seen);
            if (inherited.isPresent()) {
                return inherited;
            }
        }
        return Optional.empty();
    }

    private boolean followable(MethodRef ref) {
        MethodInfo method = ref.method();
        TypeInfo type = ref.type();
        if (method.isStaticInitializer() || method.isSynthetic()) {
            return false;
        }
        Mark mark = Marks.of(method.annotations(), Kind.FLOWS);
        if (mark.ignored() || !scope.inScope(type, Kind.FLOWS)) {
            return false;
        }
        if (!scope.mark(type, Kind.FLOWS).included() && !DiagramScope.passesTypePatterns(type, settings.includeTypes(), settings.excludeTypes())) {
            return false;
        }
        if (method.isConstructor()) {
            return settings.constructors() || mark.included();
        }
        return mark.included() || settings.accessors() || !ClassDiagrams.isAccessor(type, method);
    }

    // ---- what the boxes say

    /** {@code Order.pay()}; with the parameter types where two methods of a class in the chart have the same name. */
    private static Map<String, String> labels(Flow flow) {
        Map<String, Integer> uses = new HashMap<>();
        flow.nodes.values().forEach(ref -> uses.merge(ref.type().name() + "#" + ref.method().name(), 1, Integer::sum));
        Map<String, String> labels = new LinkedHashMap<>();
        flow.nodes.forEach((key, ref) -> {
            String type = TypeRef.displayName(ref.type().name());
            String name = ref.method().isConstructor() ? "new" : ref.method().name();
            boolean overloaded = uses.get(ref.type().name() + "#" + ref.method().name()) > 1;
            String parameters = overloaded
                    ? String.join(", ", ref.method().signatureOf().parameters().stream().map(parameter -> parameter.display().replace('~', '<')).toList())
                    : "";
            labels.put(key, type + "." + name + "(" + parameters + ")");
        });
        return labels;
    }
}
