package dev.doctize.architecture;

import dev.doctize.architecture.Marks.Mark;
import dev.doctize.architecture.TypeInfo.FieldInfo;
import dev.doctize.architecture.TypeInfo.MethodInfo;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.DiagramSettings.ClassDiagram;
import dev.doctize.config.DiagramSettings.Kind;
import dev.doctize.config.DiagramSettings.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Makes class diagrams: the types of a package (or component, or all of them), their members, and how they relate. One diagram
 * shows at most {@code maxClasses} types, so a large package is split into several.
 */
final class ClassDiagrams {

    private static final Set<String> MANY = Set.of(
            "java.util.List", "java.util.Set", "java.util.Collection", "java.util.Queue", "java.util.Deque", "java.util.SortedSet",
            "java.util.NavigableSet", "java.util.Map", "java.util.SortedMap", "java.util.NavigableMap", "java.util.ArrayList",
            "java.util.LinkedList", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.HashMap",
            "java.util.LinkedHashMap", "java.util.TreeMap", "java.lang.Iterable", "java.util.stream.Stream");
    private static final Set<String> IGNORED_SUPERTYPES = Set.of("java.lang.Object", "java.lang.Enum", "java.lang.Record");

    private final DiagramScope scope;
    private final ArchitectureSettings architecture;
    private final ClassDiagram settings;
    private final Map<String, TypeInfo> shownByName = new LinkedHashMap<>();

    ClassDiagrams(DiagramScope scope, ClassDiagram settings) {
        this.scope = scope;
        this.architecture = scope.settings();
        this.settings = settings;
    }

    DiagramResult generate() {
        for (TypeInfo type : scope.types()) {
            if (isShown(type)) {
                shownByName.put(type.name(), type);
            }
        }
        Map<String, List<TypeInfo>> groups = new TreeMap<>();
        for (TypeInfo type : shownByName.values()) {
            groups.computeIfAbsent(groupOf(type), key -> new ArrayList<>()).add(type);
        }
        List<Diagram> diagrams = new ArrayList<>();
        groups.forEach((group, types) -> {
            int parts = (types.size() + settings.maxClasses() - 1) / settings.maxClasses();
            for (int part = 0; part < parts; part++) {
                List<TypeInfo> chunk = types.subList(part * settings.maxClasses(), Math.min(types.size(), (part + 1) * settings.maxClasses()));
                String heading = parts == 1 ? group : group + " (part " + (part + 1) + " of " + parts + ")";
                List<String> notes = parts == 1 ? List.of() : List.of("The types of " + group + " do not fit in one diagram, so they are split. Relations to types in the other parts are drawn to boxes without members.");
                diagrams.add(diagram(heading, chunk, notes));
            }
        });
        return new DiagramResult(diagrams, List.of());
    }

    // ---- which types, and how they are grouped

    private boolean isShown(TypeInfo type) {
        Mark mark = scope.mark(type, Kind.CLASSES);
        if (!scope.inScope(type, Kind.CLASSES)) {
            return false;
        }
        if (mark.included()) {
            return true;
        }
        if (settings.onlyMarked() || type.isAnnotationType()) {
            return false;
        }
        return DiagramScope.passesTypePatterns(type, settings.includeTypes(), settings.excludeTypes());
    }

    private String groupOf(TypeInfo type) {
        return switch (settings.groupBy()) {
            case ALL -> "All types";
            case COMPONENT -> architecture.components().stream()
                    .filter(component -> component.packages().stream().anyMatch(pattern -> PackagePattern.matches(pattern, type.packageName())))
                    .map(ArchitectureSettings.Component::name)
                    .findFirst()
                    .orElse(packageLabel(type));
            case PACKAGE -> packageLabel(type);
        };
    }

    private static String packageLabel(TypeInfo type) {
        return type.packageName().isEmpty() ? "(default package)" : type.packageName();
    }

    // ---- one diagram

    private record Relation(String from, String to, String arrow, String multiplicity, String label) {}

    private Diagram diagram(String heading, List<TypeInfo> chunk, List<String> notes) {
        Set<String> inChunk = new LinkedHashSet<>();
        chunk.forEach(type -> inChunk.add(type.name()));
        List<Relation> relations = relations(chunk, inChunk);

        // Types of other diagrams that the relations lead to are drawn as boxes without members.
        Set<String> neighbours = new LinkedHashSet<>();
        relations.forEach(relation -> {
            for (String end : List.of(relation.from(), relation.to())) {
                if (!inChunk.contains(end)) {
                    neighbours.add(end);
                }
            }
        });
        Map<String, String> ids = ids(inChunk, neighbours);

        StringBuilder source = new StringBuilder("classDiagram\n");
        if (settings.direction() == ArchitectureSettings.Direction.LEFT_RIGHT) {
            source.append("  direction LR\n");
        }
        List<String> items = new ArrayList<>();
        for (TypeInfo type : chunk) {
            declare(source, type, ids.get(type.name()), true);
            items.add("`" + type.displayName() + "`" + kindOf(type));
        }
        for (String name : neighbours) {
            declare(source, shownByName.get(name), ids.get(name), false);
        }
        for (Relation relation : relations) {
            source.append("  ").append(ids.get(relation.from())).append(' ').append(relation.arrow()).append(' ')
                    .append(relation.multiplicity().isEmpty() ? "" : "\"" + relation.multiplicity() + "\" ")
                    .append(ids.get(relation.to()))
                    .append(relation.label().isEmpty() ? "" : " : " + Mermaid.member(relation.label())).append('\n');
        }
        return new Diagram(heading, "", source.toString().stripTrailing(), items, notes);
    }

    private static String kindOf(TypeInfo type) {
        if (type.isInterface()) {
            return " (interface)";
        }
        if (type.isEnum()) {
            return " (enum)";
        }
        if (type.isRecord()) {
            return " (record)";
        }
        if (type.isAbstract()) {
            return " (abstract class)";
        }
        return "";
    }

    /** A simple name where it is unambiguous, the whole name where two types of the diagram would share one. */
    private Map<String, String> ids(Set<String> inChunk, Set<String> neighbours) {
        List<String> names = new ArrayList<>(inChunk);
        names.addAll(neighbours);
        Map<String, Integer> uses = new HashMap<>();
        names.forEach(name -> uses.merge(Mermaid.id(TypeRef.displayName(name).replace('.', '_')), 1, Integer::sum));
        Map<String, String> ids = new HashMap<>();
        for (String name : names) {
            String simple = Mermaid.id(TypeRef.displayName(name).replace('.', '_'));
            ids.put(name, uses.get(simple) > 1 ? Mermaid.id(name) : simple);
        }
        return ids;
    }

    private void declare(StringBuilder source, TypeInfo type, String id, boolean withMembers) {
        String label = type.displayName();
        String head = "class " + id;
        if (!id.equals(label)) {
            head += "[\"" + Mermaid.label(label) + "\"]";
        } else if (!type.typeParameters().isEmpty()) {
            head += "~" + String.join(", ", type.typeParameters()) + "~";
        }
        List<String> lines = new ArrayList<>();
        if (settings.stereotypes()) {
            stereotype(type).ifPresent(lines::add);
        }
        if (withMembers) {
            lines.addAll(members(type));
        }
        if (lines.isEmpty()) {
            source.append("  ").append(head).append('\n');
            return;
        }
        source.append("  ").append(head).append(" {\n");
        lines.forEach(line -> source.append("    ").append(line).append('\n'));
        source.append("  }\n");
    }

    private static java.util.Optional<String> stereotype(TypeInfo type) {
        if (type.isInterface()) {
            return java.util.Optional.of("<<interface>>");
        }
        if (type.isEnum()) {
            return java.util.Optional.of("<<enumeration>>");
        }
        if (type.isRecord()) {
            return java.util.Optional.of("<<record>>");
        }
        if (type.isAnnotationType()) {
            return java.util.Optional.of("<<annotation>>");
        }
        return type.isAbstract() ? java.util.Optional.of("<<abstract>>") : java.util.Optional.empty();
    }

    // ---- members

    private List<String> members(TypeInfo type) {
        List<String> lines = new ArrayList<>();
        Set<String> componentNames = new HashSet<>();
        if (settings.fields()) {
            for (FieldInfo field : type.fields()) {
                if (field.isEnumConstant() && shownField(field, true)) {
                    lines.add(Mermaid.member(field.name()));
                }
            }
            type.recordComponents().forEach(component -> {
                componentNames.add(component.name());
                lines.add("+" + Mermaid.member(component.type().display()) + " " + component.name());
            });
            for (FieldInfo field : type.fields()) {
                if (!field.isEnumConstant() && !componentNames.contains(field.name()) && shownField(field, false)) {
                    lines.add(symbol(field.visibility()) + Mermaid.member(field.type().display()) + " " + field.name() + (field.isStatic() ? "$" : ""));
                }
            }
        }
        for (MethodInfo method : type.methods()) {
            if (shownMethod(type, method)) {
                lines.add(methodLine(type, method));
            }
        }
        return lines;
    }

    private boolean shownField(FieldInfo field, boolean enumConstant) {
        if (field.isSynthetic() || field.name().startsWith("$")) {
            return false;
        }
        Mark mark = Marks.of(field.annotations(), Kind.CLASSES);
        if (mark.ignored()) {
            return false;
        }
        return enumConstant || mark.included() || atLeast(field.visibility(), settings.fieldVisibility());
    }

    private boolean shownMethod(TypeInfo type, MethodInfo method) {
        if (method.isSynthetic() || method.isStaticInitializer() || method.name().startsWith("$")) {
            return false;
        }
        Mark mark = Marks.of(method.annotations(), Kind.CLASSES);
        if (mark.ignored()) {
            return false;
        }
        if (type.isEnum() && (method.isConstructor() || isEnumBuiltIn(method))) {
            return false;
        }
        if (method.isConstructor()) {
            return mark.included() || settings.constructors() && atLeast(method.visibility(), settings.methodVisibility());
        }
        if (mark.included()) {
            return true;
        }
        return settings.methods() && atLeast(method.visibility(), settings.methodVisibility()) && (settings.accessors() || !isAccessor(type, method));
    }

    private static boolean isEnumBuiltIn(MethodInfo method) {
        return method.name().equals("values") && method.descriptor().startsWith("()")
                || method.name().equals("valueOf") && method.descriptor().startsWith("(Ljava/lang/String;)");
    }

    private static boolean atLeast(String visibility, Visibility minimum) {
        return Visibility.valueOf(visibility.toUpperCase(java.util.Locale.ROOT)).compareTo(minimum) >= 0;
    }

    /** Getters, setters, record accessors, {@code equals}, {@code hashCode} and {@code toString}: what every class has. */
    static boolean isAccessor(TypeInfo type, MethodInfo method) {
        String name = method.name();
        String descriptor = method.descriptor();
        if (name.equals("equals") && descriptor.equals("(Ljava/lang/Object;)Z")
                || name.equals("hashCode") && descriptor.equals("()I")
                || name.equals("toString") && descriptor.equals("()Ljava/lang/String;")) {
            return true;
        }
        if (type.isRecord() && descriptor.startsWith("()") && type.recordComponents().stream().anyMatch(component -> component.name().equals(name))) {
            return true;
        }
        boolean noArguments = descriptor.startsWith("()") && !descriptor.endsWith(")V");
        boolean oneArgument = !descriptor.startsWith("()") && descriptor.indexOf(')') > 1 && Signatures.method(descriptor).parameters().size() == 1;
        return (name.startsWith("get") && noArguments && hasField(type, name.substring(3)))
                || (name.startsWith("is") && noArguments && hasField(type, name.substring(2)))
                || (name.startsWith("set") && oneArgument && hasField(type, name.substring(3)));
    }

    private static boolean hasField(TypeInfo type, String capitalised) {
        return !capitalised.isEmpty() && Character.isUpperCase(capitalised.charAt(0))
                && type.fields().stream().anyMatch(field -> field.name().equalsIgnoreCase(capitalised));
    }

    private String methodLine(TypeInfo type, MethodInfo method) {
        Signatures.MethodSignature signature = method.signatureOf();
        List<TypeRef> parameters = signature.parameters();
        // A member class that is not static, or a constructor of an enum, has parameters that only the compiler adds.
        if (method.isConstructor() && type.outerName() != null && (type.nestedFlags() & TypeInfo.STATIC) == 0 && !parameters.isEmpty()
                && method.signature() == null) {
            parameters = parameters.subList(1, parameters.size());
        }
        List<String> parameterTexts = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            String name = i < method.parameterNames().size() && !method.parameterNames().get(i).isBlank() ? " " + method.parameterNames().get(i) : "";
            parameterTexts.add(Mermaid.member(parameters.get(i).display()) + name);
        }
        String name = method.isConstructor() ? type.displayName().substring(type.displayName().lastIndexOf('.') + 1) : method.name();
        StringBuilder line = new StringBuilder(symbol(method.visibility())).append(name).append('(').append(String.join(", ", parameterTexts)).append(')');
        if (method.isAbstract()) {
            line.append('*');
        } else if (method.isStatic()) {
            line.append('$');
        }
        if (!method.isConstructor() && !signature.returnType().equals(TypeRef.VOID)) {
            line.append(' ').append(Mermaid.member(signature.returnType().display()));
        }
        return line.toString();
    }

    private static String symbol(String visibility) {
        return switch (visibility) {
            case "public" -> "+";
            case "protected" -> "#";
            case "private" -> "-";
            default -> "~";
        };
    }

    // ---- relations

    private List<Relation> relations(List<TypeInfo> chunk, Set<String> inChunk) {
        Set<String> all = shownByName.keySet();
        Set<Relation> relations = new LinkedHashSet<>();
        Set<String> related = new HashSet<>();
        for (TypeInfo type : chunk) {
            if (settings.inheritance()) {
                if (type.superName() != null && !IGNORED_SUPERTYPES.contains(type.superName()) && all.contains(type.superName())) {
                    relations.add(new Relation(type.superName(), type.name(), "<|--", "", ""));
                    related.add(type.name() + ">" + type.superName());
                }
                for (String implemented : type.interfaces()) {
                    if (all.contains(implemented)) {
                        relations.add(new Relation(implemented, type.name(), type.isInterface() ? "<|--" : "<|..", "", ""));
                        related.add(type.name() + ">" + implemented);
                    }
                }
            }
            if (settings.associations()) {
                associations(type, all).forEach(relation -> {
                    relations.add(relation);
                    related.add(relation.from() + ">" + relation.to());
                });
            }
        }
        if (settings.dependencies()) {
            for (TypeInfo type : chunk) {
                dependencies(type, all).forEach(target -> {
                    if (!related.contains(type.name() + ">" + target) && !related.contains(target + ">" + type.name())) {
                        relations.add(new Relation(type.name(), target, "..>", "", ""));
                    }
                });
            }
        }
        boolean withNeighbours = settings.neighbours();
        return relations.stream()
                .filter(relation -> withNeighbours ? inChunk.contains(relation.from()) || inChunk.contains(relation.to()) : inChunk.contains(relation.from()) && inChunk.contains(relation.to()))
                .sorted(Comparator.comparing(Relation::from).thenComparing(Relation::to).thenComparing(Relation::arrow))
                .toList();
    }

    private List<Relation> associations(TypeInfo type, Set<String> all) {
        List<Relation> relations = new ArrayList<>();
        List<TypeRef> types = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (FieldInfo field : type.fields()) {
            if (field.isStatic() || field.isSynthetic() || field.isEnumConstant() || Marks.of(field.annotations(), Kind.CLASSES).ignored()) {
                continue;
            }
            types.add(field.type());
            names.add(field.name());
        }
        for (TypeInfo.RecordComponentInfo component : type.recordComponents()) {
            types.add(component.type());
            names.add(component.name());
        }
        for (int i = 0; i < types.size(); i++) {
            TypeRef fieldType = types.get(i);
            for (String target : targets(fieldType)) {
                if (all.contains(target)) {
                    relations.add(new Relation(type.name(), target, "-->", multiplicity(fieldType, target), names.get(i)));
                }
            }
        }
        return relations;
    }

    /** The classes a field type leads to: itself, and what it holds. */
    private static List<String> targets(TypeRef type) {
        return type.classes().stream().distinct().toList();
    }

    private static String multiplicity(TypeRef type, String target) {
        if (type.isArray() && type.name().equals(target)) {
            return "*";
        }
        if (MANY.contains(type.name()) && !type.name().equals(target)) {
            return "*";
        }
        if (type.name().equals("java.util.Optional") && !type.name().equals(target)) {
            return "0..1";
        }
        return "";
    }

    private List<String> dependencies(TypeInfo type, Set<String> all) {
        Set<String> targets = new LinkedHashSet<>();
        for (MethodInfo method : type.methods()) {
            if (method.isSynthetic() || method.isStaticInitializer() || Marks.of(method.annotations(), Kind.CLASSES).ignored()) {
                continue;
            }
            Signatures.MethodSignature signature = method.signatureOf();
            signature.parameters().forEach(parameter -> targets.addAll(parameter.classes()));
            targets.addAll(signature.returnType().classes());
            targets.addAll(method.exceptions());
        }
        targets.remove(type.name());
        return targets.stream().filter(all::contains).toList();
    }
}
