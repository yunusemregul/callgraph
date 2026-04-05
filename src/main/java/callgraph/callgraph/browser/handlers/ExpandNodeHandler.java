package callgraph.callgraph.browser.handlers;

import callgraph.callgraph.CallGraphGenerator;
import callgraph.callgraph.browser.BrowserManager;
import callgraph.callgraph.browser.JSQueryHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class ExpandNodeHandler extends JSQueryHandler {
    public ExpandNodeHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return nodeHashCode -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (DumbService.isDumb(project)) {
                    BrowserManager.getInstance(project).showMessage("Cannot expand while indexing is in progress.");
                    return;
                }

                String[] parts = nodeHashCode.split("\\|", 2);
                int hashCode;
                String direction = null;
                try {
                    hashCode = Integer.parseInt(parts[0]);
                    if (parts.length > 1) direction = parts[1];
                } catch (NumberFormatException e) {
                    return;
                }

                final int finalHashCode = hashCode;
                final String finalDirection = direction;
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Expanding Node") {
                    public void run(@NotNull ProgressIndicator indicator) {
                        ApplicationManager.getApplication().runReadAction(() -> {
                            String delta = CallGraphGenerator.getInstance(project).expandNode(finalHashCode, finalDirection);
                            BrowserManager.getInstance(project).addToNetwork(delta);
                        });
                    }
                });
            });
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "expandNode";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "nodeHashCode";
    }
}
