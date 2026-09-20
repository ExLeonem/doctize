package dev.doctize.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Compiles small projects for the tests, so that they analyze real class files. */
final class CompiledSources {

    private CompiledSources() {}

    /**
     * @param sources the source of each class, by binary name, e.g. {@code com.acme.Cache}
     * @return the directory with the class files
     */
    static Path compile(Path work, Map<String, String> sources) {
        try {
            Path src = Files.createDirectories(work.resolve("src"));
            Path classes = Files.createDirectories(work.resolve("classes"));
            List<String> arguments = new ArrayList<>(List.of("-d", classes.toString(), "-cp", System.getProperty("java.class.path"), "-proc:none", "-g", "-parameters"));
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path file = src.resolve(source.getKey().replace('.', '/') + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue());
                arguments.add(file.toString());
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
            if (result != 0) {
                throw new IllegalStateException("The test sources do not compile");
            }
            return classes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A small layered project: api → service → domain ← infra, with one dependency that points the wrong way. */
    static Map<String, String> layeredProject() {
        return Map.ofEntries(
                Map.entry("com.acme.api.Controller", """
                        package com.acme.api;
                        public class Controller {
                            private final com.acme.service.OrderService service = new com.acme.service.OrderService();
                            public java.util.List<com.acme.domain.Order> orders() throws com.acme.domain.OrderException {
                                return service.all();
                            }
                        }
                        """),
                Map.entry("com.acme.service.OrderService", """
                        package com.acme.service;
                        import com.acme.domain.*;
                        public class OrderService {
                            private final OrderRepository repository = null;
                            public java.util.List<Order> all() throws OrderException { return repository.findAll(); }
                        }
                        """),
                Map.entry("com.acme.domain.Order", """
                        package com.acme.domain;
                        public record Order(String id, Money total) {
                            public record Line(String sku) {}
                        }
                        """),
                Map.entry("com.acme.domain.Money", """
                        package com.acme.domain;
                        public record Money(long cents) {}
                        """),
                Map.entry("com.acme.domain.OrderException", """
                        package com.acme.domain;
                        public class OrderException extends Exception {}
                        """),
                Map.entry("com.acme.domain.OrderRepository", """
                        package com.acme.domain;
                        public interface OrderRepository {
                            java.util.List<Order> findAll() throws OrderException;
                        }
                        """),
                Map.entry("com.acme.infra.JdbcOrderRepository", """
                        package com.acme.infra;
                        import com.acme.domain.*;
                        public class JdbcOrderRepository implements OrderRepository {
                            private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            public java.util.List<Order> findAll() { return java.util.List.of(new Order.Line("x") == null ? null : new Order("1", new Money(1))); }
                        }
                        """),
                // The wrong way round: the domain reaches into the infrastructure.
                Map.entry("com.acme.domain.Shortcut", """
                        package com.acme.domain;
                        public class Shortcut {
                            public Object create() { return new com.acme.infra.JdbcOrderRepository(); }
                        }
                        """));
    }
}
