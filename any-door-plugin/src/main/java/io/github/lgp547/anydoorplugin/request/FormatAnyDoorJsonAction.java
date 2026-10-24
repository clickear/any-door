package io.github.lgp547.anydoorplugin.request;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/** Formats only the JSON body surrounding the caret, keeping request directives intact. */
public final class FormatAnyDoorJsonAction extends AnAction {
    @Override public void update(@NotNull AnActionEvent event) { var file=event.getData(CommonDataKeys.PSI_FILE); event.getPresentation().setEnabledAndVisible(file!=null&&file.getVirtualFile()!=null&&"anydoor".equalsIgnoreCase(file.getVirtualFile().getExtension())); }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project=event.getProject(); Editor editor=event.getData(CommonDataKeys.EDITOR); if(project==null||editor==null)return;
        formatAt(project, editor);
    }
    static boolean canFormatAt(Editor editor) {
        String text=editor.getDocument().getText(); int start=text.lastIndexOf('{', editor.getCaretModel().getOffset()); if(start<0)start=text.indexOf('{', editor.getCaretModel().getOffset()); if(start<0)return false;
        int end=matchingBrace(text,start); if(end<0)return false;
        try { JsonParser.parseString(text.substring(start,end+1)); return true; } catch(Exception ignored) { return false; }
    }
    static void formatAt(Project project, Editor editor) {
        String text=editor.getDocument().getText(); int start=text.lastIndexOf('{', editor.getCaretModel().getOffset()); if(start<0)start=text.indexOf('{', editor.getCaretModel().getOffset()); if(start<0)return;
        int end=matchingBrace(text,start); if(end<0)return;
        try { String formatted=new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(text.substring(start,end+1))); int from=start,to=end+1; WriteCommandAction.runWriteCommandAction(project,()->{ editor.getDocument().replaceString(from,to,formatted); PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument()); }); } catch(Exception ignored) { }
    }
    private static int matchingBrace(String text,int start){int level=0;boolean quoted=false,escaped=false;for(int i=start;i<text.length();i++){char c=text.charAt(i);if(quoted){if(c=='"'&&!escaped)quoted=false;escaped=c=='\\'&&!escaped;continue;}if(c=='"'){quoted=true;continue;}if(c=='{')level++;else if(c=='}'&&--level==0)return i;}return -1;}
}
