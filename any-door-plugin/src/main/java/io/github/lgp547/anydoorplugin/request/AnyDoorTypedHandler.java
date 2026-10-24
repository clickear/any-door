package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Brings HTTP-client-like contextual completion to the characters that introduce request syntax. */
public final class AnyDoorTypedHandler extends TypedHandlerDelegate {
    @Override public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        boolean runClassStart=false;
        boolean templateStart=false;
        boolean anyDoor=file.getVirtualFile()!=null && "anydoor".equalsIgnoreCase(file.getVirtualFile().getExtension());
        if (anyDoor && c=='{') {
            int offset=editor.getCaretModel().getOffset();
            String text=editor.getDocument().getText();
            templateStart=offset>=2 && text.charAt(offset-1)=='{' && text.charAt(offset-2)=='{';
            if (templateStart && (offset>=text.length() || !text.startsWith("}}", offset))) {
                WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().insertString(offset, "}}"));
            }
        }
        if(c==' '){String text=editor.getDocument().getText();int offset=editor.getCaretModel().getOffset();int lineStart=text.lastIndexOf('\n',Math.max(0,offset-1))+1;runClassStart=text.substring(lineStart,Math.min(offset,text.length())).trim().equals("RUN");}
        if (anyDoor && (c=='@'||c=='#'||templateStart||runClassStart)) {
            AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
        }
        return Result.CONTINUE;
    }
}
