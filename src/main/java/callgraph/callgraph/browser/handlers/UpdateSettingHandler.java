package callgraph.callgraph.browser.handlers;

import callgraph.callgraph.browser.JSQueryHandler;
import callgraph.callgraph.settings.CallGraphSettings;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class UpdateSettingHandler extends JSQueryHandler {
    public UpdateSettingHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return payload -> {
            String[] parts = payload.split("\\|", 2);
            if (parts.length != 2) return null;

            String key = parts[0];
            String value = parts[1];
            CallGraphSettings settings = CallGraphSettings.getInstance(project);

            switch (key) {
                case "lazyExpansion":
                    settings.setLazyExpansion(Boolean.parseBoolean(value));
                    break;
                case "maxDepth":
                    try { settings.setMaxDepth(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                    break;
                case "maxCallersPerNode":
                    try { settings.setMaxCallersPerNode(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                    break;
                case "maxTotalNodes":
                    try { settings.setMaxTotalNodes(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                    break;
                case "graphDirection":
                    settings.setGraphDirection(value);
                    break;
                case "filterTestCode":
                    settings.setFilterTestCode(Boolean.parseBoolean(value));
                    break;
                case "backgroundType":
                    settings.setBackgroundType(value);
                    break;
                case "customBackgroundColor":
                    settings.setCustomBackgroundColor(value);
                    break;
            }
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "updateSetting";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "keyValue";
    }
}
