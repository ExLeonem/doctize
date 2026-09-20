package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.ArchitectureSettings.Direction;
import dev.doctize.config.ArchitectureSettings.ForbiddenDependency;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ArchitectureDiagramTest {

    static Path classes;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        classes = CompiledSources.compile(work, CompiledSources.layeredProject());
    }

    private static ArchitectureSettings settings(int minReferences, Direction direction, List<String> layers, List<ForbiddenDependency> forbid, boolean cycles) {
        return new ArchitectureSettings(true, "", "", 100, "", 0, null, null, null, layers, forbid, cycles, false, 2, minReferences, direction);
    }

    private static String svg(ArchitectureSettings settings) {
        ArchitectureModel model = new ArchitectureAnalyzer(settings).analyze(List.of(classes));
        return ArchitectureDiagram.svg(model, settings);
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Element> elements(Document document, String tag, String cssClass) {
        NodeList all = document.getElementsByTagName(tag);
        List<Element> matching = new ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            if (List.of(element.getAttribute("class").split(" ")).contains(cssClass)) {
                matching.add(element);
            }
        }
        return matching;
    }

    /** The position of every box, by name. */
    private static Map<String, double[]> positions(Document document) {
        Pattern translate = Pattern.compile("translate\\(([-0-9.]+) ([-0-9.]+)\\)");
        Map<String, double[]> positions = new HashMap<>();
        for (Element node : elements(document, "g", "arch-node")) {
            Matcher matcher = translate.matcher(node.getAttribute("transform"));
            assertThat(matcher.find()).isTrue();
            String name = node.getElementsByTagName("text").item(0).getTextContent();
            positions.put(name, new double[] {Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2))});
        }
        return positions;
    }

    @Test
    void theSvgIsWellFormedAndHasABoxPerPartAndAnArrowPerDependency() throws Exception {
        Document document = parse(svg(settings(1, Direction.TOP_DOWN, null, null, true)));

        assertThat(elements(document, "g", "arch-node")).hasSize(4);
        assertThat(elements(document, "path", "arch-edge")).hasSize(5);
        assertThat(document.getElementsByTagName("svg").item(0).getAttributes().getNamedItem("viewBox")).isNotNull();
    }

    @Test
    void thereIsNoBlankLineBecauseMarkdownWouldEndTheHtmlBlockThere() {
        String svg = svg(settings(1, Direction.TOP_DOWN, null, null, true));

        assertThat(svg).doesNotContain("\n\n").startsWith("<div class=\"arch-figure\">\n").endsWith("</div>\n");
        assertThat(svg.lines()).allSatisfy(line -> assertThat(line).isNotBlank());
        assertThat(svg.lines()).as("every line starts at the left edge, or Markdown would read it as code").allSatisfy(line -> assertThat(line).doesNotStartWith(" "));
    }

    @Test
    void theSameInputGivesTheSameDrawing() {
        assertThat(svg(settings(1, Direction.TOP_DOWN, null, null, true))).isEqualTo(svg(settings(1, Direction.TOP_DOWN, null, null, true)));
    }

    @Test
    void partsThatDependOnOthersAreAboveThem() throws Exception {
        Map<String, double[]> at = positions(parse(svg(settings(1, Direction.TOP_DOWN, null, null, false))));

        assertThat(at.get("api")[1]).isLessThan(at.get("service")[1]);
        assertThat(at.get("service")[1]).isLessThan(at.get("domain")[1]);
    }

    @Test
    void leftToRightPutsDependentsOnTheLeft() throws Exception {
        Map<String, double[]> at = positions(parse(svg(settings(1, Direction.LEFT_RIGHT, null, null, false))));

        assertThat(at.get("api")[0]).isLessThan(at.get("service")[0]);
        assertThat(at.get("service")[0]).isLessThan(at.get("domain")[0]);
    }

    @Test
    void configuredLayersDecideTheRows() throws Exception {
        Map<String, double[]> at = positions(parse(svg(settings(1, Direction.TOP_DOWN, List.of("infra", "api", "service", "domain"), null, false))));

        assertThat(at.get("infra")[1]).as("infra is the top layer, although nothing above it depends on it").isLessThan(at.get("api")[1]);
    }

    @Test
    void partsInACycleAreMarkedAndShareARow() throws Exception {
        Document document = parse(svg(settings(1, Direction.TOP_DOWN, null, null, true)));
        Map<String, double[]> at = positions(document);

        List<String> marked = elements(document, "g", "arch-node--cycle").stream().map(g -> g.getElementsByTagName("text").item(0).getTextContent()).toList();
        assertThat(marked).containsExactlyInAnyOrder("domain", "infra");
        assertThat(at.get("domain")[1]).isEqualTo(at.get("infra")[1]);
        assertThat(elements(document, "path", "arch-edge--alert")).as("both arrows of the cycle").hasSize(2);
    }

    @Test
    void brokenRulesAreDrawnInRed() throws Exception {
        Document document = parse(svg(settings(1, Direction.TOP_DOWN, null,
                List.of(new ForbiddenDependency("com.acme.domain.**", "com.acme.infra.**", "")), false)));

        assertThat(elements(document, "path", "arch-edge--alert")).singleElement().satisfies(edge ->
                assertThat(edge.getElementsByTagName("title").item(0).getTextContent()).startsWith("domain depends on infra"));
    }

    @Test
    void dependenciesBelowTheThresholdAreNotDrawn() throws Exception {
        Document document = parse(svg(settings(3, Direction.TOP_DOWN, null, null, false)));

        // Only service → domain and infra → domain have three references.
        assertThat(elements(document, "path", "arch-edge")).extracting(edge -> edge.getElementsByTagName("title").item(0).getTextContent())
                .containsExactlyInAnyOrder("service depends on domain (3 references)", "infra depends on domain (3 references)");
    }

    @Test
    void boxesInTheSameRowDoNotOverlap() throws Exception {
        Document document = parse(svg(settings(1, Direction.TOP_DOWN, null, null, false)));
        List<double[]> boxes = new ArrayList<>();
        for (Element node : elements(document, "g", "arch-node")) {
            Element rect = (Element) node.getElementsByTagName("rect").item(0);
            Matcher matcher = Pattern.compile("translate\\(([-0-9.]+) ([-0-9.]+)\\)").matcher(node.getAttribute("transform"));
            matcher.find();
            boxes.add(new double[] {Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(rect.getAttribute("width")), Double.parseDouble(rect.getAttribute("height"))});
        }
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                double[] a = boxes.get(i);
                double[] b = boxes.get(j);
                boolean overlap = a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
                assertThat(overlap).as("boxes %d and %d", i, j).isFalse();
            }
        }
    }

    @Test
    void everythingStaysInsideTheViewBox() throws Exception {
        Document document = parse(svg(settings(1, Direction.TOP_DOWN, null, null, true)));
        String[] viewBox = ((Element) document.getElementsByTagName("svg").item(0)).getAttribute("viewBox").split(" ");
        double width = Double.parseDouble(viewBox[2]);
        double height = Double.parseDouble(viewBox[3]);

        for (Element node : elements(document, "g", "arch-node")) {
            Element rect = (Element) node.getElementsByTagName("rect").item(0);
            Matcher matcher = Pattern.compile("translate\\(([-0-9.]+) ([-0-9.]+)\\)").matcher(node.getAttribute("transform"));
            matcher.find();
            assertThat(Double.parseDouble(matcher.group(1)) + Double.parseDouble(rect.getAttribute("width"))).isLessThanOrEqualTo(width);
            assertThat(Double.parseDouble(matcher.group(2)) + Double.parseDouble(rect.getAttribute("height"))).isLessThanOrEqualTo(height);
            assertThat(Double.parseDouble(matcher.group(1))).isGreaterThanOrEqualTo(0);
        }
    }

    /** Every point of every arrow, sampled along its curve. */
    private static List<double[]> pointsOfArrows(Document document) {
        List<double[]> points = new ArrayList<>();
        for (Element edge : elements(document, "path", "arch-edge")) {
            Matcher numbers = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(edge.getAttribute("d"));
            double[] n = new double[8];
            for (int i = 0; i < 8; i++) {
                assertThat(numbers.find()).isTrue();
                n[i] = Double.parseDouble(numbers.group());
            }
            for (int step = 0; step <= 20; step++) {
                double t = step / 20.0;
                double u = 1 - t;
                double x = u * u * u * n[0] + 3 * u * u * t * n[2] + 3 * u * t * t * n[4] + t * t * t * n[6];
                double y = u * u * u * n[1] + 3 * u * u * t * n[3] + 3 * u * t * t * n[5] + t * t * t * n[7];
                points.add(new double[] {x, y});
            }
        }
        return points;
    }

    @Test
    void arrowsStayInsideTheDrawingEvenWhenTheyArcOverTheFirstRow() throws Exception {
        // The domain and infra cycle: its arrows arc over their row.
        for (Direction direction : Direction.values()) {
            ArchitectureSettings settings = settings(1, direction, null, null, true);
            ArchitectureModel model = new ArchitectureAnalyzer(settings).analyze(List.of(classes));
            Document document = parse(ArchitectureDiagram.svg(model, settings));
            String[] viewBox = ((Element) document.getElementsByTagName("svg").item(0)).getAttribute("viewBox").split(" ");
            double width = Double.parseDouble(viewBox[2]);
            double height = Double.parseDouble(viewBox[3]);

            assertThat(pointsOfArrows(document)).as(direction.name()).allSatisfy(point -> {
                assertThat(point[0]).isBetween(0.0, width);
                assertThat(point[1]).isBetween(0.0, height);
            });
        }
    }

    @Test
    void arrowsThatArcOverTheVeryFirstRowAreNotCutOff(@TempDir Path work) throws Exception {
        // Three parts that all depend on each other and on nothing else: they are the first row, and the arcs go above it.
        Path ring = CompiledSources.compile(work, Map.of(
                "r.a.A", "package r.a; public class A { Object b = new r.b.B(); Object c = new r.c.C(); }",
                "r.b.B", "package r.b; public class B { Object c = new r.c.C(); Object a = new r.a.A(); }",
                "r.c.C", "package r.c; public class C { Object a = new r.a.A(); Object b = new r.b.B(); }"));
        for (Direction direction : Direction.values()) {
            ArchitectureSettings settings = settings(1, direction, null, null, true);
            ArchitectureModel model = new ArchitectureAnalyzer(settings).analyze(List.of(ring));
            Document document = parse(ArchitectureDiagram.svg(model, settings));
            String[] viewBox = ((Element) document.getElementsByTagName("svg").item(0)).getAttribute("viewBox").split(" ");

            assertThat(pointsOfArrows(document)).as(direction.name()).isNotEmpty().allSatisfy(point -> {
                assertThat(point[0]).isBetween(0.0, Double.parseDouble(viewBox[2]));
                assertThat(point[1]).isBetween(0.0, Double.parseDouble(viewBox[3]));
            });
        }
    }

    @Test
    void namesAreEscapedSoThatTheyCannotInjectMarkup() throws Exception {
        ArchitectureModel model = new ArchitectureModel("", 1, 1,
                List.of(new ArchitectureModel.Node("<script>x</script>", false, "", List.of("a\"b"), 1, -1)), List.of(), List.of(), List.of(), List.of());

        String svg = ArchitectureDiagram.svg(model, settings(1, Direction.TOP_DOWN, null, null, true));

        assertThat(svg).doesNotContain("<script>");
        parse(svg);
    }
}
