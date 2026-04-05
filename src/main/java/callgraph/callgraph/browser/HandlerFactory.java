package callgraph.callgraph.browser;

import callgraph.callgraph.browser.handlers.ExpandNodeHandler;
import callgraph.callgraph.browser.handlers.UpdateSettingHandler;
import callgraph.callgraph.browser.handlers.GenerateGraphHandler;
import callgraph.callgraph.browser.handlers.GoToSourceHandler;
import callgraph.callgraph.browser.handlers.OpenSettingsHandler;
import callgraph.callgraph.browser.handlers.SaveAsHtmlHandler;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowserBase;

import java.util.ArrayList;
import java.util.List;

public class HandlerFactory {
    public List<JSQueryHandler> getHandlers(JBCefBrowserBase browser, Project project) {
        List<JSQueryHandler> handlers = new ArrayList<>();
        handlers.add(new GenerateGraphHandler(browser, project));
        handlers.add(new GoToSourceHandler(browser, project));
        handlers.add(new SaveAsHtmlHandler(browser, project));
        handlers.add(new OpenSettingsHandler(browser, project));
        handlers.add(new ExpandNodeHandler(browser, project));
        handlers.add(new UpdateSettingHandler(browser, project));
        return handlers;
    }
}
