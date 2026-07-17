package callgraph.callgraph;

import callgraph.callgraph.settings.CallGraphSettings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtil;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unchecked")
@Service(Service.Level.PROJECT)
public final class CallGraphGenerator {
    private final Project project;
    private final JSONArray nodes;
    private final JSONArray edges;
    private final JSONObject groups;
    private final HashMap<Integer, PsiElement> references = new HashMap<>();
    private final HashMap<PsiElement, Integer> referenceIds = new HashMap<>();
    private final HashMap<Integer, Integer> nodeLevels = new HashMap<>();
    private final HashMap<Integer, Boolean> callerAvailability = new HashMap<>();
    private final HashMap<Integer, Boolean> calleeAvailability = new HashMap<>();
    private final Set<Integer> truncatedNodes = new HashSet<>();
    private PsiMethod lastGeneratedMethod;
    private int nextReferenceId = 1;

    public CallGraphGenerator(Project project) {
        this.project = project;
        this.nodes = new JSONArray();
        this.edges = new JSONArray();
        this.groups = new JSONObject();
    }

    public static CallGraphGenerator getInstance(Project project) {
        return project.getService(CallGraphGenerator.class);
    }

    public String generate(PsiMethod mainMethod) {
        clear();

        lastGeneratedMethod = mainMethod;

        if (mainMethod.getContainingClass() == null) {
            return getJson();
        }

        int mainMethodId = referenceId(mainMethod);
        nodeLevels.put(mainMethodId, 0);

        CallGraphSettings settings = CallGraphSettings.getInstance(project);

        JSONObject mainNode = createMethodNode(mainMethod, 0, settings);
        mainNode.put("shape", "circle");
        createGroupIfNotExists(mainMethod);
        nodes.add(mainNode);

        if (!settings.isLazyExpansion()) {
            String direction = settings.getGraphDirection();
            if (CallGraphSettings.DIRECTION_CALLERS.equals(direction) || CallGraphSettings.DIRECTION_BOTH.equals(direction)) {
                findAndAddCallers(mainMethod, 1, settings);
            }
            if (CallGraphSettings.DIRECTION_CALLEES.equals(direction) || CallGraphSettings.DIRECTION_BOTH.equals(direction)) {
                findAndAddCallees(mainMethod, 1, settings);
            }
        }

        return getJson();
    }

    /**
     * Expands a single node by finding its direct callers/callees (one level).
     * Direction overrides the settings direction if non-null.
     * Returns only the newly discovered nodes and edges (delta).
     */
    public String expandNode(int hashCode, String direction) {
        PsiElement element = references.get(hashCode);
        if (!(element instanceof PsiMethod)) {
            return "{\"nodes\":[], \"edges\":[], \"groups\":{}}";
        }

        PsiMethod method = (PsiMethod) element;
        if (method.getContainingClass() == null) {
            return "{\"nodes\":[], \"edges\":[], \"groups\":{}}";
        }

        CallGraphSettings settings = CallGraphSettings.getInstance(project);
        if (direction == null || direction.isEmpty()) {
            direction = settings.getGraphDirection();
        }

        JSONArray newNodes = new JSONArray();
        JSONArray newEdges = new JSONArray();
        JSONObject newGroups = new JSONObject();

        int parentLevel = nodeLevels.getOrDefault(hashCode, 0);

        // Clear truncated state for this node before re-expanding so we can detect fresh truncation
        truncatedNodes.remove(hashCode);

        if (CallGraphSettings.DIRECTION_CALLERS.equals(direction) || CallGraphSettings.DIRECTION_BOTH.equals(direction)) {
            findDirectCallers(method, parentLevel + 1, settings, newNodes, newEdges, newGroups);
        }
        if (CallGraphSettings.DIRECTION_CALLEES.equals(direction) || CallGraphSettings.DIRECTION_BOTH.equals(direction)) {
            findDirectCallees(method, parentLevel + 1, settings, newNodes, newEdges, newGroups);
        }

        nodes.addAll(newNodes);
        edges.addAll(newEdges);
        groups.putAll(newGroups);

        JSONArray truncated = new JSONArray();
        truncated.addAll(truncatedNodes);

        JSONObject result = new JSONObject();
        result.put("nodes", newNodes);
        result.put("edges", newEdges);
        result.put("groups", newGroups);
        result.put("truncatedNodes", truncated);
        return result.toJSONString();
    }

    public String getJson() {
        JSONObject graph = new JSONObject();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("groups", groups);
        JSONArray truncated = new JSONArray();
        truncated.addAll(truncatedNodes);
        graph.put("truncatedNodes", truncated);
        return graph.toJSONString();
    }

    private void clear() {
        nodes.clear();
        edges.clear();
        groups.clear();
        references.clear();
        referenceIds.clear();
        nodeLevels.clear();
        callerAvailability.clear();
        calleeAvailability.clear();
        truncatedNodes.clear();
        nextReferenceId = 1;
    }

    private int referenceId(PsiElement element) {
        Integer existing = referenceIds.get(element);
        if (existing != null) return existing;

        int id = nextReferenceId++;
        referenceIds.put(element, id);
        references.put(id, element);
        return id;
    }

    private boolean hasReference(PsiElement element) {
        return referenceIds.containsKey(element);
    }

    private void findAndAddCallers(PsiMethod method, int depth, CallGraphSettings settings) {
        if (depth > settings.getMaxDepth()) return;
        if (nodes.size() >= settings.getMaxTotalNodes()) return;

        Collection<PsiReference> allReferences = collectCallerReferences(method);
        int cap = settings.getMaxCallersPerNode();
        int added = 0;

        for (PsiReference reference : allReferences) {
            PsiElement callReference = reference.getElement();
            PsiMethod caller = resolveCallerMethod(callReference);

            if (hasReference(callReference)) continue;
            if (!isEligibleCaller(caller, method, settings)) continue;

            if (added >= cap || nodes.size() >= settings.getMaxTotalNodes()) {
                truncatedNodes.add(referenceId(method));
                break;
            }

            boolean nodeNotExists = !hasReference(caller);
            if (nodeNotExists) {
                int callerId = referenceId(caller);
                nodeLevels.put(callerId, depth);
                nodes.add(createMethodNode(caller, depth, settings));
                createGroupIfNotExists(caller);
            }

            referenceId(callReference);
            edges.add(createCallerEdge(method, callReference, caller));
            added++;

            if (nodeNotExists && nodes.size() < settings.getMaxTotalNodes()) {
                findAndAddCallers(caller, depth + 1, settings);
            }
        }
    }

    private void findAndAddCallees(PsiMethod method, int depth, CallGraphSettings settings) {
        if (depth > settings.getMaxDepth()) return;
        if (nodes.size() >= settings.getMaxTotalNodes()) return;

        for (CallSite site : collectCalleeSites(method)) {
            if (nodes.size() >= settings.getMaxTotalNodes()) break;
            PsiMethod callee = site.callee;
            PsiElement callExpr = site.callExpr;
            if (!isEligibleCallee(callee, settings)) continue;
            if (hasReference(callExpr)) continue;

            boolean nodeNotExists = !hasReference(callee);
            if (nodeNotExists) {
                int calleeId = referenceId(callee);
                nodeLevels.put(calleeId, depth);
                nodes.add(createMethodNode(callee, depth, settings));
                createGroupIfNotExists(callee);
            }

            referenceId(callExpr);
            edges.add(createCalleeEdge(method, callExpr, callee));

            if (nodeNotExists && nodes.size() < settings.getMaxTotalNodes()) {
                findAndAddCallees(callee, depth + 1, settings);
            }
        }
    }

    private void findDirectCallers(PsiMethod method, int level, CallGraphSettings settings,
                                    JSONArray newNodes, JSONArray newEdges, JSONObject newGroups) {
        Collection<PsiReference> allReferences = collectCallerReferences(method);
        int cap = settings.getMaxCallersPerNode();
        int added = 0;

        for (PsiReference reference : allReferences) {
            PsiElement callReference = reference.getElement();
            PsiMethod caller = resolveCallerMethod(callReference);

            if (hasReference(callReference)) continue;
            if (!isEligibleCaller(caller, method, settings)) continue;

            if (added >= cap || nodes.size() + newNodes.size() >= settings.getMaxTotalNodes()) {
                truncatedNodes.add(referenceId(method));
                break;
            }

            if (!hasReference(caller)) {
                int callerId = referenceId(caller);
                nodeLevels.put(callerId, level);
                newNodes.add(createMethodNode(caller, level, settings));
                addGroupIfNotExists(caller, newGroups);
            }

            referenceId(callReference);
            newEdges.add(createCallerEdge(method, callReference, caller));
            added++;
        }
    }

    private void findDirectCallees(PsiMethod method, int level, CallGraphSettings settings,
                                    JSONArray newNodes, JSONArray newEdges, JSONObject newGroups) {
        for (CallSite site : collectCalleeSites(method)) {
            if (nodes.size() + newNodes.size() >= settings.getMaxTotalNodes()) break;
            PsiMethod callee = site.callee;
            PsiElement callExpr = site.callExpr;
            if (!isEligibleCallee(callee, settings)) continue;
            if (hasReference(callExpr)) continue;

            if (!hasReference(callee)) {
                int calleeId = referenceId(callee);
                nodeLevels.put(calleeId, level);
                newNodes.add(createMethodNode(callee, level, settings));
                addGroupIfNotExists(callee, newGroups);
            }

            referenceId(callExpr);
            newEdges.add(createCalleeEdge(method, callExpr, callee));
        }
    }

    private boolean hasCallers(PsiMethod method, CallGraphSettings settings) {
        int methodId = referenceId(method);
        Boolean cached = callerAvailability.get(methodId);
        if (cached != null) return cached;

        boolean result = hasEligibleCaller(ReferencesSearch.search(method), method, settings);
        if (!result) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                for (PsiClass anInterface : containingClass.getInterfaces()) {
                    PsiMethod interfaceMethod = anInterface.findMethodBySignature(method, false);
                    if (interfaceMethod != null
                            && hasEligibleCaller(ReferencesSearch.search(interfaceMethod), method, settings)) {
                        result = true;
                        break;
                    }
                }
            }
        }

        callerAvailability.put(methodId, result);
        return result;
    }

    private boolean hasEligibleCaller(Query<PsiReference> query, PsiMethod method, CallGraphSettings settings) {
        boolean[] found = {false};
        query.forEach(reference -> {
            PsiMethod caller = resolveCallerMethod(reference.getElement());
            if (isEligibleCaller(caller, method, settings)) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    private boolean isEligibleCaller(@Nullable PsiMethod caller, PsiMethod method, CallGraphSettings settings) {
        return caller != null
                && caller.getProject().equals(method.getProject())
                && caller.getContainingClass() != null
                && (!settings.isFilterTestCode() || !isTestCode(caller));
    }

    private boolean isEligibleCallee(PsiMethod callee, CallGraphSettings settings) {
        return callee.getContainingClass() != null
                && isInProjectSource(callee)
                && (!settings.isFilterTestCode() || !isTestCode(callee));
    }

    @NotNull
    private Collection<PsiReference> collectCallerReferences(PsiMethod method) {
        Collection<PsiReference> allReferences = ReferencesSearch.search(method).findAll();
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            for (PsiClass anInterface : containingClass.getInterfaces()) {
                PsiMethod methodBySignature = anInterface.findMethodBySignature(method, false);
                if (methodBySignature != null) {
                    allReferences.addAll(ReferencesSearch.search(methodBySignature).findAll());
                }
            }
        }
        return allReferences;
    }

    @NotNull
    private JSONObject createCallerEdge(PsiMethod method, PsiElement callElement, PsiMethod caller) {
        JSONObject edge = new JSONObject();
        edge.put("id", referenceId(callElement));
        edge.put("from", referenceId(caller));
        edge.put("to", referenceId(method));

        PsiFile file = callElement.getContainingFile();
        Document document = file.getViewProvider().getDocument();
        int lineNumber = document.getLineNumber(callElement.getTextOffset()) + 1;
        edge.put("label", ":" + lineNumber);

        JSONObject group = getGroup(caller);
        if (group != null) edge.put("font", group.get("font"));

        return edge;
    }

    @NotNull
    private JSONObject createCalleeEdge(PsiMethod method, PsiElement callExpr, PsiMethod callee) {
        JSONObject edge = new JSONObject();
        edge.put("id", referenceId(callExpr));
        edge.put("from", referenceId(method));
        edge.put("to", referenceId(callee));

        PsiFile file = callExpr.getContainingFile();
        Document document = file.getViewProvider().getDocument();
        int lineNumber = document.getLineNumber(callExpr.getTextOffset()) + 1;
        edge.put("label", ":" + lineNumber);

        JSONObject group = getGroup(method);
        if (group != null) edge.put("font", group.get("font"));

        return edge;
    }

    private JSONObject createMethodNode(PsiMethod method, int depth, CallGraphSettings settings) {
        JSONObject node = new JSONObject();
        node.put("id", referenceId(method));
        node.put("group", method.getContainingClass().getQualifiedName());
        node.put("title", method.getContainingClass().getQualifiedName() + "\n" + method.getName());
        node.put("level", depth);

        String label = method.getContainingClass().getName() + "\n" + method.getName();

        PsiAnnotation controllerAnnotation = getControllerAnnotation(method);
        if (controllerAnnotation != null) {
            label = "*" + prepareServiceName(method, controllerAnnotation) + "*" + "\n" + label;
        }

        node.put("label", label);
        node.put("hasCallers", hasCallers(method, settings));
        node.put("hasCallees", hasCallees(method, settings));
        return node;
    }

    /** Simple holder for a call site: the call expression and the resolved callee. */
    private static class CallSite {
        final PsiElement callExpr;
        final PsiMethod callee;
        CallSite(PsiElement callExpr, PsiMethod callee) {
            this.callExpr = callExpr;
            this.callee = callee;
        }
    }

    /**
     * Collects all call sites (call expression + resolved callee) within the given method body.
     * Handles both Java ({@link PsiMethodCallExpression}) and Kotlin ({@link KtCallExpression}).
     */
    @NotNull
    private List<CallSite> collectCalleeSites(PsiMethod method) {
        List<CallSite> sites = new ArrayList<>();

        // Check Kotlin first: KtLightMethod.getBody() may return a non-null synthetic body,
        // so we must not rely on getBody() == null to detect Kotlin methods.
        KtNamedFunction ktFunction = getKotlinOrigin(method);
        if (ktFunction != null) {
            for (KtCallExpression call : PsiTreeUtil.findChildrenOfType(ktFunction, KtCallExpression.class)) {
                PsiMethod callee = resolveKtCall(call);
                if (callee != null) sites.add(new CallSite(call, callee));
            }
            return sites;
        }

        // Java method body
        if (method.getBody() != null) {
            for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(method.getBody(), PsiMethodCallExpression.class)) {
                PsiMethod callee = call.resolveMethod();
                if (callee != null) sites.add(new CallSite(call, callee));
            }
        }

        return sites;
    }

    private boolean hasCallees(PsiMethod method, CallGraphSettings settings) {
        int methodId = referenceId(method);
        Boolean cached = calleeAvailability.get(methodId);
        if (cached != null) return cached;

        boolean result = false;
        for (CallSite site : collectCalleeSites(method)) {
            if (isEligibleCallee(site.callee, settings)) {
                result = true;
                break;
            }
        }

        calleeAvailability.put(methodId, result);
        return result;
    }

    /**
     * Given a call-site reference element (from {@link ReferencesSearch}), returns the containing
     * method — works for both Java ({@link PsiMethod} ancestor) and Kotlin ({@link KtNamedFunction}
     * ancestor, converted to its light class method).
     */
    @Nullable
    private static PsiMethod resolveCallerMethod(PsiElement callReference) {
        // Java: the reference lives directly inside a PsiMethod
        PsiMethod javaMethod = PsiTreeUtil.getParentOfType(callReference, PsiMethod.class);
        if (javaMethod != null) return javaMethod;

        // Kotlin: the reference lives inside a KtNamedFunction; get its light-class PsiMethod
        KtNamedFunction ktFunction = PsiTreeUtil.getParentOfType(callReference, KtNamedFunction.class);
        if (ktFunction != null) {
            PsiElement nav = ktFunction.getNavigationElement();
            if (nav instanceof KtNamedFunction) {
                return LightClassUtil.INSTANCE.getLightClassMethod((KtNamedFunction) nav);
            }
            return LightClassUtil.INSTANCE.getLightClassMethod(ktFunction);
        }
        return null;
    }

    /** Returns the Kotlin source function for a light method, or null if this is a pure Java method. */
    @Nullable
    private static KtNamedFunction getKotlinOrigin(PsiMethod method) {
        PsiElement nav = method.getNavigationElement();
        if (nav instanceof KtNamedFunction) {
            return (KtNamedFunction) nav;
        }
        return null;
    }

    /**
     * Resolves a Kotlin call expression to a {@link PsiMethod}.
     * Handles both Kotlin-declared functions (converting them via light classes) and
     * Java methods called from Kotlin.
     */
    @Nullable
    private static PsiMethod resolveKtCall(KtCallExpression call) {
        KtExpression calleeExpr = call.getCalleeExpression();
        if (calleeExpr == null) return null;
        // Kotlin registers references via PsiReferenceContributor, so getReference() (singular)
        // returns null — we must use getReferences() (plural) to get the Kotlin-contributed reference.
        for (PsiReference ref : calleeExpr.getReferences()) {
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiMethod) return (PsiMethod) resolved;
            if (resolved instanceof KtNamedFunction) {
                return LightClassUtil.INSTANCE.getLightClassMethod((KtNamedFunction) resolved);
            }
        }
        return null;
    }

    private PsiAnnotation getControllerAnnotation(PsiJvmModifiersOwner element) {
        PsiAnnotation[] annotations = element.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            String name = annotation.getQualifiedName();
            if (name == null) continue;
            if (name.contains("GetMapping") || name.contains("PostMapping") || name.contains("PutMapping")
                    || name.contains("DeleteMapping") || name.contains("RequestMapping")) {
                return annotation;
            }
        }
        return null;
    }

    private String prepareServiceName(PsiMethod method, PsiAnnotation annotation) {
        String serviceName = "";

        PsiAnnotation classAnnotation = getControllerAnnotation(method.getContainingClass());
        if (classAnnotation != null) {
            PsiAnnotationMemberValue value = classAnnotation.findAttributeValue("value");
            if (value != null) {
                String classPath = value.getText();
                classPath = fixControllerPath(classPath.substring(1, classPath.length() - 1));
                serviceName = "/" + classPath;
            }
        }

        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value != null) {
            String methodPath = value.getText();
            methodPath = fixControllerPath(methodPath.substring(1, methodPath.length() - 1));
            serviceName = serviceName + "/" + methodPath;
        }

        return serviceName;
    }

    private String fixControllerPath(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private void createGroupIfNotExists(PsiMethod method) {
        String qualifiedName = method.getContainingClass().getQualifiedName();
        if (!groups.containsKey(qualifiedName)) {
            groups.put(qualifiedName, buildGroup());
        }
    }

    private void addGroupIfNotExists(PsiMethod method, JSONObject targetGroups) {
        String qualifiedName = method.getContainingClass().getQualifiedName();
        if (!groups.containsKey(qualifiedName)) {
            JSONObject group = buildGroup();
            groups.put(qualifiedName, group);
            targetGroups.put(qualifiedName, group);
        }
    }

    @NotNull
    private JSONObject buildGroup() {
        UIColor uiColor = UIColor.getNextColor();

        JSONObject color = new JSONObject();
        color.put("background", uiColor.getMainColor());
        color.put("border", uiColor.getMainColor());

        JSONObject hoverColor = new JSONObject();
        hoverColor.put("background", uiColor.getDarkColor());
        hoverColor.put("border", uiColor.getDarkColor());
        color.put("highlight", hoverColor);
        color.put("hover", hoverColor);

        JSONObject font = new JSONObject();
        font.put("color", uiColor.getTextColor());
        font.put("strokeColor", uiColor.getMainColor());

        JSONObject group = new JSONObject();
        group.put("color", color);
        group.put("font", font);
        return group;
    }

    private JSONObject getGroup(PsiMethod method) {
        return (JSONObject) groups.get(method.getContainingClass().getQualifiedName());
    }

    private boolean isInProjectSource(PsiMethod method) {
        PsiFile file = method.getContainingFile();
        if (file == null) return false;
        com.intellij.openapi.vfs.VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) return false;
        return ProjectFileIndex.getInstance(project).isInSourceContent(vFile);
    }

    private static final java.util.Set<String> TEST_ANNOTATION_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Test", "ParameterizedTest", "RepeatedTest",
            "Before", "After", "BeforeClass", "AfterClass",
            "BeforeEach", "AfterEach", "BeforeAll", "AfterAll",
            "Ignore", "Disabled", "TestCase", "Testable", "UnitTest"
    ));

    private static final java.util.Set<String> TEST_CLASS_SUFFIXES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Test", "Tests", "TestCase", "TestSuite", "TestBase", "IT", "Spec"
    ));

    private boolean isTestCode(PsiMethod method) {
        // 1. Check if file is in a test source root
        PsiFile file = method.getContainingFile();
        if (file != null) {
            com.intellij.openapi.vfs.VirtualFile vFile = file.getVirtualFile();
            if (vFile != null && ProjectFileIndex.getInstance(project).isInTestSourceContent(vFile)) {
                return true;
            }
        }

        // 2. Check if method has a test annotation
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String shortName = annotation.getNameReferenceElement() != null
                    ? annotation.getNameReferenceElement().getReferenceName() : null;
            if (shortName != null && TEST_ANNOTATION_NAMES.contains(shortName)) {
                return true;
            }
        }

        // 3. Check if the containing class name matches test naming conventions
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            String className = containingClass.getName();
            if (className != null) {
                for (String suffix : TEST_CLASS_SUFFIXES) {
                    if (className.endsWith(suffix) || className.startsWith("Test")) {
                        return true;
                    }
                }
            }

            // 4. Check if the class itself has a test runner annotation
            for (PsiAnnotation annotation : containingClass.getAnnotations()) {
                String shortName = annotation.getNameReferenceElement() != null
                        ? annotation.getNameReferenceElement().getReferenceName() : null;
                if (shortName != null && (shortName.contains("RunWith") || shortName.contains("ExtendWith")
                        || shortName.contains("Suite") || TEST_ANNOTATION_NAMES.contains(shortName))) {
                    return true;
                }
            }
        }

        return false;
    }

    public PsiElement getReference(Integer hashCode) {
        return references.get(hashCode);
    }

    public PsiMethod getLastGeneratedMethod() {
        return lastGeneratedMethod;
    }
}
