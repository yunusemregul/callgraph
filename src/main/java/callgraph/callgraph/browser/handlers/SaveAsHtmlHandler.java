package callgraph.callgraph.browser.handlers;

import callgraph.callgraph.CallGraphGenerator;
import callgraph.callgraph.Utils;
import callgraph.callgraph.browser.JSQueryHandler;
import callgraph.callgraph.settings.CallGraphSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;
import java.awt.Color;

import java.io.IOException;
import java.util.function.Function;

public class SaveAsHtmlHandler extends JSQueryHandler {
    public SaveAsHtmlHandler(JBCefBrowserBase browser, Project project) {
        super(browser, project);
    }

    @Override
    @NotNull
    public Function<? super String, ? extends JBCefJSQuery.Response> getHandler() {
        return unused -> {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            ApplicationManager.getApplication().invokeLater(() -> FileChooser.chooseFile(descriptor, project, null, (VirtualFile file) -> {
                try {
                    String saveAsTemplate = Utils.getResourceFileAsString("build/saveas.html");
                    PsiMethod lastGeneratedMethod = CallGraphGenerator.getInstance(project).getLastGeneratedMethod();
                    String className = lastGeneratedMethod.getContainingClass().getName();
                    String methodName = lastGeneratedMethod.getName();
                    String methodPath = className + "." + methodName;
                    String title = "Call Graph of " + project.getName() + " - " + methodPath;
                    
                    // Get background color from settings
                    CallGraphSettings settings = CallGraphSettings.getInstance(project);
                    String backgroundColor = settings.getCustomBackgroundColor();
                    if (CallGraphSettings.BACKGROUND_TYPE_IDE.equals(settings.getBackgroundType())) {
                        Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
                        backgroundColor = "#" + ColorUtil.toHex(editorBackground);
                    }
                    
                    // Replace title and background color in template
                    saveAsTemplate = saveAsTemplate.replace("${title}", title);
                    saveAsTemplate = saveAsTemplate.replace("background: black;", "background: " + backgroundColor + ";");
                    
                    // Add network update script
                    saveAsTemplate += "<script>updateNetwork(" + CallGraphGenerator.getInstance(project).getJson() + ")</script>";
                    String direction = settings.getGraphDirection();
                    Utils.writeToFile(file.getPath() + "/callgraph_" + project.getName() + "_" + className + "_" + methodName + "_" + direction + ".html", saveAsTemplate);
                } catch (IOException e) {
                    // TODO: handle this
                    throw new RuntimeException(e);
                }
            }));
            return null;
        };
    }

    @Override
    @NotNull
    public String getHandlerName() {
        return "saveAsHtml";
    }

    @Override
    @NotNull
    public String getArgName() {
        return "unused";
    }
}
