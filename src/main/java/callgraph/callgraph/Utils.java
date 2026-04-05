package callgraph.callgraph;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.asJava.LightClassUtil;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import java.io.*;
import java.util.stream.Collectors;

public final class Utils {
    private Utils() {
    }

    /**
     * Reads given resource file as a string.
     *
     * @param fileName path to the resource file
     * @return the file's contents
     * @throws IOException if read fails for any reason
     */
    public static String getResourceFileAsString(String fileName) throws IOException {
        ClassLoader classLoader = Utils.class.getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) return null;
            try (InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

    public static void writeToFile(String fileName, String content) throws IOException {
        File file = new File(fileName);
        file.getParentFile().mkdirs();
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
    }

    public static PsiMethod getMethodAtCaret(Project project, Editor editor) {
        if (project == null) {
            return null;
        }

        // Check if indexing is in progress (dumb mode)
        if (DumbService.isDumb(project)) {
            return null;
        }
        
        if (editor == null) {
            editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        }
        if (editor == null) {
            return null;
        }
        
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return null;
        }
        
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);

        if (element != null) {
            // Check Kotlin function first
            KtNamedFunction ktFunction = PsiTreeUtil.getParentOfType(element, KtNamedFunction.class);
            if (ktFunction != null) {
                return LightClassUtil.INSTANCE.getLightClassMethod(ktFunction);
            }

            if (element.getParent() instanceof PsiMethod) {
                return (PsiMethod) element.getParent();
            } else if (element.getParent() instanceof PsiReference) {
                element = ((PsiReference) element.getParent()).resolve();
                if (element instanceof PsiMethod) {
                    return (PsiMethod) element;
                }
            }
        }

        return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }
}
