package callgraph.callgraph.browser;

import callgraph.callgraph.Utils;
import callgraph.callgraph.settings.CallGraphSettings;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.Timer;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(Service.Level.PROJECT)
public final class BrowserManager {
    private final Project project;
    private final JBCefBrowser browser;
    private final AtomicBoolean browserInitialized = new AtomicBoolean(false);

    public BrowserManager(Project project) {
        this.project = project;
        try {
            browser = new JBCefBrowser();
            
            browser.loadHTML(Utils.getResourceFileAsString("build/callgraph.html"));
            createJavaScriptBridge();
        } catch (IOException e) {
            // TODO: handle this
            throw new RuntimeException(e);
        }
    }

    public static BrowserManager getInstance(Project project) {
        return project.getService(BrowserManager.class);
    }

    public JBCefBrowser getJBCefBrowser() {
        return browser;
    }

    /**
     * Checks if the browser is initialized and ready to execute JavaScript
     * @return true if the browser is initialized, false otherwise
     */
    public boolean isBrowserInitialized() {
        return browserInitialized.get();
    }
    
    /**
     * Executes JavaScript in the browser
     * @param script The JavaScript code to execute
     */
    public void executeJavaScript(String script) {
        browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0);
    }

    public void showMessage(String message) {
        executeJavaScript("showMessage('" + message + "')");
    }

    public void updateNetwork(String json) {
        executeJavaScript("updateNetwork(" + json + ")");
    }

    public void addToNetwork(String json) {
        executeJavaScript("addToNetwork(" + json + ")");
    }

    public void setLazyMode(boolean lazy) {
        executeJavaScript("setLazyMode(" + lazy + ")");
    }

    public void initSettingsUI() {
        CallGraphSettings s = CallGraphSettings.getInstance(project);
        Color editorBg = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
        String ideBgColor = "#" + ColorUtil.toHex(editorBg);
        String json = "{\"lazyExpansion\":" + s.isLazyExpansion()
                + ",\"maxDepth\":" + s.getMaxDepth()
                + ",\"maxCallersPerNode\":" + s.getMaxCallersPerNode()
                + ",\"maxTotalNodes\":" + s.getMaxTotalNodes()
                + ",\"graphDirection\":\"" + s.getGraphDirection() + "\""
                + ",\"filterTestCode\":" + s.isFilterTestCode()
                + ",\"backgroundType\":\"" + s.getBackgroundType() + "\""
                + ",\"customBackgroundColor\":\"" + s.getCustomBackgroundColor() + "\""
                + ",\"ideBackgroundColor\":\"" + ideBgColor + "\""
                + "}";
        executeJavaScript("setInitialSettings(" + json + ")");
    }

    public void setGenerateMessage(String message) {
        executeJavaScript("setGenerateMessage('" + message + "')");
    }
    
    /**
     * Execute a function when the browser is initialized.
     * If the browser is already initialized, executes immediately.
     * If not, sets up a polling mechanism to check periodically.
     *
     * @param callback The function to execute when the browser is ready
     */
    public void whenBrowserReady(Runnable callback) {
        if (isBrowserInitialized()) {
            callback.run();
        } else {
            final int[] attempts = {0};
            final int maxAttempts = 100;
            
            Timer timer = new Timer(100, e -> {
                attempts[0]++;
                
                if (isBrowserInitialized()) {
                    ((Timer) e.getSource()).stop();
                    callback.run();
                } else if (attempts[0] >= maxAttempts) {
                    ((Timer) e.getSource()).stop();
                    showMessage("Browser initialization timeout. Please try again.");
                }
            });
            timer.start();
        }
    }

    /**
     * Apply current settings to the browser display
     */
    public void applySettings() {
        if (isBrowserInitialized()) {
            CallGraphSettings settings = CallGraphSettings.getInstance(project);
            
            // Get the IDE editor background color if needed
            String backgroundColor = settings.getCustomBackgroundColor();
            if (CallGraphSettings.BACKGROUND_TYPE_IDE.equals(settings.getBackgroundType())) {
                Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
                backgroundColor = "#" + ColorUtil.toHex(editorBackground);
            }

            // Apply the background color to the graph
            executeJavaScript("document.body.style.backgroundColor = '" + backgroundColor + "';");
            executeJavaScript("document.getElementById('network').style.backgroundColor = '" + backgroundColor + "';");
            
            // Update message text color based on background color
            executeJavaScript("updateMessageTextColor('" + backgroundColor + "')");
        }
    }

    private void createJavaScriptBridge() {
        List<JSQueryHandler> handlers = new HandlerFactory().getHandlers(browser, project);
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser loadedBrowser, CefFrame frame, int httpStatusCode) {
                super.onLoadEnd(loadedBrowser, frame, httpStatusCode);
                for (JSQueryHandler handler : handlers) {
                    injectQueryHandler(handler.getHandlerName(), handler.getJsQuery(), handler.getArgName());
                }
                browserInitialized.set(true);
                
                // Apply settings after browser is initialized
                applySettings();
                initSettingsUI();
                
                if (System.getProperty("callgraph.devtools.open") != null) {
                    browser.openDevtools();
                }
            }
        }, browser.getCefBrowser());
    }

    private void injectQueryHandler(String handlerName, JBCefJSQuery handler, String argName) {
        executeJavaScript("window.JavaBridge." + handlerName + " = function(" + argName + ") {" +
                handler.inject(argName) +
                "}");
    }
}
