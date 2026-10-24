package io.github.lgp547.anydoorplugin.request;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/** Fast feedback for broken RUN targets, before the user presses Run. */
public final class AnyDoorAnnotator implements Annotator {
    @Override public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode().getElementType()==AnyDoorTokenTypes.COMMENT) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(AnyDoorSyntaxHighlighter.COMMENT).create();
            return;
        }
        if (element.getNode().getElementType()==AnyDoorTokenTypes.CLASS_REFERENCE && JavaPsiFacade.getInstance(element.getProject()).findClass(element.getText(), GlobalSearchScope.allScope(element.getProject()))==null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "找不到类 " + element.getText()).range(element).create();
        }
        if (element.getNode().getElementType()==AnyDoorTokenTypes.VARIABLE) {
            String variable=element.getText().replace("\"", "").replace("{{", "").replace("}}", "").trim();
            if (!element.getText().endsWith("}}")) {
                holder.newAnnotation(HighlightSeverity.WARNING, "变量缺少闭合的 }}").range(element).textAttributes(AnyDoorSyntaxHighlighter.VARIABLE).create();
                return;
            }
            if (variable.startsWith("now.") || variable.startsWith("faker.") || variable.startsWith("last.")) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(AnyDoorSyntaxHighlighter.VARIABLE).create();
                return;
            }
            java.util.regex.Matcher definitions=java.util.regex.Pattern.compile("(?m)^\\s*@([\\w.-]+)\\s*=").matcher(element.getContainingFile().getText());
            while(definitions.find()) if(variable.equals(definitions.group(1))) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(AnyDoorSyntaxHighlighter.VARIABLE).create();
                return;
            }
            holder.newAnnotation(HighlightSeverity.WARNING, "未定义变量 {{" + variable + "}}").range(element).textAttributes(AnyDoorSyntaxHighlighter.VARIABLE).withFix(new CreateAnyDoorVariableFix(variable)).create();
        }
        if (element.getNode().getElementType()==AnyDoorTokenTypes.METHOD_REFERENCE) {
            PsiElement cursor=element.getPrevSibling(); String className=null;
            while(cursor!=null){if(cursor.getNode().getElementType()==AnyDoorTokenTypes.CLASS_REFERENCE){className=cursor.getText();break;}cursor=cursor.getPrevSibling();}
            if(className==null)return;
            var type=JavaPsiFacade.getInstance(element.getProject()).findClass(className, GlobalSearchScope.allScope(element.getProject()));
            if(type==null)return;
            var methods=type.findMethodsByName(element.getText(), true);
            if(methods.length==0)holder.newAnnotation(HighlightSeverity.ERROR, "类 " + className + " 中找不到方法 " + element.getText()).range(element).create();
            else if(methods.length>1)holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "存在 " + methods.length + " 个重载；建议填写参数类型").range(element).create();
        }
    }
}
