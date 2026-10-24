package io.github.lgp547.anydoorplugin.request;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cmd/Ctrl-click fallback which works even while AnyDoor keeps a deliberately small PSI tree. */
public final class AnyDoorGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Pattern RUN=Pattern.compile("(?m)^\\s*RUN\\s+([\\w.$]+)#([\\w$]+)");
    @Override public @Nullable PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if(sourceElement==null||sourceElement.getContainingFile().getVirtualFile()==null||!"anydoor".equalsIgnoreCase(sourceElement.getContainingFile().getVirtualFile().getExtension()))return null;
        String source=editor.getDocument().getText(); Matcher matcher=RUN.matcher(source); while(matcher.find()) if(offset>=matcher.start(1)&&offset<=matcher.end(2)){
            PsiClass type=JavaPsiFacade.getInstance(sourceElement.getProject()).findClass(matcher.group(1), GlobalSearchScope.allScope(sourceElement.getProject()));
            if(type==null)return null;
            if(offset>=matcher.start(2)){com.intellij.psi.PsiMethod[] methods=type.findMethodsByName(matcher.group(2), true);return methods.length==0?null:methods;}
            if(type.isInterface()){PsiClass impl=ClassInheritorsSearch.search(type).findFirst();if(impl!=null)return new PsiElement[]{impl};} return new PsiElement[]{type};
        } return null;
    }
}
