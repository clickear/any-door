package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Places JSON formatting in the standard Alt/Option+Enter editor workflow. */
public final class FormatAnyDoorJsonIntention implements IntentionAction {
    @Override public @NotNull String getText() { return "Format AnyDoor JSON body"; }
    @Override public @NotNull String getFamilyName() { return "AnyDoor JSON"; }
    @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file.getVirtualFile() != null && "anydoor".equalsIgnoreCase(file.getVirtualFile().getExtension()) && FormatAnyDoorJsonAction.canFormatAt(editor);
    }
    @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) { FormatAnyDoorJsonAction.formatAt(project, editor); }
    @Override public boolean startInWriteAction() { return false; }
}
