package io.github.lgp547.anydoorplugin.request;

import com.intellij.navigation.DirectNavigationProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.lang.injection.InjectedLanguageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native editor navigation for a RUN target.  Direct navigation is consulted by
 * Cmd/Ctrl-click before the generic declaration action, so it also works when
 * the caret lands on a lexer leaf rather than a custom PSI reference node.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AnyDoorRunNavigationProvider implements DirectNavigationProvider {
    private static final Pattern RUN = Pattern.compile("(?m)^\\s*RUN\\s+([\\w.$]+)#([\\w$]+)");
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([\\w.-]+)}}");
    private static final Pattern DEFINITION = Pattern.compile("(?m)^\\s*@([\\w.-]+)\\s*=");

    @Override
    public @Nullable PsiElement getNavigationElement(@NotNull PsiElement element) {
        var file = sourceFile(element);
        if (file.getVirtualFile() == null || !"anydoor".equalsIgnoreCase(file.getVirtualFile().getExtension())) return null;
        Matcher variable = VARIABLE.matcher(element.getText());
        if (variable.find()) return definition(file, variable.group(1));
        int offset = element.getTextRange().getStartOffset();
        Matcher matcher = RUN.matcher(file.getText());
        while (matcher.find()) {
            if (offset < matcher.start(1) || offset > matcher.end(2)) continue;
            PsiClass type = JavaPsiFacade.getInstance(element.getProject())
                    .findClass(matcher.group(1), GlobalSearchScope.allScope(element.getProject()));
            if (type == null) return null;
            if (offset < matcher.start(2)) return implementationOrType(type);
            PsiMethod[] methods = type.findMethodsByName(matcher.group(2), true);
            return methods.length == 0 ? null : methods[0];
        }
        return null;
    }

    private static @NotNull PsiFile sourceFile(PsiElement element) {
        var file = element.getContainingFile();
        var host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(file.getViewProvider());
        return host == null ? file : host.getContainingFile();
    }

    private static @Nullable PsiElement definition(com.intellij.psi.PsiFile file, String name) {
        Matcher definitions = DEFINITION.matcher(file.getText());
        while (definitions.find()) {
            if (name.equals(definitions.group(1))) return file.findElementAt(definitions.start(1));
        }
        return null;
    }

    private static PsiClass implementationOrType(PsiClass type) {
        if (type.isInterface()) {
            PsiClass implementation = ClassInheritorsSearch.search(type).findFirst();
            if (implementation != null) return implementation;
        }
        return type;
    }
}
