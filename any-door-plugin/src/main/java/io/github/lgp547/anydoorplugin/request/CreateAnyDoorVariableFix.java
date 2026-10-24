package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Creates a local request variable directly from an unresolved {{variable}} warning. */
final class CreateAnyDoorVariableFix implements IntentionAction {
    private final String name;
    CreateAnyDoorVariableFix(String name) { this.name = name; }
    @Override public @NotNull String getFamilyName() { return "Create AnyDoor variable"; }
    @Override public @NotNull String getText() { return "Create @" + name + " variable"; }
    @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) { return file.getVirtualFile() != null && "anydoor".equalsIgnoreCase(file.getVirtualFile().getExtension()); }
    @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;
        WriteCommandAction.runWriteCommandAction(project, getText(), null, () -> {
            String text = document.getText();
            int insertion = text.startsWith("###") ? Math.max(0, text.indexOf('\n') + 1) : 0;
            document.insertString(insertion, "@" + name + " = \"\"\n");
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }, file);
    }
    @Override public boolean startInWriteAction() { return false; }
}
