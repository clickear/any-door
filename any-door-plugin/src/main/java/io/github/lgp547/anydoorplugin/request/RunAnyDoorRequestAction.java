package io.github.lgp547.anydoorplugin.request;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import io.github.lgp547.anydoorplugin.util.NotifierUtil;
import org.jetbrains.annotations.NotNull;

public final class RunAnyDoorRequestAction extends AnAction {
    @Override public void update(@NotNull AnActionEvent event) {
        var file = event.getData(CommonDataKeys.PSI_FILE);
        event.getPresentation().setEnabledAndVisible(file != null && "anydoor".equalsIgnoreCase(file.getVirtualFile().getExtension()));
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject(); Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;
        try {
            String result = AnyDoorRequestRunner.runAt(project, editor.getDocument().getText(), editor.getCaretModel().getOffset());
            if (result.startsWith("失败")) NotifierUtil.notifyError(project, result); else NotifierUtil.notifyInfo(project, result);
        } catch (Exception exception) { NotifierUtil.notifyError(project, exception.getMessage()); }
    }
}
