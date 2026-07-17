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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CallGraphGeneratorIntegrationTest extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return new File("test-projects").getAbsolutePath();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        copySourceRoot("java-callgraph-fixture/src/main/java", "fixtures/java/main", false);
        copySourceRoot("java-callgraph-fixture/src/test/java", "fixtures/java/test", true);
        copySourceRoot("mixed-callgraph-fixture/src/main/java", "fixtures/mixed/java", false);
        copySourceRoot("mixed-callgraph-fixture/src/main/kotlin", "fixtures/mixed/kotlin", false);
    }

    public void testJavaCalleeGraphMatchesFixture() throws Exception {
        configure(false, CallGraphSettings.DIRECTION_CALLEES, 8);
        PsiMethod root = findMethod("fixture.java.GraphEntry", "start");

        JSONObject graph = generate(root);

        assertEquals(setOf(
                "fixture.java.GraphEntry\nstart",
                "fixture.java.OrderService\nplaceOrder",
                "fixture.java.PaymentService\ncharge",
                "fixture.java.InventoryService\nreserve",
                "fixture.java.ReceiptService\ncreate",
                "fixture.java.AuditService\nrecord"
        ), nodeTitles(graph));

        assertEquals(setOf(
                "fixture.java.GraphEntry\nstart -> fixture.java.OrderService\nplaceOrder",
                "fixture.java.GraphEntry\nstart -> fixture.java.AuditService\nrecord",
                "fixture.java.OrderService\nplaceOrder -> fixture.java.PaymentService\ncharge",
                "fixture.java.OrderService\nplaceOrder -> fixture.java.InventoryService\nreserve",
                "fixture.java.PaymentService\ncharge -> fixture.java.ReceiptService\ncreate",
                "fixture.java.InventoryService\nreserve -> fixture.java.AuditService\nrecord"
        ), edgeTitles(graph));
    }

    public void testJavaCallerGraphFiltersTestSources() throws Exception {
        configure(false, CallGraphSettings.DIRECTION_CALLERS, 1);
        PsiMethod root = findMethod("fixture.java.OrderService", "placeOrder");

        JSONObject graph = generate(root);

        assertEquals(setOf(
                "fixture.java.OrderService\nplaceOrder",
                "fixture.java.GraphEntry\nstart",
                "fixture.java.BatchJob\nrun"
        ), nodeTitles(graph));
        assertEquals(setOf(
                "fixture.java.GraphEntry\nstart -> fixture.java.OrderService\nplaceOrder",
                "fixture.java.BatchJob\nrun -> fixture.java.OrderService\nplaceOrder"
        ), edgeTitles(graph));
        assertFalse(nodeTitles(graph).contains("fixture.java.OrderServiceTest\ncallsPlaceOrder"));
    }

    public void testLazyCalleeExpansionIsRecursive() throws Exception {
        configure(true, CallGraphSettings.DIRECTION_CALLEES, 8);
        PsiMethod root = findMethod("fixture.java.GraphEntry", "start");
        CallGraphGenerator generator = new CallGraphGenerator(getProject());

        JSONObject initial = parse(ReadAction.compute(() -> generator.generate(root)));
        assertEquals(setOf("fixture.java.GraphEntry\nstart"), nodeTitles(initial));
        int rootId = nodeIdByTitle(initial, "fixture.java.GraphEntry\nstart");

        JSONObject firstExpansion = parse(ReadAction.compute(
                () -> generator.expandNode(rootId, CallGraphSettings.DIRECTION_CALLEES)));
        assertEquals(setOf(
                "fixture.java.OrderService\nplaceOrder",
                "fixture.java.AuditService\nrecord"
        ), nodeTitles(firstExpansion));
        assertEquals(Boolean.TRUE,
                nodeByTitle(firstExpansion, "fixture.java.OrderService\nplaceOrder").get("hasCallees"));
        assertEquals(Boolean.FALSE,
                nodeByTitle(firstExpansion, "fixture.java.AuditService\nrecord").get("hasCallees"));

        int placeOrderId = nodeIdByTitle(firstExpansion, "fixture.java.OrderService\nplaceOrder");
        JSONObject secondExpansion = parse(ReadAction.compute(
                () -> generator.expandNode(placeOrderId, CallGraphSettings.DIRECTION_CALLEES)));
        assertEquals(setOf(
                "fixture.java.PaymentService\ncharge",
                "fixture.java.InventoryService\nreserve"
        ), nodeTitles(secondExpansion));

        int chargeId = nodeIdByTitle(secondExpansion, "fixture.java.PaymentService\ncharge");
        JSONObject thirdExpansion = parse(ReadAction.compute(
                () -> generator.expandNode(chargeId, CallGraphSettings.DIRECTION_CALLEES)));
        assertEquals(setOf("fixture.java.ReceiptService\ncreate"), nodeTitles(thirdExpansion));
    }

    public void testMixedJavaKotlinGraphResolvesBothLanguages() throws Exception {
        CallGraphSettings settings = configure(false, CallGraphSettings.DIRECTION_CALLEES, 8);
        PsiMethod checkout = findMethod("fixture.mixed.CheckoutFlow", "checkout");

        JSONObject callees = generate(checkout);
        assertEquals(setOf(
                "fixture.mixed.CheckoutFlow\ncheckout",
                "fixture.mixed.PricingService\ncalculate",
                "fixture.mixed.Notifier\nnotifyCustomer",
                "fixture.mixed.DiscountPolicy\napply",
                "fixture.mixed.TaxService\naddTax",
                "fixture.mixed.AuditSink\nrecord"
        ), nodeTitles(callees));
        assertEquals(setOf(
                "fixture.mixed.CheckoutFlow\ncheckout -> fixture.mixed.PricingService\ncalculate",
                "fixture.mixed.CheckoutFlow\ncheckout -> fixture.mixed.Notifier\nnotifyCustomer",
                "fixture.mixed.PricingService\ncalculate -> fixture.mixed.DiscountPolicy\napply",
                "fixture.mixed.PricingService\ncalculate -> fixture.mixed.TaxService\naddTax",
                "fixture.mixed.DiscountPolicy\napply -> fixture.mixed.AuditSink\nrecord",
                "fixture.mixed.Notifier\nnotifyCustomer -> fixture.mixed.AuditSink\nrecord"
        ), edgeTitles(callees));

        settings.setGraphDirection(CallGraphSettings.DIRECTION_CALLERS);
        settings.setMaxDepth(1);
        JSONObject callers = generate(checkout);
        assertEquals(setOf(
                "fixture.mixed.CheckoutFlow\ncheckout",
                "fixture.mixed.WebEndpoint\nhandle",
                "fixture.mixed.BatchCheckout\nrun"
        ), nodeTitles(callers));
        assertEquals(setOf(
                "fixture.mixed.WebEndpoint\nhandle -> fixture.mixed.CheckoutFlow\ncheckout",
                "fixture.mixed.BatchCheckout\nrun -> fixture.mixed.CheckoutFlow\ncheckout"
        ), edgeTitles(callers));
    }

    private void copySourceRoot(String fixturePath, String targetPath, boolean testSource) {
        myFixture.copyDirectoryToProject(fixturePath, targetPath);
        VirtualFile root = myFixture.findFileInTempDir(targetPath);
        assertNotNull(root);
        PsiTestUtil.addSourceRoot(getModule(), root, testSource);
    }

    private CallGraphSettings configure(boolean lazy, String direction, int maxDepth) {
        CallGraphSettings settings = CallGraphSettings.getInstance(getProject());
        settings.setLazyExpansion(lazy);
        settings.setGraphDirection(direction);
        settings.setMaxDepth(maxDepth);
        settings.setMaxCallersPerNode(20);
        settings.setMaxTotalNodes(100);
        settings.setFilterTestCode(true);
        return settings;
    }

    private JSONObject generate(PsiMethod root) throws Exception {
        CallGraphGenerator generator = new CallGraphGenerator(getProject());
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

    private static Set<String> nodeTitles(JSONObject graph) {
        Set<String> result = new HashSet<>();
        for (Object value : (JSONArray) graph.get("nodes")) {
            result.add((String) ((JSONObject) value).get("title"));
        }
        return result;
    }

    private static Set<String> edgeTitles(JSONObject graph) {
        Map<String, String> titlesById = new HashMap<>();
        for (Object value : (JSONArray) graph.get("nodes")) {
            JSONObject node = (JSONObject) value;
            titlesById.put(String.valueOf(node.get("id")), (String) node.get("title"));
        }

        Set<String> result = new HashSet<>();
        for (Object value : (JSONArray) graph.get("edges")) {
            JSONObject edge = (JSONObject) value;
            result.add(titlesById.get(String.valueOf(edge.get("from")))
                    + " -> " + titlesById.get(String.valueOf(edge.get("to"))));
        }
        return result;
    }

    private static JSONObject nodeByTitle(JSONObject graph, String title) {
        for (Object value : (JSONArray) graph.get("nodes")) {
            JSONObject node = (JSONObject) value;
            if (title.equals(node.get("title"))) return node;
        }
        fail("Node not found: " + title);
        return null;
    }

    private static int nodeIdByTitle(JSONObject graph, String title) {
        return ((Number) nodeByTitle(graph, title).get("id")).intValue();
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
