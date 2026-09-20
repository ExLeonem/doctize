package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.architecture.TypeInfo.ClassValue;
import dev.doctize.architecture.TypeInfo.MethodInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The class file reader: what it finds in real compiled code. */
class ClassScannerTest {

    static ClassScanner.Scan scan;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        Path classes = CompiledSources.compile(work, Map.of(
                "p.model.Order", """
                        package p.model;
                        import dev.doctize.annotations.*;
                        import java.util.*;
                        public class Order<T extends Comparable<T>> extends Base implements Comparable<Order<T>>, java.io.Serializable {
                            @DiagramInclude private final List<Line> lines = new ArrayList<>();
                            protected Map<String, List<Line>> byName;
                            public static final int MAX = 3;
                            private Optional<Customer> customer;
                            private Line[] array;
                            @Flow(name = "Pay", description = "Takes the money") public Money pay(Money amount, Customer by) throws java.io.IOException {
                                helper();
                                return amount;
                            }
                            private void helper() {
                                Runnable r = () -> lines.size();
                                new Thread(this::helper).start();
                            }
                            @DiagramIgnore(types = {DiagramTypes.CLASSES, "flows"}) void hidden() {}
                            public int compareTo(Order<T> other) { return 0; }
                            public static class Line { public int qty; }
                            public enum Status {
                                @Transition(to = "PAID", on = "pay") @Transition(to = "CANCELLED", on = "cancel") NEW,
                                PAID, CANCELLED;
                            }
                            public interface Listener { void on(Order<?> order); }
                            public record Money(long cents, String currency) {}
                            Object anonymous() { return new Object() { public String toString() { return "x"; } }; }
                        }
                        """,
                "p.model.Base", "package p.model; class Base { }",
                "p.model.Customer", "package p.model; class Customer { }",
                "p.hidden.Secret", "package p.hidden; public class Secret { }",
                "p.hidden.package-info", "@dev.doctize.annotations.DiagramIgnore package p.hidden;"));
        scan = ClassScanner.scanTypes(List.of(classes));
    }

    private static TypeInfo type(String name) {
        return scan.types().stream().filter(type -> type.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no type " + name));
    }

    private static MethodInfo method(String type, String name) {
        return type(type).methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void everyTypeIsFoundNestedOnesToo() {
        assertThat(scan.types()).extracting(TypeInfo::name).contains("p.model.Order", "p.model.Order$Line", "p.model.Order$Status",
                "p.model.Order$Listener", "p.model.Order$Money", "p.model.Base", "p.model.Customer", "p.hidden.Secret");
    }

    @Test
    void anAnonymousClassIsRecognisedAsSuch() {
        TypeInfo anonymous = scan.types().stream().filter(type -> type.name().matches("p\\.model\\.Order\\$\\d+")).findFirst().orElseThrow();

        assertThat(anonymous.anonymous()).isTrue();
        assertThat(type("p.model.Order$Line").anonymous()).isFalse();
    }

    @Test
    void theShapeOfAClassIsRead() {
        TypeInfo order = type("p.model.Order");

        assertThat(order.typeParameters()).containsExactly("T");
        assertThat(order.superName()).isEqualTo("p.model.Base");
        assertThat(order.interfaces()).containsExactlyInAnyOrder("java.lang.Comparable", "java.io.Serializable");
        assertThat(order.visibility()).isEqualTo("public");
        assertThat(order.packageName()).isEqualTo("p.model");
        assertThat(type("p.model.Base").visibility()).isEqualTo("package");
    }

    @Test
    void nestedTypesKnowTheirOuterTypeAndTheirDeclaredVisibility() {
        TypeInfo line = type("p.model.Order$Line");

        assertThat(line.outerName()).isEqualTo("p.model.Order");
        assertThat(line.visibility()).isEqualTo("public");
        assertThat(line.displayName()).isEqualTo("Order.Line");
        assertThat(line.nestedFlags() & TypeInfo.STATIC).isNotZero();
    }

    @Test
    void kindsOfTypesAreTold() {
        assertThat(type("p.model.Order$Status").isEnum()).isTrue();
        assertThat(type("p.model.Order$Listener").isInterface()).isTrue();
        assertThat(type("p.model.Order$Money").isRecord()).isTrue();
        assertThat(type("p.model.Order").isEnum()).isFalse();
        assertThat(type("p.model.Order").isRecord()).isFalse();
    }

    @Test
    void fieldsHaveGenericTypesThatAreReadableForADiagram() {
        TypeInfo order = type("p.model.Order");
        Map<String, TypeRef> types = new java.util.HashMap<>();
        order.fields().forEach(field -> types.put(field.name(), field.type()));

        assertThat(types.get("lines").display()).isEqualTo("List~Order.Line~");
        assertThat(types.get("byName").display()).isEqualTo("Map~String, List~Order.Line~~");
        assertThat(types.get("customer").display()).isEqualTo("Optional~Customer~");
        assertThat(types.get("array").display()).isEqualTo("Order.Line[]");
        assertThat(types.get("MAX").display()).isEqualTo("int");
        assertThat(types.get("lines").classes()).containsExactly("java.util.List", "p.model.Order$Line");
    }

    @Test
    void methodsHaveParametersReturnTypeAndExceptions() {
        MethodInfo pay = method("p.model.Order", "pay");
        Signatures.MethodSignature signature = pay.signatureOf();

        assertThat(signature.parameters()).extracting(TypeRef::display).containsExactly("Order.Money", "Customer");
        assertThat(signature.returnType().display()).isEqualTo("Order.Money");
        assertThat(pay.exceptions()).as("from the Exceptions attribute, which a method without generics has no signature for")
                .containsExactly("java.io.IOException");
        assertThat(pay.parameterNames()).as("compiled with -parameters").containsExactly("amount", "by");
        assertThat(pay.visibility()).isEqualTo("public");
    }

    @Test
    void enumConstantsAreFieldsThatKnowTheyAre() {
        assertThat(type("p.model.Order$Status").fields()).filteredOn(TypeInfo.FieldInfo::isEnumConstant).extracting(TypeInfo.FieldInfo::name)
                .containsExactly("NEW", "PAID", "CANCELLED");
    }

    @Test
    void recordComponentsAreRead() {
        assertThat(type("p.model.Order$Money").recordComponents()).extracting(TypeInfo.RecordComponentInfo::name).containsExactly("cents", "currency");
        assertThat(type("p.model.Order$Money").recordComponents().get(0).type().display()).isEqualTo("long");
    }

    @Test
    void callsAreListedInTheOrderTheyAreMade() {
        assertThat(method("p.model.Order", "pay").calls()).contains(new TypeInfo.Call("p.model.Order", "helper", "()V"));
    }

    @Test
    void lambdasAndMethodReferencesCountAsCallsToWhatTheyRun() {
        List<TypeInfo.Call> calls = method("p.model.Order", "helper").calls();

        assertThat(calls).as("the body of the lambda").anyMatch(call -> call.name().startsWith("lambda$helper$"));
        assertThat(calls).as("this::helper").contains(new TypeInfo.Call("p.model.Order", "helper", "()V"));
        assertThat(method("p.model.Order", "lambda$helper$0").isLambdaBody()).isTrue();
        assertThat(method("p.model.Order", "lambda$helper$0").isSynthetic()).isTrue();
    }

    @Test
    void annotationsWithTheirValuesAreRead() {
        AnnotationInfo flow = method("p.model.Order", "pay").annotations().get(0);
        AnnotationInfo ignore = method("p.model.Order", "hidden").annotations().get(0);

        assertThat(flow.type()).isEqualTo(Marks.FLOW);
        assertThat(flow.string("name", "")).isEqualTo("Pay");
        assertThat(flow.string("description", "")).isEqualTo("Takes the money");
        assertThat(ignore.type()).isEqualTo(Marks.IGNORE);
        assertThat(ignore.strings("types")).containsExactly("classes", "flows");
        assertThat(type("p.model.Order").fields().stream().filter(f -> f.name().equals("lines")).findFirst().orElseThrow().annotations())
                .extracting(AnnotationInfo::type).containsExactly(Marks.INCLUDE);
    }

    @Test
    void repeatedAnnotationsAreReadAsIfTheyStoodOnTheirOwn() {
        TypeInfo.FieldInfo constant = type("p.model.Order$Status").fields().stream().filter(f -> f.name().equals("NEW")).findFirst().orElseThrow();

        assertThat(constant.annotations()).extracting(AnnotationInfo::type).containsExactly(Marks.TRANSITION, Marks.TRANSITION);
        assertThat(constant.annotations()).extracting(a -> a.string("to", "")).containsExactly("PAID", "CANCELLED");
        assertThat(constant.annotations()).extracting(a -> a.string("on", "")).containsExactly("pay", "cancel");
    }

    @Test
    void annotationsOnPackagesAreRead() {
        assertThat(scan.packageAnnotations()).containsOnlyKeys("p.hidden");
        assertThat(scan.packageAnnotations().get("p.hidden")).extracting(AnnotationInfo::type).containsExactly(Marks.IGNORE);
        assertThat(scan.types()).extracting(TypeInfo::name).as("package-info is no type").doesNotContain("p.hidden.package-info");
    }

    @Test
    void theClassAttributeOfAnAnnotationIsAName() {
        AnnotationInfo info = new AnnotationInfo(Marks.TRANSITION, Map.of("machine", new ClassValue("p.model.Order$Status")));

        assertThat(info.classValue("machine")).isEqualTo("p.model.Order$Status");
        assertThat(info.classValue("missing")).isNull();
    }

    @Test
    void referencesAreStillCollectedForTheOverview() {
        assertThat(type("p.model.Order").references()).contains("p.model.Base", "p.model.Customer", "java.util.List", "p.model.Order$Line");
        assertThat(ClassScanner.scan(List.of())).isEmpty();
    }
}
