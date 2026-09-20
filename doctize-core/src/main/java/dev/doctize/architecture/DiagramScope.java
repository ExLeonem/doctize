package dev.doctize.architecture;

import dev.doctize.architecture.Marks.Mark;
import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.DiagramSettings.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The code that the diagrams are made from: all types of the analyzed classes, and the rules of the configuration and of the
 * annotations that say which of them a diagram may show.
 *
 * <p>Whether a type is shown is decided in this order, and the first rule that applies wins: the mark on the type, the mark on the
 * type it is nested in, the mark on its package, and last the configuration: {@code rootPackage}, {@code include} and
 * {@code exclude}. A mark on a type therefore beats a mark on its package, and both beat the configuration; a type that is
 * ignored stays ignored whatever else says.
 */
final class DiagramScope {

    private final ArchitectureSettings settings;
    private final Map<String, TypeInfo> byName = new HashMap<>();
    private final Map<String, List<AnnotationInfo>> packageAnnotations;
    private final List<TypeInfo> types;

    DiagramScope(ArchitectureSettings settings, ClassScanner.Scan scan) {
        this.settings = settings;
        this.packageAnnotations = scan.packageAnnotations();
        this.types = scan.types().stream()
                .filter(type -> !type.anonymous() && (type.flags() & TypeInfo.SYNTHETIC) == 0)
                .sorted(java.util.Comparator.comparing(TypeInfo::name))
                .toList();
        types.forEach(type -> byName.put(type.name(), type));
    }

    /** Every type that a person wrote: no anonymous, local or compiler-made classes. */
    List<TypeInfo> types() {
        return types;
    }

    Optional<TypeInfo> type(String binaryName) {
        return Optional.ofNullable(byName.get(binaryName));
    }

    ArchitectureSettings settings() {
        return settings;
    }

    Mark mark(TypeInfo type, Kind kind) {
        return Marks.of(type.annotations(), kind);
    }

    Mark packageMark(String packageName, Kind kind) {
        return Marks.of(packageAnnotations.getOrDefault(packageName, List.of()), kind);
    }

    /**
     * What the marks say about a type, in the order that decides it: the mark on the type, on the types it is nested in, and on its
     * package. {@link Mark#NONE} means that the marks leave it to the configuration.
     */
    Mark effectiveMark(TypeInfo type, Kind kind) {
        Mark own = mark(type, kind);
        if (own != Mark.NONE) {
            return own;
        }
        // Everything nested in an ignored type is ignored with it.
        for (TypeInfo outer = outerOf(type); outer != null; outer = outerOf(outer)) {
            if (mark(outer, kind).ignored()) {
                return Mark.IGNORE;
            }
        }
        return packageMark(type.packageName(), kind);
    }

    /** Whether a diagram of this kind may show the type. */
    boolean inScope(TypeInfo type, Kind kind) {
        Mark mark = effectiveMark(type, kind);
        return mark != Mark.NONE ? mark.included() : packageInScope(type.packageName());
    }

    /** The configuration: the root package, and the include and exclude patterns for packages. */
    boolean packageInScope(String packageName) {
        String root = settings.rootPackage();
        if (!root.isEmpty() && !packageName.equals(root) && !packageName.startsWith(root + ".")) {
            return false;
        }
        boolean included = settings.include().isEmpty() || settings.include().stream().anyMatch(pattern -> PackagePattern.matches(pattern, packageName));
        return included && settings.exclude().stream().noneMatch(pattern -> PackagePattern.matches(pattern, packageName));
    }

    private TypeInfo outerOf(TypeInfo type) {
        return type.outerName() == null ? null : byName.get(type.outerName());
    }

    /** The name used in patterns for types: the binary name with {@code .} for nested classes, {@code com.acme.Order.Line}. */
    static String patternName(TypeInfo type) {
        return type.name().replace('$', '.');
    }

    /** Whether a type passes the include and exclude patterns of a diagram; a mark on the type is dealt with elsewhere. */
    static boolean passesTypePatterns(TypeInfo type, List<String> include, List<String> exclude) {
        String name = patternName(type);
        return (include.isEmpty() || include.stream().anyMatch(pattern -> PackagePattern.matches(pattern, name)))
                && exclude.stream().noneMatch(pattern -> PackagePattern.matches(pattern, name));
    }

    /** The super class and interfaces of a type that are types of the analyzed code. */
    List<TypeInfo> supertypes(TypeInfo type) {
        List<TypeInfo> supertypes = new ArrayList<>();
        if (type.superName() != null && byName.containsKey(type.superName())) {
            supertypes.add(byName.get(type.superName()));
        }
        type.interfaces().stream().filter(byName::containsKey).map(byName::get).forEach(supertypes::add);
        return supertypes;
    }

    /** Every type of the analyzed code that extends or implements the given one, directly or not. */
    List<TypeInfo> subtypes(TypeInfo type) {
        Set<TypeInfo> found = new LinkedHashSet<>();
        collectSubtypes(type, found);
        return List.copyOf(found);
    }

    private void collectSubtypes(TypeInfo type, Set<TypeInfo> found) {
        for (TypeInfo candidate : types) {
            if (supertypes(candidate).contains(type) && found.add(candidate)) {
                collectSubtypes(candidate, found);
            }
        }
    }
}
