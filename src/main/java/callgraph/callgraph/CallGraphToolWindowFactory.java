    package callgraph.callgraph;

import callgraph.callgraph.browser.BrowserManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class CallGraphToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BrowserManager browserManager = BrowserManager.getInstance(project);
        JBCefBrowser browser = browserManager.getJBCefBrowser();

        JComponent component = browser.getComponent();
        Content content = toolWindow.getContentManager().getFactory().createContent(component, null, false);
        toolWindow.getContentManager().addContent(content);
        
        // Add listener to refresh component when content is selected
        ContentManager contentManager = toolWindow.getContentManager();
        contentManager.addContentManagerListener(new ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull ContentManagerEvent event) {
                component.revalidate();
                component.repaint();
            }
            
            @Override
            public void contentAdded(@NotNull ContentManagerEvent event) {
                component.revalidate();
                component.repaint();
            }
        });
        
        // Register a tool window activation listener
        project.getMessageBus().connect().subscribe(
            ToolWindowManagerListener.TOPIC, 
            new ToolWindowManagerListener() {
                @Override
                public void toolWindowShown(@NotNull ToolWindow window) {
                    if (window.getId().equals(toolWindow.getId())) {
                        component.revalidate();
                        component.repaint();
                    }
                }
            }
        );
        
        // Wait for browser to be ready before adding listeners
        browserManager.whenBrowserReady(() -> {
            EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
            eventMulticaster.addCaretListener(new CallGraphCaretListener(project, toolWindow));
        });
    }
}
