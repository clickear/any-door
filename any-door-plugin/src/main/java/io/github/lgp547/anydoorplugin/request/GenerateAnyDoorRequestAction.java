package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/** Creates a reviewable request file beside the project rather than hiding calls in a dialog. */
public final class GenerateAnyDoorRequestAction extends AnAction {
    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(PsiTreeUtil.getParentOfType(event.getData(CommonDataKeys.PSI_ELEMENT), PsiMethod.class) != null);
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject(); PsiMethod method = PsiTreeUtil.getParentOfType(event.getData(CommonDataKeys.PSI_ELEMENT), PsiMethod.class);
        if (project == null || method == null || method.getContainingClass() == null || method.getContainingClass().getQualifiedName() == null) return;
        String fileName = method.getName() + ".anydoor";
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < method.getParameterList().getParametersCount(); i++) {
            if (i > 0) json.append(',');
            json.append("\n  \"").append(method.getParameterList().getParameters()[i].getName()).append("\": null");
        }
        if (method.getParameterList().getParametersCount() > 0) json.append('\n');
        json.append('}');
        String content = "### @name " + method.getName() + "\n@pid = {{TARGET_PID}}\n@timeout = 10s\n\nRUN "
                + method.getContainingClass().getQualifiedName() + "#" + method.getName() + "\n" + json + "\n";
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile root = project.getBaseDir();
                VirtualFile folder = VfsUtil.createDirectoryIfMissing(root, ".idea/any-door/requests");
                VirtualFile file = folder.findOrCreateChildData(this, fileName);
                VfsUtil.saveText(file, content);
                FileEditorManager.getInstance(project).openFile(file, true);
            } catch (Exception ignored) { }
        });
    }
}
