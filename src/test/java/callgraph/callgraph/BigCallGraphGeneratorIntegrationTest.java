package callgraph.callgraph;

import callgraph.callgraph.settings.CallGraphSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

public class BigCallGraphGeneratorIntegrationTest extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return new File("test-projects").getAbsolutePath();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        copySourceRoot("big-callgraph-fixture/src/generated/main/java", "fixtures/big/main", false);
        copySourceRoot("big-callgraph-fixture/src/generated/test/java", "fixtures/big/test", true);
    }

    public void testLargeGraphLimitsLazyExpansionAndCallerBatches() throws Exception {
        CallGraphSettings settings = configure(false, CallGraphSettings.DIRECTION_CALLEES, 20, 300, 10);
        PsiMethod root = findMethod("fixture.big.BigGraphEntry", "start");

        JSONObject fullGraph = generate(new CallGraphGenerator(getProject()), root);
        assertEquals(230, nodes(fullGraph).size());
        assertEquals("Outgoing edges by source package: " + outgoingEdgeSummary(fullGraph),
                672, edges(fullGraph).size());

        settings.setMaxTotalNodes(150);
        JSONObject cappedGraph = generate(new CallGraphGenerator(getProject()), root);
        assertEquals(150, nodes(cappedGraph).size());

        settings.setLazyExpansion(true);
        settings.setMaxTotalNodes(300);
        CallGraphGenerator lazyGenerator = new CallGraphGenerator(getProject());
        JSONObject lazyRoot = generate(lazyGenerator, root);
        assertEquals(1, nodes(lazyRoot).size());
        int rootId = nodeIdByTitle(lazyRoot, "fixture.big.BigGraphEntry\nstart");

        JSONObject firstLayer = parse(ReadAction.compute(
                () -> lazyGenerator.expandNode(rootId, CallGraphSettings.DIRECTION_CALLEES)));
        assertEquals(12, nodes(firstLayer).size());
        assertEquals(12, edges(firstLayer).size());
        for (Object value : nodes(firstLayer)) {
            assertEquals(Boolean.TRUE, ((JSONObject) value).get("hasCallees"));
        }

        PsiMethod hotspot = findMethod("fixture.big.fanin.Hotspot", "execute");
        CallGraphGenerator callerGenerator = new CallGraphGenerator(getProject());
        JSONObject callerRoot = generate(callerGenerator, hotspot);
        int hotspotId = nodeIdByTitle(callerRoot, "fixture.big.fanin.Hotspot\nexecute");

        assertCallerBatch(callerGenerator, hotspotId, 10, true);
        assertCallerBatch(callerGenerator, hotspotId, 10, true);
        assertCallerBatch(callerGenerator, hotspotId, 10, true);
        assertCallerBatch(callerGenerator, hotspotId, 5, false);
    }

    private void assertCallerBatch(CallGraphGenerator generator, int hotspotId,
                                   int expectedNodes, boolean expectedTruncated) throws Exception {
        JSONObject batch = parse(ReadAction.compute(
                () -> generator.expandNode(hotspotId, CallGraphSettings.DIRECTION_CALLERS)));
        assertEquals(expectedNodes, nodes(batch).size());
        assertEquals(expectedNodes, edges(batch).size());
        assertEquals(expectedTruncated, !((JSONArray) batch.get("truncatedNodes")).isEmpty());

        for (Object value : nodes(batch)) {
            String title = (String) ((JSONObject) value).get("title");
            assertFalse("Test caller leaked into the graph: " + title, title.endsWith("Test\ninvoke"));
        }
    }

    private void copySourceRoot(String fixturePath, String targetPath, boolean testSource) {
        myFixture.copyDirectoryToProject(fixturePath, targetPath);
        VirtualFile root = myFixture.findFileInTempDir(targetPath);
        assertNotNull(root);
        PsiTestUtil.addSourceRoot(getModule(), root, testSource);
    }

    private CallGraphSettings configure(boolean lazy, String direction, int maxDepth,
                                        int maxTotalNodes, int maxCallersPerNode) {
        CallGraphSettings settings = CallGraphSettings.getInstance(getProject());
        settings.setLazyExpansion(lazy);
        settings.setGraphDirection(direction);
        settings.setMaxDepth(maxDepth);
        settings.setMaxTotalNodes(maxTotalNodes);
        settings.setMaxCallersPerNode(maxCallersPerNode);
        settings.setFilterTestCode(true);
        return settings;
    }

    private JSONObject generate(CallGraphGenerator generator, PsiMethod root) throws Exception {
        return parse(ReadAction.compute(() -> generator.generate(root)));
    }

    private PsiMethod findMethod(String qualifiedClassName, String methodName) {
        PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(
                qualifiedClassName, GlobalSearchScope.projectScope(getProject()));
        assertNotNull("Class not found: " + qualifiedClassName, psiClass);
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
        assertEquals("Method not found or ambiguous: " + qualifiedClassName + "." + methodName,
                1, methods.length);
        return methods[0];
    }

    private static JSONObject parse(String json) throws Exception {
        return (JSONObject) new JSONParser().parse(json);
    }

    private static JSONArray nodes(JSONObject graph) {
        return (JSONArray) graph.get("nodes");
    }

    private static JSONArray edges(JSONObject graph) {
        return (JSONArray) graph.get("edges");
    }

    private static int nodeIdByTitle(JSONObject graph, String title) {
        for (Object value : nodes(graph)) {
            JSONObject node = (JSONObject) value;
            if (title.equals(node.get("title"))) return ((Number) node.get("id")).intValue();
        }
        fail("Node not found: " + title);
        return -1;
    }

    private static Map<String, Integer> outgoingEdgeSummary(JSONObject graph) {
        Map<String, String> titlesById = new HashMap<>();
        for (Object value : nodes(graph)) {
            JSONObject node = (JSONObject) value;
            titlesById.put(String.valueOf(node.get("id")), (String) node.get("title"));
        }

        Map<String, Integer> result = new TreeMap<>();
        for (Object value : edges(graph)) {
            JSONObject edge = (JSONObject) value;
            String title = titlesById.get(String.valueOf(edge.get("from")));
            String className = title.substring(0, title.indexOf('\n'));
            String sourceGroup = className.contains(".layer")
                    ? className.substring(0, className.lastIndexOf('.'))
                    : className;
            result.put(sourceGroup, result.getOrDefault(sourceGroup, 0) + 1);
        }
        return result;
    }
}
