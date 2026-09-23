package dev.doctize.processor;

import dev.doctize.annotations.Doc;
import dev.doctize.annotations.DocPage;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import dev.doctize.model.SourceModelJson;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Extracts every element annotated with {@link Doc} or {@link DocPage}, together with its Javadoc,
 * and writes the result to {@code META-INF/doctize/model.json} in the class output. The generator
 * reads that file later; nothing is rendered at compile time, except that the external files of
 * {@code {@snippet}} tags are read here.
 */
@SupportedAnnotationTypes({"dev.doctize.annotations.Doc", "dev.doctize.annotations.DocPage"})
@SupportedOptions(SnippetFiles.PATH_OPTION)
public final class DocProcessor extends AbstractProcessor {

    private final List<SourceElement> collected = new ArrayList<>();
    private SourceElementFactory factory;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        this.factory = new SourceElementFactory(environment.getElementUtils(), new SnippetFiles(environment));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) {
            writeModel();
            return false;
        }
        for (Element element : round.getElementsAnnotatedWith(Doc.class)) {
            collected.add(factory.apiType((TypeElement) element));
        }
        for (Element element : round.getElementsAnnotatedWith(DocPage.class)) {
            collected.add(factory.page(element));
        }
        return false;
    }

    private void writeModel() {
        if (collected.isEmpty()) {
            return;
        }
        try {
            var file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", SourceModelJson.RESOURCE_PATH);
            try (OutputStream out = file.openOutputStream()) {
                SourceModelJson.write(SourceModel.of(collected), out);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "Doctize could not write its documentation model: " + e.getMessage());
        }
    }
}
